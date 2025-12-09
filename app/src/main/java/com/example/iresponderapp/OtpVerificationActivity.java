package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.iresponderapp.supabase.SupabaseAuthRepository;

import kotlin.Unit;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerification";
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_FULL_NAME = "full_name";
    public static final String EXTRA_PHONE = "phone";

    private EditText editOtpCode;
    private Button btnVerify;
    private TextView tvEmail, tvError, tvResendCode, tvTimer;
    private ImageView btnBack;

    private SupabaseAuthRepository authRepository;
    private String email;
    private String fullName;
    private String phone;

    private CountDownTimer resendTimer;
    private boolean canResend = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        // Get data from intent
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        fullName = getIntent().getStringExtra(EXTRA_FULL_NAME);
        phone = getIntent().getStringExtra(EXTRA_PHONE);

        if (email == null) {
            Toast.makeText(this, "Error: Email not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();

        // Start resend cooldown
        startResendCooldown();
    }

    private void initViews() {
        editOtpCode = findViewById(R.id.editOtpCode);
        btnVerify = findViewById(R.id.btnVerify);
        tvEmail = findViewById(R.id.tvEmail);
        tvError = findViewById(R.id.tvError);
        tvResendCode = findViewById(R.id.tvResendCode);
        tvTimer = findViewById(R.id.tvTimer);
        btnBack = findViewById(R.id.btnBack);

        tvEmail.setText(email);

        IreportApp app = (IreportApp) getApplication();
        authRepository = (SupabaseAuthRepository) app.getAuthRepository();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Auto-verify when 6 digits entered
        editOtpCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();
                if (s.length() == 6) {
                    verifyOtp();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnVerify.setOnClickListener(v -> verifyOtp());

        tvResendCode.setOnClickListener(v -> {
            if (canResend) {
                resendOtp();
            }
        });
    }

    private void verifyOtp() {
        String otp = editOtpCode.getText().toString().trim();

        if (TextUtils.isEmpty(otp)) {
            showError("Please enter the verification code");
            return;
        }

        if (otp.length() != 6) {
            showError("Please enter a valid 6-digit code");
            return;
        }

        btnVerify.setEnabled(false);
        btnVerify.setText("Verifying...");

        authRepository.verifyOtpAsync(
                email,
                otp,
                unit -> {
                    runOnUiThread(() -> {
                        // OTP verified, now create/update profile
                        createProfile();
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    runOnUiThread(() -> {
                        btnVerify.setEnabled(true);
                        btnVerify.setText("Verify");

                        String errorMsg = throwable.getMessage();
                        Log.e(TAG, "OTP verification error: " + errorMsg, throwable);

                        if (errorMsg != null && errorMsg.toLowerCase().contains("invalid")) {
                            showError("Invalid code. Please try again.");
                        } else if (errorMsg != null && errorMsg.toLowerCase().contains("expired")) {
                            showError("Code expired. Please request a new one.");
                        } else {
                            showError("Verification failed. Please try again.");
                        }
                    });
                    return Unit.INSTANCE;
                }
        );
    }

    private void createProfile() {
        btnVerify.setText("Setting up profile...");

        authRepository.createProfileAfterVerificationAsync(
                fullName,
                phone,
                unit -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Account verified successfully! Please sign in.", Toast.LENGTH_LONG).show();
                        // Go to sign in screen
                        Intent intent = new Intent(this, responderSignIn.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    runOnUiThread(() -> {
                        // Profile creation failed but user is verified
                        // They can still sign in and profile will be created later
                        Log.e(TAG, "Profile creation error: " + throwable.getMessage(), throwable);
                        Toast.makeText(this, "Account verified! Please sign in to complete setup.", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(this, responderSignIn.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                    return Unit.INSTANCE;
                }
        );
    }

    private void resendOtp() {
        if (!canResend) return;

        tvResendCode.setEnabled(false);
        Toast.makeText(this, "Sending new code...", Toast.LENGTH_SHORT).show();

        authRepository.resendOtpAsync(
                email,
                unit -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "New code sent to your email", Toast.LENGTH_SHORT).show();
                        startResendCooldown();
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    runOnUiThread(() -> {
                        tvResendCode.setEnabled(true);
                        Toast.makeText(this, "Failed to resend code. Try again.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Resend OTP error: " + throwable.getMessage(), throwable);
                    });
                    return Unit.INSTANCE;
                }
        );
    }

    private void startResendCooldown() {
        canResend = false;
        tvResendCode.setEnabled(false);
        tvResendCode.setAlpha(0.5f);
        tvTimer.setVisibility(View.VISIBLE);

        resendTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText("Resend available in " + (millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                canResend = true;
                tvResendCode.setEnabled(true);
                tvResendCode.setAlpha(1.0f);
                tvTimer.setVisibility(View.GONE);
            }
        }.start();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
}
