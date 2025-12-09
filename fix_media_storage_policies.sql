-- Create the storage bucket 'incident-media' if it doesn't exist
insert into storage.buckets (id, name, public)
values ('incident-media', 'incident-media', true)
on conflict (id) do nothing;

-- Remove existing policies to avoid conflicts (safely)
drop policy if exists "Public Read Access" on storage.objects;
drop policy if exists "Authenticated Upload Access" on storage.objects;
drop policy if exists "Authenticated Update Access" on storage.objects;
drop policy if exists "Authenticated Delete Access" on storage.objects;

-- Create comprehensive policies for 'incident-media' bucket

-- 1. Allow public read access to incident media (useful for dashboards/admin panels)
create policy "Public Read Access"
on storage.objects for select
using ( bucket_id = 'incident-media' );

-- 2. Allow authenticated users to upload files to 'incident-media'
create policy "Authenticated Upload Access"
on storage.objects for insert
to authenticated
with check ( bucket_id = 'incident-media' );

-- 3. Allow authenticated users to update their own files (optional but good practice)
create policy "Authenticated Update Access"
on storage.objects for update
to authenticated
using ( bucket_id = 'incident-media' and auth.uid() = owner );

-- 4. Allow authenticated users to delete their own files
create policy "Authenticated Delete Access"
on storage.objects for delete
to authenticated
using ( bucket_id = 'incident-media' and auth.uid() = owner );
