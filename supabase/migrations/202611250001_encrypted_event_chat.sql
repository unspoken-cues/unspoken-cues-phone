begin;

-- Chat relies on membership as its authorization boundary. A private event requires the join-code
-- RPC for the first RSVP. Existing members still use the phone's ordinary RSVP upsert.
drop policy if exists "Users can join as themselves" on public.event_members;
create policy "Users can join as themselves" on public.event_members for insert to authenticated
  with check (user_id = auth.uid() and exists (
    select from public.events e where e.id=event_members.event_id
      and (e.is_public or e.host_id=auth.uid() or public.is_event_member(e.id))
  ));

-- Preserve the semantics of joining and leaving (including cleanup) for clients that update an RSVP.
create or replace function public.event_membership_identity_fixed()
returns trigger language plpgsql set search_path = '' as $$
begin
  if (old.event_id,old.user_id) is distinct from (new.event_id,new.user_id) then
    raise exception 'Membership identity is fixed' using errcode='42501';
  end if;
  return new;
end;
$$;
drop trigger if exists event_membership_identity_fixed on public.event_members;
create trigger event_membership_identity_fixed before update of event_id,user_id on public.event_members
  for each row execute function public.event_membership_identity_fixed();
revoke all on function public.event_membership_identity_fixed() from public, anon, authenticated;

-- The application creates these public bundles on-device. The relay cannot replace
-- a registered identity via the app RPC, including after reinstalling on a new phone.
create table if not exists public.chat_device_keys (
  user_id uuid primary key references auth.users(id) on delete cascade,
  public_key text not null check (octet_length(public_key) between 8 and 4096
    and public_key ~ '^k1\.[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$')
);
create table if not exists public.event_chat_threads (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.events(id) on delete cascade,
  requester uuid not null references auth.users(id) on delete cascade,
  recipient uuid not null references auth.users(id) on delete cascade,
  state text not null default 'pending' check (state in ('pending','accepted','declined')),
  updated_at timestamptz not null default clock_timestamp(),
  check (requester <> recipient)
);
create unique index if not exists event_chat_pair_idx on public.event_chat_threads
  (event_id, least(requester,recipient), greatest(requester,recipient));
create index if not exists event_chat_requested_idx on public.event_chat_threads (requester,event_id);
create index if not exists event_chat_received_idx on public.event_chat_threads (recipient,event_id);
create table if not exists public.event_chat_messages (
  thread_id uuid not null references public.event_chat_threads(id) on delete cascade,
  message_id uuid not null,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (octet_length(body) between 8 and 40960
    and body ~ '^m1\.[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$'),
  created_at timestamptz not null default clock_timestamp(),
  primary key (thread_id, message_id)
);
create index if not exists event_chat_history_idx on public.event_chat_messages
  (thread_id, created_at desc, message_id desc);
alter table public.chat_device_keys enable row level security;
alter table public.event_chat_threads enable row level security;
alter table public.event_chat_messages enable row level security;
revoke all on public.chat_device_keys, public.event_chat_threads, public.event_chat_messages
  from public, anon, authenticated;

-- A delayed request from a previous account must never act on a new session. The UUID supplied
-- by the phone is a consistency check only. The authenticated JWT determines the actual caller.
create or replace function public.chat_caller(p_expected_user uuid)
returns uuid language plpgsql stable set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or me is distinct from p_expected_user then
    raise exception 'Chat account changed' using errcode = '42501';
  end if;
  return me;
end;
$$;
revoke all on function public.chat_caller(uuid) from public, anon, authenticated;

create or replace function public.chat_register_key(p_key text,p_expected_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user);
begin
  insert into public.chat_device_keys(user_id,public_key) values (me,p_key)
    on conflict (user_id) do nothing;
  if (select k.public_key from public.chat_device_keys k where k.user_id = me) is distinct from p_key then
    raise exception 'Chat identity already registered on another installation' using errcode = '22023';
  end if;
end;
$$;

-- Restrict the eligibility check to the two profiles. The host also needs a public, discoverable cue.
-- Each user's effective per-event name and cue match the event directory overrides.
create or replace function public.chat_visible_peer(p_event uuid, p_user uuid)
returns table (display_name text) language sql stable set search_path = '' as $$
  select coalesce(ep.display_name,p.display_name)
  from public.events e join public.profiles p on p.id = p_user
  left join public.event_profiles ep on ep.event_id = e.id and ep.user_id = p.id
  left join public.event_members m on m.event_id = e.id and m.user_id = p.id
  where e.id = p_event and e.active and (public.event_end(e) is null or public.event_end(e) > clock_timestamp())
    and (m.cued_in or (e.host_id = p.id and m.user_id is null))
    and p.is_public and not p.private_mode and p.show_status and coalesce(ep.status,p.status) in ('GREEN','YELLOW');
$$;

-- Event administrators get the same request targets as ordinary members.
create or replace function public.chat_targets(p_event uuid,p_expected_user uuid)
returns table (peer_id uuid,peer_name text) language plpgsql stable security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user);
begin
  return query select candidates.id,v.display_name from (
    select e.host_id as id from public.events e where e.id=p_event
    union select m.user_id from public.event_members m where m.event_id=p_event
  ) candidates
  cross join lateral public.chat_visible_peer(p_event,candidates.id) v
  where me <> candidates.id and exists (select from public.chat_visible_peer(p_event,me))
    and not public.blocked_between(me,candidates.id)
  order by v.display_name,candidates.id;
end;
$$;

-- Lock the same event, memberships and profiles used by the existing cue/profile/privacy writers.
-- Block insertion also locks the pair's profiles before invalidating their conversations.
create or replace function public.chat_check_pair(p_event uuid, me uuid, peer uuid)
returns void language plpgsql set search_path = '' as $$
begin
  if me is null or peer is null or me = peer then
    raise exception 'Conversation unavailable' using errcode = '42501';
  end if;
  perform 1 from public.events e where e.id=p_event for share;
  perform 1 from public.event_members m where m.event_id=p_event and m.user_id in (me,peer)
    order by m.user_id for share;
  perform 1 from public.profiles p where p.id in (me,peer) order by p.id for share;
  if not exists (select from public.chat_visible_peer(p_event,me))
    or not exists (select from public.chat_visible_peer(p_event,peer))
    or public.blocked_between(me,peer) then
    raise exception 'Conversation unavailable' using errcode = '42501';
  end if;
end;
$$;

create or replace function public.chat_request(p_event uuid,p_peer uuid,p_expected_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user);
begin
  perform public.chat_check_pair(p_event,me,p_peer);
  if not exists (select from public.chat_device_keys k where k.user_id=me) then
    raise exception 'Register chat identity first' using errcode = '42501';
  end if;
  -- A crossed request preserves the first recipient's decision. A retry cannot reopen a decline.
  insert into public.event_chat_threads(event_id,requester,recipient) values (p_event,me,p_peer)
    on conflict do nothing;
end;
$$;

-- Read routing IDs first, lock eligibility, then lock the thread. No writer may change its endpoints.
create or replace function public.chat_lock_thread(p_thread uuid,me uuid)
returns public.event_chat_threads language plpgsql set search_path = '' as $$
declare t public.event_chat_threads;
begin
  select * into t from public.event_chat_threads c where c.id=p_thread and me in (c.requester,c.recipient);
  if not found then raise exception 'Conversation unavailable' using errcode = '42501'; end if;
  perform public.chat_check_pair(t.event_id,me,case me when t.requester then t.recipient else t.requester end);
  select * into t from public.event_chat_threads c where c.id=p_thread for update;
  if not found then raise exception 'Conversation unavailable' using errcode = '42501'; end if;
  return t;
end;
$$;

create or replace function public.chat_respond(p_thread uuid,p_accept boolean,p_expected_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user); t public.event_chat_threads := public.chat_lock_thread(p_thread,me);
  choice text := case when p_accept then 'accepted' else 'declined' end;
begin
  if me <> t.recipient or p_accept is null or t.state not in ('pending',choice) then
    raise exception 'Chat request unavailable' using errcode = '42501';
  end if;
  if p_accept and not exists (select from public.chat_device_keys k where k.user_id=me) then
    raise exception 'Register chat identity first' using errcode = '42501';
  end if;
  if t.state='pending' then
    update public.event_chat_threads set state=choice, updated_at=clock_timestamp() where id=p_thread;
  end if;
end;
$$;

create or replace function public.chat_threads(p_event uuid,p_expected_user uuid)
returns table (thread_id uuid,peer_id uuid,peer_name text,state text,incoming boolean,peer_key text)
language plpgsql stable security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user);
begin
  return query select t.id,peer.id,visible.display_name,t.state,t.recipient=me,k.public_key
  from public.event_chat_threads t
  cross join lateral (select case t.requester when me then t.recipient else t.requester end as id) peer
  cross join lateral public.chat_visible_peer(t.event_id,peer.id) visible
  left join public.chat_device_keys k on k.user_id=peer.id
  where t.event_id=p_event and me in (t.requester,t.recipient)
    and exists (select from public.chat_visible_peer(p_event,me))
    and not public.blocked_between(me, peer.id)
  order by t.updated_at desc,t.id;
end;
$$;

create or replace function public.chat_send(p_thread uuid,p_id uuid,p_body text,p_expected_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user); t public.event_chat_threads := public.chat_lock_thread(p_thread,me);
begin
  if t.state <> 'accepted' then raise exception 'Accepted chat required' using errcode = '42501'; end if;
  -- The first envelope wins even if a reconnect has re-encrypted the same client message.
  insert into public.event_chat_messages(thread_id,message_id,sender_id,body) values (p_thread,p_id,me,p_body)
    on conflict (thread_id,message_id) do nothing;
  if found then
    update public.event_chat_threads set updated_at=clock_timestamp() where id=p_thread;
  elsif not exists (select from public.event_chat_messages m
      where m.thread_id=p_thread and m.message_id=p_id and m.sender_id=me) then
    raise exception 'Message ID unavailable' using errcode = '22023';
  end if;
end;
$$;

-- Exclusive keyset cursor in server order. Both nulls request the most recent page.
create or replace function public.chat_messages(p_thread uuid,p_expected_user uuid,p_before timestamptz default null,p_before_id uuid default null)
returns table (message_id uuid,sender_id uuid,body text,created_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
declare me uuid := public.chat_caller(p_expected_user);
begin
  return query select m.message_id,m.sender_id,m.body,m.created_at
  from public.event_chat_threads t join public.event_chat_messages m on m.thread_id=t.id
  where t.id=p_thread and t.state='accepted' and me in (t.requester,t.recipient)
    and not public.blocked_between(t.requester,t.recipient)
    and exists (select from public.chat_visible_peer(t.event_id,t.requester))
    and exists (select from public.chat_visible_peer(t.event_id,t.recipient))
    and ((p_before is null and p_before_id is null) or (p_before is not null and p_before_id is not null
        and (m.created_at,m.message_id) < (p_before,p_before_id)))
  order by m.created_at desc,m.message_id desc limit 100;
end;
$$;

create or replace function public.chat_block(p_peer uuid,p_expected_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  perform public.chat_caller(p_expected_user);
  perform public.block_user(p_peer);
end;
$$;

-- Leaving drops this event's conversation and mail. Blocking revokes consent across all events,
-- including after an unblock. The pair lock serializes concurrent sends/requests with a block.
create or replace function public.chat_on_leave()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  delete from public.event_chat_threads t where t.event_id=old.event_id and old.user_id in (t.requester,t.recipient);
  return old;
end;
$$;
create or replace function public.chat_on_block()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.profiles p where p.id in (new.blocker,new.blocked) order by p.id for update;
  update public.event_chat_threads t set state='declined',updated_at=clock_timestamp()
    where least(t.requester,t.recipient)=least(new.blocker,new.blocked)
      and greatest(t.requester,t.recipient)=greatest(new.blocker,new.blocked) and t.state<>'declined';
  return new;
end;
$$;
drop trigger if exists event_chat_on_leave on public.event_members;
create trigger event_chat_on_leave after delete on public.event_members for each row execute function public.chat_on_leave();
drop trigger if exists event_chat_on_block on public.blocks;
create trigger event_chat_on_block after insert on public.blocks for each row execute function public.chat_on_block();

revoke all on function public.chat_visible_peer(uuid,uuid), public.chat_check_pair(uuid,uuid,uuid),
  public.chat_lock_thread(uuid,uuid), public.chat_on_leave(), public.chat_on_block() from public, anon, authenticated;
revoke all on function public.chat_block(uuid,uuid), public.chat_targets(uuid,uuid), public.chat_register_key(text,uuid), public.chat_request(uuid,uuid,uuid), public.chat_respond(uuid,boolean,uuid),
  public.chat_threads(uuid,uuid), public.chat_send(uuid,uuid,text,uuid), public.chat_messages(uuid,uuid,timestamptz,uuid) from public, anon;
grant execute on function public.chat_block(uuid,uuid), public.chat_targets(uuid,uuid), public.chat_register_key(text,uuid), public.chat_request(uuid,uuid,uuid), public.chat_respond(uuid,boolean,uuid),
  public.chat_threads(uuid,uuid), public.chat_send(uuid,uuid,text,uuid), public.chat_messages(uuid,uuid,timestamptz,uuid) to authenticated;

commit;
