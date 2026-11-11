# Setup and build

Return to the [handoff index](README.md).

## Checkout layout and tools

Both repositories are independent Gradle builds, each with one `:app` module. Open them separately in Android Studio. The folder name `UnspokenQueues` and Kotlin namespace retain the older spelling; the displayed product is Unspoken Cues.

```text
AndroidStudioProjects/
  UnspokenQueues/          # phone; backend SQL is here
  unspoken-cues-wearos/    # companion; no backend credentials
```

The checked-in configuration is the source for these versions:

| Setting | Phone | Watch |
| --- | --- | --- |
| Gradle wrapper | 9.6.0, checksum pinned | 9.6.0, checksum pinned |
| Android Gradle Plugin | 9.4.0 | 9.4.0 |
| Kotlin Compose plugin | 2.2.10 | 2.2.10 |
| Daemon toolchain criteria | Java 25 | Java 25 in current local tree |
| Java source/target compatibility | 11, with core library desugaring | 11 |
| compileSdk / targetSdk | 37 / 37 | 37 / 37 |
| minSdk | 24 | 30 |
| versionCode / versionName | 1 / 1.0 | 1 / 0.1.0 |
| Namespace | `com.example.unspokenqueues` | `com.unspokencues.wear` |
| Debug application ID | `com.example.unspokenqueues` | `com.example.unspokenqueues` through variant override |
| Current release application ID | `com.example.unspokenqueues` | `com.unspokencues.wear` |

Install Android SDK platform 37 and appropriate phone/Wear OS system images. Use an Android Studio version capable of opening this checked-in AGP build. The daemon criteria request Java 25 through the Foojay resolver; Java 11 above describes compilation compatibility, not the Gradle runtime. The older watch README's “JDK 21 or newer” is not a complete description of the current criteria. First sync can need network access for Gradle, JDK and Maven dependencies.

The phone pins Kotlin artifacts to 2.2.10 in `app/build.gradle.kts` because of transitive metadata compatibility. Treat that resolution rule, AGP and Compose/serialization plugins as a coordinated upgrade.

## Phone configuration

Create an **untracked** `local.properties` in the phone repository. Obtain the actual values from the project owner through a private channel:

```properties
sdk.dir=/absolute/path/to/Android/sdk
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<public-client-key>
```

`SUPABASE_URL` and `SUPABASE_ANON_KEY` are read from `local.properties`, then from environment variables if a property is absent, then default to an empty string. A present but empty local property wins over the environment. They are trimmed and compiled into `BuildConfig`; changing them requires rebuilding/reinstalling. Use the project HTTP(S) REST URL, never a Postgres connection URI. The lazy client rejects empty values or non-HTTP(S) URLs when first used. The Gradle interpolation expects ordinary URL/key strings, not quoted or multiline values.

The anon/public client key is shipped in the APK; authorization comes from Auth and RLS. A service-role key, database password or signing key must never occupy this setting. `.env` and `google-services.json` are not loaded by the current build. Firebase appears in legacy security guidance, but there is no Firebase runtime dependency or initialization in the reviewed apps.

The watch needs only its SDK location/toolchain; it does not use Supabase. Never copy phone credentials into it.

## Bootstrap a development Supabase project

1. Use a development project whose data can be changed for testing. Preserve a backup and review live differences before touching an existing environment.
2. Run the complete [schema.sql](../../supabase/schema.sql) in that project's SQL editor. It creates profile/signup triggers, event and swap tables, RPCs, policies and public storage buckets. Later sections add columns required by current clients.
3. Configure the email/password Auth provider, email confirmation policy and email delivery. Record these settings for the team; they are outside source control.
4. Allow `unspokencues://password-reset` in Auth redirect URLs. Keep the reset email template's recovery link based on `{{ .ConfirmationURL }}`. Verify a real email on an installed device. The app expects a recovery session parsed from the returned URL; other callback formats require separate testing.
5. Check the tables, policies, buckets and functions using [database verification](database.md#verify-an-environment). Create two test accounts through Auth and test with normal user sessions.
6. Insert the project's URL/client key locally and build the phone.

The schema is a cumulative bootstrap script with rerunnable constructs, not a versioned migration system. It redefines policies/functions and forces the two buckets public. It does not backfill every derived field or prove that an arbitrary older database matches the expected shape.

## Build and install

Run from the intended repository root on macOS/Linux:

```sh
./gradlew assembleDebug testDebugUnitTest
./gradlew lintDebug
```

Windows PowerShell equivalent:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

If the wrapper lacks executable permission, use `sh ./gradlew ...` or correct its local executable bit. The debug APK in each build is `app/build/outputs/apk/debug/app-debug.apk`. Unit tests need no running device; instrumentation does:

```sh
adb devices -l
adb -s <phone-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the install command from the watch repository with a watch serial for the companion APK. Specify the serial every time with multiple devices connected. For Gradle instrumentation, select the intended device (for example with `ANDROID_SERIAL`) and run `./gradlew connectedDebugAndroidTest` from the matching project.

Phone launcher: `com.example.unspokenqueues/.MainActivity`. Watch debug launcher: `com.example.unspokenqueues/com.unspokencues.wear.MainActivity`. The watch's installed debug package differs from its activity namespace.

## Pairing for development

Pair the watch and phone through their normal device/emulator pairing flow and ensure Google Play services is available. Both debug APKs must use the same application ID **and the same signing certificate**. The IDs already match locally; debug keystores on different computers may not. Compare `./gradlew signingReport` in both projects and coordinate authorized development signing privately. Installing two applications with the same ID on one device replaces/conflicts with the other; these APKs belong on different device types.

Sign in on the phone, finish setup/tutorial, open the watch, and use Profile → Settings → Sync now. A connected node is not an application-level receipt; confirm the watch's displayed state. See [watch sync](watch-sync.md) for ordering and offline behavior.

## First successful setup

Complete signup/confirmation, profile setup, all four cues, profile save and reload, a two-account card exchange, event create/join/RSVP, a real reset email, and paired watch sync. Record device/OS, environment, commit, results and any deployment differences. No production release configuration is established by a successful debug build.
