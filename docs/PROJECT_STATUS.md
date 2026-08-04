# Minimum project status (source of truth)

Last reviewed: 2026-08-05

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
- Added T99/T88/generic device profile detection and a central multi-key PTT manager. T99 physical
  capture proves PTT is `gpio-keys` F1 and EXIT is F2, so T99 now accepts only F1 plus media/headset
  alternates and forcibly resets its managed push-key preference to F1. T88 temporarily retains
  F1/F2 plus media defaults until the real device is captured.
- Added the six-character persistent public Device ID and unit tests. It is now created at app
  process startup by `MumlaApplication`; provisioning may assign this lookup identity as the
  user-facing Config Profile without changing any USB serial.
- Added MediaSession handling for Android public media-style PTT keys.
- Added fail-safe PTT recovery. F1 received by the T99 recovery dashboard opens RadioShell, sounds
  a local failure alert and requests an immediate connection; a disconnected RadioShell does the
  same. The triggering press is never queued for later TX, so the operator must press again after
  Ready. A service-backed release-required lock is armed before the Activity transition and cleared
  only after key-up, preventing the original press from becoming a new RadioShell DOWN event.
- Added a 120-second PTT watchdog, release-on-disconnect/service-destroy behavior and lockout until
  the physical key is released after a timeout.
- Added a service-owned managed-radio TX gate: synchronization, PTT mode and verified entry into the
  configured room must all be true before Activity or MediaSession input can start transmission.
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
  fallback and Android service-intent redelivery after process death. T99/T88 also renew a
  process-independent 30-second AlarmManager lease every 10 seconds because T99 firmware can defer
  a killed service restart for roughly 16 minutes. A certificate pin/policy failure remains an
  intentional fail-closed retry hold.
- Replaced the touch PTT screen with a compact full-screen hardware-first UI: whole-screen
  connecting/RX/TX/error states, speaker identity sourced from the long-lived service, room-join
  gating before Ready, connection attempt count and a live TX elapsed timer.
- Managed radios automatically enable PTT mode, input preprocessing, half-duplex playback muting,
  auto-reconnect and TTS, while suppressing the normal Mumla PTT confirmation click. The Speex VAD setter and half-duplex runtime/
  teardown unmute paths were corrected; Thai TTS is selected when the installed engine provides a
  `th-TH` voice.
- RX, TX and disconnect edges wake the small-radio display for a bounded five seconds. Offline or
  locally undeliverable PTT produces an error tone and full-screen failure state; encoded-packet
  confirmation is explicitly not claimed as remote server receipt.
- Added `MinimumHomeActivity` as the small-device recovery dashboard with one large icon per swipe
  page: Minimum and Settings. T99 F1 reopens the radio from this dashboard, and physical green
  activates the visible page. It is intentionally not an Android HOME handler because the T99 OEM
  resolver excludes it and displays an unusable chooser.
- Completed the dark `RadioShellActivity`: it loads the Last Known Good config, silently ensures a
  client certificate, connects/reconnects automatically, authenticates with resolved public room
  tokens, resolves the default room by its exact full path, joins it, and displays offline,
  connecting, ready, RX, TX and access-denied states. Holding Up/Down for one second selects and
  joins the adjacent configured room without confirmation. MENU (`DPAD_CENTER`), EXIT (F2) and red
  (vendor-remapped `DPAD_RIGHT`) must be held for five seconds before opening the recovery dashboard; physical green
  (`KEY_MENU`) remains an immediate confirm/rejoin control.
- Added config-authorized automatic trust for managed/self-signed Mumble servers. Normal Android
  trust is attempted first; on failure, `autoTrustServerCertificate` defaults to true, stores the
  presented leaf certificate app-privately and retries without a dialog. An optional SHA-256 pin is
  stricter and a mismatch is still refused.
- Added config-version downgrade rejection and JVM tests for config parsing, downgrade behavior,
  token handling and full-path room resolution.
- Advanced the config contract to schema 2 and made `mumble.username` explicit and independent
  from the six-character Config Profile/device lookup key. The current T99 uses Config Profile
  `GYZ3DE` and Mumble username `E25FGL-T99`.
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

- Physical PTT while the T99 display is off has passed an operator test. A subsequent physical trace
  classified the labelled control as `KEYCODE_F1` 131 / scan 59 / deviceId 4 / source `0x101` /
  `gpio-keys`; repeat DOWN events occur while held and a normal UP releases TX. F2 is physically
  EXIT and is explicitly excluded from T99 PTT.
- T88 has no runtime capture yet. Do not add T88 keycodes or USB values until the real device is
  connected and inspected.
- Android cannot route an arbitrary F1 key to an ordinary app while another application owns the
  foreground. Instant T99 PTT recovery is therefore implemented for RadioShell and MinimumHome; a
  truly global path would require a separately tested OEM broadcast, privileged integration or
  provisioning-time keylayout remap. Media/headset keys already have the public MediaSession path.
- The first physical F1-from-dashboard acceptance exposed a cross-window event race: RadioShell
  opened and the original held press began TX. The installed hotfix now arms service and Activity
  release lockouts before launch and can force-stop any accidental TX; physical regression retest
  remains required before this path is marked PASS.
- The physical red-button path has passed acceptance. An isolated capture proved kernel scan 2 /
  `KEY_BACK` is vendor-remapped to Android `KEYCODE_DPAD_RIGHT`; the installed protected-exit path
  displayed the hold prompt and opened MinimumHome after a greater-than-five-second hold.
- Multiple configured room presets and one-second hold switching are implemented but have only
  been exercised with one live room. Permission-denied fallback and room changes during real
  traffic still need a multi-room acceptance test.
- The checked-in backend intentionally has `autoConnect: false`, a placeholder host and no access
  token. The successful endpoint configuration remains local/device-private.
- T88 boot/dashboard and legacy shortcut behavior still require live-device verification. Each new
  firmware must pass the no-ResolverActivity provisioning check.
- Pending-config promotion and failure rollback pass JVM tests. Physical T99 inspection after the
  schema-2 identity migration showed active v1003 with Config Profile `GYZ3DE` and Mumble username
  `E25FGL-T99`; a fresh launch connected and joined the configured room as Ready. A physical
  failed-candidate rollback injection remains open.
- The workstation reboot cleared the Windows USB/ADB wedge. The latest APK was installed and the
  guarded 30-second Wi-Fi/LTE outage restored both original settings and returned to Ready without
  operator intervention. A same-UID SIGKILL initially exposed T99's roughly 16-minute OEM service
  restart backoff; after adding the renewable watchdog lease, a second physical run produced a new
  PID in 23.9 seconds and restored RadioShell/Ready in 30.9 seconds without `am start` or PTT.
- Wake-screen, reconnect visual state, half-duplex and PTT-failure behavior still require the
  remaining supervised matrix in `RECONNECT_TEST_PLAN.md`.
- The reconnect harness now validates that both original network settings are exactly `0` or `1`,
  verifies their restored values, and detects Ready through the stable accessibility marker
  `minimum-state-ready` rather than localized UI text.
- Thai TTS depends on the Android TTS engine and Thai voice data installed on each device. Missing
  Thai data does not block radio operation.
- Boot activity launch can be blocked by newer Android/OEM policy. T99 is API 22 and passed an
  actual reboot-to-ready-room test; a newer-device foreground-service/notification fallback remains
  future work.

## Immediate next work

1. With the operator present, verify one physical F1 press from MinimumHome opens/reconnects without
   TX, then press again only after Ready; decide whether T99 provisioning needs a tested global
   keylayout/OEM path for unrelated foreground apps.
2. Execute the supervised server-restart, long-outage, reconnect visual, wake-screen, half-duplex
   and PTT-failure portions of `RECONNECT_TEST_PLAN.md`.
3. Connect T88, capture its hardware profile and repeat provisioning, boot, room and PTT tests.
4. Exercise two or more room presets, denied-room fallback and safe room switching during traffic.
5. Run physical failed-candidate rollback acceptance, then add config signatures.
6. Extend the new private key diagnostics with config/audio health, then decide the dedicated radio
   flavor/application ID.

The detailed Technical Brief comparison and implementation order are maintained in
`docs/TECHNICAL_BRIEF_GAP_ANALYSIS.md`. The bounded Sol/Luna delegation contract is in
`docs/CODEX_WORKFLOW.md`.

## Important safety rules

- Never commit the Mumble access token, GitHub credentials, private keys or device-specific secrets.
- Do not attempt to rewrite the T99 USB/ADB serial from an unprivileged script; use ADB
  `transport_id` to disambiguate identical units and use Minimum Device ID for app identity.
- Keep T99 F2 permanently reserved for physical EXIT; do not copy the T99 F1 mapping to T88 without
  a real T88 trace.
- Keep the normal Mumla build working while the radio interface is developed.
- Do not merge PR #1 without explicit user approval.
