# Known issues and ownership transfer

Return to the [handoff index](README.md). Findings below are from static inspection on 2026-11-11. They are not newly fixed by this documentation and do not assert a production incident. Confirm impact against the deployed environment.

## Observed implementation limits

| Priority | Finding/evidence | Successor action |
| --- | --- | --- |
| Release blocker | Phone release ID is `com.example.unspokenqueues`; watch release is `com.unspokencues.wear`; older target phone ID is `com.unspokencues.phone`. Debug matching is deliberate. | Agree shared installed ID/signing and existing-listing migration before publishing either app. |
| Privacy | `profiles` permits public reads and private reads by swap partners. `swap_by_card_token` does not test is_public; phone QR/share is not gated by it. | Define product meaning of Private and align phone/watch/RPC/UI copy. Existing valid tokens are capabilities; visibility alone does not revoke them. |
| Privacy | Red/Purple attendee hiding exists only in `visibleAttendees`, after readable profiles reach the client. | If this is a confidentiality requirement, enforce the intended restriction server-side and test direct API reads. |
| Authorization | `event_members` INSERT permits any authenticated user as themselves with a valid event FK; no code/active/public check. UPDATE similarly only checks own user_id. | Review direct API membership creation and RSVP policy; active-only UI/RPC checks are insufficient. |
| Data exposure | Host can read member IDs but profile RLS has no event-specific grant; private non-swap profiles are skipped. | Decide whether host should receive private attendee data and implement the intended server projection/policy. “Host sees everyone” currently means everyone returned. |
| Correctness | `EventRepository.myEvents(userId)` ignores userId and selects every RLS-readable event; public-read policy broadens that set. | Filter using host ownership/membership and test an unrelated public event on Upcoming/Past. |
| Reliability | Root cue, visibility, profile/card-image saves and sign-out suppress failures. No durable queue; partial UPDATE often has no affected-row check. | Add explicit success/error/retry/rollback as appropriate; test missing rows, offline edits and concurrent changes. |
| Account lifecycle | Local onboarded flag enters MAIN before profile read; initial profile is MockData and account change does not explicitly reset profile/status. | Guard rendering or use an actual account-scoped cache/loading state. Verify cold offline start and switching accounts after failed profile load. Watch publishing has separate gates, but phone display still needs review. |
| Synchronization | Watch choices never write back; phone publisher is composition-scoped; connected-node status is not a receipt. | Treat as one-way initial integration. Design ownership/conflicts/background delivery before adding two-way writes. |
| Offline privacy | Watch keeps last accepted identity while disconnected. Stable card tokens have no expiry/revoke/rotate API. Removing a swap allows recreation using the old token. | Communicate current behavior; design server-side revocation if required. Clearing local caches does not invalidate tokens. |
| Storage | Both buckets are public. Removing URL/event/account does not clean Storage objects through these clients/schema FKs. | Define retention/cleanup/private-media policy and implement reviewed cleanup without deleting still-referenced data. |
| Event lifecycle | Join RPC checks active only; direct membership policy is broader. UI schedule classification does not change active. | Decide whether schedule end prevents joining and enforce consistently on server. |
| Counts/freshness | Going count has no historical backfill; quick RSVP estimates locally and lacks an in-flight guard. UI “now” is remembered. | Verify trigger/backfill, duplicate taps, list refresh and time-boundary updates. |
| Pagination | Discover limits to 60 before client filtering of time-past events; no paging for binder/my-events. | Add appropriate server time filtering/pagination when data grows. |
| State restoration | Edit-profile draft fields mostly use `remember`, while camera/picked URI uses saved state. Root EventSaver manually encodes fields. | Test rotation/process recreation with unsaved edits and update saver whenever Event changes. |
| Multi-publisher | Watch accepts an unseen publisher and permanently retires prior generation; no explicit selected-phone arbitration. | Test/define multi-phone pairing and replacement behavior before claiming support. |
| Security hardening | Phone allows backup; only watch_publisher is explicitly excluded. Password fields avoid saved state, but SDK session/prefs backup policy is not comprehensively audited here. | Review desired backup behavior and actual SDK session storage before claiming all account data is excluded. |
| Backend lifecycle | One cumulative schema script; no migration ledger/down scripts, backfill automation, backend tests or checked-in CI. | Establish deployment ownership and repeatable migration/access tests. |
| Media edge case | EXIF rotation helper handles 90/180/270, not mirrored orientations. | Add orientation support/tests if affected input must be supported. |

## Historical documentation differences

The phone README's old Foundation phase wording and some source comments predate the working Auth/events/storage integration. ProfileRepository's “own-row only” comment omits public/swap reads. EventRepository's comments assume RLS limits myEvents to hosted/joined rows, but later SQL adds public reads. Schema manual-check policy counts omit later additions. Watch README/plan branch names describe historical work rather than the checked-out branch. The current Gradle daemon criteria request Java 25. This handoff documents actual code behavior; preserve plans as historical intent instead of treating them as shipped guarantees.

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
