package com.example.iresponderapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

public class AccomplishedDetailsActivity extends AppCompatActivity {

    private static final String TAG = "AccomplishedDetails";

    private DatabaseReference incidentRef;

    private TextView detail_incidentCode, detail_status, detail_incidentType,
            detail_reporterName, detail_dateTime, detail_additionalInfo;
    private TextView detail_address_full, detail_coordinate_display, detail_location_context;

    // History specific fields
    private TextView detail_assignment_info;
    private LinearLayout mapClickableArea;
    private ImageView detail_incidentImage;

    private String incidentKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // CRITICAL: Use the new dedicated layout file
        setContentView(R.layout.activity_accomplished_details);

        // --- 1. Initialize Views (Matching activity_accomplished_details.xml) ---
        detail_incidentCode = findViewById(R.id.detail_incidentCode);
        detail_status = findViewById(R.id.detail_status);
        detail_incidentType = findViewById(R.id.detail_incidentType);
        detail_reporterName = findViewById(R.id.detail_reporterName);
        detail_dateTime = findViewById(R.id.detail_dateTime);
        detail_additionalInfo = findViewById(R.id.detail_additionalInfo);
        detail_incidentImage = findViewById(R.id.detail_incidentImage);

        detail_address_full = findViewById(R.id.detail_address_full);
        detail_coordinate_display = findViewById(R.id.detail_coordinate_display);
        detail_location_context = findViewById(R.id.detail_location_context);

        detail_assignment_info = findViewById(R.id.detail_assignment_info);
        mapClickableArea = findViewById(R.id.mapClickableArea);

        // --- 2. Get Incident Key and Set Firebase Reference ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");

        if (incidentKey != null) {
            incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp")
                    .child("Incidents_")
                    .child(incidentKey);
            loadIncidentDetails();
        } else {
            Toast.makeText(this, "Error: Incident key not found.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadIncidentDetails() {
        incidentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {

                    String code = getIntent().getStringExtra("INCIDENT_CODE");
                    String status = snapshot.child("Status").getValue(String.class);
                    String type = snapshot.child("incidentType").getValue(String.class);
                    String reporter = snapshot.child("reporterName").getValue(String.class);
                    String date = snapshot.child("date").getValue(String.class);
                    String time = snapshot.child("Time").getValue(String.class);
                    String address = snapshot.child("address").getValue(String.class);
                    String latitude = snapshot.child("latitude").getValue(String.class);
                    String longitude = snapshot.child("longitude").getValue(String.class);
                    String info = snapshot.child("additionalInfo").getValue(String.class);
                    String imageUrl = snapshot.child("imageURL").getValue(String.class);

                    // History Data
                    String assignedName = snapshot.child("AssignedResponderName").getValue(String.class);

                    // --- Populate Views ---
                    detail_incidentCode.setText("Incident #" + code);
                    detail_status.setText("Status: " + status);
                    detail_incidentType.setText("Type: " + type);
                    detail_reporterName.setText("Reported by: " + reporter);
                    detail_dateTime.setText("Date/Time: " + date + ", " + time);
                    detail_additionalInfo.setText("Additional Info: " + info);

                    detail_address_full.setText("Address: " + address);
                    detail_coordinate_display.setText(latitude + ", " + longitude);
                    detail_location_context.setText("Camarines Norte, Philippines");

                    // Populate History Field
                    if (status.equalsIgnoreCase("Rejected")) {
                        detail_assignment_info.setText("Reason: Incident was deemed unsuitable for dispatch.");
                    } else if (assignedName != null && !assignedName.isEmpty()) {
                        detail_assignment_info.setText("Assigned Responder: " + assignedName);
                    } else {
                        detail_assignment_info.setText("Status: " + status + " (No responder assigned)");
                    }

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Picasso.get().load(imageUrl).into(detail_incidentImage);
                    } else {
                        detail_incidentImage.setImageResource(R.drawable.placeholder_image);
                    }

                    // Set Map Click Listener (Read-only functionality)
                    mapClickableArea.setOnClickListener(v -> {
                        double decimalLat = convertDMSToDecimal(latitude);
                        double decimalLon = convertDMSToDecimal(longitude);
                        if (decimalLat != 0.0 && decimalLon != 0.0) {
                            openMap(decimalLat, decimalLon);
                        } else {
                            Toast.makeText(AccomplishedDetailsActivity.this, "Location coordinates invalid.", Toast.LENGTH_SHORT).show();
                        }
                    });


                } else {
                    Toast.makeText(AccomplishedDetailsActivity.this, "Incident data not found.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Database Error: " + error.getMessage());
                Toast.makeText(AccomplishedDetailsActivity.this, "Failed to load details: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // You MUST copy convertDMSToDecimal and openMap here from activity_incident_details.java

    private double convertDMSToDecimal(String dms) {
        if (dms == null || dms.isEmpty()) return 0.0;
        try {
            String cleanedDMS = dms.replaceAll("[^0-9\\.]", " ").trim();
            String[] parts = cleanedDMS.split("\\s+");
            if (parts.length >= 3) {
                double degrees = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);
                return decimal;
            }
        } catch (Exception e) {
            Log.e(TAG, "DMS conversion failed for: " + dms, e);
        }
        return 0.0;
    }


    private void openMap(double lat, double lon) {
        String uri = "geo:" + lat + "," + lon + "?z=15&q=" + lat + "," + lon + "(Incident Location)";
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Google Maps is not installed.", Toast.LENGTH_LONG).show();
            String genericUrl = "http://maps.google.com/?q=" + lat + "," + lon;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(genericUrl));
            startActivity(browserIntent);
        }
    }
}