package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ResponderSignUp extends AppCompatActivity {

    EditText fullName, contactNumber, email, password, confirmPassword;
    Spinner beneficiaryAgency, locationSpinner; // ⭐ ADDED locationSpinner
    Button signUpBtn;
    TextView signInText;

    DatabaseReference responderDB;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_responder_sign_up);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // -----------------------------
        // 🔗 Connect XML Views
        // -----------------------------
        fullName = findViewById(R.id.fullName);
        contactNumber = findViewById(R.id.contactNumber);
        email = findViewById(R.id.email);
        locationSpinner = findViewById(R.id.locationSpinner); // ⭐ CONNECTED
        beneficiaryAgency = findViewById(R.id.agencySpinner);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);
        signUpBtn = findViewById(R.id.btnSignUp);
        signInText = findViewById(R.id.tvSignIn);

        // -----------------------------
        // ⭐ Firebase Auth Init
        // -----------------------------
        auth = FirebaseAuth.getInstance();

        // -----------------------------
        // 📌 Location Spinner Options (NEW)
        // -----------------------------
        String[] locations = {
                "Basud",
                "Capalonga",
                "Daet",
                "Jose Panganiban",
                "Labo",
                "Mercedes",
                "Paracale",
                "San Lorenzo Ruiz",
                "San Vicente",
                "Santa Elena",
                "Talisay",
                "Vinzons"
        };

        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                locations);

        locationSpinner.setAdapter(locationAdapter); // ⭐ SETTING ADAPTER

        // -----------------------------
        // 📌 Agency Spinner Options (UPDATED)
        // -----------------------------
        String[] agencies = {
                "PNP",
                "BFP",
                "MDRRMO"
        };

        ArrayAdapter<String> agencyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                agencies);

        beneficiaryAgency.setAdapter(agencyAdapter); // Renamed adapter for clarity

        // -----------------------------
        // ⭐ Realtime DB Reference
        // -----------------------------
        responderDB = FirebaseDatabase.getInstance()
                .getReference("IresponderApp")
                .child("Responders");

        // -----------------------------
        // 🟢 Sign Up Button Logic
        // -----------------------------
        signUpBtn.setOnClickListener(v -> registerResponder());

        // -----------------------------
        // 🔵 If user already has an account
        // -----------------------------
        signInText.setOnClickListener(v -> {
            startActivity(new Intent(ResponderSignUp.this, responderSignIn.class));
            finish();
        });
    }

    // ===========================================================
    // ⭐ Register Responder WITH Firebase Auth
    // ===========================================================
    private void registerResponder() {

        String name = fullName.getText().toString().trim();
        String contact = contactNumber.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String location = locationSpinner.getSelectedItem().toString(); // ⭐ GETTING LOCATION
        String agency = beneficiaryAgency.getSelectedItem().toString();
        String pass = password.getText().toString().trim();
        String confirmPass = confirmPassword.getText().toString().trim();

        // -----------------------------
        // ❗ VALIDATION
        // -----------------------------
        // NOTE: You should also validate that the spinner selections are not empty,
        // but since you are using a predefined array, the first item will be selected by default.
        if (TextUtils.isEmpty(name) ||
                TextUtils.isEmpty(contact) ||
                TextUtils.isEmpty(userEmail) ||
                TextUtils.isEmpty(pass) ||
                TextUtils.isEmpty(confirmPass)) {

            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pass.equals(confirmPass)) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (contact.length() != 11) {
            Toast.makeText(this, "Contact number must be 11 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        // ================================
        // ⭐ CREATE ACCOUNT (AUTH)
        // ================================
        auth.createUserWithEmailAndPassword(userEmail, pass)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        // Firebase Auth UID
                        String uid = auth.getCurrentUser().getUid();

                        // Create user object
                        // NOTE: You'll need to update your ResponderModel class
                        // to include a field for 'location'
                        ResponderModel responder = new ResponderModel(
                                uid,
                                name,
                                contact,
                                userEmail,
                                location, // ⭐ ADDING LOCATION
                                agency,
                                pass
                        );

                        // Save to Realtime DB
                        responderDB.child(uid).setValue(responder)
                                .addOnCompleteListener(dbTask -> {

                                    if (dbTask.isSuccessful()) {
                                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                                        // Go to sign in screen
                                        startActivity(new Intent(this, responderSignIn.class));
                                        finish();

                                    } else {
                                        Toast.makeText(this, "Database error!", Toast.LENGTH_SHORT).show();
                                    }
                                });

                    } else {
                        Toast.makeText(this, "Auth failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}