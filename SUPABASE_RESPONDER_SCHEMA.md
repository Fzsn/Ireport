# Supabase Schema Plan – Responder App

This document captures the current **responder-facing** data flows in the Android app and a proposed Supabase schema that can support them (and align with `ireport_v1`).

---

## 1. Current Firebase model (Responder side only)

### 1.1 Auth & responder profile

**Auth:** Firebase Authentication
- Inputs:
  - `email`, `password` (ResponderSignUp / responderSignIn)
- Outputs:
  - `uid` (used as `userId` in DB and as node key under `Responders`)

**Realtime DB path:** `IresponderApp/Responders/{uid}`

Fields used in code:
- `userId` – Firebase auth UID (string)
- `fullName`
- `contactNumber`
- `email`
- `location` – one of the municipalities (Basud, Capalonga, Daet, Jose Panganiban, Labo, Mercedes, Paracale, San Lorenzo Ruiz, San Vicente, Santa Elena, Talisay, Vinzons)
- `agency` – "PNP", "BFP", "MDRRMO"
- `password` – stored in plain text (we should NOT replicate this in Supabase)

Operations:
- Create responder profile under `Responders/{uid}` on sign-up.
- Read responder by auth id:
  - `orderByChild("userId").equalTo(currentUid)` (HomeFragment, ProfileFragment)
- Read all responders (desk/officer side) and filter by `agency` + `location` for incident assignment.
- Delete responder profile + auth account in ProfileFragment.

---

### 1.2 Incidents lifecycle (responder view)

**Realtime DB path:** `IresponderApp/Incidents_/{incidentKey}`

Fields used across responder screens:

Common fields:
- `Status` – e.g. "Pending", "Assigned", "Completed", "Rejected"
- `incidentType` – e.g. "Crime", "Fire", "Medical Emergency", "Disaster"
- `address`
- `date` – string; used to determine "today" stats
- `Time` – string; display only
- `agency` – text; used mainly when passing to detail screens
- `AssignedResponderUID` – UID of responder assigned
- `AssignedResponderName` – responder name as string

Detail screens (`ResponderDetailActivity`, `AccomplishedDetailsActivity`, `activity_incident_details`):
- `reporterName`
- `additionalInfo`
- `imageURL`
- `latitude`, `longitude` – stored as strings (DMS-style) and converted to decimal for Google Maps

Key queries/updates:
- **Dashboard (HomeFragment)**
  - `Incidents_.orderByChild("AssignedResponderUID").equalTo(currentUid)`
  - For each incident:
    - `Status`, `date`
    - If `date == today`: count `Completed` vs non-completed
    - For list of ongoing cases: include any with `Status != "Completed"`

- **Alert tab (AlertFragment)**
  - Same query: `AssignedResponderUID == currentUid`
  - Filter to incidents with `Status == "Assigned"`

- **OnProcess tab (OnProcessFragment)**
  - `Incidents_.orderByChild("Status").equalTo("Pending")`
  - Used by staff to see all pending incidents

- **Detail views**
  - Direct read of `Incidents_/{incidentKey}` to show full details

- **Assignment / rejection (desk/officer view)**
  - On assignment:
    - `Status = "Assigned"`
    - `AssignedResponderUID = uid`
    - `AssignedResponderName = fullName + (agency + location)`
  - On rejection:
    - `Status = "Rejected"`

---

### 1.3 Responder "Reports" (unit-specific forms)

**Realtime DB path:** `IresponderApp/Reports/{agencyFolder}/{reportId}`

Where `agencyFolder` is:
- `"PNP"`, `"BFP"`, or `"MDRRMO"`

Fields used in `FormsFragment`:
- `incidentKey` – references `Incidents_` key
- `timestamp` – string; used to show date
- `responderUid` – filter by current user:
  - `orderByChild("responderUid").equalTo(currentUid)`

Agency-specific fields for display name:
- `MDRRMO`:
  - `natureOfCall`
  - `emergencyType`
- `BFP`:
  - `fireLocation`
- `PNP`:
  - title is hard-coded as "Crime Incident Report"

Operations:
- List all reports by responder (per agency).
- Open a unit form activity in edit mode with `INCIDENT_KEY` + `IS_EDIT_MODE = true`.

---

## 2. Proposed Supabase schema (responder-focused)

Goal: support all existing responder flows while aligning with `ireport_v1` Supabase tables (e.g. `incidents`, `incident_status_history`).

### 2.1 Auth and responders

Use **Supabase Auth** for credentials (email/password). Do **not** store plain-text passwords.

**Table: `responders`**

Columns:
- `id` – `uuid`, primary key, **FK to `auth.users.id`**
- `full_name` – `text`
- `phone` – `text` (11-digit PH mobile)
- `email` – `text`, unique (optionally mirrors `auth.users.email`)
- `agency` – `text` or enum (`'PNP' | 'BFP' | 'MDRRMO'`)
- `location` – `text` or enum (municipality list)
- `created_at` – `timestamptz` (default `now()`)
- `updated_at` – `timestamptz`
- `is_active` – `boolean` (for soft delete / disable)

Mapping from Firebase `Responders`:
- `userId` → `responders.id`
- `fullName` → `responders.full_name`
- `contactNumber` → `responders.phone`
- `email` → `responders.email`
- `agency` → `responders.agency`
- `location` → `responders.location`
- `password` → **NOT stored** (handled by Supabase Auth)

Typical queries:
- Get responder profile for current user:
  - `SELECT * FROM responders WHERE id = auth.uid()`
- Filter responders for assignment by `agency` + `location`.

---

### 2.2 Incidents & responder assignment

We assume or extend an existing `incidents` table used in `ireport_v1`.

**Table: `incidents`**

Core fields (some may already exist):
- `id` – PK (`bigint` or `uuid`)
- `public_code` – `text` (optional, for `#IR-xxxxx` style codes)
- `agency` / `agency_id` – match your existing schema (PNP/BFP/MDRRMO)
- `incident_type` – `text` (maps `incidentType`)
- `status` – `text` or enum (`'pending' | 'assigned' | 'completed' | 'rejected' | ...`)
- `reporter_name` – `text`
- `reporter_phone` – `text` (if tracked)
- `address` – `text`
- `latitude` – `double precision`
- `longitude` – `double precision`
- `image_url` – `text`
- `occurred_at` – `timestamptz` (combined `date` + `Time`)
- `created_at` – `timestamptz`
- `updated_at` – `timestamptz`

Responder-related fields (new or confirming existing):
- `assigned_responder_id` – `uuid` FK → `responders.id`
- `assigned_responder_name` – `text` (optional denormalized display)
- `rejection_reason` – `text` (optional)

Mapping from Firebase `Incidents_`:
- Node key (`incidentKey`) → `incidents.id` or a `legacy_key` column
- `Status` → `status`
- `incidentType` → `incident_type`
- `reporterName` → `reporter_name`
- `date` + `Time` → `occurred_at`; optionally a `date` column for day filters
- `address` → `address`
- `latitude`, `longitude` (string/DMS) → numeric `latitude`, `longitude`
- `imageURL` → `image_url`
- `AssignedResponderUID` → `assigned_responder_id`
- `AssignedResponderName` → `assigned_responder_name`

Key queries to replicate:
- Home/dashboard:
  - `SELECT * FROM incidents WHERE assigned_responder_id = auth.uid()`
  - Filter where `occurred_at::date = CURRENT_DATE` for daily stats.
  - For ongoing list: `status != 'completed'`.
- Alert tab:
  - Same base query, plus `status = 'assigned'`.
- OnProcess tab:
  - `SELECT * FROM incidents WHERE status = 'pending'`.

Updates:
- Assignment:
  - `UPDATE incidents SET status='assigned', assigned_responder_id=..., assigned_responder_name=... WHERE id = ...`.
- Rejection:
  - `UPDATE incidents SET status='rejected', rejection_reason=... WHERE id = ...`.

---

### 2.3 Incident status history (timeline / audit)

Matches patterns already present in `ireport_v1` (`incident_status_history`).

**Table: `incident_status_history`**

Columns:
- `id` – PK
- `incident_id` – FK → `incidents.id`
- `status` – `text` (same enum as `incidents.status`)
- `notes` – `text`
- `changed_by` – `text` or FK → `responders.id` / `auth.users.id`
- `changed_at` – `timestamptz` (default `now()`)

Usage in responder context:
- On assignment:
  - Insert row with `status = 'assigned'`, `notes = 'Assigned to [responder]'`.
- On rejection / completion:
  - Insert corresponding `status` + `notes` entries.

This is not yet surfaced in the current Android UI, but it will support richer future dashboards while remaining compatible with `ireport_v1`.

---

### 2.4 Unit-specific responder reports

Instead of three separate subtrees under `Reports/PNP`, `Reports/BFP`, `Reports/MDRRMO`, we normalize to a single table with a flexible JSON payload.

**Table: `unit_reports`**

Columns:
- `id` – PK
- `incident_id` – FK → `incidents.id` (maps `incidentKey`)
- `responder_id` – FK → `responders.id` (maps `responderUid`)
- `agency` – `text` or enum (`'PNP' | 'BFP' | 'MDRRMO'`)
- `title` – `text` (for list display in FormsFragment)
- `created_at` – `timestamptz`
- `updated_at` – `timestamptz`
- `details` – `jsonb` (full contents of the unit-specific form)

Mapping from Firebase `Reports` nodes:
- `incidentKey` → `unit_reports.incident_id` (or `incident_legacy_key`)
- `responderUid` → `unit_reports.responder_id`
- `timestamp` → `unit_reports.created_at`
- `agencyFolder` → `unit_reports.agency`
- MDRRMO fields:
  - `natureOfCall`, `emergencyType` → stored in `details` and used to compute `title`
- BFP fields:
  - `fireLocation` → stored in `details`, also used for `title` (e.g. `"Fire: [fireLocation]"`)
- PNP fields:
  - All form fields stored in `details`; list `title` may just be `"Crime Incident Report"`.

Replicating FormsFragment behaviour:
- Query per-agency reports:
  - `SELECT * FROM unit_reports WHERE responder_id = auth.uid() AND agency = 'PNP' ORDER BY created_at DESC` (and similarly for BFP/MDRRMO), or a single query grouping by agency.
- Derive `displayName` in Android from `agency` + `details` like the current Java logic.

---

## 3. Quick mapping summary (Firebase → Supabase)

- `IresponderApp/Responders/{uid}` → `responders`
  - `userId` → `responders.id`
  - `fullName` → `responders.full_name`
  - `contactNumber` → `responders.phone`
  - `email` → `responders.email`
  - `agency` → `responders.agency`
  - `location` → `responders.location`
  - `password` → handled only by Supabase Auth, not stored in table

- `IresponderApp/Incidents_/{incidentKey}` → `incidents`
  - `Status` → `incidents.status`
  - `incidentType` → `incidents.incident_type`
  - `reporterName` → `incidents.reporter_name`
  - `address` → `incidents.address`
  - `date` + `Time` → `incidents.occurred_at`
  - `latitude`, `longitude` (string) → numeric `latitude`, `longitude`
  - `imageURL` → `incidents.image_url`
  - `AssignedResponderUID` → `incidents.assigned_responder_id`
  - `AssignedResponderName` → `incidents.assigned_responder_name`

- `IresponderApp/Reports/{agencyFolder}/{reportId}` → `unit_reports`
  - `incidentKey` → `unit_reports.incident_id`
  - `responderUid` → `unit_reports.responder_id`
  - `timestamp` → `unit_reports.created_at`
  - `agencyFolder` → `unit_reports.agency`
  - Additional per-agency fields → `unit_reports.details` (JSON) and `unit_reports.title`

---

## 4. Next implementation steps (high level)

1. **Create / adjust Supabase tables** using SQL based on this plan.
2. **Wire Supabase Auth** in the Android app and map authenticated users to `responders` rows.
3. **Implement repositories**:
   - `ResponderRepository` (profile read/update, soft delete)
   - `IncidentRepository` (assigned incidents, pending incidents, updates)
   - `UnitReportRepository` (list + edit reports)
4. **Refactor screens** step by step from Firebase → Supabase data sources:
   - `responderSignIn` / `ResponderSignUp` → Supabase Auth + responders table
   - `HomeFragment` → `incidents` queries
   - `AlertFragment` / `OnProcessFragment` → `incidents` queries
   - `ResponderDetailActivity` / `AccomplishedDetailsActivity` → `incidents` + `incident_status_history`
   - `FormsFragment` → `unit_reports`
5. Once all flows are Supabase-based, **remove Firebase dependencies** from the Android project.
