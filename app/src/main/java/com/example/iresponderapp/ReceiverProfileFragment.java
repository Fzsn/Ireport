package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ReceiverProfileFragment extends Fragment {

    private static final String TAG = "ReceiverProfileFragment";

    // UI Elements
    private TextView profileFullName, profileEmail, profileAgency, profileLocation;
    private Button btnEditProfile, btnDeleteProfile, btnSignOut;

    // Firebase components
    private FirebaseAuth mAuth;
    private DatabaseReference receiverRef;
    private String currentReceiverId;

    public ReceiverProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_receiver_profile, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        // Check if user is logged in
        if (user == null) {
            // Handle sign-out state if necessary
            return view;
        }
        currentReceiverId = user.getUid();

        // Initialize Database reference to the current receiver's node
        receiverRef = FirebaseDatabase.getInstance().getReference("IresponderApp")
                .child("Receivers")
                .child(currentReceiverId);

        // --- 1. Initialize Views ---
        profileFullName = view.findViewById(R.id.profileFullName);
        profileEmail = view.findViewById(R.id.profileEmail);
        profileAgency = view.findViewById(R.id.profileAgency);
        profileLocation = view.findViewById(R.id.profileLocation);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnDeleteProfile = view.findViewById(R.id.btnDeleteProfile);
        btnSignOut = view.findViewById(R.id.btnSignOut);

        // --- 2. Load Data ---
        loadReceiverProfile();

        // --- 3. Setup Listeners ---
        setupListeners();

        return view;
    }

    private void loadReceiverProfile() {
        receiverRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Extract data based on the database structure (image_2f7722.png)
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String agency = snapshot.child("agency").getValue(String.class);
                    String location = snapshot.child("location").getValue(String.class);

                    // Populate UI
                    profileFullName.setText(fullName);
                    profileEmail.setText(email);
                    profileAgency.setText(agency);
                    profileLocation.setText(location);
                } else {
                    Toast.makeText(getContext(), "Profile data not found.", Toast.LENGTH_SHORT).show();
                    // Force sign-out if profile doesn't exist but user is authenticated
                    mAuth.signOut();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to read profile data: " + error.getMessage());
                Toast.makeText(getContext(), "Error loading profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupListeners() {

        btnSignOut.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getContext(), "Signed out successfully.", Toast.LENGTH_SHORT).show();
            // TODO: Navigate user back to the Login/Main Activity
            // Example: startActivity(new Intent(getActivity(), LoginActivity.class));
            getActivity().finish();
        });

        btnEditProfile.setOnClickListener(v -> {
            // TODO: Implement logic to open a new EditProfileActivity/Fragment
            Toast.makeText(getContext(), "Opening Edit Profile Screen...", Toast.LENGTH_SHORT).show();
        });

        btnDeleteProfile.setOnClickListener(v -> {
            showDeleteConfirmationDialog();
        });
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Profile")
                .setMessage("WARNING: Deleting your profile is permanent and cannot be undone. Are you sure you want to delete your account?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> deleteReceiverAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReceiverAccount() {
        // Step 1: Delete database entry
        receiverRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Database profile entry deleted.");

                    // Step 2: Delete Firebase Authentication user
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(getContext(), "Account deleted successfully.", Toast.LENGTH_LONG).show();
                                        // TODO: Navigate user back to the Login/Main Activity
                                        getActivity().finish();
                                    } else {
                                        Log.e(TAG, "Failed to delete Auth user: " + task.getException());
                                        // Handle re-authentication requirement if necessary
                                        Toast.makeText(getContext(), "Re-authentication required to delete account.", Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete database entry: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to delete profile data.", Toast.LENGTH_LONG).show();
                });
    }
}