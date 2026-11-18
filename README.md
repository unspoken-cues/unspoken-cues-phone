# Unspoken Cues — Android Phone App

Android control center for **Unspoken Cues**: status (Green/Yellow/Red/Purple), profile, QR identity, Wear OS sync, and the S.W.A.P. digital card experience.

Planned phone Play identity: `com.unspokencues.phone`. Current build application ID: `com.example.unspokenqueues`; coordinate the phone/watch release identity before publishing.

## Status
The current code includes Supabase email/password authentication and recovery, profile onboarding/editing, four cues, event hosting/discovery/RSVPs, [host announcements](docs/features/event-announcements.md), mutual S.W.A.P. cards/binder, and [two-way global cue synchronization](docs/handoff/watch-sync.md). Release identity and several reliability/privacy decisions remain open; see the handoff's known issues.

## Docs
- **Start here for engineering takeover:** [Engineering handoff](docs/handoff/README.md) — setup, architecture, feature flows, API calls, database/access rules, watch protocol, source map, testing, troubleshooting, and ownership transfer.
- Contribution workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security/secret handling: [SECURITY.md](SECURITY.md)

## Emulator can't reach the network
If sign-in or loading times out on the emulator, it is probably failing to resolve names through the host's DNS settings. Close the emulator, then start it from a terminal with public DNS servers instead (it opens in its own window rather than inside Android Studio):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_7 -dns-server 8.8.8.8,1.1.1.1
```

Replace `Pixel_7` with your own device name; `emulator.exe -list-avds` shows them. The setting only lasts for that run, so use this command each time instead of the Run button's device launcher.

## Product context
Full product handoff lives outside this repo. Key rules:
- Exactly four statuses, one active at a time.
- Phone is the source-of-truth control surface; must sync reliably with the Wear OS app.
- Final Play identity is `com.unspokencues.phone` — do not reuse `com.unspokencues.mobile` or leave a `playtest` applicationId in a release build.

## Wear OS companion

After pairing and onboarding, changing a cue on either device updates the signed-in phone profile. The watch can queue a cue offline and shows confirmation after the phone verifies it with Supabase. **Settings → Sync now** retries retained watch input and republishes the committed cue. Private profiles withhold their sharing token. Sign-out clears the watch after delivery.

The companion repository is `../unspoken-cues-wearos`. Both debug APKs use `com.example.unspokenqueues` and must share a signing certificate. See the [sync contract and paired-device checks](docs/handoff/watch-sync.md) before configuring release builds.

## Email confirmation page

When someone creates an account, Supabase emails a confirmation link. After confirming, Supabase
sends them to `https://unspoken-cues.github.io/unspoken-cues-phone/auth/confirmed/`, a small page
in [`docs/auth/confirmed/`](docs/auth/confirmed/index.html). It says the email is confirmed (or
that the link has expired) and has an **Open Unspoken Cues** button that opens the app on the
sign-in form through `unspokencues://sign-in`. The page never sends the link's token anywhere and
wipes it from the address bar.

One-time setup:

1. In GitHub, **Settings → Pages → Build and deployment**: Source **Deploy from a branch**, branch
   `main`, folder `/docs`. The page is live a minute later.
2. In Supabase, **Authentication → URL Configuration**:
   - set **Site URL** to `https://unspoken-cues.github.io/unspoken-cues-phone/auth/confirmed/`, so
     any email that has no redirect of its own lands there too;
   - add the same URL under **Redirect URLs** (next to `unspokencues://password-reset`).
3. In **Authentication → Email Templates → Confirm signup**, keep the link as
   `{{ .ConfirmationURL }}`.

Test: create an account with a fresh email, open the email on a phone with the app installed,
tap the link, then **Open Unspoken Cues**: the app opens on sign-in. Opening the same link a
second time should show the "link has expired" version of the page.

## Password recovery

The sign-in screen includes **Forgot password?**. Supabase sends an email link that opens
`unspokencues://password-reset` in the Android app. The app validates the recovery session
before showing the new-password form, and saves the password through Supabase Auth.

Before using this flow in a Supabase project:

1. In **Authentication → URL Configuration → Redirect URLs**, allow
   `unspokencues://password-reset` exactly.
2. In **Authentication → Email Templates → Reset Password**, keep the recovery link using
   `{{ .ConfirmationURL }}` so Supabase verifies the token before redirecting to the app.
3. Ensure the project's email delivery is configured for the users who will test this flow.

Test with an existing account: request a reset from sign-in, open the email on the Android
device where the app is installed, enter matching new passwords, then sign out and sign in
with the new password. Expired links should offer a return to sign-in to request another.
The confirmation after requesting a link deliberately does not reveal whether an email
belongs to an account. Automated UI tests use callbacks and do not send real emails.

[Watch event alert setup and paired-device checks](docs/design/watch-event-alerts.md)
