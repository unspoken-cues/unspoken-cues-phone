-- Run after the complete schema, or the waves baseline plus 202611170003 and 202611170004, in a
-- disposable DB. All tests roll back. Fail fast (psql: -v ON_ERROR_STOP=1). No pgTAP required.
begin;
create function pg_temp.assert_true(ok boolean, message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', message; end if; end; $$;
create function pg_temp.assert_error(statement text, code text) returns void language plpgsql as $$
begin
  begin execute statement;
  exception when others then
    if sqlstate = code then return; end if;
    raise exception 'FAILED: % raised % (%), expected %', statement, sqlstate, sqlerrm, code;
  end;
  raise exception 'FAILED: statement unexpectedly allowed: %', statement;
end; $$;
create function pg_temp.assert_denied(statement text) returns void language sql as $$
  select pg_temp.assert_error(statement, '42501');
$$;
-- The row user 3 (a plain member who has not swapped) sees for user 2 at an event.
create function pg_temp.seen(event_id uuid) returns record language sql as $$
  select (a.display_name, a.bio, a.status) from public.event_attendees(event_id) a
  where a.user_id = '00000000-0000-0000-0000-000000000002';
$$;
insert into auth.users(id) values
 ('00000000-0000-0000-0000-000000000001'), -- host
 ('00000000-0000-0000-0000-000000000002'), -- member with overrides
 ('00000000-0000-0000-0000-000000000003'), -- other member (viewer)
 ('00000000-0000-0000-0000-000000000004'); -- outsider
insert into public.profiles(id, display_name, bio) select id, 'Person ' || right(id::text,1), 'Main bio' from auth.users
 on conflict(id) do update set display_name = excluded.display_name, bio = excluded.bio;
insert into public.events(id, host_id, name, join_code) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Resort','PROF01'),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Cruise','PROF02');
insert into public.event_members(event_id, user_id) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002'),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003'),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002'),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003');

set local role anon;
select pg_temp.assert_denied($q$select public.my_event_profile('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.set_event_profile('10000000-0000-0000-0000-000000000001','X',null,null)$q$);
select pg_temp.assert_denied('select * from public.event_profiles');

-- A non-participant can neither read nor set an override.
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000004"}';
select pg_temp.assert_denied($q$select public.my_event_profile('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.set_event_profile('10000000-0000-0000-0000-000000000001','Intruder',null,'GREEN')$q$);

-- Fallback: no override means the main profile.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001')::text = '("Person 2","Main bio",GREEN)', 'fallback to main profile');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_true(public.my_event_profile('10000000-0000-0000-0000-000000000001')
  = '{"display_name":null,"bio":null,"status":null}'::jsonb, 'off by default');
select pg_temp.assert_true(public.set_event_profile('10000000-0000-0000-0000-000000000001','  Sam at the resort ','Here for the pool','YELLOW')
  = '{"display_name":"Sam at the resort","bio":"Here for the pool","status":"YELLOW"}'::jsonb, 'saved trimmed');

-- Direct table access and internal helpers stay closed even to the owner.
select pg_temp.assert_denied('select * from public.event_profiles');
select pg_temp.assert_denied($q$insert into public.event_profiles(event_id, user_id, display_name) values ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003','Forged')$q$);
select pg_temp.assert_denied($q$update public.event_profiles set display_name = 'Forged'$q$);
select pg_temp.assert_denied('delete from public.event_profiles');
select pg_temp.assert_denied($q$select * from public.event_effective_profiles('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_true(to_regprocedure('public.my_event_profile(uuid,uuid)') is null, 'no way to name another user');

-- Others see the override at this event only; the main profile is untouched.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001')::text = '("Sam at the resort","Here for the pool",YELLOW)', 'override shown to attendees');
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000002')::text = '("Person 2","Main bio",GREEN)', 'other events use main profile');
select pg_temp.assert_true(public.my_event_profile('10000000-0000-0000-0000-000000000001')->>'display_name' is null, 'cannot read another override');
reset role;
select pg_temp.assert_true((select display_name = 'Person 2' and bio = 'Main bio' and status = 'GREEN' from public.profiles
  where id = '00000000-0000-0000-0000-000000000002'), 'main profile unchanged');
select pg_temp.assert_true((select count(*) = 1 from public.event_profiles), 'only the caller row was written');

-- show_* toggles still hide fields whatever their source; the owner still sees their own.
update public.profiles set show_bio = false, show_status = false where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001')::text = '("Sam at the resort","",)', 'show_bio/show_status hide overrides');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001')::text = '("Sam at the resort","Here for the pool",YELLOW)', 'owner sees own override');
reset role;
update public.profiles set show_bio = true, show_status = true, private_mode = true where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001') is null, 'Private Mode still hides');
reset role;
update public.profiles set private_mode = false where id = '00000000-0000-0000-0000-000000000002';

-- An event cue of Red hides the person from non-hosts there, as a main Red cue does.
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select public.set_event_profile('10000000-0000-0000-0000-000000000001','Sam at the resort',null,'RED');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001') is null, 'event Red hidden from members');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true(pg_temp.seen('10000000-0000-0000-0000-000000000001')::text = '("Sam at the resort","Main bio",RED)', 'host sees event Red; cleared bio falls back');

-- Validation and clearing.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_error(format('select public.set_event_profile(%L, null, %L, null)',
  '10000000-0000-0000-0000-000000000001', repeat('b', 121)), '22023');
select pg_temp.assert_error($q$select public.set_event_profile('10000000-0000-0000-0000-000000000001',null,null,'BLUE')$q$, '22P02');
select pg_temp.assert_true(public.set_event_profile('10000000-0000-0000-0000-000000000001','   ', repeat('b', 120), null)
  = jsonb_build_object('display_name', null, 'bio', repeat('b', 120), 'status', null), 'blank name clears it; 120 fits');
select public.set_event_profile('10000000-0000-0000-0000-000000000001', '', '', null);
reset role;
select pg_temp.assert_true((select count(*) = 0 from public.event_profiles), 'clearing every field removes the row');

-- Waves: the event cue decides eligibility and the event name labels targets and the inbox.
update public.profiles set status = 'RED' where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001', true);
select pg_temp.assert_true(not (public.event_wave_state('10000000-0000-0000-0000-000000000001')->>'can_wave')::boolean, 'main Red blocks waves');
select public.set_event_profile('10000000-0000-0000-0000-000000000001','Sam at the resort',null,'GREEN');
select pg_temp.assert_true((public.event_wave_state('10000000-0000-0000-0000-000000000001')->>'can_wave')::boolean, 'event Green allows waves');
select pg_temp.assert_true(not (public.event_wave_state('10000000-0000-0000-0000-000000000002')->>'can_wave')::boolean, 'other event keeps main Red');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001', true);
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'recipients'->0->>'display_name' = 'Sam at the resort',
  'targets use the event name');
select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox'->0->>'sender_name' = 'Sam at the resort',
  'inbox uses the event name');
reset role;
update public.profiles set status = 'GREEN' where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select public.set_event_profile('10000000-0000-0000-0000-000000000001',null,null,'PURPLE');
select pg_temp.assert_true(not (public.event_wave_state('10000000-0000-0000-0000-000000000001')->>'can_wave')::boolean, 'event Purple blocks waves over main Green');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003')$q$);
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'recipients' = '[]'::jsonb
  and public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'event Purple hides target and history');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')$q$);

-- Leaving deletes the override; rejoining starts from the main profile.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select public.set_event_profile('10000000-0000-0000-0000-000000000002','Sam on deck',null,null);
delete from public.event_members where event_id = '10000000-0000-0000-0000-000000000001' and user_id = '00000000-0000-0000-0000-000000000002';
select pg_temp.assert_denied($q$select public.set_event_profile('10000000-0000-0000-0000-000000000001','Gone',null,null)$q$);
reset role;
select pg_temp.assert_true((select count(*) = 0 from public.event_profiles where event_id = '10000000-0000-0000-0000-000000000001'), 'leaving deletes the override');
select pg_temp.assert_true((select count(*) = 1 from public.event_profiles), 'other events keep theirs');
insert into public.event_members(event_id, user_id) values ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002');
set local role authenticated;
select pg_temp.assert_true(public.my_event_profile('10000000-0000-0000-0000-000000000001')->>'display_name' is null, 'rejoin starts clean');

-- The host is a participant without membership.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true(public.set_event_profile('10000000-0000-0000-0000-000000000001','Your host',null,null)->>'display_name' = 'Your host',
  'host may set one without membership');

-- A cued-out member still owns their event profile, and the host still sees it.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select public.set_event_cued_in('10000000-0000-0000-0000-000000000002', false);
select pg_temp.assert_true(public.set_event_profile('10000000-0000-0000-0000-000000000002','Quiet guest',null,null)->>'display_name' = 'Quiet guest',
  'cued-out member may set an override');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true((select a.display_name = 'Quiet guest' and not a.cued_in from public.event_attendees('10000000-0000-0000-0000-000000000002') a
  where a.user_id = '00000000-0000-0000-0000-000000000003'), 'host sees the cued-out override');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000003"}';
select public.set_event_profile('10000000-0000-0000-0000-000000000002',null,null,null);
reset role;

-- Deleting an event or an account cascades.
delete from public.events where id = '10000000-0000-0000-0000-000000000001';
select pg_temp.assert_true((select count(*) = 1 from public.event_profiles), 'event delete cascades');
delete from auth.users where id = '00000000-0000-0000-0000-000000000002';
select pg_temp.assert_true((select count(*) = 0 from public.event_profiles), 'account delete cascades');
rollback;
