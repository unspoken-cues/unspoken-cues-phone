# Contributing

## Branching

- `main` is protected: no direct pushes, no force-push, no deletion. All changes land via pull request.
- Branch naming: `feature/short-description`, `fix/short-description`, `chore/short-description`.
- Every PR needs **1 approval** before merge, and all review conversations must be resolved.

## Workflow

1. Pull an issue from the [org Project board](../../projects) (or org-level Projects tab) and move it to **In Progress**, assign yourself.
2. Branch off `main`.
3. Commit with clear messages; reference the issue number (`#12`) in the PR description.
4. Open a PR against `main`, move the card to **In Review**.
5. After merge, move the card to **Done** and delete the branch.

## Sprints

Five sprints run through the semester (see the repo milestones): Audit + Architecture → Identity + Backend → Phone↔Watch Sync → QR + S.W.A.P. → Hardening + Handoff. Assign issues to the current sprint's milestone.

## Commit hygiene

See [SECURITY.md](SECURITY.md) before committing anything touching Firebase, signing, or `.env` files.
