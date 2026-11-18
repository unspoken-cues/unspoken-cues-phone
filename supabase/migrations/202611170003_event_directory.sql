-- Cue In / Cue Out: a member can stay in an event but leave its participant directory.
-- Apply after 202611170002_quick_reactions.sql. Transactional and repeatable.
begin;

-- Existing and new memberships start cued in, so nobody's visibility changes on deploy.
alter table public.event_members add column if not exists cued_in boolean not null default true;

-- directory: a co-member reading the table directly must not find a cued-out member either.
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
  join public.profiles p on p.id = m.user_id
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
