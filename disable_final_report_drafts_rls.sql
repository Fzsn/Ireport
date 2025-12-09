-- ============================================================================
-- QUICK FIX: Disable RLS on final_report_drafts
-- This allows all authenticated users to insert/update/delete drafts
-- Use this if you want to match the behavior of unit_reports (if it has no RLS)
-- ============================================================================

-- Disable RLS on final_report_drafts table
ALTER TABLE public.final_report_drafts DISABLE ROW LEVEL SECURITY;

-- Note: This means ANY authenticated user can access ANY draft
-- If you need proper security, use fix_final_report_drafts_rls.sql instead
