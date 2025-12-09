package com.example.iresponderapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.iresponderapp.supabase.AppNotification;
import com.example.iresponderapp.supabase.IncidentSummary;
import com.example.iresponderapp.supabase.NotificationsRealtimeManager;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;
import com.example.iresponderapp.supabase.SupabaseNotificationsRepository;
import com.example.iresponderapp.supabase.SupabaseResponderProfileRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;

public class HomeFragment extends Fragment implements NotificationsRealtimeManager.Listener {

    // UI Components
    private TextView txtOfficerName, txtOfficerStatus;
    private TextView txtActive, txtCompleted, txtTotalReports, txtCaseCount;
    private RecyclerView recyclerOngoingCases;
    private LinearLayout loadingContainer;
    private View contentContainer, emptyStateContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView notificationBadge;
    private FrameLayout notificationContainer;

    // Supabase repositories
    private SupabaseResponderProfileRepository profileRepository;
    private SupabaseIncidentsRepository incidentsRepository;
    private SupabaseNotificationsRepository notificationsRepository;
    private NotificationsRealtimeManager realtimeManager;

    // Adapter
    private OngoingCasesAdapter adapter;
    private List<IncidentSummary> ongoingCasesList;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. Initialize Views
        txtOfficerName = view.findViewById(R.id.txtOfficerName);

        txtActive = view.findViewById(R.id.txtActive);
        txtCompleted = view.findViewById(R.id.txtCompleted);
        txtTotalReports = view.findViewById(R.id.txtTotalReports);
        txtCaseCount = view.findViewById(R.id.txtCaseCount);
        recyclerOngoingCases = view.findViewById(R.id.recyclerOngoingCases);
        loadingContainer = view.findViewById(R.id.loadingContainer);
        contentContainer = view.findViewById(R.id.contentContainer);
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadOfficerInfo();
            loadDashboardData();
        });
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        // 2. Setup RecyclerView
        recyclerOngoingCases.setLayoutManager(new LinearLayoutManager(getContext()));
        ongoingCasesList = new ArrayList<>();
        adapter = new OngoingCasesAdapter(ongoingCasesList, getContext());
        recyclerOngoingCases.setAdapter(adapter);

        // 3. Initialize Supabase repositories
        IreportApp app = (IreportApp) requireActivity().getApplication();
        profileRepository = (SupabaseResponderProfileRepository) app.getResponderProfileRepository();
        incidentsRepository = (SupabaseIncidentsRepository) app.getIncidentsRepository();
        notificationsRepository = app.getNotificationsRepository();
        realtimeManager = app.getNotificationsRealtimeManager();

        // 4. Setup notification badge reference (click handler is in ResponderDashboard)
        View headerView = requireActivity().findViewById(R.id.appHeader);
        if (headerView != null) {
            notificationBadge = headerView.findViewById(R.id.notificationBadge);
            notificationContainer = headerView.findViewById(R.id.notificationContainer);
            // Note: Click listener is handled by ResponderDashboard activity
        }

        loadOfficerInfo();
        loadDashboardData();
        loadNotificationCount();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register as listener for realtime notifications
        if (realtimeManager != null) {
            realtimeManager.addListener(this);
        }
        loadNotificationCount();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister listener
        if (realtimeManager != null) {
            realtimeManager.removeListener(this);
        }
    }

    // --- NotificationsRealtimeManager.Listener implementation ---
    @Override
    public void onNewNotification(AppNotification notification) {
        if (getContext() == null) return;
        // Show toast for new notification
        Toast.makeText(getContext(), notification.getTitle(), Toast.LENGTH_SHORT).show();
        // Refresh incidents list
        loadDashboardData();
    }

    @Override
    public void onUnreadCountChanged(int count) {
        updateNotificationBadge(count);
    }

    // --- Notification helpers ---
    private void loadNotificationCount() {
        if (notificationsRepository == null) return;
        notificationsRepository.loadUnreadCountAsync(
                count -> {
                    updateNotificationBadge(count);
                    return Unit.INSTANCE;
                },
                error -> Unit.INSTANCE
        );
    }

    private void updateNotificationBadge(int count) {
        if (notificationBadge == null) return;
        if (count > 0) {
            notificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            notificationBadge.setVisibility(View.VISIBLE);
        } else {
            notificationBadge.setVisibility(View.GONE);
        }
    }

    private void openNotificationsList() {
        if (getContext() == null) return;
        
        // Load ALL notifications (not just unread) for the modal
        notificationsRepository.loadAllNotificationsAsync(
                notifications -> {
                    if (getContext() == null) return Unit.INSTANCE;
                    showNotificationsDialog(notifications);
                    return Unit.INSTANCE;
                },
                error -> {
                    Toast.makeText(getContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
        );
    }
    
    private void showNotificationsDialog(List<AppNotification> notifications) {
        if (getContext() == null) return;
        
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_notifications, null);
        LinearLayout container = dialogView.findViewById(R.id.notificationsContainer);
        TextView emptyText = dialogView.findViewById(R.id.emptyText);
        TextView btnMarkAllRead = dialogView.findViewById(R.id.btnMarkAllRead);
        TextView btnClose = dialogView.findViewById(R.id.btnClose);
        
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (notifications.isEmpty()) {
            container.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            btnMarkAllRead.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            
            LayoutInflater inflater = LayoutInflater.from(getContext());
            for (AppNotification notif : notifications) {
                View itemView = inflater.inflate(R.layout.item_notification, container, false);
                
                TextView titleView = itemView.findViewById(R.id.notificationTitle);
                TextView bodyView = itemView.findViewById(R.id.notificationBody);
                TextView timeView = itemView.findViewById(R.id.notificationTime);
                View unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
                
                titleView.setText(notif.getTitle());
                bodyView.setText(notif.getBody());
                timeView.setText(formatNotificationTime(notif.getCreatedAt()));
                
                // Show/hide unread indicator
                unreadIndicator.setVisibility(notif.isRead() ? View.INVISIBLE : View.VISIBLE);
                
                // Click to open incident
                itemView.setOnClickListener(v -> {
                    if (notif.getIncidentId() != null && !notif.getIncidentId().isEmpty()) {
                        Intent intent = new Intent(getContext(), ResponderDetailActivity.class);
                        intent.putExtra("INCIDENT_KEY", notif.getIncidentId());
                        startActivity(intent);
                        
                        // Mark as read
                        if (!notif.isRead()) {
                            notificationsRepository.markAsReadAsync(notif.getId(),
                                    unit -> {
                                        loadNotificationCount();
                                        return Unit.INSTANCE;
                                    },
                                    error -> Unit.INSTANCE
                            );
                        }
                        dialog.dismiss();
                    } else {
                        Toast.makeText(getContext(), "No incident linked to this notification", Toast.LENGTH_SHORT).show();
                    }
                });
                
                container.addView(itemView);
            }
        }
        
        // Mark all as read button
        btnMarkAllRead.setOnClickListener(v -> {
            notificationsRepository.markAllAsReadAsync(
                    unit -> {
                        loadNotificationCount();
                        Toast.makeText(getContext(), "All notifications marked as read", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Toast.makeText(getContext(), "Failed to mark as read", Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
            );
        });
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private String formatNotificationTime(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = inputFormat.parse(createdAt.substring(0, Math.min(19, createdAt.length())));
            if (date != null) {
                long diff = System.currentTimeMillis() - date.getTime();
                long minutes = diff / (60 * 1000);
                long hours = diff / (60 * 60 * 1000);
                long days = diff / (24 * 60 * 60 * 1000);
                
                if (minutes < 1) return "Just now";
                if (minutes < 60) return minutes + "m ago";
                if (hours < 24) return hours + "h ago";
                if (days < 7) return days + "d ago";
                
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
                return outputFormat.format(date);
            }
        } catch (ParseException e) {
            // Ignore
        }
        return "";
    }

    // --- STEP 1: Load Officer Name ---
    private void loadOfficerInfo() {
        profileRepository.loadCurrentProfileAsync(
                profile -> {
                    if (profile != null) {
                        String name = profile.getDisplayName();
                        // Map agency_id to agency name
                        String agencyName = "";
                        if (profile.getAgencyId() != null) {
                            switch (profile.getAgencyId()) {
                                case 1: agencyName = "PNP"; break;
                                case 2: agencyName = "BFP"; break;
                                case 3: agencyName = "MDRRMO"; break;
                            }
                        }
                        if (name != null) {
                            txtOfficerName.setText(name + (!agencyName.isEmpty() ? " (" + agencyName + ")" : ""));
                        }
                    } else {
                        txtOfficerName.setText("Unknown Responder");
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    txtOfficerName.setText("Unknown Responder");
                    return Unit.INSTANCE;
                }
        );
    }

    // --- STEP 2 & 3: Load Stats & Ongoing Cases ---
    private void loadDashboardData() {
        // Show loading, hide content
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE);
        if (contentContainer != null) contentContainer.setVisibility(View.GONE);
        recyclerOngoingCases.setVisibility(View.GONE);

        incidentsRepository.loadAssignedIncidentsForToday(
                incidents -> {
                    // Hide loading, show content
                    if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                    if (contentContainer != null) contentContainer.setVisibility(View.VISIBLE);
                    recyclerOngoingCases.setVisibility(View.VISIBLE);

                    int activeCount = 0;
                    int completedCount = 0;
                    ongoingCasesList.clear();

                    for (IncidentSummary incident : incidents) {
                        String status = incident.getStatus();

                        // Completed if resolved/closed; everything else counted as active
                        boolean isCompleted = status != null && (
                                status.equalsIgnoreCase("resolved") ||
                                status.equalsIgnoreCase("closed")
                        );

                        // Count ALL assigned incidents regardless of creation date
                        if (isCompleted) {
                            completedCount++;
                        } else {
                            activeCount++;
                        }

                        // Ongoing list: anything not completed
                        if (!isCompleted) {
                            ongoingCasesList.add(incident);
                        }
                    }

                    txtActive.setText(String.valueOf(activeCount));
                    txtCompleted.setText(String.valueOf(completedCount));
                    txtTotalReports.setText(String.valueOf(activeCount + completedCount));
                    adapter.notifyDataSetChanged();

                    // Update case count label and show/hide empty state
                    if (ongoingCasesList.isEmpty()) {
                        if (txtCaseCount != null) txtCaseCount.setText("");
                        if (emptyStateContainer != null) emptyStateContainer.setVisibility(View.VISIBLE);
                        recyclerOngoingCases.setVisibility(View.GONE);
                    } else {
                        if (txtCaseCount != null) {
                            txtCaseCount.setText(ongoingCasesList.size() + " case" + (ongoingCasesList.size() > 1 ? "s" : ""));
                        }
                        if (emptyStateContainer != null) emptyStateContainer.setVisibility(View.GONE);
                        recyclerOngoingCases.setVisibility(View.VISIBLE);
                    }

                    // Stop refresh indicator
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                    return Unit.INSTANCE;
                },
                throwable -> {
                    // Hide loading, show content even on error
                    if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                    if (contentContainer != null) contentContainer.setVisibility(View.VISIBLE);
                    recyclerOngoingCases.setVisibility(View.VISIBLE);

                    // Stop refresh indicator
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    return Unit.INSTANCE;
                }
        );
    }

    // --- Inner Class: Recycler Adapter ---
    private static class OngoingCasesAdapter extends RecyclerView.Adapter<OngoingCasesAdapter.ViewHolder> {
        private final List<IncidentSummary> list;
        private final android.content.Context context;

        public OngoingCasesAdapter(List<IncidentSummary> list, android.content.Context context) {
            this.list = list;
            this.context = context;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ongoing_case, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            IncidentSummary incident = list.get(position);

            String type = incident.getAgencyType();
            String loc = incident.getLocationAddress();
            String createdAt = incident.getCreatedAt();
            String status = incident.getStatus();
            String incidentId = incident.getId();

            // Title
            holder.tvTitle.setText(type != null ? type.toUpperCase(Locale.getDefault()) : "Incident");

            // Incident ID
            holder.tvId.setText(incidentId != null ? "#" + incidentId.substring(0, Math.min(8, incidentId.length())).toUpperCase() : "#N/A");

            // Date and Time
            if (createdAt != null && createdAt.length() >= 16) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date date = inputFormat.parse(createdAt.substring(0, 19));
                    if (date != null) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        holder.tvDate.setText(dateFormat.format(date));
                        holder.tvTime.setText(timeFormat.format(date));
                    }
                } catch (ParseException e) {
                    holder.tvDate.setText("N/A");
                    holder.tvTime.setText("--:--");
                }
            } else {
                holder.tvDate.setText("N/A");
                holder.tvTime.setText("--:--");
            }

            // Location
            holder.tvLocation.setText(loc != null && !loc.isEmpty() ? loc : "Unknown Location");

            // Coordinates (optional - show if available)
            if (incident.getLatitude() != null && incident.getLongitude() != null) {
                String coords = String.format(Locale.getDefault(), "%.4f, %.4f",
                        incident.getLatitude(), incident.getLongitude());
                holder.tvCoordinates.setText(coords);
                holder.coordinatesRow.setVisibility(View.VISIBLE);
            } else {
                holder.coordinatesRow.setVisibility(View.GONE);
            }

            // Status
            if (status != null) {
                holder.tvStatus.setText(status.toUpperCase(Locale.getDefault()));
            }

            // Click listener
            holder.cardView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ResponderDetailActivity.class);
                intent.putExtra("INCIDENT_KEY", incidentId);
                intent.putExtra("INCIDENT_CODE", incidentId);
                intent.putExtra("AGENCY", type);
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardView;
            TextView tvTitle, tvId, tvDate, tvTime, tvLocation, tvCoordinates, tvStatus;
            LinearLayout coordinatesRow;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.caseCard);
                tvTitle = itemView.findViewById(R.id.caseTitle);
                tvId = itemView.findViewById(R.id.caseId);
                tvDate = itemView.findViewById(R.id.caseDate);
                tvTime = itemView.findViewById(R.id.caseTime);
                tvLocation = itemView.findViewById(R.id.caseLocation);
                tvCoordinates = itemView.findViewById(R.id.caseCoordinates);
                tvStatus = itemView.findViewById(R.id.caseStatus);
                coordinatesRow = itemView.findViewById(R.id.coordinatesRow);
            }
        }
    }
}