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

import com.example.iresponderapp.supabase.IncidentSummary;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;

import kotlin.Unit;

public class OnProcessFragment extends Fragment {

    private static final String TAG = "OnProcessFragment";
    private LinearLayout incidentListContainer;
    private SupabaseIncidentsRepository incidentsRepository;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_on_process, container, false);

        incidentListContainer = view.findViewById(R.id.incidentListContainer);

        // Initialize Supabase repository
        IreportApp app = (IreportApp) requireActivity().getApplication();
        incidentsRepository = (SupabaseIncidentsRepository) app.getIncidentsRepository();

        loadIncidents();

        return view;
    }

    private void loadIncidents() {
        // Query for incidents with status = "pending"
        incidentsRepository.loadPendingIncidentsAsync(
                incidents -> {
                    if (getContext() == null) {
                        Log.w(TAG, "Fragment is detached, skipping UI update.");
                        return Unit.INSTANCE;
                    }

                    incidentListContainer.removeAllViews();

                    if (incidents.isEmpty()) {
                        Log.d(TAG, "No pending incidents found.");
                        return Unit.INSTANCE;
                    }

                    LayoutInflater inflater = LayoutInflater.from(getContext());

                    for (IncidentSummary incident : incidents) {
                        final String incidentKey = incident.getId();
                        String incidentCode = incidentKey.length() > 8 ? incidentKey.substring(0, 8) : incidentKey;

                        String type = incident.getAgencyType().toUpperCase();
                        String createdAt = incident.getCreatedAt();
                        String date = createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : "--";
                        String location = incident.getLocationAddress();

                        // Priority Derivation based on agency type
                        String priority = "Low";
                        if ("BFP".equalsIgnoreCase(type) || "PNP".equalsIgnoreCase(type)) {
                            priority = "High";
                        }

                        // Inflate the incident card layout
                        View card = inflater.inflate(R.layout.incident_card_item, incidentListContainer, false);

                        // Populate card views
                        ((TextView) card.findViewById(R.id.incidentCode)).setText("#IR-" + incidentCode);
                        ((TextView) card.findViewById(R.id.incidentType)).setText(type);
                        ((TextView) card.findViewById(R.id.incidentDate)).setText(date);
                        ((TextView) card.findViewById(R.id.incidentLocation)).setText(location != null ? location : "Unknown");
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

                        incidentListContainer.addView(card);
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Database Error: " + throwable.getMessage());
                    Toast.makeText(getContext(), "Failed to load incidents: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    return Unit.INSTANCE;
                }
        );
    }
}