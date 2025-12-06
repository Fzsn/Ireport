package com.example.iresponderapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MdrrmoReportFormActivity extends AppCompatActivity {

    private static final String TAG = "MdrrmoReportForm";

    private LinearLayout containerPatients;
    private Button btnAddPatient, btnSubmit;
    private Spinner spinnerNatureOfCall, spinnerEmergencyType, spinnerAreaType, spinnerFacilityType;
    private EditText editIncidentLocation, editFacilityName, editNarrative;

    // Time Fields
    private EditText timeCall, timeDispatch, timeScene, timeDeparture, timeFacility, timeHandover, timeClear, timeBase;

    private String incidentKey;
    private DatabaseReference mdrrmoReportsRef, incidentRef;
    private String currentResponderUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mdrrmo_report_form);

        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        if (incidentKey == null) { finish(); return; }

        mdrrmoReportsRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Reports").child("MDRRMO");
        incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Incidents_").child(incidentKey);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentResponderUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentResponderUid = "Unknown";
        }

        initUiElements();

        boolean isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        if (isEditMode) {
            btnSubmit.setText("Update Report");
            loadExistingReportData();
        } else {
            loadIncidentBasicInfo();
            addPatientCard(null); // Add 1 blank patient card by default
        }

        btnAddPatient.setOnClickListener(v -> addPatientCard(null));
        btnSubmit.setOnClickListener(v -> showConfirmationDialog());
    }

    private void initUiElements() {
        containerPatients = findViewById(R.id.containerPatients);
        btnAddPatient = findViewById(R.id.btnAddPatient);
        btnSubmit = findViewById(R.id.btnSubmitMdrrmoReport);

        spinnerNatureOfCall = findViewById(R.id.spinnerNatureOfCall);
        spinnerEmergencyType = findViewById(R.id.spinnerEmergencyType);
        spinnerAreaType = findViewById(R.id.spinnerAreaType);
        editIncidentLocation = findViewById(R.id.editIncidentLocation);
        editNarrative = findViewById(R.id.editNarrative);

        timeCall = findViewById(R.id.timeCall);
        timeDispatch = findViewById(R.id.timeDispatch);
        timeScene = findViewById(R.id.timeScene);
        timeDeparture = findViewById(R.id.timeDeparture);
        timeFacility = findViewById(R.id.timeFacility);
        timeHandover = findViewById(R.id.timeHandover);
        timeClear = findViewById(R.id.timeClear);
        timeBase = findViewById(R.id.timeBase);

        setupTimePicker(timeCall);
        setupTimePicker(timeDispatch);
        setupTimePicker(timeScene);
        setupTimePicker(timeDeparture);
        setupTimePicker(timeFacility);
        setupTimePicker(timeHandover);
        setupTimePicker(timeClear);
        setupTimePicker(timeBase);

        spinnerFacilityType = findViewById(R.id.spinnerFacilityType);
        editFacilityName = findViewById(R.id.editFacilityName);
    }

    private void setupTimePicker(EditText editText) {
        editText.setOnClickListener(v -> {
            Calendar mcurrentTime = Calendar.getInstance();
            int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mcurrentTime.get(Calendar.MINUTE);
            TimePickerDialog mTimePicker;
            mTimePicker = new TimePickerDialog(this, (timePicker, selectedHour, selectedMinute) -> {
                String time = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute);
                editText.setText(time);
            }, hour, minute, true);
            mTimePicker.setTitle("Select Time");
            mTimePicker.show();
        });
    }

    private void addPatientCard(DataSnapshot data) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_mdrrmo_patient_entry, containerPatients, false);

        TextView title = view.findViewById(R.id.patientHeaderTitle);
        title.setText("Patient " + (containerPatients.getChildCount() + 1));

        setVitalRowLabel(view, R.id.rowBP, "BP");
        setVitalRowLabel(view, R.id.rowPulseRate, "Pulse");
        setVitalRowLabel(view, R.id.rowRespRate, "Resp");
        setVitalRowLabel(view, R.id.rowTemp, "Temp");
        setVitalRowLabel(view, R.id.rowSaO2, "SaO2");
        setVitalRowLabel(view, R.id.rowCapRefillVital, "Cap Refill");
        setVitalRowLabel(view, R.id.rowPain, "Pain");
        setVitalRowLabel(view, R.id.rowGlucose, "Glucose");
        setVitalRowLabel(view, R.id.rowObsTime, "Obs Time");

        // --- Multi-Select: Body Parts ---
        setupMultiSelect(view.findViewById(R.id.editBodyParts), R.array.body_parts_options, "Select Affected Body Parts");

        // --- Multi-Select: Injury Type ---
        setupMultiSelect(view.findViewById(R.id.editInjuryType), R.array.injury_type_options, "Select Injury Types");

        view.findViewById(R.id.btnRemoveEntry).setOnClickListener(v -> containerPatients.removeView(view));

        if (data != null) fillPatientCard(view, data);

        containerPatients.addView(view);
    }

    // Helper to setup multi-select dialogs
    private void setupMultiSelect(EditText editText, int arrayResId, String title) {
        String[] itemsArray = getResources().getStringArray(arrayResId);
        boolean[] checkedItems = new boolean[itemsArray.length];
        ArrayList<Integer> userItems = new ArrayList<>();

        editText.setOnClickListener(v -> {
            // Re-calc checked state based on current text
            String currentText = editText.getText().toString();
            userItems.clear();
            for(int i=0; i<itemsArray.length; i++) {
                if(currentText.contains(itemsArray[i])) {
                    checkedItems[i] = true;
                    userItems.add(i);
                } else {
                    checkedItems[i] = false;
                }
            }

            AlertDialog.Builder mBuilder = new AlertDialog.Builder(MdrrmoReportFormActivity.this);
            mBuilder.setTitle(title);
            mBuilder.setMultiChoiceItems(itemsArray, checkedItems, (dialogInterface, position, isChecked) -> {
                if (isChecked) userItems.add(position); else userItems.remove((Integer.valueOf(position)));
            });
            mBuilder.setPositiveButton("OK", (dialogInterface, which) -> {
                String item = "";
                for (int i = 0; i < userItems.size(); i++) {
                    item += itemsArray[userItems.get(i)];
                    if (i != userItems.size() - 1) item += ", ";
                }
                editText.setText(item);
            });
            mBuilder.setNegativeButton("Dismiss", (dialog, i) -> dialog.dismiss());
            mBuilder.setNeutralButton("Clear All", (dialog, which) -> {
                for (int i = 0; i < checkedItems.length; i++) checkedItems[i] = false;
                userItems.clear();
                editText.setText("");
            });
            mBuilder.create().show();
        });
    }

    private void setVitalRowLabel(View parent, int rowId, String label) {
        View row = parent.findViewById(rowId);
        ((TextView) row.findViewById(R.id.lblParameter)).setText(label);
    }

    private void loadIncidentBasicInfo() {
        incidentRef.child("address").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && editIncidentLocation.getText().toString().isEmpty()) {
                    editIncidentLocation.setText(snapshot.getValue(String.class));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void loadExistingReportData() {
        mdrrmoReportsRef.child(incidentKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    editNarrative.setText(snapshot.child("narrative").getValue(String.class));
                    editIncidentLocation.setText(snapshot.child("incidentLocation").getValue(String.class));
                    editFacilityName.setText(snapshot.child("facilityName").getValue(String.class));

                    timeCall.setText(snapshot.child("time_call").getValue(String.class));
                    timeDispatch.setText(snapshot.child("time_dispatch").getValue(String.class));
                    timeScene.setText(snapshot.child("time_scene").getValue(String.class));
                    timeDeparture.setText(snapshot.child("time_depart").getValue(String.class));
                    timeFacility.setText(snapshot.child("time_facility").getValue(String.class));
                    timeHandover.setText(snapshot.child("time_handover").getValue(String.class));
                    timeClear.setText(snapshot.child("time_clear").getValue(String.class));
                    timeBase.setText(snapshot.child("time_base").getValue(String.class));

                    if (snapshot.child("patients").exists()) {
                        containerPatients.removeAllViews();
                        for (DataSnapshot p : snapshot.child("patients").getChildren()) {
                            addPatientCard(p);
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MdrrmoReportFormActivity.this, "Failed to load report.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Submit Report")
                .setMessage("Are you sure you want to submit this report?")
                .setPositiveButton("Yes, Submit", (dialog, which) -> submitReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitReport() {
        Map<String, Object> reportData = new HashMap<>();

        // --- CRITICAL FIX: Save Responder UID at the ROOT level ---
        reportData.put("incidentKey", incidentKey);
        reportData.put("responderUid", currentResponderUid); // Must be here for FormsFragment to find it
        reportData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
        // ---------------------------------------------------------

        reportData.put("natureOfCall", getSpinnerValue(spinnerNatureOfCall));
        reportData.put("emergencyType", getSpinnerValue(spinnerEmergencyType));
        reportData.put("areaType", getSpinnerValue(spinnerAreaType));
        reportData.put("incidentLocation", editIncidentLocation.getText().toString());
        reportData.put("narrative", editNarrative.getText().toString());
        reportData.put("facilityType", getSpinnerValue(spinnerFacilityType));
        reportData.put("facilityName", editFacilityName.getText().toString());

        reportData.put("time_call", timeCall.getText().toString());
        reportData.put("time_dispatch", timeDispatch.getText().toString());
        reportData.put("time_scene", timeScene.getText().toString());
        reportData.put("time_depart", timeDeparture.getText().toString());
        reportData.put("time_facility", timeFacility.getText().toString());
        reportData.put("time_handover", timeHandover.getText().toString());
        reportData.put("time_clear", timeClear.getText().toString());
        reportData.put("time_base", timeBase.getText().toString());

        List<Map<String, Object>> patientsList = new ArrayList<>();
        for (int i = 0; i < containerPatients.getChildCount(); i++) {
            patientsList.add(scrapePatientData(containerPatients.getChildAt(i)));
        }
        reportData.put("patients", patientsList);

        mdrrmoReportsRef.child(incidentKey).setValue(reportData)
                .addOnSuccessListener(aVoid -> markIncidentAsCompleted())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save.", Toast.LENGTH_SHORT).show());
    }

    private Map<String, Object> scrapePatientData(View view) {
        Map<String, Object> p = new HashMap<>();

        p.put("name", getText(view, R.id.editPatientName));
        p.put("age", getText(view, R.id.editPatientAge));
        p.put("sex", getText(view, R.id.editPatientSex));
        p.put("address", getText(view, R.id.editPatientAddress));
        p.put("nextOfKin", getText(view, R.id.editNextOfKin));

        p.put("chiefComplaint", getText(view, R.id.editChiefComplaint));
        p.put("c_spine", getSpinnerValue(view, R.id.spinnerCSpine));
        p.put("airway", getSpinnerValue(view, R.id.spinnerAirway));
        p.put("breathing", getSpinnerValue(view, R.id.spinnerBreathing));
        p.put("pulse", getSpinnerValue(view, R.id.spinnerPulse));
        p.put("skin", getSpinnerValue(view, R.id.spinnerSkin));
        p.put("loc", getSpinnerValue(view, R.id.spinnerLOC));
        p.put("consciousness", getSpinnerValue(view, R.id.spinnerConsciousness));
        p.put("cap_refill", getSpinnerValue(view, R.id.spinnerCapRefill));

        p.put("signs", getText(view, R.id.editSigns));
        p.put("allergies", getText(view, R.id.editAllergies));
        p.put("meds", getText(view, R.id.editMeds));
        p.put("history", getText(view, R.id.editHistory));
        p.put("oral", getText(view, R.id.editOral));
        p.put("events", getText(view, R.id.editEvents));

        p.put("obs_time", scrapeVitalRow(view, R.id.rowObsTime));
        p.put("bp", scrapeVitalRow(view, R.id.rowBP));
        p.put("pulse_rate", scrapeVitalRow(view, R.id.rowPulseRate));
        p.put("resp_rate", scrapeVitalRow(view, R.id.rowRespRate));
        p.put("temp", scrapeVitalRow(view, R.id.rowTemp));
        p.put("spo2", scrapeVitalRow(view, R.id.rowSaO2));
        p.put("cap_vital", scrapeVitalRow(view, R.id.rowCapRefillVital));
        p.put("pain", scrapeVitalRow(view, R.id.rowPain));
        p.put("glucose", scrapeVitalRow(view, R.id.rowGlucose));

        p.put("gcs_eye", getText(view, R.id.editGcsEye));
        p.put("gcs_verbal", getText(view, R.id.editGcsVerbal));
        p.put("gcs_motor", getText(view, R.id.editGcsMotor));
        p.put("gcs_total", getText(view, R.id.editGcsTotal));

        p.put("manage_airway", getSpinnerValue(view, R.id.spinnerManageAirway));
        p.put("manage_circ", getSpinnerValue(view, R.id.spinnerManageCirc));
        p.put("manage_wound", getSpinnerValue(view, R.id.spinnerManageWound));
        p.put("manage_immob", getSpinnerValue(view, R.id.spinnerManageImmob));
        p.put("manage_other", getSpinnerValue(view, R.id.spinnerManageOther));

        // Multi-select Fields
        p.put("injury_type", getText(view, R.id.editInjuryType));
        p.put("affected_body_parts", getText(view, R.id.editBodyParts));
        p.put("patient_narrative", getText(view, R.id.editPatientNarrative));

        return p;
    }

    private void fillPatientCard(View view, DataSnapshot data) {
        setText(view, R.id.editPatientName, data.child("name").getValue(String.class));
        setText(view, R.id.editPatientAge, data.child("age").getValue(String.class));
        setText(view, R.id.editPatientSex, data.child("sex").getValue(String.class));
        setText(view, R.id.editPatientAddress, data.child("address").getValue(String.class));
        setText(view, R.id.editNextOfKin, data.child("nextOfKin").getValue(String.class));

        setText(view, R.id.editChiefComplaint, data.child("chiefComplaint").getValue(String.class));
        setText(view, R.id.editPatientNarrative, data.child("patient_narrative").getValue(String.class));

        setText(view, R.id.editSigns, data.child("signs").getValue(String.class));
        setText(view, R.id.editAllergies, data.child("allergies").getValue(String.class));
        setText(view, R.id.editMeds, data.child("meds").getValue(String.class));
        setText(view, R.id.editHistory, data.child("history").getValue(String.class));
        setText(view, R.id.editOral, data.child("oral").getValue(String.class));
        setText(view, R.id.editEvents, data.child("events").getValue(String.class));

        setText(view, R.id.editGcsEye, data.child("gcs_eye").getValue(String.class));
        setText(view, R.id.editGcsVerbal, data.child("gcs_verbal").getValue(String.class));
        setText(view, R.id.editGcsMotor, data.child("gcs_motor").getValue(String.class));
        setText(view, R.id.editGcsTotal, data.child("gcs_total").getValue(String.class));

        fillVitalRow(view, R.id.rowBP, data.child("bp"));
        fillVitalRow(view, R.id.rowPulseRate, data.child("pulse_rate"));
        fillVitalRow(view, R.id.rowRespRate, data.child("resp_rate"));
        fillVitalRow(view, R.id.rowSaO2, data.child("sao2"));
        fillVitalRow(view, R.id.rowTemp, data.child("temp"));
        fillVitalRow(view, R.id.rowCapRefillVital, data.child("cap_vital"));
        fillVitalRow(view, R.id.rowGlucose, data.child("glucose"));
        fillVitalRow(view, R.id.rowPain, data.child("pain"));

        setText(view, R.id.editBodyParts, data.child("affected_body_parts").getValue(String.class));
        setText(view, R.id.editInjuryType, data.child("injury_type").getValue(String.class));
    }

    private String getText(View parent, int id) {
        EditText et = parent.findViewById(id);
        return et != null ? et.getText().toString() : "";
    }

    private void setText(View parent, int id, String val) {
        EditText et = parent.findViewById(id);
        if (et != null && val != null) et.setText(val);
    }

    private Map<String, String> scrapeVitalRow(View parent, int rowId) {
        View row = parent.findViewById(rowId);
        Map<String, String> v = new HashMap<>();
        v.put("t1", ((EditText) row.findViewById(R.id.inputTime1)).getText().toString());
        v.put("t2", ((EditText) row.findViewById(R.id.inputTime2)).getText().toString());
        v.put("t3", ((EditText) row.findViewById(R.id.inputTime3)).getText().toString());
        return v;
    }

    private void fillVitalRow(View parent, int rowId, DataSnapshot data) {
        if (!data.exists()) return;
        View row = parent.findViewById(rowId);
        ((EditText) row.findViewById(R.id.inputTime1)).setText(data.child("t1").getValue(String.class));
        ((EditText) row.findViewById(R.id.inputTime2)).setText(data.child("t2").getValue(String.class));
        ((EditText) row.findViewById(R.id.inputTime3)).setText(data.child("t3").getValue(String.class));
    }

    private String getSpinnerValue(Object viewOrSpinner) {
        Spinner s;
        if (viewOrSpinner instanceof View) {
            // Should not happen with current logic, but safe fallback
            return "";
        } else {
            s = (Spinner) viewOrSpinner;
        }
        if (s != null && s.getSelectedItem() != null) return s.getSelectedItem().toString();
        return "";
    }

    private String getSpinnerValue(View parent, int id) {
        Spinner s = parent.findViewById(id);
        if (s != null && s.getSelectedItem() != null) return s.getSelectedItem().toString();
        return "";
    }

    private void markIncidentAsCompleted() {
        incidentRef.child("Status").setValue("Completed")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Report Submitted!", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
}