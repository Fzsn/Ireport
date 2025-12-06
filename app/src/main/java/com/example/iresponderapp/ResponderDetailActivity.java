package com.example.iresponderapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

public class ResponderDetailActivity extends AppCompatActivity {

    private static final String TAG = "ResponderDetailAct";

    private DatabaseReference incidentRef;

    // UI Elements
    private TextView detail_incidentCode, detail_status, detail_incidentType,
            detail_reporterName, detail_dateTime, detail_additionalInfo;
    private TextView detail_coordinate_display, detail_location_context, detail_address_full;
    private ImageView detail_incidentImage;
    private LinearLayout mapClickableArea;
    private Button btnAccomplishReport;

    // Data Variables
    private String incidentKey;
    private String incidentCode;
    private String incidentTypeStr; // To store the type (Crime, Fire, etc.)
    private String incidentLatitude;
    private String incidentLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_detail);

        // --- 1. Initialize Views ---
        detail_incidentCode = findViewById(R.id.detail_incidentCode);
        detail_status = findViewById(R.id.detail_status);
        detail_incidentType = findViewById(R.id.detail_incidentType);
        detail_reporterName = findViewById(R.id.detail_reporterName);
        detail_dateTime = findViewById(R.id.detail_dateTime);
        detail_additionalInfo = findViewById(R.id.detail_additionalInfo);
        detail_incidentImage = findViewById(R.id.detail_incidentImage);

        detail_coordinate_display = findViewById(R.id.detail_coordinate_display);
        detail_location_context = findViewById(R.id.detail_location_context);
        detail_address_full = findViewById(R.id.detail_address_full);
        mapClickableArea = findViewById(R.id.mapClickableArea);
        btnAccomplishReport = findViewById(R.id.btnAccomplishReport);

        // --- 2. Retrieve Data Passed from AlertFragment ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        incidentCode = getIntent().getStringExtra("INCIDENT_CODE");
        // Note: We don't rely on 'AGENCY' intent extra anymore, we will derive it from type.

        // --- 3. Set Firebase Reference and Load Details ---
        if (incidentKey != null) {
            incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp")
                    .child("Incidents_")
                    .child(incidentKey);
            loadIncidentDetails();
        } else {
            Toast.makeText(this, "Incident key missing.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // --- 4. Setup Action Listeners ---
        btnAccomplishReport.setOnClickListener(v -> launchAgencyForm());

        // Map Click Listener
        mapClickableArea.setOnClickListener(v -> {
            double decimalLat = convertDMSToDecimal(incidentLatitude);
            double decimalLon = convertDMSToDecimal(incidentLongitude);

            if (decimalLat != 0.0 && decimalLon != 0.0) {
                openMap(decimalLat, decimalLon);
            } else {
                Toast.makeText(this, "Location coordinates invalid.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadIncidentDetails() {
        incidentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("Status").getValue(String.class);
                    incidentTypeStr = snapshot.child("incidentType").getValue(String.class); // Get the type
                    String reporter = snapshot.child("reporterName").getValue(String.class);
                    String date = snapshot.child("date").getValue(String.class);
                    String time = snapshot.child("Time").getValue(String.class);
                    String address = snapshot.child("address").getValue(String.class);
                    String info = snapshot.child("additionalInfo").getValue(String.class);
                    String imageUrl = snapshot.child("imageURL").getValue(String.class);

                    // Coordinates
                    incidentLatitude = snapshot.child("latitude").getValue(String.class);
                    incidentLongitude = snapshot.child("longitude").getValue(String.class);

                    // Populate UI
                    detail_incidentCode.setText("Incident #" + incidentCode);
                    detail_status.setText("Status: " + status);
                    detail_incidentType.setText("Type: " + incidentTypeStr);
                    detail_reporterName.setText("Reporter: " + reporter);
                    detail_dateTime.setText("Date/Time: " + date + ", " + time);
                    detail_additionalInfo.setText("Additional Info: " + info);

                    detail_address_full.setText("Address: " + address);
                    detail_coordinate_display.setText(incidentLatitude + ", " + incidentLongitude);
                    detail_location_context.setText("Camarines Norte, Philippines");

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Picasso.get().load(imageUrl).into(detail_incidentImage);
                    } else {
                        detail_incidentImage.setImageResource(R.drawable.placeholder_image);
                    }

                } else {
                    Toast.makeText(ResponderDetailActivity.this, "Incident data not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to load details: " + error.getMessage());
            }
        });
    }

    private void launchAgencyForm() {
        if (incidentTypeStr == null || incidentTypeStr.isEmpty()) {
            Toast.makeText(this, "Error: Incident type is unknown.", Toast.LENGTH_SHORT).show();
            return;
        }

        Class<?> formActivity;

        // --- ROUTING LOGIC based on Incident Type ---
        if (incidentTypeStr.equalsIgnoreCase("Crime")) {
            formActivity = PnpReportFormActivity.class;
        } else if (incidentTypeStr.equalsIgnoreCase("Fire")) {
            formActivity = BfpReportFormActivity.class;
        } else if (incidentTypeStr.equalsIgnoreCase("Medical Emergency") || incidentTypeStr.equalsIgnoreCase("Disaster")) {
            formActivity = MdrrmoReportFormActivity.class;
        } else {
            // Default fallback or error handling
            Toast.makeText(this, "No specific form for type: " + incidentTypeStr, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, formActivity);
        intent.putExtra("INCIDENT_KEY", incidentKey);
        intent.putExtra("INCIDENT_CODE", incidentCode);
        // We pass the derived agency type if needed by the form, though form class is already specific
        startActivity(intent);
        finish();
    }

    // --- Utility Methods ---
    private double convertDMSToDecimal(String dms) {
        if (dms == null || dms.isEmpty()) return 0.0;
        try {
            String cleanedDMS = dms.replaceAll("[^0-9\\.]", " ").trim();
            String[] parts = cleanedDMS.split("\\s+");
            if (parts.length >= 3) {
                double degrees = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return degrees + (minutes / 60.0) + (seconds / 3600.0);
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