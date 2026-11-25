# Encrypted event conversations

## What attendees can do

Two people at the same active event can open a one-to-one text conversation from the attendee list. The recipient accepts or declines a request before either phone can send text. Both sides can block. The server checks event eligibility and privacy again when accepting or sending. End, leave and block close access according to those checks. The inbox refreshes in the foreground, including messages sent while the recipient was offline.

## Implementation and ownership

1. The backend owner implements a migration, the equivalent schema definitions and transactional authorization tests. Use existing event membership, privacy and block rules. RPCs derive the caller from auth.uid(). Enforce unique pairs and idempotent message IDs. Keep message bodies and private keys out of the database.
2. The encryption owner implements device identity storage, authenticated encryption for both participants, local trust on first use, comparable fingerprints and fail-closed behavior on key changes. Use vetted primitives and bind event, thread, message, sender and recipient identities. Account-specific private keys must survive process restart, remain isolated between accounts and stay outside Android backups.
3. The UI owner integrates a request action and inbox in event details, pending and accepted states, block and decline, conversation history, verification and retry feedback. Keep typed text out of saved-instance state and logs.
4. The integration owner connects the Android client to Supabase and provides an isolated emulator exercise with sample attendees. Verify the actual database and phone paths, record measurements and leave the virtual phone running. Keep shared infrastructure deployments separate from local testing.

## Acceptance checklist

- [ ] Eligible attendee can request, recipient alone can accept, either party can block, third parties and ineligible attendees have no access.
- [ ] Concurrent or retried requests/sends produce one thread/message. Leave, event end, privacy and block changes are enforced on subsequent operations.
- [ ] Both participants can open text. An unrelated key, altered payload or identity context fails to open it. First-seen peer keys are pinned. A changed peer identity cannot silently receive new text.
- [ ] A unique test phrase is absent from the serialized relay payload and persisted server row. History survives phone app restart with the same identity.
- [ ] On Android, request, accept, send, reconnect/reopen, read and block. Record time from sending to display under foreground refresh and target under ten seconds on the local emulator.
- [ ] Build, unit tests, SQL policy tests and relevant Android tests pass. Review failures, fix and rerun the affected checks.

## Security and product limits

One phone identity per account. Device loss needs explicit recovery before trusting a replacement key. The relay can see participants, events, timing and approximate size. Initial key verification uses an independent comparison between participants. The proposed static recipient-key encryption does not provide forward secrecy after private-key compromise. The initial release design includes no remote plaintext backup, push delivery, media or watch messaging. Production rollout requires a reviewed migration and independent review of device identity, protocol integration and recovery.

## Device identity and transport

Android keeps a Tink HPKE X25519 / HKDF-SHA256 / AES-128-GCM keyset and a separate Ed25519 signing keyset per account. An Android Keystore key wraps both private keysets in the app’s no-backup directory. Each message has an encrypted copy for each participant. The sender signs both copies and the length-prefixed context containing protocol version, event, thread, message ID, sender and recipient. A recipient verifies the signature before displaying text. Each phone pins the first peer bundle and shows both SHA-256 fingerprints under **Conversation options → Verify device keys**. Attendees can compare those codes in person.

The server derives authority from the authenticated JWT. Each chat RPC also checks the account the phone expected to be acting as, so an account switch during a request cannot enroll a key or perform an action for the next user. Registration is immutable. Losing the enrolled installation currently disables new chat for that account. Self-service enrollment on a replacement phone and recovery of old messages are outside this implementation.

The relay admits discoverable, cued-in participants with Green or Yellow effective status. Existing event privacy and block settings apply to both people, including the host. A first membership in a private event requires the join-code RPC. The recipient can decline a request. A block withdraws consent across events. Leaving removes that event’s thread and messages. Once an event ends the relay no longer serves its history. A three-second foreground refresh fetches up to 100 messages. Older pages load on demand. Re-sending after a lost receipt reuses the client message ID. No message preview goes through push or the watch.

The scheme protects message content against a compromised relay database. An active relay could substitute identities on first contact until the participants compare fingerprints. Compromise of a phone, screenshots by a participant, and identifying metadata remain outside that guarantee. Treat this as a device-tested pilot pending independent security review. [Tink hybrid encryption](https://developers.google.com/tink/hybrid) and [digital signatures](https://developers.google.com/tink/digital-signature).

## Run the phone lab

The debug lab uses two local accounts, Alex and Blair, in an isolated PostgreSQL database and PostgREST server bound to the Mac’s loopback interface. The debug app uses the same chat screen, repository, message format, Keystore and SQL as the signed-in app. The lab account chooser substitutes for sign-in. In the signed-in app, the entry point is the event attendee list and **Event inbox**.

From the phone repo, with Docker running and a Pixel emulator already booted, run the following. The optional application ID suffix installs the lab alongside the ordinary phone app.

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./tools/chat-lab/start.sh
./gradlew -PchatLab=true assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
./tools/chat-lab/attach.sh emulator-5554
```

To start a fresh conversation, choose **Use as Alex → Chat with Blair → Request chat**. Back out to **Switch attendee**, choose **Blair → Open event inbox → Alex → Accept**, then type a message. Use the same chooser to read and reply as Alex. Blocking applies to both lab events and persists across restarts. The two sample users share a virtual phone, with distinct account-scoped device keys. The lab’s short-lived test credentials are installed through ADB into the isolated debug app. Running the start and attach scripts again renews them. Stop the local services with `docker stop uc-event-chat-lab-rest uc-event-chat-lab-db`.

Run the complete Android suite and the live delivery benchmark with the installed test APK. The benchmark uses a separate event, a new UUID for every message, and a real elapsed-time clock. It reads the relay’s stored representation, checks both participants’ delivery behavior and measures time until the recipient’s accessibility tree contains the new text.

```sh
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell am instrument -w -r \
  -e chatBench true com.example.unspokenqueues.chatlab.test/androidx.test.runner.AndroidJUnitRunner
```

Run the SQL checks against a disposable local database initialized with `supabase/tests/bootstrap.sql` followed by `supabase/schema.sql`. Apply the feature tests with `psql -X -v ON_ERROR_STOP=1`. The concurrency suite uses the PostgreSQL dblink extension and its own isolated test rows. For an existing project, the deployable incremental change is `supabase/migrations/202611250001_encrypted_event_chat.sql`.
