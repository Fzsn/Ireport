-- ============================================================================
-- DIAGNOSTIC: Check RLS status and existing policies
-- Run this in Supabase SQL Editor to see what's currently configured
-- ============================================================================

-- Check if RLS is enabled on final_report_drafts
SELECT 
    schemaname,
    tablename,
    rowsecurity as rls_enabled
FROM pg_tables
WHERE tablename = 'final_report_drafts';

-- Check existing policies on final_report_drafts
SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual,
    with_check
FROM pg_policies
WHERE tablename = 'final_report_drafts';

-- Check if RLS is enabled on unit_reports (for comparison)
SELECT 
    schemaname,
    tablename,
    rowsecurity as rls_enabled
FROM pg_tables
WHERE tablename = 'unit_reports';

-- Check existing policies on unit_reports (for comparison)
SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual,
    with_check
FROM pg_policies
WHERE tablename = 'unit_reports';

-- Check if the current user exists in profiles table
SELECT 
    id,
    display_name,
    email,
    role,
    agency_id
FROM public.profiles
WHERE id = auth.uid();
