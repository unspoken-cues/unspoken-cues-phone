-- Blocked attendee warning: before joining or RSVPing, the app asks which people the caller blocked
-- are going, so it can warn them. Apply after 202611170004_event_profiles.sql. Transactional and repeatable.
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
