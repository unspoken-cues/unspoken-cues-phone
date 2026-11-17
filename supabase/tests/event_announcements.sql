-- ISOLATED DATABASE ONLY, after bootstrap.sql + schema.sql (or baseline + migration).
-- All assertions run as real API roles, not the table owner. Everything is rolled back.
begin;
create function public.test_assert(ok boolean, label text) returns void language plpgsql as $$
begin
  if ok is distinct from true then raise exception 'FAIL: %', label; end if;
end;
$$;
create function public.test_error(statement text, expected text, label text)
returns void language plpgsql as $$
begin
  begin
    execute statement;
  exception when others then
    if sqlstate = expected then return; end if;
    raise exception 'FAIL: % expected %, got % (%)', label, expected, sqlstate, sqlerrm;
  end;
  raise exception 'FAIL: % unexpectedly succeeded', label;
end;
$$;

insert into auth.users (id) values
 ('11111111-1111-1111-1111-111111111111'),
 ('22222222-2222-2222-2222-222222222222'),
 ('33333333-3333-3333-3333-333333333333'),
 ('44444444-4444-4444-4444-444444444444');
insert into public.events (id, host_id, name, join_code, active, is_public, starts_at, ends_at) values
 ('eeeeeeee-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Public fixture', 'ANN001', true, true, null, null),
 ('eeeeeeee-0000-0000-0000-000000000002', '44444444-4444-4444-4444-444444444444', 'Private fixture', 'ANN002', true, false, null, null),
 ('eeeeeeee-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Ended fixture', 'ANN003', false, false, null, null),
 ('eeeeeeee-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Expired fixture', 'ANN004', true, false, now()-interval '2 hours', now()-interval '1 hour'),
 ('eeeeeeee-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Implicit end', 'ANN005', true, false, now()-interval '3 hours', null),
 ('eeeeeeee-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Future end', 'ANN006', true, false, null, now()+interval '1 hour'),
 ('eeeeeeee-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'Exact end', 'ANN007', true, false, null, now()),
 ('eeeeeeee-0000-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', 'Cascade', 'ANN008', true, false, null, null);
insert into public.event_members (event_id, user_id) values
 ('eeeeeeee-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222');
-- Model old history on ended events using an admin seed; client inserts are tested below.
insert into public.event_announcements (event_id, body, request_id) values
 ('eeeeeeee-0000-0000-0000-000000000003', 'History remains readable', 'aaaaaaaa-0000-0000-0000-000000000003');

set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';
select public.test_assert(
 (public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001', E' \t\n' || chr(11) || U&'\00A0\2007\202F\3000' || 'vivid' || U&'\00A0', 'aaaaaaaa-0000-0000-0000-000000000001')).body = 'vivid',
 'server trims Unicode/vertical whitespace and preserves letter v');
select public.test_assert(
 (public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001', 'vivid', 'aaaaaaaa-0000-0000-0000-000000000001')).id =
 (select id from public.event_announcements where request_id='aaaaaaaa-0000-0000-0000-000000000001'),
 'retry returns same persisted row');
select public.test_assert((select count(*) = 1 from public.event_announcements where event_id='eeeeeeee-0000-0000-0000-000000000001'), 'retry writes exactly once');
select public.test_assert((select created_at >= transaction_timestamp() from public.event_announcements where request_id='aaaaaaaa-0000-0000-0000-000000000001'), 'timestamp comes from server');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001','changed','aaaaaaaa-0000-0000-0000-000000000001')$q$, '22023', 'request cannot be reused for changed text');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001', E' \t\n' || chr(11) || U&'\00A0\2007\202F\3000', gen_random_uuid())$q$, '22023', 'Unicode-only whitespace denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001',null,gen_random_uuid())$q$, '22023', 'null body denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001',repeat('a',1001),gen_random_uuid())$q$, '22023', 'over limit denied');
select public.test_assert(char_length((public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000006',repeat('🎉',1000),gen_random_uuid())).body)=1000, 'Unicode code point limit accepted');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000003','no',gen_random_uuid())$q$, '42501', 'inactive event denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000004','no',gen_random_uuid())$q$, '42501', 'expired event denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000005','no',gen_random_uuid())$q$, '42501', 'implicit three hour end denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000007','no',gen_random_uuid())$q$, '42501', 'exact end boundary denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000002','no',gen_random_uuid())$q$, '42501', 'other hosts event denied');
select public.test_assert((select count(*)=1 from public.event_announcements where event_id='eeeeeeee-0000-0000-0000-000000000003'), 'host reads ended history');
select public.test_error($q$insert into public.event_announcements(id,event_id,body,created_at,request_id) values(gen_random_uuid(),'eeeeeeee-0000-0000-0000-000000000001','spoof', '2000-01-01',gen_random_uuid())$q$, '42501', 'direct host insert and timestamp spoof denied');
select public.test_error($q$update public.event_announcements set body='edit'$q$, '42501', 'host cannot edit');
select public.test_error($q$delete from public.event_announcements$q$, '42501', 'host cannot delete announcements');
select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000008','Cascade me',gen_random_uuid());

set local request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';
select public.test_assert((select count(*)=1 from public.event_announcements where event_id='eeeeeeee-0000-0000-0000-000000000001'), 'joined member reads');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001','member spoof',gen_random_uuid())$q$, '42501', 'member cannot publish');
select public.test_error($q$insert into public.event_announcements(event_id,body,request_id) values('eeeeeeee-0000-0000-0000-000000000001','member insert',gen_random_uuid())$q$, '42501', 'member direct write denied');

set local request.jwt.claims = '{"sub":"33333333-3333-3333-3333-333333333333","role":"authenticated"}';
select public.test_assert((select count(*)=1 from public.events where id='eeeeeeee-0000-0000-0000-000000000001'), 'outsider can discover public event');
select public.test_assert((select count(*)=0 from public.event_announcements), 'public visibility does not expose announcements');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001','outsider spoof',gen_random_uuid())$q$, '42501', 'outsider cannot publish');

set local role anon;
set local request.jwt.claims = '{"role":"anon"}';
select public.test_error('select * from public.event_announcements', '42501', 'anonymous read denied');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001','anonymous spoof',gen_random_uuid())$q$, '42501', 'anonymous RPC denied');

reset role;
update public.events set active=false where id='eeeeeeee-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';
select public.test_assert((select count(*)=1 from public.event_announcements where event_id='eeeeeeee-0000-0000-0000-000000000001'), 'member reads history after end');
delete from public.event_members where event_id='eeeeeeee-0000-0000-0000-000000000001' and user_id=auth.uid();
select public.test_assert((select count(*)=0 from public.event_announcements), 'leaving revokes reading immediately');
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';
select public.test_assert((public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001','vivid','aaaaaaaa-0000-0000-0000-000000000001')).body='vivid', 'retry of committed row after end does not write again');
select public.test_error($q$select public.post_event_announcement('eeeeeeee-0000-0000-0000-000000000001','new post',gen_random_uuid())$q$, '42501', 'new post after end denied');
delete from public.events where id='eeeeeeee-0000-0000-0000-000000000008';
reset role;
select public.test_assert((select count(*)=0 from public.event_announcements where event_id='eeeeeeee-0000-0000-0000-000000000008'), 'event deletion cascades');
select public.test_assert((select count(*)=0 from information_schema.columns where table_schema='public' and table_name='event_announcements' and column_name in ('author_id','host_name','display_name','profile','email')), 'feed contains no hidden profile data');
rollback;
