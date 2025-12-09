package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.iresponderapp.supabase.SupabaseAuthRepository;

import kotlin.Unit;

public class responderSignIn extends AppCompatActivity {

    private EditText email, password;
    private TextView emailError, passwordError;
    private Button loginBtn;
    private TextView forgotPassword, signUp;
    private CheckBox rememberMe;
    private ImageView togglePassword;
    private boolean isPasswordVisible = false;
    private SupabaseAuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_sign_in);

        initViews();
        setupListeners();
    }

    private void initViews() {
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        emailError = findViewById(R.id.emailError);
        passwordError = findViewById(R.id.passwordError);
        loginBtn = findViewById(R.id.loginBtn);
        forgotPassword = findViewById(R.id.forgotPassword);
        signUp = findViewById(R.id.signUp);
        rememberMe = findViewById(R.id.rememberMe);
        togglePassword = findViewById(R.id.togglePassword);

        IreportApp app = (IreportApp) getApplication();
        authRepository = (SupabaseAuthRepository) app.getAuthRepository();
    }

    private void setupListeners() {
        // Clear errors on text change
        email.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideError(emailError);
        });
        password.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideError(passwordError);
        });

        loginBtn.setOnClickListener(v -> loginUser());

        forgotPassword.setOnClickListener(v ->
                Toast.makeText(this, "Forgot Password feature coming soon!", Toast.LENGTH_SHORT).show()
        );

        signUp.setOnClickListener(v -> {
            startActivity(new Intent(responderSignIn.this, ResponderSignUp.class));
        });

        // Password visibility toggle
        togglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                password.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePassword.setImageResource(R.drawable.ic_visibility);
            } else {
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePassword.setImageResource(R.drawable.ic_visibility_off);
            }
            password.setSelection(password.getText().length());
        });
    }

    private void loginUser() {
        // Clear previous errors
        hideError(emailError);
        hideError(passwordError);

        String userEmail = email.getText().toString().trim();
        String userPass = password.getText().toString().trim();

        // Validate inputs
        boolean isValid = true;

        if (TextUtils.isEmpty(userEmail)) {
            showError(emailError, "Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            showError(emailError, "Please enter a valid email address");
            isValid = false;
        }

        if (TextUtils.isEmpty(userPass)) {
            showError(passwordError, "Password is required");
            isValid = false;
        } else if (userPass.length() < 6) {
            showError(passwordError, "Password must be at least 6 characters");
            isValid = false;
        }

        if (!isValid) return;

        // Disable button and show loading state
        loginBtn.setEnabled(false);
        loginBtn.setText("Signing in...");

        authRepository.signInAsync(
                userEmail,
                userPass,
                unit -> {
                    runOnUiThread(() -> {
                        // Start realtime notifications after successful login
                        IreportApp app = (IreportApp) getApplication();
                        app.startRealtimeNotifications();
                        
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, ResponderDashboard.class));
                        finish();
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    runOnUiThread(() -> {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Sign In");

                        String errorMsg = throwable.getMessage();
                        if (errorMsg != null && errorMsg.toLowerCase().contains("invalid")) {
                            showError(passwordError, "Invalid email or password");
                        } else if (errorMsg != null && errorMsg.toLowerCase().contains("network")) {
                            Toast.makeText(this, "Network error. Please check your connection.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Login failed: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                    return Unit.INSTANCE;
                }
        );
    }

    private void showError(TextView errorView, String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError(TextView errorView) {
        errorView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        // Exit app instead of going back to splash
        super.onBackPressed();
        finishAffinity();
    }
}
