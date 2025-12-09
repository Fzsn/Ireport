package com.example.iresponderapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iresponderapp.adapter.MediaPreviewAdapter;
import com.example.iresponderapp.util.ReportDraftManager;
import com.example.iresponderapp.supabase.FinalReportDraft;
import com.example.iresponderapp.supabase.SupabaseAuthRepository;
import com.example.iresponderapp.supabase.SupabaseFinalReportDraftsRepository;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;
import com.example.iresponderapp.supabase.SupabaseStorageRepository;
import com.example.iresponderapp.supabase.SupabaseUnitReportsRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kotlin.Unit;

public class PnpReportFormActivity extends AppCompatActivity {

    private static final String TAG = "PnpReportForm";

    // Read-only UI elements
    private TextView formIncidentType, formIncidentDate, formReportedBy, formIncidentAddress, formIncidentDescription;

    // Containers and Buttons
    private LinearLayout containerSuspects;
    private LinearLayout containerVictims;
    private Button btnAddSuspect, btnAddVictim;

    private EditText editIncidentNarrative;
    private Button btnSubmit;
    
    // Evidence capture buttons
    private Button btnCapturePhoto, btnCaptureVideo;
    private TextView tvEvidenceCount;
    private RecyclerView recyclerMediaPreview;
    private MediaPreviewAdapter mediaPreviewAdapter;
    private int evidenceCount = 0;
    private List<Uri> capturedMediaUris = new ArrayList<>();
    private Uri photoUri;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Intent> takeVideoLauncher;

    // Supabase repositories
    private String incidentKey;
    private SupabaseIncidentsRepository incidentsRepository;
    private SupabaseUnitReportsRepository reportsRepository;
    private SupabaseAuthRepository authRepository;
    private SupabaseFinalReportDraftsRepository draftsRepository;
    private SupabaseStorageRepository storageRepository;
    private String currentResponderUid;
    private List<String> uploadedMediaUrls = new ArrayList<>();
    
    // Intent data
    private String incidentType;
    private String incidentReporter;
    private String incidentAddress;
    private String incidentDescription;
    private String incidentCreatedAt;
    
    // Draft manager
    private ReportDraftManager draftManager;
    private static final String AGENCY_TYPE = "PNP";
    
    // Read-only mode for viewing submitted reports
    private boolean isReadOnlyMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pnp_report_form);

        // --- 1. Get Intent Data ---
        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        if (incidentKey == null) {
            Toast.makeText(this, "Error: Incident Key Missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Get passed incident data
        incidentType = getIntent().getStringExtra("INCIDENT_TYPE");
        incidentReporter = getIntent().getStringExtra("INCIDENT_REPORTER");
        incidentAddress = getIntent().getStringExtra("INCIDENT_ADDRESS");
        incidentDescription = getIntent().getStringExtra("INCIDENT_DESCRIPTION");
        incidentCreatedAt = getIntent().getStringExtra("INCIDENT_CREATED_AT");

        // --- 2. Supabase Init ---
        IreportApp app = (IreportApp) getApplication();
        incidentsRepository = (SupabaseIncidentsRepository) app.getIncidentsRepository();
        reportsRepository = (SupabaseUnitReportsRepository) app.getUnitReportsRepository();
        authRepository = (SupabaseAuthRepository) app.getAuthRepository();
        draftsRepository = app.getFinalReportDraftsRepository();
        storageRepository = app.getStorageRepository();
        
        currentResponderUid = authRepository.getCurrentUserId();
        if (currentResponderUid == null) {
            currentResponderUid = "Unknown";
        }
        
        // Initialize local draft manager (fallback for offline)
        draftManager = new ReportDraftManager(this);

        // --- 3. Initialize Camera Launchers ---
        initCameraLaunchers();

        // --- 4. Initialize UI ---
        initUiElements();

        // --- 5. Load Header Data from Intent ---
        loadIncidentHeaderData();

        // --- 6. Add default blank cards ---
        addPersonCard(containerSuspects, "Suspect");
        addPersonCard(containerVictims, "Victim");

        // --- 7. Setup Listeners ---
        btnAddSuspect.setOnClickListener(v -> addPersonCard(containerSuspects, "Suspect"));
        btnAddVictim.setOnClickListener(v -> addPersonCard(containerVictims, "Victim"));
        
        btnCapturePhoto.setOnClickListener(v -> capturePhoto());
        btnCaptureVideo.setOnClickListener(v -> captureVideo());

        btnSubmit.setOnClickListener(v -> validateAndSubmit());
        
        // --- 8. Check if this is a submitted report (read-only mode) ---
        isReadOnlyMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        if (isReadOnlyMode) {
            enableReadOnlyMode();
            loadIncidentDataFromRepo(); // Fetch incident details from DB
            loadSubmittedReportData();
        } else {
            // Check for existing draft only if not in read-only mode
            checkAndRestoreDraft();
        }
    }

    private void initUiElements() {
        formIncidentType = findViewById(R.id.formIncidentType);
        formIncidentDate = findViewById(R.id.formIncidentDate);
        formReportedBy = findViewById(R.id.formReportedBy);
        formIncidentAddress = findViewById(R.id.formIncidentAddress);
        formIncidentDescription = findViewById(R.id.formIncidentDescription);

        containerSuspects = findViewById(R.id.containerSuspects);
        containerVictims = findViewById(R.id.containerVictims);
        btnAddSuspect = findViewById(R.id.btnAddSuspect);
        btnAddVictim = findViewById(R.id.btnAddVictim);

        editIncidentNarrative = findViewById(R.id.editIncidentNarrative);
        btnSubmit = findViewById(R.id.btnSubmitPnpReport);
        
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto);
        btnCaptureVideo = findViewById(R.id.btnCaptureVideo);
        tvEvidenceCount = findViewById(R.id.tvEvidenceCount);
        
        // Setup media preview RecyclerView
        recyclerMediaPreview = findViewById(R.id.recyclerMediaPreview);
        recyclerMediaPreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mediaPreviewAdapter = new MediaPreviewAdapter(this, capturedMediaUris, (position, uri) -> {
            capturedMediaUris.remove(position);
            mediaPreviewAdapter.notifyItemRemoved(position);
            updateEvidenceCount();
        });
        recyclerMediaPreview.setAdapter(mediaPreviewAdapter);
    }
    
    private void initCameraLaunchers() {
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (result) {
                evidenceCount++;
                capturedMediaUris.add(photoUri);
                updateEvidenceCount();
                Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show();
                // Upload immediately as draft evidence
                uploadMediaToDraft(photoUri, capturedMediaUris.size() - 1);
            }
        });

        takeVideoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri videoUri = result.getData().getData();
                if (videoUri != null) {
                    evidenceCount++;
                    capturedMediaUris.add(videoUri);
                    updateEvidenceCount();
                    Toast.makeText(this, "Video recorded!", Toast.LENGTH_SHORT).show();
                    // Upload immediately as draft evidence
                    uploadMediaToDraft(videoUri, capturedMediaUris.size() - 1);
                }
            }
        });
    }
    
    private void updateEvidenceCount() {
        int count = capturedMediaUris.size();
        if (count == 0) {
            tvEvidenceCount.setText("No evidence captured");
            recyclerMediaPreview.setVisibility(View.GONE);
        } else {
            tvEvidenceCount.setText(count + " evidence file(s) captured");
            recyclerMediaPreview.setVisibility(View.VISIBLE);
            mediaPreviewAdapter.notifyDataSetChanged();
        }
    }
    
    private void capturePhoto() {
        try {
            File photoFile = createMediaFile("IMG_", ".jpg", Environment.DIRECTORY_PICTURES);
            photoUri = FileProvider.getUriForFile(this, "com.example.iresponderapp.fileprovider", photoFile);
            takePictureLauncher.launch(photoUri);
        } catch (IOException ex) {
            Log.e(TAG, "Error creating image file", ex);
            Toast.makeText(this, "Error: Could not create image file", Toast.LENGTH_SHORT).show();
        }
    }

    private void captureVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60); // 60 seconds max
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            takeVideoLauncher.launch(takeVideoIntent);
        } else {
            Toast.makeText(this, "No video app available", Toast.LENGTH_SHORT).show();
        }
    }

    private File createMediaFile(String prefix, String suffix, String directory) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = prefix + timeStamp + "_";
        File storageDir = getExternalFilesDir(directory);
        return File.createTempFile(fileName, suffix, storageDir);
    }

    // --- Dynamic Card Logic (Adds a blank card) ---
    private void addPersonCard(LinearLayout container, String title) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);

        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title + " Data");

        // Setup Remove Button Logic
        View btnRemove = view.findViewById(R.id.btnRemoveEntry);
        btnRemove.setOnClickListener(v -> container.removeView(view));

        container.addView(view);
    }

    // --- Load Incident Header from Intent Data ---
    private void loadIncidentHeaderData() {
        // Format date/time
        String dateTime = "N/A";
        if (incidentCreatedAt != null && incidentCreatedAt.length() >= 16) {
            dateTime = incidentCreatedAt.substring(0, 10) + " " + incidentCreatedAt.substring(11, 16);
        }
        
        formIncidentType.setText("Incident Type: " + (incidentType != null ? incidentType : "PNP"));
        formIncidentDate.setText("Date & Time: " + dateTime);
        formReportedBy.setText("Reported by: " + (incidentReporter != null ? incidentReporter : "N/A"));
        formIncidentAddress.setText("Address: " + (incidentAddress != null ? incidentAddress : "N/A"));
        formIncidentDescription.setText("Description: " + (incidentDescription != null ? incidentDescription : "N/A"));
    }

    // --- Validation ---
    private void validateAndSubmit() {
        String narrative = editIncidentNarrative.getText().toString().trim();
        
        if (TextUtils.isEmpty(narrative)) {
            editIncidentNarrative.setError("Narrative is required");
            editIncidentNarrative.requestFocus();
            Toast.makeText(this, "Please provide a detailed narrative", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (narrative.length() < 20) {
            editIncidentNarrative.setError("Narrative is too short (minimum 20 characters)");
            editIncidentNarrative.requestFocus();
            Toast.makeText(this, "Please provide more details in the narrative", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showConfirmationDialog();
    }
    
    // --- Submission Logic ---
    private void showConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Submission")
                .setMessage("Are you sure you want to submit this report? The incident status will be set to RESOLVED.")
                .setPositiveButton("Yes, Submit", (dialog, which) -> submitPnpReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitPnpReport() {
        // Show uploading progress
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Uploading...");
        
        // If there are media files to upload, upload them first
        if (!capturedMediaUris.isEmpty()) {
            uploadMediaFilesAndSubmit();
        } else {
            // No media to upload, submit directly
            submitReportWithMedia(new ArrayList<>());
        }
    }
    
    private void uploadMediaFilesAndSubmit() {
        uploadedMediaUrls.clear();
        uploadNextMedia(0);
    }
    
    private void uploadNextMedia(int index) {
        if (index >= capturedMediaUris.size()) {
            // All files uploaded, now submit the report
            submitReportWithMedia(uploadedMediaUrls);
            return;
        }
        
        Uri mediaUri = capturedMediaUris.get(index);
        
        // Check if this is already a remote URL (from restored draft)
        String uriString = mediaUri.toString();
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            // Already uploaded, just add to list and continue
            uploadedMediaUrls.add(uriString);
            Log.d(TAG, "Media already uploaded (from draft): " + uriString);
            uploadNextMedia(index + 1);
            return;
        }
        
        try {
            byte[] fileBytes = readBytesFromUri(mediaUri);
            String fileName = generateMediaFileName(index, mediaUri);
            String contentType = getContentResolver().getType(mediaUri);
            if (contentType == null) {
                contentType = fileName.endsWith(".mp4") ? "video/mp4" : "image/jpeg";
            }
            
            storageRepository.uploadFileAsync(
                    fileBytes,
                    fileName,
                    contentType,
                    url -> {
                        uploadedMediaUrls.add(url);
                        Log.d(TAG, "Uploaded media " + (index + 1) + "/" + capturedMediaUris.size() + ": " + url);
                        tvEvidenceCount.setText("Uploading " + (index + 2) + "/" + capturedMediaUris.size() + "...");
                        uploadNextMedia(index + 1);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.e(TAG, "Failed to upload media: " + error.getMessage());
                        Toast.makeText(this, "Failed to upload media: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Submit Report");
                        return Unit.INSTANCE;
                    }
            );
        } catch (IOException e) {
            Log.e(TAG, "Error reading media file: " + e.getMessage());
            Toast.makeText(this, "Error reading media file", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            btnSubmit.setText("Submit Report");
        }
    }
    
    private byte[] readBytesFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) throw new IOException("Cannot open input stream");
        
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        inputStream.close();
        return buffer.toByteArray();
    }
    
    private String generateMediaFileName(int index, Uri uri) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String contentType = getContentResolver().getType(uri);
        String extension = ".jpg";
        if (contentType != null) {
            if (contentType.contains("video")) extension = ".mp4";
            else if (contentType.contains("png")) extension = ".png";
            else if (contentType.contains("webp")) extension = ".webp";
        }
        return "pnp/" + incidentKey + "/" + timestamp + "_" + index + extension;
    }
    
    private void uploadMediaToDraft(Uri mediaUri, int index) {
        try {
            byte[] fileBytes = readBytesFromUri(mediaUri);
            String fileName = "drafts/" + generateMediaFileName(index, mediaUri);
            String contentType = getContentResolver().getType(mediaUri);
            if (contentType == null) {
                contentType = fileName.endsWith(".mp4") ? "video/mp4" : "image/jpeg";
            }
            
            storageRepository.uploadFileAsync(fileBytes, fileName, contentType,
                url -> {
                    int uriIndex = capturedMediaUris.indexOf(mediaUri);
                    if (uriIndex != -1) {
                        capturedMediaUris.set(uriIndex, Uri.parse(url));
                        Log.d(TAG, "Draft media uploaded: " + url);
                    }
                    saveDraftToServer();
                    return Unit.INSTANCE;
                },
                error -> {
                    Log.e(TAG, "Failed to upload draft media: " + error.getMessage());
                    Toast.makeText(this, "Warning: Failed to backup evidence", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
            );
        } catch (IOException e) {
            Log.e(TAG, "Error reading media file for draft: " + e.getMessage());
        }
    }
    
    private void submitReportWithMedia(List<String> mediaUrls) {
        Map<String, Object> reportDetails = new HashMap<>();
        reportDetails.put("narrative", editIncidentNarrative.getText().toString().trim());
        reportDetails.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        reportDetails.put("evidence_count", String.valueOf(mediaUrls.size()));
        reportDetails.put("media_urls", new JSONArray(mediaUrls).toString());

        // Collect Data from Dynamic Views
        List<Map<String, String>> suspects = collectPersonData(containerSuspects);
        List<Map<String, String>> victims = collectPersonData(containerVictims);
        reportDetails.put("suspects_count", String.valueOf(suspects.size()));
        reportDetails.put("victims_count", String.valueOf(victims.size()));

        // Flatten suspects/victims to string for JSON
        StringBuilder suspectsStr = new StringBuilder();
        for (Map<String, String> s : suspects) {
            suspectsStr.append(s.get("firstName")).append(" ").append(s.get("lastName")).append("; ");
        }
        reportDetails.put("suspects", suspectsStr.toString());

        StringBuilder victimsStr = new StringBuilder();
        for (Map<String, String> v : victims) {
            victimsStr.append(v.get("firstName")).append(" ").append(v.get("lastName")).append("; ");
        }
        reportDetails.put("victims", victimsStr.toString());

        // Save to Supabase
        reportsRepository.createOrUpdateReportAsync(
                incidentKey,
                "PNP",
                "PNP Crime Report",
                reportDetails,
                unit -> {
                    markIncidentAsCompleted();
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Toast.makeText(this, "Failed to submit report: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit Report");
                    return Unit.INSTANCE;
                }
        );
    }

    // Loops through the layout container and extracts data from each card
    private List<Map<String, String>> collectPersonData(LinearLayout container) {
        List<Map<String, String>> personList = new ArrayList<>();

        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);

            EditText fName = card.findViewById(R.id.editFirstName);
            EditText mName = card.findViewById(R.id.editMiddleName);
            EditText lName = card.findViewById(R.id.editLastName);
            EditText address = card.findViewById(R.id.editAddress);
            EditText occupation = card.findViewById(R.id.editOccupation);
            EditText status = card.findViewById(R.id.editStatus);

            Map<String, String> personData = new HashMap<>();
            personData.put("firstName", fName.getText().toString().trim());
            personData.put("middleName", mName.getText().toString().trim());
            personData.put("lastName", lName.getText().toString().trim());
            personData.put("address", address.getText().toString().trim());
            personData.put("occupation", occupation.getText().toString().trim());
            personData.put("status", status.getText().toString().trim());

            // Only add to the list if at least a name is provided
            if (!personData.get("firstName").isEmpty() || !personData.get("lastName").isEmpty()) {
                personList.add(personData);
            }
        }
        return personList;
    }

    private void markIncidentAsCompleted() {
        // Delete drafts after successful submission (both server and local)
        draftsRepository.deleteDraftAsync(incidentKey, u -> Unit.INSTANCE, e -> Unit.INSTANCE);
        draftManager.deleteDraft(incidentKey, AGENCY_TYPE);
        
        incidentsRepository.updateIncidentStatusAsync(
                incidentKey,
                "resolved",
                unit -> {
                    Toast.makeText(this, "Report Submitted & Incident Resolved!", Toast.LENGTH_LONG).show();
                    finish();
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Toast.makeText(this, "Report saved but failed to update status.", Toast.LENGTH_SHORT).show();
                    finish();
                    return Unit.INSTANCE;
                }
        );
    }
    
    // --- Draft Management (Server-side with local fallback) ---
    @Override
    protected void onPause() {
        super.onPause();
        saveDraftToServer();
    }
    
    private void saveDraftToServer() {
        if (incidentKey == null || isReadOnlyMode) return;
        
        Map<String, Object> draftDetails = buildDraftDetailsMap();
        
        draftsRepository.saveDraftAsync(
                incidentKey,
                AGENCY_TYPE,
                draftDetails,
                "draft",
                unit -> {
                    Log.d(TAG, "Draft saved to server for incident: " + incidentKey);
                    saveLocalDraft();
                    return Unit.INSTANCE;
                },
                error -> {
                    Log.e(TAG, "Failed to save draft to server: " + error.getMessage());
                    saveLocalDraft();
                    return Unit.INSTANCE;
                }
        );
    }
    
    private Map<String, Object> buildDraftDetailsMap() {
        Map<String, Object> draftDetails = new HashMap<>();
        draftDetails.put("narrative", editIncidentNarrative.getText().toString());
        draftDetails.put("evidence_count", String.valueOf(capturedMediaUris.size()));
        
        // Save media URLs (already uploaded or to be uploaded)
        JSONArray mediaUrlsArray = new JSONArray();
        for (Uri uri : capturedMediaUris) {
            mediaUrlsArray.put(uri.toString());
        }
        draftDetails.put("media_urls", mediaUrlsArray.toString());
        
        try {
            JSONArray suspectsArray = new JSONArray();
            for (int i = 0; i < containerSuspects.getChildCount(); i++) {
                View card = containerSuspects.getChildAt(i);
                JSONObject person = extractPersonAsJson(card);
                if (person != null) {
                    suspectsArray.put(person);
                }
            }
            draftDetails.put("suspects", suspectsArray.toString());

            JSONArray victimsArray = new JSONArray();
            for (int i = 0; i < containerVictims.getChildCount(); i++) {
                View card = containerVictims.getChildAt(i);
                JSONObject person = extractPersonAsJson(card);
                if (person != null) {
                    victimsArray.put(person);
                }
            }
            draftDetails.put("victims", victimsArray.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error building draft JSON: " + e.getMessage());
        }
        
        return draftDetails;
    }

    private JSONObject extractPersonAsJson(View card) {
        try {
            EditText fName = card.findViewById(R.id.editFirstName);
            EditText mName = card.findViewById(R.id.editMiddleName);
            EditText lName = card.findViewById(R.id.editLastName);
            EditText address = card.findViewById(R.id.editAddress);
            EditText occupation = card.findViewById(R.id.editOccupation);
            EditText status = card.findViewById(R.id.editStatus);

            JSONObject person = new JSONObject();
            person.put("firstName", fName.getText().toString().trim());
            person.put("middleName", mName.getText().toString().trim());
            person.put("lastName", lName.getText().toString().trim());
            person.put("address", address.getText().toString().trim());
            person.put("occupation", occupation.getText().toString().trim());
            person.put("status", status.getText().toString().trim());
            return person;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveLocalDraft() {
        if (draftManager == null) return;

        ReportDraftManager.ReportDraft draft = new ReportDraftManager.ReportDraft();
        draft.narrative = editIncidentNarrative.getText().toString();
        draft.setMediaUris(capturedMediaUris);

        draft.suspects = new ArrayList<>();
        for (int i = 0; i < containerSuspects.getChildCount(); i++) {
            View card = containerSuspects.getChildAt(i);
            ReportDraftManager.PersonData person = extractPersonFromCard(card);
            if (person != null) {
                draft.suspects.add(person);
            }
        }

        draft.victims = new ArrayList<>();
        for (int i = 0; i < containerVictims.getChildCount(); i++) {
            View card = containerVictims.getChildAt(i);
            ReportDraftManager.PersonData person = extractPersonFromCard(card);
            if (person != null) {
                draft.victims.add(person);
            }
        }

        draftManager.saveDraft(incidentKey, AGENCY_TYPE, draft);
        Log.d(TAG, "Draft saved locally for incident: " + incidentKey);
    }

    private ReportDraftManager.PersonData extractPersonFromCard(View card) {
        EditText fName = card.findViewById(R.id.editFirstName);
        EditText mName = card.findViewById(R.id.editMiddleName);
        EditText lName = card.findViewById(R.id.editLastName);
        EditText address = card.findViewById(R.id.editAddress);
        EditText occupation = card.findViewById(R.id.editOccupation);
        EditText status = card.findViewById(R.id.editStatus);

        return new ReportDraftManager.PersonData(
                fName.getText().toString().trim(),
                mName.getText().toString().trim(),
                lName.getText().toString().trim(),
                address.getText().toString().trim(),
                occupation.getText().toString().trim(),
                status.getText().toString().trim()
        );
    }

    private void checkAndRestoreDraft() {
        // First try to load from server
        draftsRepository.getDraftAsync(
                incidentKey,
                serverDraft -> {
                    if (serverDraft != null) {
                        new AlertDialog.Builder(this)
                                .setTitle("Restore Draft")
                                .setMessage("You have a saved draft for this report. Would you like to restore it?")
                                .setPositiveButton("Restore", (dialog, which) -> restoreServerDraft(serverDraft))
                                .setNegativeButton("Discard", (dialog, which) -> {
                                    draftsRepository.deleteDraftAsync(incidentKey, u -> Unit.INSTANCE, e -> Unit.INSTANCE);
                                    draftManager.deleteDraft(incidentKey, AGENCY_TYPE);
                                })
                                .setCancelable(false)
                                .show();
                    } else {
                        checkLocalDraft();
                    }
                    return Unit.INSTANCE;
                },
                error -> {
                    Log.e(TAG, "Failed to check server draft: " + error.getMessage());
                    checkLocalDraft();
                    return Unit.INSTANCE;
                }
        );
    }

    private void checkLocalDraft() {
        if (draftManager != null && draftManager.hasDraft(incidentKey, AGENCY_TYPE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Restore Local Draft")
                    .setMessage("You have a locally saved draft. Would you like to restore it?")
                    .setPositiveButton("Restore", (dialog, which) -> restoreLocalDraft())
                    .setNegativeButton("Discard", (dialog, which) -> draftManager.deleteDraft(incidentKey, AGENCY_TYPE))
                    .setCancelable(false)
                    .show();
        }
    }

    private void restoreServerDraft(FinalReportDraft serverDraft) {
        try {
            String detailsJson = serverDraft.getDraftDetails().toString();
            JSONObject details = new JSONObject(detailsJson);

            String narrative = details.optString("narrative", "");
            editIncidentNarrative.setText(narrative);
            
            // Restore media URLs
            String mediaUrlsStr = details.optString("media_urls", "[]");
            JSONArray mediaUrlsArray = new JSONArray(mediaUrlsStr);
            capturedMediaUris.clear();
            for (int i = 0; i < mediaUrlsArray.length(); i++) {
                String urlStr = mediaUrlsArray.getString(i);
                if (!urlStr.isEmpty()) {
                    capturedMediaUris.add(Uri.parse(urlStr));
                }
            }
            updateEvidenceCount();

            containerSuspects.removeAllViews();
            String suspectsStr = details.optString("suspects", "[]");
            JSONArray suspectsArray = new JSONArray(suspectsStr);
            if (suspectsArray.length() > 0) {
                for (int i = 0; i < suspectsArray.length(); i++) {
                    JSONObject person = suspectsArray.getJSONObject(i);
                    addPersonCardWithJsonData(containerSuspects, "Suspect", person);
                }
            } else {
                addPersonCard(containerSuspects, "Suspect");
            }

            containerVictims.removeAllViews();
            String victimsStr = details.optString("victims", "[]");
            JSONArray victimsArray = new JSONArray(victimsStr);
            if (victimsArray.length() > 0) {
                for (int i = 0; i < victimsArray.length(); i++) {
                    JSONObject person = victimsArray.getJSONObject(i);
                    addPersonCardWithJsonData(containerVictims, "Victim", person);
                }
            } else {
                addPersonCard(containerVictims, "Victim");
            }

            Toast.makeText(this, "Draft restored from server (including " + capturedMediaUris.size() + " media files)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error restoring server draft: " + e.getMessage());
            Toast.makeText(this, "Error restoring draft", Toast.LENGTH_SHORT).show();
        }
    }

    private void addPersonCardWithJsonData(LinearLayout container, String title, JSONObject data) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);
        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title + " Data");
        ((EditText) view.findViewById(R.id.editFirstName)).setText(data.optString("firstName", ""));
        ((EditText) view.findViewById(R.id.editMiddleName)).setText(data.optString("middleName", ""));
        ((EditText) view.findViewById(R.id.editLastName)).setText(data.optString("lastName", ""));
        ((EditText) view.findViewById(R.id.editAddress)).setText(data.optString("address", ""));
        ((EditText) view.findViewById(R.id.editOccupation)).setText(data.optString("occupation", ""));
        ((EditText) view.findViewById(R.id.editStatus)).setText(data.optString("status", ""));
        View btnRemove = view.findViewById(R.id.btnRemoveEntry);
        btnRemove.setOnClickListener(v -> container.removeView(view));
        container.addView(view);
    }

    private void restoreLocalDraft() {
        ReportDraftManager.ReportDraft draft = draftManager.loadDraft(incidentKey, AGENCY_TYPE);
        if (draft == null) return;

        if (draft.narrative != null) {
            editIncidentNarrative.setText(draft.narrative);
        }

        capturedMediaUris.clear();
        capturedMediaUris.addAll(draft.getMediaUris());
        updateEvidenceCount();

        containerSuspects.removeAllViews();
        if (draft.suspects != null && !draft.suspects.isEmpty()) {
            for (ReportDraftManager.PersonData person : draft.suspects) {
                addPersonCardWithData(containerSuspects, "Suspect", person);
            }
        } else {
            addPersonCard(containerSuspects, "Suspect");
        }

        containerVictims.removeAllViews();
        if (draft.victims != null && !draft.victims.isEmpty()) {
            for (ReportDraftManager.PersonData person : draft.victims) {
                addPersonCardWithData(containerVictims, "Victim", person);
            }
        } else {
            addPersonCard(containerVictims, "Victim");
        }
        Toast.makeText(this, "Draft restored from local storage", Toast.LENGTH_SHORT).show();
    }

    private void addPersonCardWithData(LinearLayout container, String title, ReportDraftManager.PersonData data) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);
        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title + " Data");
        ((EditText) view.findViewById(R.id.editFirstName)).setText(data.firstName);
        ((EditText) view.findViewById(R.id.editMiddleName)).setText(data.middleName);
        ((EditText) view.findViewById(R.id.editLastName)).setText(data.lastName);
        ((EditText) view.findViewById(R.id.editAddress)).setText(data.address);
        ((EditText) view.findViewById(R.id.editOccupation)).setText(data.occupation);
        ((EditText) view.findViewById(R.id.editStatus)).setText(data.status);
        
        View btnRemove = view.findViewById(R.id.btnRemoveEntry);
        btnRemove.setOnClickListener(v -> container.removeView(view));
        
        container.addView(view);
    }

    // --- Read-Only Mode & Loading Data ---

    private void enableReadOnlyMode() {
        btnAddSuspect.setVisibility(View.GONE);
        btnAddVictim.setVisibility(View.GONE);
        btnCapturePhoto.setVisibility(View.GONE);
        btnCaptureVideo.setVisibility(View.GONE);
        btnSubmit.setVisibility(View.GONE);
        editIncidentNarrative.setEnabled(false);
        editIncidentNarrative.setFocusable(false);
        editIncidentNarrative.setBackgroundColor(0xFFF5F5F5);
        
        // Set adapter to read-only mode
        if (mediaPreviewAdapter != null) {
            mediaPreviewAdapter.setReadOnly(true);
        }
    }
    
    private void loadIncidentDataFromRepo() {
        incidentsRepository.loadIncidentByIdAsync(
                incidentKey,
                incident -> {
                    if (incident != null) {
                        // Update UI with fetched data
                        String type = incident.getAgencyType();
                        String createdAt = incident.getCreatedAt();
                        String date = createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : "";
                        String time = createdAt != null && createdAt.length() >= 16 ? createdAt.substring(11, 16) : "";
                        String dateTime = date + " " + time;
                        String reporter = incident.getReporterName();
                        String address = incident.getLocationAddress();
                        String info = incident.getDescription();
                        
                        formIncidentType.setText("Incident Type: " + (type != null ? type : "PNP"));
                        formIncidentDate.setText("Date & Time: " + dateTime);
                        formReportedBy.setText("Reported by: " + (reporter != null ? reporter : "N/A"));
                        formIncidentAddress.setText("Address: " + (address != null ? address : "N/A"));
                        formIncidentDescription.setText("Description: " + (info != null ? info : "N/A"));
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Failed to load incident details: " + throwable.getMessage());
                    return Unit.INSTANCE;
                }
        );
    }

    private void loadSubmittedReportData() {
        // Directly load from unit_reports table (submitted reports)
        // We do not want to load from drafts in read-only mode because drafts might be incomplete
        // (e.g., missing media_urls) or outdated if the report is already submitted.
        reportsRepository.loadReportByIncidentIdAsync(
                incidentKey,
                report -> {
                    if (report != null && report.getDetails() != null) {
                        loadDraftDataIntoReadOnlyView(report.getDetails().toString());
                    }
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Failed to load report: " + throwable.getMessage());
                    Toast.makeText(this, "Failed to load report data", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
        );
    }

    private void loadDraftDataIntoReadOnlyView(String detailsJson) {
        try {
            JSONObject details = new JSONObject(detailsJson);

            // Load narrative
            String narrative = details.optString("narrative", "");
            if (!narrative.isEmpty()) {
                editIncidentNarrative.setText(narrative);
            }
            
            // Load Media Evidence
            String mediaUrlsStr = details.optString("media_urls", "[]");
            try {
                JSONArray mediaUrlsArray = new JSONArray(mediaUrlsStr);
                capturedMediaUris.clear();
                if (mediaUrlsArray.length() > 0) {
                    for (int i = 0; i < mediaUrlsArray.length(); i++) {
                        String url = mediaUrlsArray.getString(i);
                        if (!url.isEmpty()) {
                            capturedMediaUris.add(Uri.parse(url));
                        }
                    }
                    updateEvidenceCount();
                } else {
                    tvEvidenceCount.setText("No evidence attached");
                    recyclerMediaPreview.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing media URLs: " + e.getMessage());
            }

            // Clear containers
            containerSuspects.removeAllViews();
            containerVictims.removeAllViews();

            // Parse and display suspects
            String suspectsStr = details.optString("suspects", "[]");
            try {
                JSONArray suspectsArray = new JSONArray(suspectsStr);
                if (suspectsArray.length() > 0) {
                    for (int i = 0; i < suspectsArray.length(); i++) {
                        JSONObject person = suspectsArray.getJSONObject(i);
                        addReadOnlyPersonCard(containerSuspects, "Suspect " + (i + 1), person);
                    }
                } else {
                    addReadOnlyInfoCard(containerSuspects, "Suspects", "No suspects recorded");
                }
            } catch (Exception e) {
                addReadOnlyInfoCard(containerSuspects, "Suspects", suspectsStr.isEmpty() ? "No suspects recorded" : suspectsStr);
            }

            // Parse and display victims
            String victimsStr = details.optString("victims", "[]");
            try {
                JSONArray victimsArray = new JSONArray(victimsStr);
                if (victimsArray.length() > 0) {
                    for (int i = 0; i < victimsArray.length(); i++) {
                        JSONObject person = victimsArray.getJSONObject(i);
                        addReadOnlyPersonCard(containerVictims, "Victim " + (i + 1), person);
                    }
                } else {
                    addReadOnlyInfoCard(containerVictims, "Victims", "No victims recorded");
                }
            } catch (Exception e) {
                addReadOnlyInfoCard(containerVictims, "Victims", victimsStr.isEmpty() ? "No victims recorded" : victimsStr);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing draft details: " + e.getMessage());
        }
    }

    private void addReadOnlyPersonCard(LinearLayout container, String title, JSONObject person) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);

        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title);

        View btnRemove = view.findViewById(R.id.btnRemoveEntry);
        btnRemove.setVisibility(View.GONE);

        setReadOnlyEditText(view, R.id.editFirstName, person.optString("firstName", ""));
        setReadOnlyEditText(view, R.id.editMiddleName, person.optString("middleName", ""));
        setReadOnlyEditText(view, R.id.editLastName, person.optString("lastName", ""));
        setReadOnlyEditText(view, R.id.editAddress, person.optString("address", ""));
        setReadOnlyEditText(view, R.id.editOccupation, person.optString("occupation", ""));
        setReadOnlyEditText(view, R.id.editStatus, person.optString("status", ""));

        container.addView(view);
    }

    private void setReadOnlyEditText(View parent, int id, String value) {
        EditText editText = parent.findViewById(id);
        editText.setText(value);
        editText.setEnabled(false);
        editText.setFocusable(false);
        editText.setBackgroundColor(0xFFF5F5F5);
    }

    private void addReadOnlyInfoCard(LinearLayout container, String title, String content) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_pnp_person_entry, container, false);
        TextView header = view.findViewById(R.id.entryHeaderTitle);
        header.setText(title);
        View btnRemove = view.findViewById(R.id.btnRemoveEntry);
        btnRemove.setVisibility(View.GONE);
        EditText firstName = view.findViewById(R.id.editFirstName);
        firstName.setText(content);
        firstName.setEnabled(false);
        firstName.setFocusable(false);
        view.findViewById(R.id.editMiddleName).setVisibility(View.GONE);
        view.findViewById(R.id.editLastName).setVisibility(View.GONE);
        view.findViewById(R.id.editAddress).setVisibility(View.GONE);
        view.findViewById(R.id.editOccupation).setVisibility(View.GONE);
        view.findViewById(R.id.editStatus).setVisibility(View.GONE);
        container.addView(view);
    }
}