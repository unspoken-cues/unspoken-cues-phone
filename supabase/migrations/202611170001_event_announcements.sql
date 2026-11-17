-- Event announcements. Apply after the event-page columns in schema.sql exist.
-- No direct client writes: the RPC supplies server identity, IDs and timestamps.
begin;

create table if not exists public.event_announcements (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.events (id) on delete cascade,
  body text not null,
  created_at timestamptz not null default clock_timestamp(),
  request_id uuid not null,
  unique (event_id, request_id),
  constraint event_announcements_body_check check (
    char_length(body) between 1 and 1000
    and body = btrim(body, E' \t\n\r\f' || chr(11) || U&'\001C\001D\001E\001F\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000')
  )
);
create index if not exists event_announcements_feed_idx
  on public.event_announcements (event_id, created_at desc, id desc);

alter table public.event_announcements enable row level security;
revoke all on public.event_announcements from public, anon, authenticated;
grant select on public.event_announcements to authenticated;

drop policy if exists "Host and joined members read announcements" on public.event_announcements;
create policy "Host and joined members read announcements"
  on public.event_announcements for select to authenticated
  using (
    auth.uid() is not null
    and (
      exists (select 1 from public.events e where e.id = event_id and e.host_id = auth.uid())
      or public.is_event_member(event_id)
    )
  );

create or replace function public.post_event_announcement(p_event_id uuid, p_body text, p_request_id uuid)
returns public.event_announcements
language plpgsql
security definer
set search_path = ''
as $$
declare
  ev public.events;
  saved public.event_announcements;
  clean_body text := btrim(p_body, E' \t\n\r\f' || chr(11) || U&'\001C\001D\001E\001F\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000');
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  -- Serialize posts against host changes, ending/deleting the event and concurrent retries.
  select * into ev from public.events where id = p_event_id for update;
  if not found or ev.host_id <> auth.uid() then
    raise exception 'Only the host can post' using errcode = '42501';
  end if;
  if clean_body is null or char_length(clean_body) not between 1 and 1000 or p_request_id is null then
    raise exception 'Announcement must contain 1–1000 characters and a request ID' using errcode = '22023';
  end if;
  select * into saved from public.event_announcements
    where event_id = p_event_id and request_id = p_request_id;
  if found then
    if saved.body <> clean_body then
      raise exception 'Request ID already used for another announcement' using errcode = '22023';
    end if;
    return saved; -- A lost response can be retried even after the event ends; no new write.
  end if;
  if not ev.active or coalesce(ev.ends_at, ev.starts_at + interval '3 hours') <= clock_timestamp() then
    raise exception 'Event has ended' using errcode = '42501';
  end if;
  insert into public.event_announcements (event_id, body, request_id)
    values (p_event_id, clean_body, p_request_id)
    returning * into saved;
  return saved;
end;
$$;

revoke all on function public.post_event_announcement(uuid, text, uuid) from public, anon, authenticated;
grant execute on function public.post_event_announcement(uuid, text, uuid) to authenticated;
comment on table public.event_announcements is 'Plain-text host updates readable only by the host and current event members.';

commit;
