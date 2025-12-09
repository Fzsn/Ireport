package com.example.iresponderapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.iresponderapp.supabase.AppNotification;
import com.example.iresponderapp.supabase.NotificationsRealtimeManager;
import com.example.iresponderapp.supabase.SupabaseNotificationsRepository;
import com.example.iresponderapp.supabase.SupabaseResponderProfileRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;

public class ResponderDashboard extends AppCompatActivity implements NotificationsRealtimeManager.Listener {

    private BottomNavigationView bottomNavigationView;
    private TextView headerUserName, headerUserAgency, headerProfileInitial;
    private FrameLayout headerProfileButton;
    private SupabaseResponderProfileRepository profileRepository;
    private SupabaseNotificationsRepository notificationsRepository;
    private NotificationsRealtimeManager realtimeManager;
    private TextView notificationBadge;
    private FrameLayout notificationContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_dashboard);

        initViews();
        setupHeader();
        
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

    private void initViews() {
        View headerView = findViewById(R.id.appHeader);
        headerUserName = headerView.findViewById(R.id.headerUserName);
        headerUserAgency = headerView.findViewById(R.id.headerUserAgency);
        headerProfileInitial = headerView.findViewById(R.id.headerProfileInitial);
        headerProfileButton = headerView.findViewById(R.id.headerProfileButton);
        notificationBadge = headerView.findViewById(R.id.notificationBadge);
        notificationContainer = headerView.findViewById(R.id.notificationContainer);

        IreportApp app = (IreportApp) getApplication();
        profileRepository = (SupabaseResponderProfileRepository) app.getResponderProfileRepository();
        notificationsRepository = app.getNotificationsRepository();
        realtimeManager = app.getNotificationsRealtimeManager();

        // Profile button click - go to profile tab
        headerProfileButton.setOnClickListener(v -> {
            bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        });
        
        // Notification bell click - show notifications dialog
        if (notificationContainer != null) {
            notificationContainer.setOnClickListener(v -> openNotificationsList());
        }
        
        // Load initial notification count
        loadNotificationCount();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (realtimeManager != null) {
            realtimeManager.addListener(this);
        }
        loadNotificationCount();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (realtimeManager != null) {
            realtimeManager.removeListener(this);
        }
    }
    
    // --- NotificationsRealtimeManager.Listener implementation ---
    @Override
    public void onNewNotification(AppNotification notification) {
        runOnUiThread(() -> {
            Toast.makeText(this, notification.getTitle(), Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onUnreadCountChanged(int count) {
        runOnUiThread(() -> updateNotificationBadge(count));
    }
    
    // --- Notification helpers ---
    private void loadNotificationCount() {
        if (notificationsRepository == null) return;
        notificationsRepository.loadUnreadCountAsync(
                count -> {
                    runOnUiThread(() -> updateNotificationBadge(count));
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
        notificationsRepository.loadAllNotificationsAsync(
                notifications -> {
                    showNotificationsDialog(notifications);
                    return Unit.INSTANCE;
                },
                error -> {
                    Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
        );
    }
    
    private void showNotificationsDialog(List<AppNotification> notifications) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notifications, null);
        LinearLayout container = dialogView.findViewById(R.id.notificationsContainer);
        TextView emptyText = dialogView.findViewById(R.id.emptyText);
        TextView btnMarkAllRead = dialogView.findViewById(R.id.btnMarkAllRead);
        TextView btnClose = dialogView.findViewById(R.id.btnClose);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        if (notifications.isEmpty()) {
            container.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            btnMarkAllRead.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            
            LayoutInflater inflater = LayoutInflater.from(this);
            for (AppNotification notif : notifications) {
                View itemView = inflater.inflate(R.layout.item_notification, container, false);
                
                TextView titleView = itemView.findViewById(R.id.notificationTitle);
                TextView bodyView = itemView.findViewById(R.id.notificationBody);
                TextView timeView = itemView.findViewById(R.id.notificationTime);
                View unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
                
                titleView.setText(notif.getTitle());
                bodyView.setText(notif.getBody());
                timeView.setText(formatNotificationTime(notif.getCreatedAt()));
                unreadIndicator.setVisibility(notif.isRead() ? View.INVISIBLE : View.VISIBLE);
                
                itemView.setOnClickListener(v -> {
                    if (notif.getIncidentId() != null && !notif.getIncidentId().isEmpty()) {
                        Intent intent = new Intent(this, ResponderDetailActivity.class);
                        intent.putExtra("INCIDENT_KEY", notif.getIncidentId());
                        startActivity(intent);
                        
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
                        Toast.makeText(this, "No incident linked", Toast.LENGTH_SHORT).show();
                    }
                });
                
                container.addView(itemView);
            }
        }
        
        btnMarkAllRead.setOnClickListener(v -> {
            notificationsRepository.markAllAsReadAsync(
                    unit -> {
                        loadNotificationCount();
                        Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Toast.makeText(this, "Failed to mark as read", Toast.LENGTH_SHORT).show();
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

    private void setupHeader() {
        profileRepository.loadCurrentProfileAsync(
                profile -> {
                    runOnUiThread(() -> {
                        if (profile != null) {
                            String name = profile.getDisplayName();
                            if (name != null && !name.isEmpty()) {
                                String trimmedName = name.trim();
                                String[] parts = trimmedName.split("\\s+");
                                String firstName = parts.length > 0 ? parts[0] : trimmedName;
                                headerUserName.setText(firstName);

                                // Set initials using first and last parts when available
                                String initials = "";
                                if (parts.length >= 2) {
                                    initials = parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1);
                                } else if (parts.length == 1) {
                                    initials = parts[0].substring(0, Math.min(2, parts[0].length()));
                                }
                                headerProfileInitial.setText(initials.toUpperCase());
                            } else {
                                headerUserName.setText("Responder");
                                headerProfileInitial.setText("R");
                            }

                            // Set agency
                            String agencyName = "";
                            if (profile.getAgencyId() != null) {
                                switch (profile.getAgencyId()) {
                                    case 1: agencyName = "PNP"; break;
                                    case 2: agencyName = "BFP"; break;
                                    case 3: agencyName = "MDRRMO"; break;
                                }
                            }
                            headerUserAgency.setText(agencyName);
                        }
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    runOnUiThread(() -> {
                        headerUserName.setText("Officer");
                        headerProfileInitial.setText("O");
                    });
                    return Unit.INSTANCE;
                }
        );
    }
}
