package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class OnProcessFragment extends Fragment {

    private static final String TAG = "OnProcessFragment";
    private LinearLayout incidentListContainer;
    private DatabaseReference incidentRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_on_process, container, false);

        incidentListContainer = view.findViewById(R.id.incidentListContainer);

        // Correct Firebase path to IresponderApp/Incidents_
        incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Incidents_");

        loadIncidents();

        return view;
    }

    private void loadIncidents() {
        // Query for incidents where the key "Status" is "Pending"
        incidentRef.orderByChild("Status").equalTo("Pending")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        // CRITICAL FIX: Ensure the fragment and its context are still valid
                        if (getContext() == null) {
                            Log.w(TAG, "Fragment is detached, skipping UI update.");
                            return;
                        }

                        incidentListContainer.removeAllViews();

                        if (snapshot.getChildrenCount() == 0) {
                            Log.d(TAG, "No pending incidents found. Check Status indexing/rules.");
                            return;
                        }

                        // Use the LayoutInflater safely from the Activity's context
                        LayoutInflater inflater = LayoutInflater.from(getContext());

                        for (DataSnapshot data : snapshot.getChildren()) {

                            final String incidentKey = data.getKey();
                            String incidentCode = incidentKey;

                            // Data Retrieval (Matching keys from your JSON)
                            String type = data.child("incidentType").getValue(String.class);
                            String date = data.child("date").getValue(String.class);
                            String location = data.child("address").getValue(String.class);

                            // Priority Derivation
                            String priority = "Low";
                            if ("Fire".equalsIgnoreCase(type) || "Crime".equalsIgnoreCase(type)) {
                                priority = "High";
                            }

                            if (incidentCode == null || type == null || date == null || location == null) {
                                Log.e(TAG, "Missing essential data for incident: " + incidentKey);
                                continue;
                            }

                            // Inflate the incident card layout using the safe inflater
                            View card = inflater.inflate(R.layout.incident_card_item, incidentListContainer, false);

                            // Populate card views
                            ((TextView) card.findViewById(R.id.incidentCode)).setText("#IR-" + incidentCode);
                            ((TextView) card.findViewById(R.id.incidentType)).setText(type);
                            ((TextView) card.findViewById(R.id.incidentDate)).setText(date);
                            ((TextView) card.findViewById(R.id.incidentLocation)).setText(location);
                            ((TextView) card.findViewById(R.id.incidentPriority)).setText("Priority: " + priority);

                            // Apply color based on derived priority
                            TextView priorityTv = card.findViewById(R.id.incidentPriority);
                            if ("High".equals(priority)) {
                                priorityTv.setTextColor(getResources().getColor(R.color.red));
                            } else if ("Medium".equals(priority)) {
                                priorityTv.setTextColor(getResources().getColor(R.color.orange));
                            } else {
                                priorityTv.setTextColor(getResources().getColor(R.color.gray));
                            }

                            // Button setup
                            Button btnView = card.findViewById(R.id.btnViewDetails);

                            // Handle View Details Click
                            btnView.setOnClickListener(v -> {
                                Intent intent = new Intent(getContext(), activity_incident_details.class);
                                intent.putExtra("INCIDENT_KEY", incidentKey);
                                intent.putExtra("INCIDENT_CODE", incidentCode);
                                startActivity(intent);
                            });

                            // NOTE: btnEscalate and btnReject logic REMOVED as requested.
                            // The buttons must also be removed from incident_card_item.xml

                            incidentListContainer.addView(card);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Database Error: " + error.getMessage());
                        Toast.makeText(getContext(), "Failed to load incidents: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}