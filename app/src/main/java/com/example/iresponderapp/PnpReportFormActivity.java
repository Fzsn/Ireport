package com.example.iresponderapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PnpReportFormActivity extends AppCompatActivity {

    private static final String TAG = "PnpReportForm";

    // Read-only UI elements
    private TextView formIncidentType, formIncidentDate, formReportedBy, formIncidentAddress, formIncidentDescription;

    // Containers and Buttons
    private LinearLayout containerSuspects;
    private LinearLayout containerVictims;
    private Button btnAddSuspect, btnAddVictim;

    private EditText editIncidentNarrative;
    private Button btnSubmit;

    private String incidentKey;
    private DatabaseReference incidentRef;
    private DatabaseReference pnpReportsRef;
    private String currentResponderUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pnp_report_form);

        // --- 1. Firebase Init ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        if (incidentKey == null) {
            Toast.makeText(this, "Error: Incident Key Missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Incidents_").child(incidentKey);
        pnpReportsRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Reports").child("PNP");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentResponderUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentResponderUid = "Unknown";
        }

        // --- 2. Initialize UI ---
        initUiElements();

        // --- 3. Load Header Data (Always required) ---
        loadIncidentHeaderData();

        // --- 4. Determine Mode (Edit vs New) ---
        boolean isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);

        if (isEditMode) {
            // EDIT MODE: Change button text and load saved report data
            btnSubmit.setText("Update Report");
            loadExistingReportData();
        } else {
            // NEW REPORT MODE: Add default blank cards
            addPersonCard(containerSuspects, "Suspect");
            addPersonCard(containerVictims, "Victim");
        }

        // --- 5. Setup Listeners ---
        btnAddSuspect.setOnClickListener(v -> addPersonCard(containerSuspects, "Suspect"));
        btnAddVictim.setOnClickListener(v -> addPersonCard(containerVictims, "Victim"));

        btnSubmit.setOnClickListener(v -> showConfirmationDialog());
    }

    private void initUiElements() {
        formIncidentType = findViewById(R.id.formIncidentType);
        formIncidentDate = findViewById(R.id.formIncidentDate);
        formReportedBy = findViewById(R.id.formReportedBy);
        formIncidentAddress = findViewById(R.id.formIncidentAddress);
        formIncidentDescription = findViewById(R.id.formIncidentDescription);

        containerSuspects = findViewById(R.id.containerSuspects);
        containerVictims = findViewById(R.id.containerVictims);
        btnAddSuspect = findViewById(R.id.btnAddSuspect);
        btnAddVictim = findViewById(R.id.btnAddVictim);

        editIncidentNarrative = findViewById(R.id.editIncidentNarrative);
        btnSubmit = findViewById(R.id.btnSubmitPnpReport);
    }

    // --- Dynamic Card Logic (Adds a blank card) ---
    private void addPersonCard(LinearLayout container, String title) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);

        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title + " Data");

        // Setup Remove Button Logic
        View btnRemove = view.findViewById(R.id.btnRemoveEntry);
        btnRemove.setOnClickListener(v -> container.removeView(view));

        container.addView(view);
    }

    // --- Load Incident Header (Read-Only) ---
    private void loadIncidentHeaderData() {
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

                    formIncidentType.setText("Incident Type: " + (type != null ? type : "N/A"));
                    formIncidentDate.setText("Date & Time: " + (date != null ? date : "") + " " + (time != null ? time : ""));
                    formReportedBy.setText("Reported by: " + (reporter != null ? reporter : "N/A"));
                    formIncidentAddress.setText("Address: " + (address != null ? address : "N/A"));
                    formIncidentDescription.setText("Description: " + (info != null ? info : "N/A"));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    // --- EDIT MODE: Load Existing Report Data ---
    private void loadExistingReportData() {
        pnpReportsRef.child(incidentKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Pre-fill Narrative
                    String narrative = snapshot.child("narrative").getValue(String.class);
                    if (editIncidentNarrative != null) editIncidentNarrative.setText(narrative);

                    // Pre-fill Suspects
                    if (snapshot.child("suspects").exists()) {
                        containerSuspects.removeAllViews(); // Clear default empty cards
                        for (DataSnapshot suspect : snapshot.child("suspects").getChildren()) {
                            addPersonCardWithData(containerSuspects, "Suspect", suspect);
                        }
                    }

                    // Pre-fill Victims
                    if (snapshot.child("victims").exists()) {
                        containerVictims.removeAllViews(); // Clear default empty cards
                        for (DataSnapshot victim : snapshot.child("victims").getChildren()) {
                            addPersonCardWithData(containerVictims, "Victim", victim);
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(PnpReportFormActivity.this, "Failed to load saved report.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Helper to Add Card & Fill Data ---
    private void addPersonCardWithData(LinearLayout container, String title, DataSnapshot data) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);

        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title + " Data");

        // Fill fields from Firebase snapshot
        ((EditText) view.findViewById(R.id.editFirstName)).setText(data.child("firstName").getValue(String.class));
        ((EditText) view.findViewById(R.id.editMiddleName)).setText(data.child("middleName").getValue(String.class));
        ((EditText) view.findViewById(R.id.editLastName)).setText(data.child("lastName").getValue(String.class));
        ((EditText) view.findViewById(R.id.editAddress)).setText(data.child("address").getValue(String.class));
        ((EditText) view.findViewById(R.id.editOccupation)).setText(data.child("occupation").getValue(String.class));
        ((EditText) view.findViewById(R.id.editStatus)).setText(data.child("status").getValue(String.class));

        // Remove logic
        view.findViewById(R.id.btnRemoveEntry).setOnClickListener(v -> container.removeView(view));

        container.addView(view);
    }

    // --- Submission Logic ---
    private void showConfirmationDialog() {
        if (editIncidentNarrative.getText().toString().trim().isEmpty()) {
            editIncidentNarrative.setError("Narrative is required");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Submission")
                .setMessage("Are you sure you want to submit this report? The incident status will be set to COMPLETED.")
                .setPositiveButton("Yes, Submit", (dialog, which) -> submitPnpReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitPnpReport() {
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("incidentKey", incidentKey);
        reportData.put("responderUid", currentResponderUid);
        reportData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        reportData.put("narrative", editIncidentNarrative.getText().toString().trim());

        // Collect Data from Dynamic Views
        reportData.put("suspects", collectPersonData(containerSuspects));
        reportData.put("victims", collectPersonData(containerVictims));

        // Save to Firebase
        pnpReportsRef.child(incidentKey).setValue(reportData)
                .addOnSuccessListener(aVoid -> markIncidentAsCompleted())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to submit report.", Toast.LENGTH_SHORT).show());
    }

    // Loops through the layout container and extracts data from each card
    private List<Map<String, String>> collectPersonData(LinearLayout container) {
        List<Map<String, String>> personList = new ArrayList<>();

        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);

            EditText fName = card.findViewById(R.id.editFirstName);
            EditText mName = card.findViewById(R.id.editMiddleName);
            EditText lName = card.findViewById(R.id.editLastName);
            EditText address = card.findViewById(R.id.editAddress);
            EditText occupation = card.findViewById(R.id.editOccupation);
            EditText status = card.findViewById(R.id.editStatus);

            Map<String, String> personData = new HashMap<>();
            personData.put("firstName", fName.getText().toString().trim());
            personData.put("middleName", mName.getText().toString().trim());
            personData.put("lastName", lName.getText().toString().trim());
            personData.put("address", address.getText().toString().trim());
            personData.put("occupation", occupation.getText().toString().trim());
            personData.put("status", status.getText().toString().trim());

            // Only add to the list if at least a name is provided
            if (!personData.get("firstName").isEmpty() || !personData.get("lastName").isEmpty()) {
                personList.add(personData);
            }
        }
        return personList;
    }

    private void markIncidentAsCompleted() {
        incidentRef.child("Status").setValue("Completed")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Report Submitted & Incident Completed!", Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}