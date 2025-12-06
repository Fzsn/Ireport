package com.example.iresponderapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BfpReportFormActivity extends AppCompatActivity {

    private static final String TAG = "BfpReportForm";

    private TextView formIncidentType, formIncidentDateTime, formReportedBy, formIncidentDescription;
    private TextView formIncidentAddress, formCoordinates;

    private EditText editFireLocation, editAreaOwnership, editClassOfFire, editRootCause, editPeopleInjured;
    private Button btnSubmit;

    private String incidentKey;
    private DatabaseReference incidentRef;
    private DatabaseReference bfpReportsRef;
    private String currentResponderUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bfp_report_form);

        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        if (incidentKey == null) {
            Toast.makeText(this, "Incident ID missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Incidents_").child(incidentKey);
        bfpReportsRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Reports").child("BFP");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentResponderUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentResponderUid = "Unknown";
        }

        initUiElements();

        // Always load header data
        loadIncidentData();

        // Check for Edit Mode
        boolean isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        if (isEditMode) {
            btnSubmit.setText("Update Report");
            loadExistingReportData();
        }

        btnSubmit.setOnClickListener(v -> showConfirmationDialog());
    }

    private void initUiElements() {
        formIncidentType = findViewById(R.id.formIncidentType);
        formIncidentDateTime = findViewById(R.id.formIncidentDateTime);
        formReportedBy = findViewById(R.id.formReportedBy);
        formIncidentDescription = findViewById(R.id.formIncidentDescription);
        formIncidentAddress = findViewById(R.id.formIncidentAddress);
        formCoordinates = findViewById(R.id.formCoordinates);

        editFireLocation = findViewById(R.id.editFireLocation);
        editAreaOwnership = findViewById(R.id.editAreaOwnership);
        editClassOfFire = findViewById(R.id.editClassOfFire);
        editRootCause = findViewById(R.id.editRootCause);
        editPeopleInjured = findViewById(R.id.editPeopleInjured);

        btnSubmit = findViewById(R.id.btnSubmitBfpReport);
    }

    private void loadIncidentData() {
        incidentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String type = snapshot.child("incidentType").getValue(String.class);
                    String date = snapshot.child("date").getValue(String.class);
                    String time = snapshot.child("Time").getValue(String.class);
                    String reporter = snapshot.child("reporterName").getValue(String.class);
                    String address = snapshot.child("address").getValue(String.class);
                    String info = snapshot.child("additionalInfo").getValue(String.class);
                    String lat = snapshot.child("latitude").getValue(String.class);
                    String lon = snapshot.child("longitude").getValue(String.class);

                    formIncidentType.setText(type != null ? type : "Fire");
                    formIncidentDateTime.setText((date != null ? date : "") + "\n" + (time != null ? time : ""));
                    formReportedBy.setText(reporter != null ? reporter : "N/A");
                    formIncidentDescription.setText(info != null ? info : "N/A");
                    formIncidentAddress.setText(address != null ? address : "N/A");
                    formCoordinates.setText((lat != null ? lat : "") + ", " + (lon != null ? lon : ""));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    private void loadExistingReportData() {
        bfpReportsRef.child(incidentKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    editFireLocation.setText(snapshot.child("fireLocation").getValue(String.class));
                    editAreaOwnership.setText(snapshot.child("areaOwnership").getValue(String.class));
                    editClassOfFire.setText(snapshot.child("classOfFire").getValue(String.class));
                    editRootCause.setText(snapshot.child("rootCause").getValue(String.class));
                    editPeopleInjured.setText(snapshot.child("peopleInjured").getValue(String.class));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    private void showConfirmationDialog() {
        if (editFireLocation.getText().toString().trim().isEmpty()) {
            editFireLocation.setError("Required");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Submit Report")
                .setMessage("Are you sure you want to submit this report?")
                .setPositiveButton("Yes, Submit", (dialog, which) -> submitBfpReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitBfpReport() {
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("incidentKey", incidentKey);
        reportData.put("responderUid", currentResponderUid);
        reportData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        reportData.put("fireLocation", editFireLocation.getText().toString().trim());
        reportData.put("areaOwnership", editAreaOwnership.getText().toString().trim());
        reportData.put("classOfFire", editClassOfFire.getText().toString().trim());
        reportData.put("rootCause", editRootCause.getText().toString().trim());
        reportData.put("peopleInjured", editPeopleInjured.getText().toString().trim());

        bfpReportsRef.child(incidentKey).setValue(reportData)
                .addOnSuccessListener(aVoid -> {
                    markIncidentAsCompleted();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to submit report.", Toast.LENGTH_SHORT).show();
                });
    }

    private void markIncidentAsCompleted() {
        incidentRef.child("Status").setValue("Completed")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Report Submitted!", Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}