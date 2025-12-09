package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.iresponderapp.supabase.SupabaseUnitReportsRepository;
import com.example.iresponderapp.supabase.UnitReport;

import kotlin.Unit;

public class FormsFragment extends Fragment {

    private LinearLayout formsListContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SupabaseUnitReportsRepository unitReportsRepository;
    private boolean isLoading = false;

    public FormsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forms, container, false);
        formsListContainer = view.findViewById(R.id.formsListContainer);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        // Initialize Supabase repository
        IreportApp app = (IreportApp) requireActivity().getApplication();
        unitReportsRepository = (SupabaseUnitReportsRepository) app.getUnitReportsRepository();

        // Setup pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadAllReports();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Clear the list completely before reloading to prevent duplicates
        if(formsListContainer != null) formsListContainer.removeAllViews();

        if (unitReportsRepository != null) {
            loadAllReports();
        }
    }

    private void loadAllReports() {
        if (isLoading) return;
        isLoading = true;
        unitReportsRepository.loadAllMyReportsAsync(
                reports -> {
                    if (getContext() == null) return Unit.INSTANCE;

                    // Clear existing views to prevent duplicates on refresh
                    formsListContainer.removeAllViews();

                    LayoutInflater inflater = LayoutInflater.from(getContext());

                    for (UnitReport report : reports) {
                        try {
                            final String incidentKey = report.getIncidentId();
                            String agency = report.getAgency();
                            String title = report.getTitle();
                            String createdAt = report.getCreatedAt();
                            String date = createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : "--";
                            
                            // Get report type from details for MDRRMO reports
                            final String reportType = getReportType(report);

                            // Create the Card Row
                            View row = inflater.inflate(R.layout.item_submitted_form, formsListContainer, false);

                            String shortId = (incidentKey != null && incidentKey.length() > 8)
                                    ? incidentKey.substring(0, 8).toUpperCase() : "---";

                            ((TextView) row.findViewById(R.id.rowIncidentId)).setText("#" + shortId);
                            ((TextView) row.findViewById(R.id.rowPrimaryName)).setText(title != null ? title : agency + " Report");
                            ((TextView) row.findViewById(R.id.rowDate)).setText(date);

                            // Set agency badge
                            TextView agencyBadge = row.findViewById(R.id.rowAgencyBadge);
                            agencyBadge.setText(agency);
                            // Set badge color based on agency
                            int badgeColor;
                            switch (agency) {
                                case "PNP": badgeColor = 0xFF1565C0; break; // Blue
                                case "BFP": badgeColor = 0xFFD32F2F; break; // Red
                                case "MDRRMO": badgeColor = 0xFF388E3C; break; // Green
                                default: badgeColor = 0xFF757575; break; // Gray
                            }
                            agencyBadge.getBackground().setTint(badgeColor);

                            ImageButton btnEdit = row.findViewById(R.id.btnRowEdit);
                            btnEdit.setOnClickListener(v -> openEditForm(agency, incidentKey, reportType));

                            formsListContainer.addView(row);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    // Stop refresh animation
                    isLoading = false;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    // Handle error silently
                    // Stop refresh animation
                    isLoading = false;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    return Unit.INSTANCE;
                }
        );
    }

    private String getReportType(UnitReport report) {
        try {
            if (report.getDetails() != null) {
                org.json.JSONObject details = new org.json.JSONObject(report.getDetails().toString());
                return details.optString("report_type", "MEDICAL");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "MEDICAL"; // Default to medical/rescue form
    }

    private void openEditForm(String agency, String incidentKey, String reportType) {
        Class<?> targetActivity;
        if (agency.equals("PNP")) {
            targetActivity = PnpReportFormActivity.class;
        } else if (agency.equals("BFP")) {
            targetActivity = BfpReportFormActivity.class;
        } else if (agency.equals("MDRRMO")) {
            // Check report type for MDRRMO
            if ("DISASTER".equals(reportType)) {
                targetActivity = MdrrmoDisasterReportFormActivity.class;
            } else {
                targetActivity = MdrrmoReportFormActivity.class;
            }
        } else {
            targetActivity = MdrrmoReportFormActivity.class;
        }

        Intent intent = new Intent(getContext(), targetActivity);
        intent.putExtra("INCIDENT_KEY", incidentKey);
        intent.putExtra("IS_EDIT_MODE", true);
        startActivity(intent);
    }
}