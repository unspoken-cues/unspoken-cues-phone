-- Disposable database: load tests/bootstrap.sql and schema.sql first.
-- psql -X -v ON_ERROR_STOP=1 -f supabase/tests/encrypted_event_chat.sql
begin;
\set host 90000000-0000-0000-0000-000000000001
\set alice 90000000-0000-0000-0000-000000000002
\set bob 90000000-0000-0000-0000-000000000003
\set outsider 90000000-0000-0000-0000-000000000004
\set event 91000000-0000-0000-0000-000000000001
\set other 91000000-0000-0000-0000-000000000002
\set msg 92000000-0000-0000-0000-000000000001
create function pg_temp.check_ok(condition boolean, label text) returns void language plpgsql as $$
begin if condition is distinct from true then raise exception 'FAILED: %', label; end if; end;
$$;
create function pg_temp.expect_error(statement text, expected text default '42501')
returns void language plpgsql as $$
begin
  begin execute statement; exception when others then
    if sqlstate = expected then return; else raise; end if;
  end;
  raise exception 'FAILED: expected % for %',expected,statement;
end;
$$;
create function pg_temp.login(uid uuid) returns void language sql as $$
  select set_config('request.jwt.claims',jsonb_build_object('sub',uid)::text,true)::void;
$$;
insert into auth.users(id) values (:'host'),(:'alice'),(:'bob'),(:'outsider');
update public.profiles set is_public=true, private_mode=false,show_status=true,status='GREEN'
  where id in (:'host',:'alice',:'bob',:'outsider');
insert into public.events(id,host_id,name,join_code) values
  (:'event',:'host','Private chat test','CHAT01'), (:'other',:'host','Other event','CHAT02');
insert into public.event_members(event_id,user_id) values
  (:'event',:'alice'),(:'event',:'bob'),(:'other',:'outsider');
set local role anon;
select pg_temp.expect_error(format('select public.chat_register_key(%L,%L)','k1.anon.public',:'alice'));
select pg_temp.expect_error(format('select * from public.chat_messages(%L,auth.uid())',:'msg'));
set local role authenticated;
-- Preserve all three supported RSVP paths: public discovery, private join code and subsequent
-- RSVP updates. A held membership must continue to refer to the same event and person.
select pg_temp.login(:'outsider');
select pg_temp.expect_error(format('insert into public.event_members(event_id,user_id) values (%L,%L)',:'event',:'outsider'));
select pg_temp.expect_error(format('update public.event_members set event_id=%L where event_id=%L and user_id=auth.uid()',:'event',:'other'));
reset role;
update public.events set is_public=true where id=:'other';
set local role authenticated;
select pg_temp.login(:'bob');
insert into public.event_members(event_id,user_id,rsvp) values (:'other',:'bob','going')
  on conflict(event_id,user_id) do update set event_id=excluded.event_id,user_id=excluded.user_id,rsvp=excluded.rsvp;
delete from public.event_members where event_id=:'other' and user_id=auth.uid();
select pg_temp.login(:'outsider');
select public.join_event_by_code('CHAT01');
insert into public.event_members(event_id,user_id,rsvp) values (:'event',:'outsider','maybe')
  on conflict(event_id,user_id) do update set event_id=excluded.event_id,user_id=excluded.user_id,rsvp=excluded.rsvp;
select pg_temp.check_ok((select rsvp='maybe' from public.event_members where user_id=auth.uid() and event_id=:'event'),
  'Code-joined private member can change RSVP');
delete from public.event_members where event_id=:'event' and user_id=auth.uid();
select pg_temp.login(:'alice');
select pg_temp.check_ok((select count(*)=2 and bool_and(peer_id in (:'bob',:'host')) from public.chat_targets(:'event',auth.uid())),
  'Targets are co-attendees and the host only');
select pg_temp.expect_error('select * from public.chat_device_keys');
select pg_temp.expect_error('select * from public.event_chat_threads');
select pg_temp.expect_error('select * from public.event_chat_messages');
select pg_temp.expect_error(format('select * from public.chat_visible_peer(%L,%L)',:'event',:'bob'));
select pg_temp.expect_error(format('select public.chat_request(%L,%L,auth.uid())',:'event',:'bob'));
select public.chat_register_key('k1.alice.public',:'alice');
select public.chat_register_key('k1.alice.public',:'alice');
select pg_temp.expect_error(format('select public.chat_register_key(%L,%L)','k1.alice.replacement',:'alice'),'22023');
select pg_temp.expect_error(format('select public.chat_request(%L,%L,auth.uid())',:'event',:'alice'));
select pg_temp.expect_error(format('select public.chat_request(%L,%L,auth.uid())',:'event',:'outsider'));
select pg_temp.expect_error(format('select public.chat_request(%L,%L,auth.uid())',:'other',:'outsider'));
select public.chat_request(:'event',:'bob',auth.uid());
select public.chat_request(:'event',:'bob',auth.uid());
select thread_id as thread from public.chat_threads(:'event',auth.uid()) \gset
select pg_temp.check_ok((select count(*)=1 and bool_and(peer_key is null and state='pending' and not incoming)
  from public.chat_threads(:'event',auth.uid())),'One outgoing request before recipient enrolls');
select pg_temp.expect_error(format('select public.chat_respond(%L,true,auth.uid())',:'thread'));
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.login(:'bob');
select pg_temp.check_ok((select incoming and peer_key='k1.alice.public' from public.chat_threads(:'event',auth.uid())),
  'Recipient sees request and sender public identity');
select pg_temp.expect_error(format('select public.chat_respond(%L,true,auth.uid())',:'thread'));
select pg_temp.expect_error(format('select public.chat_register_key(%L,%L)','k1.alice.public',:'alice'));
select public.chat_register_key('k1.bob.public',:'bob');
select public.chat_request(:'event',:'alice',auth.uid());
select pg_temp.check_ok((select incoming and state='pending' from public.chat_threads(:'event',auth.uid())),
  'Crossed requests are not implicit consent');
select public.chat_respond(:'thread',true,auth.uid());
select public.chat_respond(:'thread',true,auth.uid());
select pg_temp.expect_error(format('select public.chat_respond(%L,false,auth.uid())',:'thread'));
-- Old-account UI and retry jobs are rejected consistently, including when both accounts are eligible.
select pg_temp.expect_error(format('select public.chat_request(%L,%L,%L)',:'event',:'alice',:'alice'));
select pg_temp.expect_error(format('select public.chat_respond(%L,true,%L)',:'thread',:'alice'));
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,%L)',:'thread',:'msg','m1.stale.cipher',:'alice'));
select pg_temp.expect_error(format('select public.chat_block(%L,%L)',:'alice',:'alice'));
select pg_temp.expect_error(format('select * from public.chat_threads(%L,%L)',:'event',:'alice'));
select pg_temp.expect_error(format('select * from public.chat_targets(%L,%L)',:'event',:'alice'));
select pg_temp.expect_error(format('select * from public.chat_messages(%L,%L)',:'thread',:'alice'));
select public.chat_send(:'thread',:'msg','m1.cipher.origin',auth.uid());
select public.chat_send(:'thread',:'msg','m1.cipher.retry',auth.uid());
select pg_temp.login(:'alice');
select pg_temp.check_ok((select count(*)=1 and bool_and(sender_id=:'bob' and body='m1.cipher.origin')
  from public.chat_messages(:'thread',auth.uid())),'Both participants can read, retry delivers original exactly once');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.hijack'),'22023');
select pg_temp.expect_error(format('select public.chat_send(%L,gen_random_uuid(),%L,auth.uid())',:'thread','plaintext body'),'23514');
select pg_temp.expect_error(format('select public.chat_send(%L,gen_random_uuid(),%L,auth.uid())',:'thread','m1.'||repeat('x',41000)),'23514');
select pg_temp.login(:'host');
select pg_temp.check_ok(not exists(select from public.chat_threads(:'event',auth.uid())) and
  not exists(select from public.chat_messages(:'thread',auth.uid())),'Host has no special access to attendee conversations');
select pg_temp.expect_error(format('select public.chat_respond(%L,true,auth.uid())',:'thread'));
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.login(:'outsider');
select pg_temp.check_ok(not exists(select from public.chat_threads(:'event',auth.uid())) and
  not exists(select from public.chat_messages(:'thread',auth.uid())),'External account cannot list threads/history');
select pg_temp.login(:'alice');
select public.chat_request(:'event',:'host',auth.uid());
select thread_id as host_thread from public.chat_threads(:'event',auth.uid()) where peer_id=:'host' \gset
select pg_temp.login(:'host');
select public.chat_respond(:'host_thread',false,auth.uid());
select pg_temp.login(:'alice');
select public.chat_request(:'event',:'host',auth.uid());
select pg_temp.check_ok((select state='declined' from public.chat_threads(:'event',auth.uid()) where thread_id=:'host_thread'),
  'Declines survive retries');

-- Authorization is reevaluated on every list, read, accept and send, including a stale open screen.
select pg_temp.login(:'bob');
select public.set_event_profile(:'event','Bob on holiday',null,'YELLOW');
select pg_temp.login(:'alice');
select pg_temp.check_ok((select peer_name='Bob on holiday' from public.chat_threads(:'event',auth.uid()) where thread_id=:'thread'),
  'Inbox uses event alias');
select pg_temp.login(:'bob');
select public.set_event_profile(:'event',null,null,'RED');
select pg_temp.login(:'alice');
select pg_temp.check_ok(not exists(select from public.chat_messages(:'thread',auth.uid())),'Event cue restricts history');
select pg_temp.check_ok(not exists(select from public.chat_targets(:'event',auth.uid()) where peer_id=:'bob'),'Red hides chat affordance');
select pg_temp.login(:'host');
select pg_temp.check_ok(not exists(select from public.chat_targets(:'event',auth.uid()) where peer_id=:'bob'),'Host has no cue exemption');
select pg_temp.login(:'alice');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.login(:'bob');
select public.set_event_profile(:'event',null,null,null);
select public.set_event_cued_in(:'event',false);
select pg_temp.login(:'alice');
select pg_temp.check_ok(not exists(select from public.chat_threads(:'event',auth.uid()) where thread_id=:'thread'),'Cue out hides chat');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.login(:'bob');
select public.set_event_cued_in(:'event',true);
update public.profiles set private_mode=true where id=auth.uid();
select pg_temp.login(:'alice');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.login(:'bob');
update public.profiles set private_mode=false,show_status=false where id=auth.uid();
select pg_temp.login(:'alice');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.login(:'bob');
update public.profiles set show_status=true where id=auth.uid();
reset role;
update public.events set active=false where id=:'event';
set local role authenticated;
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
reset role;
update public.events set active=true, starts_at=now()-interval '5 hours' where id=:'event';
set local role authenticated;
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
reset role;
update public.events set starts_at=null,ends_at=now()-interval '1 second' where id=:'event';
set local role authenticated;
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
reset role;
update public.events set ends_at=null where id=:'event';

-- Populate one extra page, including ties, to exercise keyset pagination.
insert into public.event_chat_messages(thread_id,message_id,sender_id,body,created_at)
  select :'thread',gen_random_uuid(),:'bob','m1.cipher.page',now()-interval '1 hour' from generate_series(1,100);
set local role authenticated;
select pg_temp.check_ok((select count(*)=100 from public.chat_messages(:'thread',auth.uid())),'Page limited to 100');
select created_at as boundary,message_id as boundary_id from public.chat_messages(:'thread',auth.uid())
  order by created_at desc,message_id desc offset 99 limit 1 \gset
select pg_temp.check_ok((select count(*)=1 from public.chat_messages(:'thread',auth.uid(),:'boundary',:'boundary_id')),
  'Timestamp and UUID page cursor retrieves remaining row');

-- A block immediately closes both directions, and unblocking does not resurrect consent.
select public.chat_block(:'alice',auth.uid());
select pg_temp.check_ok(not exists(select from public.chat_threads(:'event',auth.uid()) where thread_id=:'thread'),
  'Blocker no longer sees peer in inbox');
select pg_temp.login(:'alice');
select pg_temp.check_ok(not exists(select from public.chat_messages(:'thread',auth.uid())),'Blocked peer cannot read');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
select pg_temp.expect_error(format('select public.chat_request(%L,%L,auth.uid())',:'event',:'bob'));
select pg_temp.login(:'bob');
delete from public.blocks where blocker=auth.uid() and blocked=:'alice';
select pg_temp.check_ok((select state='declined' from public.chat_threads(:'event',auth.uid()) where thread_id=:'thread'),
  'Unblock preserves withdrawn consent');
delete from public.event_members where event_id=:'event' and user_id=auth.uid();
select pg_temp.login(:'alice');
select pg_temp.expect_error(format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'msg','m1.cipher'));
reset role;
select pg_temp.check_ok(not exists(select from public.event_chat_messages where thread_id=:'thread'),
  'Leaving deletes event-scoped mail');
insert into public.event_members(event_id,user_id) values (:'event',:'bob');
set local role authenticated;
select public.chat_request(:'event',:'bob',auth.uid());
select pg_temp.check_ok((select state='pending' and thread_id<>:'thread' from public.chat_threads(:'event',auth.uid())
  where peer_id=:'bob'),'Rejoining requires fresh bilateral consent');
rollback;
