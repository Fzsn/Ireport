package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileFragment extends Fragment {

    private TextView profileFullName, profileEmail, profileAgency, profileLocation;
    private Button btnEditProfile, btnDeleteProfile, btnSignOut;

    private FirebaseAuth mAuth;
    private DatabaseReference respondersRef;
    private String currentUid;
    private String databaseKey; // Important: To store the random key (-Oew...)

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // 1. Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
            respondersRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Responders");
        }

        // 2. Initialize Views
        profileFullName = view.findViewById(R.id.profileFullName);
        profileEmail = view.findViewById(R.id.profileEmail);
        profileAgency = view.findViewById(R.id.profileAgency);
        profileLocation = view.findViewById(R.id.profileLocation);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnDeleteProfile = view.findViewById(R.id.btnDeleteProfile);
        btnSignOut = view.findViewById(R.id.btnSignOut);

        // 3. Load Data & Listeners
        loadResponderProfile();
        setupListeners();

        return view;
    }

    private void loadResponderProfile() {
        if (currentUid == null) return;

        // Query Responders where "userId" matches current Login UID
        respondersRef.orderByChild("userId").equalTo(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Loop through results (should be only 1)
                            for (DataSnapshot data : snapshot.getChildren()) {
                                databaseKey = data.getKey(); // Capture the key (-Oew...) for editing later

                                String name = data.child("fullName").getValue(String.class);
                                String email = data.child("email").getValue(String.class);
                                String agency = data.child("agency").getValue(String.class);
                                String loc = data.child("location").getValue(String.class);

                                profileFullName.setText(name != null ? name : "N/A");
                                profileEmail.setText(email != null ? email : "N/A");
                                profileAgency.setText(agency != null ? agency : "N/A");
                                profileLocation.setText(loc != null ? loc : "N/A");
                            }
                        } else {
                            profileFullName.setText("Profile Not Found");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Error loading profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupListeners() {
        btnSignOut.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(getActivity(), responderSignIn.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        btnEditProfile.setOnClickListener(v -> {
            if (databaseKey != null) {
                Intent intent = new Intent(getActivity(), EditProfileActivity.class);
                intent.putExtra("DB_KEY", databaseKey); // Pass the key so EditActivity knows which node to update
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Please wait for profile to load...", Toast.LENGTH_SHORT).show();
            }
        });

        btnDeleteProfile.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Delete Account")
                    .setMessage("Are you sure? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void deleteAccount() {
        if (databaseKey == null) return;

        // Delete from Database using the captured key
        respondersRef.child(databaseKey).removeValue().addOnSuccessListener(aVoid -> {
            // Delete Auth
            if (mAuth.getCurrentUser() != null) {
                mAuth.getCurrentUser().delete().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Account Deleted", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getActivity(), responderSignIn.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadResponderProfile(); // Reload data when returning from Edit screen
    }
}