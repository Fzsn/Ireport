package com.example.iresponderapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    // UI Components
    private TextView txtOfficerName, txtOfficerStatus;
    private TextView txtActive, txtCompleted, txtTotalReports;
    private RecyclerView recyclerOngoingCases;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String currentUid;

    // Adapter
    private OngoingCasesAdapter adapter;
    private List<DataSnapshot> ongoingCasesList;

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
        recyclerOngoingCases = view.findViewById(R.id.recyclerOngoingCases);

        // 2. Setup RecyclerView
        recyclerOngoingCases.setLayoutManager(new LinearLayoutManager(getContext()));
        ongoingCasesList = new ArrayList<>();
        adapter = new OngoingCasesAdapter(ongoingCasesList);
        recyclerOngoingCases.setAdapter(adapter);

        // 3. Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("IresponderApp");

        if (mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
            loadOfficerInfo();
            loadDashboardData();
        }

        return view;
    }

    // --- STEP 1: Load Officer Name ---
    private void loadOfficerInfo() {
        // We look up the user in "Responders" using their Auth ID
        // Note: As we fixed before, we query by "userId" to match the Auth UID
        mDatabase.child("Responders").orderByChild("userId").equalTo(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot responder : snapshot.getChildren()) {
                                String name = responder.child("fullName").getValue(String.class);
                                String agency = responder.child("agency").getValue(String.class);

                                // Display Name and Agency
                                if (name != null) {
                                    txtOfficerName.setText(name + (agency != null ? " (" + agency + ")" : ""));
                                }
                            }
                        } else {
                            txtOfficerName.setText("Unknown Responder");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // --- STEP 2 & 3: Load Stats & Ongoing Cases ---
    private void loadDashboardData() {
        // Get today's date string to filter stats
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()); // Matches your DB format "2025-12-4" style might vary, ensure format matches DB

        // Query Incidents assigned to this user
        mDatabase.child("Incidents_").orderByChild("AssignedResponderUID").equalTo(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int activeCount = 0;
                        int completedCount = 0;
                        ongoingCasesList.clear();

                        for (DataSnapshot data : snapshot.getChildren()) {
                            String status = data.child("Status").getValue(String.class);
                            String dbDate = data.child("date").getValue(String.class); // "2025-12-4"

                            boolean isToday = (dbDate != null && dbDate.equals(todayDate));

                            // --- Logic for Counters (Today Only) ---
                            if (isToday) {
                                if ("Completed".equalsIgnoreCase(status)) {
                                    completedCount++;
                                } else {
                                    activeCount++;
                                }
                            }

                            // --- Logic for Ongoing Cases List (All dates, just active status) ---
                            // "Ongoing" means it is assigned to me but NOT completed yet
                            if (!"Completed".equalsIgnoreCase(status)) {
                                ongoingCasesList.add(data);
                            }
                        }

                        // Update UI Stats
                        txtActive.setText(String.valueOf(activeCount));
                        txtCompleted.setText(String.valueOf(completedCount));
                        txtTotalReports.setText(String.valueOf(activeCount + completedCount));

                        // Update UI List
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // --- Inner Class: Recycler Adapter ---
    private static class OngoingCasesAdapter extends RecyclerView.Adapter<OngoingCasesAdapter.ViewHolder> {
        private final List<DataSnapshot> list;

        public OngoingCasesAdapter(List<DataSnapshot> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ongoing_case, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DataSnapshot data = list.get(position);

            String type = data.child("incidentType").getValue(String.class);
            String loc = data.child("address").getValue(String.class);
            String time = data.child("Time").getValue(String.class);
            String status = data.child("Status").getValue(String.class);

            holder.tvTitle.setText(type != null ? type : "Incident");
            holder.tvLocation.setText(loc != null ? loc : "Unknown Location");
            holder.tvTime.setText(time != null ? time : "--:--");

            if(status != null) holder.tvStatus.setText(status.toUpperCase());
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvLocation, tvTime, tvStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.caseTitle);
                tvLocation = itemView.findViewById(R.id.caseLocation);
                tvTime = itemView.findViewById(R.id.caseTime);
                tvStatus = itemView.findViewById(R.id.caseStatus);
            }
        }
    }
}