-- Run AFTER quick_reactions_bootstrap.sql + 202611170002_quick_reactions.sql in a disposable DB.
-- All tests roll back. Fail fast (psql: -v ON_ERROR_STOP=1). No pgTAP extension required.
begin;
create function pg_temp.assert_true(ok boolean, message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', message; end if; end; $$;
create function pg_temp.assert_denied(statement text) returns void language plpgsql as $$
begin
  begin execute statement;
  exception when insufficient_privilege or undefined_function then return; end;
  raise exception 'FAILED: statement unexpectedly allowed: %', statement;
end; $$;
insert into auth.users(id) values
 ('00000000-0000-0000-0000-000000000001'), -- host
 ('00000000-0000-0000-0000-000000000002'), -- sender
 ('00000000-0000-0000-0000-000000000003'), -- other member
 ('00000000-0000-0000-0000-000000000004'); -- outsider
insert into public.profiles(id, display_name) select id, 'Person ' || right(id::text,1) from auth.users
 on conflict(id) do update set display_name = excluded.display_name;
insert into public.events(id, host_id, name, join_code) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Wave test','WAVET1');
insert into public.event_members(event_id, user_id) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002'),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003');
set local role anon;
select pg_temp.assert_denied($q$select public.event_wave_state('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true)$q$);
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000004"}';
select pg_temp.assert_denied($q$select public.event_wave_state('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true)$q$);
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_true(not (public.event_wave_state('10000000-0000-0000-0000-000000000001')->>'receive_waves')::boolean, 'default off');
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'recipients' = '[]'::jsonb, 'recipient must opt in');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
-- Direct access, bypass writes, forged sender and internal helper must all fail.
select pg_temp.assert_denied('select * from public.event_wave_preferences');
select pg_temp.assert_denied('select * from public.event_waves');
select pg_temp.assert_denied($q$insert into public.event_waves values ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003',now())$q$);
select pg_temp.assert_denied($q$insert into public.event_wave_preferences values ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',true)$q$);
select pg_temp.assert_denied('update public.event_wave_preferences set receive_waves = true');
select pg_temp.assert_denied('delete from public.event_waves');
select pg_temp.assert_denied($q$select public.event_wave_participant('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003')$q$);
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(jsonb_array_length(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'recipients') = 1, 'host counts without membership');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')$q$);
select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001');
select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001');
select pg_temp.assert_true((public.event_wave_state('10000000-0000-0000-0000-000000000001')->'recipients'->0->>'sent')::boolean, 'acknowledged sent state');
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'sender cannot read recipient inbox');
reset role;
select pg_temp.assert_true((select count(*) = 1 from public.event_waves), 'duplicate requests persist once');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox'->0->>'sender_name' = 'Person 2', 'receiver sees own wave');
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',false);
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'opt-out hides inbox');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',false);
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true);
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true);
-- Exercise sender and recipient hidden/Red/Purple; host never bypasses those checks.
reset role;
update public.profiles set is_public = false where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'hidden sender absent from history');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')$q$);
reset role;
update public.profiles set is_public = true, status = 'RED' where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'Red sender absent from history');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')$q$);
reset role;
update public.profiles set status = 'PURPLE' where id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'Purple caller cannot read inbox');
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
reset role;
update public.profiles set status = 'YELLOW' where id = '00000000-0000-0000-0000-000000000002';
update public.events set starts_at = now() - interval '4 hours';
set local role authenticated;
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
reset role;
update public.events set starts_at = now() + interval '1 hour', ends_at = now();
set local role authenticated;
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
reset role;
update public.events set starts_at = null, ends_at = null, active = false;
set local role authenticated;
select pg_temp.assert_denied($q$select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')$q$);
reset role;
update public.events set active = true;
delete from public.event_members where user_id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.assert_denied($q$select public.event_wave_state('10000000-0000-0000-0000-000000000001')$q$);
select pg_temp.assert_denied($q$select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true)$q$);
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true(public.event_wave_state('10000000-0000-0000-0000-000000000001')->'inbox' = '[]'::jsonb, 'departed sender hidden');
reset role;
insert into public.event_members(event_id, user_id) values ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_true(not (public.event_wave_state('10000000-0000-0000-0000-000000000001')->>'receive_waves')::boolean, 'rejoin requires new consent');
select public.set_event_wave_preference('10000000-0000-0000-0000-000000000001',true);
select public.send_event_wave('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001');
reset role;
select pg_temp.assert_true((select count(*) = 1 from public.event_waves), 'rejoin does not reset limit');
delete from public.events where id = '10000000-0000-0000-0000-000000000001';
select pg_temp.assert_true((select count(*) = 0 from public.event_waves), 'event cascade waves');
select pg_temp.assert_true((select count(*) = 0 from public.event_wave_preferences), 'event cascade preferences');
rollback;
