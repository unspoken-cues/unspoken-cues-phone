# Quick reactions design

## Goal
An optional, quiet wave between consenting event participants, without starting conversation or creating a S.W.A.P. connection. Standalone PR based on origin/main.

## Behavior and consent
Add event-scoped Receive waves setting, default OFF and persisted per user/event. Explain that turning it on allows waves from other opted-in participants while both profiles are public and both cues are Green or Yellow. Host counts as participant. Only host/members can configure/read the feature. The event page offers a labeled Wave button for eligible attendee rows; never self. Both sender and recipient must explicitly opt in, both currently public and Green/Yellow, and event must be active and not past. Server enforces all rules, including direct API calls; host has no bypass of privacy. A unique event/sender/recipient tuple enforces one wave per pair per event atomically, safe to retry. No free text, chat, auto-connection, push, vibration or realtime.

## Receiving and privacy
Inside event details show a private Waves for you inbox newest first, with sender display name and timestamp; only receiver can read their inbox (sender may read own sent recipient IDs for disabled sent buttons). Private Mode or a hidden cue pauses waves for that person. A block in either direction hides both people from each other in wave recipients and history and prevents sends. Keep visibility checked at read time: hidden/red/purple users should not be exposed via reaction history. Opt-out immediately disables incoming/outgoing waves and clears/hides inbox; no public reaction counts or participant opt-in directory. Membership loss removes access; cascade on event deletion. Use restricted RPCs or equally robust RLS with explicit auth grants and fixed search_path; prevent spoofed sender IDs and bypass writes. Unknown/not-loaded eligibility fails closed.

## UI and structure
Dedicated Reaction model/DTO/repository + injectable composable(s); integrate with a small EventDetailScreen change and optional injection parameter preserving existing callers. Loading, empty inbox, Refresh waves, opt-in update pending/error, send pending/sent/error/retry all explicit. Do not optimistically claim receipt or mutate on view. Migration filename: supabase/migrations/202611170002_quick_reactions.sql.

## Shared constraints and validation
Preserve all existing event, RSVP, cue, QR, watch, auth and binder behavior. Follow existing Compose components, typography and theme; use labeled 48dp targets, calm inline feedback, accessible content descriptions and scrollable content. Scene: someone checking an event one-handed at a busy party in changing light; honor the existing light/dark theme and keep cue colors reserved for cues. No new dependencies or background push delivery. Key UI state by event and signed-in user; preserve cancellation and prevent duplicate in-flight writes. Explicit errors with retry; show success only after server acknowledgement.

Implementation must include a forward SQL migration for existing databases, corresponding fresh-schema definitions, server authorization tests with authenticated/anonymous roles, meaningful JVM model/serialization tests and Compose tests using fakes for success, denial/empty/error/retry paths. Run debug build, JVM tests, compile instrumentation tests and lint; run feature UI tests if an emulator is available. Document setup, permissions, manual two-account verification and limitations. Do not deploy to any live backend.

## Review acceptance checklist
- [x] End-to-end persisted repository + usable event-page UI.
- [x] Authorization enforced by SQL, including direct API bypass attempts.
- [x] Empty/loading/error/retry and in-flight states; no false success.
- [x] Privacy boundaries hold; no hidden profile data is exposed.
- [x] Fresh schema and forward migration agree; rollback guidance included.
- [x] Focused tests pass; existing event behavior preserved.
- [x] Build, unit tests, lint and available UI tests recorded with actual outcomes.
- [x] Deployment prerequisites and limitations documented.
