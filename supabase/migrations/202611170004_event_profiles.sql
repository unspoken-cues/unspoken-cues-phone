-- Per-event profile overrides. Apply after the privacy section of schema.sql and
-- 202611170002_quick_reactions.sql. Transactional and repeatable.
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
  if me is null or not public.event_wave_participant(p_event_id, me) then
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
  if me is null or not public.event_wave_participant(p_event_id, me) then
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

-- Existing functions that now read effective per-event values. Each change is marked
-- "event profile:"; everything else matches schema.sql sections 16 and 18 (privileges are kept).
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
      and (coalesce(e.ends_at, e.starts_at + interval '3 hours') is null
        or coalesce(e.ends_at, e.starts_at + interval '3 hours') > clock_timestamp())
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

commit;
