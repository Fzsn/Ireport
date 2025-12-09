# Supabase Responder Migration Plan

This document contrasts the **current Supabase schema** (from `currentsupabase_schema.md`) with the **Responder schema plan** (from `SUPABASE_RESPONDER_SCHEMA.md`) and lists what needs to be **added** or **changed** to support the Android responder app.

---

## 1. Existing Supabase schema (relevant tables)

From `currentsupabase_schema.md`:

### 1.1 `profiles`

```sql
CREATE TABLE public.profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id),
  display_name text,
  email text UNIQUE,
  role varchar NOT NULL CHECK (role IN ('Resident','Desk Officer','Field Officer','Chief')),
  agency_id integer REFERENCES public.agencies(id),
  phone_number varchar,
  age integer CHECK (...),
  date_of_birth date CHECK (...),
  station_id integer REFERENCES public.agency_stations(id),
  created_at timestamptz DEFAULT now()
);
```

- Represents **all user roles** (residents, desk officers, field officers, chiefs).
- Already has:
  - `display_name`, `email`, `phone_number` for identity/contact.
  - `role` to distinguish user types.
  - `agency_id` → `agencies.id`.
  - `station_id` → `agency_stations.id`.

### 1.2 `incidents`

```sql
CREATE TABLE public.incidents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_type text NOT NULL CHECK (agency_type IN ('pnp','bfp','pdrrmo')),
  reporter_id uuid REFERENCES auth.users(id),
  reporter_name text NOT NULL,
  reporter_age integer NOT NULL,
  description text NOT NULL,
  latitude numeric NOT NULL,
  longitude numeric NOT NULL,
  location_address text,
  media_urls text[] DEFAULT '{}',
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','assigned','in_progress','resolved','closed')),
  assigned_officer_id uuid REFERENCES auth.users(id),
  assigned_station_id integer REFERENCES public.agency_stations(id),
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  resolved_at timestamptz,
  updated_by text,
  first_response_at timestamptz,
  assigned_officer_ids uuid[] DEFAULT '{}',
  reporter_phone text,
  reporter_latitude numeric,
  reporter_longitude numeric
);
```

- Already covers most responder-related incident data:
  - Status, agency, reporter info, coordinates, address, timestamps.
  - Assignment fields: `assigned_officer_id`, `assigned_officer_ids`, `assigned_station_id`.

### 1.3 `incident_status_history`

```sql
CREATE TABLE public.incident_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  incident_id uuid NOT NULL REFERENCES public.incidents(id),
  status text NOT NULL,
  notes text,
  changed_by text NOT NULL,
  changed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now()
);
```

- Tracks status changes over time.
- Fits the planned use for assignment/rejection/completion history.

### 1.4 Supporting tables

- `agencies`, `agency_stations` – model PNP/BFP/PDRRMO and their stations.
- `incident_updates`, `final_reports`, `media`, `notifications`, `push_tokens`, `security_logs` – optional/advanced features (updates, media, notifications, security), not strictly required for the first responder Android integration.

---

## 2. Planned responder schema (from SUPABASE_RESPONDER_SCHEMA.md)

Key ideas from the plan:

1. A dedicated **`responders`** table (logical role) with:
   - `id`, `full_name`, `phone`, `email`, `agency`, `location`, timestamps, `is_active`.
2. Enhanced **`incidents`** fields for responder flows:
   - `incident_type`, `status` including `rejected`, `address`, numeric lat/lon, `image_url`, `occurred_at`, `assigned_responder_id`, `assigned_responder_name`, `rejection_reason`.
3. A unified **`unit_reports`** table to store per-agency forms currently under Firebase `Reports/PNP|BFP|MDRRMO`.

---

## 3. Mapping existing schema to the plan

### 3.1 Responders: use `profiles` instead of a new `responders` table

We can *avoid creating a separate `responders` table* by reusing `profiles` with `role = 'Field Officer'` as the responder population.

**Logical mapping:**

- Planned `responders.id`           → `profiles.id`
- Planned `responders.full_name`    → `profiles.display_name`
- Planned `responders.phone`        → `profiles.phone_number`
- Planned `responders.email`        → `profiles.email`
- Planned `responders.agency`       → join `profiles.agency_id` → `agencies.short_name` (`PNP/BFP/PDRRMO`)
- Planned `responders.location`     → derived from `profiles.station_id` → `agency_stations.name` (or a future `municipality` column)
- Planned `is_active`, timestamps   → already partially covered via `created_at`; can add extra columns if needed.

**Conclusion:**
- **No new `responders` table is required.**
- Treat `profiles.role = 'Field Officer'` as responder accounts.

Optional improvement:
- Add `municipality text` to `profiles` if you want an explicit Basud/Daet/etc. field instead of relying purely on station name.

---

### 3.2 Incidents: reuse `incidents` with light extensions/mapping

The plan proposed extra columns (e.g. `incident_type`, `image_url`, `occurred_at`, `assigned_responder_id`).
Your existing `incidents` table already covers most of these needs:

- **Status**
  - Firebase: `Status` = "Pending", "Assigned", "Completed", "Rejected".
  - Supabase: `status` enum: `'pending','assigned','in_progress','resolved','closed'`.
  - Mapping options in code:
    - `pending`        ↔ "Pending"
    - `assigned`       ↔ "Assigned"
    - `in_progress`    ↔ "On-Process/Ongoing"
    - `resolved/closed`↔ "Completed"
    - For "Rejected", either:
      - Use `closed` + a note in `incident_status_history`, **or**
      - Relax the CHECK to allow `'rejected'` if you want it as a first-class status.

- **Responder assignment**
  - Firebase: `AssignedResponderUID`.
  - Supabase: `assigned_officer_id uuid` → `auth.users(id)`.
  - Android can treat `assigned_officer_id` as `AssignedResponderUID`.
  - Name (`AssignedResponderName`) can be derived from `profiles.display_name` – no need for a stored column.

- **Location & coordinates**
  - Firebase: `address`, `latitude`, `longitude` as strings.
  - Supabase: `location_address`, `latitude`, `longitude` as numeric.
  - Map Android `address` → `location_address`.

- **Reporter info**
  - Firebase: `reporterName` and optionally phone.
  - Supabase: `reporter_name`, `reporter_phone` – already present.

- **Incident type**
  - Plan: explicit `incident_type`.
  - Current: `agency_type` (`'pnp'|'bfp'|'pdrrmo'`).
  - Options:
    - Infer in code: `'pnp'` → `"Crime"`, `'bfp'` → `"Fire"`, `'pdrrmo'` → `"Disaster"`/`"Medical Emergency"`.
    - Or add `incident_type text` to `incidents` as a convenience.

- **Images/media**
  - Firebase: single `imageURL`.
  - Supabase: `media_urls text[]` and `media` table.
  - For Android, you can:
    - Use `media_urls[1]` as the primary `imageURL`; or
    - Join `media` and pick the first `photo` entry.
  - Optional: add `primary_image_url text` if you want a dedicated field.

- **Occurred at / date+time**
  - Firebase: separate `date` and `Time` strings.
  - Supabase: `created_at` and optional `resolved_at`, `first_response_at`.
  - You can:
    - Treat `created_at` as the incident time for dashboards, or
    - Add `occurred_at timestamptz` if you need separate “time of incident” vs “time recorded”.

**Conclusion for `incidents`:**
- You can **reuse the existing `incidents` table** as-is with smart mapping in the Android app.
- Optional schema tweaks (not required but useful):
  - Add `incident_type text`.
  - Add `occurred_at timestamptz`.
  - Relax `status` CHECK to also allow `'rejected'` if you want that exact label.

---

### 3.3 Incident history: `incident_status_history`

- Your current `incident_status_history` matches the planned structure:
  - `incident_id`, `status`, `notes`, `changed_by`, `changed_at`.
- No schema change needed.
- Implementation: ensure you insert rows for:
  - Assignment (`status = 'assigned'`).
  - Rejection (`status = 'rejected'` or mapped equivalent).
  - Completion (`status = 'resolved'`/`'closed'`).

---

## 4. New table to add: `unit_reports`

The only **missing** piece in your current Supabase schema for the responder Android flows is a table that represents the per-agency reports under Firebase `IresponderApp/Reports/...`.

In Firebase:
- Path: `IresponderApp/Reports/{agencyFolder}/{reportId}`
- `agencyFolder` ∈ {`"PNP"`, `"BFP"`, `"MDRRMO"`}
- Fields used:
  - `incidentKey` (links to incident)
  - `responderUid` (current responder)
  - `timestamp`
  - MDRRMO: `natureOfCall`, `emergencyType`
  - BFP: `fireLocation`
  - PNP: other form fields, with title hard-coded in UI

**Proposed Supabase table: `unit_reports`**

```sql
CREATE TABLE public.unit_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  incident_id uuid NOT NULL REFERENCES public.incidents(id),
  responder_id uuid NOT NULL REFERENCES public.profiles(id),
  agency text NOT NULL CHECK (agency IN ('PNP','BFP','MDRRMO')),
  title text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  details jsonb NOT NULL
);
```

- `incident_id`   – maps Firebase `incidentKey`.
- `responder_id`  – maps Firebase `responderUid`.
- `agency`        – corresponds to `agencyFolder`.
- `title`         – what `FormsFragment` currently computes for row title.
- `details`       – entire unit-specific form payload (MDRRMO/BFP/PNP fields) as JSON.

Android `FormsFragment` logic can be implemented by querying:

```sql
SELECT *
FROM unit_reports
WHERE responder_id = auth.uid()
ORDER BY created_at DESC;
```

and building `displayName`/rows from `agency` + `details`, mirroring the existing Java logic for `MDRRMO`, `BFP`, `PNP`.

---

## 5. Final checklist: what to add/change

### 5.1 Add

- **New table: `unit_reports`** (see SQL sketch above).

### 5.2 Reuse (no new tables needed)

- **Responders** → `profiles` with `role = 'Field Officer'`.
- **Incidents** → existing `incidents` table.
- **Status history** → existing `incident_status_history`.
- **Agencies & stations** → `agencies`, `agency_stations`.

### 5.3 Optional, but recommended tweaks

- `profiles`:
  - Add `municipality text` if you need a dedicated LGU field instead of deriving from station.

- `incidents`:
  - Add `incident_type text` if you want to store labels like "Crime"/"Fire"/"Medical Emergency".
  - Add `occurred_at timestamptz` if you want a separate incident timestamp.
  - Relax `status` CHECK constraint to optionally include `'rejected'`.

With these pieces in place, the Android responder app can be gradually migrated from Firebase to Supabase using the existing schema plus the single new `unit_reports` table.
