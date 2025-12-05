package com.example.iresponderapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class EditReceiverProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private EditText editFullName, editContactNumber, editAgency, editLocation;
    private Button btnSaveProfile;

    private FirebaseAuth mAuth;
    private DatabaseReference receiverRef;
    private String currentReceiverId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_receiver_profile);

        // --- 1. Initialize Views ---
        editFullName = findViewById(R.id.editFullName);
        editContactNumber = findViewById(R.id.editContactNumber);
        editAgency = findViewById(R.id.editAgency);
        editLocation = findViewById(R.id.editLocation);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        // --- 2. Initialize Firebase ---
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "Authentication error. Please sign in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        currentReceiverId = user.getUid();

        // Reference to the current receiver's database node
        receiverRef = FirebaseDatabase.getInstance().getReference("IresponderApp")
                .child("Receivers")
                .child(currentReceiverId);

        // --- 3. Load existing data to pre-fill the form ---
        loadCurrentProfileData();

        // --- 4. Setup Save Listener ---
        btnSaveProfile.setOnClickListener(v -> saveProfileChanges());
    }

    private void loadCurrentProfileData() {
        receiverRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Pre-fill fields with existing data
                    editFullName.setText(snapshot.child("fullName").getValue(String.class));
                    editContactNumber.setText(snapshot.child("contactNumber").getValue(String.class));
                    editAgency.setText(snapshot.child("agency").getValue(String.class));
                    editLocation.setText(snapshot.child("location").getValue(String.class));
                } else {
                    Toast.makeText(EditReceiverProfileActivity.this, "Existing data not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to load current profile: " + error.getMessage());
                Toast.makeText(EditReceiverProfileActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfileChanges() {
        String newFullName = editFullName.getText().toString().trim();
        String newContactNumber = editContactNumber.getText().toString().trim();
        String newAgency = editAgency.getText().toString().trim();
        String newLocation = editLocation.getText().toString().trim();

        if (newFullName.isEmpty() || newContactNumber.isEmpty() || newAgency.isEmpty() || newLocation.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare the updates map
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", newFullName);
        updates.put("contactNumber", newContactNumber);
        updates.put("agency", newAgency);
        updates.put("location", newLocation);

        // Push updates to Firebase
        receiverRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditReceiverProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_LONG).show();
                    // Close the activity and return to the profile fragment
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save profile changes: " + e.getMessage());
                    Toast.makeText(EditReceiverProfileActivity.this, "Failed to save changes. Please try again.", Toast.LENGTH_LONG).show();
                });
    }
}