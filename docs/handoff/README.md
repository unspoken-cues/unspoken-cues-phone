# Unspoken Cues engineering handoff

This is the entry point for maintaining the Android phone app and its Wear OS companion. It describes the **working tree reviewed on 2026-11-11**, including changes that were already uncommitted when documentation began. It is an implementation reference, not a statement that a production deployment has been verified.

## Read in this order

1. [Setup and build](setup.md): tools, configuration, backend bootstrap, installation.
2. [Architecture](architecture.md): boundaries, dependencies, state ownership, persistence, navigation.
3. [Feature flows](feature-flows.md): authentication, recovery, onboarding, cue, profile, events, QR, binder.
4. [API reference](api-reference.md): every repository operation, payload, query, RPC and failure contract.
5. [Database and storage](database.md): schema, relationships, functions, triggers, access rules and verification queries.
6. [Phone/watch contract](watch-sync.md): message fields, ordering, retries, privacy and release identity.
7. [Source map and change guide](source-map.md): where each responsibility lives and what to update together.
8. [Testing](testing.md): existing coverage, commands, integration and device acceptance checks.
9. [Operations and troubleshooting](operations.md): diagnose failures, handle schema changes and prepare releases.
10. [Known issues and ownership transfer](known-issues.md): observed implementation gaps and information the departing owner must transfer.
11. [Documentation review record](review.md): scope, validation and revision notes.

The companion has a self-contained [Wear OS handoff](../../../unspoken-cues-wearos/docs/handoff/README.md). Links between repositories assume sibling checkouts named `UnspokenQueues` and `unspoken-cues-wearos`; the local documentation in each repository remains usable independently.

## Project in one paragraph

Unspoken Cues lets a signed-in user select exactly one cue: Green (open to connect), Yellow (proceed with care), Red (please give me space), or Purple (maybe open to connect). The phone manages the profile, preferences, boundaries, events, and mutual S.W.A.P. card connections. Supabase supplies Auth, PostgREST/Postgres and Storage. The watch receives a small snapshot through Google Play services Data Layer, caches it and displays a cue or a profile QR. Watch choices currently stay on the watch.

## What to trust

Executable source and `supabase/schema.sql` define local behavior. These documents explain that behavior and call out contradictions in older comments and plans. The live Supabase configuration, release signing, Play Console state, organization permissions and deployed schema were not inspected. Obtain those through the ownership checklist; do not infer them from this repository.

Reference points at review: phone `6e3fce7` on `feature/ui-ux-overhaul`; watch `7cbdc31` on `feature/glanceable-accessible-cues`, plus local changes. Existing contribution rules are in [CONTRIBUTING.md](../../CONTRIBUTING.md), secret handling in [SECURITY.md](../../SECURITY.md), and product/design intent in [PRODUCT.md](../../PRODUCT.md).

## Keep this handoff current

Update the matching topic when changing a repository method, SQL policy/function, saved-state field, deep link, dependency, application ID or watch field. Review both applications for every shared contract change. Record verification results with the environment and date; keep proposals and unverified behavior explicitly labeled. Do not add credentials, recovery links, real card tokens, signing keys or personal test data to examples.
