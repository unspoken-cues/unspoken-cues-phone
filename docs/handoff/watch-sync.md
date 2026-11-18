# Phone and Wear OS cue sync

The paired watch can select any of the four global cues. The signed-in phone owns the Supabase write. Both devices display the committed global cue after the phone returns its receipt. Event-specific cues use their existing event profile.

## Using it

Pair the devices, sign in on the phone and finish onboarding. On the watch, open **Options → Color locked → Unlock color** and select a cue. The watch saves its pending choice before sending it. A phone selection or **Settings → Watch → Sync now** also enters the same reconciliation path. Sync now reads the latest retained watch request, checks the profile and sends a fresh authoritative snapshot even for an unchanged cue.

The phone connection pill reports connected Data Layer nodes. Settings reports profile confirmation or retry status separately. A successful DataClient submission may still be awaiting delivery to the other device.

## Transport

Google Play services retains the latest urgent DataItem per path and originating node. Requests use `/unspoken-cues/cue-request`. The schema is integer 1. Each request has a random account-generation UUID, a positive long `baseRevision`, a random `operation` UUID, a persistent random watch `writer` UUID, a positive long `sequence` and the exact `status` name GREEN, YELLOW, RED or PURPLE. Unknown schemas, paths, missing metadata, invalid types and unknown cue names cannot initiate a phone write.

The phone answers on the existing `/unspoken-cues/state` path. Older receivers can read the original fields.

| Snapshot keys | Meaning |
| --- | --- |
| `schema`, `active`, `status` | Integer 1, account availability flag and global cue. A signed-out snapshot clears identity. |
| `name`, `token`, `public` | At most 80 characters of display name, valid 32-hex public card token or empty string, sharing flag. Private Mode withholds the token. |
| `publisher`, `revision` | Installation UUID and monotonic positive transport revision. The existing publisher identity and counter survive upgrades. |
| `generation`, `cueRevision` | Random identity for this signed-in account session and positive logical cue revision. Inactive snapshots use an empty string and zero. |
| `receipt`, `receiptWriter`, `receiptBase`, `receiptSequence` | Last accepted operation UUID, writer UUID, originating base and sequence. A phone selection clears these fields. Empty receipt fields are empty strings and zero longs. |

Request metadata contains no profile identifier, credentials or sharing capability. Only the existing privacy-filtered snapshot carries profile sharing data. A disconnected watch retains its previous snapshot until the next delivery. Clearing the cache does not revoke an already shared card token.

## Conflicts and recovery

The phone serializes cue writes and authoritative reads. The watch generation must match the signed-in account and the base revision must still be current. The same watch can replace an earlier choice based on the same revision until a phone choice intervenes. This allows rapid offline selections to converge. A fresh explicit phone selection advances the cue revision even when its value is unchanged.

The phone journals the in-flight operation before writing and requests the updated Supabase row. The receipt follows confirmed persistence. If a response is lost during a process restart, the phone checks the profile value against its journal. A duplicate, retired generation or stale base produces the current authoritative snapshot. Account changes rotate the generation even when both profiles look identical. All phone journal, sharing and publisher state is in the backup-excluded `watch_publisher` preferences.

The activity retries every 30 seconds while its sync effect is active. A background Wear Data Layer delivery schedules a network-constrained Android job, which waits for Auth initialization, refreshes the signed-in profile and retries transient failures with backoff. Android can defer that job while the phone is asleep. The foreground and Sync now paths also read retained DataItems after a restart or disconnection. The watch can display a pending cue while disconnected and confirms it only on a matching receipt. A watch paired with an older phone retains its original local-only selection behavior.

## Installation and verification

Both APKs must use the same installed application ID and signing certificate on their respective devices. Both debug variants use `com.example.unspokenqueues`. The default release application IDs differ. The companion supports a coordinated release build using its documented `companionApplicationId` Gradle property and the phone's signing certificate. Play listing and signing choices require release-owner coordination.

Run `bash gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` in both repositories. Phone instrumentation class `com.example.unspokenqueues.data.PhoneCueSyncTest` exercises all cues, same-value Sync now, network loss/restart, duplicate and racing edits, changing accounts and privacy. The Wear counterpart is `com.unspokencues.wear.WatchCueSyncTest`. These tests inject the backend and Data Layer boundary. For end-to-end release verification, pair two Google Play services devices signed with the same certificate and repeat offline selection, same-value phone edits and account switching against an approved test profile.
