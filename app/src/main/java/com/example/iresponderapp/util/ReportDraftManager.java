package com.example.iresponderapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages draft storage for report forms using SharedPreferences.
 * Allows saving and restoring form data when the app is closed or navigated away.
 */
public class ReportDraftManager {
    
    private static final String PREFS_NAME = "report_drafts";
    private static final String KEY_PREFIX = "draft_";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    public ReportDraftManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    /**
     * Save a draft for a specific incident
     */
    public void saveDraft(String incidentKey, String agencyType, ReportDraft draft) {
        String key = KEY_PREFIX + agencyType + "_" + incidentKey;
        String json = gson.toJson(draft);
        prefs.edit().putString(key, json).apply();
    }
    
    /**
     * Load a draft for a specific incident
     */
    public ReportDraft loadDraft(String incidentKey, String agencyType) {
        String key = KEY_PREFIX + agencyType + "_" + incidentKey;
        String json = prefs.getString(key, null);
        if (json == null) {
            return null;
        }
        return gson.fromJson(json, ReportDraft.class);
    }
    
    /**
     * Delete a draft after successful submission
     */
    public void deleteDraft(String incidentKey, String agencyType) {
        String key = KEY_PREFIX + agencyType + "_" + incidentKey;
        prefs.edit().remove(key).apply();
    }
    
    /**
     * Check if a draft exists
     */
    public boolean hasDraft(String incidentKey, String agencyType) {
        String key = KEY_PREFIX + agencyType + "_" + incidentKey;
        return prefs.contains(key);
    }
    
    /**
     * Data class to hold draft information
     */
    public static class ReportDraft {
        public String narrative;
        public List<PersonData> suspects;
        public List<PersonData> victims;
        public List<String> mediaUriStrings;
        public Map<String, String> additionalFields;
        public long lastModified;
        
        public ReportDraft() {
            suspects = new ArrayList<>();
            victims = new ArrayList<>();
            mediaUriStrings = new ArrayList<>();
            additionalFields = new HashMap<>();
            lastModified = System.currentTimeMillis();
        }
        
        public List<Uri> getMediaUris() {
            List<Uri> uris = new ArrayList<>();
            if (mediaUriStrings != null) {
                for (String uriString : mediaUriStrings) {
                    try {
                        uris.add(Uri.parse(uriString));
                    } catch (Exception ignored) {}
                }
            }
            return uris;
        }
        
        public void setMediaUris(List<Uri> uris) {
            mediaUriStrings = new ArrayList<>();
            if (uris != null) {
                for (Uri uri : uris) {
                    mediaUriStrings.add(uri.toString());
                }
            }
        }
    }
    
    /**
     * Data class for person entries (suspects/victims)
     */
    public static class PersonData {
        public String firstName;
        public String middleName;
        public String lastName;
        public String address;
        public String occupation;
        public String status;
        
        public PersonData() {}
        
        public PersonData(String firstName, String middleName, String lastName, 
                         String address, String occupation, String status) {
            this.firstName = firstName;
            this.middleName = middleName;
            this.lastName = lastName;
            this.address = address;
            this.occupation = occupation;
            this.status = status;
        }
    }
}
