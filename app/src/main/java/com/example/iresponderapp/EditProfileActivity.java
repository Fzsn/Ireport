package com.example.iresponderapp;

import android.app.ProgressDialog;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.iresponderapp.supabase.ResponderProfile;
import com.example.iresponderapp.supabase.SupabaseResponderProfileRepository;

import kotlin.Unit;

public class EditProfileActivity extends AppCompatActivity {

    private EditText editFullName, editContactNumber, editAgency, editLocation;
    private Button btnSaveProfile;
    private LinearLayout loadingContainer;
    private ProgressDialog progressDialog;

    private SupabaseResponderProfileRepository profileRepository;
    private ResponderProfile currentProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Initialize Supabase repository
        IreportApp app = (IreportApp) getApplication();
        profileRepository = (SupabaseResponderProfileRepository) app.getResponderProfileRepository();

        loadingContainer = findViewById(R.id.loadingContainer);
        editFullName = findViewById(R.id.editFullName);
        editContactNumber = findViewById(R.id.editContactNumber);
        editAgency = findViewById(R.id.editAgency);
        editLocation = findViewById(R.id.editLocation);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        
        // Initially disable save button until data is loaded
        btnSaveProfile.setEnabled(false);

        loadCurrentData();

        btnSaveProfile.setOnClickListener(v -> validateAndSave());
    }

    private void validateAndSave() {
        String fullName = editFullName.getText().toString().trim();
        String phone = editContactNumber.getText().toString().trim();
        String agency = editAgency.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(fullName)) {
            editFullName.setError("Full name is required");
            editFullName.requestFocus();
            return;
        }

        if (fullName.length() < 3) {
            editFullName.setError("Name must be at least 3 characters");
            editFullName.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(phone) && phone.length() < 10) {
            editContactNumber.setError("Enter a valid phone number");
            editContactNumber.requestFocus();
            return;
        }

        String agencyUpper = agency.toUpperCase();
        if (!agencyUpper.equals("PNP") && !agencyUpper.equals("BFP") && !agencyUpper.equals("MDRRMO") && !agency.isEmpty()) {
            editAgency.setError("Agency must be PNP, BFP, or MDRRMO");
            editAgency.requestFocus();
            return;
        }

        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Confirm Changes")
                .setMessage("Are you sure you want to update your profile?")
                .setPositiveButton("Save", (dialog, which) -> saveChanges())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadCurrentData() {
        loadingContainer.setVisibility(View.VISIBLE);
        btnSaveProfile.setEnabled(false);
        
        profileRepository.loadCurrentProfileAsync(
                profile -> {
                    loadingContainer.setVisibility(View.GONE);
                    if (profile != null) {
                        currentProfile = profile;
                        editFullName.setText(profile.getDisplayName());
                        editContactNumber.setText(profile.getPhoneNumber());
                        // Map agency_id to agency name
                        String agencyName = "";
                        if (profile.getAgencyId() != null) {
                            switch (profile.getAgencyId()) {
                                case 1: agencyName = "PNP"; break;
                                case 2: agencyName = "BFP"; break;
                                case 3: agencyName = "MDRRMO"; break;
                            }
                        }
                        editAgency.setText(agencyName);
                        editAgency.setEnabled(false); // Agency should not be editable
                        // Location - use station_id to fetch station details if available
                        if (profile.getStationId() != null) {
                            editLocation.setText("Loading station...");
                            profileRepository.loadStationAsync(
                                    profile.getStationId(),
                                    station -> {
                                        String locationText = "N/A";
                                        if (station != null) {
                                            if (station.getAddress() != null && !station.getAddress().isEmpty()) {
                                                locationText = station.getName() + " - " + station.getAddress();
                                            } else {
                                                locationText = station.getName();
                                            }
                                        }
                                        editLocation.setText(locationText);
                                        return Unit.INSTANCE;
                                    },
                                    error -> {
                                        editLocation.setText("Station " + profile.getStationId());
                                        return Unit.INSTANCE;
                                    }
                            );
                        } else {
                            editLocation.setText("N/A");
                        }
                        editLocation.setEnabled(false); // Location should not be editable
                        btnSaveProfile.setEnabled(true);
                    } else {
                        Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show();
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    loadingContainer.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load profile: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
        );
    }

    private void saveChanges() {
        if (currentProfile == null) {
            Toast.makeText(this, "Profile not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Saving changes...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Integer agencyId = currentProfile.getAgencyId(); // Keep original agency
        
        ResponderProfile updatedProfile = new ResponderProfile(
                currentProfile.getId(),
                editFullName.getText().toString().trim(),
                currentProfile.getEmail(),
                currentProfile.getRole(),
                agencyId,
                currentProfile.getStationId(),
                editContactNumber.getText().toString().trim(),
                currentProfile.getStatus()
        );

        profileRepository.updateProfileAsync(
                updatedProfile,
                unit -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(this, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                    return Unit.INSTANCE;
                },
                throwable -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(this, "Update Failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    return Unit.INSTANCE;
                }
        );
    }
}