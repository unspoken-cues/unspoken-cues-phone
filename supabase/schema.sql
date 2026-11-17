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

-- No insert policy on purpose: creating a swap needs proof that the other person shared
-- their card. swap_by_card_token() (section 12) checks that proof and inserts the row.

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
drop policy if exists "Users can leave events"           on public.event_members;

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

-- Leaving an event removes the caller's own membership row (never someone else's).
create policy "Users can leave events"
  on public.event_members for delete
  using (auth.uid() = user_id);

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
-- 12. Card tokens + swapping. Each user has one secret token; their QR code and
--     share link carry it. Whoever presents the token proves the owner shared
--     their card, which is what lets swap_by_card_token() create the swap.
--     Tokens live in their own table (not on profiles) because public profiles
--     are readable by anyone, and a readable token would let anyone swap with
--     everyone.
-- ---------------------------------------------------------------------------
create table if not exists public.card_tokens (
  user_id     uuid        primary key references auth.users (id) on delete cascade,
  token       text        not null unique default replace(gen_random_uuid()::text, '-', ''),
  created_at  timestamptz not null default now()
);

comment on table public.card_tokens is 'Secret token behind each user''s S.W.A.P. card QR code and share link.';

alter table public.card_tokens enable row level security;

drop policy if exists "Owner can read own card token" on public.card_tokens;

create policy "Owner can read own card token"
  on public.card_tokens for select
  using (auth.uid() = user_id);

-- No insert/update/delete policies: my_card_token() creates the row.

-- Returns the caller's card token, creating it the first time.
create or replace function public.my_card_token()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  t text;
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  insert into public.card_tokens (user_id)
  values (auth.uid())
  on conflict (user_id) do nothing;

  select ct.token into t from public.card_tokens ct where ct.user_id = auth.uid();
  return t;
end;
$$;

revoke execute on function public.my_card_token() from public, anon;
grant  execute on function public.my_card_token() to authenticated;

-- Swaps cards between the caller and the owner of the token, so each appears in the
-- other's binder, and returns the owner's profile. Swapping twice is a no-op.
-- SECURITY DEFINER because the caller can read neither the token nor (if it is
-- private) the owner's profile before the swap exists.
create or replace function public.swap_by_card_token(card_token text)
returns public.profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  me       uuid := auth.uid();
  owner_id uuid;
  p        public.profiles;
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  select ct.user_id into owner_id
  from public.card_tokens ct
  where ct.token = lower(trim(card_token));

  if not found then
    raise exception 'No card with that code' using errcode = 'P0002';
  end if;

  if owner_id = me then
    raise exception 'That is your own card' using errcode = '22023';
  end if;

  select * into p from public.profiles pr where pr.id = owner_id;

  if not found then
    raise exception 'No card with that code' using errcode = 'P0002';
  end if;

  insert into public.swaps (user_a, user_b)
  values (least(me, owner_id), greatest(me, owner_id))
  on conflict (user_a, user_b) do nothing;

  return p;
end;
$$;

revoke execute on function public.swap_by_card_token(text) from public, anon;
grant  execute on function public.swap_by_card_token(text) to authenticated;

-- ---------------------------------------------------------------------------
-- 13. Onboarding: whether the user has finished the first-run walkthrough.
--     False for new rows; the app sets it to true once and never back.
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column if not exists onboarding_complete boolean not null default false;

-- ---------------------------------------------------------------------------
-- 14. Event pages: when, where, a cover image, tags, a public flag and RSVPs.
--     Existing events keep working: every new column has a default.
--       starts_at / ends_at  when it happens (ends_at null = starts_at + 3 hours)
--       location             a place name or address, free text
--       cover_url            public URL of an image in the event-covers bucket
--       tags                 short lowercase labels people filter by
--       is_public            true = shows in everyone's Discover feed
--       host_name            the host's display name when the event was made
--       going_count          people whose RSVP is "going" (kept by a trigger)
--     event_members.rsvp is "going" or "maybe". Joining by code counts as going.
-- ---------------------------------------------------------------------------
alter table public.events
  add column if not exists starts_at   timestamptz,
  add column if not exists ends_at     timestamptz,
  add column if not exists location    text    not null default '',
  add column if not exists cover_url   text    not null default '',
  add column if not exists tags        text[]  not null default '{}',
  add column if not exists is_public   boolean not null default false,
  add column if not exists host_name   text    not null default '',
  add column if not exists going_count integer not null default 0;

create index if not exists events_starts_at_idx on public.events (starts_at);
create index if not exists events_public_idx on public.events (is_public) where is_public;

alter table public.event_members
  add column if not exists rsvp text not null default 'going';

alter table public.event_members drop constraint if exists event_members_rsvp_check;
alter table public.event_members
  add constraint event_members_rsvp_check check (rsvp in ('going', 'maybe'));

-- Keep events.going_count in step with the "going" rows.
create or replace function public.refresh_going_count()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  ev uuid := coalesce(new.event_id, old.event_id);
begin
  update public.events
     set going_count = (select count(*) from public.event_members
                         where event_id = ev and rsvp = 'going')
   where id = ev;
  return null;
end;
$$;

drop trigger if exists event_members_going_count on public.event_members;
create trigger event_members_going_count
  after insert or update of rsvp or delete on public.event_members
  for each row execute function public.refresh_going_count();

-- Public events are readable by every signed-in user (that is what Discover lists).
drop policy if exists "Anyone can read public events" on public.events;
create policy "Anyone can read public events"
  on public.events for select
  to authenticated
  using (is_public);

-- A member can change their own RSVP (never someone else's).
drop policy if exists "Members can update own rsvp" on public.event_members;
create policy "Members can update own rsvp"
  on public.event_members for update
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- event-covers Storage bucket: anyone can view; a signed-in user writes only inside the
-- folder named after their own user id, e.g. event-covers/<auth.uid()>/<file>.jpg
insert into storage.buckets (id, name, public)
values ('event-covers', 'event-covers', true)
on conflict (id) do update set public = true;

drop policy if exists "Event covers are publicly readable"   on storage.objects;
drop policy if exists "Users can upload own event covers"    on storage.objects;
drop policy if exists "Users can update own event covers"    on storage.objects;
drop policy if exists "Users can delete own event covers"    on storage.objects;

create policy "Event covers are publicly readable"
  on storage.objects for select
  using (bucket_id = 'event-covers');

create policy "Users can upload own event covers"
  on storage.objects for insert
  to authenticated
  with check (bucket_id = 'event-covers' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "Users can update own event covers"
  on storage.objects for update
  to authenticated
  using (bucket_id = 'event-covers' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'event-covers' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "Users can delete own event covers"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'event-covers' and (storage.foldername(name))[1] = auth.uid()::text);

-- ---------------------------------------------------------------------------
-- 15. Card image: the picture a user puts on their S.W.A.P. card. Public URL of
--     an image in the avatars bucket (avatars/<user id>/card.jpg), blank for none.
--     Swap partners read it through the profile row, like avatar_url.
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column if not exists card_image_url text not null default '';

-- ---------------------------------------------------------------------------
-- 16. Event announcements (same definitions as 202611170001_event_announcements.sql).
-- ---------------------------------------------------------------------------
-- Event announcements. Apply after the event-page columns in schema.sql exist.
-- No direct client writes: the RPC supplies server identity, IDs and timestamps.
begin;

create table if not exists public.event_announcements (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.events (id) on delete cascade,
  body text not null,
  created_at timestamptz not null default clock_timestamp(),
  request_id uuid not null,
  unique (event_id, request_id),
  constraint event_announcements_body_check check (
    char_length(body) between 1 and 1000
    and body = btrim(body, E' \t\n\r\f' || chr(11) || U&'\001C\001D\001E\001F\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000')
  )
);
create index if not exists event_announcements_feed_idx
  on public.event_announcements (event_id, created_at desc, id desc);

alter table public.event_announcements enable row level security;
revoke all on public.event_announcements from public, anon, authenticated;
grant select on public.event_announcements to authenticated;

drop policy if exists "Host and joined members read announcements" on public.event_announcements;
create policy "Host and joined members read announcements"
  on public.event_announcements for select to authenticated
  using (
    auth.uid() is not null
    and (
      exists (select 1 from public.events e where e.id = event_id and e.host_id = auth.uid())
      or public.is_event_member(event_id)
    )
  );

create or replace function public.post_event_announcement(p_event_id uuid, p_body text, p_request_id uuid)
returns public.event_announcements
language plpgsql
security definer
set search_path = ''
as $$
declare
  ev public.events;
  saved public.event_announcements;
  clean_body text := btrim(p_body, E' \t\n\r\f' || chr(11) || U&'\001C\001D\001E\001F\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000');
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  -- Serialize posts against host changes, ending/deleting the event and concurrent retries.
  select * into ev from public.events where id = p_event_id for update;
  if not found or ev.host_id <> auth.uid() then
    raise exception 'Only the host can post' using errcode = '42501';
  end if;
  if clean_body is null or char_length(clean_body) not between 1 and 1000 or p_request_id is null then
    raise exception 'Announcement must contain 1–1000 characters and a request ID' using errcode = '22023';
  end if;
  select * into saved from public.event_announcements
    where event_id = p_event_id and request_id = p_request_id;
  if found then
    if saved.body <> clean_body then
      raise exception 'Request ID already used for another announcement' using errcode = '22023';
    end if;
    return saved; -- A lost response can be retried even after the event ends; no new write.
  end if;
  if not ev.active or coalesce(ev.ends_at, ev.starts_at + interval '3 hours') <= clock_timestamp() then
    raise exception 'Event has ended' using errcode = '42501';
  end if;
  insert into public.event_announcements (event_id, body, request_id)
    values (p_event_id, clean_body, p_request_id)
    returning * into saved;
  return saved;
end;
$$;

revoke all on function public.post_event_announcement(uuid, text, uuid) from public, anon, authenticated;
grant execute on function public.post_event_announcement(uuid, text, uuid) to authenticated;
comment on table public.event_announcements is 'Plain-text host updates readable only by the host and current event members.';

commit;

-- ---------------------------------------------------------------------------
-- Manual test notes
-- ---------------------------------------------------------------------------
-- 1. Paste this whole file into the SQL editor and run it. Run it a second time:
--    it must finish with no errors and change nothing.
--
-- 2. Check that everything exists (each query should return the rows noted):
--
--    select column_name from information_schema.columns
--     where table_schema = 'public' and table_name = 'profiles'
--       and column_name in ('avatar_url', 'onboarding_complete');  -- 2 rows
--
--    select tablename, rowsecurity from pg_tables
--     where schemaname = 'public' and tablename in ('events', 'event_members');  -- 2 rows, both true
--
--    select tablename, policyname from pg_policies
--     where (schemaname = 'public' and tablename in ('events', 'event_members'))
--        or (schemaname = 'storage' and policyname ilike '%avatar%')
--     order by 1, 2;  -- 5 on events, 4 on event_members, 4 on objects
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
--
-- 4. Try the swap flow, again impersonating real users from auth.users:
--
--    begin;
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select public.my_card_token();                        -- 32 hex characters
--      select public.my_card_token();                        -- the same token again
--      select * from public.card_tokens;                     -- one row: user 1 only
--      select * from public.swap_by_card_token('<that token>');  -- error: That is your own card
--      select * from public.swap_by_card_token('nope');      -- error: No card with that code
--    rollback;
--
--    Run my_card_token() for user 1 without the rollback to keep a token, then:
--
--    begin;
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-2-id>", "role": "authenticated"}';
--      select * from public.swap_by_card_token('<user 1 token>');  -- returns user 1's profile
--      select * from public.swap_by_card_token('<user 1 token>');  -- same row again, no error
--      select * from public.swaps;                                 -- one row holding both users
--      select * from public.card_tokens;                           -- no rows: user 1's token stays hidden
--    rollback;

-- 16. Quick reactions (same definitions as 202611170002_quick_reactions.sql).
-- Event-scoped waves. No table access: authenticated clients use the three RPCs below.
-- A wave is one per directed pair/event, even after consent is switched off and on.
create table if not exists public.event_wave_preferences (
  event_id uuid not null references public.events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  receive_waves boolean not null default false,
  primary key (event_id, user_id)
);
create table if not exists public.event_waves (
  event_id uuid not null references public.events(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  recipient_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (event_id, sender_id, recipient_id),
  check (sender_id <> recipient_id)
);
create index if not exists event_waves_inbox_idx
  on public.event_waves(event_id, recipient_id, created_at desc);
alter table public.event_wave_preferences enable row level security;
alter table public.event_waves enable row level security;
revoke all on public.event_wave_preferences, public.event_waves from public, anon, authenticated;

create or replace function public.event_wave_participant(p_event_id uuid, p_user_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.events e where e.id = p_event_id and
    (e.host_id = p_user_id or exists (select 1 from public.event_members m
      where m.event_id = e.id and m.user_id = p_user_id)));
$$;
revoke all on function public.event_wave_participant(uuid, uuid) from public, anon, authenticated;

-- A private snapshot, not a participant preference directory. Targets are actionable public
-- Green/Yellow participants only, and only returned to an eligible opted-in caller.
create or replace function public.event_wave_state(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  opted boolean;
  eligible boolean;
  targets jsonb := '[]'::jsonb;
  inbox jsonb := '[]'::jsonb;
begin
  -- Keep the caller's membership, profile and consent stable throughout this private read.
  -- Other participants are filtered in the same statements that read their names/history.
  perform 1 from public.events e where e.id = p_event_id for share;
  perform 1 from public.event_members m where m.event_id = p_event_id and m.user_id = me for key share;
  perform 1 from public.profiles p where p.id = me for share;
  if me is null or not public.event_wave_participant(p_event_id, me) then
    raise exception 'Event participant required' using errcode = '42501';
  end if;
  select coalesce((select w.receive_waves from public.event_wave_preferences w
    where w.event_id = p_event_id and w.user_id = me), false) into opted;
  select opted and exists (select 1 from public.events e join public.profiles p on p.id = me
    where e.id = p_event_id and e.active
      and (coalesce(e.ends_at, e.starts_at + interval '3 hours') is null
        or coalesce(e.ends_at, e.starts_at + interval '3 hours') > clock_timestamp())
      and p.is_public and p.status in ('GREEN', 'YELLOW')) into eligible;
  if eligible then
    select coalesce(jsonb_agg(jsonb_build_object(
      'user_id', p.id, 'display_name', p.display_name,
      'sent', exists (select 1 from public.event_waves w where w.event_id = p_event_id
        and w.sender_id = me and w.recipient_id = p.id)) order by p.display_name, p.id), '[]'::jsonb)
    into targets from public.profiles p join public.event_wave_preferences pref on pref.user_id = p.id
    where pref.event_id = p_event_id and pref.receive_waves and p.id <> me
      and p.is_public and p.status in ('GREEN', 'YELLOW')
      and public.event_wave_participant(p_event_id, p.id);
    select coalesce(jsonb_agg(jsonb_build_object(
      'sender_id', p.id, 'sender_name', p.display_name, 'created_at', w.created_at)
      order by w.created_at desc, w.sender_id), '[]'::jsonb)
    into inbox from public.event_waves w join public.profiles p on p.id = w.sender_id
      join public.event_wave_preferences pref on pref.event_id = w.event_id and pref.user_id = p.id
    where w.event_id = p_event_id and w.recipient_id = me and pref.receive_waves
      and p.is_public and p.status in ('GREEN', 'YELLOW')
      and public.event_wave_participant(p_event_id, p.id);
  end if;
  return jsonb_build_object('receive_waves', opted, 'can_wave', eligible,
    'recipients', targets, 'inbox', inbox);
end;
$$;

create or replace function public.set_event_wave_preference(p_event_id uuid, p_receive_waves boolean)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  -- Event lock serializes consent and sending. Membership locks prevent leaving mid-write.
  perform 1 from public.events e where e.id = p_event_id for update;
  perform 1 from public.event_members m where m.event_id = p_event_id and m.user_id = me for key share;
  if me is null or not public.event_wave_participant(p_event_id, me) then
    raise exception 'Event participant required' using errcode = '42501';
  end if;
  if p_receive_waves is null then
    raise exception 'Consent must be explicit' using errcode = '22023';
  end if;
  insert into public.event_wave_preferences(event_id, user_id, receive_waves)
    values (p_event_id, me, p_receive_waves)
    on conflict (event_id, user_id) do update set receive_waves = excluded.receive_waves;
  -- Historical rows stay private while off, retaining the one-wave limit after re-enabling.
  return public.event_wave_state(p_event_id);
end;
$$;

create or replace function public.send_event_wave(p_event_id uuid, p_recipient_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  ev public.events;
  sent_at timestamptz;
begin
  select * into ev from public.events e where e.id = p_event_id for update;
  if me is null or me = p_recipient_id or not found then
    raise exception 'Wave unavailable' using errcode = '42501';
  end if;
  perform 1 from public.event_members m where m.event_id = p_event_id
    and m.user_id in (me, p_recipient_id) order by m.user_id for key share;
  -- Lock profiles in a consistent order: visibility/cue updates cannot race this check.
  perform 1 from public.profiles p where p.id in (me, p_recipient_id) order by p.id for update;
  if not ev.active or coalesce(ev.ends_at, ev.starts_at + interval '3 hours') <= clock_timestamp()
    or not public.event_wave_participant(p_event_id, me)
    or not public.event_wave_participant(p_event_id, p_recipient_id)
    or (select count(*) from public.profiles p where p.id in (me, p_recipient_id)
      and p.is_public and p.status in ('GREEN', 'YELLOW')) <> 2
    or (select count(*) from public.event_wave_preferences pref where pref.event_id = p_event_id
      and pref.user_id in (me, p_recipient_id) and pref.receive_waves) <> 2 then
    raise exception 'Wave unavailable' using errcode = '42501';
  end if;
  insert into public.event_waves(event_id, sender_id, recipient_id)
    values (p_event_id, me, p_recipient_id) on conflict (event_id, sender_id, recipient_id) do nothing;
  select w.created_at into sent_at from public.event_waves w
    where w.event_id = p_event_id and w.sender_id = me and w.recipient_id = p_recipient_id;
  return jsonb_build_object('created_at', sent_at);
end;
$$;

-- Leaving resets consent; joining again always requires a fresh opt-in. Keep historical waves
-- (hidden unless both remain eligible) so leaving/rejoining cannot circumvent the unique limit.
create or replace function public.reset_event_wave_consent_on_leave()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  delete from public.event_wave_preferences w where w.event_id = old.event_id and w.user_id = old.user_id;
  return old;
end;
$$;
drop trigger if exists event_members_reset_wave_consent on public.event_members;
create trigger event_members_reset_wave_consent after delete on public.event_members
  for each row execute function public.reset_event_wave_consent_on_leave();
revoke all on function public.reset_event_wave_consent_on_leave() from public, anon, authenticated;
revoke all on function public.event_wave_state(uuid), public.set_event_wave_preference(uuid, boolean),
  public.send_event_wave(uuid, uuid) from public, anon;
grant execute on function public.event_wave_state(uuid), public.set_event_wave_preference(uuid, boolean),
  public.send_event_wave(uuid, uuid) to authenticated;
