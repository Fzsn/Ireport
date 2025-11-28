package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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

public class responderSignIn extends AppCompatActivity {

    EditText email, password;
    Button loginBtn;
    TextView forgotPassword, signUp;
    FirebaseAuth auth;  // ⭐ Firebase Auth

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_responder_sign_in);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ------------------- Initialize Views -------------------
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginBtn);
        forgotPassword = findViewById(R.id.forgotPassword);
        signUp = findViewById(R.id.signUp);

        // ------------------- Firebase Auth Init -------------------
        auth = FirebaseAuth.getInstance();

        // ------------------- Login Button -------------------
        loginBtn.setOnClickListener(v -> loginUser());

        // ------------------- Forgot Password -------------------
        forgotPassword.setOnClickListener(v ->
                Toast.makeText(this, "Forgot Password feature coming soon!", Toast.LENGTH_SHORT).show()
        );

        // ------------------- Go to Sign Up Screen -------------------
        signUp.setOnClickListener(v -> {
            startActivity(new Intent(responderSignIn.this, ResponderSignUp.class));
            finish();
        });
    }

    // ===========================================================
    // ⭐ LOGIN USER (Firebase Authentication)
    // ===========================================================
    private void loginUser() {

        String userEmail = email.getText().toString().trim();
        String userPass = password.getText().toString().trim();

        if (TextUtils.isEmpty(userEmail) || TextUtils.isEmpty(userPass)) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(userEmail, userPass)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

                        // After login, go to the dashboard or home screen
                        startActivity(new Intent(this, ResponderDashboard.class));

                        finish();

                    } else {
                        Toast.makeText(this,
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
