# Unspoken Cues — Android Phone App

Android control center for **Unspoken Cues**: status (Green/Yellow/Red/Purple), profile, QR identity, Wear OS sync, and the S.W.A.P. digital card experience.

Target package: `com.unspokencues.phone`

## Status
Foundation phase — see [Issues](../../issues) and the org [Project board](https://github.com/orgs/unspoken-cues/projects) for current sprint work.

## Docs
- Contribution workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security/secret handling: [SECURITY.md](SECURITY.md)

## Product context
Full product handoff lives outside this repo. Key rules:
- Exactly four statuses, one active at a time.
- Phone is the source-of-truth control surface; must sync reliably with the Wear OS app.
- Final Play identity is `com.unspokencues.phone` — do not reuse `com.unspokencues.mobile` or leave a `playtest` applicationId in a release build.
