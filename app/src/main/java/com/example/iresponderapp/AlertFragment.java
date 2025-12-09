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

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.example.iresponderapp.supabase.AppNotification;
import com.example.iresponderapp.supabase.IncidentSummary;
import com.example.iresponderapp.supabase.NotificationsRealtimeManager;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;

import kotlin.Unit;

public class AlertFragment extends Fragment implements NotificationsRealtimeManager.Listener {

    private static final String TAG = "AlertFragment";
    private LinearLayout incidentListContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View alertLoadingContainer;
    private TextView emptyStateView;

    private SupabaseIncidentsRepository incidentsRepository;
    private NotificationsRealtimeManager realtimeManager;

    public AlertFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_alert, container, false);

        // Link to the container in fragment_alert.xml
        incidentListContainer = view.findViewById(R.id.responderIncidentListContainer);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshAlerts);
        alertLoadingContainer = view.findViewById(R.id.alertLoadingContainer);
        emptyStateView = view.findViewById(R.id.alertEmptyState);

        // --- 1. Initialize Supabase repository ---
        IreportApp app = (IreportApp) requireActivity().getApplication();
        incidentsRepository = (SupabaseIncidentsRepository) app.getIncidentsRepository();
        realtimeManager = app.getNotificationsRealtimeManager();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.primary_default);
            swipeRefreshLayout.setOnRefreshListener(() -> loadAssignedIncidents(true));
        }

        loadAssignedIncidents(false);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (realtimeManager != null) {
            realtimeManager.addListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (realtimeManager != null) {
            realtimeManager.removeListener(this);
        }
    }

    // --- NotificationsRealtimeManager.Listener implementation ---
    @Override
    public void onNewNotification(AppNotification notification) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), notification.getTitle(), Toast.LENGTH_SHORT).show();
        // Refresh the assigned incidents list
        loadAssignedIncidents();
    }

    @Override
    public void onUnreadCountChanged(int count) {
        // Badge is handled by HomeFragment, no action needed here
    }

    private void loadAssignedIncidents() {
        loadAssignedIncidents(false);
    }

    private void loadAssignedIncidents(boolean fromSwipe) {
        if (!fromSwipe && alertLoadingContainer != null) {
            alertLoadingContainer.setVisibility(View.VISIBLE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }

        // Query for incidents assigned to current user (assigned or in_progress)
        incidentsRepository.loadAssignedIncidentsByStatusAsync(
                null,  // null = show all active incidents assigned to this user
                incidents -> {
                    if (getContext() == null) return Unit.INSTANCE;

                    if (alertLoadingContainer != null) {
                        alertLoadingContainer.setVisibility(View.GONE);
                    }
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    incidentListContainer.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(getContext());

                    if (incidents.isEmpty()) {
                        Log.d(TAG, "No incidents currently assigned to this responder.");
                        if (emptyStateView != null) {
                            emptyStateView.setVisibility(View.VISIBLE);
                        }
                    }

                    for (IncidentSummary incident : incidents) {
                        try {
                            final String incidentKey = incident.getId();
                            String incidentCode = incidentKey.length() > 8 ? incidentKey.substring(0, 8).toUpperCase() : incidentKey.toUpperCase();
                            String type = incident.getAgencyType().toUpperCase();
                            String createdAt = incident.getCreatedAt();
                            String location = incident.getLocationAddress();
                            Double latitude = incident.getLatitude();
                            Double longitude = incident.getLongitude();

                            // Inflate the Responder Card
                            View card = inflater.inflate(R.layout.incident_card_responder, incidentListContainer, false);

                            // Populate Views
                            ((TextView) card.findViewById(R.id.incidentCode)).setText("#" + incidentCode);
                            ((TextView) card.findViewById(R.id.incidentType)).setText(type);

                            // Format date and time
                            if (createdAt != null && createdAt.length() >= 16) {
                                try {
                                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                                    Date dateObj = inputFormat.parse(createdAt.substring(0, 19));
                                    if (dateObj != null) {
                                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                        ((TextView) card.findViewById(R.id.incidentDate)).setText(dateFormat.format(dateObj));
                                        ((TextView) card.findViewById(R.id.incidentTime)).setText(timeFormat.format(dateObj));
                                    }
                                } catch (ParseException e) {
                                    ((TextView) card.findViewById(R.id.incidentDate)).setText("N/A");
                                    ((TextView) card.findViewById(R.id.incidentTime)).setText("--:--");
                                }
                            } else {
                                ((TextView) card.findViewById(R.id.incidentDate)).setText("N/A");
                                ((TextView) card.findViewById(R.id.incidentTime)).setText("--:--");
                            }

                            ((TextView) card.findViewById(R.id.incidentLocation)).setText(location != null ? location : "Unknown");

                            // Coordinates
                            LinearLayout coordRow = card.findViewById(R.id.coordinatesRow);
                            TextView coordText = card.findViewById(R.id.incidentCoordinates);
                            if (latitude != null && longitude != null) {
                                coordText.setText(String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude));
                                coordRow.setVisibility(View.VISIBLE);
                            } else {
                                coordRow.setVisibility(View.GONE);
                            }

                            // Status/Priority Display - show actual status
                            TextView priorityTv = card.findViewById(R.id.incidentPriority);
                            String status = incident.getStatus();
                            if ("in_progress".equals(status)) {
                                priorityTv.setText("IN PROGRESS");
                            } else {
                                priorityTv.setText(status != null ? status.toUpperCase().replace("_", " ") : "ASSIGNED");
                            }

                            // Action button setup
                            Button btnView = card.findViewById(R.id.btnViewDetails);
                            btnView.setText("TAKE ACTION");

                            // Handle click to open ResponderDetailActivity
                            btnView.setOnClickListener(v -> {
                                Intent intent = new Intent(getContext(), ResponderDetailActivity.class);
                                intent.putExtra("INCIDENT_KEY", incidentKey);
                                intent.putExtra("INCIDENT_CODE", incidentCode);
                                intent.putExtra("AGENCY", type);
                                startActivity(intent);
                            });

                            incidentListContainer.addView(card);

                        } catch (Exception e) {
                            Log.e(TAG, "Error displaying card: " + e.getMessage());
                        }
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Database Error: " + throwable.getMessage());
                    Toast.makeText(getContext(), "Failed to load assigned incidents.", Toast.LENGTH_LONG).show();
                    if (alertLoadingContainer != null) {
                        alertLoadingContainer.setVisibility(View.GONE);
                    }
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    return Unit.INSTANCE;
                }
        );
    }
}