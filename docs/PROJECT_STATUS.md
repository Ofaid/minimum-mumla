# Minimum project status (source of truth)

Last reviewed: 2026-08-04

This is the canonical hand-off document for the `awatchar/minimum` public PoC. If another
document disagrees with this file, verify the code and update this file first.

## Repository and build identity

- Local repository: `D:\VR Android App\mumla`
- Build-safe junction: `D:\mumla-dev` (same working tree; use this path for Gradle/NDK)
- GitHub remote: `https://github.com/awatchar/minimum.git`
- GitLab upstream remote: `https://gitlab.com/quite/mumla.git`
- Working branch: `agent/minimum-foundation`
- Draft PR: https://github.com/awatchar/minimum/pull/1
- Android application ID: `se.lublin.mumla`
- Current supported build target: FOSS debug APK

## Completed

- Imported the Mumla/Humla source and preserved the existing Mumble, TLS, Opus, audio and
  foreground-service core.
- Captured and documented T99 identity, USB, display, audio and input data.
- Added T99/T88/generic device profile detection and a central multi-key PTT manager. T99/T88 radio
  defaults enable PTT automatically and recognize F1/F2 plus media/headset keys; F1 has been live
  observed on T99 and F2 remains a required live verification.
- Added the six-character persistent public Device ID and unit tests. It is now created at app
  process startup by `MumlaApplication`.
- Added MediaSession handling for Android public media-style PTT keys.
- Added a 120-second PTT watchdog, release-on-disconnect/service-destroy behavior and lockout until
  the physical key is released after a timeout.
- Made first-run client certificate creation automatic with retry on failure.
- Added boot auto-start, enabled by default, with an OEM/background-launch exception guard.
- Removed Zello for user 0 on the connected T99 and added a guarded repeatable PowerShell script.
- Added a public static GitHub Pages backend under `backend/`, with schema, defaults, model files and
  a Pages workflow. No user token is committed.
- Added Android-side `RadioConfigRepository`: embedded safe default, HTTPS-only remote fetch,
  default/model/device merge, validation, size limits and active/previous cache files.
- Added a best-effort six-hour background refresh scheduler. It never delays normal startup and
  falls back to the embedded/cache configuration when Pages or the response is unavailable.
- Added `RadioShellActivity` as a non-launcher minimal PTT UI. The standard Mumla activity remains
  the launcher until the dedicated radio flavor is reviewed.
- Verified the FOSS debug unit tests and APK build after the current changes.

## Known limitations / not falsely marked complete

- T99 F1/F2 foreground PTT is now mapped automatically. Screen-off F1/F2 and raw GPIO PTT are not
  proven: Android public APIs do not prove that these OEM events can reach the service while the
  screen is off. Media/headset keys use the MediaSession path for screen-off operation; F1/F2 may
  require OEM or privileged input integration.
- T88 has no runtime capture yet. Do not add T88 keycodes or USB values until the real device is
  connected and inspected.
- The config repository refreshes in the background, but the resulting radio config is not yet wired
  to automatic Mumble connection/room selection. The checked-in backend intentionally has
  `autoConnect: false` and a placeholder host.
- Mumble room path and access-token resolution from a remote radio room preset are not implemented.
  The supplied test server is documented without its token; keep that token local-only.
- `RadioShellActivity` is intentionally not the launcher and is not a complete radio flavor.
- Boot activity launch can be blocked by newer Android/OEM policy. T99 is API 22 and passed the
  simulated boot test; a newer-device foreground-service/notification fallback remains future work.

## Immediate next work

1. Connect the repository to a worker and add cache refresh/rollback diagnostics.
2. Add a local-only test configuration for the supplied Mumble endpoint and resolve the target room
   through the existing server database without committing the access token.
3. Capture T88 and T99 screen-off input events; verify F2 and determine whether an OEM input path is
   required for F1/F2 while the display is off.
4. Decide the dedicated radio flavor/application ID after the minimal shell is exercised on T99.
5. Add an instrumentation/manual acceptance pass for screen-off PTT, boot, network loss and a
   120-second watchdog timeout.

## Important safety rules

- Never commit the Mumble access token, GitHub credentials, private keys or device-specific secrets.
- Do not claim screen-off PTT support for an OEM key until an actual screen-off trace proves it.
- Keep the normal Mumla build working while the radio interface is developed.
- Do not merge PR #1 without explicit user approval.
