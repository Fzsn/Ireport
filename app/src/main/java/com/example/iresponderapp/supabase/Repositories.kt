package com.example.iresponderapp.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlin.Unit
import kotlin.jvm.functions.Function1
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AuthRepository {
    suspend fun signUpResponder(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
        agencyId: Int?,
        stationId: Int?
    )

    suspend fun signIn(email: String, password: String)

    suspend fun signOut()

    fun getCurrentUserId(): String?
}

interface ResponderProfileRepository {
    suspend fun getCurrentResponderProfile(): ResponderProfile?
    suspend fun updateResponderProfile(profile: ResponderProfile)
    suspend fun updateOfficerStatus(officerId: String, newStatus: String)
}

interface IncidentsRepository {
    suspend fun getAssignedIncidentsForToday(): List<IncidentSummary>
    suspend fun getAssignedIncidentsByStatus(status: String? = null): List<IncidentSummary>
    suspend fun getIncidentById(incidentId: String): IncidentSummary?
    suspend fun getPendingIncidents(): List<IncidentSummary>
    suspend fun getIncidentsByStatusList(statuses: List<String>): List<IncidentSummary>
    suspend fun updateIncidentStatus(incidentId: String, newStatus: String)
    suspend fun assignIncident(incidentId: String, officerId: String, officerName: String)
    suspend fun getRespondersForAgencyAndLocation(agencyId: Int, location: String): List<ResponderProfile>
}

interface UnitReportsRepository {
    suspend fun getReportsByAgency(agency: String): List<UnitReport>
    suspend fun getAllMyReports(): List<UnitReport>
    suspend fun getReportByIncidentId(incidentId: String): UnitReport?
    suspend fun createOrUpdateReport(incidentId: String, agency: String, title: String, details: Map<String, Any>)
}

class SupabaseAuthRepository(
    private val client: SupabaseClient
) : AuthRepository {

    override suspend fun signUpResponder(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
        agencyId: Int?,
        stationId: Int?
    ) {
        // Just create auth user - profile will be created after OTP verification
        // When email confirmation is enabled, this sends an OTP to the user's email
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // Note: Profile creation is deferred to after OTP verification
        // because the user is not yet authenticated (no session token)
    }

    /**
     * Verify OTP code sent to email during signup
     */
    suspend fun verifyOtp(email: String, token: String) {
        client.auth.verifyEmailOtp(
            type = io.github.jan.supabase.auth.OtpType.Email.EMAIL,
            email = email,
            token = token
        )
    }

    /**
     * Resend OTP code to email
     */
    suspend fun resendOtp(email: String) {
        throw UnsupportedOperationException("Resend OTP is not available in this build")
    }

    /**
     * Create profile after OTP verification (user is now authenticated)
     */
    suspend fun createProfileAfterVerification(fullName: String, phone: String?) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("No authenticated user found")
        val userEmail = client.auth.currentUserOrNull()?.email
            ?: throw Exception("No email found for user")

        client.from("profiles")
            .upsert(
                ProfileInsert(
                    id = userId,
                    displayName = fullName,
                    email = userEmail,
                    role = "Field Officer",
                    agencyId = null,
                    stationId = null,
                    phoneNumber = phone
                )
            ) {
                onConflict = "id"
            }
    }

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        
        // Validate that the user is a Field Officer
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("Authentication failed")
        
        val profile = client.from("profiles")
            .select() {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingleOrNull<ResponderProfile>()
        
        if (profile == null) {
            client.auth.signOut()
            throw Exception("Profile not found. Please complete your registration.")
        }
        
        if (profile.role != "Field Officer") {
            client.auth.signOut()
            // If the officer exists but hasn't been assigned yet, provide a clear message
            if (profile.agencyId == null || profile.stationId == null) {
                throw Exception("Your account is pending admin assignment. Please wait for your agency/station assignment before logging in.")
            }
            throw Exception("Access denied. This app is for Field Officers only. Chiefs and Desk Officers should use the web dashboard.")
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    /**
     * Java-friendly sign-in helper
     */
    fun signInAsync(
        email: String,
        password: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                signIn(email, password)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly sign-up helper
     */
    fun signUpResponderAsync(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
        agencyId: Int?,
        stationId: Int?,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                signUpResponder(email, password, fullName, phone, agencyId, stationId)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly sign-out helper
     */
    fun signOutAsync(
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                signOut()
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly OTP verification helper
     */
    fun verifyOtpAsync(
        email: String,
        token: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                verifyOtp(email, token)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly resend OTP helper
     */
    fun resendOtpAsync(
        email: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                resendOtp(email)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly profile creation after verification helper
     */
    fun createProfileAfterVerificationAsync(
        fullName: String,
        phone: String?,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                createProfileAfterVerification(fullName, phone)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }
}

class SupabaseResponderProfileRepository(
    private val client: SupabaseClient
) : ResponderProfileRepository {

    override suspend fun getCurrentResponderProfile(): ResponderProfile? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null

        val results = client.from("profiles")
            .select(
                columns = Columns.list(
                    "id",
                    "display_name",
                    "email",
                    "role",
                    "agency_id",
                    "station_id",
                    "phone_number",
                    "status"
                )
            ) {
                filter {
                    eq("id", userId)
                }
            }
            .decodeList<ResponderProfile>()

        return results.firstOrNull()
    }

    private suspend fun getStationByIdInternal(stationId: Int): AgencyStation? {
        return client.from("agency_stations")
            .select(
                columns = Columns.list(
                    "id",
                    "agency_id",
                    "name",
                    "latitude",
                    "longitude",
                    "municipality",
                    "contact_number",
                    "address"
                )
            ) {
                filter { eq("id", stationId) }
                single()
            }
            .decodeSingleOrNull<AgencyStation>()
    }

    /**
     * Java-friendly helper to load station details by id
     */
    fun loadStationAsync(
        stationId: Int,
        onSuccess: Function1<AgencyStation?, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val station = getStationByIdInternal(stationId)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(station)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    override suspend fun updateResponderProfile(profile: ResponderProfile) {
        client.from("profiles")
            .update(
                {
                    set("display_name", profile.displayName)
                    set("phone_number", profile.phoneNumber)
                    set("agency_id", profile.agencyId)
                    set("station_id", profile.stationId)
                }
            ) {
                filter {
                    eq("id", profile.id)
                }
            }
    }

    /**
     * Java-friendly helper to load current profile
     */
    fun loadCurrentProfileAsync(
        onSuccess: Function1<ResponderProfile?, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = getCurrentResponderProfile()
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(profile)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper to update profile
     */
    fun updateProfileAsync(
        profile: ResponderProfile,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateResponderProfile(profile)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }
    
    override suspend fun updateOfficerStatus(officerId: String, newStatus: String) {
        client.from("profiles")
            .update({
                set("status", newStatus)
            }) {
                filter {
                    eq("id", officerId)
                }
            }
    }
    
    /**
     * Java-friendly helper to update officer status (manual: on_duty/off_duty/available)
     */
    fun updateOfficerStatusAsync(
        newStatus: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")
                updateOfficerStatus(userId, newStatus)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }
}

class SupabaseIncidentsRepository(
    private val client: SupabaseClient
) : IncidentsRepository {

    override suspend fun getAssignedIncidentsForToday(): List<IncidentSummary> {
        val session = client.auth.currentSessionOrNull() ?: return emptyList()
        val userId = session.user?.id ?: return emptyList()
        
        android.util.Log.d("IncidentsRepo", "getAssignedIncidentsForToday: userId=$userId")

        val results = client.from("incidents")
            .select(
                columns = Columns.list(
                    "id",
                    "status",
                    "agency_type",
                    "location_address",
                    "created_at",
                    "description",
                    "reporter_name",
                    "latitude",
                    "longitude",
                    "media_urls"
                )
            ) {
                filter {
                    // Check both assigned_officer_id and assigned_officer_ids array
                    or {
                        eq("assigned_officer_id", userId)
                        contains("assigned_officer_ids", listOf(userId))
                    }
                }
            }
            .decodeList<IncidentSummary>()
        
        android.util.Log.d("IncidentsRepo", "getAssignedIncidentsForToday: Found ${results.size} incidents")
        
        return results
    }

    override suspend fun getAssignedIncidentsByStatus(status: String?): List<IncidentSummary> {
        val session = client.auth.currentSessionOrNull()
        if (session == null) {
            android.util.Log.w("IncidentsRepo", "getAssignedIncidentsByStatus: No session found")
            return emptyList()
        }
        val userId = session.user?.id
        if (userId == null) {
            android.util.Log.w("IncidentsRepo", "getAssignedIncidentsByStatus: No user ID found")
            return emptyList()
        }
        
        android.util.Log.d("IncidentsRepo", "getAssignedIncidentsByStatus: userId=$userId, status=$status")

        val results = client.from("incidents")
            .select(
                columns = Columns.list(
                    "id",
                    "status",
                    "agency_type",
                    "location_address",
                    "created_at",
                    "latitude",
                    "longitude",
                    "description"
                )
            ) {
                filter {
                    // Check both assigned_officer_id and assigned_officer_ids array
                    or {
                        eq("assigned_officer_id", userId)
                        contains("assigned_officer_ids", listOf(userId))
                    }
                    if (status != null) {
                        eq("status", status)
                    } else {
                        // Show only active incidents (not resolved/closed/rejected)
                        isIn("status", listOf("assigned", "in_progress"))
                    }
                }
            }
            .decodeList<IncidentSummary>()
        
        android.util.Log.d("IncidentsRepo", "getAssignedIncidentsByStatus: Found ${results.size} incidents")
        results.forEach { incident ->
            android.util.Log.d("IncidentsRepo", "  - ${incident.id}: ${incident.agencyType} - ${incident.status}")
        }
        
        return results
    }

    override suspend fun getIncidentById(incidentId: String): IncidentSummary? {
        val results = client.from("incidents")
            .select(
                columns = Columns.list(
                    "id",
                    "status",
                    "agency_type",
                    "location_address",
                    "created_at",
                    "description",
                    "reporter_name",
                    "latitude",
                    "longitude",
                    "media_urls",
                    "assigned_officer_id",
                    "reporter_id"
                )
            ) {
                filter {
                    eq("id", incidentId)
                }
            }
            .decodeList<IncidentSummary>()

        return results.firstOrNull()
    }

    override suspend fun getPendingIncidents(): List<IncidentSummary> {
        return client.from("incidents")
            .select(
                columns = Columns.list(
                    "id",
                    "status",
                    "agency_type",
                    "location_address",
                    "created_at",
                    "description",
                    "reporter_name",
                    "latitude",
                    "longitude",
                    "media_urls"
                )
            ) {
                filter {
                    eq("status", "pending")
                }
            }
            .decodeList<IncidentSummary>()
    }

    override suspend fun updateIncidentStatus(incidentId: String, newStatus: String) {
        val userId = client.auth.currentUserOrNull()?.id
        val currentTime = kotlinx.datetime.Clock.System.now().toString()
        
        client.from("incidents")
            .update(
                {
                    set("status", newStatus)
                    set("updated_at", currentTime)
                    if (userId != null) {
                        set("updated_by", userId)
                    }
                    // Set resolved_at when status is resolved or closed
                    if (newStatus == "resolved" || newStatus == "closed") {
                        set("resolved_at", currentTime)
                    }
                    // Set first_response_at when status changes to in_progress (first time responding)
                    if (newStatus == "in_progress" || newStatus == "responding") {
                        // We'll set this only if it's null (handled by checking in a separate query or using COALESCE in DB)
                        set("first_response_at", currentTime)
                    }
                }
            ) {
                filter {
                    eq("id", incidentId)
                }
            }
        
        // Also add entry to incident_status_history
        try {
            val changedBy = userId ?: "system"
            client.from("incident_status_history")
                .insert(
                    mapOf(
                        "incident_id" to incidentId,
                        "status" to newStatus,
                        "changed_by" to changedBy,
                        "changed_at" to currentTime,
                        "notes" to "Status updated to $newStatus"
                    )
                )
        } catch (e: Exception) {
            // Log but don't fail the main operation
            e.printStackTrace()
        }
        
        // Update officer status based on incident status change
        if (userId != null) {
            try {
                when (newStatus) {
                    "in_progress", "responding" -> {
                        // Officer is actively working on incident
                        client.from("profiles")
                            .update({ set("status", "busy") }) {
                                filter { eq("id", userId) }
                            }
                    }
                    "resolved", "closed" -> {
                        // Officer finished - set back to available
                        client.from("profiles")
                            .update({ set("status", "available") }) {
                                filter { eq("id", userId) }
                            }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun getIncidentsByStatusList(statuses: List<String>): List<IncidentSummary> {
        return client.from("incidents")
            .select() {
                filter {
                    isIn("status", statuses)
                }
            }
            .decodeList<IncidentSummary>()
    }

    override suspend fun assignIncident(incidentId: String, officerId: String, officerName: String) {
        val currentUserId = client.auth.currentUserOrNull()?.id
        val currentTime = kotlinx.datetime.Clock.System.now().toString()
        
        client.from("incidents")
            .update(
                {
                    set("status", "assigned")
                    set("assigned_officer_id", officerId)
                    set("updated_at", currentTime)
                    if (currentUserId != null) {
                        set("updated_by", currentUserId)
                    }
                }
            ) {
                filter {
                    eq("id", incidentId)
                }
            }
        
        // Add to status history
        try {
            val changedBy = currentUserId ?: "system"
            client.from("incident_status_history")
                .insert(
                    mapOf(
                        "incident_id" to incidentId,
                        "status" to "assigned",
                        "changed_by" to changedBy,
                        "changed_at" to currentTime,
                        "notes" to "Assigned to officer: $officerName"
                    )
                )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Add incident update
        try {
            client.from("incident_updates")
                .insert(
                    mapOf(
                        "incident_id" to incidentId,
                        "author_id" to currentUserId,
                        "update_text" to "Incident assigned to $officerName"
                    )
                )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Set assigned officer's status to busy
        try {
            client.from("profiles")
                .update({
                    set("status", "busy")
                }) {
                    filter {
                        eq("id", officerId)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getRespondersForAgencyAndLocation(agencyId: Int, location: String): List<ResponderProfile> {
        return client.from("profiles")
            .select() {
                filter {
                    eq("agency_id", agencyId)
                    eq("role", "Field Officer")
                }
            }
            .decodeList<ResponderProfile>()
    }

    /**
     * Java-friendly helper for loading today's assigned incidents.
     */
    fun loadAssignedIncidentsForToday(
        onSuccess: Function1<List<IncidentSummary>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val incidents = getAssignedIncidentsForToday()
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(incidents)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for loading assigned incidents by status
     */
    fun loadAssignedIncidentsByStatusAsync(
        status: String?,
        onSuccess: Function1<List<IncidentSummary>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val incidents = getAssignedIncidentsByStatus(status)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(incidents)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for loading pending incidents
     */
    fun loadPendingIncidentsAsync(
        onSuccess: Function1<List<IncidentSummary>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val incidents = getPendingIncidents()
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(incidents)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for loading incident by ID
     */
    fun loadIncidentByIdAsync(
        incidentId: String,
        onSuccess: Function1<IncidentSummary?, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val incident = getIncidentById(incidentId)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(incident)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for updating incident status
     */
    fun updateIncidentStatusAsync(
        incidentId: String,
        newStatus: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateIncidentStatus(incidentId, newStatus)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for assigning incident
     */
    fun assignIncidentAsync(
        incidentId: String,
        officerId: String,
        officerName: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                assignIncident(incidentId, officerId, officerName)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for loading incidents by status list
     */
    fun loadIncidentsByStatusListAsync(
        statuses: List<String>,
        onSuccess: Function1<List<IncidentSummary>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val incidents = getIncidentsByStatusList(statuses)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(incidents)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper for getting responders
     */
    fun loadRespondersForAgencyAsync(
        agencyId: Int,
        location: String,
        onSuccess: Function1<List<ResponderProfile>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val responders = getRespondersForAgencyAndLocation(agencyId, location)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(responders)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }
}

class SupabaseUnitReportsRepository(
    private val client: SupabaseClient
) : UnitReportsRepository {

    override suspend fun getReportsByAgency(agency: String): List<UnitReport> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()

        return client.from("unit_reports")
            .select() {
                filter {
                    eq("responder_id", userId)
                    eq("agency", agency)
                }
            }
            .decodeList<UnitReport>()
    }

    override suspend fun getAllMyReports(): List<UnitReport> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()

        return client.from("unit_reports")
            .select() {
                filter {
                    eq("responder_id", userId)
                }
            }
            .decodeList<UnitReport>()
    }

    override suspend fun getReportByIncidentId(incidentId: String): UnitReport? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null

        val results = client.from("unit_reports")
            .select() {
                filter {
                    eq("incident_id", incidentId)
                    eq("responder_id", userId)
                }
            }
            .decodeList<UnitReport>()

        return results.firstOrNull()
    }

    override suspend fun createOrUpdateReport(
        incidentId: String,
        agency: String,
        title: String,
        details: Map<String, Any>
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("User not authenticated")

        val detailsJson = kotlinx.serialization.json.JsonObject(
            details.mapValues { (_, v) ->
                when (v) {
                    is String -> kotlinx.serialization.json.JsonPrimitive(v)
                    is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                    is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                    else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                }
            }
        )

        // Check if report already exists for this incident and responder
        val existingReport = getReportByIncidentId(incidentId)
        
        if (existingReport != null) {
            // Update existing report
            client.from("unit_reports")
                .update({
                    set("title", title)
                    set("agency", agency)
                    set("details", detailsJson)
                    set("updated_at", kotlinx.datetime.Clock.System.now().toString())
                }) {
                    filter {
                        eq("id", existingReport.id)
                    }
                }
        } else {
            // Insert new report
            val reportInsert = UnitReportInsert(
                incidentId = incidentId,
                responderId = userId,
                agency = agency,
                title = title,
                details = detailsJson
            )
            client.from("unit_reports").insert(reportInsert)
        }
        
        // Also create/update final_reports entry
        try {
            createOrUpdateFinalReport(incidentId, userId, detailsJson)
        } catch (e: Exception) {
            // Log but don't fail the main operation
            e.printStackTrace()
        }
        
        // Add incident update entry
        try {
            client.from("incident_updates")
                .insert(
                    mapOf(
                        "incident_id" to incidentId,
                        "author_id" to userId,
                        "update_text" to "Report submitted: $title"
                    )
                )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Create or update the final_reports entry for an incident
     */
    private suspend fun createOrUpdateFinalReport(
        incidentId: String,
        userId: String,
        reportDetails: kotlinx.serialization.json.JsonObject
    ) {
        // Check if final report already exists
        val existingFinalReport = client.from("final_reports")
            .select() {
                filter {
                    eq("incident_id", incidentId)
                }
            }
            .decodeList<Map<String, Any>>()
        
        if (existingFinalReport.isEmpty()) {
            // Insert new final report
            client.from("final_reports")
                .insert(
                    mapOf(
                        "incident_id" to incidentId,
                        "report_details" to reportDetails,
                        "completed_by_user_id" to userId
                    )
                )
        } else {
            // Update existing final report
            client.from("final_reports")
                .update({
                    set("report_details", reportDetails)
                    set("completed_by_user_id", userId)
                    set("completed_at", kotlinx.datetime.Clock.System.now().toString())
                }) {
                    filter {
                        eq("incident_id", incidentId)
                    }
                }
        }
    }

    /**
     * Java-friendly helper to load all reports
     */
    fun loadAllMyReportsAsync(
        onSuccess: Function1<List<UnitReport>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reports = getAllMyReports()
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(reports)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper to load reports by agency
     */
    fun loadReportsByAgencyAsync(
        agency: String,
        onSuccess: Function1<List<UnitReport>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reports = getReportsByAgency(agency)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(reports)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper to load report by incident ID
     */
    fun loadReportByIncidentIdAsync(
        incidentId: String,
        onSuccess: Function1<UnitReport?, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val report = getReportByIncidentId(incidentId)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(report)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }

    /**
     * Java-friendly helper to create or update report
     */
    fun createOrUpdateReportAsync(
        incidentId: String,
        agency: String,
        title: String,
        details: Map<String, Any>,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                createOrUpdateReport(incidentId, agency, title, details)
                withContext(Dispatchers.Main) {
                    onSuccess.invoke(Unit)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onError.invoke(t)
                }
            }
        }
    }
}

// ============================================================
// NOTIFICATIONS REPOSITORY
// ============================================================

interface NotificationsRepository {
    suspend fun getUnreadNotifications(): List<AppNotification>
    suspend fun getAllNotifications(): List<AppNotification>
    suspend fun getUnreadCount(): Int
    suspend fun markAsRead(notificationId: Long)
    suspend fun markAllAsRead()
}

class SupabaseNotificationsRepository(
    private val client: SupabaseClient
) : NotificationsRepository {

    override suspend fun getUnreadNotifications(): List<AppNotification> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()
        
        return client.from("notifications")
            .select() {
                filter {
                    eq("recipient_id", userId)
                    eq("is_read", false)
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<AppNotification>()
    }

    override suspend fun getAllNotifications(): List<AppNotification> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()
        
        return client.from("notifications")
            .select() {
                filter {
                    eq("recipient_id", userId)
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<AppNotification>()
    }

    override suspend fun getUnreadCount(): Int {
        return getUnreadNotifications().size
    }

    override suspend fun markAsRead(notificationId: Long) {
        client.from("notifications")
            .update({ set("is_read", true) }) {
                filter { eq("id", notificationId) }
            }
    }

    override suspend fun markAllAsRead() {
        val userId = client.auth.currentUserOrNull()?.id ?: return
        client.from("notifications")
            .update({ set("is_read", true) }) {
                filter {
                    eq("recipient_id", userId)
                    eq("is_read", false)
                }
            }
    }

    // ---- Java-friendly async helpers ----

    fun loadUnreadNotificationsAsync(
        onSuccess: Function1<List<AppNotification>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notifications = getUnreadNotifications()
                withContext(Dispatchers.Main) { onSuccess.invoke(notifications) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun loadAllNotificationsAsync(
        onSuccess: Function1<List<AppNotification>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notifications = getAllNotifications()
                withContext(Dispatchers.Main) { onSuccess.invoke(notifications) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun loadUnreadCountAsync(
        onSuccess: Function1<Int, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = getUnreadCount()
                withContext(Dispatchers.Main) { onSuccess.invoke(count) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun markAsReadAsync(
        notificationId: Long,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                markAsRead(notificationId)
                withContext(Dispatchers.Main) { onSuccess.invoke(Unit) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun markAllAsReadAsync(
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                markAllAsRead()
                withContext(Dispatchers.Main) { onSuccess.invoke(Unit) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }
}

// ============================================================
// FINAL REPORT DRAFTS REPOSITORY (Server-side drafts)
// ============================================================

interface FinalReportDraftsRepository {
    suspend fun getDraft(incidentId: String): FinalReportDraft?
    suspend fun saveDraft(incidentId: String, agencyType: String, draftDetails: Map<String, Any>, status: String = "draft")
    suspend fun deleteDraft(incidentId: String)
    suspend fun promoteDraftToFinal(incidentId: String): Boolean
}

class SupabaseFinalReportDraftsRepository(
    private val client: SupabaseClient
) : FinalReportDraftsRepository {

    /**
     * Get existing draft for an incident
     */
    override suspend fun getDraft(incidentId: String): FinalReportDraft? {
        return try {
            val drafts = client.from("final_report_drafts")
                .select() {
                    filter {
                        eq("incident_id", incidentId)
                    }
                }
                .decodeList<FinalReportDraft>()
            drafts.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save or update a draft (upsert by incident_id)
     */
    override suspend fun saveDraft(
        incidentId: String,
        agencyType: String,
        draftDetails: Map<String, Any>,
        status: String
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("User not authenticated")

        val detailsJson = kotlinx.serialization.json.JsonObject(
            draftDetails.mapValues { (_, v) ->
                when (v) {
                    is String -> kotlinx.serialization.json.JsonPrimitive(v)
                    is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                    is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                    else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                }
            }
        )

        val existingDraft = getDraft(incidentId)
        val currentTime = kotlinx.datetime.Clock.System.now().toString()

        if (existingDraft != null) {
            // Update existing draft
            client.from("final_report_drafts")
                .update({
                    set("draft_details", detailsJson)
                    set("status", status)
                    set("author_id", userId)
                    set("updated_at", currentTime)
                }) {
                    filter { eq("incident_id", incidentId) }
                }
        } else {
            // Insert new draft
            val draftUpsert = FinalReportDraftUpsert(
                incidentId = incidentId,
                agencyType = agencyType.lowercase(),
                authorId = userId,
                draftDetails = detailsJson,
                status = status
            )
            client.from("final_report_drafts").insert(draftUpsert)
        }
    }

    /**
     * Delete a draft (e.g., after promoting to final report)
     */
    override suspend fun deleteDraft(incidentId: String) {
        client.from("final_report_drafts")
            .delete {
                filter { eq("incident_id", incidentId) }
            }
    }

    /**
     * Promote draft to final report:
     * 1. Set status to 'ready_for_review'
     * 2. Optionally copy to final_reports table
     * Returns true if successful
     */
    override suspend fun promoteDraftToFinal(incidentId: String): Boolean {
        val draft = getDraft(incidentId) ?: return false
        
        // Update status to ready_for_review
        val currentTime = kotlinx.datetime.Clock.System.now().toString()
        client.from("final_report_drafts")
            .update({
                set("status", "ready_for_review")
                set("updated_at", currentTime)
            }) {
                filter { eq("incident_id", incidentId) }
            }
        
        return true
    }

    // ---- Java-friendly async helpers ----

    fun getDraftAsync(
        incidentId: String,
        onSuccess: Function1<FinalReportDraft?, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val draft = getDraft(incidentId)
                withContext(Dispatchers.Main) { onSuccess.invoke(draft) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun saveDraftAsync(
        incidentId: String,
        agencyType: String,
        draftDetails: Map<String, Any>,
        status: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                saveDraft(incidentId, agencyType, draftDetails, status)
                withContext(Dispatchers.Main) { onSuccess.invoke(Unit) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun deleteDraftAsync(
        incidentId: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deleteDraft(incidentId)
                withContext(Dispatchers.Main) { onSuccess.invoke(Unit) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    fun promoteDraftToFinalAsync(
        incidentId: String,
        onSuccess: Function1<Boolean, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = promoteDraftToFinal(incidentId)
                withContext(Dispatchers.Main) { onSuccess.invoke(result) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }
}

/**
 * Repository for uploading media files to Supabase Storage
 */
class SupabaseStorageRepository(
    private val client: SupabaseClient
) {
    companion object {
        private const val BUCKET_NAME = "incident-media"
    }

    /**
     * Upload a file to Supabase Storage
     * @param fileBytes The file content as byte array
     * @param fileName The name to save the file as (e.g., "incident_123/photo_1.jpg")
     * @param contentType The MIME type (e.g., "image/jpeg", "video/mp4")
     * @return The public URL of the uploaded file
     */
    suspend fun uploadFile(fileBytes: ByteArray, fileName: String, contentType: String): String {
        val storage = client.storage
        val bucket = storage.from(BUCKET_NAME)
        
        bucket.upload(fileName, fileBytes) {
            upsert = true
        }
        
        // Return the public URL
        return bucket.publicUrl(fileName)
    }

    /**
     * Upload multiple files and return their URLs
     */
    suspend fun uploadFiles(files: List<Triple<ByteArray, String, String>>): List<String> {
        return files.map { (bytes, name, contentType) ->
            uploadFile(bytes, name, contentType)
        }
    }

    /**
     * Delete a file from storage
     */
    suspend fun deleteFile(fileName: String) {
        val storage = client.storage
        val bucket = storage.from(BUCKET_NAME)
        bucket.delete(fileName)
    }

    /**
     * Java-friendly helper to upload a single file
     */
    fun uploadFileAsync(
        fileBytes: ByteArray,
        fileName: String,
        contentType: String,
        onSuccess: Function1<String, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = uploadFile(fileBytes, fileName, contentType)
                withContext(Dispatchers.Main) { onSuccess.invoke(url) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    /**
     * Java-friendly helper to upload multiple files
     */
    fun uploadFilesAsync(
        files: List<Triple<ByteArray, String, String>>,
        onSuccess: Function1<List<String>, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urls = uploadFiles(files)
                withContext(Dispatchers.Main) { onSuccess.invoke(urls) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }

    /**
     * Java-friendly helper to delete a file
     */
    fun deleteFileAsync(
        fileName: String,
        onSuccess: Function1<Unit, Unit>,
        onError: Function1<Throwable, Unit>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deleteFile(fileName)
                withContext(Dispatchers.Main) { onSuccess.invoke(Unit) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onError.invoke(t) }
            }
        }
    }
}
