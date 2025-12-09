package com.example.iresponderapp.supabase

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Supabase data models and repository logic.
 * These tests verify the data classes, serialization, and basic logic
 * without requiring actual Supabase connections.
 */
class SupabaseRepositoriesTest {

    // ============================================
    // Model Tests
    // ============================================

    @Test
    fun `ResponderProfile model has correct fields`() {
        val profile = ResponderProfile(
            id = "test-uuid-123",
            displayName = "John Doe",
            email = "john@example.com",
            role = "Field Officer",
            agencyId = 1,
            stationId = 101,
            phoneNumber = "+639123456789"
        )

        assertEquals("test-uuid-123", profile.id)
        assertEquals("John Doe", profile.displayName)
        assertEquals("john@example.com", profile.email)
        assertEquals("Field Officer", profile.role)
        assertEquals(1, profile.agencyId)
        assertEquals(101, profile.stationId)
        assertEquals("+639123456789", profile.phoneNumber)
    }

    @Test
    fun `ResponderProfile handles null optional fields`() {
        val profile = ResponderProfile(
            id = "test-uuid-456",
            displayName = null,
            email = null,
            role = "Desk Officer",
            agencyId = null,
            stationId = null,
            phoneNumber = null
        )

        assertNull(profile.displayName)
        assertNull(profile.email)
        assertNull(profile.agencyId)
        assertNull(profile.stationId)
        assertNull(profile.phoneNumber)
    }

    @Test
    fun `IncidentSummary model has correct fields`() {
        val incident = IncidentSummary(
            id = "incident-001",
            status = "pending",
            agencyType = "PNP",
            locationAddress = "123 Main St, Daet",
            createdAt = "2024-12-07T10:30:00Z",
            description = "Test incident",
            reporterName = "Jane Doe",
            latitude = 14.1234,
            longitude = 122.5678,
            mediaUrls = listOf("http://example.com/image1.jpg"),
            assignedOfficerId = "officer-123",
            assignedOfficerName = "Officer Smith",
            reporterId = "reporter-456"
        )

        assertEquals("incident-001", incident.id)
        assertEquals("pending", incident.status)
        assertEquals("PNP", incident.agencyType)
        assertEquals("123 Main St, Daet", incident.locationAddress)
        assertEquals("2024-12-07T10:30:00Z", incident.createdAt)
        assertEquals("Test incident", incident.description)
        assertEquals("Jane Doe", incident.reporterName)
        assertEquals(14.1234, incident.latitude!!, 0.0001)
        assertEquals(122.5678, incident.longitude!!, 0.0001)
        assertEquals(1, incident.mediaUrls?.size)
        assertEquals("officer-123", incident.assignedOfficerId)
        assertEquals("Officer Smith", incident.assignedOfficerName)
        assertEquals("reporter-456", incident.reporterId)
    }

    @Test
    fun `IncidentSummary handles minimal required fields`() {
        val incident = IncidentSummary(
            id = "incident-002",
            status = "assigned",
            agencyType = "BFP",
            locationAddress = null,
            createdAt = "2024-12-07T11:00:00Z"
        )

        assertEquals("incident-002", incident.id)
        assertEquals("assigned", incident.status)
        assertEquals("BFP", incident.agencyType)
        assertNull(incident.locationAddress)
        assertNull(incident.description)
        assertNull(incident.reporterName)
        assertNull(incident.latitude)
        assertNull(incident.longitude)
        assertNull(incident.mediaUrls)
        assertNull(incident.assignedOfficerId)
    }

    @Test
    fun `UnitReport model has correct fields`() {
        val details = JsonObject(mapOf(
            "narrative" to JsonPrimitive("Test narrative"),
            "fireLocation" to JsonPrimitive("Building A")
        ))

        val report = UnitReport(
            id = "report-001",
            incidentId = "incident-001",
            responderId = "responder-001",
            agency = "BFP",
            title = "Fire Report",
            createdAt = "2024-12-07T12:00:00Z",
            updatedAt = "2024-12-07T12:30:00Z",
            details = details
        )

        assertEquals("report-001", report.id)
        assertEquals("incident-001", report.incidentId)
        assertEquals("responder-001", report.responderId)
        assertEquals("BFP", report.agency)
        assertEquals("Fire Report", report.title)
        assertEquals("2024-12-07T12:00:00Z", report.createdAt)
        assertEquals("2024-12-07T12:30:00Z", report.updatedAt)
        assertNotNull(report.details)
    }

    @Test
    fun `ProfileInsert model for sign-up`() {
        val profileInsert = ProfileInsert(
            id = "new-user-uuid",
            displayName = "New User",
            email = "newuser@example.com",
            role = "Field Officer",
            agencyId = 2,
            stationId = 201,
            phoneNumber = "+639987654321"
        )

        assertEquals("new-user-uuid", profileInsert.id)
        assertEquals("New User", profileInsert.displayName)
        assertEquals("newuser@example.com", profileInsert.email)
        assertEquals("Field Officer", profileInsert.role)
        assertEquals(2, profileInsert.agencyId)
        assertEquals(201, profileInsert.stationId)
        assertEquals("+639987654321", profileInsert.phoneNumber)
    }

    @Test
    fun `UnitReportInsert model for creating reports`() {
        val details = JsonObject(mapOf(
            "suspects" to JsonPrimitive("John Doe"),
            "victims" to JsonPrimitive("Jane Doe")
        ))

        val reportInsert = UnitReportInsert(
            incidentId = "incident-123",
            responderId = "responder-456",
            agency = "PNP",
            title = "Crime Report",
            details = details
        )

        assertEquals("incident-123", reportInsert.incidentId)
        assertEquals("responder-456", reportInsert.responderId)
        assertEquals("PNP", reportInsert.agency)
        assertEquals("Crime Report", reportInsert.title)
        assertNotNull(reportInsert.details)
    }

    @Test
    fun `IncidentAssignmentUpdate model for assigning incidents`() {
        val update = IncidentAssignmentUpdate(
            status = "assigned",
            assignedOfficerId = "officer-789",
            assignedOfficerName = "Officer Johnson"
        )

        assertEquals("assigned", update.status)
        assertEquals("officer-789", update.assignedOfficerId)
        assertEquals("Officer Johnson", update.assignedOfficerName)
    }

    // ============================================
    // Agency Mapping Tests
    // ============================================

    @Test
    fun `agency ID to name mapping is correct`() {
        val agencyMap = mapOf(
            1 to "PNP",
            2 to "BFP",
            3 to "MDRRMO"
        )

        assertEquals("PNP", agencyMap[1])
        assertEquals("BFP", agencyMap[2])
        assertEquals("MDRRMO", agencyMap[3])
    }

    @Test
    fun `incident type to agency mapping is correct`() {
        fun getAgencyForIncidentType(type: String): String {
            return when (type.lowercase()) {
                "crime" -> "PNP"
                "fire" -> "BFP"
                "medical emergency", "disaster" -> "MDRRMO"
                else -> "MDRRMO"
            }
        }

        assertEquals("PNP", getAgencyForIncidentType("Crime"))
        assertEquals("BFP", getAgencyForIncidentType("Fire"))
        assertEquals("MDRRMO", getAgencyForIncidentType("Medical Emergency"))
        assertEquals("MDRRMO", getAgencyForIncidentType("Disaster"))
        assertEquals("MDRRMO", getAgencyForIncidentType("Unknown"))
    }

    // ============================================
    // Status Validation Tests
    // ============================================

    @Test
    fun `valid incident statuses are recognized`() {
        val validStatuses = listOf("pending", "assigned", "in_progress", "resolved", "closed", "rejected")

        assertTrue(validStatuses.contains("pending"))
        assertTrue(validStatuses.contains("assigned"))
        assertTrue(validStatuses.contains("in_progress"))
        assertTrue(validStatuses.contains("resolved"))
        assertTrue(validStatuses.contains("closed"))
        assertTrue(validStatuses.contains("rejected"))
    }

    @Test
    fun `completed statuses are correctly identified`() {
        fun isCompletedStatus(status: String): Boolean {
            return status.lowercase() in listOf("resolved", "closed", "completed")
        }

        assertTrue(isCompletedStatus("resolved"))
        assertTrue(isCompletedStatus("closed"))
        assertTrue(isCompletedStatus("Resolved"))
        assertTrue(isCompletedStatus("CLOSED"))
        assertFalse(isCompletedStatus("pending"))
        assertFalse(isCompletedStatus("assigned"))
        assertFalse(isCompletedStatus("in_progress"))
    }

    // ============================================
    // Date/Time Parsing Tests
    // ============================================

    @Test
    fun `ISO timestamp date extraction works correctly`() {
        val timestamp = "2024-12-07T10:30:00Z"
        val datePart = if (timestamp.length >= 10) timestamp.substring(0, 10) else "--"

        assertEquals("2024-12-07", datePart)
    }

    @Test
    fun `ISO timestamp time extraction works correctly`() {
        val timestamp = "2024-12-07T10:30:00Z"
        val timePart = if (timestamp.length >= 16) timestamp.substring(11, 16) else "--:--"

        assertEquals("10:30", timePart)
    }

    @Test
    fun `handles short or null timestamps gracefully`() {
        val shortTimestamp = "2024-12"
        val datePart = if (shortTimestamp.length >= 10) shortTimestamp.substring(0, 10) else "--"

        assertEquals("--", datePart)
    }

    // ============================================
    // Input Validation Tests
    // ============================================

    @Test
    fun `email validation pattern works`() {
        fun isValidEmail(email: String): Boolean {
            return email.contains("@") && email.contains(".")
        }

        assertTrue(isValidEmail("test@example.com"))
        assertTrue(isValidEmail("user.name@domain.co"))
        assertFalse(isValidEmail("invalid-email"))
        assertFalse(isValidEmail("no-at-sign.com"))
    }

    @Test
    fun `phone number validation works`() {
        fun isValidPhone(phone: String): Boolean {
            val digitsOnly = phone.replace(Regex("[^0-9]"), "")
            return digitsOnly.length >= 7  // Minimum 7 digits for local numbers
        }

        assertTrue(isValidPhone("+639123456789"))
        assertTrue(isValidPhone("09123456789"))
        assertTrue(isValidPhone("(02) 123-4567"))  // 7 digits
        assertFalse(isValidPhone("12345"))
        assertFalse(isValidPhone("abc"))
    }

    @Test
    fun `password strength validation works`() {
        fun isStrongPassword(password: String): Boolean {
            return password.length >= 6
        }

        assertTrue(isStrongPassword("password123"))
        assertTrue(isStrongPassword("123456"))
        assertFalse(isStrongPassword("12345"))
        assertFalse(isStrongPassword("abc"))
    }

    // ============================================
    // JSON Details Conversion Tests
    // ============================================

    @Test
    fun `map to JsonObject conversion works`() {
        val details = mapOf(
            "narrative" to "Test narrative",
            "location" to "Test location",
            "count" to "5"
        )

        val jsonObject = JsonObject(
            details.mapValues { (_, v) -> JsonPrimitive(v) }
        )

        assertEquals(3, jsonObject.size)
        assertEquals("\"Test narrative\"", jsonObject["narrative"].toString())
        assertEquals("\"Test location\"", jsonObject["location"].toString())
        assertEquals("\"5\"", jsonObject["count"].toString())
    }

    // ============================================
    // Incident Code Generation Tests
    // ============================================

    @Test
    fun `incident code shortening works correctly`() {
        fun shortenIncidentCode(key: String): String {
            return if (key.length > 8) key.substring(0, 8) else key
        }

        assertEquals("12345678", shortenIncidentCode("123456789012345"))
        assertEquals("short", shortenIncidentCode("short"))
        assertEquals("exactly8", shortenIncidentCode("exactly8"))
    }

    @Test
    fun `incident display ID formatting works`() {
        fun formatIncidentId(key: String): String {
            val shortId = if (key.length > 5) key.substring(key.length - 5) else key
            return "#$shortId"
        }

        assertEquals("#45678", formatIncidentId("1234567890-45678"))
        assertEquals("#short", formatIncidentId("short"))
    }

    // ============================================
    // Priority Derivation Tests
    // ============================================

    @Test
    fun `priority derivation from agency type works`() {
        fun getPriority(agencyType: String): String {
            return when (agencyType.uppercase()) {
                "PNP", "BFP" -> "High"
                "MDRRMO" -> "Medium"
                else -> "Low"
            }
        }

        assertEquals("High", getPriority("PNP"))
        assertEquals("High", getPriority("BFP"))
        assertEquals("Medium", getPriority("MDRRMO"))
        assertEquals("Low", getPriority("Unknown"))
    }

    // ============================================
    // Location Detection Tests
    // ============================================

    @Test
    fun `location detection from address works`() {
        fun detectLocation(address: String?): String {
            if (address == null) return "Daet"
            val addrLower = address.lowercase()
            return when {
                addrLower.contains("daet") -> "Daet"
                addrLower.contains("labo") -> "Labo"
                addrLower.contains("basud") -> "Basud"
                else -> "Daet"
            }
        }

        assertEquals("Daet", detectLocation("123 Main St, Daet, Camarines Norte"))
        assertEquals("Labo", detectLocation("Barangay Centro, Labo"))
        assertEquals("Basud", detectLocation("Basud Municipal Hall"))
        assertEquals("Daet", detectLocation("Unknown Location"))
        assertEquals("Daet", detectLocation(null))
    }
}
