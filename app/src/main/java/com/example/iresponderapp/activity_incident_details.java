package com.example.iresponderapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class activity_incident_details extends AppCompatActivity {

    private static final String TAG = "IncidentDetailsAct";

    private DatabaseReference incidentRef;
    private DatabaseReference respondersRef;

    private TextView detail_incidentCode, detail_status, detail_incidentType,
            detail_reporterName, detail_dateTime, detail_additionalInfo;

    private TextView detail_coordinate_display, detail_location_context, detail_address_full;
    private ImageView detail_incidentImage;
    private LinearLayout mapClickableArea;
    private Button btnEscalateDetail, btnRejectDetail;

    private String incidentLatitude;
    private String incidentLongitude;
    private String incidentKey;
    private String incidentAgency;
    private String incidentLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incident_details);

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
        btnEscalateDetail = findViewById(R.id.btnEscalateDetail);
        btnRejectDetail = findViewById(R.id.btnRejectDetail);

        // --- 2. Get Incident Key and Set Firebase Reference ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");

        if (incidentKey != null) {
            incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp")
                    .child("Incidents_")
                    .child(incidentKey);
            respondersRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Responders");

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
                    incidentLatitude = snapshot.child("latitude").getValue(String.class);
                    incidentLongitude = snapshot.child("longitude").getValue(String.class);
                    String info = snapshot.child("additionalInfo").getValue(String.class);
                    String imageUrl = snapshot.child("imageURL").getValue(String.class);

                    // --- Derive Agency ---
                    if ("Fire".equalsIgnoreCase(type)) {
                        incidentAgency = "BFP";
                    } else if ("Crime".equalsIgnoreCase(type)) {
                        incidentAgency = "PNP";
                    } else {
                        incidentAgency = "MDRRMO";
                    }

                    // --- FIX: Detect Location dynamically from Address ---
                    if (address != null) {
                        String addrLower = address.toLowerCase();
                        if (addrLower.contains("daet")) {
                            incidentLocation = "Daet";
                        } else if (addrLower.contains("labo")) {
                            incidentLocation = "Labo";
                        } else if (addrLower.contains("basud")) {
                            incidentLocation = "Basud";
                        } else {
                            incidentLocation = "Daet"; // Default Fallback
                        }
                    } else {
                        incidentLocation = "Daet";
                    }

                    // --- Populate Views ---
                    detail_incidentCode.setText("Incident #" + code);
                    detail_status.setText("Status: " + status);
                    detail_incidentType.setText("Type: " + type);
                    detail_reporterName.setText("Reporter: " + reporter);
                    detail_dateTime.setText("Date/Time: " + date + ", " + time);
                    detail_additionalInfo.setText("Additional Info: " + info);

                    detail_address_full.setText("Address: " + address);
                    detail_coordinate_display.setText(incidentLatitude + ", " + incidentLongitude);

                    // Show the derived location
                    detail_location_context.setText(incidentLocation + ", Camarines Norte");

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Picasso.get().load(imageUrl).into(detail_incidentImage);
                    } else {
                        detail_incidentImage.setImageResource(R.drawable.placeholder_image);
                    }

                    // --- Attach Click Listeners ---
                    setupActionListeners();

                } else {
                    Toast.makeText(activity_incident_details.this, "Incident data not found.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Database Error: " + error.getMessage());
                Toast.makeText(activity_incident_details.this, "Failed to load details: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupActionListeners() {
        String incidentCode = getIntent().getStringExtra("INCIDENT_CODE");

        // Map View Listener
        mapClickableArea.setOnClickListener(v -> {
            double decimalLat = convertDMSToDecimal(incidentLatitude);
            double decimalLon = convertDMSToDecimal(incidentLongitude);

            if (decimalLat != 0.0 && decimalLon != 0.0) {
                openMap(decimalLat, decimalLon);
            } else {
                Toast.makeText(this, "Location coordinates invalid.", Toast.LENGTH_SHORT).show();
            }
        });

        // Escalate Button Listener - Opens Assignment Dialog
        btnEscalateDetail.setOnClickListener(v -> {
            showAssignmentDialog(incidentKey, incidentCode, incidentAgency, incidentLocation);
        });

        // Reject Button Listener - Opens Confirmation Dialog
        btnRejectDetail.setOnClickListener(v -> {
            showRejectConfirmation(incidentKey, incidentCode);
        });
    }

    // =======================================================================
    //                        DIALOG AND ASSIGNMENT LOGIC
    // =======================================================================

    // --- ESCALATION: Step 1 (Fetch Responders) ---
    private void showAssignmentDialog(String key, String code, String agency, String location) {

        // Fetch ALL responders and then filter locally by agency AND location.
        respondersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                final Map<String, String> responderMap = new HashMap<>();
                final List<String> responderNames = new ArrayList<>();

                // Add default "unassigned" option
                responderNames.add("--- Select Responder ---");
                responderMap.put("--- Select Responder ---", null);

                for (DataSnapshot data : snapshot.getChildren()) {
                    String rAgency = data.child("agency").getValue(String.class);
                    String rLocation = data.child("location").getValue(String.class);
                    String rFullName = data.child("fullName").getValue(String.class);

                    // Ensure string safety
                    if (rAgency != null && rLocation != null && rFullName != null) {

                        boolean agencyMatches = rAgency.trim().equalsIgnoreCase(agency);
                        boolean locationMatches = rLocation.trim().equalsIgnoreCase(location);

                        // Filter by matching both AGENCY and LOCATION
                        if (agencyMatches && locationMatches) {
                            String uniqueName = rFullName + " (" + rAgency + " " + rLocation + ")";
                            responderNames.add(uniqueName);
                            responderMap.put(uniqueName, data.getKey()); // Store UID as value
                        }
                    }
                }

                // Step 2: Display the dialog with the filtered list
                displayResponderDialog(key, code, responderNames, responderMap, agency, location);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(activity_incident_details.this, "Failed to fetch responders: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- ESCALATION: Step 2 (Display Dialog) ---
    private void displayResponderDialog(String key, String code, List<String> names, Map<String, String> map, String agency, String location) {

        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_responder_assignment, null);

        // Set criteria text
        TextView criteriaText = dialogView.findViewById(R.id.assignmentCriteriaText);
        criteriaText.setText("Agency: " + agency + ", Location: " + location);

        final Spinner spinner = dialogView.findViewById(R.id.responderSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        spinner.setAdapter(adapter);

        new AlertDialog.Builder(this)
                .setTitle("Assign Incident " + code)
                .setMessage("Select a qualified responder with the same agency and location to attend to the incident.")
                .setView(dialogView)
                .setPositiveButton("Confirm Assignment", (dialog, which) -> {
                    String selectedNameWithLocation = (String) spinner.getSelectedItem();
                    String selectedUid = map.get(selectedNameWithLocation);

                    if (selectedUid != null) {
                        // Step 3: Update Firebase and close activity
                        updateIncidentAssignment(key, code, selectedUid, selectedNameWithLocation);
                    } else {
                        Toast.makeText(this, "Please select a valid responder.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- ESCALATION: Step 3 (Update Firebase) ---
    private void updateIncidentAssignment(String key, String code, String uid, String name) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("Status", "Assigned");
        updates.put("AssignedResponderUID", uid);
        updates.put("AssignedResponderName", name);

        incidentRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, code + " assigned to " + name + ".", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to assign incident.", Toast.LENGTH_SHORT).show();
                });
    }

    // --- REJECTION: Confirmation Dialog ---
    private void showRejectConfirmation(String key, String code) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Incident Rejection")
                .setMessage("You are confirming that no responders will attend to the reported incident. The incident status will be marked as Rejected. Are you sure you want to reject this incident?")
                .setPositiveButton("Yes, Reject Incident", (dialog, which) -> {
                    incidentRef.child("Status").setValue("Rejected")
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, code + " has been rejected.", Toast.LENGTH_LONG).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to reject incident.", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =======================================================================
    //                        UTILITY METHODS (MAP FIX)
    // =======================================================================

    // --- Utility Method for DMS Conversion (ROBUST STRING CLEANING) ---
    private double convertDMSToDecimal(String dms) {
        if (dms == null || dms.isEmpty()) return 0.0;

        try {
            // Step 1: Clean the string by replacing all characters that are NOT a number (0-9) or a decimal point (.)
            // with a single space. This handles ° and ' safely.
            String cleanedDMS = dms.replaceAll("[^0-9\\.]", " ").trim();

            // Step 2: Split the resulting string by one or more spaces ("\\s+")
            String[] parts = cleanedDMS.split("\\s+");

            if (parts.length >= 3) {
                double degrees = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1]);
                double seconds = Double.parseDouble(parts[2]);

                double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);

                return decimal;
            } else {
                Log.e(TAG, "DMS String did not split into 3 parts: " + cleanedDMS + " (Parts found: " + parts.length + ")");
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "DMS conversion failed due to invalid numbers in string: " + dms, e);
        } catch (Exception e) {
            Log.e(TAG, "DMS conversion failed for: " + dms, e);
        }
        return 0.0;
    }


    // New Method to Open Google Maps
    private void openMap(double lat, double lon) {
        String uri = "geo:" + lat + "," + lon + "?z=15&q=" + lat + "," + lon + "(Incident Location)";
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Google Maps is not installed. Opening in browser.", Toast.LENGTH_LONG).show();
            String genericUrl = "http://maps.google.com/?q=" + lat + "," + lon;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(genericUrl));
            startActivity(browserIntent);
        }
    }
}