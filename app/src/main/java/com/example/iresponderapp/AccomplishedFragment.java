package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AccomplishedFragment extends Fragment {

    private static final String TAG = "AccomplishedFragment";
    private LinearLayout incidentListContainer;
    private DatabaseReference incidentRef;

    private TextView filterApproved;
    private TextView filterDeclined;

    // State variable to hold the currently selected filter: "ASSIGNED", "REJECTED", or "ALL"
    private String currentFilterStatus = "ALL";

    public AccomplishedFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Assuming fragment_accomplished.xml has been updated with filterApproved/filterDeclined IDs
        View view = inflater.inflate(R.layout.fragment_accomplished, container, false);

        incidentListContainer = view.findViewById(R.id.accomplishedListContainer);
        filterApproved = view.findViewById(R.id.filterApproved);
        filterDeclined = view.findViewById(R.id.filterDeclined);

        incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Incidents_");

        setupFilters();
        loadAccomplishedIncidents();

        return view;
    }

    private void setupFilters() {
        filterApproved.setOnClickListener(v -> {
            currentFilterStatus = "ASSIGNED";
            updateFilterUI();
            loadAccomplishedIncidents();
        });

        filterDeclined.setOnClickListener(v -> {
            currentFilterStatus = "REJECTED";
            updateFilterUI();
            loadAccomplishedIncidents();
        });

        // Initial setup
        updateFilterUI();
    }

    private void updateFilterUI() {
        // NOTE: Define R.color.white, R.color.black, R.color.red, R.color.green in your colors.xml

        int activeTextColor = ContextCompat.getColor(getContext(), R.color.white);
        int inactiveTextColor = ContextCompat.getColor(getContext(), R.color.black);

        // Use custom drawable backgrounds (you need to define these in res/drawable)
        int activeApprovedBg = R.drawable.status_approved_bg;
        int activeDeclinedBg = R.drawable.status_declined_bg;
        int inactiveApprovedBg = R.drawable.status_approved_bg_inactive;
        int inactiveDeclinedBg = R.drawable.status_declined_bg_inactive;


        // Set Active/Inactive Styles
        if (currentFilterStatus.equals("ASSIGNED")) {
            filterApproved.setTextColor(activeTextColor);
            filterApproved.setBackgroundResource(activeApprovedBg);
            filterDeclined.setTextColor(inactiveTextColor);
            filterDeclined.setBackgroundResource(inactiveDeclinedBg);
        } else if (currentFilterStatus.equals("REJECTED")) {
            filterApproved.setTextColor(inactiveTextColor);
            filterApproved.setBackgroundResource(inactiveApprovedBg);
            filterDeclined.setTextColor(activeTextColor);
            filterDeclined.setBackgroundResource(activeDeclinedBg);
        } else {
            // Default to inactive state if showing ALL (or no filter selected)
            filterApproved.setTextColor(inactiveTextColor);
            filterDeclined.setTextColor(inactiveTextColor);
            filterApproved.setBackgroundResource(inactiveApprovedBg);
            filterDeclined.setBackgroundResource(inactiveDeclinedBg);
        }
    }


    private void loadAccomplishedIncidents() {
        incidentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                if (getContext() == null) return;
                incidentListContainer.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(getContext());

                for (DataSnapshot data : snapshot.getChildren()) {
                    String status = data.child("Status").getValue(String.class);

                    if (status == null) continue;

                    // --- FILTERING LOGIC: LOCAL FILTER BY STATUS ---
                    boolean matchesFilter = false;
                    String statusUpper = status.toUpperCase();

                    // 1. Check if the status is a finalized one
                    boolean isFinalized = !statusUpper.equals("PENDING");

                    // 2. Filter based on the selected UI tab
                    if (currentFilterStatus.equals("ALL")) {
                        matchesFilter = isFinalized;
                    } else if (currentFilterStatus.equals("ASSIGNED")) {
                        // Check for assigned status
                        matchesFilter = statusUpper.equals("ASSIGNED");
                    } else if (currentFilterStatus.equals("REJECTED")) {
                        // Check for rejected or declined status
                        matchesFilter = statusUpper.equals("REJECTED") || statusUpper.equals("DECLINED");
                    }

                    if (matchesFilter) {

                        final String incidentKey = data.getKey();
                        String incidentCode = incidentKey;
                        String type = data.child("incidentType").getValue(String.class);
                        String date = data.child("date").getValue(String.class);
                        String location = data.child("address").getValue(String.class);

                        // Inflate the NEW, dedicated incident card layout
                        View card = inflater.inflate(R.layout.incident_card_accomplished, incidentListContainer, false);

                        // Populate card views
                        ((TextView) card.findViewById(R.id.incidentCode)).setText("#IR-" + incidentCode);
                        ((TextView) card.findViewById(R.id.incidentType)).setText(type);
                        ((TextView) card.findViewById(R.id.incidentDate)).setText(date);
                        ((TextView) card.findViewById(R.id.incidentLocation)).setText(location);

                        // Set the status text where the priority used to be
                        TextView statusTv = card.findViewById(R.id.incidentPriority);
                        statusTv.setText(statusUpper);

                        // Adjust color based on final status
                        if (statusUpper.equals("REJECTED") || statusUpper.equals("DECLINED")) {
                            statusTv.setTextColor(getResources().getColor(R.color.red));
                        } else {
                            statusTv.setTextColor(getResources().getColor(R.color.green));
                        }

                        // === BUTTONS ADJUSTMENT ===
                        // ONLY reference the button that exists in incident_card_accomplished.xml
                        Button btnView = card.findViewById(R.id.btnViewDetails);

                        btnView.setText("SEE MORE DETAILS");

                        // Handle click to view full history/details (AccomplishedDetailsActivity)
                        btnView.setOnClickListener(v -> {
                            Intent intent = new Intent(getContext(), AccomplishedDetailsActivity.class);
                            intent.putExtra("INCIDENT_KEY", incidentKey);
                            intent.putExtra("INCIDENT_CODE", incidentCode);
                            startActivity(intent);
                        });

                        incidentListContainer.addView(card);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Database Error: " + error.getMessage());
                Toast.makeText(getContext(), "Failed to load accomplished incidents.", Toast.LENGTH_LONG).show();
            }
        });
    }
}