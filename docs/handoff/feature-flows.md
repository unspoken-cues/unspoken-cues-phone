# Feature flows and business rules

Return to the [handoff index](README.md). Network details are in the [API reference](api-reference.md); persistence and authorization are in [database.md](database.md).

## Authentication

Welcome → Sign in/Create account is implemented by `AuthFlow` in `Screens.kt`. The common form rejects blank email/password and checks password confirmation for signup. It does not implement a local password-strength policy; Supabase enforces the configured policy. Passwords use `remember`, not saved-instance state. Email is trimmed in the repository. Loading disables form actions; mapped errors replace raw exceptions that could include request URLs/headers.

`AuthRepository.signUp` may create an account without a session when email confirmation is enabled. It reports `EmailConfirmationRequiredException`, and the user must confirm then sign in. The SQL Auth trigger creates the profile row; profile onboarding is separate. Successful login changes SDK session state, which drives navigation instead of an explicit “login success” callback.

Sign out calls SDK `signOut` in a best-effort coroutine. The root returns to Cue selection and closes Settings, but actual account clearing follows NotAuthenticated. A failed sign-out is currently suppressed. There is no app UI for account deletion, email change, admin roles or social login.

## Password recovery

1. Sign in → Forgot password accepts an Android-validated email and calls `resetPasswordForEmail` with redirect `unspokencues://password-reset`.
2. Successful submission shows a deliberately non-enumerating “if an account exists” message; the client cannot prove delivery.
3. An incoming URI must have scheme `unspokencues`, host `password-reset`, no credentials/port, and an empty path or `/`. Card links do not enter this flow.
4. `recoverPassword` awaits SDK initialization, parses the session from the link, requires type `recovery`, validates it by retrieving the user using the access token, then imports that session. It does not replace a session before validation succeeds.
5. The root shows checking/error/new-password states ahead of onboarding/tabs. Only a validated recovery session enables entry. The form checks nonblank matching passwords; Supabase applies password policy.
6. On successful update, password fields are cleared, the user sees confirmation, and Continue returns to the authenticated app. Cancel after successful session validation clears the temporary session locally and returns to sign in.

Recovery URLs contain credentials: never log, screenshot into public issues or paste them into docs. Isolated UI tests do not prove email configuration, link routing, expiry or server-side session behavior. Test a real reset on the intended project and device.

## Onboarding

`appPhase` keeps the app in LOADING until the account's onboarding result is known. The profile fetch starts only after session confirmation. A local `onboarded_<uid>` flag permits returning accounts into MAIN before the fetch completes; it is a completion shortcut, not a cached profile.

New/incomplete accounts pass through six wizard steps: name, optional bio, preferences, boundaries, optional photo, privacy. Completion requires a nonblank name and at least one nonblank preference **or** boundary. Bio input is capped at 120 characters. Pending list text is added when navigating away from that step. A selected avatar uploads on Finish; profile upsert must succeed before advancing. Failed profile saves retain the uploaded URL for retry.

The five-tab tutorial follows profile setup. Both Skip and Done complete onboarding. Completion is written locally first, then sent to `profiles.onboarding_complete`; a later profile load retries it when the local completion flag exists. Ordinary profile upserts intentionally exclude this flag. Replay tutorial in Settings changes no completion state.

## Cue and watch connection

Exactly one of GREEN/YELLOW/RED/PURPLE is active. The phone immediately changes local cue state and asynchronously updates `profiles.status`. A failed database update is hidden and not queued. Invalid stored cue strings map to GREEN on the phone; the watch rejects invalid active snapshot statuses instead.

The same local state drives watch snapshots once session/profile/onboarding gates are satisfied. The connection pill reflects publishing progress and connected Data Layer nodes. It does not confirm that the companion installed, accepted, displayed or persisted a snapshot. Settings → Sync now restarts the publisher effect.

## Profile, photos and visibility

Profile contains display name, bio, preferences, boundaries, visibility, avatar and optional separate S.W.A.P. card image. Local image URI fields are display optimizations and never serialized to Postgres. Card picture precedence is local card image → card URL → local avatar → avatar URL → initials.

`AvatarPicker` uses the Android photo picker or a camera capture into `cache/captures` via FileProvider. Runtime camera permission is needed for capture/scanning; there is no broad gallery permission in the manifest. Chosen edit-profile images upload on Save. Save requires a name; editing an existing profile does not repeat the onboarding requirement for preferences/boundaries.

Uploads are JPEGs with EXIF rotation correction and bounded dimensions. Profile/visibility edits update local state before database confirmation. Card image URL has its own database update to avoid ordinary profile saves overwriting it. Removing a photo clears its URL; no repository call deletes the uploaded object. Upload and row save are not one transaction.

Private visibility is **not full revocation**: public profile reads are disabled by the flag, but existing swap partners can still read the row. The phone still displays/shares its card token regardless of the flag; the watch withholds private-profile QR sharing. The swap RPC itself accepts a previously shared valid token for a private profile. Public image URLs remain public. See [known issues](known-issues.md) before promising stronger privacy.

## Events

Events screen has Upcoming, Discover and Past views. My-events data currently comes from an unfiltered RLS-limited query; since public events are also readable, unrelated public events can appear in Upcoming/Past. Discover queries active public events ordered by start time, limited to 60 before client-side removal of past events. Tags filter by **all** selected tags; changing subtab clears selection.

Hosting collects name, details, start/end, place, cover, tags and discoverability. Name and end-after-start are required in the UI. Initial start is the current local hour truncated then advanced two hours; initial duration is two hours. Moving start keeps duration. Photo-picker cover upload happens immediately, before event creation. Tags normalize to lowercase ASCII alphanumeric/hyphen, at most five tags of 20 characters. The repository expects already normalized tags. Host name is a snapshot, not a live profile join. Repository creates a six-character code from an alphabet without `0/O/1/I/L` and retries unique-constraint collisions up to five total inserts. The host is not automatically added as an event member.

Joining accepts typed or scanned raw codes, with UI validation of 4–8 alphanumeric characters. The RPC trims/case-normalizes, checks `active`, inserts membership idempotently and returns the event. Existing membership retains its RSVP; joining twice does not change Maybe to Going. A new membership defaults to Going. Event QR contains only the join code, not the card URI.

Discover quick RSVP writes Going and adjusts lists/counts locally. Detail offers Going/Maybe for non-hosts while the event is active and not time-past. `event_members` is upserted on `(event_id,user_id)`; a trigger maintains `going_count`. Displayed count changes are estimates until a fresh read.

Detail loads membership rows and then readable profiles, preserving joined order. Missing/RLS-hidden profiles are skipped. The UI host sees every **returned** attendee; another viewer sees only Green/Yellow plus themselves. Both Going and Maybe memberships are included in this attendee list. There is no event-specific profile-read policy, and the cue filter is not enforced by SQL.

The host can End (set active false; retain event/members/history) or Delete (remove event and cascade memberships). A member can Leave (delete own membership). Delete/leave read back deleted rows and throw on an empty result; End does not verify affected rows. Confirmation dialogs and working/error state wrap these actions. Attendee Refresh reloads attendees; there is no continuous feed.

Time rules are in `EventSchedule.kt`: inactive means Past immediately; no start means Upcoming indefinitely; missing end means start plus three hours; otherwise start-inclusive/end-exclusive determines Live. The create screen normally supplies an explicit two-hour end, so the three-hour fallback applies to missing-end data. Dates are stored as instants and formatted in the viewing device's zone with US-style date/time patterns. The UI's “now” does not tick while sitting on a screen. Server join checks active only, so time-past but active events remain joinable by code.

## S.W.A.P., links and binder

`my_card_token()` creates/returns a stable 32-hex token. The QR/share payload is `unspokencues://card/<token>`, a custom app link with no public web page. `cardTokenFrom` accepts a bare 32-hex string or a card link embedded in text, case-insensitively, and lowercases it. Event codes and ordinary QR text fail this parser.

Scanning or submitting pasted text calls `collectCard` directly. An Android-opened card link waits until MAIN and asks for confirmation because accepting also shares the caller's card. Only one root swap runs at a time; own-token and malformed-input checks happen locally, then `swap_by_card_token` validates and creates one canonical mutual row. Repeating a swap is harmless. Success updates the binder and opens a reveal/result dialog; the other account sees it on its next binder refresh.

Binder reads swap partners' current profile rows, not immutable copies of cards. Search matches name, bio, preferences and boundaries case-insensitively; cue filtering is combined with search. Cards sort by lowercased name. Removing one deletes the shared swap, removing both people's connection. It does not rotate either token: scanning an old token can recreate the swap. Removal rolls back the local captured collection if the server call throws. The binder does not subscribe to other people's changes.

## Scanner and QR rendering

`QrScanner` has Scanning, Needs permission, Permission blocked, No camera and Camera error states. It asks once automatically, can open system app settings after denial, rechecks on resume and rebuilds on retry. CameraX prefers rear then front camera, uses a compatible TextureView preview and latest-frame backpressure. ML Kit decodes QR only and delivers the first nonblank value once. Disposal unbinds camera resources, closes scanner and executor, and suppresses late results. The calling feature validates the decoded payload and closes the scanner.

Phone ZXing encoding uses error correction M; callers provide a white background and quiet-zone padding. The watch renders integer-sized modules with a four-module quiet zone. Keep the complete code readable on round displays; generation tests alone cannot verify real-world scanning.
