# Phone ↔ Wear OS contract

Return to the [handoff index](README.md). Despite the bidirectional heading, **implemented transport is phone-to-watch only**. The watch does not send cue choices back to the phone or Supabase.

Phone sources: [WatchSync.kt](../../app/src/main/java/com/example/unspokenqueues/data/WatchSync.kt), [WatchSnapshot.kt](../../app/src/main/java/com/example/unspokenqueues/model/WatchSnapshot.kt), root wiring in [MainActivity.kt](../../app/src/main/java/com/example/unspokenqueues/MainActivity.kt). Receiver/storage details are also documented in the [companion handoff](../../../unspoken-cues-wearos/docs/handoff/README.md).

## Transport and identity

Google Play services Wearable DataClient stores an urgent DataItem at the exact path `/unspoken-cues/state`. This is a latest-state snapshot, not a command/event history. There is no app-level acknowledgment, message endpoint, capability discovery or backend token on the watch.

Current debug IDs both resolve to `com.example.unspokenqueues`. Both APKs must have matching signing certificates. The watch namespace is still `com.unspokencues.wear`; use the installed package ID for adb and the namespace for the activity class. Current release IDs differ (phone `com.example.unspokenqueues`, watch `com.unspokencues.wear`), and older product notes want phone `com.unspokencues.phone`. A coordinated shared application ID and signing decision is a release blocker. Changing only namespace does not solve transport identity.

## Schema 1 DataMap

| Key | DataMap type | Meaning |
| --- | --- | --- |
| `schema` | Int | Exactly 1; unsupported versions ignored |
| `active` | Boolean | Account state available; false is an explicit clear |
| `status` | String | Exact GREEN/YELLOW/RED/PURPLE when active; empty on clear |
| `name` | String | Display name, limited to 80 characters |
| `token` | String | Lowercase 32-hex card token only when public and valid; otherwise empty |
| `public` | Boolean | Profile sharing flag; false on clear |
| `revision` | Long | Positive monotonic state revision within a publisher generation |
| `publisher` | String | UUID for this phone installation's publishing generation |

Only cue/name/card token/visibility travel. No user email, account UUID, session, password, preferences, boundaries, photos or Supabase credential is sent. The token is still a sharing capability, not non-sensitive decoration.

## Snapshot gating

Before the session has a definite answer, the root passes null and publishes nothing. A definite signed-out session produces an inactive empty snapshot. An authenticated account publishes active data only after its profile has loaded for `sessionUserId` and onboarding is complete. Missing/invalid token withholds sharing but does not withhold the cue.

During profile loading, if the confirmed account differs from persisted `watch_owner`, an empty snapshot clears the previous identity. Same-account loading passes null to preserve an offline watch cache. `watch_owner` records successful DataClient submission of an active snapshot, not proof that the watch received it. Publishing never intentionally uses a previous account/mock profile while waiting for session/profile confirmation.

## Publisher revisions and retries

`companionRequest` compares active/status/name/token/public to saved fields in phone `watch_publisher` preferences. On changed content (or first publish) revision becomes `max(currentTimeMillis, previousRevision + 1)`; unchanged retries reuse the revision. A persistent random publisher UUID makes a reset/reinstall a new generation. All publisher preferences are excluded from cloud backup and device transfer so a cloned/restored publisher does not reuse the ordering identity.

`WatchSync` is a composable effect keyed by context, snapshot, ownerId and manual retry counter. For non-null snapshots it submits immediately, checks connected nodes, then waits 30 seconds and repeats. It reports SYNCING, then CONNECTED if any node exists, otherwise DISCONNECTED. Failures report disconnected. Manual Sync now restarts the effect. Its lifetime is the root composition; this is not a guaranteed background worker or perpetual service. The platform can carry the last submitted DataItem after reconnection.

Unchanged retries and Sync now reuse revisions. Therefore they do not override an intentional local watch cue. A later **content-changing** phone snapshot overrides it. There is no “force resend as new revision” action in the UI.

## Receiver and ordering

Watch `WatchSyncService` receives DATA_CHANGED through its manifest service. It handles changed DataEvents, not deletion events. `receiveDataItem` requires exact path, schema 1 and a valid active cue; inactive snapshots clear cue/profile. Public profiles with no valid token produce no shareable profile, while private profiles can retain a name without sharing.

The activity also reads existing DataItems every 30 seconds while STARTED to recover data missed by the listener. It releases the returned DataItem buffer. Both paths feed `WatchStore.receive`.

`SnapshotOrder.accepts` requires a nonblank publisher, positive revision and publisher not retired. For the current generation, only a strictly greater revision is accepted. A new unretired generation is accepted even if its revision is lower, and the previous publisher is retired. Retired publishers cannot later resurrect identity. This handles a single phone installation/reset; simultaneous multiple publishers have no explicit owner arbitration and should be tested before supporting that scenario.

`WatchStore` synchronizes access through a shared monitor, persists snapshot fields together and records local receive time. Accepted data always locks the cue and closes obsolete QR/selection screens. `saveLocal` accepts a UI write only if its revision and publisher still match current persisted data, preventing an old UI callback from overwriting a newer phone snapshot. Duplicate phone snapshots are ignored, preserving local cue choices and their receive timestamp.

## Watch UI and disconnected behavior

The watch initially shows Waiting for phone. Options leads to Share, Cue guide or deliberate unlock/selection. A local cue selection is permitted only while unlocked, persists immediately and relocks; it does not update phone/server. Back/Cancel relocks. A profile QR works independently of cue lock and closes after 30 seconds, Close or Back. The app is a normal Wear OS activity; it does not replace a watch face or force always-on illumination.

Disconnected, the watch displays the last accepted snapshot/local choice. A privacy change or sign-out can clear it only after a newer snapshot arrives. Known tokens remain usable by the swap RPC even for a private profile; clearing a watch cache is not server-side token revocation. See [privacy limits](known-issues.md).

## Changing the contract

Update phone snapshot construction, DataMap writer, watch parser/model/store and both apps' tests together. Treat field types, cue names, exact path, clear semantics and revision comparison as compatibility requirements. A schema bump currently makes old receivers silently ignore data; plan mixed-version behavior before deploying one. Test equal/old revisions, new/retired generations, reinstallation, sign-out, account switch, private/public state, malformed data, reconnection and local watch overrides.
