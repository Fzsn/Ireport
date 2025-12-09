package com.example.iresponderapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iresponderapp.supabase.IncidentSummary;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import com.squareup.picasso.Picasso;

import kotlin.Unit;

import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import android.graphics.Color;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

public class AccomplishedDetailsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "AccomplishedDetails";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private SupabaseIncidentsRepository incidentsRepository;

    private TextView detail_incidentCode, detail_status, detail_incidentType,
            detail_reporterName, detail_dateTime, detail_additionalInfo;
    private TextView detail_address_full, detail_coordinate_display, detail_location_context;

    // History specific fields
    private TextView detail_assignment_info;
    private ImageView detail_incidentImage;

    private GoogleMap mMap;
    private Double mLatitude;
    private Double mLongitude;
    private Polyline currentPolyline;

    private FusedLocationProviderClient fusedLocationClient;
    private LatLng userLocation;

    private String incidentKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // CRITICAL: Use the new dedicated layout file
        setContentView(R.layout.activity_accomplished_details);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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

        // Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Check/Request Location Permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getUserLocation();
        }

        // --- 2. Get Incident Key and Initialize Supabase Repository ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");

        if (incidentKey != null) {
            IreportApp app = (IreportApp) getApplication();
            incidentsRepository = (SupabaseIncidentsRepository) app.getIncidentsRepository();
            loadIncidentDetails();
        } else {
            Toast.makeText(this, "Error: Incident key not found.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        updateMap();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            } else {
                Toast.makeText(this, "Location permission required for routing.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadIncidentDetails() {
        incidentsRepository.loadIncidentByIdAsync(
                incidentKey,
                incident -> {
                    if (incident != null) {

                        String code = getIntent().getStringExtra("INCIDENT_CODE");
                        String status = incident.getStatus();
                        String type = incident.getAgencyType();
                        String reporter = incident.getReporterName();
                        String createdAt = incident.getCreatedAt();
                        String date = createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : "--";
                        String time = createdAt != null && createdAt.length() >= 16 ? createdAt.substring(11, 16) : "--:--";
                        String address = incident.getLocationAddress();
                        mLatitude = incident.getLatitude();
                        mLongitude = incident.getLongitude();
                        String info = incident.getDescription();
                        String assignedName = incident.getAssignedOfficerName();

                        // --- Populate Views ---
                        detail_incidentCode.setText("Incident #" + code);
                        detail_status.setText("Status: " + (status != null ? status.toUpperCase() : "--"));
                        detail_incidentType.setText("Type: " + type);
                        detail_reporterName.setText("Reported by: " + (reporter != null ? reporter : "Unknown"));
                        detail_dateTime.setText("Date/Time: " + date + ", " + time);
                        detail_additionalInfo.setText("Additional Info: " + (info != null ? info : "N/A"));

                        detail_address_full.setText("Address: " + (address != null ? address : "N/A"));
                        String coordDisplay = (mLatitude != null && mLongitude != null)
                                ? String.format("%.6f, %.6f", mLatitude, mLongitude) : "N/A";
                        detail_coordinate_display.setText(coordDisplay);
                        detail_location_context.setText("Camarines Norte, Philippines");

                        // Populate History Field
                        if (status != null && status.equalsIgnoreCase("rejected")) {
                            detail_assignment_info.setText("Reason: Incident was deemed unsuitable for dispatch.");
                        } else if (assignedName != null && !assignedName.isEmpty()) {
                            detail_assignment_info.setText("Assigned Responder: " + assignedName);
                        } else {
                            detail_assignment_info.setText("Status: " + (status != null ? status : "--") + " (No responder assigned)");
                        }

                        // Handle media URLs
                        if (incident.getMediaUrls() != null && !incident.getMediaUrls().isEmpty()) {
                            Picasso.get().load(incident.getMediaUrls().get(0)).into(detail_incidentImage);
                        } else {
                            detail_incidentImage.setImageResource(R.drawable.placeholder_image);
                        }

                        // Update Map
                        updateMap();

                    } else {
                        Toast.makeText(AccomplishedDetailsActivity.this, "Incident data not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Database Error: " + throwable.getMessage());
                    Toast.makeText(AccomplishedDetailsActivity.this, "Failed to load details: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    return Unit.INSTANCE;
                }
        );
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        updateMap();
    }

    private void updateMap() {
        if (mMap == null) return;

        mMap.clear(); // Clear existing markers

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasPoints = false;

        // 1. Add Incident Marker (Red)
        LatLng incidentLoc = null;
        if (mLatitude != null && mLongitude != null && mLatitude != 0.0 && mLongitude != 0.0) {
            incidentLoc = new LatLng(mLatitude, mLongitude);
            mMap.addMarker(new MarkerOptions()
                    .position(incidentLoc)
                    .title("Incident Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            builder.include(incidentLoc);
            hasPoints = true;
        }

        // 2. Add User Marker (Blue)
        if (userLocation != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(userLocation)
                    .title("My Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            builder.include(userLocation);
            hasPoints = true;
        }

        // 3. Draw Route if both points exist
        if (userLocation != null && incidentLoc != null) {
            fetchAndDrawRoute(userLocation, incidentLoc);
        }

        // 4. Update Camera
        if (hasPoints) {
            try {
                LatLngBounds bounds = builder.build();
                int padding = 100; // offset from edges of the map in pixels
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            } catch (IllegalStateException e) {
                // Bounds might be empty or invalid
                if (incidentLoc != null) {
                     mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(incidentLoc, 15f));
                } else if (userLocation != null) {
                     mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f));
                }
            } catch (Exception e) {
                 Log.e(TAG, "Camera update failed", e);
            }
        }
    }

    private void fetchAndDrawRoute(LatLng origin, LatLng dest) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            String url = getDirectionsUrl(origin, dest);
            String data = "";
            try {
                data = downloadUrl(url);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching directions", e);
            }

            String finalData = data;
            handler.post(() -> {
                if (!finalData.isEmpty()) {
                    List<LatLng> path = parseDirections(finalData);
                    if (path != null && !path.isEmpty()) {
                        if (currentPolyline != null) {
                            currentPolyline.remove();
                        }
                        PolylineOptions opts = new PolylineOptions()
                                .addAll(path)
                                .color(Color.BLUE)
                                .width(10);
                        currentPolyline = mMap.addPolyline(opts);
                    }
                }
            });
        });
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=driving";
        String key = "key=" + BuildConfig.GOOGLE_MAPS_API_KEY;
        return "https://maps.googleapis.com/maps/api/directions/json?" + str_origin + "&" + str_dest + "&" + mode + "&" + key;
    }

    private String downloadUrl(String strUrl) throws Exception {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } finally {
            if (iStream != null) iStream.close();
            if (urlConnection != null) urlConnection.disconnect();
        }
        return data;
    }

    private List<LatLng> parseDirections(String jsonData) {
        List<LatLng> path = new ArrayList<>();
        try {
            JSONObject jObject = new JSONObject(jsonData);
            JSONArray routes = jObject.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String points = overviewPolyline.getString("points");
                path = decodePoly(points);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing directions", e);
        }
        return path;
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }
}