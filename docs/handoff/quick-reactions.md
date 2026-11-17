# Event waves

Waves are an optional, event-scoped hello between consenting participants. They do not create S.W.A.P. connections, chat, free text, public counts, push notifications, vibration or realtime subscriptions.

## Setup and deployment

Review and apply [202611170002_quick_reactions.sql](../../supabase/migrations/202611170002_quick_reactions.sql) to an existing development database before exercising the real repository. The migration is transactional and repeatable. New databases can use [schema.sql](../../supabase/schema.sql), whose final wave definitions match the migration. Existing `events`, `event_members`, `profiles`, `auth.users`, `auth.uid()`, and the Supabase `authenticated`/`anon` roles must exist. No new dependencies, Storage buckets, secrets or background services are required. The Android client uses its existing Supabase project and signed-in session. These changes have **not** been deployed to a live backend.

An unmigrated backend reports a recoverable waves load error; event, RSVP, QR and attendee actions remain available. Build with the existing SDK and Gradle setup. Tests and the sample preview use fakes and require no Supabase credentials.

## Authorization and privacy

`event_wave_preferences` stores one OFF-by-default preference per event/account. `event_waves` stores one directed sender/recipient tuple per event, with a server timestamp. Both tables have RLS, no client policies, and explicit privilege revocations for PUBLIC, anon and authenticated. Clients can only execute these authenticated, fixed-search-path SECURITY DEFINER RPCs:

- `event_wave_state(p_event_id)`: the caller's setting, actionable public Green/Yellow recipients and own newest-first inbox. No arbitrary recipient inbox or participant preference list is exposed. Opted-out or otherwise ineligible callers receive no targets/history.
- `set_event_wave_preference(p_event_id, p_receive_waves)`: explicit caller consent only. It returns the acknowledged private state.
- `send_event_wave(p_event_id, p_recipient_id)`: derives sender from `auth.uid()`; rejects self, outsiders, opted-out people, private profiles, Red/Purple cues, inactive events and elapsed schedules. The host is a participant and has no privacy exception.

An event without an explicit end expires three hours after its start. Undated events remain usable until the host ends them. Server checks use the current database clock after acquiring locks. A unique primary key and `ON CONFLICT DO NOTHING` make repeated/concurrent retries persist one wave and return its original timestamp.

Event locks serialize consent/sends. Membership locks protect against a concurrent departure; ordered profile locks serialize cue/visibility changes with sending. Snapshot reads keep the caller's profile/membership/consent stable and filter other profiles at the statement that reads their names. A membership-delete trigger resets consent so rejoining requires a fresh opt-in. Deleting an event/account cascades dependent data. Privacy changes never create a public history feed.

Opting out immediately hides local history/actions while saving; only acknowledgement confirms the setting. Stored history remains private while off and may reappear after both participants become eligible again. Keeping the unique tuple prevents opt-out/rejoin from resetting the limit. Profile/cue changes made locally also hide history before the best-effort profile save reaches the server. The screen expires at the event end without waiting for refresh. Empty/unknown account, visibility and eligibility fail closed.

Waves update on initial load and **Refresh waves**; there is no background delivery. Another person's changed privacy is applied by the next server read; send always rechecks it. A failed send discards stale history and refreshes eligibility before offering a retry. A failed refresh leaves no previously loaded inbox visible. State and coroutine scopes are keyed by account/event/profile/cue/schedule so navigation or account changes cannot reuse private results.

## Verify with two accounts

1. Apply the migration to a development project. Account A hosts an active event; B joins. With public Green/Yellow profiles and both preferences OFF, neither can wave.
2. Enable Receive waves as A only: B must still be unavailable. Enable it as B. Both should see an eligible participant row, including the host even if the host never joined their own event.
3. As B, tap **Wave** once. Confirm **Sending…** becomes disabled **Sent** only after the server acknowledgement. Refresh; it remains Sent. Repeating the same RPC returns the same timestamp and does not add a wave.
4. As A, use **Refresh waves**. **Waves for you** contains B's current display name and timestamp, newest first. B and an unrelated account cannot read A's inbox. No binder entry or notification is created.
5. Switch either account private, Red or Purple; refresh both. Targets and matching inbox history must disappear. A host must also be denied when directly calling `send_event_wave`. Restore public Yellow/Green and verify eligibility.
6. Disable Receive waves. Confirm history hides while pending, stops incoming/outgoing sends, and survives reopening as OFF. Disconnect networking while saving/sending: show an unconfirmed/error state, then retry with networking restored. Do not assume a failed response means the server did not commit.
7. Leave and rejoin as B: consent must reset to OFF. Re-enable it and verify the original pair still has only one wave. End/expire the event and verify sends fail, including direct RPC calls. Delete the event and verify wave/preferences cascade.

## Automated verification

Run:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.unspokenqueues.ui.screens.EventReactionsTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.unspokenqueues.ui.screens.EventsScreenTest
```

The role fixture [quick_reactions_authorization.sql](../../supabase/tests/quick_reactions_authorization.sql) uses authenticated/anonymous roles, not admin-only assertions. Run it after the canonical schema or an upgraded baseline in a disposable PostgreSQL database with the usual Supabase Auth/Storage contracts. An optional [minimal bootstrap](../../supabase/tests/quick_reactions_bootstrap.sql) exists for an empty isolated PostgreSQL/PGlite database; never apply the bootstrap to a deployed project. With psql, use `-v ON_ERROR_STOP=1`. The fixture seeds test accounts in a transaction and rolls back. It covers direct table bypass, sender spoofing, helper privilege, consent/defaults, self/outsider/host rules, own-inbox isolation, current privacy/cues, expiry, duplicate requests, departure/rejoin and cascades.

Actual validation during implementation:

- Debug build, full JVM suite (including five reaction serialization/model tests), Android test compilation and lint: passed. One intermediate lint invocation hit an internal Kotlin FIR analysis error while sources were being edited; the stable-source rerun passed without suppressions.
- Parent review ran the role fixture in in-process PostgreSQL 18.6 via PGlite against both the complete fresh schema and an upgrade from the unchanged baseline: passed. No Docker container or live backend was used. PGlite uses a single connection, so true simultaneous transaction races were reviewed through lock/constraint design rather than executed here.
- After announcements merged into main, the shared event screen, fake repositories, and debug sample activities were integrated. Debug build, all 152 combined JVM tests, Android test compilation, lint, and both SQL authorization suites against the combined schema passed.
- A final direct AndroidJUnitRunner pass against the integrated build passed all three selected checks: populated waves preview, member announcement preview, and public outsider announcement gating.
- Connected Pixel_10a emulator (Android 17): all 13 `EventReactionsTest` tests passed, covering loading, empty/self exclusion, consent, send acknowledgement, denial/privacy refresh, retry, local cue/visibility, expiry and account cancellation. Existing event instrumentation tests compiled with a fake reaction repository; this focused run selected only the reaction class.

## Sample preview

Debug builds include `ReactionPreviewActivity` with a labeled local sample event, seeded private inbox, preference changes and acknowledged wave buttons. It never initializes Supabase. It is absent from release sources/manifests.

```sh
adb shell am start -n com.example.unspokenqueues/.ReactionPreviewActivity
```

Scroll past attendees to **Waves**. Sample data starts OFF, like production. Turn on Receive waves to show the seeded inbox and participant rows, send to Jordan or Harper, and refresh. You can reopen the sample from the **Waves preview** debug app icon. All sample changes are in memory and reset when the Activity is recreated.

## Rollback and limitations

Prefer leaving the additive schema in place while rolling back the app; older clients ignore it. To remove the feature, first roll back clients, back up data, then transactionally drop `event_members_reset_wave_consent`, the three public RPCs, `reset_event_wave_consent_on_leave()`, `event_wave_participant(uuid,uuid)`, and the two wave tables. Removing tables permanently deletes consent/history; do not execute rollback as part of normal setup. No event/profile/RSVP policies need changes.

Targets/inbox are fetched as an event snapshot without pagination; suitable for the current small event experience. Event-level serialization favors privacy and atomicity over high-throughput bulk reactions. Errors, deadlock victims or lost responses can be retried safely. There is no automatic live inbox refresh or read receipt. Authenticated role fixtures validate the SQL contract in isolation; a final two-account development-project verification checks the hosted PostgREST/JWT integration before deployment.

Waves inherits the existing `event_members` joining rules. The baseline schema permits an authenticated user to self-insert membership using a known event ID, including private events. This feature does not change that authorization boundary; its separate opt-in, profile-visibility and cue checks still apply to every wave.
