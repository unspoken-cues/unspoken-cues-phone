# Security

This repo is public. Secret scanning and push protection are enabled at the org level — a push containing a recognizable credential pattern (API key, private key, etc.) will be **blocked automatically**.

Rules for this project:

- Never commit `google-services.json`, keystores (`*.jks`/`*.keystore`), signing configs, `.env` files, or service-account JSON. They're in `.gitignore` — don't force-add them.
- Firebase config and signing material go in GitHub Actions secrets or a local untracked file, never in source.
- If you accidentally commit a secret: **rotate/revoke it immediately**, then tell the team lead. Removing it from a later commit does not remove it from git history — assume it's burned and needs rotation.
- A prior Firebase service-account credential was found among the original founder's project materials and was deliberately excluded from the handoff. Do not reintroduce any credential you find in old prototype files, chat exports, or APKs without rotating it first.

Report a concern to the team lead directly rather than opening a public issue.
