-- Isolated PostgreSQL only. A second local session uses dblink and the test DB's local authentication.
-- Load the application schema first, then psql -X -v ON_ERROR_STOP=1 -f this_file.sql.
\set a a0000000-0000-0000-0000-000000000001
\set b a0000000-0000-0000-0000-000000000002
\set e a1000000-0000-0000-0000-000000000001
\set m a2000000-0000-0000-0000-000000000001
create extension if not exists dblink;
create function pg_temp.expect(ok boolean, explanation text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %',explanation; end if; end;
$$;
-- Prove the second statement actually reached a conflicting lock before committing the first.
create function pg_temp.await_contention(other_session integer) returns void language plpgsql as $$
declare deadline timestamptz := clock_timestamp() + interval '5 seconds';
begin
  while not (pg_backend_pid() = any(pg_blocking_pids(other_session))) loop
    if clock_timestamp() >= deadline then raise exception 'Second session did not wait on the first'; end if;
    perform pg_sleep(0.01);
  end loop;
end;
$$;
insert into auth.users(id) values (:'a'),(:'b');
update public.profiles set is_public=true,show_status=true,status='GREEN' where id in (:'a',:'b');
insert into public.events(id,host_id,name,join_code) values (:'e',:'a','Contention test','RACE01');
insert into public.event_members(event_id,user_id) values (:'e',:'b');
select dblink_connect('bob',format('dbname=%L',current_database()));
select pid as second_pid from dblink('bob','select pg_backend_pid()') as x(pid integer) \gset
select dblink_exec('bob','set role authenticated');
select dblink_exec('bob',format('set request.jwt.claims = %L',json_build_object('sub',:'b')::text));
select result from dblink('bob',format('select public.chat_register_key(%L,%L)','k1.bob.public',:'b')) as x(result text);
set role authenticated;
select set_config('request.jwt.claims',json_build_object('sub',:'a')::text,false);
select public.chat_register_key('k1.alice.public',:'a');

begin;
select public.chat_request(:'e',:'b',auth.uid());
select dblink_send_query('bob',format('select public.chat_request(%L,%L,auth.uid())',:'e',:'a'));
select pg_temp.await_contention(:second_pid);
commit;
select result from dblink_get_result('bob') as x(result text);
select result from dblink_get_result('bob') as x(result text);
select pg_temp.expect((select count(*)=1 and bool_and(state='pending' and not incoming)
  from public.chat_threads(:'e',auth.uid())),'crossed simultaneous requests yield a single pending request');
select thread_id as thread from public.chat_threads(:'e',auth.uid()) \gset
select result from dblink('bob',format('select public.chat_respond(%L,true,auth.uid())',:'thread')) as x(result text);

-- Put both send sessions under Alice's identity so the same client UUID represents a retry.
select dblink_exec('bob',format('set request.jwt.claims = %L',json_build_object('sub',:'a')::text));
begin;
select public.chat_send(:'thread',:'m','m1.first.cipher',auth.uid());
select dblink_send_query('bob',format('select public.chat_send(%L,%L,%L,auth.uid())',:'thread',:'m','m1.retry.cipher'));
select pg_temp.await_contention(:second_pid);
commit;
select result from dblink_get_result('bob') as x(result text);
select result from dblink_get_result('bob') as x(result text);
select pg_temp.expect((select count(*)=1 and bool_and(body='m1.first.cipher')
  from public.chat_messages(:'thread',auth.uid())),'overlapping sends preserve first envelope');

-- Hold Bob's block uncommitted while sending from a stale Alice session. The send must observe the block.
select set_config('request.jwt.claims',json_build_object('sub',:'b')::text,false);
begin;
select public.chat_block(:'a',auth.uid());
select dblink_send_query('bob',format('select public.chat_send(%L,gen_random_uuid(),%L,auth.uid())',:'thread','m1.stale.cipher'));
select pg_temp.await_contention(:second_pid);
commit;
select * from dblink_get_result('bob',false) as x(result text);
select pg_temp.expect(dblink_error_message('bob') like '%Conversation unavailable%', 'pending send denied across block');
select dblink_disconnect('bob');
reset role;
select pg_temp.expect((select count(*)=1 from public.event_chat_messages where thread_id=:'thread'),
  'blocked send left no ciphertext');
delete from auth.users where id in (:'a',:'b');
