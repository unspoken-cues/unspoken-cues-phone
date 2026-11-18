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
--    Other people's profiles are read only through binder_cards() and
--    event_attendees() (section 16), which apply the privacy rules.
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

-- Swap partners read each other's profile through binder_cards() (section 16), which
-- also honors Private Mode and blocks. The old direct-read policy is removed.
drop policy if exists "Swap partners can read each other" on public.profiles;

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

-- Section 21 drops both read policies below: everyone reads only their own rows.
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
-- The app uploads only JPEGs it has shrunk on the device, well under 5 MB.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 5242880, '{image/jpeg}')
on conflict (id) do update set public = true, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

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
-- other's binder, and returns the owner's profile. Swapping again is a no-op, except
-- that it makes a temporary or expired swap permanent again.
-- SECURITY DEFINER because the caller can read neither the token nor the owner's
-- profile before the swap exists.
-- Refuses while either person is in Private Mode (UC001 = the caller, UC002 = the
-- owner), and answers a block either way exactly like an unknown card (P0002) so the
-- caller can't tell they were blocked. private_mode, swaps.expires_at and
-- blocked_between() come from section 16; plpgsql only looks them up when the function
-- runs, so defining it here is fine.
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

  if exists (select 1 from public.profiles pr where pr.id = me and pr.private_mode) then
    raise exception 'Turn off Private Mode to swap cards' using errcode = 'UC001';
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

  -- The block check comes before the owner's Private Mode check, so a blocked caller
  -- learns nothing about the owner.
  if not found or public.blocked_between(me, owner_id) then
    raise exception 'No card with that code' using errcode = 'P0002';
  end if;

  if p.private_mode then
    raise exception 'Not accepting swaps right now' using errcode = 'UC002';
  end if;

  insert into public.swaps (user_a, user_b)
  values (least(me, owner_id), greatest(me, owner_id))
  on conflict (user_a, user_b) do update set expires_at = null;

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
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('event-covers', 'event-covers', true, 5242880, '{image/jpeg}')
on conflict (id) do update set public = true, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

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
-- 16. Privacy: Private Mode, per-field privacy, temporary swaps, blocks, reports.
--     Must come after sections 6, 12 and 14: it replaces policies they create.
--
--     Who sees what when viewer V looks at person T:
--       V is T                        everything (the owner policy in section 5)
--       T is in Private Mode          nothing
--       a block, either way           nothing
--       an active swap                everything
--       a shared event, T is public   the name always; photo, bio, preferences,
--                                     boundaries and cue color only where T's
--                                     show_* flag is on
--       otherwise                     nothing
--     Other people's profiles are read ONLY through binder_cards() and
--     event_attendees(), which apply these rules, so the rules hold even for a
--     client that skips the app.
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column if not exists private_mode     boolean not null default false,
  add column if not exists show_photo       boolean not null default true,
  add column if not exists show_bio         boolean not null default true,
  add column if not exists show_preferences boolean not null default true,
  add column if not exists show_boundaries  boolean not null default true,
  add column if not exists show_status      boolean not null default true;

-- Null = permanent. A temporary swap stops counting at expires_at.
-- shortcut: expired rows are filtered, never deleted; add a cleanup job if the table grows large.
alter table public.swaps
  add column if not exists expires_at timestamptz;

drop policy if exists "Members can read their swaps" on public.swaps;
create policy "Members can read their swaps"
  on public.swaps for select
  using (auth.uid() in (user_a, user_b) and (expires_at is null or expires_at > now()));

-- A whole profiles row would leak hidden fields, so nobody but the owner reads one directly.
drop policy if exists "Public profiles are readable"      on public.profiles;
drop policy if exists "Swap partners can read each other" on public.profiles;

-- blocks: one row per person someone has blocked. block_user() inserts (it also ends
-- the swap); the blocker unblocks by deleting their own row.
create table if not exists public.blocks (
  blocker     uuid        not null default auth.uid() references auth.users (id) on delete cascade,
  blocked     uuid        not null references auth.users (id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (blocker, blocked),
  constraint blocks_not_self check (blocker <> blocked)
);

create index if not exists blocks_blocked_idx on public.blocks (blocked);

comment on table public.blocks is 'People a user has blocked. A block hides both people from each other.';

alter table public.blocks enable row level security;

drop policy if exists "Blocker can read own blocks"   on public.blocks;
drop policy if exists "Blocker can delete own blocks" on public.blocks;

create policy "Blocker can read own blocks"
  on public.blocks for select
  using (auth.uid() = blocker);

create policy "Blocker can delete own blocks"
  on public.blocks for delete
  using (auth.uid() = blocker);

-- reports: the team reads these in the Supabase dashboard. Users can only add them,
-- never read, change or remove one (not even their own).
create table if not exists public.reports (
  id          uuid        primary key default gen_random_uuid(),
  reporter    uuid        default auth.uid() references auth.users (id) on delete set null,
  reported    uuid        not null references auth.users (id) on delete cascade,
  reason      text        not null check (reason in ('harassment', 'unsafe', 'spam', 'fake', 'other')),
  note        text        not null default '' check (char_length(note) <= 1000),
  event_id    uuid        references public.events (id) on delete set null,
  created_at  timestamptz not null default now()
);

comment on table public.reports is 'Reports users file about other users; read by the team in the dashboard.';

alter table public.reports enable row level security;

drop policy if exists "Users can file reports" on public.reports;
create policy "Users can file reports"
  on public.reports for insert
  to authenticated
  with check (reporter = auth.uid() and reported <> auth.uid());

-- Helpers for the functions below. Nobody may call them directly: they would tell
-- anyone who blocked or swapped with whom.
create or replace function public.blocked_between(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.blocks bl
    where (bl.blocker = a and bl.blocked = b) or (bl.blocker = b and bl.blocked = a)
  );
$$;

create or replace function public.active_swap(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.swaps s
    where s.user_a = least(a, b) and s.user_b = greatest(a, b)
      and (s.expires_at is null or s.expires_at > now())
  );
$$;

revoke execute on function public.blocked_between(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.active_swap(uuid, uuid)     from public, anon, authenticated;

-- The caller's binder: everyone they have an active swap with, minus people in
-- Private Mode and blocks either way. Partners see every field.
create or replace function public.binder_cards()
returns table (
  id uuid, display_name text, bio text, preferences text[], boundaries text[],
  status public.cue_status, avatar_url text, card_image_url text, expires_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  -- Every column is qualified: the names above are also variables in here.
  return query
  select p.id, p.display_name, p.bio, p.preferences, p.boundaries,
         p.status, p.avatar_url, p.card_image_url, s.expires_at
  from public.swaps s
  join public.profiles p
    on p.id = case when s.user_a = auth.uid() then s.user_b else s.user_a end
  where auth.uid() in (s.user_a, s.user_b)
    and (s.expires_at is null or s.expires_at > now())
    and not p.private_mode
    and not public.blocked_between(auth.uid(), p.id);
end;
$$;

revoke execute on function public.binder_cards() from public, anon;
grant  execute on function public.binder_cards() to authenticated;

-- Who has joined an event, in the order they joined, as the caller may see them.
-- Only the host or a member may ask. On top of the rules at the top of this section,
-- someone who isn't the host never sees an attendee whose cue is red or purple, so
-- those people stay private to the host. The caller always sees themselves in full.
-- status is null when the attendee hides their cue color.
-- directory: section 19 gives this function another return type; drop it so re-runs work.
drop function if exists public.event_attendees(uuid);
create or replace function public.event_attendees(p_event_id uuid)
returns table (
  user_id uuid, display_name text, avatar_url text, bio text,
  preferences text[], boundaries text[], status public.cue_status
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  me      uuid := auth.uid();
  ev_host uuid;
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  select e.host_id into ev_host from public.events e where e.id = p_event_id;

  if ev_host is distinct from me and not public.is_event_member(p_event_id) then
    raise exception 'Not part of this event' using errcode = '42501';
  end if;

  -- Every column is qualified: the names above are also variables in here.
  return query
  select m.user_id,
         p.display_name,
         case when v.full_view or p.show_photo       then p.avatar_url  else ''           end,
         case when v.full_view or p.show_bio         then p.bio         else ''           end,
         case when v.full_view or p.show_preferences then p.preferences else '{}'::text[] end,
         case when v.full_view or p.show_boundaries  then p.boundaries  else '{}'::text[] end,
         case when v.full_view or p.show_status      then p.status                        end
  from public.event_members m
  -- event profile: per-event name, bio and cue override the main profile here.
  join public.event_effective_profiles(p_event_id) p on p.id = m.user_id
  cross join lateral (
    select m.user_id = me or public.active_swap(me, m.user_id) as full_view
  ) v
  where m.event_id = p_event_id
    and (
      m.user_id = me
      or (
        not p.private_mode
        and not public.blocked_between(me, m.user_id)
        and (v.full_view or p.is_public)
        and (ev_host = me or p.status in ('GREEN', 'YELLOW'))
      )
    )
  order by m.joined_at;
end;
$$;

revoke execute on function public.event_attendees(uuid) from public, anon;
grant  execute on function public.event_attendees(uuid) to authenticated;

-- Makes the caller's swap with other_id temporary and returns when it ends: the
-- soonest end still ahead of an active event they are both in (as members, or one
-- hosts and the other joined), else 24 hours from now. An event's end is
-- public.event_end() (section 21), as the app assumes. If the swap already ends sooner
-- it keeps that time, so either person choosing temporary wins.
create or replace function public.make_swap_temporary(other_id uuid)
returns timestamptz
language plpgsql
security definer
set search_path = public
as $$
declare
  me   uuid := auth.uid();
  ends timestamptz;
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  if not public.active_swap(me, other_id) then
    raise exception 'No swap with that person' using errcode = 'P0002';
  end if;

  select min(public.event_end(e)) into ends
  from public.events e
  where e.active
    and public.event_end(e) > now()
    and (e.host_id = me or exists (
          select 1 from public.event_members m where m.event_id = e.id and m.user_id = me))
    and (e.host_id = other_id or exists (
          select 1 from public.event_members m where m.event_id = e.id and m.user_id = other_id));

  ends := coalesce(ends, now() + interval '24 hours');

  update public.swaps s
     set expires_at = least(coalesce(s.expires_at, ends), ends)
   where s.user_a = least(me, other_id) and s.user_b = greatest(me, other_id)
  returning s.expires_at into ends;

  return ends;
end;
$$;

revoke execute on function public.make_swap_temporary(uuid) from public, anon;
grant  execute on function public.make_swap_temporary(uuid) to authenticated;

-- Blocks other_id and ends any swap between the two. Blocking twice is a no-op.
-- SECURITY DEFINER because blocks has no insert policy and the caller may not be
-- able to see the swap row (e.g. it has expired).
create or replace function public.block_user(other_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  insert into public.blocks (blocker, blocked)
  values (me, other_id)
  on conflict (blocker, blocked) do nothing;

  delete from public.swaps s
  where s.user_a = least(me, other_id) and s.user_b = greatest(me, other_id);
end;
$$;

revoke execute on function public.block_user(uuid) from public, anon;
grant  execute on function public.block_user(uuid) to authenticated;

-- Ends the caller's swap with other_id and gives the caller a new card token. The
-- token never changes otherwise, so without this the removed person could swap
-- again from the old link or a photo of the QR code. Deleting the caller's
-- card_tokens row is enough: my_card_token() makes a fresh one when next asked.
-- Rotates even when there is no swap row, since the swap may already have expired.
-- SECURITY DEFINER because card_tokens has no delete policy.
create or replace function public.remove_swap(other_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  delete from public.swaps s
  where s.user_a = least(me, other_id) and s.user_b = greatest(me, other_id);

  delete from public.card_tokens ct where ct.user_id = me;
end;
$$;

revoke execute on function public.remove_swap(uuid) from public, anon;
grant  execute on function public.remove_swap(uuid) to authenticated;

-- The people the caller has blocked, newest first, with their names so the app can
-- list them for unblocking (the caller can't read their profiles any more).
create or replace function public.my_blocked_users()
returns table (user_id uuid, display_name text, blocked_at timestamptz)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  return query
  select bl.blocked, coalesce(p.display_name, ''), bl.created_at
  from public.blocks bl
  left join public.profiles p on p.id = bl.blocked
  where bl.blocker = auth.uid()
  order by bl.created_at desc;
end;
$$;

revoke execute on function public.my_blocked_users() from public, anon;
grant  execute on function public.my_blocked_users() to authenticated;
-- 17. Event announcements (same definitions as 202611170001_event_announcements.sql).
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
  if not ev.active or public.event_end(ev) <= clock_timestamp() then
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

-- 18. Quick reactions (same definitions as 202611170002_quick_reactions.sql).
begin;

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

-- Shared by the inbox, recipient list and send path. Both sides of a block are hidden.
create or replace function public.event_wave_visible(p public.profiles, viewer uuid)
returns boolean language sql stable set search_path = '' as $$
  select p.is_public and not p.private_mode and p.show_status and p.status in ('GREEN', 'YELLOW')
    and (p.id = viewer or not public.blocked_between(p.id, viewer));
$$;
revoke all on function public.event_wave_visible(public.profiles, uuid) from public, anon, authenticated;

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
  -- event profile: eligibility uses the caller's cue for this event.
  select opted and exists (select 1 from public.events e join public.event_effective_profiles(p_event_id) p on p.id = me
    where e.id = p_event_id and e.active
      and (public.event_end(e) is null
        or public.event_end(e) > clock_timestamp())
      and public.event_wave_visible(p, me)) into eligible;
  if eligible then
    select coalesce(jsonb_agg(jsonb_build_object(
      'user_id', p.id, 'display_name', p.display_name,
      'sent', exists (select 1 from public.event_waves w where w.event_id = p_event_id
        and w.sender_id = me and w.recipient_id = p.id)) order by p.display_name, p.id), '[]'::jsonb)
    -- event profile: targets use per-event names and cues.
    into targets from public.event_effective_profiles(p_event_id) p join public.event_wave_preferences pref on pref.user_id = p.id
    where pref.event_id = p_event_id and pref.receive_waves and p.id <> me
      and public.event_wave_visible(p, me)
      and public.event_wave_participant(p_event_id, p.id);
    select coalesce(jsonb_agg(jsonb_build_object(
      'sender_id', p.id, 'sender_name', p.display_name, 'created_at', w.created_at)
      order by w.created_at desc, w.sender_id), '[]'::jsonb)
    -- event profile: inbox uses per-event names and cues.
    into inbox from public.event_waves w join public.event_effective_profiles(p_event_id) p on p.id = w.sender_id
      join public.event_wave_preferences pref on pref.event_id = w.event_id and pref.user_id = p.id
    where w.event_id = p_event_id and w.recipient_id = me and pref.receive_waves
      and public.event_wave_visible(p, me)
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
  if not ev.active or public.event_end(ev) <= clock_timestamp()
    or not public.event_wave_participant(p_event_id, me)
    or not public.event_wave_participant(p_event_id, p_recipient_id)
    -- event profile: both cues are checked as set for this event.
    or (select count(*) from public.event_effective_profiles(p_event_id) p where p.id in (me, p_recipient_id)
      and public.event_wave_visible(p, me)) <> 2
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

commit;

-- 19. Event directory (same definitions as 202611170003_event_directory.sql).
begin;

-- Existing and new memberships start cued in, so nobody's visibility changes on deploy.
alter table public.event_members add column if not exists cued_in boolean not null default true;

-- directory: a co-member reading the table directly must not find a cued-out member either.
-- (Section 21 drops this policy again: members now read only their own rows.)
drop policy if exists "Members can read co-members" on public.event_members;
create policy "Members can read co-members"
  on public.event_members for select
  using (public.is_event_member(event_id) and (cued_in or user_id = auth.uid()));

-- Only the caller's own flag, and only this column. A host without a membership row has none.
create or replace function public.set_event_cued_in(p_event_id uuid, p_cued_in boolean)
returns boolean language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  if p_cued_in is null then
    raise exception 'Choose Cue In or Cue Out' using errcode = '22023';
  end if;
  -- Same event lock as send_event_wave, so a wave cannot slip past a concurrent Cue Out.
  perform 1 from public.events e where e.id = p_event_id for update;
  update public.event_members m set cued_in = p_cued_in
    where m.event_id = p_event_id and m.user_id = me;
  if not found then
    raise exception 'Event member required' using errcode = '42501';
  end if;
  return p_cued_in;
end;
$$;
revoke all on function public.set_event_cued_in(uuid, boolean) from public, anon;
grant execute on function public.set_event_cued_in(uuid, boolean) to authenticated;

-- directory: the return type gains cued_in, which create or replace cannot change.
drop function if exists public.event_attendees(uuid);
create function public.event_attendees(p_event_id uuid)
returns table (
  user_id uuid, display_name text, avatar_url text, bio text,
  preferences text[], boundaries text[], status public.cue_status,
  cued_in boolean -- directory: false only ever reaches the host and the member themselves
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  me      uuid := auth.uid();
  ev_host uuid;
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;

  select e.host_id into ev_host from public.events e where e.id = p_event_id;

  if ev_host is distinct from me and not public.is_event_member(p_event_id) then
    raise exception 'Not part of this event' using errcode = '42501';
  end if;

  -- Every column is qualified: the names above are also variables in here.
  return query
  select m.user_id,
         p.display_name,
         case when v.full_view or p.show_photo       then p.avatar_url  else ''           end,
         case when v.full_view or p.show_bio         then p.bio         else ''           end,
         case when v.full_view or p.show_preferences then p.preferences else '{}'::text[] end,
         case when v.full_view or p.show_boundaries  then p.boundaries  else '{}'::text[] end,
         case when v.full_view or p.show_status      then p.status                        end,
         m.cued_in -- directory:
  from public.event_members m
  -- event profile: per-event name, bio and cue override the main profile here.
  join public.event_effective_profiles(p_event_id) p on p.id = m.user_id
  cross join lateral (
    select m.user_id = me or public.active_swap(me, m.user_id) as full_view
  ) v
  where m.event_id = p_event_id
    and (
      m.user_id = me
      or (
        not p.private_mode
        and not public.blocked_between(me, m.user_id)
        and (v.full_view or p.is_public)
        and (ev_host = me or p.status in ('GREEN', 'YELLOW'))
        and (ev_host = me or m.cued_in) -- directory: the host still sees cued-out members
      )
    )
  order by m.joined_at;
end;
$$;

revoke execute on function public.event_attendees(uuid) from public, anon;
grant  execute on function public.event_attendees(uuid) to authenticated;

-- Waves are discoverability: a cued-out member is no wave participant, so they are no target,
-- cannot send or receive, their sent waves leave inboxes, and event_wave_state refuses them.
create or replace function public.event_wave_participant(p_event_id uuid, p_user_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.events e where e.id = p_event_id and
    (e.host_id = p_user_id or exists (select 1 from public.event_members m
      where m.event_id = e.id and m.user_id = p_user_id
        and m.cued_in))); -- directory:
$$;
revoke all on function public.event_wave_participant(uuid, uuid) from public, anon, authenticated;

commit;

-- 20. Event profiles (same definitions as 202611170004_event_profiles.sql). The migration also
--     redefines event_attendees (sections 16 and 19), event_wave_state and send_event_wave (section 18);
--     those changes are made in place above, each marked "event profile:".
begin;

-- Optional per-event name, bio and cue. No row, or a null field, means the main profile's value.
-- No table access: the owner uses the two RPCs below; others see only effective values through
-- event_attendees() and the wave RPCs, which still apply every privacy rule on top.
create table if not exists public.event_profiles (
  event_id uuid not null references public.events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  display_name text check (btrim(display_name) <> ''),
  -- Same 120-character limit the app puts on the main bio.
  bio text check (btrim(bio) <> '' and char_length(bio) <= 120),
  status public.cue_status,
  primary key (event_id, user_id),
  -- An all-empty override is stored as no row.
  check (coalesce(display_name, bio, status::text) is not null)
);
alter table public.event_profiles enable row level security;
revoke all on public.event_profiles from public, anon, authenticated;

-- The event's participants (host and members) as profiles rows with their overrides applied,
-- so callers keep their existing privacy expressions unchanged.
create or replace function public.event_effective_profiles(p_event_id uuid)
returns setof public.profiles language plpgsql stable set search_path = '' as $$
declare
  p public.profiles;
  o public.event_profiles;
begin
  for p in select pr.* from public.profiles pr where pr.id in (
      select e.host_id from public.events e where e.id = p_event_id
      union select m.user_id from public.event_members m where m.event_id = p_event_id) loop
    select * into o from public.event_profiles ep where ep.event_id = p_event_id and ep.user_id = p.id;
    p.display_name := coalesce(o.display_name, p.display_name);
    p.bio := coalesce(o.bio, p.bio);
    p.status := coalesce(o.status, p.status);
    return next p;
  end loop;
end;
$$;
revoke all on function public.event_effective_profiles(uuid) from public, anon, authenticated;

-- The caller's own override fields for one event; null fields use the main profile.
create or replace function public.my_event_profile(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  o public.event_profiles;
begin
  -- Not event_wave_participant: a cued-out member still owns their event profile.
  if me is null or not exists (select 1 from public.events e where e.id = p_event_id and (e.host_id = me
      or exists (select 1 from public.event_members m where m.event_id = e.id and m.user_id = me))) then
    raise exception 'Event participant required' using errcode = '42501';
  end if;
  select * into o from public.event_profiles ep where ep.event_id = p_event_id and ep.user_id = me;
  return jsonb_build_object('display_name', o.display_name, 'bio', o.bio, 'status', o.status);
end;
$$;

-- Replaces the caller's override for one event. A blank or null field clears it; clearing all
-- three deletes the row.
create or replace function public.set_event_profile(
  p_event_id uuid, p_display_name text, p_bio text, p_status public.cue_status)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  clean_name text := nullif(btrim(p_display_name), '');
  clean_bio text := nullif(btrim(p_bio), '');
begin
  -- The membership lock stops a concurrent leave; the profile lock serializes a cue change with
  -- send_event_wave, which locks both profiles before checking cues.
  perform 1 from public.event_members m where m.event_id = p_event_id and m.user_id = me for key share;
  perform 1 from public.profiles p where p.id = me for update;
  -- Not event_wave_participant: a cued-out member still owns their event profile.
  if me is null or not exists (select 1 from public.events e where e.id = p_event_id and (e.host_id = me
      or exists (select 1 from public.event_members m where m.event_id = e.id and m.user_id = me))) then
    raise exception 'Event participant required' using errcode = '42501';
  end if;
  if char_length(clean_bio) > 120 then
    raise exception 'Bio must be 120 characters or fewer' using errcode = '22023';
  end if;
  if coalesce(clean_name, clean_bio, p_status::text) is null then
    delete from public.event_profiles ep where ep.event_id = p_event_id and ep.user_id = me;
  else
    insert into public.event_profiles(event_id, user_id, display_name, bio, status)
      values (p_event_id, me, clean_name, clean_bio, p_status)
      on conflict (event_id, user_id) do update
        set display_name = excluded.display_name, bio = excluded.bio, status = excluded.status;
  end if;
  return public.my_event_profile(p_event_id);
end;
$$;

-- Leaving an event drops the override, so rejoining starts from the main profile.
create or replace function public.delete_event_profile_on_leave()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  delete from public.event_profiles ep where ep.event_id = old.event_id and ep.user_id = old.user_id;
  return old;
end;
$$;
drop trigger if exists event_members_delete_event_profile on public.event_members;
create trigger event_members_delete_event_profile after delete on public.event_members
  for each row execute function public.delete_event_profile_on_leave();
revoke all on function public.delete_event_profile_on_leave() from public, anon, authenticated;
revoke all on function public.my_event_profile(uuid),
  public.set_event_profile(uuid, text, text, public.cue_status) from public, anon;
grant execute on function public.my_event_profile(uuid),
  public.set_event_profile(uuid, text, text, public.cue_status) to authenticated;

commit;

-- 21. Hardening (same definitions as 202611170005_hardening.sql). The migration also redefines
--     make_swap_temporary (section 16), post_event_announcement (section 17), event_wave_state and
--     send_event_wave (section 18) to call public.event_end(); those are changed in place above.
begin;

-- Members read only their own membership rows. Everyone else's go through event_attendees(),
-- which applies blocks, Private Mode, visibility, the cue rule and Cue Out; a direct read skipped
-- all of those.
drop policy if exists "Members can read co-members"    on public.event_members;
drop policy if exists "Host can read event members"    on public.event_members;
drop policy if exists "Users can read own memberships" on public.event_members;
create policy "Users can read own memberships"
  on public.event_members for select
  using (user_id = auth.uid());

-- Profile size limits. Check constraints cannot hold a subquery, hence the helper. Executable by
-- every role on purpose: a check constraint runs it as whoever writes the row.
create or replace function public.text_items_fit(items text[], max_items integer, max_chars integer)
returns boolean language sql immutable set search_path = '' as $$
  select cardinality(items) <= max_items
    and not exists (select 1 from unnest(items) i where char_length(i) > max_chars);
$$;

-- not valid: rows already over a limit stay, but every new write is checked.
alter table public.profiles drop constraint if exists profiles_display_name_length;
alter table public.profiles drop constraint if exists profiles_bio_length;
alter table public.profiles drop constraint if exists profiles_preferences_size;
alter table public.profiles drop constraint if exists profiles_boundaries_size;
-- 80: the watch shows at most 80 characters of a name. 120: the app's BIO_MAX_LENGTH.
alter table public.profiles add constraint profiles_display_name_length
  check (char_length(display_name) <= 80) not valid;
alter table public.profiles add constraint profiles_bio_length
  check (char_length(bio) <= 120) not valid;
alter table public.profiles add constraint profiles_preferences_size
  check (public.text_items_fit(preferences, 20, 60)) not valid;
alter table public.profiles add constraint profiles_boundaries_size
  check (public.text_items_fit(boundaries, 20, 60)) not valid;
alter table public.event_profiles drop constraint if exists event_profiles_display_name_length;
alter table public.event_profiles add constraint event_profiles_display_name_length
  check (char_length(display_name) <= 80) not valid;

-- The app uploads only JPEGs it has shrunk on the device, well under 5 MB.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 5242880, '{image/jpeg}'),
       ('event-covers', 'event-covers', true, 5242880, '{image/jpeg}')
on conflict (id) do update set public = true, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Deletes the caller's account. Every app table cascades from auth.users, except that reports
-- the caller filed keep their row with a null reporter. The app deletes the caller's Storage
-- files first: Storage objects are not covered by these cascades.
create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  delete from auth.users where id = auth.uid();
end;
$$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- When an event ends. One with no end time runs 3 hours, like DEFAULT_EVENT_LENGTH in the app;
-- one with no start either has no end. make_swap_temporary, post_event_announcement,
-- event_wave_state and send_event_wave use it; each is otherwise unchanged.
create or replace function public.event_end(e public.events)
returns timestamptz language sql stable set search_path = '' as $$
  select coalesce(e.ends_at, e.starts_at + interval '3 hours');
$$;
revoke all on function public.event_end(public.events) from public, anon, authenticated;

commit;

-- 22. Blocked attendee warning (same definitions as 202611170006_blocked_attendee_warning.sql).
begin;

-- Only the caller's own blocks, never who blocked them (that would reveal someone else's block). Only
-- people an ordinary member would see in event_attendees() anyway (not Private Mode, public profile,
-- green or yellow for this event, cued in), so nothing leaks that a second, unblocked account could not
-- already see. The host always counts: the event already shows them as host_name. Names come from the
-- main profile, as on the blocked list, so a per-event alias is never revealed.
-- Not SECURITY DEFINER and not executable by clients: only the two functions below call it.
create or replace function public.blocked_attendees_for(p_event_id uuid, p_me uuid)
returns table (user_id uuid, display_name text) language sql stable set search_path = '' as $$
  select bl.blocked, coalesce(pr.display_name, '')
  from public.blocks bl
  join public.events e on e.id = p_event_id
  left join public.profiles pr on pr.id = bl.blocked
  where bl.blocker = p_me
    and (bl.blocked = e.host_id or bl.blocked in (
      select m.user_id from public.event_members m
      join public.event_effective_profiles(p_event_id) ep on ep.id = m.user_id
      where m.event_id = p_event_id and m.cued_in
        and not ep.private_mode and ep.is_public and ep.status in ('GREEN', 'YELLOW')))
  order by 2, 1;
$$;
revoke all on function public.blocked_attendees_for(uuid, uuid) from public, anon, authenticated;

-- By event id, only for an event the caller can already read (public, hosted or joined, as the events
-- policies allow); any other id answers nothing, so this is no way to test whether an event exists.
create or replace function public.blocked_attendees(p_event_id uuid)
returns table (user_id uuid, display_name text) language plpgsql stable security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  if exists (select 1 from public.events e where e.id = p_event_id
      and (e.is_public or e.host_id = me or public.is_event_member(e.id))) then
    return query select * from public.blocked_attendees_for(p_event_id, me);
  end if;
end;
$$;

-- By join code, found the way join_event_by_code() finds it (active, trimmed, any case), without
-- joining. Anyone holding the code could join and look, so this reveals nothing more.
create or replace function public.blocked_attendees_by_code(code text)
returns table (user_id uuid, display_name text) language plpgsql stable security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  ev_id uuid;
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  select e.id into ev_id from public.events e
  where upper(e.join_code) = upper(trim(code)) and e.active
  limit 1;
  if ev_id is not null then
    return query select * from public.blocked_attendees_for(ev_id, me);
  end if;
end;
$$;

revoke all on function public.blocked_attendees(uuid), public.blocked_attendees_by_code(text) from public, anon;
grant execute on function public.blocked_attendees(uuid), public.blocked_attendees_by_code(text) to authenticated;

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
--
-- 5. Privacy (section 16). First check it exists:
--
--    select proname from pg_proc
--     where proname in ('binder_cards', 'event_attendees', 'make_swap_temporary', 'block_user',
--                       'my_blocked_users', 'blocked_between', 'active_swap', 'remove_swap');  -- 8 rows
--
--    The checks below need three real users from auth.users: a host, and users 1
--    and 2 who have both joined one of the host's events (join_event_by_code, as in
--    note 3, without the rollback) and swapped cards (note 4, without the rollback).
--    Each check undoes itself with rollback. The updates run as admin, before
--    "set local role"; to switch users inside a check, run the "set local
--    request.jwt.claims" line again with the other id.
--
--    a. Anon can no longer read profiles:
--
--    begin;
--      set local role anon;
--      select * from public.profiles;                     -- 0 rows
--    rollback;
--
--    b. Private Mode hides user 2 from partners and attendees and stops swaps both ways:
--
--    begin;
--      update public.profiles set private_mode = true where id = '<user-2-id>';
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select * from public.binder_cards();               -- no row for user 2
--      select * from public.event_attendees('<event-id>');  -- no row for user 2
--      select * from public.swap_by_card_token('<user 2 token>');  -- error UC002 (owner is private)
--      set local request.jwt.claims = '{"sub": "<user-2-id>", "role": "authenticated"}';
--      select * from public.swap_by_card_token('<user 1 token>');  -- error UC001 (caller is private)
--    rollback;
--
--    c. A block hides both ways and refuses swaps like an unknown card:
--
--    begin;
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select public.block_user('<user-2-id>');
--      select * from public.my_blocked_users();           -- one row: user 2 with their name
--      select * from public.swaps;                        -- no rows: the swap ended
--      select * from public.event_attendees('<event-id>');  -- no row for user 2
--      select * from public.swap_by_card_token('<user 2 token>');  -- error P0002 No card with that code
--      set local request.jwt.claims = '{"sub": "<user-2-id>", "role": "authenticated"}';
--      select * from public.event_attendees('<event-id>');  -- no row for user 1
--      select * from public.swap_by_card_token('<user 1 token>');  -- error P0002 No card with that code
--      select * from public.blocks;                       -- no rows: user 2 can't see user 1's block
--    rollback;
--
--    d. Temporary and expired swaps (now() is fixed for the whole transaction):
--
--    begin;
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select public.make_swap_temporary('<user-2-id>');  -- the event's end if still ahead, else now() + 24 hours
--      select * from public.binder_cards();               -- user 2, expires_at = that time
--    rollback;
--
--    begin;
--      update public.swaps set expires_at = now() - interval '1 minute'
--       where '<user-1-id>' in (user_a::text, user_b::text) and '<user-2-id>' in (user_a::text, user_b::text);
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select * from public.swaps;                        -- no rows: the swap expired
--      select * from public.binder_cards();               -- no row for user 2
--      select public.make_swap_temporary('<user-2-id>');  -- error P0002: no active swap
--      select * from public.swap_by_card_token('<user 2 token>');  -- user 2's profile
--      select expires_at from public.swaps;               -- one row, expires_at null (permanent again)
--    rollback;
--
--    e. Field toggles blank the fields for a co-attendee who hasn't swapped:
--
--    begin;
--      delete from public.swaps
--       where '<user-1-id>' in (user_a::text, user_b::text) and '<user-2-id>' in (user_a::text, user_b::text);
--      update public.profiles
--         set is_public = true, status = 'GREEN', show_photo = false, show_bio = false,
--             show_preferences = false, show_boundaries = false, show_status = false
--       where id = '<user-2-id>';
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select * from public.event_attendees('<event-id>');
--        -- user 2's row: display_name set; avatar_url and bio '', preferences and
--        -- boundaries {}, status null
--      set local request.jwt.claims = '{"sub": "<user-2-id>", "role": "authenticated"}';
--      select * from public.event_attendees('<event-id>');  -- user 2 sees their own row in full
--    rollback;
--
--    f. Someone who isn't the host never sees red or purple attendees:
--
--    begin;
--      update public.profiles set status = 'RED', is_public = true where id = '<user-2-id>';
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select * from public.event_attendees('<event-id>');  -- no row for user 2 (even though swapped)
--      set local request.jwt.claims = '{"sub": "<host-user-id>", "role": "authenticated"}';
--      select * from public.event_attendees('<event-id>');  -- user 2's row, status RED
--      set local request.jwt.claims = '{"sub": "<some-other-user-id>", "role": "authenticated"}';
--      select * from public.event_attendees('<event-id>');  -- error 42501: Not part of this event
--    rollback;
--
--    g. Removing someone gives the remover a new card token:
--
--    begin;
--      set local role authenticated;
--      set local request.jwt.claims = '{"sub": "<user-1-id>", "role": "authenticated"}';
--      select public.my_card_token();                     -- user 1's old token
--      select public.remove_swap('<user-2-id>');
--      select * from public.swaps;                        -- no rows: the swap ended
--      select public.my_card_token();                     -- a different token
--      set local request.jwt.claims = '{"sub": "<user-2-id>", "role": "authenticated"}';
--      select * from public.swap_by_card_token('<user 1 old token>');  -- error P0002 No card with that code
--    rollback;
-- Read-only cursor for the connected Wear companion. Existing table RLS applies to every read.
begin;
create or replace function public.watch_announcement_cursor(
  p_generation uuid default null, p_after bigint default 0
) returns table(generation uuid, cursor_us bigint)
language plpgsql stable security invoker set search_path = '' as $$
declare
  me uuid := auth.uid();
  session_id text := nullif(auth.jwt()->>'session_id', '');
  session_generation uuid;
  latest timestamptz;
begin
  if me is null or session_id is null then
    raise exception 'An authenticated session is required' using errcode = '28000';
  end if;
  -- A per-session pseudonym. Neither an account ID nor a usable auth/session token leaves the phone.
  session_generation := md5('watch-alerts-v1/' || me::text || '/' || session_id)::uuid;
  if p_generation is distinct from session_generation or p_after is null or p_after <= 0 then
    return query select session_generation,
      (extract(epoch from statement_timestamp()) * 1000000)::bigint;
    return;
  end if;

  select max(a.created_at) into latest
    from public.event_members m
    join public.events e on e.id = m.event_id
    join public.event_announcements a on a.event_id = m.event_id
    where m.user_id = me and e.host_id <> me and a.created_at >= m.joined_at
      and a.created_at > timestamptz 'epoch'
        + (p_after / 1000000) * interval '1 second'
        + (p_after % 1000000) * interval '1 microsecond';
  return query select session_generation, coalesce((extract(epoch from latest) * 1000000)::bigint, p_after);
end;
$$;
revoke all on function public.watch_announcement_cursor(uuid,bigint) from public, anon, authenticated;
grant execute on function public.watch_announcement_cursor(uuid,bigint) to authenticated;
commit;
