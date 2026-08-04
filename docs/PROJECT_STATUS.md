# Minimum project status (source of truth)

Last reviewed: 2026-08-04

This is the canonical hand-off document for the `awatchar/minimum` public PoC. If another
document disagrees with this file, verify the code and update this file first.

## Repository and build identity

- Local repository: `D:\VR Android App\mumla`
- Build-safe junction: `D:\mumla-dev` (same working tree; use this path for Gradle/NDK)
- GitHub remote: `https://github.com/awatchar/minimum.git`
- GitLab upstream remote: `https://gitlab.com/quite/mumla.git`
- Humla upstream history is retained in the submodule; Minimum's required Humla commit is published
  as branch `humla-minimum` in the same GitHub repository and `.gitmodules` points there.
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
- Added a best-effort six-hour background refresh scheduler plus a network-return trigger and an
  in-flight guard. Failed attempts do not postpone the next retry and never delay normal startup.
- Remote config now has an explicit Last Known Good lifecycle: validated downloads are staged as
  `pending-config.json`, trialled only while RX/TX and connection transitions are idle, promoted to
  active only after the candidate connects and joins its configured room, and discarded on trial
  failure. The old active config becomes `previous-config.json`; repository rollback and corrupt-
  active recovery are covered by JVM tests.
- RX idleness is service-owned and tracks all remote talking, shouting and whispering sessions, so
  resuming an Activity mid-transmission cannot incorrectly activate a pending config.
- Managed-radio reconnect now covers every unexpected Mumble disconnect with indefinite capped
  backoff (2/4/8/16/32/60 seconds), immediate network-return retry, a 60-second OEM broadcast
  fallback and Android service-intent redelivery after process death. A certificate pin/policy
  failure remains an intentional fail-closed retry hold.
- Replaced the touch PTT screen with a compact full-screen hardware-first UI: whole-screen
  connecting/RX/TX/error states, speaker identity sourced from the long-lived service, room-join
  gating before Ready, connection attempt count and a live TX elapsed timer.
- Managed radios automatically enable PTT mode, input preprocessing, half-duplex playback muting,
  auto-reconnect, PTT confirmation sound and TTS. The Speex VAD setter and half-duplex runtime/
  teardown unmute paths were corrected; Thai TTS is selected when the installed engine provides a
  `th-TH` voice.
- RX, TX and disconnect edges wake the small-radio display for a bounded five seconds. Offline or
  locally undeliverable PTT produces an error tone and full-screen failure state; encoded-packet
  confirmation is explicitly not claimed as remote server receipt.
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
- Pending-config promotion and failure rollback pass JVM tests and APK build, but their physical
  T99 acceptance run remains open because the workstation ADB host wedged before the trial could
  start. The device's active v1001 config stayed unchanged and the v1002 candidate remained staged,
  which is the intended fail-safe state. The final APK containing the service-owned RX tracker was
  built but could not be installed during that wedged ADB session.
- Reconnect, wake-screen, modern UI, half-duplex and PTT-failure behavior pass JVM/build checks but
  still require the physical matrix in `RECONNECT_TEST_PLAN.md`. A full T99 power cycle on
  2026-08-04 did not clear the host wedge: Windows still reported the MI_03 ADB Interface healthy,
  while both platform-tools 36.0.0 and an isolated official 34.0.5 daemon hung identically. The next
  recovery step is a Windows reboot, not a handset reset or app-data clear.
- The reconnect harness now validates that both original network settings are exactly `0` or `1`,
  verifies their restored values, and detects Ready through the stable accessibility marker
  `minimum-state-ready` rather than localized UI text.
- Thai TTS depends on the Android TTS engine and Thai voice data installed on each device. Missing
  Thai data does not block radio operation.
- Boot activity launch can be blocked by newer Android/OEM policy. T99 is API 22 and passed an
  actual reboot-to-ready-room test; a newer-device foreground-service/notification fallback remains
  future work.

## Immediate next work

1. Reconnect USB/ADB, install the latest APK and run `scripts/test-radio-reconnect.ps1`, then execute
   the supervised server-restart, long-outage, process-death, wake-screen and PTT-failure matrix.
2. Capture the successful T99 screen-off PTT event's keyCode, scanCode and input device to determine
   whether the physical control arrives as Button Jack `KEY_MEDIA` or raw GPIO F1/F2.
3. Connect T88, capture its hardware profile and repeat provisioning, boot, room and PTT tests.
4. Exercise two or more room presets, denied-room fallback and safe room switching during traffic.
5. Re-run physical T99 success/failure acceptance for pending config, then add config signatures.
6. Add hidden key/config/audio diagnostics, then decide the dedicated radio flavor/application ID.

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
