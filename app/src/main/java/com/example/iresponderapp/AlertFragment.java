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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AlertFragment extends Fragment {

    private static final String TAG = "AlertFragment";
    private LinearLayout incidentListContainer;
    private DatabaseReference incidentRef;
    private String currentResponderUid;

    public AlertFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_alert, container, false);

        // Link to the container in fragment_alert.xml
        incidentListContainer = view.findViewById(R.id.responderIncidentListContainer);

        // --- 1. Get Current User UID ---
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null) {
            currentResponderUid = user.getUid();
            Log.d(TAG, "RESPONDER UID LOGGED IN: " + currentResponderUid);
        } else {
            Toast.makeText(getContext(), "Responder not logged in.", Toast.LENGTH_LONG).show();
            return view;
        }

        incidentRef = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Incidents_");
        loadAssignedIncidents();

        return view;
    }

    private void loadAssignedIncidents() {
        if (currentResponderUid == null) return;

        // --- 2. Query Database: Find incidents assigned to this user ---
        incidentRef.orderByChild("AssignedResponderUID").equalTo(currentResponderUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        if (getContext() == null) return;
                        incidentListContainer.removeAllViews();
                        LayoutInflater inflater = LayoutInflater.from(getContext());

                        if (snapshot.getChildrenCount() == 0) {
                            Log.d(TAG, "No incidents currently assigned to this responder.");
                        }

                        for (DataSnapshot data : snapshot.getChildren()) {
                            try {
                                String status = data.child("Status").getValue(String.class);

                                // Only show incidents that are actively assigned
                                if (status == null || !status.equalsIgnoreCase("Assigned")) {
                                    continue;
                                }

                                // --- 3. Retrieve Data ---
                                final String incidentKey = data.getKey();
                                String incidentCode = incidentKey;
                                String type = data.child("incidentType").getValue(String.class);
                                String date = data.child("date").getValue(String.class);
                                String location = data.child("address").getValue(String.class);
                                String agency = data.child("agency").getValue(String.class);

                                // --- 4. Inflate the NEW Responder Card ---
                                // Make sure you are inflating 'incident_card_responder'
                                View card = inflater.inflate(R.layout.incident_card_responder, incidentListContainer, false);

                                // --- 5. Populate Views ---
                                ((TextView) card.findViewById(R.id.incidentCode)).setText("#IR-" + incidentCode);
                                ((TextView) card.findViewById(R.id.incidentType)).setText(type);
                                ((TextView) card.findViewById(R.id.incidentDate)).setText(date);
                                ((TextView) card.findViewById(R.id.incidentLocation)).setText(location);

                                // Status/Priority Display
                                TextView priorityTv = card.findViewById(R.id.incidentPriority);
                                priorityTv.setText("ACTIVE");
                                // Ensure R.color.blue is defined in colors.xml, otherwise change to Color.BLUE
                                priorityTv.setTextColor(getResources().getColor(R.color.blue));

                                // Action button setup (The only button on this card)
                                Button btnView = card.findViewById(R.id.btnViewDetails);
                                btnView.setText("TAKE ACTION");

                                // Handle click to open ResponderDetailActivity
                                btnView.setOnClickListener(v -> {
                                    Intent intent = new Intent(getContext(), ResponderDetailActivity.class);
                                    intent.putExtra("INCIDENT_KEY", incidentKey);
                                    intent.putExtra("INCIDENT_CODE", incidentCode);
                                    intent.putExtra("AGENCY", agency);
                                    startActivity(intent);
                                });

                                incidentListContainer.addView(card);

                            } catch (Exception e) {
                                Log.e(TAG, "Error displaying card: " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Database Error: " + error.getMessage());
                        Toast.makeText(getContext(), "Failed to load assigned incidents.", Toast.LENGTH_LONG).show();
                    }
                });
    }
}