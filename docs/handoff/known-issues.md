# Known issues and ownership transfer

Return to the [handoff index](README.md). Findings below are from static inspection on 2026-11-11, re-checked against the code on 2026-11-17. They do not assert a production incident. Confirm impact against the deployed environment.

## Observed implementation limits

| Priority | Finding/evidence | Successor action |
| --- | --- | --- |
| Release blocker | Phone release ID is `com.example.unspokenqueues`; watch release is `com.unspokencues.wear`; older target phone ID is `com.unspokencues.phone`. Debug matching is deliberate. | Agree shared installed ID/signing and existing-listing migration before publishing either app. |
| Privacy | Other people's profiles are readable only through `binder_cards()` and `event_attendees()`. `swap_by_card_token` refuses Private Mode and blocks but does not test `is_public`; phone QR/share is not gated by it. | Define the product meaning of a non-public profile for swaps. Existing valid tokens are capabilities; visibility alone does not revoke them. |
| Authorization | `event_members` INSERT permits any authenticated user as themselves with a valid event FK; no code/active/public check. UPDATE similarly only checks own user_id. | Review direct API membership creation and RSVP policy; active-only UI/RPC checks are insufficient. |
| Reliability | Cue, visibility, Private Mode and privacy-toggle saves now revert and show a message on failure (`saveOrRevert`). Sign-out still suppresses failures (`runCatching`). No durable queue; partial UPDATE often has no affected-row check. | Test missing rows, offline edits and concurrent changes; decide whether sign-out needs an error path. |
| Account lifecycle | The profile starts blank and a failed load shows a retry screen. Settings → Delete account removes the user's photos, then calls `delete_my_account()` (see [hardening](hardening.md)). | Verify cold offline start and switching accounts after a failed profile load. |
| Synchronization | Android can defer the background cue job while asleep. | A pending watch choice retries on delivery or through phone Settings → Sync now. See the [cue contract](watch-sync.md). |
| Offline privacy | Watch keeps last accepted identity while disconnected. Card tokens have no expiry. `remove_swap` gives the remover a new token, so the old one stops working; a block makes swaps fail like an unknown card. | Communicate current behavior. Clearing local caches does not invalidate tokens. |
| Storage | Both buckets are public and accept only JPEGs up to 5 MB. Removing a URL or an event does not delete Storage objects; account deletion relies on the app deleting the user's files first. | Define retention/cleanup/private-media policy and implement reviewed cleanup without deleting still-referenced data. |
| Event lifecycle | Join RPC checks active only; direct membership policy is broader. UI schedule classification does not change active. | Decide whether schedule end prevents joining and enforce consistently on server. |
| Counts/freshness | Going count has no historical backfill; Discover quick RSVP estimates locally and lacks an in-flight guard. UI “now” is remembered. | Verify trigger/backfill, duplicate taps, list refresh and time-boundary updates. |
| Pagination | Discover filters past events on the server, then limits to 60 with no paging; no paging for binder/my-events. | Add appropriate server time filtering/pagination when data grows. |
| State restoration | Edit-profile draft fields mostly use `remember`, while camera/picked URI uses saved state. Root EventSaver manually encodes fields. | Test rotation/process recreation with unsaved edits and update saver whenever Event changes. |
| Multi-publisher | Watch accepts an unseen publisher and permanently retires prior generation; no explicit selected-phone arbitration. (Watch repository, not re-checked.) | Test/define multi-phone pairing and replacement behavior before claiming support. |
| Security hardening | Phone allows backup; only watch_publisher is explicitly excluded. Password fields avoid saved state, but SDK session/prefs backup policy is not comprehensively audited here. | Review desired backup behavior and actual SDK session storage before claiming all account data is excluded. |
| Backend lifecycle | `schema.sql` plus ordered, repeatable migrations in `supabase/migrations`, and role fixtures in `supabase/tests`. No migration ledger, down scripts, backfill automation or checked-in CI. | Establish deployment ownership; run the fixtures before every schema deploy. |
| Media edge case | EXIF rotation helper handles 90/180/270, not mirrored orientations. | Add orientation support/tests if affected input must be supported. |

## Fixed since the first inspection

- Red/Purple attendees are hidden from non-hosts by `event_attendees()` on the server; the client-side `visibleAttendees` filter is gone.
- `EventRepository.myEvents` filters by host and membership, so unrelated public events no longer appear.
- `profiles` has no public or swap-partner read policy any more.
- This branch ([hardening](hardening.md)): `event_members` reads return only the caller's own rows, so the host and co-members see others only through `event_attendees()` (the host sees Red/Purple, never blocked or Private Mode members); profile text and list sizes have server limits; buckets accept only JPEGs up to 5 MB; `delete_my_account()` exists; the event end rule lives in one function, `event_end()`; a core privacy fixture covers these rules.

## Historical documentation differences

The phone README's old Foundation phase wording and some source comments predate the working Auth/events/storage integration. Schema manual-check policy counts omit later additions. Watch README/plan branch names describe historical work rather than the checked-out branch. The current Gradle daemon criteria request Java 25. This handoff documents actual code behavior; preserve plans as historical intent instead of treating them as shipped guarantees.

## Information the departing owner must transfer privately

No credentials or ownership details can be recovered reliably from code. Fill in these fields in the team's approved private record, then give the successor access through the services themselves:

| Item | Information/access needed | Current documentation status |
| --- | --- | --- |
| Repository ownership | Canonical repository URLs, org admins, maintainers, branch protections, active PRs and release branch | CODEOWNERS/contribution rules exist; remote state not verified |
| Supabase environments | Project IDs/URLs, owner/org, billing, dev/staging/production mapping, client-key distribution and admin access | Build accepts URL/client key; actual ownership unknown |
| Database state | Applied schema/revisions, any dashboard-only policies/functions, production data migrations, backups/restores | Local schema only |
| Auth/email | Sender/provider ownership, SMTP configuration, confirmation settings, reset redirect/template, rate limits | Required callback documented; hosted settings unverified |
| Storage | Bucket settings, quotas, retention, existing public URLs, cleanup ownership | Two public buckets defined locally |
| Android publishing | Phone/watch listings, package history, Play Console access, app signing/upload keys and recovery | Release identity remains unresolved in source |
| Signing | Authorized key custody, certificate fingerprints, development pairing key distribution | No private material should be committed |
| Runtime support | Support owner, error/log access, incident channel, backup owner, release/rollback authority | No operational ownership record in source |
| Product/design | Final requirements, original design assets, outstanding decisions, acceptance owner | PRODUCT.md and historical design notes available |
| Test equipment/accounts | Approved synthetic accounts, device pairing instructions, phone/watch OS/device matrix | Runbooks present; access/results need recording |
| Work in progress | Which local changes are intentional, pending reviews, issue/project links and agreed priorities | Baseline was already dirty; docs do not commit or resolve those changes |

## Suggested successor onboarding

First obtain repo/backend access and run both debug builds using [setup](setup.md). Then complete the [acceptance matrix](testing.md) on the actual development environment and compare SQL definitions. Resolve release identity and privacy/membership semantics with the product owner before external release. Establish a baseline of test results, record deployment drift and prioritize the findings above. Keep credentials and personal account data outside Markdown/source control.
