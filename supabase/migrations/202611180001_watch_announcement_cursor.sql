-- Read-only cursor for the connected Wear companion. Existing table RLS applies to every read.
begin;
create or replace function public.watch_announcement_cursor(
  p_generation uuid default null, p_after bigint default 0
) returns table(generation uuid, cursor_us bigint)
language plpgsql stable security invoker set search_path = '' as $$
declare
  me uuid := auth.uid();
  session_id text := nullif(auth.jwt()->>'session_id', '');
  session_generation uuid;
  latest timestamptz;
begin
  if me is null or session_id is null then
    raise exception 'An authenticated session is required' using errcode = '28000';
  end if;
  -- A per-session pseudonym. Neither an account ID nor a usable auth/session token leaves the phone.
  session_generation := md5('watch-alerts-v1/' || me::text || '/' || session_id)::uuid;
  if p_generation is distinct from session_generation or p_after is null or p_after <= 0 then
    return query select session_generation,
      (extract(epoch from statement_timestamp()) * 1000000)::bigint;
    return;
  end if;

  select max(a.created_at) into latest
    from public.event_members m
    join public.events e on e.id = m.event_id
    join public.event_announcements a on a.event_id = m.event_id
    where m.user_id = me and e.host_id <> me and a.created_at >= m.joined_at
      and a.created_at > timestamptz 'epoch'
        + (p_after / 1000000) * interval '1 second'
        + (p_after % 1000000) * interval '1 microsecond';
  return query select session_generation, coalesce((extract(epoch from latest) * 1000000)::bigint, p_after);
end;
$$;
revoke all on function public.watch_announcement_cursor(uuid,bigint) from public, anon, authenticated;
grant execute on function public.watch_announcement_cursor(uuid,bigint) to authenticated;
commit;
