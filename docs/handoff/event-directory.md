# Event directory: Cue In / Cue Out

A member can **Cue Out** of one event: they stay a member (RSVP, announcements and the event in their list are unchanged) but leave that event's attendee list and waves. **Cue In** puts them back. Every membership starts cued in, so nothing changes for anyone who never touches the switch.

## Setup and deployment

Apply [202611170003_event_directory.sql](../../supabase/migrations/202611170003_event_directory.sql) after the waves migration (202611170002). It is transactional and repeatable. New databases use [schema.sql](../../supabase/schema.sql), whose section 19 mirrors the migration; section 16 now drops `event_attendees(uuid)` before recreating it so the file stays re-runnable after the return type changed. No new dependencies, buckets or secrets. Not deployed to a live backend.

The app tolerates an unmigrated backend for reads (a missing `cued_in` reads as cued in), but tapping Cue In/Out there shows the usual "Couldn't cue you…" error because the RPC is missing.

## What changed on the server

- `event_members.cued_in boolean not null default true`. Joining by code, RSVP upserts and rejoining after leaving all keep the default; changing an RSVP keeps the current choice.
- `set_event_cued_in(p_event_id, p_cued_in)`: authenticated, SECURITY DEFINER, empty search path. Updates only `cued_in` on the caller's own row (`auth.uid()`); there is no target-user parameter. Not a member, including a host who never joined: `42501`. Null: `22023`. Signed out: `28000`. It takes the same event row lock as `send_event_wave`, so a send cannot race a Cue Out.
- `event_attendees(p_event_id)` returns a new last column `cued_in`. Other attendees never get a cued-out member's row. The host gets it, with `cued_in = false`; the member always gets their own row. Lines changed for this feature carry a `-- directory:` comment.
- `event_wave_participant` requires `cued_in` for members (the host-by-`host_id` branch is unchanged). That single change means a cued-out member is no wave target, cannot send or be sent a wave, their earlier waves drop out of other inboxes, and `event_wave_state` / `set_event_wave_preference` refuse them with `42501`. Cueing back in restores earlier waves (the one-wave-per-pair limit still holds).
- Direct reads of `event_members` return only the caller's own rows (see [hardening](hardening.md)), so no other member, cued in or out, is visible there.

The existing "Members can update own rsvp" policy still lets a member update any column of their own row directly, including `cued_in`. That stays self-only; the RPC is the path the app uses.

## App

`EventRepository.setCuedIn` calls the RPC. `Attendee.cuedIn` comes from `event_attendees`. On the event page, a member (anyone with their own attendee row) sees an **Event directory** card with a one-line explanation and a full-width 52dp **Cue Out** / **Cue In** button with a spoken description. It uses the screen's `working`/`actionError` pattern. While cued out the Waves section is hidden; announcements stay. Attendee rows with `cuedIn = false` show "Cued out" (the host's view, and the member's own row). A host who never joined has no own row and no switch.

## Verify with two accounts

1. Apply the migration. Account A hosts an active event; B and C join. Each sees the others in Attendees, and B sees **Cue Out**.
2. As B, tap **Cue Out**. B's row says "Cued out", the card says only the host sees them, and Waves disappears. RSVP buttons and announcements remain.
3. As C, refresh Attendees: B is gone. B is not in C's wave list, and any wave B sent C is gone from C's inbox. A direct `send_event_wave` to B fails.
4. As A (host), refresh: B is listed with "Cued out". A has no Cue In/Out card.
5. As B, change RSVP to Maybe: still cued out. Tap **Cue In**: C sees B again after refresh, and waves come back.
6. Leave and rejoin as B: B starts cued in.

## Automated verification

- `supabase/tests/event_directory_authorization.sql` (role-based, rolls back): default cued in, anon denied, self-toggle, others (member, host, outsider) cannot change a member's flag, null rejected, RSVP and membership kept, hidden from other attendees and direct table reads, shown and marked for the host, wave target/send/inbox exclusion both ways, RSVP change keeps the flag, rejoin resets it. Run like the waves fixture: `bootstrap.sql`, `schema.sql`, then the fixture.
- JVM: `DtoMappersTest.attendeeRow_carriesCuedOut_andReadsAsCuedInWhenAbsent`.
- Instrumented: `EventsScreenTest.member_canCueOut_andBackIn_whichHidesAndRestoresWaves` and `host_seesCuedOutMembersMarked_andHasNoSwitch`.

Validation on 2026-11-17 (Windows): `assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` passed (158 JVM tests). PGlite 0.3.14 ran the waves, announcements and directory fixtures against a fresh `bootstrap.sql` + `schema.sql` (applied twice) and against the pre-feature `main` schema + this migration (applied twice): all passed. The instrumented tests were compiled, not run.

## Rollback

Roll back clients first; older clients ignore the extra column. Leaving the schema in place is safe. To remove it, in one transaction: drop `set_event_cued_in(uuid, boolean)`; drop and recreate `event_attendees(uuid)` and `event_wave_participant(uuid, uuid)` from section 16 / section 18 (or the 202611170002 migration); then `alter table public.event_members drop column cued_in`. Dropping the column makes every cued-out member visible again.
