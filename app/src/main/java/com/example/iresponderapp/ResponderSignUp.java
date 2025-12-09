package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.iresponderapp.supabase.SupabaseAuthRepository;

import kotlin.Unit;

public class ResponderSignUp extends AppCompatActivity {

    private EditText fullName, contactNumber, email, password, confirmPassword;
    private TextView fullNameError, contactError, emailError, passwordError, confirmPasswordError;
    private Button signUpBtn;
    private TextView signInText;
    private ImageView togglePassword;
    private boolean isPasswordVisible = false;
    private SupabaseAuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_sign_up);

        initViews();
        setupListeners();
    }

    private void initViews() {
        fullName = findViewById(R.id.fullName);
        contactNumber = findViewById(R.id.contactNumber);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);
        signUpBtn = findViewById(R.id.btnSignUp);
        signInText = findViewById(R.id.tvSignIn);
        togglePassword = findViewById(R.id.togglePassword);

        // Error TextViews
        fullNameError = findViewById(R.id.fullNameError);
        contactError = findViewById(R.id.contactError);
        emailError = findViewById(R.id.emailError);
        passwordError = findViewById(R.id.passwordError);
        confirmPasswordError = findViewById(R.id.confirmPasswordError);

        IreportApp app = (IreportApp) getApplication();
        authRepository = (SupabaseAuthRepository) app.getAuthRepository();
    }

    private void setupListeners() {
        // Clear errors on focus
        fullName.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) hideError(fullNameError); });
        contactNumber.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) hideError(contactError); });
        email.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) hideError(emailError); });
        password.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) hideError(passwordError); });
        confirmPassword.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) hideError(confirmPasswordError); });

        signUpBtn.setOnClickListener(v -> registerResponder());

        signInText.setOnClickListener(v -> {
            // Go back to sign in screen
            finish();
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

    private void registerResponder() {
        // Clear all errors
        hideAllErrors();

        String name = fullName.getText().toString().trim();
        String contact = contactNumber.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String pass = password.getText().toString().trim();
        String confirmPass = confirmPassword.getText().toString().trim();

        // Validate all fields
        boolean isValid = true;

        if (TextUtils.isEmpty(name)) {
            showError(fullNameError, "Full name is required");
            isValid = false;
        } else if (name.length() < 2) {
            showError(fullNameError, "Name must be at least 2 characters");
            isValid = false;
        }

        if (TextUtils.isEmpty(contact)) {
            showError(contactError, "Contact number is required");
            isValid = false;
        } else if (!contact.matches("^09\\d{9}$")) {
            showError(contactError, "Enter valid PH number (09XXXXXXXXX)");
            isValid = false;
        }

        if (TextUtils.isEmpty(userEmail)) {
            showError(emailError, "Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            showError(emailError, "Please enter a valid email address");
            isValid = false;
        }

        if (TextUtils.isEmpty(pass)) {
            showError(passwordError, "Password is required");
            isValid = false;
        } else if (pass.length() < 6) {
            showError(passwordError, "Password must be at least 6 characters");
            isValid = false;
        }

        if (TextUtils.isEmpty(confirmPass)) {
            showError(confirmPasswordError, "Please confirm your password");
            isValid = false;
        } else if (!pass.equals(confirmPass)) {
            showError(confirmPasswordError, "Passwords do not match");
            isValid = false;
        }

        if (!isValid) return;

        // Disable button and show loading
        signUpBtn.setEnabled(false);
        signUpBtn.setText("Creating account...");

        // Agency and station will be assigned by admin later
        authRepository.signUpResponderAsync(
                userEmail,
                pass,
                name,
                contact,
                null,  // agencyId - to be assigned by admin
                null,  // stationId - to be assigned by admin
                unit -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Verification code sent to your email!", Toast.LENGTH_LONG).show();
                        // Navigate to OTP verification screen
                        Intent intent = new Intent(this, OtpVerificationActivity.class);
                        intent.putExtra(OtpVerificationActivity.EXTRA_EMAIL, userEmail);
                        intent.putExtra(OtpVerificationActivity.EXTRA_FULL_NAME, name);
                        intent.putExtra(OtpVerificationActivity.EXTRA_PHONE, contact);
                        startActivity(intent);
                        finish();
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    runOnUiThread(() -> {
                        signUpBtn.setEnabled(true);
                        signUpBtn.setText("Create Account");

                        String errorMsg = throwable.getMessage();
                        Log.e("ResponderSignUp", "Sign up error: " + errorMsg, throwable);

                        if (errorMsg != null) {
                            String lowerMsg = errorMsg.toLowerCase();
                            if (lowerMsg.contains("already registered") || lowerMsg.contains("already been registered") || lowerMsg.contains("user_already_exists")) {
                                showError(emailError, "This email is already registered. Try signing in.");
                            } else if (lowerMsg.contains("email") && lowerMsg.contains("invalid")) {
                                showError(emailError, "Please enter a valid email address");
                            } else if (lowerMsg.contains("network") || lowerMsg.contains("timeout") || lowerMsg.contains("connection")) {
                                Toast.makeText(this, "Network error. Please check your connection.", Toast.LENGTH_LONG).show();
                            } else if (lowerMsg.contains("password") && lowerMsg.contains("weak")) {
                                showError(passwordError, "Password is too weak. Use a stronger password.");
                            } else {
                                Toast.makeText(this, "Sign up failed: " + errorMsg, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(this, "Sign up failed. Please try again.", Toast.LENGTH_LONG).show();
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
        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    private void hideAllErrors() {
        hideError(fullNameError);
        hideError(contactError);
        hideError(emailError);
        hideError(passwordError);
        hideError(confirmPasswordError);
    }

    @Override
    public void onBackPressed() {
        // Go back to sign in screen
        super.onBackPressed();
    }
}