# Operations, troubleshooting and release handoff

Return to the [handoff index](README.md). These procedures are derived from source; hosted service ownership/settings must be supplied by the team.

## Diagnose by boundary

Start with the symptom's boundary: local UI → repository/SDK → Auth/RLS/schema/storage, or local UI → Data Layer → watch parser/store/UI. Identify the installed APK/environment and account before changing data. Use synthetic examples and redact tokens, recovery URLs, email addresses and request credentials from logs/issues.

| Symptom | First checks | Interpretation/action |
| --- | --- | --- |
| Gradle fails before compilation | Wrapper, Java 25 criteria, Foojay/dependency network, SDK 37 location | Separate toolchain/download failures from application failures; do not downgrade versions independently |
| Kotlin metadata version error | Phone dependency resolution rule and AGP/plugin/catalog alignment | Kotlin artifacts are intentionally pinned to compiler compatibility |
| Empty Supabase URL/key error | Presence/precedence in local.properties or environment; rebuild | Values are compile-time BuildConfig, not runtime settings |
| Sign-in/network timeout | HTTP(S) project URL, device connectivity/DNS, project availability | A Postgres URI is wrong; safe UI hides SDK request detail |
| Account created but cannot enter | Email confirmation configuration/delivery | Signup can succeed without session; confirm then sign in |
| Loading account never finishes | Confirmed session, profile schema/RLS, retry button | Profile read decides onboarding; transient SDK initialization must not be treated as sign-out |
| Reset email never arrives | Project/email provider, rate limit, email template and allowed redirect | Confirmation copy intentionally does not reveal account existence |
| Reset link fails/opens wrong place | Installed app, scheme/host, actual recovery parameters/type, expiry | Never debug by publishing a real link; request a fresh one after checking configuration |
| Edit/cue/privacy appears saved then reverts | Read actual profile after reload; inspect network and SQL permissions | Root writes currently discard failure and have no durable retry |
| Missing card image only | `profiles.card_image_url` column and separate update | Profile save and card-image update are independent |
| Image upload fails | Public bucket exists, signed-in UUID matches first folder, content decodable | Apply/reconcile Storage policies in dev; URL save is a separate operation |
| Old image persists | Returned URL's version query, persisted URL, Coil download/cache | Avatars/cards overwrite fixed paths; cache-busting timestamp should change |
| Private image remains accessible | Bucket public flag and previously shared URL | Expected current behavior; row privacy does not privatize object bytes |
| Binder missing/stale | Own swap rows, partner profile visibility, reopen Binder/Cue | No Realtime; failed loads preserve memory; removal is mutual |
| Unknown/own card error | 32-hex payload/parser, RPC SQLSTATE | P0002 invalid token/profile; 22023 self-swap |
| Unrelated events in Upcoming/Past | Unfiltered `myEvents` plus public-event RLS | Known local implementation issue, not evidence of bad membership rows |
| Event member absent from attendee UI | Member row, profile RLS, cue UI filter | Host membership access does not grant all profiles; Red/Purple filtering happens after read |
| Ended event still accepts direct membership | Actual membership policies | UI and join RPC checks are narrower than direct INSERT; see known issues |
| Event “going” number wrong | Server memberships/trigger vs local estimates, old rows | Trigger is event-driven; bootstrap has no historical backfill; recount in a reviewed migration if needed |
| Watch Waiting for phone | Debug IDs/certs, pairing/Play services, completed profile/onboarding, Sync now | Node connected alone does not prove companion delivery |
| Watch local cue survives Sync now | Same publisher/revision with unchanged phone content | Designed deduplication; change phone content to publish a newer state |
| Watch stale after sign-out/private | Connection and accepted revision, publisher order | Offline cache cannot receive revocation; also tokens are not revoked server-side |
| Watch cannot share | Valid 32-hex token and public profile in last accepted snapshot | Watch has no Auth/Storage/HTTP lookup fallback |

## Useful local diagnostics

From the appropriate repository:

```sh
./gradlew --version
./gradlew signingReport
adb devices -l
adb -s <serial> shell dumpsys package com.example.unspokenqueues
adb -s <phone-serial> shell am start -W -a android.intent.action.VIEW -d 'unspokencues://card/00000000000000000000000000000000' com.example.unspokenqueues
```

The last command uses a synthetic, syntactically valid token only to exercise routing. Confirming it may show invalid-card feedback; do not use real tokens in shared terminal output. It cannot test a valid password recovery session. A browser opening a custom URI is not a public profile website.

For emulator DNS problems, close the device and launch its AVD with explicit DNS if appropriate for the development network:

```sh
"$ANDROID_SDK_ROOT/emulator/emulator" -list-avds
"$ANDROID_SDK_ROOT/emulator/emulator" -avd <avd-name> -dns-server 8.8.8.8,1.1.1.1
```

Set `ANDROID_SDK_ROOT` to the installed SDK if not already configured. The root README also has a Windows example. DNS changes apply to that launch and do not fix wrong backend URLs or rejected credentials.

There is no checked-in crash-reporting/analytics/log aggregation integration or operational dashboard. Android Studio/adb and the owner's Supabase logs/dashboard are the available diagnostic surfaces implied by this code; exact access, retention and alert ownership need transfer.

## Backend changes and recovery

Use the [database change workflow](database.md#schema-change-workflow). Obtain the real environment's backup/restore method, retention and responsible owner. This repo defines neither a backup job nor a tested restore procedure. Take/rehearse a development restore before relying on it for production. Do not replace a live schema merely because a local table is missing; compare functions/policies and deploy a reviewed change.

Keep APK and SQL compatibility in mind: DTO defaults can hide missing read fields, but writes to missing columns or RPCs still fail. Rolling back the app does not undo SQL mutations, public bucket settings or uploaded objects. Delete/leave/swap removal modify real shared data; use designated test accounts during diagnosis.

## Release preparation

Current Gradle release configuration is not a complete publishing pipeline. There is no checked-in release signing setup, deployment workflow or production pairing decision. Phone release optimization is explicitly disabled. Version numbers are still initial values.

Before shipping, resolve and record:

1. Shared phone/watch installed application ID and signing strategy; planned product IDs currently conflict with Data Layer matching.
2. Existing Play listings/package ownership and update compatibility. A new application ID changes installed identity and storage/session location; coordinate migration deliberately.
3. Signing/upload key custody, Play App Signing access, authorized maintainers and recovery path. Transfer privately; never commit keys.
4. Release environment URL/client key, deployed schema/RLS/RPC inventory, Auth confirmation/reset email configuration, public storage expectations and backups.
5. Final versionCode/versionName, release build/signing commands, required store assets/policies and acceptance results on phone plus Wear OS devices.
6. Known-issue disposition, particularly privacy guarantees, membership authorization and best-effort saves; record what is fixed versus accepted.

After those decisions and configuration exist, Gradle tasks such as `bundleRelease`/`assembleRelease` produce artifacts, but an unsigned or untested artifact is not a release. Keep reproducible build inputs and signing instructions in private operational records alongside this public source handoff.

## Team workflow

[CONTRIBUTING.md](../../CONTRIBUTING.md) describes branching/PR review and protected-main expectations; actual remote protection was not verified. CODEOWNERS names `@unspoken-cues/engineering`. The PR template asks for device/emulator and offline/reconnect testing where relevant. No checked-in Actions workflow was found, so do not assume required automated checks currently run. Update these expectations if the team establishes CI or changes ownership.
