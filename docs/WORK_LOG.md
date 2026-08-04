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

## Prior milestones

- T99 hardware/ADB/input investigation and sanitized profile documentation.
- MediaSession PTT bridge and 120-second fail-safe watchdog.
- Automatic certificate generation on first run.
- Boot auto-start and simulated T99 boot verification.
- Zello user-0 removal and guarded repeatable removal script.
- Static GitHub Pages configuration backend and workflow.
