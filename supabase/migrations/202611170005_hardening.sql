-- Hardening from the 2026-11-17 audit. Apply after 202611170004_event_profiles.sql.
-- Transactional and repeatable.
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

commit;
