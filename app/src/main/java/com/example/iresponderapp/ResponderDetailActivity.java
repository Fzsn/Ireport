package com.example.iresponderapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iresponderapp.supabase.IncidentSummary;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import kotlin.Unit;
import com.squareup.picasso.Picasso;

public class ResponderDetailActivity extends AppCompatActivity {

    private static final String TAG = "ResponderDetailAct";

    private SupabaseIncidentsRepository incidentsRepository;

    // UI Elements
    private TextView detail_incidentCode, detail_status, detail_incidentType,
            detail_reporterName, detail_dateTime, detail_additionalInfo;
    private TextView detail_coordinate_display, detail_location_context, detail_address_full;
    private ImageView detail_incidentImage, detail_mapImage;
    private LinearLayout mapClickableArea;
    private Button btnAccomplishReport;
    
    // Timeline elements
    private TextView timeline_created_date;
    private LinearLayout timeline_assigned_row, timeline_inprogress_row, timeline_resolved_row;
    private TextView timeline_assigned_date, timeline_inprogress_date, timeline_resolved_date;

    // Data Variables
    private String incidentKey;
    private String incidentCode;
    private String incidentTypeStr; // To store the type (Crime, Fire, etc.)
    private Double incidentLatitude;
    private Double incidentLongitude;
    
    // Incident data to pass to form
    private String incidentReporterName;
    private String incidentAddress;
    private String incidentDescription;
    private String incidentCreatedAt;
    
    // Officer location
    private FusedLocationProviderClient fusedLocationClient;
    private Double officerLatitude;
    private Double officerLongitude;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_detail);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Setup location permission launcher
        locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (fineLocationGranted != null && fineLocationGranted) {
                    getOfficerLocationAndUpdateMap();
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    getOfficerLocationAndUpdateMap();
                }
            }
        );

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
        detail_mapImage = findViewById(R.id.detail_mapImage);
        mapClickableArea = findViewById(R.id.mapClickableArea);
        btnAccomplishReport = findViewById(R.id.btnAccomplishReport);

        // Timeline elements
        timeline_created_date = findViewById(R.id.timeline_created_date);
        timeline_assigned_row = findViewById(R.id.timeline_assigned_row);
        timeline_assigned_date = findViewById(R.id.timeline_assigned_date);
        timeline_inprogress_row = findViewById(R.id.timeline_inprogress_row);
        timeline_inprogress_date = findViewById(R.id.timeline_inprogress_date);
        timeline_resolved_row = findViewById(R.id.timeline_resolved_row);
        timeline_resolved_date = findViewById(R.id.timeline_resolved_date);

        // --- 2. Retrieve Data Passed from AlertFragment ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        incidentCode = getIntent().getStringExtra("INCIDENT_CODE");
        // Note: We don't rely on 'AGENCY' intent extra anymore, we will derive it from type.

        // --- 3. Initialize Supabase Repository and Load Details ---
        if (incidentKey != null) {
            IreportApp app = (IreportApp) getApplication();
            incidentsRepository = (SupabaseIncidentsRepository) app.getIncidentsRepository();
            loadIncidentDetails();
        } else {
            Toast.makeText(this, "Incident key missing.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // --- 4. Setup Action Listeners ---
        btnAccomplishReport.setOnClickListener(v -> launchAgencyForm());

        // Map Click Listener
        mapClickableArea.setOnClickListener(v -> {
            if (incidentLatitude != null && incidentLongitude != null && incidentLatitude != 0.0 && incidentLongitude != 0.0) {
                openMap(incidentLatitude, incidentLongitude);
            } else {
                Toast.makeText(this, "Location coordinates invalid.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadIncidentDetails() {
        incidentsRepository.loadIncidentByIdAsync(
                incidentKey,
                incident -> {
                    if (incident != null) {
                        String status = incident.getStatus();
                        incidentTypeStr = incident.getAgencyType(); // Get the type
                        String reporter = incident.getReporterName();
                        String createdAt = incident.getCreatedAt();
                        String date = createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : "--";
                        String time = createdAt != null && createdAt.length() >= 16 ? createdAt.substring(11, 16) : "--:--";
                        String address = incident.getLocationAddress();
                        String info = incident.getDescription();

                        // Debug logging
                        Log.d(TAG, "Reporter Name: " + reporter);
                        Log.d(TAG, "Status: " + status);
                        Log.d(TAG, "Agency Type: " + incidentTypeStr);
                        Log.d(TAG, "Media URLs: " + (incident.getMediaUrls() != null ? incident.getMediaUrls().size() : "null"));

                        // Coordinates
                        incidentLatitude = incident.getLatitude();
                        incidentLongitude = incident.getLongitude();
                        
                        // Store data for passing to form
                        incidentReporterName = reporter;
                        incidentAddress = address;
                        incidentDescription = info;
                        incidentCreatedAt = createdAt;

                        // Populate UI
                        String shortCode = incidentCode != null && incidentCode.length() > 8 
                            ? incidentCode.substring(0, 8).toUpperCase() 
                            : (incidentCode != null ? incidentCode.toUpperCase() : "N/A");
                        detail_incidentCode.setText("Incident #" + shortCode);
                        detail_status.setText("Status: " + (status != null ? status.toUpperCase() : "--"));
                        detail_incidentType.setText("Type: " + (incidentTypeStr != null ? incidentTypeStr.toUpperCase() : "N/A"));
                        detail_reporterName.setText("Reporter: " + (reporter != null ? reporter : "Unknown"));
                        detail_dateTime.setText("Date/Time: " + date + ", " + time);
                        detail_additionalInfo.setText("Additional Info: " + (info != null ? info : "N/A"));

                        detail_address_full.setText("Address: " + (address != null ? address : "N/A"));
                        String coordDisplay = (incidentLatitude != null && incidentLongitude != null)
                                ? String.format("%.6f, %.6f", incidentLatitude, incidentLongitude) : "N/A";
                        detail_coordinate_display.setText(coordDisplay);
                        detail_location_context.setText("Camarines Norte, Philippines");

                        // Handle media URLs
                        if (incident.getMediaUrls() != null && !incident.getMediaUrls().isEmpty()) {
                            final String imageUrl = incident.getMediaUrls().get(0);
                            Log.d(TAG, "Loading image from URL: " + imageUrl);
                            Picasso.get()
                                    .load(imageUrl)
                                    .placeholder(R.drawable.placeholder_image)
                                    .error(R.drawable.placeholder_image)
                                    .fit()
                                    .centerCrop()
                                    .into(detail_incidentImage);
                            
                            // Make image clickable to view full screen
                            detail_incidentImage.setOnClickListener(v -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(Uri.parse(imageUrl), "image/*");
                                try {
                                    startActivity(intent);
                                } catch (Exception e) {
                                    // Fallback: open in browser
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl));
                                    startActivity(browserIntent);
                                }
                            });
                        } else {
                            detail_incidentImage.setImageResource(R.drawable.placeholder_image);
                        }
                        
                        // Load static map image - first try to get officer location
                        if (incidentLatitude != null && incidentLongitude != null) {
                            // Request location permission and get officer location
                            requestOfficerLocation();
                        }
                        
                        // Populate Status Timeline
                        updateStatusTimeline(status, createdAt);

                    } else {
                        Toast.makeText(ResponderDetailActivity.this, "Incident data not found.", Toast.LENGTH_SHORT).show();
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Failed to load details: " + throwable.getMessage());
                    return Unit.INSTANCE;
                }
        );
    }

    private void launchAgencyForm() {
        if (incidentTypeStr == null || incidentTypeStr.isEmpty()) {
            Toast.makeText(this, "Error: Incident type is unknown.", Toast.LENGTH_SHORT).show();
            return;
        }

        Class<?> formActivity;

        // --- ROUTING LOGIC based on agency_type from Supabase (lowercase) ---
        String agencyTypeLower = incidentTypeStr.toLowerCase();
        if (agencyTypeLower.equals("pnp") || agencyTypeLower.equals("crime")) {
            formActivity = PnpReportFormActivity.class;
        } else if (agencyTypeLower.equals("bfp") || agencyTypeLower.equals("fire")) {
            formActivity = BfpReportFormActivity.class;
        } else if (agencyTypeLower.equals("pdrrmo") || agencyTypeLower.equals("mdrrmo") || 
                   agencyTypeLower.equals("medical emergency") || agencyTypeLower.equals("disaster")) {
            // Show dialog to choose between Medical/Rescue or Disaster report
            showMdrrmoReportTypeDialog();
            return;
        } else {
            // Default fallback or error handling
            Toast.makeText(this, "No specific form for type: " + incidentTypeStr, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, formActivity);
        intent.putExtra("INCIDENT_KEY", incidentKey);
        intent.putExtra("INCIDENT_CODE", incidentCode);
        intent.putExtra("INCIDENT_TYPE", incidentTypeStr != null ? incidentTypeStr.toUpperCase() : "N/A");
        intent.putExtra("INCIDENT_REPORTER", incidentReporterName);
        intent.putExtra("INCIDENT_ADDRESS", incidentAddress);
        intent.putExtra("INCIDENT_DESCRIPTION", incidentDescription);
        intent.putExtra("INCIDENT_CREATED_AT", incidentCreatedAt);
        startActivity(intent);
        finish();
    }

    private void showMdrrmoReportTypeDialog() {
        // Create a dialog to choose between Medical/Rescue or Disaster report
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Report Type");
        builder.setMessage("Please select the type of report you want to submit:");
        builder.setPositiveButton("Medical/Rescue", (dialog, id) -> {
            Intent intent = new Intent(this, MdrrmoReportFormActivity.class);
            intent.putExtra("INCIDENT_KEY", incidentKey);
            intent.putExtra("INCIDENT_CODE", incidentCode);
            intent.putExtra("INCIDENT_TYPE", incidentTypeStr != null ? incidentTypeStr.toUpperCase() : "N/A");
            intent.putExtra("INCIDENT_REPORTER", incidentReporterName);
            intent.putExtra("INCIDENT_ADDRESS", incidentAddress);
            intent.putExtra("INCIDENT_DESCRIPTION", incidentDescription);
            intent.putExtra("INCIDENT_CREATED_AT", incidentCreatedAt);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton("Disaster", (dialog, id) -> {
            Intent intent = new Intent(this, MdrrmoDisasterReportFormActivity.class);
            intent.putExtra("INCIDENT_KEY", incidentKey);
            intent.putExtra("INCIDENT_CODE", incidentCode);
            intent.putExtra("INCIDENT_TYPE", incidentTypeStr != null ? incidentTypeStr.toUpperCase() : "N/A");
            intent.putExtra("INCIDENT_REPORTER", incidentReporterName);
            intent.putExtra("INCIDENT_ADDRESS", incidentAddress);
            intent.putExtra("INCIDENT_DESCRIPTION", incidentDescription);
            intent.putExtra("INCIDENT_CREATED_AT", incidentCreatedAt);
            startActivity(intent);
            finish();
        });
        builder.show();
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
        // Open Google Maps with navigation directions from current location to incident
        String navigationUri = "google.navigation:q=" + lat + "," + lon + "&mode=d";
        Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
        navigationIntent.setPackage("com.google.android.apps.maps");
        
        if (navigationIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(navigationIntent);
        } else {
            // Fallback to regular map view
            String uri = "geo:" + lat + "," + lon + "?z=15&q=" + lat + "," + lon + "(Incident Location)";
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Toast.makeText(this, "Google Maps is not installed.", Toast.LENGTH_LONG).show();
                String genericUrl = "http://maps.google.com/maps?daddr=" + lat + "," + lon;
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(genericUrl));
                startActivity(browserIntent);
            }
        }
    }

    private void updateStatusTimeline(String status, String createdAt) {
        // Format created date for timeline
        String createdDate = "--";
        if (createdAt != null && createdAt.length() >= 16) {
            createdDate = createdAt.substring(0, 10) + " at " + createdAt.substring(11, 16);
        }
        timeline_created_date.setText(createdDate);

        if (status == null) return;

        String statusLower = status.toLowerCase();

        // Show timeline rows based on status progression
        // pending -> assigned -> in_progress -> resolved

        if (statusLower.equals("assigned") || statusLower.equals("in_progress") || 
            statusLower.equals("responding") || statusLower.equals("resolved") || statusLower.equals("completed")) {
            timeline_assigned_row.setVisibility(View.VISIBLE);
            timeline_assigned_date.setText(createdDate); // Using created date as fallback
        }

        if (statusLower.equals("in_progress") || statusLower.equals("responding") || 
            statusLower.equals("resolved") || statusLower.equals("completed")) {
            timeline_inprogress_row.setVisibility(View.VISIBLE);
            timeline_inprogress_date.setText("En route to location");
        }

        if (statusLower.equals("resolved") || statusLower.equals("completed")) {
            timeline_resolved_row.setVisibility(View.VISIBLE);
            timeline_resolved_date.setText("Incident closed");
        }
    }

    private void requestOfficerLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getOfficerLocationAndUpdateMap();
        } else {
            locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            });
            loadMapWithMarkers(null, null);
        }
    }

    private void getOfficerLocationAndUpdateMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            loadMapWithMarkers(null, null);
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
            .addOnSuccessListener(this, location -> {
                if (location != null) {
                    officerLatitude = location.getLatitude();
                    officerLongitude = location.getLongitude();
                    Log.d(TAG, "Officer location: " + officerLatitude + ", " + officerLongitude);
                    loadMapWithMarkers(officerLatitude, officerLongitude);
                } else {
                    Log.w(TAG, "Officer location is null");
                    loadMapWithMarkers(null, null);
                }
            })
            .addOnFailureListener(this, e -> {
                Log.e(TAG, "Failed to get officer location: " + e.getMessage());
                loadMapWithMarkers(null, null);
            });
    }

    private void loadMapWithMarkers(Double officerLat, Double officerLon) {
        if (incidentLatitude == null || incidentLongitude == null) return;

        if (officerLat != null && officerLon != null) {
            fetchRouteAndDisplayMap(officerLat, officerLon);
        } else {
            displayStaticMap(null, null, null, null);
        }
    }

    private void fetchRouteAndDisplayMap(Double officerLat, Double officerLon) {
        String googleMapsApiKey = BuildConfig.GOOGLE_MAPS_API_KEY;
        String directionsUrl = String.format(java.util.Locale.US,
            "https://maps.googleapis.com/maps/api/directions/json?origin=%f,%f&destination=%f,%f&mode=driving&key=%s",
            officerLat, officerLon, incidentLatitude, incidentLongitude, googleMapsApiKey);

        Log.d(TAG, "Fetching directions from API");

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(directionsUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Directions API response code: " + responseCode);

                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    org.json.JSONObject json = new org.json.JSONObject(response.toString());
                    String status = json.getString("status");
                    Log.d(TAG, "Directions API status: " + status);

                    if ("OK".equals(status)) {
                        org.json.JSONArray routes = json.getJSONArray("routes");
                        if (routes.length() > 0) {
                            org.json.JSONObject route = routes.getJSONObject(0);
                            String encodedPolyline = route.getJSONObject("overview_polyline").getString("points");
                            org.json.JSONArray legs = route.getJSONArray("legs");
                            if (legs.length() > 0) {
                                org.json.JSONObject leg = legs.getJSONObject(0);
                                String distanceText = leg.getJSONObject("distance").getString("text");
                                String durationText = leg.getJSONObject("duration").getString("text");

                                Log.d(TAG, "Route found: " + distanceText + ", " + durationText);

                                final Double oLat = officerLat;
                                final Double oLon = officerLon;
                                runOnUiThread(() -> {
                                    detail_location_context.setText("Distance: " + distanceText + " (" + durationText + ")");
                                    displayStaticMap(oLat, oLon, encodedPolyline, distanceText);
                                });
                                return;
                            }
                        }
                    } else {
                        String errorMsg = json.optString("error_message", "Unknown error");
                        Log.e(TAG, "Directions API error: " + status + " - " + errorMsg);
                    }
                } else {
                    Log.e(TAG, "Directions API HTTP error: " + responseCode);
                }

                final Double oLat = officerLat;
                final Double oLon = officerLon;
                runOnUiThread(() -> displayStaticMap(oLat, oLon, null, null));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching directions: " + e.getMessage(), e);
                final Double oLat = officerLat;
                final Double oLon = officerLon;
                runOnUiThread(() -> displayStaticMap(oLat, oLon, null, null));
            }
        }).start();
    }

    private void displayStaticMap(Double officerLat, Double officerLon, String encodedPolyline, String distanceText) {
        String googleMapsApiKey = BuildConfig.GOOGLE_MAPS_API_KEY;
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("https://maps.googleapis.com/maps/api/staticmap?");

        if (officerLat != null && officerLon != null) {
            double distance = calculateDistance(incidentLatitude, incidentLongitude, officerLat, officerLon);
            int zoom = calculateZoomLevel(distance);
            double centerLat = (incidentLatitude + officerLat) / 2;
            double centerLon = (incidentLongitude + officerLon) / 2;

            urlBuilder.append(String.format(java.util.Locale.US, "center=%f,%f&zoom=%d&size=600x300&maptype=roadmap",
                centerLat, centerLon, zoom));
            urlBuilder.append(String.format(java.util.Locale.US, "&markers=color:red|label:I|%f,%f",
                incidentLatitude, incidentLongitude));
            urlBuilder.append(String.format(java.util.Locale.US, "&markers=color:blue|label:O|%f,%f",
                officerLat, officerLon));

            if (encodedPolyline != null && !encodedPolyline.isEmpty()) {
                try {
                    String encodedPath = java.net.URLEncoder.encode(encodedPolyline, "UTF-8");
                    urlBuilder.append("&path=color:0x4285F4|weight:4|enc:").append(encodedPath);
                } catch (Exception e) {
                    urlBuilder.append(String.format(java.util.Locale.US, "&path=color:0x4285F480|weight:3|%f,%f|%f,%f",
                        officerLat, officerLon, incidentLatitude, incidentLongitude));
                }
            } else {
                urlBuilder.append(String.format(java.util.Locale.US, "&path=color:0x4285F480|weight:3|%f,%f|%f,%f",
                    officerLat, officerLon, incidentLatitude, incidentLongitude));
            }

            if (distanceText == null) {
                String distText = distance < 1 ?
                    String.format(java.util.Locale.US, "%.0f meters away", distance * 1000) :
                    String.format(java.util.Locale.US, "%.1f km away", distance);
                detail_location_context.setText("Distance: " + distText);
            }
        } else {
            urlBuilder.append(String.format(java.util.Locale.US, "center=%f,%f&zoom=15&size=600x300&maptype=roadmap",
                incidentLatitude, incidentLongitude));
            urlBuilder.append(String.format(java.util.Locale.US, "&markers=color:red|%f,%f",
                incidentLatitude, incidentLongitude));
        }

        urlBuilder.append("&key=").append(googleMapsApiKey);
        String staticMapUrl = urlBuilder.toString();
        Log.d(TAG, "Static Map URL: " + staticMapUrl);

        Picasso.get()
            .load(staticMapUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(detail_mapImage, new com.squareup.picasso.Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Map image loaded successfully");
                }
                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Map image load error: " + e.getMessage());
                }
            });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private int calculateZoomLevel(double distanceKm) {
        if (distanceKm < 0.5) return 16;
        if (distanceKm < 1) return 15;
        if (distanceKm < 2) return 14;
        if (distanceKm < 5) return 13;
        if (distanceKm < 10) return 12;
        if (distanceKm < 20) return 11;
        return 10;
    }
}