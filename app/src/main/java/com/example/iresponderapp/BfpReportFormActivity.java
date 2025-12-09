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
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iresponderapp.adapter.MediaPreviewAdapter;
import com.example.iresponderapp.supabase.FinalReportDraft;
import com.example.iresponderapp.supabase.SupabaseAuthRepository;
import com.example.iresponderapp.supabase.SupabaseFinalReportDraftsRepository;
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository;
import com.example.iresponderapp.supabase.SupabaseStorageRepository;
import com.example.iresponderapp.supabase.SupabaseUnitReportsRepository;
import com.example.iresponderapp.util.ReportDraftManager;

import org.json.JSONArray;
import org.json.JSONObject;

import kotlin.Unit;

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

public class BfpReportFormActivity extends AppCompatActivity {

    private static final String TAG = "BfpReportForm";

    private TextView formIncidentType, formIncidentDateTime, formReportedBy, formIncidentDescription;
    private TextView formIncidentAddress, formCoordinates;

    private EditText editFireLocation, editRootCause, editPeopleInjured;
    private Spinner spinnerAreaOwnership, spinnerClassOfFire;
    private Button btnSubmit;
    
    // Media capture
    private Button btnCapturePhoto, btnCaptureVideo;
    private TextView tvMediaCount;
    private RecyclerView recyclerMediaPreview;
    private MediaPreviewAdapter mediaPreviewAdapter;
    private List<Uri> capturedMediaUris = new ArrayList<>();
    private Uri photoUri;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Intent> takeVideoLauncher;
    private List<String> uploadedMediaUrls = new ArrayList<>();

    private ArrayAdapter<String> areaOwnershipAdapter;
    private ArrayAdapter<String> classOfFireAdapter;

    private static final String AGENCY_TYPE = "BFP";
    
    private String incidentKey;
    private SupabaseIncidentsRepository incidentsRepository;
    private SupabaseUnitReportsRepository reportsRepository;
    private SupabaseAuthRepository authRepository;
    private SupabaseFinalReportDraftsRepository draftsRepository;
    private SupabaseStorageRepository storageRepository;
    private String currentResponderUid;
    
    private ReportDraftManager draftManager;
    private boolean isReadOnlyMode = false;

    // Intent data
    private String incidentType;
    private String incidentReporter;
    private String incidentAddress;
    private String incidentDescription;
    private String incidentCreatedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bfp_report_form);

        incidentKey = getIntent().getStringExtra("INCIDENT_KEY");
        if (incidentKey == null) {
            Toast.makeText(this, "Incident ID missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Get passed incident data
        incidentType = getIntent().getStringExtra("INCIDENT_TYPE");
        incidentReporter = getIntent().getStringExtra("INCIDENT_REPORTER");
        incidentAddress = getIntent().getStringExtra("INCIDENT_ADDRESS");
        incidentDescription = getIntent().getStringExtra("INCIDENT_DESCRIPTION");
        incidentCreatedAt = getIntent().getStringExtra("INCIDENT_CREATED_AT");

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
        
        draftManager = new ReportDraftManager(this);
        
        // Initialize camera launchers
        initCameraLaunchers();

        initUiElements();

        // Check for Edit Mode (read-only for submitted reports)
        boolean isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        if (isEditMode) {
            isReadOnlyMode = true;
            enableReadOnlyMode();
            // In edit mode, load incident data from database
            loadIncidentData();
            loadSubmittedReportData();
        } else {
            // In create mode, load header data from intent
            loadIncidentDataFromIntent();
            checkAndRestoreDraft();
        }

        btnSubmit.setOnClickListener(v -> validateAndSubmit());
    }

    private void initUiElements() {
        formIncidentType = findViewById(R.id.formIncidentType);
        formIncidentDateTime = findViewById(R.id.formIncidentDateTime);
        formReportedBy = findViewById(R.id.formReportedBy);
        formIncidentDescription = findViewById(R.id.formIncidentDescription);
        formIncidentAddress = findViewById(R.id.formIncidentAddress);
        formCoordinates = findViewById(R.id.formCoordinates);

        editFireLocation = findViewById(R.id.editFireLocation);
        spinnerAreaOwnership = findViewById(R.id.spinnerAreaOwnership);
        spinnerClassOfFire = findViewById(R.id.spinnerClassOfFire);
        editRootCause = findViewById(R.id.editRootCause);
        editPeopleInjured = findViewById(R.id.editPeopleInjured);
        btnSubmit = findViewById(R.id.btnSubmitBfpReport);
        
        // Media capture UI
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto);
        btnCaptureVideo = findViewById(R.id.btnCaptureVideo);
        tvMediaCount = findViewById(R.id.tvMediaCount);
        recyclerMediaPreview = findViewById(R.id.recyclerMediaPreview);
        
        // Setup media preview RecyclerView
        recyclerMediaPreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mediaPreviewAdapter = new MediaPreviewAdapter(this, capturedMediaUris, (position, uri) -> {
            capturedMediaUris.remove(position);
            mediaPreviewAdapter.notifyItemRemoved(position);
            updateMediaCount();
        });
        recyclerMediaPreview.setAdapter(mediaPreviewAdapter);
        
        // Setup camera button listeners
        btnCapturePhoto.setOnClickListener(v -> capturePhoto());
        btnCaptureVideo.setOnClickListener(v -> captureVideo());
        
        setupSpinners();
    }
    
    private void initCameraLaunchers() {
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (result) {
                capturedMediaUris.add(photoUri);
                updateMediaCount();
                Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show();
                uploadMediaToDraft(photoUri, capturedMediaUris.size() - 1);
            }
        });

        takeVideoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri videoUri = result.getData().getData();
                if (videoUri != null) {
                    capturedMediaUris.add(videoUri);
                    updateMediaCount();
                    Toast.makeText(this, "Video recorded!", Toast.LENGTH_SHORT).show();
                    uploadMediaToDraft(videoUri, capturedMediaUris.size() - 1);
                }
            }
        });
    }
    
    private void updateMediaCount() {
        int count = capturedMediaUris.size();
        if (count == 0) {
            tvMediaCount.setText("No media attached");
            recyclerMediaPreview.setVisibility(View.GONE);
        } else {
            tvMediaCount.setText(count + " media file(s) attached");
            recyclerMediaPreview.setVisibility(View.VISIBLE);
            mediaPreviewAdapter.notifyDataSetChanged();
        }
    }
    
    private void capturePhoto() {
        try {
            File photoFile = createMediaFile("BFP_IMG_", ".jpg", Environment.DIRECTORY_PICTURES);
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

    private void setupSpinners() {
        // Class of Fire options (standard fire classification)
        String[] classOfFireOptions = {
            "Select Class",
            "Class A - Ordinary Combustibles (Wood, Paper, Cloth)",
            "Class B - Flammable Liquids (Gasoline, Oil, Paint)",
            "Class C - Electrical Equipment",
            "Class D - Combustible Metals",
            "Class K - Cooking Oils and Fats"
        };
        
        classOfFireAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, classOfFireOptions);
        classOfFireAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClassOfFire.setAdapter(classOfFireAdapter);
        
        // Area Ownership options
        String[] areaOwnershipOptions = {
            "Select Ownership",
            "Residential - Private",
            "Residential - Rental",
            "Commercial - Private",
            "Commercial - Rental",
            "Industrial",
            "Government Property",
            "Public Area",
            "Agricultural Land",
            "Other"
        };
        
        areaOwnershipAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, areaOwnershipOptions);
        areaOwnershipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAreaOwnership.setAdapter(areaOwnershipAdapter);
    }
    
    private void loadIncidentDataFromIntent() {
        // Format date/time
        String date = "N/A";
        String time = "";
        if (incidentCreatedAt != null && incidentCreatedAt.length() >= 16) {
            date = incidentCreatedAt.substring(0, 10);
            time = incidentCreatedAt.substring(11, 16);
        }
        
        formIncidentType.setText(incidentType != null ? incidentType : "FIRE");
        formIncidentDateTime.setText(date + "\n" + time);
        formReportedBy.setText(incidentReporter != null ? incidentReporter : "N/A");
        formIncidentDescription.setText(incidentDescription != null ? incidentDescription : "N/A");
        formIncidentAddress.setText(incidentAddress != null ? incidentAddress : "N/A");
        formCoordinates.setText("See incident details");
        
        // Pre-fill fire location with incident address
        if (incidentAddress != null && !incidentAddress.isEmpty()) {
            editFireLocation.setText(incidentAddress);
        }
    }

    private void loadIncidentData() {
        incidentsRepository.loadIncidentByIdAsync(
                incidentKey,
                incident -> {
                    if (incident != null) {
                        String type = incident.getAgencyType();
                        String createdAt = incident.getCreatedAt();
                        String date = createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : "";
                        String time = createdAt != null && createdAt.length() >= 16 ? createdAt.substring(11, 16) : "";
                        String reporter = incident.getReporterName();
                        String address = incident.getLocationAddress();
                        String info = incident.getDescription();
                        Double lat = incident.getLatitude();
                        Double lon = incident.getLongitude();

                        formIncidentType.setText(type != null ? type : "Fire");
                        formIncidentDateTime.setText(date + "\n" + time);
                        formReportedBy.setText(reporter != null ? reporter : "N/A");
                        formIncidentDescription.setText(info != null ? info : "N/A");
                        formIncidentAddress.setText(address != null ? address : "N/A");
                        String coords = (lat != null && lon != null) ? String.format("%.6f, %.6f", lat, lon) : "N/A";
                        formCoordinates.setText(coords);
                    }
                    return Unit.INSTANCE;
                },
                throwable -> Unit.INSTANCE
        );
    }

    private void showConfirmationDialog() {
        if (editFireLocation.getText().toString().trim().isEmpty()) {
            editFireLocation.setError("Required");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Submit Report")
                .setMessage("Are you sure you want to submit this report?")
                .setPositiveButton("Yes, Submit", (dialog, which) -> submitBfpReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitBfpReport() {
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
                        tvMediaCount.setText("Uploading " + (index + 2) + "/" + capturedMediaUris.size() + "...");
                        uploadNextMedia(index + 1);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.e(TAG, "Failed to upload media: " + error.getMessage());
                        Toast.makeText(this, "Failed to upload media: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Submit");
                        return Unit.INSTANCE;
                    }
            );
        } catch (IOException e) {
            Log.e(TAG, "Error reading media file: " + e.getMessage());
            Toast.makeText(this, "Error reading media file", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            btnSubmit.setText("Submit");
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
        return "bfp/" + incidentKey + "/" + timestamp + "_" + index + extension;
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
        reportDetails.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        reportDetails.put("fireLocation", editFireLocation.getText().toString().trim());
        reportDetails.put("areaOwnership", spinnerAreaOwnership.getSelectedItem().toString());
        reportDetails.put("classOfFire", spinnerClassOfFire.getSelectedItem().toString());
        reportDetails.put("rootCause", editRootCause.getText().toString().trim());
        reportDetails.put("peopleInjured", editPeopleInjured.getText().toString().trim());
        reportDetails.put("media_count", String.valueOf(mediaUrls.size()));
        reportDetails.put("media_urls", new JSONArray(mediaUrls).toString());

        Map<String, Object> draftDetails = new HashMap<>();
        draftDetails.put("fireLocation", editFireLocation.getText().toString());
        draftDetails.put("areaOwnership", spinnerAreaOwnership.getSelectedItem() != null ? spinnerAreaOwnership.getSelectedItem().toString() : "");
        draftDetails.put("classOfFire", spinnerClassOfFire.getSelectedItem() != null ? spinnerClassOfFire.getSelectedItem().toString() : "");
        draftDetails.put("rootCause", editRootCause.getText().toString());
        draftDetails.put("peopleInjured", editPeopleInjured.getText().toString());
        draftDetails.put("evidence_count", String.valueOf(capturedMediaUris.size()));
        
        JSONArray mediaUrlsArray = new JSONArray();
        for (Uri uri : capturedMediaUris) {
            mediaUrlsArray.put(uri.toString());
        }
        draftDetails.put("media_urls", mediaUrlsArray.toString());

        reportsRepository.createOrUpdateReportAsync(
                incidentKey,
                "BFP",
                "BFP Fire Report",
                reportDetails,
                unit -> {
                    markIncidentAsCompleted();
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Toast.makeText(this, "Failed to submit report: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit");
                    return Unit.INSTANCE;
                }
        );
    }

    private void markIncidentAsCompleted() {
        draftsRepository.deleteDraftAsync(incidentKey, u -> Unit.INSTANCE, e -> Unit.INSTANCE);
        draftManager.deleteDraft(incidentKey, AGENCY_TYPE);
        
        incidentsRepository.updateIncidentStatusAsync(
                incidentKey,
                "resolved",
                unit -> {
                    Toast.makeText(this, "Report Submitted!", Toast.LENGTH_LONG).show();
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
    
    private void validateAndSubmit() {
        String fireLocation = editFireLocation.getText().toString().trim();
        int classOfFirePos = spinnerClassOfFire.getSelectedItemPosition();
        
        if (fireLocation.isEmpty()) {
            editFireLocation.setError("Fire location is required");
            editFireLocation.requestFocus();
            Toast.makeText(this, "Please enter the fire location", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (classOfFirePos == 0) {
            Toast.makeText(this, "Please select the class of fire", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showConfirmationDialog();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        saveDraftToServer();
    }
    
    private void saveDraftToServer() {
        if (incidentKey == null || isReadOnlyMode) return;
        
        Map<String, Object> draftDetails = new HashMap<>();
        draftDetails.put("fireLocation", editFireLocation.getText().toString());
        draftDetails.put("areaOwnership", spinnerAreaOwnership.getSelectedItem() != null ? spinnerAreaOwnership.getSelectedItem().toString() : "");
        draftDetails.put("classOfFire", spinnerClassOfFire.getSelectedItem() != null ? spinnerClassOfFire.getSelectedItem().toString() : "");
        draftDetails.put("rootCause", editRootCause.getText().toString());
        draftDetails.put("peopleInjured", editPeopleInjured.getText().toString());
        draftDetails.put("evidence_count", String.valueOf(capturedMediaUris.size()));
        
        JSONArray mediaUrlsArray = new JSONArray();
        for (Uri uri : capturedMediaUris) {
            mediaUrlsArray.put(uri.toString());
        }
        draftDetails.put("media_urls", mediaUrlsArray.toString());

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
    
    private void saveLocalDraft() {
        if (draftManager == null) return;
        
        ReportDraftManager.ReportDraft draft = new ReportDraftManager.ReportDraft();
        draft.narrative = editFireLocation.getText().toString() + "|" +
                          (spinnerAreaOwnership.getSelectedItem() != null ? spinnerAreaOwnership.getSelectedItem().toString() : "") + "|" +
                          (spinnerClassOfFire.getSelectedItem() != null ? spinnerClassOfFire.getSelectedItem().toString() : "") + "|" +
                          editRootCause.getText().toString() + "|" +
                          editPeopleInjured.getText().toString();
        
        draftManager.saveDraft(incidentKey, AGENCY_TYPE, draft);
        Log.d(TAG, "Draft saved locally for incident: " + incidentKey);
    }
    
    private void checkAndRestoreDraft() {
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
            
            editFireLocation.setText(details.optString("fireLocation", ""));
            setSpinnerValue(spinnerAreaOwnership, details.optString("areaOwnership", ""));
            setSpinnerValue(spinnerClassOfFire, details.optString("classOfFire", ""));
            
            String mediaUrlsStr = details.optString("media_urls", "[]");
            JSONArray mediaUrlsArray = new JSONArray(mediaUrlsStr);
            capturedMediaUris.clear();
            for (int i = 0; i < mediaUrlsArray.length(); i++) {
                String urlStr = mediaUrlsArray.getString(i);
                if (!urlStr.isEmpty()) {
                    capturedMediaUris.add(Uri.parse(urlStr));
                }
            }
            updateMediaCount();
            
            editRootCause.setText(details.optString("rootCause", ""));
            editPeopleInjured.setText(details.optString("peopleInjured", ""));
            
            Toast.makeText(this, "Draft restored from server", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error restoring server draft: " + e.getMessage());
            Toast.makeText(this, "Error restoring draft", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void restoreLocalDraft() {
        ReportDraftManager.ReportDraft draft = draftManager.loadDraft(incidentKey, AGENCY_TYPE);
        if (draft == null || draft.narrative == null) return;
        
        String[] parts = draft.narrative.split("\\|", -1);
        if (parts.length >= 1) editFireLocation.setText(parts[0]);
        if (parts.length >= 2) setSpinnerValue(spinnerAreaOwnership, parts[1]);
        if (parts.length >= 3) setSpinnerValue(spinnerClassOfFire, parts[2]);
        if (parts.length >= 4) editRootCause.setText(parts[3]);
        if (parts.length >= 5) editPeopleInjured.setText(parts[4]);
        
        Toast.makeText(this, "Draft restored from local storage", Toast.LENGTH_SHORT).show();
    }
    
    private void enableReadOnlyMode() {
        btnSubmit.setVisibility(android.view.View.GONE);
        btnCapturePhoto.setVisibility(android.view.View.GONE);
        btnCaptureVideo.setVisibility(android.view.View.GONE);
        
        editFireLocation.setEnabled(false);
        spinnerAreaOwnership.setEnabled(false);
        spinnerClassOfFire.setEnabled(false);
        editRootCause.setEnabled(false);
        editPeopleInjured.setEnabled(false);
        
        editFireLocation.setBackgroundColor(0xFFF5F5F5);
        spinnerAreaOwnership.setBackgroundColor(0xFFF5F5F5);
        spinnerClassOfFire.setBackgroundColor(0xFFF5F5F5);
        editRootCause.setBackgroundColor(0xFFF5F5F5);
        editPeopleInjured.setBackgroundColor(0xFFF5F5F5);
        
        // Set adapter to read-only mode
        if (mediaPreviewAdapter != null) {
            mediaPreviewAdapter.setReadOnly(true);
        }
    }
    
    private void loadSubmittedReportData() {
        reportsRepository.loadReportByIncidentIdAsync(
                incidentKey,
                report -> {
                    if (report != null && report.getDetails() != null) {
                        try {
                            String detailsJson = report.getDetails().toString();
                            Log.d(TAG, "Loading report details: " + detailsJson);
                            JSONObject details = new JSONObject(detailsJson);
                            
                            editFireLocation.setText(details.optString("fireLocation", ""));
                            setSpinnerValue(spinnerAreaOwnership, details.optString("areaOwnership", ""));
                            setSpinnerValue(spinnerClassOfFire, details.optString("classOfFire", ""));
                            editRootCause.setText(details.optString("rootCause", ""));
                            editPeopleInjured.setText(details.optString("peopleInjured", ""));
                            
                            // Load Media Evidence
                            String mediaUrlsStr = details.optString("media_urls", "[]");
                            JSONArray mediaUrlsArray = new JSONArray(mediaUrlsStr);
                            capturedMediaUris.clear();
                            if (mediaUrlsArray.length() > 0) {
                                for (int i = 0; i < mediaUrlsArray.length(); i++) {
                                    String url = mediaUrlsArray.getString(i);
                                    if (!url.isEmpty()) {
                                        capturedMediaUris.add(Uri.parse(url));
                                    }
                                }
                                updateMediaCount();
                            } else {
                                tvMediaCount.setText("No media attached");
                                recyclerMediaPreview.setVisibility(View.GONE);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing report details: " + e.getMessage());
                        }
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
    
    private void setSpinnerValue(Spinner spinner, String value) {
        if (value == null || value.isEmpty()) return;
        
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        if (adapter == null) return;
        
        // Try exact match first
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
        
        // Try partial match (for backwards compatibility with old single-letter codes)
        for (int i = 0; i < adapter.getCount(); i++) {
            String item = adapter.getItem(i).toString();
            if (item.contains(value) || value.contains(item)) {
                spinner.setSelection(i);
                return;
            }
        }
    }
}
