# Event announcements design

## Goal
Hosts publish short event updates; people who have joined see them inside event details. Standalone PR based on origin/main.

## Behavior
Add an Announcements section to event details using existing visual language. Host sees an inline composer for active, non-past events; trimmed nonblank text, max 1000 characters with counter and disabled invalid/pending submit. Plain text only. Feed shows body, host label and localized timestamp, newest first with deterministic ordering and at most 50 entries. Explicit Refresh announcements action, loading, helpful empty state and retryable load/post errors; preserve draft on failed post. Success adds returned persisted row and clears draft, with no duplicate writes. Ended/past events retain readable history and cannot accept new posts. Authenticated host and joined members may read; public discover visibility alone grants no access. The server checks current auth UID is the event host, active and ends_at not elapsed; text validity is enforced by DB too. No client-supplied identity or timestamp trusted. Event deletion cascades. No editing, comments, push notification or realtime subscription in v1.

## Structure
Prefer dedicated Announcement model/DTO/repository and EventAnnouncements composable with injectable repository for tests. Integrate via a small insertion into EventDetailScreen and an optional injection argument; keep existing callers compatible. Keep section limited to eligible viewers when membership has loaded, with SQL as authority. Migration filename: supabase/migrations/202611170001_event_announcements.sql.

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

Implementation and validation details: [Event announcements](../features/event-announcements.md). Database validation used isolated in-process PostgreSQL; the migration has not been applied to a live Supabase project.
