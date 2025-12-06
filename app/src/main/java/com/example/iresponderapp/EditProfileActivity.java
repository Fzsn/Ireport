package com.example.iresponderapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private EditText editFullName, editContactNumber, editAgency, editLocation;
    private Button btnSaveProfile;

    private DatabaseReference responderRef;
    private String dbKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Get key passed from Fragment
        dbKey = getIntent().getStringExtra("DB_KEY");
        if (dbKey == null) {
            Toast.makeText(this, "Error: Profile Key Missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Point directly to the specific responder node (e.g., .../Responders/-Oew...)
        responderRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Responders").child(dbKey);

        editFullName = findViewById(R.id.editFullName);
        editContactNumber = findViewById(R.id.editContactNumber);
        editAgency = findViewById(R.id.editAgency);
        editLocation = findViewById(R.id.editLocation);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        loadCurrentData();

        btnSaveProfile.setOnClickListener(v -> saveChanges());
    }

    private void loadCurrentData() {
        responderRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    editFullName.setText(snapshot.child("fullName").getValue(String.class));
                    editContactNumber.setText(snapshot.child("contactNumber").getValue(String.class));
                    editAgency.setText(snapshot.child("agency").getValue(String.class));
                    editLocation.setText(snapshot.child("location").getValue(String.class));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void saveChanges() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", editFullName.getText().toString().trim());
        updates.put("contactNumber", editContactNumber.getText().toString().trim());
        updates.put("agency", editAgency.getText().toString().trim());
        updates.put("location", editLocation.getText().toString().trim());

        responderRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Update Failed", Toast.LENGTH_SHORT).show();
        });
    }
}