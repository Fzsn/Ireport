package com.example.iresponderapp;

import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class OnProcessFragment extends Fragment {

    private LinearLayout incidentListContainer;
    private DatabaseReference incidentRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_on_process, container, false);

        incidentListContainer = view.findViewById(R.id.incidentListContainer);
        incidentRef = FirebaseDatabase.getInstance().getReference("Incidents");

        loadIncidents();

        return view;
    }

    private void loadIncidents() {
        incidentRef.orderByChild("status").equalTo("Pending")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        incidentListContainer.removeAllViews(); // Clear old items

                        for (DataSnapshot data : snapshot.getChildren()) {

                            String incidentCode = data.child("incidentCode").getValue(String.class);
                            String type = data.child("incidentType").getValue(String.class);
                            String date = data.child("date").getValue(String.class);
                            String location = data.child("location").getValue(String.class);
                            String priority = data.child("priority").getValue(String.class);

                            View card = getLayoutInflater().inflate(R.layout.incident_card_item, incidentListContainer, false);

                            // Populate card
                            ((TextView) card.findViewById(R.id.incidentCode)).setText(incidentCode);
                            ((TextView) card.findViewById(R.id.incidentType)).setText(type);
                            ((TextView) card.findViewById(R.id.incidentDate)).setText(date);
                            ((TextView) card.findViewById(R.id.incidentLocation)).setText(location);
                            ((TextView) card.findViewById(R.id.incidentPriority)).setText("Priority: " + priority);

                            // Buttons
                            Button btnView = card.findViewById(R.id.btnViewDetails);
                            Button btnEscalate = card.findViewById(R.id.btnEscalate);
                            Button btnReject = card.findViewById(R.id.btnReject);

                            btnView.setOnClickListener(v -> {
                                // TODO: Show details activity or dialog
                            });

                            btnEscalate.setOnClickListener(v -> {
                                incidentRef.child(data.getKey()).child("status").setValue("Escalated");
                            });

                            btnReject.setOnClickListener(v -> {
                                incidentRef.child(data.getKey()).child("status").setValue("Rejected");
                            });

                            incidentListContainer.addView(card);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) { }
                });
    }
}
