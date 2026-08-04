# Work log

This short log records meaningful project milestones. Detailed code truth remains in
`PROJECT_STATUS.md` and the source files.

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
  candidate was falsely marked active.

## Prior milestones

- T99 hardware/ADB/input investigation and sanitized profile documentation.
- MediaSession PTT bridge and 120-second fail-safe watchdog.
- Automatic certificate generation on first run.
- Boot auto-start and simulated T99 boot verification.
- Zello user-0 removal and guarded repeatable removal script.
- Static GitHub Pages configuration backend and workflow.
