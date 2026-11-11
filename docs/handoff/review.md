# Documentation review record

Return to the [handoff index](README.md).

## Scope and baseline

Reviewed on 2026-11-11 in sibling local phone/watch checkouts. Phone HEAD: `6e3fce7`, branch `feature/ui-ux-overhaul`. Watch HEAD: `7cbdc31`, branch `feature/glanceable-accessible-cues`. These documents include the current working files, including pre-existing local phone sync code/tests and build/backup changes. They do not describe HEAD alone.

Implementation inventory: 34 phone production Kotlin files and 7 watch production Kotlin files; all appear in their respective source maps. Reviewed application entry points, data repositories/DTOs/helpers, model rules, Compose UI and theme, Android manifests/backup/FileProvider resources, Gradle versions/build settings, SQL bootstrap, existing READMEs/design/contribution/security notes and test inventory. Generated output, caches and private local configuration were not used as architecture sources. The adjacent historical PDF was not treated as the implemented specification.

Test inventory at review: phone 17 JVM files with 137 `@Test` annotations and 10 device files with 81; watch 2 JVM files with 13 and 1 device file with 4. These are static annotation counts, including starter examples, **not executed/pass counts or coverage percentages**.

## Iteration 1: source-derived draft

Created linked topic documents for setup, architecture, feature flows, every data repository operation, schema/RLS/storage, watch protocol, source map, validation, operations and known issues. Added a self-contained companion handoff and entry links in both root READMEs. Existing uncommitted runtime/build changes were preserved.

## Iteration 2: consistency and evidence review

Rechecked draft claims against actual repository methods, schema policy bodies, watch ordering/store code, manifests, Gradle settings and concrete test methods. The review tightened several distinctions:

- Current Java daemon criteria request 25; Java compilation target is 11. Historical JDK/branch/checkout guidance was updated in the root README where needed.
- Target/installed/debug/release IDs are explicitly separated; production pairing remains unresolved.
- My-events RLS can include unrelated public events; host access to member rows does not imply access to every profile.
- UI-only cue filtering and active-state checks are distinguished from server authorization; private profile and image/token behavior are stated accurately.
- Repeated watch submissions use the same revision and cannot force-replace a local cue; a changed snapshot is required.
- Test descriptions were narrowed to actual methods: Settings tests exercise replay, and watch device tests do not test a transport-driven phone update. Receiver/store persistence and paired delivery remain integration work.
- Historical SQL manual-check totals are superseded by definition-based inventory queries; expected-error tests need transaction handling.

## Documentation validation

The final documentation check resolves local Markdown links (including cross-repository links in this workspace), checks linked section anchors and balanced code fences, verifies production Kotlin filenames are represented in source maps, and confirms every public suspending repository method is named in the API reference. Git whitespace checks cover the changed root READMEs; newly created Markdown is checked directly for trailing whitespace. Results are documentation checks only.

Recorded result: **PASS** for 15 handoff Markdown files, 68 local links/anchors, all 41 production Kotlin filenames and 32 public repository method declarations (including overloads). Both repositories' `git diff --check` passed. The initial link check caught the not-yet-created review page; it was added and the complete check rerun successfully.

No application code was changed, no credentials copied, no account messages sent, no remote resources mutated, and no application builds/unit/device tests or live backend checks were run for this documentation-only task. Hosted settings, deployed schema, Play listings, signing custody and operational owners remain explicit transfer items. Mermaid diagrams are supplied as Markdown source; no separate rendered-diagram QA was performed.

## Revalidation triggers

Revisit this handoff whenever adding/changing a model/DTO field, API method, SQL function/policy, deep link, persistence key, cue meaning, watch schema/order rule, toolchain or application identity. Re-run real device/backend acceptance checks after corresponding implementation changes and record their results separately from this documentation review.
