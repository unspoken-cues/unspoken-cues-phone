-- Core privacy rules: attendee list, binder, swapping, direct table reads, profile limits and
-- account deletion. Run after the complete schema, or the event profiles baseline plus
-- 202611170005_hardening.sql, in a disposable DB. All tests roll back. Fail fast
-- (psql: -v ON_ERROR_STOP=1). No pgTAP required.
begin;
create function pg_temp.assert_true(ok boolean, message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', message; end if; end; $$;
create function pg_temp.assert_denied(statement text) returns void language plpgsql as $$
begin
  begin execute statement;
  exception when insufficient_privilege then return; end;
  raise exception 'FAILED: statement unexpectedly allowed: %', statement;
end; $$;
-- 'SQLSTATE: message' of the error the statement raises, or '' if it succeeds. Undone either way.
create function pg_temp.error_of(statement text) returns text language plpgsql as $$
begin
  begin
    execute statement;
    raise exception using errcode = 'UCTST';
  exception when sqlstate 'UCTST' then return '';
  end;
exception when others then return sqlstate || ': ' || sqlerrm;
end; $$;
create function pg_temp.listed(who uuid) returns boolean language sql as $$
  select exists (select 1 from public.event_attendees('10000000-0000-0000-0000-000000000001') a
    where a.user_id = who)
$$;
create function pg_temp.in_binder(who uuid) returns boolean language sql as $$
  select exists (select 1 from public.binder_cards() b where b.id = who)
$$;
insert into auth.users(id) values
 ('00000000-0000-0000-0000-000000000001'), -- host, not a member
 ('00000000-0000-0000-0000-000000000002'), -- viewer, a member
 ('00000000-0000-0000-0000-000000000003'), -- member the viewer and the host blocked
 ('00000000-0000-0000-0000-000000000004'), -- member who blocked the viewer and the host
 ('00000000-0000-0000-0000-000000000005'), -- member in Private Mode
 ('00000000-0000-0000-0000-000000000006'), -- non-public member
 ('00000000-0000-0000-0000-000000000007'), -- Red member
 ('00000000-0000-0000-0000-000000000008'), -- Purple member
 ('00000000-0000-0000-0000-000000000009'), -- public Green member; deletes their account at the end
 ('00000000-0000-0000-0000-000000000010'); -- outsider
insert into public.profiles(id, display_name) select id, 'Person ' || right(id::text, 2) from auth.users
 on conflict(id) do update set display_name = excluded.display_name;
update public.profiles set private_mode = true where id = '00000000-0000-0000-0000-000000000005';
update public.profiles set is_public = false where id = '00000000-0000-0000-0000-000000000006';
update public.profiles set status = 'RED' where id = '00000000-0000-0000-0000-000000000007';
update public.profiles set status = 'PURPLE' where id = '00000000-0000-0000-0000-000000000008';
-- No end time: public.event_end() makes it end 3 hours after the start.
insert into public.events(id, host_id, name, join_code, starts_at) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Privacy test','PRIV01',
  now() - interval '1 hour');
insert into public.event_members(event_id, user_id)
 select '10000000-0000-0000-0000-000000000001', id from auth.users
 where id not in ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010');
insert into public.blocks(blocker, blocked) values
 ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003'),
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003'),
 ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002'),
 ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000001');
-- Inserted directly so the blocked pairs still have a swap row for binder_cards() to filter.
insert into public.swaps(user_a, user_b, expires_at)
 select least(a, b), greatest(a, b), e from (values
  ('00000000-0000-0000-0000-000000000002'::uuid,'00000000-0000-0000-0000-000000000003'::uuid,null::timestamptz),
  ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000004',null),
  ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000005',null),
  ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000006',now() - interval '1 minute'),
  ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000007',null),
  ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000009',null),
  ('00000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000010',null)) v(a, b, e);
insert into public.card_tokens(user_id, token) values
 ('00000000-0000-0000-0000-000000000002','tok02'), ('00000000-0000-0000-0000-000000000003','tok03'),
 ('00000000-0000-0000-0000-000000000004','tok04'), ('00000000-0000-0000-0000-000000000005','tok05'),
 ('00000000-0000-0000-0000-000000000009','tok09');

set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
-- A non-host's attendee list.
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000002'), 'viewer sees themselves');
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000009'), 'public Green member is listed');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000003'), 'member the viewer blocked is hidden');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000004'), 'member who blocked the viewer is hidden');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000005'), 'Private Mode member is hidden');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000006'), 'non-public member is hidden');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000007'), 'Red member is hidden, even when swapped');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000008'), 'Purple member is hidden');

-- The binder: active swaps only, minus blocks either way and Private Mode. The cue does not matter.
select pg_temp.assert_true(pg_temp.in_binder('00000000-0000-0000-0000-000000000009'), 'active swap is in the binder');
select pg_temp.assert_true(pg_temp.in_binder('00000000-0000-0000-0000-000000000007'), 'Red partner stays in the binder');
select pg_temp.assert_true(not pg_temp.in_binder('00000000-0000-0000-0000-000000000003'), 'binder hides someone the viewer blocked');
select pg_temp.assert_true(not pg_temp.in_binder('00000000-0000-0000-0000-000000000004'), 'binder hides someone who blocked the viewer');
select pg_temp.assert_true(not pg_temp.in_binder('00000000-0000-0000-0000-000000000005'), 'binder hides a Private Mode partner');
select pg_temp.assert_true(not pg_temp.in_binder('00000000-0000-0000-0000-000000000006'), 'binder hides an expired swap');

-- Swapping by card token.
select pg_temp.assert_true(pg_temp.error_of($q$select public.swap_by_card_token('nope')$q$) like 'P0002:%', 'unknown token is P0002');
select pg_temp.assert_true(pg_temp.error_of($q$select public.swap_by_card_token('tok03')$q$)
  = pg_temp.error_of($q$select public.swap_by_card_token('nope')$q$), 'a block the caller made looks like an unknown token');
select pg_temp.assert_true(pg_temp.error_of($q$select public.swap_by_card_token('tok04')$q$)
  = pg_temp.error_of($q$select public.swap_by_card_token('nope')$q$), 'a block against the caller looks like an unknown token');
select pg_temp.assert_true(pg_temp.error_of($q$select public.swap_by_card_token('tok02')$q$) like '22023:%', 'own card is refused');
select pg_temp.assert_true(pg_temp.error_of($q$select public.swap_by_card_token('tok05')$q$) like 'UC002:%', 'owner in Private Mode is refused');

-- Direct table reads show nobody else's rows.
select pg_temp.assert_true(exists (select 1 from public.profiles where id = auth.uid()), 'own profile is readable');
select pg_temp.assert_true(not exists (select 1 from public.profiles where id <> auth.uid()), 'no other profile is readable');
select pg_temp.assert_true(not exists (select 1 from public.card_tokens where user_id <> auth.uid()), 'no other card token is readable');
select pg_temp.assert_true(not exists (select 1 from public.swaps where auth.uid() not in (user_a, user_b)), 'no other people''s swaps');
select pg_temp.assert_true(not exists (select 1 from public.blocks where blocker <> auth.uid()), 'a block against the viewer stays hidden');
select pg_temp.assert_true(exists (select 1 from public.event_members where user_id = auth.uid()), 'own membership is readable');
select pg_temp.assert_true(not exists (select 1 from public.event_members where user_id <> auth.uid()),
  'a co-member reads only their own membership rows');

-- The event has no end time, so a temporary swap ends 3 hours after its start.
select pg_temp.assert_true(public.make_swap_temporary('00000000-0000-0000-0000-000000000009') = now() + interval '2 hours',
  'temporary swap ends with the event');

set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000005"}';
select pg_temp.assert_true(pg_temp.error_of($q$select public.swap_by_card_token('tok09')$q$) like 'UC001:%', 'caller in Private Mode is refused');

set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001"}';
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000007'), 'host sees the Red member');
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000008'), 'host sees the Purple member');
select pg_temp.assert_true(pg_temp.listed('00000000-0000-0000-0000-000000000009'), 'host sees the Green member');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000003'), 'host does not see someone they blocked');
select pg_temp.assert_true(not pg_temp.listed('00000000-0000-0000-0000-000000000004'), 'host does not see someone who blocked them');
select pg_temp.assert_true(not exists (select 1 from public.event_members), 'the host reads no membership rows directly');

-- Profile size limits.
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002"}';
select pg_temp.assert_true(pg_temp.error_of($q$update public.profiles set display_name = repeat('n', 81) where id = auth.uid()$q$)
  like '23514:%', 'name over 80 characters is refused');
select pg_temp.assert_true(pg_temp.error_of($q$update public.profiles set bio = repeat('b', 121) where id = auth.uid()$q$)
  like '23514:%', 'bio over 120 characters is refused');
select pg_temp.assert_true(pg_temp.error_of($q$update public.profiles set preferences = array_fill('p'::text, array[21]) where id = auth.uid()$q$)
  like '23514:%', 'more than 20 preferences are refused');
select pg_temp.assert_true(pg_temp.error_of($q$update public.profiles set boundaries = array[repeat('b', 61)] where id = auth.uid()$q$)
  like '23514:%', 'a boundary over 60 characters is refused');
select pg_temp.assert_true(pg_temp.error_of($q$select public.set_event_profile('10000000-0000-0000-0000-000000000001', repeat('n', 81), null, null)$q$)
  like '23514:%', 'event name over 80 characters is refused');
update public.profiles set display_name = repeat('n', 80), bio = repeat('b', 120),
  preferences = array_fill(repeat('p', 60), array[20]), boundaries = array_fill(repeat('b', 60), array[20])
  where id = auth.uid();
select pg_temp.assert_true((select char_length(display_name) = 80 and cardinality(boundaries) = 20
  from public.profiles where id = auth.uid()), 'values at the limits are accepted');

reset role;
select pg_temp.assert_true((select count(*) = 2 from storage.buckets where id in ('avatars', 'event-covers')
  and file_size_limit = 5242880 and allowed_mime_types = '{image/jpeg}'), 'buckets take only JPEGs up to 5 MB');

-- Account deletion. Person 9 also has waves, an event profile, a hosted event and a block.
insert into public.event_wave_preferences(event_id, user_id, receive_waves) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000009',true),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',true);
insert into public.event_waves(event_id, sender_id, recipient_id) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000002'),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000009');
insert into public.event_profiles(event_id, user_id, display_name) values
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000009','Nine at the event');
insert into public.events(id, host_id, name, join_code) values
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000009','Hosted by nine','PRIV02');
insert into public.event_members(event_id, user_id) values
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002');
insert into public.blocks(blocker, blocked) values
 ('00000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000010');

set local role anon;
select pg_temp.assert_denied('select public.delete_my_account()');
set local role authenticated;
set local request.jwt.claims = '';
select pg_temp.assert_true(pg_temp.error_of('select public.delete_my_account()') like '28000:%', 'signed-out call is refused');
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000009"}';
insert into public.reports(reported, reason) values ('00000000-0000-0000-0000-000000000010', 'spam');
select public.delete_my_account();
reset role;
select pg_temp.assert_true(not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-000000000009'), 'auth user deleted');
select pg_temp.assert_true(not exists (select 1 from public.profiles where id = '00000000-0000-0000-0000-000000000009'), 'profile deleted');
select pg_temp.assert_true(not exists (select 1 from public.event_members where user_id = '00000000-0000-0000-0000-000000000009'), 'memberships deleted');
select pg_temp.assert_true(not exists (select 1 from public.swaps
  where '00000000-0000-0000-0000-000000000009' in (user_a, user_b)), 'swaps deleted');
select pg_temp.assert_true(not exists (select 1 from public.card_tokens where user_id = '00000000-0000-0000-0000-000000000009'), 'card token deleted');
select pg_temp.assert_true(not exists (select 1 from public.blocks
  where '00000000-0000-0000-0000-000000000009' in (blocker, blocked)), 'blocks deleted');
select pg_temp.assert_true(not exists (select 1 from public.event_waves
  where '00000000-0000-0000-0000-000000000009' in (sender_id, recipient_id)), 'waves both ways deleted');
select pg_temp.assert_true(not exists (select 1 from public.event_wave_preferences
  where user_id = '00000000-0000-0000-0000-000000000009'), 'wave consent deleted');
select pg_temp.assert_true(not exists (select 1 from public.event_profiles
  where user_id = '00000000-0000-0000-0000-000000000009'), 'event profile deleted');
select pg_temp.assert_true(not exists (select 1 from public.events where id = '10000000-0000-0000-0000-000000000002')
  and not exists (select 1 from public.event_members where event_id = '10000000-0000-0000-0000-000000000002'),
  'hosted event and its memberships deleted');
select pg_temp.assert_true(exists (select 1 from public.reports
  where reporter is null and reported = '00000000-0000-0000-0000-000000000010'), 'a filed report survives with no reporter');
select pg_temp.assert_true(exists (select 1 from public.profiles where id = '00000000-0000-0000-0000-000000000002')
  and exists (select 1 from public.event_members where user_id = '00000000-0000-0000-0000-000000000002'
    and event_id = '10000000-0000-0000-0000-000000000001'), 'other people keep their data');
rollback;
