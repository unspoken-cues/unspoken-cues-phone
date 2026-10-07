-- Unspoken Cues — Supabase schema
-- Run this in the Supabase SQL editor (Dashboard -> SQL Editor -> New query).
-- Safe to re-run: uses IF NOT EXISTS / CREATE OR REPLACE where possible.

-- ---------------------------------------------------------------------------
-- 1. Status enum: exactly four states (matches the Android CueStatus enum).
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_type where typname = 'cue_status') then
    create type public.cue_status as enum ('GREEN', 'YELLOW', 'RED', 'PURPLE');
  end if;
end$$;

-- ---------------------------------------------------------------------------
-- 2. profiles table: one row per authenticated user.
--    id references auth.users so a profile is deleted when the account is.
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  display_name  text        not null default '',
  bio           text        not null default '',
  preferences   text[]      not null default '{}',
  boundaries    text[]      not null default '{}',
  is_public     boolean     not null default true,
  status        public.cue_status not null default 'GREEN',
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.profiles is 'Account-backed identity, status and visibility for Unspoken Cues.';

-- ---------------------------------------------------------------------------
-- 3. keep updated_at fresh on every write.
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- 4. Auto-create a profile row when a new auth user signs up.
--    SECURITY DEFINER so it can insert despite RLS.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', ''))
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 5. Row Level Security: a user may only read/write their own row.
--    Publicly visible profiles are additionally readable by anyone — this
--    supports the QR "public profile" view without exposing private rows.
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;

drop policy if exists "Owner can read own profile"   on public.profiles;
drop policy if exists "Public profiles are readable"  on public.profiles;
drop policy if exists "Owner can insert own profile"  on public.profiles;
drop policy if exists "Owner can update own profile"  on public.profiles;
drop policy if exists "Owner can delete own profile"  on public.profiles;

create policy "Owner can read own profile"
  on public.profiles for select
  using (auth.uid() = id);

create policy "Public profiles are readable"
  on public.profiles for select
  using (is_public = true);

create policy "Owner can insert own profile"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "Owner can update own profile"
  on public.profiles for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

create policy "Owner can delete own profile"
  on public.profiles for delete
  using (auth.uid() = id);

-- ---------------------------------------------------------------------------
-- 6. swaps table: one row per pair of users who hold each other's S.W.A.P. card.
--    The pair is stored once (user_a < user_b), so deleting the row removes each
--    person from the other's binder at the same time.
-- ---------------------------------------------------------------------------
create table if not exists public.swaps (
  user_a      uuid        not null references auth.users (id) on delete cascade,
  user_b      uuid        not null references auth.users (id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (user_a, user_b),
  constraint swaps_ordered check (user_a < user_b)
);

create index if not exists swaps_user_b_idx on public.swaps (user_b);

comment on table public.swaps is 'Mutual S.W.A.P. card connections between two users.';

alter table public.swaps enable row level security;

drop policy if exists "Members can read their swaps"   on public.swaps;
drop policy if exists "Members can delete their swaps" on public.swaps;

create policy "Members can read their swaps"
  on public.swaps for select
  using (auth.uid() in (user_a, user_b));

-- Either person can end the swap; the single shared row means it ends for both.
create policy "Members can delete their swaps"
  on public.swaps for delete
  using (auth.uid() in (user_a, user_b));

-- No insert policy on purpose: creating a swap needs proof that both people took part
-- (e.g. a scanned QR token), which will be added with the collect flow.

-- Swap partners can read each other's profile even when it is not public.
drop policy if exists "Swap partners can read each other" on public.profiles;
create policy "Swap partners can read each other"
  on public.profiles for select
  using (
    exists (
      select 1 from public.swaps s
      where (s.user_a = auth.uid() and s.user_b = profiles.id)
         or (s.user_b = auth.uid() and s.user_a = profiles.id)
    )
  );

-- ---------------------------------------------------------------------------
-- 7. Profile photo: public URL of the user's image in the avatars bucket
--    (section 11). Empty string means no photo.
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column if not exists avatar_url text not null default '';

-- ---------------------------------------------------------------------------
-- 8. events + event_members: an event is hosted by one user and joined by
--    others with a short join code.
-- ---------------------------------------------------------------------------
create table if not exists public.events (
  id          uuid        primary key default gen_random_uuid(),
  host_id     uuid        not null default auth.uid() references auth.users (id) on delete cascade,
  name        text        not null,
  details     text        not null default '',
  join_code   text        not null unique,
  active      boolean     not null default true,
  created_at  timestamptz not null default now()
);

create index if not exists events_host_id_idx on public.events (host_id);

comment on table public.events is 'Events a user hosts; others join with the join code.';

create table if not exists public.event_members (
  event_id   uuid        not null references public.events (id) on delete cascade,
  user_id    uuid        not null references auth.users (id) on delete cascade,
  joined_at  timestamptz not null default now(),
  primary key (event_id, user_id)
);

create index if not exists event_members_user_id_idx on public.event_members (user_id);

comment on table public.event_members is 'Who has joined which event.';

-- ---------------------------------------------------------------------------
-- 9. Row Level Security for events + event_members.
--    is_event_member() is SECURITY DEFINER so the policies below can check
--    membership without re-triggering RLS on event_members (which would recurse).
-- ---------------------------------------------------------------------------
create or replace function public.is_event_member(p_event_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.event_members m
    where m.event_id = p_event_id and m.user_id = auth.uid()
  );
$$;

alter table public.events enable row level security;

drop policy if exists "Host can read own events"         on public.events;
drop policy if exists "Members can read joined events"   on public.events;
drop policy if exists "Host can insert own events"       on public.events;
drop policy if exists "Host can update own events"       on public.events;
drop policy if exists "Host can delete own events"       on public.events;

create policy "Host can read own events"
  on public.events for select
  using (auth.uid() = host_id);

create policy "Members can read joined events"
  on public.events for select
  using (public.is_event_member(id));

create policy "Host can insert own events"
  on public.events for insert
  with check (auth.uid() = host_id);

create policy "Host can update own events"
  on public.events for update
  using (auth.uid() = host_id)
  with check (auth.uid() = host_id);

create policy "Host can delete own events"
  on public.events for delete
  using (auth.uid() = host_id);

alter table public.event_members enable row level security;

drop policy if exists "Users can join as themselves"     on public.event_members;
drop policy if exists "Members can read co-members"      on public.event_members;
drop policy if exists "Host can read event members"      on public.event_members;

-- Any signed-in user may add a row for themselves (never for someone else).
create policy "Users can join as themselves"
  on public.event_members for insert
  to authenticated
  with check (auth.uid() = user_id);

create policy "Members can read co-members"
  on public.event_members for select
  using (public.is_event_member(event_id));

-- The host sees who joined even if they have not joined their own event.
create policy "Host can read event members"
  on public.event_members for select
  using (
    exists (
      select 1 from public.events e
      where e.id = event_members.event_id and e.host_id = auth.uid()
    )
  );

-- ---------------------------------------------------------------------------
-- 10. join_event_by_code: look up an active event by its join code and add the
--     caller as a member. SECURITY DEFINER because a non-member cannot read the
--     event row to find its id. Codes match case-insensitively; joining twice
--     is a no-op. Returns the joined event.
-- ---------------------------------------------------------------------------
create or replace function public.join_event_by_code(code text)
returns public.events
language plpgsql
security definer
set search_path = public
as $$
declare
  ev public.events;
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  select * into ev
  from public.events e
  where upper(e.join_code) = upper(trim(code)) and e.active
  limit 1;

  if not found then
    raise exception 'No active event with that code' using errcode = 'P0002';
  end if;

  insert into public.event_members (event_id, user_id)
  values (ev.id, auth.uid())
  on conflict (event_id, user_id) do nothing;

  return ev;
end;
$$;

revoke execute on function public.join_event_by_code(text) from public, anon;
grant  execute on function public.join_event_by_code(text) to authenticated;

-- ---------------------------------------------------------------------------
-- 11. avatars Storage bucket: anyone can view images; a signed-in user can only
--     write inside the folder named after their own user id, e.g.
--     avatars/<auth.uid()>/photo.jpg
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do update set public = true;

drop policy if exists "Avatars are publicly readable"   on storage.objects;
drop policy if exists "Users can upload own avatar"     on storage.objects;
drop policy if exists "Users can update own avatar"     on storage.objects;
drop policy if exists "Users can delete own avatar"     on storage.objects;

create policy "Avatars are publicly readable"
  on storage.objects for select
  using (bucket_id = 'avatars');

create policy "Users can upload own avatar"
  on storage.objects for insert
  to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "Users can update own avatar"
  on storage.objects for update
  to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "Users can delete own avatar"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ---------------------------------------------------------------------------
-- Manual test notes
-- ---------------------------------------------------------------------------
-- 1. Paste this whole file into the SQL editor and run it. Run it a second time:
--    it must finish with no errors and change nothing.
--
-- 2. Check that everything exists (each query should return the rows noted):
--
--    select column_name from information_schema.columns
--     where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url';  -- 1 row
--
--    select tablename, rowsecurity from pg_tables
--     where schemaname = 'public' and tablename in ('events', 'event_members');  -- 2 rows, both true
--
--    select tablename, policyname from pg_policies
--     where (schemaname = 'public' and tablename in ('events', 'event_members'))
--        or (schemaname = 'storage' and policyname ilike '%avatar%')
--     order by 1, 2;  -- 5 on events, 3 on event_members, 4 on objects
--
--    select id, public from storage.buckets where id = 'avatars';  -- 1 row, public = true
--
--    select proname, prosecdef from pg_proc
--     where proname in ('join_event_by_code', 'is_event_member');  -- 2 rows, both true
--
-- 3. Try the join flow. The SQL editor runs as an admin with no signed-in user,
--    so impersonate one first (replace the ids with real rows from auth.users):
--
--    insert into public.events (host_id, name, join_code)
--    values ('<host-user-id>', 'Test Mixer', 'MIXER1');
--
--    begin;
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<other-user-id>", "role": "authenticated"}';
--      select * from public.join_event_by_code('mixer1');   -- returns the Test Mixer row
--      select * from public.join_event_by_code('mixer1');   -- same row again, no error
--      select * from public.event_members;                  -- one row: the other user
--      select * from public.join_event_by_code('nope');     -- error: No active event with that code
--    rollback;
--
--    delete from public.events where join_code = 'MIXER1';  -- clean up
