package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iresponderapp.supabase.ResponderProfile;
import com.example.iresponderapp.supabase.SupabaseAuthRepository;
import com.example.iresponderapp.supabase.SupabaseResponderProfileRepository;

import kotlin.Unit;

public class ProfileFragment extends Fragment {

    private TextView profileFullName, profileEmail, profileAgency, profileLocation, profileRole, profilePhone;
    private Button btnEditProfile, btnSignOut;
    private LinearLayout loadingContainer;

    private SupabaseAuthRepository authRepository;
    private SupabaseResponderProfileRepository profileRepository;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // 1. Initialize Supabase repositories
        IreportApp app = (IreportApp) requireActivity().getApplication();
        authRepository = (SupabaseAuthRepository) app.getAuthRepository();
        profileRepository = (SupabaseResponderProfileRepository) app.getResponderProfileRepository();

        // 2. Initialize Views
        profileFullName = view.findViewById(R.id.profileFullName);
        profileEmail = view.findViewById(R.id.profileEmail);
        profileAgency = view.findViewById(R.id.profileAgency);
        profileLocation = view.findViewById(R.id.profileLocation);
        profileRole = view.findViewById(R.id.profileRole);
        profilePhone = view.findViewById(R.id.profilePhone);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnSignOut = view.findViewById(R.id.btnSignOut);
        loadingContainer = view.findViewById(R.id.loadingContainer);

        // 3. Load Data & Listeners
        loadResponderProfile();
        setupListeners();

        return view;
    }

    private void loadResponderProfile() {
        if (loadingContainer != null) {
            loadingContainer.setVisibility(View.VISIBLE);
        }
        profileRepository.loadCurrentProfileAsync(
                profile -> {
                    if (loadingContainer != null) {
                        loadingContainer.setVisibility(View.GONE);
                    }
                    if (profile != null) {
                        profileFullName.setText(profile.getDisplayName() != null ? profile.getDisplayName() : "N/A");
                        profileEmail.setText(profile.getEmail() != null ? profile.getEmail() : "N/A");
                        
                        // Map agency_id to agency name (adjust IDs based on your agencies table)
                        String agencyName = "N/A";
                        if (profile.getAgencyId() != null) {
                            switch (profile.getAgencyId()) {
                                case 1: agencyName = "PNP"; break;
                                case 2: agencyName = "BFP"; break;
                                case 3: agencyName = "MDRRMO"; break;
                            }
                        }
                        profileAgency.setText(agencyName);
                        
                        // Role
                        profileRole.setText(profile.getRole() != null ? profile.getRole() : "N/A");
                        
                        // Phone
                        profilePhone.setText(profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "N/A");
                        
                        // Location: fetch station details if station_id is present
                        if (profile.getStationId() != null) {
                            profileLocation.setText("Loading...");
                            profileRepository.loadStationAsync(
                                    profile.getStationId(),
                                    station -> {
                                        String locationText = "N/A";
                                        if (station != null) {
                                            StringBuilder sb = new StringBuilder(station.getName());
                                            // Add municipality or address for location context
                                            if (station.getMunicipality() != null && !station.getMunicipality().isEmpty()) {
                                                sb.append("\n").append(station.getMunicipality());
                                            }
                                            if (station.getAddress() != null && !station.getAddress().isEmpty()) {
                                                sb.append("\n").append(station.getAddress());
                                            }
                                            locationText = sb.toString();
                                        }
                                        profileLocation.setText(locationText);
                                        return Unit.INSTANCE;
                                    },
                                    error -> {
                                        profileLocation.setText("Station #" + profile.getStationId());
                                        return Unit.INSTANCE;
                                    }
                            );
                        } else {
                            profileLocation.setText("Not assigned");
                        }
                    } else {
                        profileFullName.setText("Profile Not Found");
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    if (loadingContainer != null) {
                        loadingContainer.setVisibility(View.GONE);
                    }
                    Toast.makeText(getContext(), "Error loading profile", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
        );
    }

    private void setupListeners() {
        btnSignOut.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to sign out?")
                    .setPositiveButton("Sign Out", (dialog, which) -> performSignOut())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
        });
    }

    private void performSignOut() {
        authRepository.signOutAsync(
                unit -> {
                    Toast.makeText(getContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(getActivity(), responderSignIn.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Toast.makeText(getContext(), "Sign out failed", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        loadResponderProfile(); // Reload data when returning from Edit screen
    }
}