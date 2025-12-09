package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.iresponderapp.supabase.SupabaseAuthRepository;

/**
 * Splash screen that shows the app logo and checks authentication status.
 * If user is already logged in, redirects to dashboard.
 * Otherwise, redirects to login screen.
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Check auth status after delay
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndNavigate, SPLASH_DELAY);
    }

    private void checkAuthAndNavigate() {
        IreportApp app = (IreportApp) getApplication();
        SupabaseAuthRepository authRepository = (SupabaseAuthRepository) app.getAuthRepository();

        String userId = authRepository.getCurrentUserId();

        Intent intent;
        if (userId != null && !userId.isEmpty()) {
            // User is logged in, go to dashboard
            intent = new Intent(this, ResponderDashboard.class);
        } else {
            // User not logged in, go to login
            intent = new Intent(this, responderSignIn.class);
        }

        startActivity(intent);
        finish();
    }
}
