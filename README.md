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
