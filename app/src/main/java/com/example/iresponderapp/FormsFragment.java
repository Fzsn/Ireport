package com.example.iresponderapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FormsFragment extends Fragment {

    private LinearLayout formsListContainer;
    private String currentUid;

    public FormsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forms, container, false);
        formsListContainer = view.findViewById(R.id.formsListContainer);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            // REMOVED load calls here to prevent duplicates on startup.
            // The onResume() method will handle it.
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Clear the list completely before reloading to prevent duplicates
        if(formsListContainer != null) formsListContainer.removeAllViews();

        if (currentUid != null) {
            loadReportsFromFolder("PNP");
            loadReportsFromFolder("BFP");
            loadReportsFromFolder("MDRRMO");
        }
    }

    private void loadReportsFromFolder(String agencyFolder) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("IresponderApp").child("Reports").child(agencyFolder);

        // Use SingleValueEvent to load data ONCE.
        // This prevents the "infinite duplication" bug when navigating back and forth.
        ref.orderByChild("responderUid").equalTo(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        LayoutInflater inflater = LayoutInflater.from(getContext());

                        for (DataSnapshot data : snapshot.getChildren()) {
                            try {
                                final String incidentKey = data.child("incidentKey").getValue(String.class);
                                String date = data.child("timestamp").getValue(String.class);

                                // --- NEW DISPLAY LOGIC ---
                                String displayName = agencyFolder + " Incident"; // Default

                                if (agencyFolder.equals("MDRRMO")) {
                                    // CHANGED: Now looks for Nature/Type instead of Patient Name
                                    String nature = data.child("natureOfCall").getValue(String.class);
                                    String type = data.child("emergencyType").getValue(String.class);

                                    if (nature != null && !nature.isEmpty()) {
                                        displayName = nature;
                                        if (type != null && !type.isEmpty()) displayName += " (" + type + ")";
                                    } else {
                                        displayName = "Medical/Trauma Incident";
                                    }

                                } else if (agencyFolder.equals("BFP")) {
                                    String loc = data.child("fireLocation").getValue(String.class);
                                    if (loc != null) displayName = "Fire: " + loc;

                                } else if (agencyFolder.equals("PNP")) {
                                    displayName = "Crime Incident Report";
                                }

                                // Create the Card Row
                                View row = inflater.inflate(R.layout.item_submitted_form, formsListContainer, false);

                                String shortId = (incidentKey != null && incidentKey.length() > 5)
                                        ? incidentKey.substring(incidentKey.length() - 5) : "---";

                                ((TextView) row.findViewById(R.id.rowIncidentId)).setText("#" + shortId);
                                ((TextView) row.findViewById(R.id.rowPrimaryName)).setText(displayName);
                                ((TextView) row.findViewById(R.id.rowDate)).setText(date != null ? date.split(" ")[0] : "--");

                                ImageButton btnEdit = row.findViewById(R.id.btnRowEdit);
                                btnEdit.setOnClickListener(v -> openEditForm(agencyFolder, incidentKey));

                                formsListContainer.addView(row);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void openEditForm(String agency, String incidentKey) {
        Class<?> targetActivity;
        if (agency.equals("PNP")) targetActivity = PnpReportFormActivity.class;
        else if (agency.equals("BFP")) targetActivity = BfpReportFormActivity.class;
        else targetActivity = MdrrmoReportFormActivity.class;

        Intent intent = new Intent(getContext(), targetActivity);
        intent.putExtra("INCIDENT_KEY", incidentKey);
        intent.putExtra("IS_EDIT_MODE", true);
        startActivity(intent);
    }
}