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

commit;
