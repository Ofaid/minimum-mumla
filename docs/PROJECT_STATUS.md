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
- Removed Zello for user 0 on the connected T99 and evolved the guarded PowerShell workflow into
  `scripts/prepare-t99.ps1`: serial report/collision handling, Zello removal and Minimum Device ID
  verification. The old Zello script name remains a compatibility wrapper.
- Added a public static GitHub Pages backend under `backend/`, with schema, defaults, model files and
  a Pages workflow. No user token is committed.
- Added Android-side `RadioConfigRepository`: embedded safe default, HTTPS-only remote fetch,
  default/model/device merge, validation, size limits and active/previous cache files.
- Added `AccessTokenResolver` with JVM tests for public-token trimming, case preservation,
  first-seen ordering, deduplication and safe exclusion of malformed/none/protected entries. The
  radio connection now passes the resolved values through the existing Humla authentication path
  without writing them to the server database or logs.
- Added a best-effort six-hour background refresh scheduler. It never delays normal startup and
  falls back to the embedded/cache configuration when Pages or the response is unavailable.
- Added `MinimumHomeActivity` as the small-device recovery dashboard with one large icon per swipe
  page: Minimum and Settings. It is intentionally not an Android HOME handler because the T99 OEM
  resolver excludes it and displays an unusable chooser.
- Completed the dark `RadioShellActivity`: it loads the Last Known Good config, silently ensures a
  client certificate, connects/reconnects automatically, authenticates with resolved public room
  tokens, resolves the default room by its exact full path, joins it, and displays offline,
  connecting, ready, RX, TX and access-denied states. Direction keys select configured rooms;
  green/Enter confirms and red/End returns to the default room.
- Added config-authorized automatic trust for managed/self-signed Mumble servers. Normal Android
  trust is attempted first; on failure, `autoTrustServerCertificate` defaults to true, stores the
  presented leaf certificate app-privately and retries without a dialog. An optional SHA-256 pin is
  stricter and a mismatch is still refused.
- Added config-version downgrade rejection and JVM tests for config parsing, downgrade behavior,
  token handling and full-path room resolution.
- Minimum now defaults to dark mode while preserving an explicit user choice of light or system
  theme.
- Added a legacy Launcher3 recovery shortcut installer and provisioning receiver. Launcher3 now
  contains Minimum plus Settings, while provisioning verifies that system HOME has no chooser.
- Extended `scripts/prepare-t99.ps1` to install a locally supplied config into app-private storage,
  set restrictive permissions, grant/recognize microphone permission by Android API level and open
  the radio client without exposing token values.
- Verified on the physical T99: private config provisioning, pinned TLS connection to the supplied
  self-signed server, token authentication, exact full-path room join, ready UI, service survival
  while the display was off, and a real reboot returning directly to the same ready room without a
  HOME chooser or operator input.
- Verified automatic self-signed trust separately on T99: removed the old app-private trust store,
  provisioned a config with no fingerprint, and observed Minimum recreate private trust, reconnect
  and return to the exact ready room without a certificate dialog.
- Verified the FOSS debug unit tests and APK build after the current changes.

## Known limitations / not falsely marked complete

- Physical PTT while the T99 display is off has now passed an operator test. The live device also
  shows Minimum's `PttMediaSession` active, so the supported explanation is the Minimum
  MediaSession/service code path rather than a provisioning setting. The exact keyCode/source was
  not captured during that successful press, so raw GPIO F1/F2 screen-off support remains
  unclassified rather than claimed.
- T88 has no runtime capture yet. Do not add T88 keycodes or USB values until the real device is
  connected and inspected.
- Multiple configured room presets and physical room switching are implemented but have only been
  exercised with one live room. Permission-denied fallback and room changes during real traffic
  still need a multi-room acceptance test.
- The checked-in backend intentionally has `autoConnect: false`, a placeholder host and no access
  token. The successful endpoint configuration remains local/device-private.
- T88 boot/dashboard and legacy shortcut behavior still require live-device verification. Each new
  firmware must pass the no-ResolverActivity provisioning check.
- Boot activity launch can be blocked by newer Android/OEM policy. T99 is API 22 and passed an
  actual reboot-to-ready-room test; a newer-device foreground-service/notification fallback remains
  future work.

## Immediate next work

1. Capture the successful T99 screen-off PTT event's keyCode, scanCode and input device to determine
   whether the physical control arrives as Button Jack `KEY_MEDIA` or raw GPIO F1/F2.
2. Connect T88, capture its hardware profile and repeat provisioning, boot, room and PTT tests.
3. Exercise two or more room presets, denied-room fallback and safe room switching during traffic.
4. Add explicit previous-config rollback, signature verification, idle-only config activation and
   network-return refresh/reconnect evidence.
5. Decide the dedicated radio flavor/application ID before production provisioning.
6. Add an instrumentation/manual acceptance pass for screen-off PTT, network loss and a
   120-second watchdog timeout.

The detailed Technical Brief comparison and implementation order are maintained in
`docs/TECHNICAL_BRIEF_GAP_ANALYSIS.md`. The bounded Sol/Luna delegation contract is in
`docs/CODEX_WORKFLOW.md`.

## Important safety rules

- Never commit the Mumble access token, GitHub credentials, private keys or device-specific secrets.
- Do not attempt to rewrite the T99 USB/ADB serial from an unprivileged script; use ADB
  `transport_id` to disambiguate identical units and use Minimum Device ID for app identity.
- Keep the successful T99 screen-off PTT acceptance result, but do not label its OEM key path as
  F1/F2 or KEY_MEDIA until an actual trace identifies it.
- Keep the normal Mumla build working while the radio interface is developed.
- Do not merge PR #1 without explicit user approval.
