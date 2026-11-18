# Blocked attendee warning

Before a user joins or RSVPs to an event, the app checks whether someone **they** blocked is going. If so it shows "Jordan, who you blocked, is going to this event." (several names are listed together) with **Join anyway** and **Cancel**. Cancel changes nothing; Join anyway joins exactly as before. The warning is advisory: if the check itself fails (for example offline), the join goes ahead without it.

It runs on every path that makes a non-member join: the first RSVP on the event page (Going or Maybe), the one-tap RSVP in Discover, and Join with code (typed, or scanned QR). Changing an existing RSVP does not check, and neither does joining by code an event already in the user's list (hosted or joined).

## Who is listed, and why

Only people the caller blocked (`blocks.blocker = auth.uid()`). People who blocked the caller are never listed: that would reveal someone else's block.

Only people the caller could learn about anyway, so the warning leaks nothing a second, unblocked account could not already see:

- the **host**, whatever their settings, because the event already shows them as `host_name`;
- a **member** only if an ordinary non-host member would see them in `event_attendees()`: not in Private Mode, profile public, cue for this event Green or Yellow (per-event cue overrides apply, through `event_effective_profiles`), and cued in.

Names are the **main** profile `display_name`, the name on the blocker's blocked list (`my_blocked_users()`). A per-event alias is never returned, so the warning cannot tie an alias to a blocked person.

The caller is not a member yet, so membership is not required. By event id, the server answers only for events the caller can already read (public, hosted or joined, mirroring the `events` select policies); any other id returns nothing, so it is no way to test whether an event exists. By join code it looks up an active event the way `join_event_by_code` does (trimmed, any case) without joining; an unknown or ended code returns nothing. Anyone holding a code could join and look, so the code path reveals nothing more.

## Setup and deployment

Apply [202611170006_blocked_attendee_warning.sql](../../supabase/migrations/202611170006_blocked_attendee_warning.sql) after the event profiles migration (202611170004). It is transactional and repeatable. New databases use [schema.sql](../../supabase/schema.sql), whose section 22 mirrors the migration. No new tables, columns, dependencies, buckets or secrets. Not deployed to a live backend.

On an unmigrated backend the RPCs are missing, the check fails, and joining works exactly as before with no warning.

## What changed on the server

- `blocked_attendees(p_event_id uuid) returns table (user_id uuid, display_name text)`
- `blocked_attendees_by_code(code text) returns table (user_id uuid, display_name text)`

Both are authenticated only (anon and public revoked), SECURITY DEFINER, `search_path = ''`, raise `28000` when signed out, and return rows sorted by name. Both call `blocked_attendees_for(p_event_id, p_me)`, which applies the rules above; it is not SECURITY DEFINER and no client role may execute it.

## App

`EventRepository.blockedAttendees(eventId)` and `blockedAttendeesByCode(code)` return the names (decoded with the existing `BlockedUserRow`). `blockedAttendeeWarning(names)` in `model/Models.kt` builds the sentence. In `EventsScreens.kt`, `BlockedAttendeeDialog` shows it; the RSVP paths use the screen's `working`/`actionError` pattern and join by code uses `joining`/`joinError`. The join dialog hides while the warning is up and comes back on Cancel.

## Verify with accounts

1. Apply the migration. Account A hosts a public, active event. B (public, Green) joins it. C has blocked B (for example from B's card in the binder) and has not joined.
2. As C, open the event from Discover and tap **Going**: the warning names B by their main name. **Cancel**: C is still not going. Tap again, **Join anyway**: C is going.
3. As C, leave. As B, set a per-event cue of Red, or turn on Private Mode, or Cue Out: C's RSVP shows no warning. Undo it.
4. As C, use **Join with code** with A's code: the warning names B. Scan the QR instead: same.
5. As B (who did not block C), join any event C attends: no warning about C, and nothing tells B that C blocked them.
6. As C, block A, then RSVP to another of A's events: A is named even with Private Mode on.

## Automated verification

- `supabase/tests/blocked_attendee_warning.sql` (role-based, rolls back): visible blocked member listed by main name (never the alias); people who blocked the caller not listed; Private Mode, non-public, Red, Purple via event override, and cued-out members not listed; an event override to Green and cueing back in make members count; blocked host listed despite Private Mode; unreadable or unknown event by id returns nothing; private event by code works; ended or unknown code returns nothing; members can ask by id; the check never joins; anon denied; the internal helper is not callable.
- JVM: `EventRulesTest.blockedWarning_namesEveryone`.
- Instrumented: `BlockedAttendeeWarningTest` (first RSVP warns by name, Cancel does not RSVP, Join anyway does, no warning when nobody is blocked, a failed check goes ahead, a member changing RSVP is not checked, Discover RSVP warns, join by code warns and Cancel keeps the dialog).

## Rollback

Roll back clients first, or not at all: older clients never call these functions, and newer clients join normally when they are missing. To remove the server part, in one transaction: `drop function public.blocked_attendees(uuid); drop function public.blocked_attendees_by_code(text); drop function public.blocked_attendees_for(uuid, uuid);`. Nothing else depends on them and no data changes.
