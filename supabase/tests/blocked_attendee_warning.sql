-- Run after the complete schema, or the waves baseline plus 202611170003, 202611170004 and 202611170006,
-- in a disposable DB. All tests roll back. Fail fast (psql: -v ON_ERROR_STOP=1). No pgTAP required.
begin;
create function pg_temp.assert_true(ok boolean, message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', message; end if; end; $$;
create function pg_temp.assert_denied(statement text) returns void language plpgsql as $$
begin
  begin execute statement;
  exception when insufficient_privilege then return; end;
  raise exception 'FAILED: statement unexpectedly allowed: %', statement;
end; $$;
-- The warning's names for an event id or a join code, in the order the server returns them.
create function pg_temp.by_id(event_id uuid) returns text language sql as $$
  select coalesce(string_agg(b.display_name, ','), '') from public.blocked_attendees(event_id) b
$$;
create function pg_temp.by_code(code text) returns text language sql as $$
  select coalesce(string_agg(b.display_name, ','), '') from public.blocked_attendees_by_code(code) b
$$;
insert into auth.users(id) values
 ('00000000-0000-0000-0000-000000000001'), -- host, blocked by the caller
 ('00000000-0000-0000-0000-000000000002'), -- caller, not a member
 ('00000000-0000-0000-0000-000000000003'), -- blocked, visible member (with a per-event alias)
 ('00000000-0000-0000-0000-000000000004'), -- visible member who blocked the caller
 ('00000000-0000-0000-0000-000000000005'), -- blocked, Private Mode
 ('00000000-0000-0000-0000-000000000006'), -- blocked, profile not public
 ('00000000-0000-0000-0000-000000000007'), -- blocked, red
 ('00000000-0000-0000-0000-000000000008'), -- blocked, green but purple for this event
 ('00000000-0000-0000-0000-000000000009'); -- blocked, cued out
insert into public.profiles(id, display_name) select id, 'Person ' || right(id::text,1) from auth.users
 on conflict(id) do update set display_name = excluded.display_name;
-- The host hides everything; they still count, as host_name already names them.
update public.profiles set private_mode = true, is_public = false, status = 'PURPLE'
 where id in ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005');
update public.profiles set is_public = false where id = '00000000-0000-0000-0000-000000000006';
update public.profiles set status = 'RED' where id = '00000000-0000-0000-0000-000000000007';
insert into public.events(id, host_id, name, join_code, is_public, active) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Public','BLKW01', true, true),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Private','BLKW02', false, true),
 ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004','Ended','BLKW03', false, false);
insert into public.event_members(event_id, user_id, cued_in)
 select '10000000-0000-0000-0000-000000000001', id, id <> '00000000-0000-0000-0000-000000000009' from auth.users
 where id not in ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002');
insert into public.event_members(event_id, user_id) values
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003'),
 ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003');
insert into public.event_profiles(event_id, user_id, display_name, status) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003','Alias Three', null),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000008', null, 'PURPLE');
insert into public.blocks(blocker, blocked)
 select '00000000-0000-0000-0000-000000000002', id from auth.users
 where id not in ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004');
insert into public.blocks(blocker, blocked) values
 ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002');

set local role anon;
select pg_temp.assert_denied($q$select * from public.blocked_attendees('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select * from public.blocked_attendees_by_code('BLKW01')$q$);

set local role authenticated;
select pg_temp.assert_denied($q$select * from public.blocked_attendees_for('10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002')$q$);
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
-- Only the host and the visible member, by main name; nobody filtered out by privacy, cue, Cue Out,
-- or because they blocked the caller.
select pg_temp.assert_true(pg_temp.by_id('10000000-0000-0000-0000-000000000001') = 'Person 1,Person 3',
  'public event by id: ' || pg_temp.by_id('10000000-0000-0000-0000-000000000001'));
select pg_temp.assert_true(pg_temp.by_code('  blkw01 ') = 'Person 1,Person 3', 'code is trimmed and case-insensitive');
select pg_temp.assert_true(not exists (select 1 from public.blocked_attendees('10000000-0000-0000-0000-000000000001') b
  where b.display_name = 'Alias Three'), 'per-event alias never returned');
-- An override to green makes a red member count; cueing back in makes a cued-out one count.
reset role;
insert into public.event_profiles(event_id, user_id, status) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000007','GREEN');
update public.event_members set cued_in = true where user_id = '00000000-0000-0000-0000-000000000009';
set local role authenticated;
select pg_temp.assert_true(pg_temp.by_id('10000000-0000-0000-0000-000000000001') = 'Person 1,Person 3,Person 7,Person 9',
  'event cue override and cue in apply: ' || pg_temp.by_id('10000000-0000-0000-0000-000000000001'));

-- A private event the caller cannot read answers nothing by id, but its code works.
select pg_temp.assert_true(pg_temp.by_id('10000000-0000-0000-0000-000000000002') = '', 'unreadable event by id');
select pg_temp.assert_true(pg_temp.by_id('10000000-0000-0000-0000-0000000000ff') = '', 'unknown event by id');
select pg_temp.assert_true(pg_temp.by_code('BLKW02') = 'Person 1,Person 3', 'private event by code');
-- Ended events and unknown codes answer nothing.
select pg_temp.assert_true(pg_temp.by_code('BLKW03') = '', 'ended event by code');
select pg_temp.assert_true(pg_temp.by_code('NOPE99') = '', 'unknown code');
-- A member can ask by id; the check never joins anyone.
select public.join_event_by_code('BLKW02');
select pg_temp.assert_true(pg_temp.by_id('10000000-0000-0000-0000-000000000002') = 'Person 1,Person 3', 'member by id');
reset role;
select pg_temp.assert_true((select count(*) = 1 from public.event_members
  where user_id = '00000000-0000-0000-0000-000000000002'), 'only the explicit join added a membership');

-- Each person's warning is about their own blocks: the member who blocked the caller is warned.
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000004"}';
select pg_temp.assert_true(pg_temp.by_code('BLKW02') = 'Person 2', 'blocker is warned about whom they blocked');
rollback;
