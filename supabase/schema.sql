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
