# Work log

This short log records meaningful project milestones. Detailed code truth remains in
`PROJECT_STATUS.md` and the source files.

## 2026-08-05 — PTT recovery and deliberate hardware actions

- Added fail-safe PTT recovery from MinimumHome and disconnected RadioShell: alert, foreground
  RadioShell and request connection immediately, but never queue the triggering press for TX.
- Added a service-owned room-ready gate so no Activity or MediaSession PTT path can transmit before
  the configured room join has been verified.
- Added a five-second hold requirement for T99 MENU/EXIT/red and a one-second Up/Down hold that
  selects and joins the configured room without green confirmation.
- Installed and checked on T99 without injecting PTT: short exit keys stayed in RadioShell, long F2
  opened MinimumHome, green reopened RadioShell, long Up returned Ready, and a stopped-process
  recovery intent returned Ready in one second.
- Recorded the standard Android limitation: F1 cannot be captured globally while an unrelated app
  owns the foreground; a global route needs separately verified OEM/privileged/keylayout support.
- Physical dashboard-F1 acceptance found a cross-window race: the old press reached RadioShell and
  started TX. Added a pre-launch service safety action plus Activity/service release-required state,
  rebuilt and installed the hotfix, and left the physical regression honestly open.
- Corrected an earlier red-button misidentification with an isolated physical capture: the T99
  kernel reports matrix-keypad scan 2 / `KEY_BACK`, while its vendor WindowManager remaps the event
  to Android `KEYCODE_DPAD_RIGHT` (22). Added DPAD_RIGHT to the protected five-second exit and
  diagnostic paths while retaining BACK for firmware compatibility.
- Added release-timestamp completion so UI-handler delay cannot cancel a completed hold, and moved
  protected-exit interception ahead of normal Activity dispatch so repeated vendor key events stay
  inside the deliberate-action path. Physical acceptance then passed: the prompt appeared and a
  greater-than-five-second red hold opened MinimumHome.
- Disabled the normal Mumla PTT confirmation click for every managed radio in defaults and runtime;
  verified the installed T99 preference is false while retaining the PTT failure alert.
- Split managed identity roles: config schema 2 now requires an explicit Mumble username instead
  of reusing Device ID. Provisioning treats the six-character Device ID as the Config Profile used
  for device-specific lookup. Current T99 values are profile `GYZ3DE` and Mumble username
  `E25FGL-T99`; operator account `GY3ZDE` remains separate.
- Installed private config v1003 on T99, verified the app preference and active config both use
  `GYZ3DE`, and observed a fresh schema-2 launch return to the exact configured room in Ready state.
  The temporary workstation migration copy (which could contain room access data) was removed.

## 2026-08-04

- Confirmed the working tree and GitHub draft PR #1 for `awatchar/minimum`.
- Added automatic radio PTT defaults and a central multi-key manager for F1/F2 plus media/headset
  keys. F1 was verified from the T99 `gpio-keys` raw event; F2 remains a live verification item.
- Added application-start Device ID creation.
- Added `RadioConfigRepository` with embedded default, HTTPS fetch, merge, validation, cache and
  rollback copy.
- Added the non-blocking six-hour `RadioConfigUpdater` startup refresh.
- Added the non-launcher `RadioShellActivity` with Device ID/profile display and touch PTT bridge.
- Added the hand-off, architecture, runbook, backend contract, test matrix and decision records.
- FOSS debug unit tests and APK build passed after the code changes.
- Applied the user-provided Minimum logo to the Android adaptive icon and added an API 21+
  legacy launcher resource for T99. Changed the launcher label to `Minimum` and retained the
  editable SVG source at `docs/Minimum-app-icon-foreground.svg`.
- Added `MinimumHomeActivity` for T99/T88 with one large icon per swipe page for Minimum and
  Settings. A reboot test exposed an unacceptable Android HOME chooser while Launcher3 remained
  enabled. T99 rejected both a third-party HOME priority and package-disable takeover as reliable
  production solutions. The final design removes Minimum from the HOME resolver, launches the radio
  dashboard explicitly at boot, installs a Minimum recovery shortcut in Launcher3, and verifies no
  ResolverActivity. Reboot and shortcut recovery passed on the physical T99.
- Added physical-key dashboard navigation after the T99 proved non-touch in practice: up/left and
  down/right change pages, while Select/Enter/Call activates the visible page. PTT F1/F2 remain
  isolated from dashboard actions.
- Re-read the complete Technical Brief and added a phase/acceptance-test gap analysis instead of
  treating foundation classes as completed end-to-end features.
- Changed Minimum's unset/invalid theme default to dark while preserving explicit light/system
  choices, then visually verified the updated APK on T99.
- Added and reviewed the bounded Luna worker configuration outside the repository. Its first task
  produced `AccessTokenResolver`; Sol review corrected its test fixtures, added a test-only JSON
  runtime, and the full FOSS unit-test/APK build passed.
- Completed the config-driven radio path: typed validation, downgrade rejection, public-token
  authentication, automatic certificate/connect/reconnect, exact full-path room join, room preset
  navigation and the compact dark ready/RX/TX/access status UI.
- Added optional exact SHA-256 pinning for managed/self-signed Mumble servers. The first unpinned
  T99 connection correctly failed closed; the locally provisioned exact pin allowed the existing
  app-private BKS trust path to connect without disabling TLS verification.
- Extended `prepare-t99.ps1` to install a local config into app-private storage without showing its
  token, apply restrictive permissions and launch the managed radio client.
- Passed end-to-end physical T99 acceptance against the supplied active server: authentication,
  nested full-path room join, green ready state, connection persistence with the display off, and a
  real reboot returning directly to the ready room without a HOME chooser or touch input. No PTT
  transmission was made while the operator was away; F2 and screen-off hardware PTT remain open.
- The operator subsequently proved physical PTT while the T99 display was off. Code/device review
  found Minimum's active MediaSession/service path and no provisioning setting that enables it, so
  no PTT system-setting command was added to `prepare-t99.ps1`. Exact key event identity remains to
  be captured before labelling the path as KEY_MEDIA versus raw F1/F2.
- Replaced pin-required self-signed TLS policy with config-authorized automatic trust by default.
  Normal Android trust still runs first, app-private trust is used for the retry, and an optional
  configured SHA-256 pin remains a stricter fail-closed override.
- Live-tested the new no-pin path by deleting only Minimum's app-private Mumble trust store and
  provisioning a config with automatic trust enabled. T99 recreated the store, reconnected and
  returned to the exact ready room without an operator dialog.
- Implemented the Technical Brief's Last Known Good config lifecycle: network-return refresh,
  non-overlapping fetches, pending staging, RX/TX/transition idle gate, in-memory candidate trial,
  commit only after the configured room is joined, failure fallback and explicit previous rollback.
  RX safety state is now owned by the long-lived service and covers talk, shout and whisper events.
  Repository/updater/policy JVM tests and the FOSS APK build pass. A physical T99 acceptance attempt
  left active v1001 untouched and v1002 safely pending when the workstation ADB host wedged; no
  candidate was falsely marked active. The final APK passed build but remains to be installed after
  USB/ADB is reconnected.
- Hardened the managed-radio lifecycle: retry every unexpected disconnect indefinitely with capped
  exponential backoff, immediate network-return retry plus timer fallback, and service intent
  redelivery after process death. Certificate-policy mismatch remains a fail-closed hold.
- Rebuilt RadioShell as a compact full-screen state surface for the real 132x132 T99 constraint:
  no touch PTT, no false Ready before room join, service-owned speaker names, prominent RX/TX/error
  screens, connection attempt count and elapsed TX timer.
- Enabled radio preprocessing, half-duplex, TTS, PTT confirmation and auto-reconnect automatically;
  corrected the Speex probability setter, half-duplex runtime mode lookup and teardown unmute.
  Thai TTS is selected conditionally on installed engine support.
- Added bounded wake-on-RX/TX/disconnect and a local PTT failure tone/state when offline or no encoded
  packet reaches the synchronized connection. Documented that this is not a server delivery ACK.
- Added `scripts/test-radio-reconnect.ps1` and `docs/RECONNECT_TEST_PLAN.md` for repeatable network,
  server, process, certificate and supervised PTT-failure acceptance. Physical execution remains
  blocked by the workstation ADB host wedge.
- Retried device acceptance after a full T99 power cycle. Windows continued to report the composite
  device and MI_03 ADB Interface as healthy, but ADB daemons from platform-tools 36.0.0 and an
  isolated official 34.0.5 both hung. This isolates the remaining recovery action to a Windows
  USB-stack reboot; no handset reset, app-data clear, network outage or PTT transmission was made.
- Hardened reconnect acceptance before the next physical run: the script rejects ambiguous network
  state, reads both settings back after restoration, and uses a stable ASCII accessibility marker
  from RadioShell instead of localized Thai text. Windows PowerShell 5.1 parsing, JVM tests and the
  FOSS debug APK build pass.
- The first live 30-second network-loss run restored both transport settings and Minimum rejoined
  the configured room, but the harness could not see the Ready marker after the display timed out:
  T99/API-22 `uiautomator` exposes no app nodes while the display is off. The harness now wakes an
  off display only for UI inspection before retrying the marker check.
- After the Windows reboot restored ADB, installed the latest APK and physically verified config
  promotion: active v1002, previous v1001, no pending candidate and Ready in the configured room.
- Passed the guarded 30-second Wi-Fi/LTE-loss acceptance. Both original transport settings were
  restored and read back, then Minimum returned to the stable Ready marker without PTT.
- A baseline same-UID process kill exposed T99's OEM behavior: ActivityManager scheduled service
  redelivery roughly 16 minutes later. Added a profile-gated renewable AlarmManager watchdog lease
  (10-second heartbeat, 30-second expiry) that retries RadioShell startup independently of the dead
  process. The final physical SIGKILL test produced a new PID in 23.9 seconds and Ready in 30.9
  seconds without force-stop, manual post-kill launch, config mutation or PTT.
- Captured all ten physical T99 buttons at the Linux input layer and classified them with the
  installed Android keylayout. Crucially, labelled PTT is gpio-keys F1 while labelled EXIT is F2;
  the prior provisional F1/F2 PTT default could therefore turn EXIT into a short TX.
- Captured the real PTT at Android level as keyCode 131, scanCode 59, deviceId 4, source 0x101,
  `gpio-keys`, including repeat and UP. T99 now forces F1, rejects F2 before stale settings, routes
  MENU/EXIT/green/red according to their physically captured events, and keeps media keys only as
  alternate PTT inputs. Added a bounded app-private hardware-key diagnostic log with no text,
  config, token or audio content.

## Prior milestones

- T99 hardware/ADB/input investigation and sanitized profile documentation.
- MediaSession PTT bridge and 120-second fail-safe watchdog.
- Automatic certificate generation on first run.
- Boot auto-start and simulated T99 boot verification.
- Zello user-0 removal and guarded repeatable removal script.
- Static GitHub Pages configuration backend and workflow.
