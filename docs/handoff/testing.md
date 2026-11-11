# Testing and acceptance

Return to the [handoff index](README.md). This is a coverage map and runbook. Runtime suites were **not executed for the documentation-only change**; test presence is not a passing result.

## Commands and reports

From each repository independently:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
```

The first command compiles/tests/lints without a device. The second needs a connected target of the correct device type. On Windows use `.\gradlew.bat`. With both a phone and watch visible, select the intended target before invoking instrumentation (for example `ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest`). Do not assume a phone test suite can run meaningfully on a watch.

Useful report paths after execution:

- JVM HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.
- JVM XML: `app/build/test-results/testDebugUnitTest/`.
- Lint: `app/build/reports/lint-results-debug.html` and associated report files.
- Connected UI reports: `app/build/reports/androidTests/connected/`; results under `app/build/outputs/androidTest-results/connected/`.

Focused examples from the phone root:

```sh
./gradlew testDebugUnitTest --tests 'com.example.unspokenqueues.model.EventScheduleTest'
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.unspokenqueues.ui.screens.PasswordRecoveryScreenTest
```

Watch JVM tests are in package `com.unspokencues.wear` despite the debug installed ID `com.example.unspokenqueues`. Check the configured Java daemon criteria and SDK platform if Gradle fails before test discovery.

## Existing phone JVM coverage

Source root: [`app/src/test/java/com/example/unspokenqueues`](../../app/src/test/java/com/example/unspokenqueues).

| Test files | What they exercise |
| --- | --- |
| `data/AuthHelpersTest.kt` | Friendly Auth error mapping without leaking raw details |
| `data/PasswordRecoveryTest.kt` | Dedicated callback URI recognition and rejection cases |
| `data/AvatarHelpersTest.kt`, `data/CardImageTest.kt` | Paths/URLs, dimensions/sampling and card-image model/serialization behavior |
| `data/DtoMappersTest.kt`, `data/EventDtoTest.kt` | Snake_case decoding, model conversion/defaults, onboarding exclusion, timestamps/RSVPs and profile joins |
| `data/EventHelpersTest.kt`, `data/SwapHelpersTest.kt` | Event/swap payload and error helper behavior |
| `model/OnboardingRulesTest.kt` | Required profile inputs and every app phase |
| `model/EventRulesTest.kt` | Host/member cue filtering and join-code rules/generation |
| `model/EventScheduleTest.kt` | Time phases/fallbacks, sorting, tags, date/time-zone formatting, RSVP wire values |
| `model/BinderTest.kt`, `model/CardLinkTest.kt` | Counts/search/cue combinations; token/URI parsing |
| `model/WatchSnapshotTest.kt` | Clear payload, private/invalid token withholding, account-transition loading gates |
| `ui/components/QrCodeTest.kt`, `ui/components/ScannerStateTest.kt` | QR module round trips and scanner state priority |
| `ExampleUnitTest.kt` | Starter example; not meaningful integration coverage |

## Existing device coverage

Phone source root: [`app/src/androidTest/java/com/example/unspokenqueues`](../../app/src/androidTest/java/com/example/unspokenqueues).

| Test file | Scope |
| --- | --- |
| `ui/screens/AuthFlowTest.kt` | Navigation, form validation/callbacks, safe errors and forgot-password request states |
| `ui/screens/PasswordRecoveryScreenTest.kt` | Readiness/error gates, password mismatch, successful/failed update UI |
| `ui/screens/ProfileSetupWizardTest.kt` | Wizard order/validation, emitted fields, pending list text, back navigation and profile-save failure |
| `ui/screens/TabTutorialScreenTest.kt` | Pager/navigation and completion callbacks |
| `ui/screens/SettingsScreenTest.kt` | Tutorial replay, completion/skip/back and repeat replay |
| `ui/screens/QrScreenTest.kt` | Card/share/scan/link UI behavior |
| `ui/screens/EventsScreenTest.kt` | Fake repository-backed listing, joins, hosting, detail, visibility and event actions |
| `ui/components/QrScannerTest.kt` | Scanner presentation/permission/error actions without requiring live camera frames |
| `ui/components/QrCodeScanTest.kt` | Generated code rendering decoded through ML Kit |
| `ExampleInstrumentedTest.kt` | Starter package/context check |

Isolated callbacks and fake repositories make these tests deterministic. They do not establish that deployed RLS, Auth emails, internet access, Storage policies or Data Layer delivery work. The QR scan test does not replace pointing a real phone camera at a small round watch.

Watch tests: `WatchStateTest.kt` checks selection/lock, snapshot validation, sharing, clear/account behavior and publisher ordering; `QrTest.kt` checks QR encoding/decoding; device `WatchAppTest.kt` checks UI semantics/guide, gestures, intentional unlock and sharing/timeout. New phone updates are covered at the model level; actual receiver/store delivery and UI transitions after transport need integration checks. See the companion's own [testing guide](../../../unspoken-cues-wearos/docs/handoff/testing.md).

## Integration acceptance matrix

Use two normal test accounts A/B plus an unrelated viewer C, a configured development backend, a camera-capable phone and paired Wear OS device. Record actual results; expected limits below are not assumed passes.

| Area | Exercise | Observe |
| --- | --- | --- |
| New account | Signup with confirmation enabled/disabled as appropriate | Correct email/session state; profile trigger; wizard then tutorial |
| Returning account | Relaunch, rotate, background for camera, temporary offline load | No unintended logout; no onboarding reset; inspect stale/mock profile limitation |
| Recovery | Real reset request, valid/expired/malformed link, cancel, successful update, login with new password | Recovery gates; template/redirect/session validity; no raw credentials/errors shown |
| Cue | Each cue; reopen after online/offline change | Phone, server and watch agree when successful; expose failed-save limitation |
| Profile | Edit text/lists/privacy, gallery/camera, avatar/card image, remove photo | Stored fields after reload; separate card-image write; public object remains after URL removal |
| Cards | Scan A from B; repeat; own code; invalid code; pasted link; cold/warm deep link | Mutual row, idempotency, consent dialog for launched link, parser errors |
| Binder | Search/filter, B removes A, reopen A binder | Both lose connection after refresh; old token can recreate connection |
| Events | Private/public creation, time/place/cover/tags, collision simulation if practical, typed/scanned join, Going/Maybe | Correct rows, membership and trigger count; host is not automatically a member |
| Event permissions | C reads public event, joins by code/RSVP; B tries host actions; private attendee profiles and Red/Purple | Actual RLS versus UI behavior; document gaps rather than assuming desired policy |
| Event lifecycle | Start/end boundary, host End, Leave, Delete | UI classification; FK cascade; active-only join RPC limit |
| Images | Large/rotated photo, unsupported image, offline upload, foreign-folder write | Size/rotation behavior, safe errors, ownership enforcement |
| Scanner | Deny, repeated denial, allow in Settings, missing/in-use camera, retry, close/rotate | Correct fallback and resource cleanup; typed input remains possible |
| Watch | All cues, disconnected cache, local override, unchanged retry, content change, private, sign-out, A→B, reinstall | Ordering and clearing only after accepted delivery; no server credentials on watch |
| Accessibility | Light/dark, large text, TalkBack, animations off; small round watch | Named cues/symbols, reachable controls, QR fits, system navigation preserved |

For server authorization, use authenticated client sessions or controlled role/JWT tests in a development database. An admin SQL query bypassing RLS is not an access-control test. Negative tests that intentionally raise SQL errors need separate transactions/savepoints.

## Coverage gaps to prioritize

There is no checked-in backend integration test harness, migration validation pipeline, end-to-end recovery-email automation, paired-device transport test or CI workflow. Existing event UI tests use fake lists with an empty Discover response and do not exercise the real discovery query or RSVP writes. Settings tests cover replay, not theme persistence or transport. Root application lifecycle/account races and best-effort write failures need device/integration checks beyond pure helpers. Validate storage cleanup/privacy behavior and derived counts with real policies. Do not infer production readiness from a successful `assembleDebug`.
