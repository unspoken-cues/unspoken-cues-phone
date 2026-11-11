# Source map and maintenance guide

Return to the [handoff index](README.md). All paths below are relative to the phone repository. Kotlin source root is [`app/src/main/java/com/example/unspokenqueues`](../../app/src/main/java/com/example/unspokenqueues).

## Application and data

| Source | Responsibility and connections |
| --- | --- |
| `MainActivity.kt` | Android lifecycle/theme/deep links; `Tab`, `Overlay`, `EventSaver`, `UnspokenCuesApp`, tabs scaffold; central owner for profile/cue/session/token/binder/watch state |
| `data/SupabaseClientProvider.kt` | Lazy singleton; checks BuildConfig; installs Auth/Postgrest/Storage |
| `data/AuthRepository.kt` | Email/password operations, recovery validation, session flow, safe error mapping |
| `data/PasswordRecovery.kt` | Dedicated reset redirect constant and strict callback route test |
| `data/ProfileRepository.kt` | Own-profile read/upsert and isolated status/card image/privacy/onboarding writes |
| `data/ProfileDto.kt` | ProfileRow, ProfileUpsert, snake_case serialization, model/status mapping |
| `data/SwapRepository.kt` | SwapRow, card projection/error mapping, stable token RPC, mutual swap RPC, binder lookup/delete |
| `data/EventRepository.kt` | EventDraft/insert/member payloads, list/detail/create/join/RSVP/end/leave/delete and error mapping |
| `data/EventDto.kt` | Event/member row decoding, timestamp conversion, RSVP mapping and member-to-profile join |
| `data/AvatarRepository.kt` | Avatar/card image upload; separate row URL writes occur in root callbacks |
| `data/AvatarHelpers.kt` | Bucket/path/URL constants, scaledSize and sampling calculations |
| `data/ImageCompression.kt` | Android content decoding, downsampling, EXIF rotation, JPEG encoding |
| `data/EventCoverRepository.kt` | Public versioned event-cover upload and URL helpers |
| `data/WatchSync.kt` | Compose-driven Data Layer publishing/retry, persistent publisher revision generation, Task-to-coroutine adapter |

## Domain rules

| Source | Key definitions |
| --- | --- |
| `model/Models.kt` | CueStatus/WatchConnection/ThemeMode, Profile and picture precedence, Event/Attendee/Rsvp, attendee visibility, join-code rules, CollectedCard/SwapResult, card URI/token parser, MockData |
| `model/Onboarding.kt` | Required name/preference-or-boundary rules; AppPhase selection |
| `model/EventSchedule.kt` | Upcoming/Live/Past, missing-end fallback, sorting, tag normalization/filtering, date labels and counts |
| `model/Binder.kt` | Per-cue counts and combined text/cue search |
| `model/WatchSnapshot.kt` | Minimal companion payload, token validation/privacy, same-account versus changed-account loading behavior |

## Screens and reusable UI

| Source | Responsibility |
| --- | --- |
| `ui/screens/Screens.kt` | Shared ScreenColumn; auth forms/loading; cue hero/choices; profile/edit/image picker/list inputs; My Card/QR/share/paste; link confirmation and swap reveal; PersonDetail; Settings/theme/sync/tutorial |
| `ui/screens/PasswordRecoveryScreens.kt` | Reset request and new-password readiness/validation/completion states |
| `ui/screens/ProfileSetupWizard.kt` | Six onboarding steps, saved draft, deferred avatar upload and blocking initial profile save |
| `ui/screens/TabTutorial.kt` | Five-tab pager, next/back/skip/done, exhaustive per-Tab tutorial text |
| `ui/screens/EventsScreens.kt` | Upcoming/Discover/Past, joins, quick RSVP, hosting form/date pickers/cover upload, detail/attendees/RSVP and end/leave/delete |
| `ui/screens/BinderScreen.kt` | Own-card strip, collection search/count filters, two-column cards and detail removal |
| `ui/components/Components.kt` | ScreenTitle, SectionCard, Dot, WatchPill, Chip, Avatar/Coil, Halo, outlined-button contrast wrapper, reduced-motion pulse and loading skeletons |
| `ui/components/EventComponents.kt` | Segmented tabs, tag controls, stable cover fallback, metadata/host lines, badge logic, list/feature cards |
| `ui/components/SwapCard.kt` | Front/back card, full-screen QR, ZXing module encoder and Canvas renderer |
| `ui/components/QrScanner.kt` | Scanner state rules, permission/settings/retry UI, CameraX lifecycle, ML Kit once-only QR analyzer |
| `ui/theme/Color.kt` | Fixed cue and light/dark neutral palettes |
| `ui/theme/Theme.kt` | Material 3 color schemes; dynamic color intentionally disabled |
| `ui/theme/Type.kt` | Bundled display/body font families and typography |
| `ui/theme/Motion.kt` | Durations/easing and remove-animation detection |
| `ui/theme/Ambience.kt` | Current cue radial wash behind tabs |

Much of the phone currently lives in two large files (`MainActivity.kt`, `Screens.kt`). Start by tracing a UI callback to its root/repository and the corresponding SQL policy; filenames alone do not define strict layers. A future split should preserve saved-state keys, callback behavior, cancellation and account boundaries.

## Build, platform and resources

| Path | Purpose |
| --- | --- |
| `app/build.gradle.kts` | Application IDs/SDKs, BuildConfig injection, Compose/desugaring, release optimization setting, Kotlin dependency pinning and dependencies |
| `gradle/libs.versions.toml` | Phone version catalog and plugin aliases |
| `build.gradle.kts`, `settings.gradle.kts` | Plugin versions/aliases, repositories, Foojay resolver, single app inclusion |
| `gradle.properties`, `gradle/gradle-daemon-jvm.properties` | Daemon memory/configuration-cache and Java criteria |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Version/checksum-pinned wrapper; use it for reproducible builds |
| `app/src/main/AndroidManifest.xml` | Internet/camera, optional camera hardware, launch and two deep links, FileProvider |
| `app/src/main/res/xml/file_paths.xml` | `cache/captures` sharing with camera app |
| `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Exclude phone publisher identity from backup/transfer; not a complete session-data exclusion policy |
| `app/src/main/res/drawable`, `mipmap-*` | UI vectors, launcher assets |
| `app/src/main/res/font`, `app/licenses` | Bundled fonts and OFL licenses |
| `app/src/main/res/values` | App label, platform theme and color resources; much Compose UI copy remains inline in Kotlin |
| `app/src/main/keepRules/rules.keep` | Existing keep-rules resource; release optimization is currently disabled |
| `supabase/schema.sql` | Entire local backend schema/policies/RPCs/storage bootstrap; no separate server repository here |
| `.github` | CODEOWNERS, issue and PR templates; no checked-in Actions workflow found |
| `.gitignore`, `SECURITY.md`, `CONTRIBUTING.md`, `PRODUCT.md` | Local/secret exclusions, security workflow, review rules and product intent |

Generated `build`, `.gradle`, `.kotlin`, `.idea`, local SDK paths, binaries and private configuration are not implementation sources. The adjacent historical PDF is not a versioned executable specification; this handoff derives behavior from current source. Never restore old credentials from prototype material.

## Dependency purpose

| Dependency family | Why it exists |
| --- | --- |
| Compose BOM 2026.02.01 / Material 3 | Phone rendering, state, forms and transitions |
| Supabase BOM 3.2.6 | Auth, PostgREST and Storage SDK modules |
| Ktor OkHttp 3.3.0 | Supabase HTTP engine |
| Serialization 1.7.3 / coroutines 1.9.0 | DTO JSON and suspending/flow operations |
| Coil 3.3.0 + network OkHttp | Remote/local image display; network module is required for URLs |
| CameraX 1.5.0 / ML Kit 17.3.0 | Camera frame lifecycle and on-device QR decoding |
| ZXing 3.5.3 | QR encoding and test decoding |
| Play services Wearable 19.0.0 | Phone/watch Data Layer |
| Desugar JDK libs 2.1.3 | Required newer Java APIs on phone minSdk 24 |
| JUnit / Compose UI test / AndroidX test / Espresso | JVM rules/serialization and device UI verification |

The watch declares dependencies directly in Gradle and adds Wear Compose material/foundation 1.5.0. Upgrades must preserve both dependency graphs and the shared protocol.

## Common change recipes

| Change | Review together |
| --- | --- |
| Add/rename a profile field | SQL column/RLS exposure → ProfileRow/Upsert → Profile → forms/cards → root save path → tests/API docs; consider whether it belongs on watch |
| Change cue semantics/colors | Phone CueStatus/Color.kt → SQL enum/data migration → watch CueStatus/CueSymbol → guide/copy/visibility rules → tests/protocol compatibility |
| Add a tab | Tab/icon → TabsScaffold/root branch → Tab.tourText → state restoration/back behavior → tutorial/tests |
| Add an event field | SQL → insert/row/draft/model → EventSaver → create/detail/list UI → serialization/schedule/UI tests |
| Change privacy | SQL read policies and SECURITY DEFINER RPCs → phone share controls → image access → watch snapshot and cache → multi-account server/device tests |
| Change deep links | Manifest → parser/constants → root intent routing → reset Auth redirect/template if relevant → cold/warm/recreated activity tests |
| Add reliable saves | Explicit success/error state and retry/rollback design → root callbacks/repositories → account-switch/cancellation behavior → offline/integration tests |
| Change installed identity | Both application IDs/variants and signing → FileProvider authority → test package assertions/adb commands → watch pairing → release/migration decisions |

Use [testing.md](testing.md) to choose validation and [known-issues.md](known-issues.md) to avoid perpetuating already identified assumptions.
