package com.example.iresponderapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class receiverSignIn extends AppCompatActivity {

    EditText email, password;
    CheckBox rememberMe;
    Button loginBtn;
    TextView forgotPassword, signUp;

    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_sign_in);

        auth = FirebaseAuth.getInstance();

        // UI elements
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        rememberMe = findViewById(R.id.rememberMe);
        loginBtn = findViewById(R.id.loginBtn);
        forgotPassword = findViewById(R.id.forgotPassword);
        signUp = findViewById(R.id.signUp);

        loadSavedCredentials();

        // login
        loginBtn.setOnClickListener(v -> loginUser());

        // go to sign up
        signUp.setOnClickListener(v ->
                startActivity(new Intent(receiverSignIn.this, receiverSignUp.class))
        );
    }

    private void loginUser() {
        String emailTxt = email.getText().toString().trim();
        String passwordTxt = password.getText().toString().trim();

        if (emailTxt.isEmpty() || passwordTxt.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        loginBtn.setEnabled(false);
        loginBtn.setText("Logging in...");

        auth.signInWithEmailAndPassword(emailTxt, passwordTxt)
                .addOnCompleteListener(task -> {

                    loginBtn.setEnabled(true);
                    loginBtn.setText("Log In");

                    if (task.isSuccessful()) {

                        saveCredentials(emailTxt, passwordTxt);

                        Toast.makeText(receiverSignIn.this,
                                "Login successful!", Toast.LENGTH_SHORT).show();

                        startActivity(new Intent(receiverSignIn.this, RecieverDashboard.class));
                        finish();

                    } else {
                        Toast.makeText(receiverSignIn.this,
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveCredentials(String email, String password) {
        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (rememberMe.isChecked()) {
            editor.putString("email", email);
            editor.putString("password", password);
            editor.putBoolean("remember", true);
        } else {
            editor.clear();
        }

        editor.apply();
    }

    private void loadSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);

        if (prefs.getBoolean("remember", false)) {
            email.setText(prefs.getString("email", ""));
            password.setText(prefs.getString("password", ""));
            rememberMe.setChecked(true);
        }
    }
}
