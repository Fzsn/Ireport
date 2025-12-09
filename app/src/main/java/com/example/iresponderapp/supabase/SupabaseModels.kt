package com.example.iresponderapp.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResponderProfile(
    @SerialName("id")
    val id: String,
    @SerialName("display_name")
    val displayName: String?,
    @SerialName("email")
    val email: String?,
    @SerialName("role")
    val role: String,
    @SerialName("agency_id")
    val agencyId: Int?,
    @SerialName("station_id")
    val stationId: Int?,
    @SerialName("phone_number")
    val phoneNumber: String?,
    @SerialName("status")
    val status: String? = "available"  // available, on_duty, busy, off_duty
)

@Serializable
data class AgencyStation(
    @SerialName("id")
    val id: Int,
    @SerialName("agency_id")
    val agencyId: Int,
    @SerialName("name")
    val name: String,
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    @SerialName("municipality")
    val municipality: String? = null,
    @SerialName("contact_number")
    val contactNumber: String? = null,
    @SerialName("address")
    val address: String? = null
)

@Serializable
data class IncidentSummary(
    @SerialName("id")
    val id: String,
    @SerialName("status")
    val status: String,
    @SerialName("agency_type")
    val agencyType: String,
    @SerialName("location_address")
    val locationAddress: String?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("reporter_name")
    val reporterName: String? = null,
    @SerialName("latitude")
    val latitude: Double? = null,
    @SerialName("longitude")
    val longitude: Double? = null,
    @SerialName("media_urls")
    val mediaUrls: List<String>? = null,
    @SerialName("assigned_officer_id")
    val assignedOfficerId: String? = null,
    @SerialName("assigned_officer_name")
    val assignedOfficerName: String? = null,
    @SerialName("reporter_id")
    val reporterId: String? = null
)

/**
 * For updating incident assignment
 */
@Serializable
data class IncidentAssignmentUpdate(
    @SerialName("status")
    val status: String,
    @SerialName("assigned_officer_id")
    val assignedOfficerId: String,
    @SerialName("assigned_officer_name")
    val assignedOfficerName: String
)

@Serializable
data class UnitReport(
    @SerialName("id")
    val id: String,
    @SerialName("incident_id")
    val incidentId: String,
    @SerialName("responder_id")
    val responderId: String,
    @SerialName("agency")
    val agency: String,
    @SerialName("title")
    val title: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("details")
    val details: JsonElement
)

/**
 * For inserting a new profile row after sign-up
 */
@Serializable
data class ProfileInsert(
    @SerialName("id")
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("email")
    val email: String,
    @SerialName("role")
    val role: String,
    @SerialName("agency_id")
    val agencyId: Int?,
    @SerialName("station_id")
    val stationId: Int?,
    @SerialName("phone_number")
    val phoneNumber: String?,
    @SerialName("status")
    val status: String = "available"  // New officers default to available
)

/**
 * For inserting a new unit report
 */
@Serializable
data class UnitReportInsert(
    @SerialName("incident_id")
    val incidentId: String,
    @SerialName("responder_id")
    val responderId: String,
    @SerialName("agency")
    val agency: String,
    @SerialName("title")
    val title: String,
    @SerialName("details")
    val details: JsonElement
)

/**
 * App notification from notifications table
 */
@Serializable
data class AppNotification(
    @SerialName("id")
    val id: Long,
    @SerialName("recipient_id")
    val recipientId: String,
    @SerialName("incident_id")
    val incidentId: String? = null,
    @SerialName("title")
    val title: String,
    @SerialName("body")
    val body: String,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String
)

/**
 * Server-side draft for final reports (from final_report_drafts table)
 */
@Serializable
data class FinalReportDraft(
    @SerialName("id")
    val id: Long,
    @SerialName("incident_id")
    val incidentId: String,
    @SerialName("agency_type")
    val agencyType: String,
    @SerialName("author_id")
    val authorId: String? = null,
    @SerialName("draft_details")
    val draftDetails: JsonElement,
    @SerialName("status")
    val status: String = "draft",  // 'draft' or 'ready_for_review'
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

/**
 * For inserting/updating a draft
 */
@Serializable
data class FinalReportDraftUpsert(
    @SerialName("incident_id")
    val incidentId: String,
    @SerialName("agency_type")
    val agencyType: String,
    @SerialName("author_id")
    val authorId: String,
    @SerialName("draft_details")
    val draftDetails: JsonElement,
    @SerialName("status")
    val status: String = "draft"
)
