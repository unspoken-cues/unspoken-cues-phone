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
