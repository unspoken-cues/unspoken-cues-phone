-- Run after the complete schema, or the waves baseline plus the event directory migration, in a
-- disposable DB. All tests roll back. Fail fast (psql: -v ON_ERROR_STOP=1). No pgTAP required.
begin;
create function pg_temp.assert_true(ok boolean, message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', message; end if; end; $$;
create function pg_temp.assert_denied(statement text) returns void language plpgsql as $$
begin
  begin execute statement;
  exception when insufficient_privilege then return; end;
  raise exception 'FAILED: statement unexpectedly allowed: %', statement;
end; $$;
-- Whom the caller sees in the attendee list.
create function pg_temp.listed(who uuid) returns boolean language sql as $$
  select exists (select 1 from public.event_attendees('10000000-0000-0000-0000-000000000001') a
    where a.user_id = who)
$$;
insert into auth.users(id) values
 ('00000000-0000-0000-0000-000000000001'), -- host, not a member
 ('00000000-0000-0000-0000-000000000002'), -- member who cues out
 ('00000000-0000-0000-0000-000000000003'), -- other member
 ('00000000-0000-0000-0000-000000000004'); -- outsider
insert into public.profiles(id, display_name) select id, 'Person ' || right(id::text,1) from auth.users
 on conflict(id) do update set display_name = excluded.display_name;
insert into public.events(id, host_id, name, join_code) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Directory test','DIRT01');
insert into public.event_members(event_id, user_id) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002'),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003');
insert into public.event_wave_preferences(event_id, user_id, receive_waves)
 select '10000000-0000-0000-0000-000000000001', id, true from auth.users;

set local role anon;
select pg_temp.assert_denied($q$select public.set_event_cued_in('10000000-0000-0000-0000-000000000001', false)$q$);

set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
-- Default on join is cued in, and everyone sees everyone.
select pg_temp.assert_true((select a.cued_in from public.event_attendees('10000000-0000-0000-0000-000000000001') a
  where a.user_id = auth.uid()), 'joined members start cued in');
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000003'), 'member sees co-member');
select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000002'), 'cued-in member is listed');
select pg_temp.assert_true(jsonb_array_length(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox') = 1,
  'wave from a cued-in sender arrives');

-- Nobody but the member changes the flag, and never through someone else's row.
select pg_temp.assert_true(to_regprocedure('public.set_event_cued_in(uuid,uuid,boolean)') is null, 'no target override');
update public.event_members set cued_in = false where user_id = '00000000-0000-0000-0000-000000000002';
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_denied($q$select public.set_event_cued_in('10000000-0000-0000-0000-000000000001', false)$q$);
update public.event_members set cued_in = false where user_id = '00000000-0000-0000-0000-000000000002';
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000004"}';
select pg_temp.assert_denied($q$select public.set_event_cued_in('10000000-0000-0000-0000-000000000001', false)$q$);
reset role;
select pg_temp.assert_true((select bool_and(cued_in) from public.event_members), 'other people cannot cue a member out');

set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
do $$ begin
  perform public.set_event_cued_in('10000000-0000-0000-0000-000000000001', null);
  raise exception 'FAILED: a null choice was accepted';
exception when invalid_parameter_value then null; end $$;
select pg_temp.assert_true(not public.set_event_cued_in('10000000-0000-0000-0000-000000000001', false), 'cue out acknowledged');
reset role;
select pg_temp.assert_true((select not m.cued_in and m.rsvp = 'going' from public.event_members m
  where m.user_id = '00000000-0000-0000-0000-000000000002'), 'cue out keeps membership and RSVP');
select pg_temp.assert_true((select m.cued_in from public.event_members m
  where m.user_id = '00000000-0000-0000-0000-000000000003'), 'cue out touches only the caller');

set local role authenticated;
-- The member still sees themselves, marked cued out, and keeps announcements.
select pg_temp.assert_true((select not a.cued_in from public.event_attendees('10000000-0000-0000-0000-000000000001') a
  where a.user_id = auth.uid()), 'member sees own cued-out row');
select pg_temp.assert_true(public.is_event_member('10000000-0000-0000-0000-000000000001'), 'still a member');
-- Waves: no state, no sending while cued out.
select pg_temp.assert_denied($q$select public.event_wave_state('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);

set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000002'), 'other attendee does not see cued-out member');
select pg_temp.assert_true(not exists (select 1 from public.event_members m
  where m.user_id = '00000000-0000-0000-0000-000000000002'), 'direct table read hides cued-out member');
select pg_temp.assert_true(exists (select 1 from public.event_members m
  where m.user_id = auth.uid()), 'direct table read still shows own row');
select pg_temp.assert_true(not exists (select 1 from jsonb_array_elements(
  public.event_wave_state('10000000-0000-0000-0000-000000000001')->'recipients') r
  where r->>'user_id' = '00000000-0000-0000-0000-000000000002'), 'cued-out member is no wave target');
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb,
  'inbox hides waves from a cued-out sender');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')$q$);

set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true((select not a.cued_in from public.event_attendees('10000000-0000-0000-0000-000000000001') a
  where a.user_id = '00000000-0000-0000-0000-000000000002'), 'host sees cued-out member, marked');
-- hardening: the host reads members only through event_attendees(), never the table.
select pg_temp.assert_true(not exists (select 1 from public.event_members m
  where m.user_id = '00000000-0000-0000-0000-000000000002'), 'host cannot read the membership row directly');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')$q$);

-- RSVP and rejoin keep the choice / start fresh.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
insert into public.event_members(event_id, user_id, rsvp)
  values ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','maybe')
  on conflict (event_id, user_id) do update set rsvp = excluded.rsvp;
reset role;
select pg_temp.assert_true((select not m.cued_in from public.event_members m
  where m.user_id = '00000000-0000-0000-0000-000000000002'), 'changing RSVP keeps cued out');
set local role authenticated;
select pg_temp.assert_true(public.set_event_cued_in('10000000-0000-0000-0000-000000000001', true), 'cue in acknowledged');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000002'), 'cued back in is listed again');
select pg_temp.assert_true(jsonb_array_length(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox') = 1,
  'earlier wave returns with its sender');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select public.set_event_cued_in('10000000-0000-0000-0000-000000000001', false);
delete from public.event_members where user_id = auth.uid();
select pg_temp.assert_denied($q$select public.set_event_cued_in('10000000-0000-0000-0000-000000000001', true)$q$);
select public.join_event_by_code('dirt01');
select pg_temp.assert_true((select a.cued_in from public.event_attendees('10000000-0000-0000-0000-000000000001') a
  where a.user_id = auth.uid()), 'rejoining starts cued in');
rollback;
