# Unspoken Cues — Android Phone App

Android control center for **Unspoken Cues**: status (Green/Yellow/Red/Purple), profile, QR identity, Wear OS sync, and the S.W.A.P. digital card experience.

Target package: `com.unspokencues.phone`

## Status
Foundation phase — see [Issues](../../issues) and the org [Project board](https://github.com/orgs/unspoken-cues/projects) for current sprint work.

## Docs
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

## Initial Wear OS companion

The companion project is in `../unspoken-cues-wearos` on branch `luana-wearos`.
This phone app publishes the signed-in user's cue and public sharing information
to `/unspoken-cues/state` using the Wear OS Data Layer. Open the phone app, complete
onboarding, and pair your Wear OS watch; Settings → **Sync now** retries immediately.
Updates also retry every 30 seconds while this app's composition is alive.

The initial integration is phone-to-watch. Color changes made on the watch are
local, and a newer phone snapshot replaces them. The watch uses the same
`unspokencues://card/<token>` QR payload as the phone. No backend credentials are
sent to the watch. Private profiles withhold their sharing token; sign-out sends
an empty snapshot, which clears cached identity after delivery.

Development builds currently pair using `com.example.unspokenqueues` on both
devices and the same debug signing certificate. The documented release IDs differ;
production pairing requires a coordinated shared application ID/signing decision
before publishing. See the companion README for installation and device checks.

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
