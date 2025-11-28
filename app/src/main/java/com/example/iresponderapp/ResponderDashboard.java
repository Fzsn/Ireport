package com.example.iresponderapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ResponderDashboard extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_responder_dashboard);

        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // Load Home fragment first
        loadFragment(new HomeFragment());

        // Bottom navigation listener
        bottomNavigationView.setOnItemSelectedListener(item -> {

            Fragment fragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (itemId == R.id.nav_alert) {
                fragment = new AlertFragment();
            } else if (itemId == R.id.nav_forms) {
                fragment = new FormsFragment();
            } else if (itemId == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }

            return loadFragment(fragment);
        });

    }

    // Method to switch fragments
    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.dashboardFragmentContainer, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}
