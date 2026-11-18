# Event profiles

One account works across every event. A participant (host or member) can optionally set a name, bio and cue for one event. Anything not set falls back to the main profile. With no override, nothing changes. The global cue on the home screen, S.W.A.P. card, binder and watch is always the main profile's cue. Overrides affect only event-scoped views: the attendee list and waves.

## Setup and deployment

Apply [202611170004_event_profiles.sql](../../supabase/migrations/202611170004_event_profiles.sql) after the privacy schema and [quick reactions](quick-reactions.md). It is transactional and repeatable. New databases can use [schema.sql](../../supabase/schema.sql). Section 19 creates the new objects. The changes to `event_attendees` (section 16) and `event_wave_state`/`send_event_wave` (section 18) are made in place there, each marked `-- event profile:`. The migration carries full copies of those three functions, so **whichever event migration is applied last must contain every other migration's changes to them**. No new dependencies, buckets or secrets are needed. These changes have **not** been deployed to a live backend.

An unmigrated backend makes the "Your profile at this event" section say it couldn't load. Waves then use the main cue, and everything else on the event page keeps working.

## Authorization and privacy

`event_profiles` holds one optional row per (event, user): `display_name`, `bio` and `status`, each nullable (null = main profile). It has RLS, no policies, and all privileges revoked from PUBLIC, anon and authenticated. Clients use two authenticated SECURITY DEFINER RPCs with `search_path = ''`:

- `my_event_profile(p_event_id)`: the caller's own override fields. There is no parameter for another user, so nobody can read someone else's raw row.
- `set_event_profile(p_event_id, p_display_name, p_bio, p_status)`: replaces the caller's override. Values are trimmed. A blank or null field clears that field, and clearing all three deletes the row. The bio is limited to 120 characters, the app's main-bio limit (`22023` above that). A name is limited to 80 characters, like the main profile (`23514` above that; see [hardening](hardening.md)).

Both RPCs require `auth.uid()` to be the host or a member (`event_wave_participant`). Otherwise they raise `42501`. `set_event_profile` takes a key-share lock on the caller's membership row, so a concurrent leave cannot slip past. It also locks the caller's profile row, which serializes an event-cue change with `send_event_wave`'s profile locks.

`event_effective_profiles(p_event_id)` is an internal helper with no client execute. It returns the event's participants as `profiles` rows with overrides applied. `event_attendees`, `event_wave_state` and `send_event_wave` read it in place of `profiles`. Every privacy expression on top is unchanged: `is_public`, `private_mode`, the `show_*` toggles, blocks and swaps. As a result:

- Other attendees see the event name, bio and cue. `show_bio`/`show_status` still blank those fields whatever their source.
- The non-host Green/Yellow filter uses the event cue. An event cue of Red or Purple hides the person from non-hosts at that event only.
- Wave eligibility on both sides uses the event cue. Wave targets and the inbox use event names. Wave locks and consent are unchanged.

A membership-delete trigger (`event_members_delete_event_profile`) removes the override, so rejoining starts from the main profile. If a host is also a member and leaves the member row, their override goes too. Deleting the event or the account cascades.

## App

`EventRepository.myEventProfile`/`saveEventProfile` call the RPCs. `EventDetailScreen` loads the override for participants and shows **Your profile at this event** with an **Edit profile for this event** button. The editor (`EventProfileDialog`) shows the main name, bio and cue as fallbacks, with a **Main profile** cue option and **Use main profile** to clear. After a save, the attendee list refreshes. The waves section waits for the override and checks local eligibility against the event cue (`eventProfile?.status ?: status`).

## Verify with two accounts

1. Apply the migration to a development project. A hosts an event; B and C join. As C, open the event: B shows with B's main name and cue.
2. As B, open **Your profile at this event**. Check that the fields show B's main values as fallbacks. Set a name, a bio and Yellow, then save. Refresh as C: B shows the event name, bio and Yellow. B's home cue, card and watch are unchanged, and another event still shows B's main profile.
3. As B, turn off "show bio" and "show status" in the main profile. C sees the event name, but no bio or cue. Turn them back on.
4. As B, set the event cue to Red. C no longer sees B, and A (host) sees B as Red.
5. Set B's main cue to Red and event cue to Green, and turn on Receive waves for B and C. Both can wave each other at this event, and C's inbox and target list show B's event name. Set B's event cue to Purple: B disappears from C's waves.
6. Tap **Use main profile** as B; C sees the main profile again. Set an override, then leave and rejoin as B: the editor starts empty.
7. As C, call `my_event_profile` for the event: it returns only C's own (empty) override. Direct `select` on `event_profiles` fails for both accounts.

## Automated verification

```sh
./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.unspokenqueues.ui.screens.EventProfileTest
```

The role fixture [event_profiles_authorization.sql](../../supabase/tests/event_profiles_authorization.sql) runs the same way as the waves fixture (bootstrap + schema, or baseline + migration, in a disposable database; it rolls back). It covers: fallback, override shown to others, other events unaffected, `show_*` and Private Mode, event Red hidden from non-hosts, validation and clearing, no direct table or helper access, outsider denial, wave eligibility/targets/inbox by event cue and name, leave/rejoin and cascades.

Results on 2026-11-17 in PGlite (PostgreSQL 17.5, in-process, single connection): all three fixtures (event profiles, quick reactions, announcements) passed against fresh `bootstrap.sql` + `schema.sql` (applied twice) and against the pre-feature schema + this migration (applied twice). The fixture fails against the baseline and against a migration without the three function changes. Concurrent lock behavior was reviewed, not executed. Gradle build, 160 JVM tests, androidTest compilation and lint passed. `EventProfileTest` was compiled but not run on a device.

## Rollback

Prefer leaving the additive schema in place while rolling back the app; older clients ignore it. To remove the feature: roll back clients and back up `event_profiles`. Restore the previous `event_attendees`, `event_wave_state` and `send_event_wave` definitions (drop the `-- event profile:` lines and read `public.profiles` again). Then drop the trigger `event_members_delete_event_profile`, the functions `delete_event_profile_on_leave()`, `set_event_profile(uuid,text,text,cue_status)`, `my_event_profile(uuid)` and `event_effective_profiles(uuid)`, and the `event_profiles` table. Restore the functions **before** dropping the helper, or event pages and waves will fail.
