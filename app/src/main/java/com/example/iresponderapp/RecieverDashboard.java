package com.example.iresponderapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.fragment.app.Fragment;

public class RecieverDashboard extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reciever_dashboard);

        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // Load default fragment (On Process)
        if (savedInstanceState == null) {
            loadFragment(new OnProcessFragment());
        }

        // Handle bottom nav clicks
        bottomNavigationView.setOnItemSelectedListener(item -> {

            Fragment selectedFragment = null;

            int id = item.getItemId();

            if (id == R.id.nav_process) {
                selectedFragment = new OnProcessFragment();

            } else if (id == R.id.nav_accomplished) {
                selectedFragment = new AccomplishedFragment();

            } else if (id == R.id.nav_receiver_profile) {
                selectedFragment = new ReceiverProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }

            return true;
        });
    }

    private void loadFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.receiverdashboardFragmentContainer, fragment)
                .commit();
    }
}
