# Event announcements

Hosts can publish plain-text updates from event details. The host and current joined members can read the latest 50 updates, newest first. Discover visibility alone does not grant access. This feature adds no notifications, realtime subscription, comments, edits or announcement deletion.

## Setup

For a new Supabase project, apply `supabase/schema.sql`. For an existing project that already has event-page columns (`starts_at`, `ends_at`, RSVPs), apply `supabase/migrations/202611170001_event_announcements.sql` once through the normal migration process. Both paths define the same table, policy, grants and function; they are safe to rerun. Deploy the database change before distributing this app version. No additional app credentials, dependencies or profile permissions are needed. No live database migration was performed during implementation.

`public.event_announcements` stores a server-generated UUID, event ID, trimmed body, server timestamp and a client request UUID used only for idempotency. Its event foreign key cascades on event deletion. The feed index covers `(event_id, created_at desc, id desc)`. The API returns no profile data; the UI labels each update “Host” and formats its timestamp in the device's locale and time zone. Body text is rendered literally, including markup-looking text.

## Permissions and timing

| Caller | Read | Publish |
| --- | --- | --- |
| Authenticated current host | Yes, including ended history | Active event before effective end |
| Authenticated current member, including Maybe RSVP | Yes, including ended history | No |
| Authenticated outsider / Discover viewer | No | No |
| Anonymous | No | No |

Authenticated clients have SELECT privilege with RLS and no direct INSERT/UPDATE/DELETE privilege. Only `post_event_announcement(p_event_id, p_body, p_request_id)` can publish. The function checks `auth.uid()` against the current event host, locks the event row, checks active/end time and validates the trimmed body (1–1000 Unicode code points). IDs and timestamps come from database defaults; the API accepts no author, ID or timestamp parameters. Whitespace normalization matches Kotlin's Unicode trim, including vertical tabs and nonbreaking spaces. An explicit `ends_at` is authoritative; otherwise a start implies a three-hour duration. Legacy events without either timestamp remain postable until ended.

The event row lock serializes posting against event changes, deletion and other posts. Retrying a request UUID with the same body returns its original persisted row without another insert. A different body with the same UUID is rejected. A retry may return an already-committed row after the event ends; it cannot create a new one. The app reuses the request UUID after a failed post and replaces it after editing or successful acknowledgement. It never retries automatically. In-flight posting disables editing, posting and refresh; failures retain the draft. Event/account changes dispose pending UI work and clear private state.

This feature relies on existing event membership semantics. The existing `event_members` self-join policy permits a signed-in user who knows an event ID to insert their own membership; announcements do not tighten that pre-existing policy. Leaving removes read access on the next request. Already viewed text cannot be recalled from a person's memory or screenshots. The screen refreshes explicitly and clears displayed rows on a failed reload. It does not detect revocation in the background.

## Verification

JVM coverage checks Unicode limits, blank normalization, host/end-time eligibility, deterministic ordering/deduplication/50-entry bounds, localized timestamps and wire mapping. Fake-backed Compose coverage checks empty/loading/error/retry states, publish acknowledgement, preserved drafts, idempotency-key reuse, pending controls, read-only history, account/event isolation and public outsider gating. Existing event UI tests inject the new fake so they never call the backend.

Run Android checks with the local Android SDK configured in untracked `local.properties`:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.unspokenqueues.ui.screens.EventAnnouncementsTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.unspokenqueues.ui.screens.EventsScreenTest
```

The SQL role suite is `supabase/tests/event_announcements.sql`. It runs authenticated and anonymous roles with JWT claims, tests direct-write bypass attempts, read boundaries, input validation, server timestamps, end times, retries, leaving and cascade deletion, then rolls back its fixtures. On a disposable PostgreSQL database only, load `supabase/tests/bootstrap.sql` (minimal auth/storage stand-ins), then the full schema and role suite. To test upgrades separately, load the previous schema, the forward migration, then the same suite. Never run the bootstrap or fixture suite on a hosted project.

Actual validation on November 17, 2026:

- `assembleDebug`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin` and `lintDebug` passed. All 147 JVM tests passed, including six new announcement tests. Lint reported no errors and no announcement-specific findings (38 existing warnings and two hints). An initial lint analysis crashed internally while sources were being added; its stable rerun passed.
- All 11 announcement Compose tests passed on Pixel_10a / Android 17. After refining the read-only history wording, its affected test passed again in isolation. A populated fake-backed feed screenshot was captured during the passing member-view test.
- The existing 19-test event suite initially had three failures: the new read-only notice made an old substring selector ambiguous, and two FAB selectors targeted its animated text instead of its clickable surface. The copy and selectors were corrected. All three affected tests passed in focused reruns, in addition to the other 16 passing tests from the full run. These are aggregate results across the full run and focused reruns, not a claim of a second complete 19-test run.
- All 32 SQL assertions passed against in-process PostgreSQL 18.6 (PGlite 0.5.16) for both fresh-schema-plus-migration and previous-schema-plus-migration paths. This exercised real role grants, RLS, functions and constraints without Docker or a live backend. A deployed Supabase/PostgREST round trip and concurrent multi-session races were not exercised.

## Interactive debug sample

Debug builds include an app-drawer entry named **Announcements sample**. It opens the actual event details UI using only seeded, in-memory data; no login or database deployment is required. Scroll below Attendees to Announcements, publish a sample update, switch between Host and Member views, refresh, or end the sample event. Reopen the preview to reset it. The sample is excluded from release builds and does not modify a signed-in account or remote database. Its launch component is `com.example.unspokenqueues/.debug.AnnouncementPreviewActivity`.

## Manual two-account check

1. In a development project with the migration applied, sign in as account A, create an active event and publish an update containing leading/trailing whitespace. Confirm the trimmed text and localized timestamp appear only after acknowledgement.
2. As account B, view the public event before joining: no Announcements section should appear. Direct SELECT must return no announcement rows and direct INSERT/RPC publication must fail. Join or RSVP Maybe; refresh event details and confirm history appears without a composer.
3. On A, go offline and post a draft. Confirm the draft and helpful error remain. Reconnect and retry. Simulate a lost response if possible and retry the same request UUID; the database must contain one row.
4. End the event on A (also check a scheduled event whose end time elapsed). Both accounts retain readable history; A has no composer and new direct RPC requests fail. Leave on B and confirm a subsequent SELECT returns no history. Delete the event on A and verify its announcement rows disappear.
5. Check a long multiline update with large system text, light/dark themes and TalkBack. Announcements must remain scrollable, controls have labeled 48dp targets, and errors/success are announced without using cue colors.

## Rollback

Roll back the app first. Export any announcement history that must be retained, then drop `public.post_event_announcement(uuid, text, uuid)` and `public.event_announcements`. Dropping the table removes its data, index and policy. No existing event/member/profile columns or policies need rollback. Restoring the feature requires reapplying the migration and restoring exported history as an administrator.
