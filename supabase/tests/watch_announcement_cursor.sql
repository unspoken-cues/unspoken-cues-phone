-- Isolated database only, after bootstrap.sql and schema.sql. All data rolls back.
begin;
create function public.check_alert(ok boolean, label text) returns void language plpgsql as $$
begin
  if ok is distinct from true then raise exception 'FAIL: %', label; end if;
end;
$$;
insert into auth.users(id) values
 ('aaaaaaaa-0000-4000-8000-000000000001'), ('aaaaaaaa-0000-4000-8000-000000000002'),
 ('aaaaaaaa-0000-4000-8000-000000000003');
insert into public.events(id, host_id, name, join_code, is_public) values
 ('eeeeeeee-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000001','Fixture','ALT001',true),
 ('eeeeeeee-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000002','Own','ALT002',true);
insert into public.event_members (event_id,user_id,joined_at) values
 ('eeeeeeee-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000002',now()-interval '1 day'),
 ('eeeeeeee-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000002',now()-interval '1 day');
insert into public.event_announcements(event_id,body,request_id,created_at) values
 ('eeeeeeee-0000-4000-8000-000000000001','Old',gen_random_uuid(),now()-interval '1 hour');
set local role authenticated;
set local request.jwt.claims = '{"sub":"aaaaaaaa-0000-4000-8000-000000000002","session_id":"bbbbbbbb-0000-4000-8000-000000000002"}';
create temporary table watch_baseline as select * from public.watch_announcement_cursor();
select public.check_alert((select cursor_us from watch_baseline) > extract(epoch from now() - interval '1 minute') * 1000000, 'baseline uses server clock');
select public.check_alert((select p.cursor_us = b.cursor_us from watch_baseline b, lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'history suppressed');

reset role;
insert into public.event_announcements(event_id,body,request_id,created_at) values
 ('eeeeeeee-0000-4000-8000-000000000002','Self',gen_random_uuid(),clock_timestamp());
set local role authenticated;
select public.check_alert((select p.cursor_us = b.cursor_us from watch_baseline b, lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'self posts suppressed');
reset role;
insert into public.event_announcements(event_id,body,request_id,created_at) values
 ('eeeeeeee-0000-4000-8000-000000000001','New',gen_random_uuid(),clock_timestamp()),
 ('eeeeeeee-0000-4000-8000-000000000001','Newer',gen_random_uuid(),clock_timestamp());
set local role authenticated;
create temporary table watch_update as select p.* from watch_baseline b, lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p;
select public.check_alert((select u.cursor_us > b.cursor_us and u.generation = b.generation from watch_update u,watch_baseline b), 'new joined announcements coalesce');
select public.check_alert((select p.cursor_us = u.cursor_us from watch_baseline b,watch_update u,lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'lost reply retries same window');
select public.check_alert((select p.cursor_us = u.cursor_us from watch_update u,lateral public.watch_announcement_cursor(u.generation,u.cursor_us) p), 'acknowledged updates do not advance again');
delete from public.event_members where event_id='eeeeeeee-0000-4000-8000-000000000001' and user_id=auth.uid();
select public.check_alert((select p.cursor_us = b.cursor_us from watch_baseline b,lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'lost reply after leaving cannot deliver');
reset role;
insert into public.event_members(event_id,user_id,joined_at) values
 ('eeeeeeee-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000002',clock_timestamp());
set local role authenticated;
select public.check_alert((select p.cursor_us = b.cursor_us from watch_baseline b,lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'rejoin excludes previous membership history');

set local request.jwt.claims = '{"sub":"aaaaaaaa-0000-4000-8000-000000000003","session_id":"bbbbbbbb-0000-4000-8000-000000000003"}';
select public.check_alert((select p.generation <> b.generation from watch_baseline b,lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'account switch rebaselines');
select public.check_alert((select p.cursor_us = 1 from public.watch_announcement_cursor() b,lateral public.watch_announcement_cursor(b.generation,1) p), 'public directory visibility grants no alert');
set local request.jwt.claims = '{"sub":"aaaaaaaa-0000-4000-8000-000000000002","session_id":"bbbbbbbb-0000-4000-8000-000000000004"}';
select public.check_alert((select p.generation <> b.generation from watch_baseline b,lateral public.watch_announcement_cursor(b.generation,b.cursor_us) p), 'same user new login rebaselines');
select public.check_alert(not has_function_privilege('anon','public.watch_announcement_cursor(uuid,bigint)','execute'), 'anonymous execution denied');
rollback;
