-- Isolated PostgreSQL/PGlite test database only. Minimal Supabase contract, no live credentials.
create role anon nologin;
create role authenticated nologin;
create schema auth;
create function auth.uid() returns uuid language sql stable as $$
  select (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')::uuid;
$$;
grant usage on schema auth, public to anon, authenticated;
create table auth.users(id uuid primary key);
create type public.cue_status as enum ('GREEN', 'YELLOW', 'RED', 'PURPLE');
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '', is_public boolean not null default true,
  status public.cue_status not null default 'GREEN'
);
create table public.events (
  id uuid primary key, host_id uuid not null references auth.users(id) on delete cascade,
  name text not null, join_code text not null unique,
  active boolean not null default true, starts_at timestamptz, ends_at timestamptz
);
create table public.event_members (
  event_id uuid references public.events(id) on delete cascade,
  user_id uuid references auth.users(id) on delete cascade, primary key(event_id, user_id)
);
-- Deliberately mimic broad Supabase defaults: migration must revoke these privileges.
alter default privileges in schema public grant all on tables to anon, authenticated;
