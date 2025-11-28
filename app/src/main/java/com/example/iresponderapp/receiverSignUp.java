package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class receiverSignUp extends AppCompatActivity {

    EditText fullName, contactNumber, email, password, confirmPassword;
    Spinner agencySpinner, locationSpinner;
    Button btnSignUp;
    TextView tvSignIn;

    FirebaseAuth mAuth;
    DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_receiver_sign_up);

        // -------------------------
        //   Firebase Setup
        // -------------------------
        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Receivers");

        // -------------------------
        //   Initialize UI
        // -------------------------
        fullName = findViewById(R.id.fullName);
        contactNumber = findViewById(R.id.contactNumber);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);

        agencySpinner = findViewById(R.id.agencySpinner);
        locationSpinner = findViewById(R.id.Location);

        btnSignUp = findViewById(R.id.btnSignUp);
        tvSignIn = findViewById(R.id.tvSignIn);

        // -------------------------
        //   Populate Spinners
        // -------------------------
        String[] agencies = {"Select Agency", "PNP", "BFP", "MDRRMO"};
        ArrayAdapter<String> agencyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, agencies);
        agencySpinner.setAdapter(agencyAdapter);

        String[] locations = {"Select Location", "Daet", "Labo", "Paracale", "Vinzons", "Talisay"};
        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, locations);
        locationSpinner.setAdapter(locationAdapter);


        // -------------------------
        //   SIGN UP BUTTON LOGIC
        // -------------------------
        btnSignUp.setOnClickListener(v -> createReceiverAccount());

        // -------------------------
        //   GO TO LOGIN SCREEN
        // -------------------------
        tvSignIn.setOnClickListener(v -> {
            Intent intent = new Intent(receiverSignUp.this, receiverSignIn.class);
            startActivity(intent);
            finish();
        });

    }

    // ====================================================
    //   CREATE ACCOUNT (AUTH + SAVE TO DATABASE)
    // ====================================================
    private void createReceiverAccount() {

        String name = fullName.getText().toString().trim();
        String phone = contactNumber.getText().toString().trim();
        String emailStr = email.getText().toString().trim();
        String pass = password.getText().toString().trim();
        String cPass = confirmPassword.getText().toString().trim();
        String agency = agencySpinner.getSelectedItem().toString();
        String location = locationSpinner.getSelectedItem().toString();

        // -------------------------
        //   VALIDATION
        // -------------------------
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone) ||
                TextUtils.isEmpty(emailStr) || TextUtils.isEmpty(pass) ||
                TextUtils.isEmpty(cPass)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pass.equals(cPass)) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (phone.length() < 10) {
            Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (agency.equals("Select Agency")) {
            Toast.makeText(this, "Please select an agency", Toast.LENGTH_SHORT).show();
            return;
        }

        if (location.equals("Select Location")) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
            return;
        }

        // -------------------------
        //   CREATE USER WITH AUTH
        // -------------------------
        mAuth.createUserWithEmailAndPassword(emailStr, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        String receiverId = mAuth.getCurrentUser().getUid();

                        // ------------------------------------------
                        // SAVE USER DATA IN REALTIME DATABASE
                        // ------------------------------------------
                        ReceiverModel model = new ReceiverModel(
                                receiverId,
                                name,
                                phone,
                                emailStr,
                                agency,
                                location
                        );

                        dbRef.child(receiverId).setValue(model)
                                .addOnCompleteListener(saveTask -> {
                                    if (saveTask.isSuccessful()) {
                                        Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();

                                        // Redirect to Sign In
                                        Intent intent = new Intent(receiverSignUp.this, receiverSignIn.class);
                                        startActivity(intent);
                                        finish();

                                    } else {
                                        Toast.makeText(this, "Failed to save data!", Toast.LENGTH_SHORT).show();
                                    }
                                });

                    } else {
                        Toast.makeText(this, "Registration Failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
