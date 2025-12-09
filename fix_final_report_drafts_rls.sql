-- ============================================================================
-- FIX FOR: "new row violates row level security policy" 
-- ISSUE: final_report_drafts table has RLS enabled but no policies defined
-- ============================================================================

-- IMPORTANT: The author_id column has a foreign key to profiles(id)
-- Make sure users exist in the profiles table before they can save drafts
-- If you're getting FK constraint errors, you may need to:
-- 1. Create a trigger to auto-create profile entries when users sign up
-- 2. Or manually insert profile records for existing auth.users

-- Step 1: Enable RLS on final_report_drafts table
ALTER TABLE public.final_report_drafts ENABLE ROW LEVEL SECURITY;

-- Step 3: Drop existing policies if any (to avoid conflicts)
DROP POLICY IF EXISTS "Users can view their own drafts" ON public.final_report_drafts;
DROP POLICY IF EXISTS "Users can insert their own drafts" ON public.final_report_drafts;
DROP POLICY IF EXISTS "Users can update their own drafts" ON public.final_report_drafts;
DROP POLICY IF EXISTS "Users can delete their own drafts" ON public.final_report_drafts;

-- Step 4: Create RLS policies

-- Policy 1: Allow users to view their own drafts
CREATE POLICY "Users can view their own drafts"
ON public.final_report_drafts
FOR SELECT
TO authenticated
USING (
    auth.uid() = author_id 
    OR 
    author_id IS NULL
);

-- Policy 2: Allow users to insert their own drafts
-- This policy allows insert if the author_id matches the authenticated user
CREATE POLICY "Users can insert their own drafts"
ON public.final_report_drafts
FOR INSERT
TO authenticated
WITH CHECK (
    auth.uid() = author_id
);

-- Policy 3: Allow users to update their own drafts
CREATE POLICY "Users can update their own drafts"
ON public.final_report_drafts
FOR UPDATE
TO authenticated
USING (
    auth.uid() = author_id 
    OR 
    author_id IS NULL
)
WITH CHECK (
    auth.uid() = author_id
);

-- Policy 4: Allow users to delete their own drafts
CREATE POLICY "Users can delete their own drafts"
ON public.final_report_drafts
FOR DELETE
TO authenticated
USING (
    auth.uid() = author_id 
    OR 
    author_id IS NULL
);
