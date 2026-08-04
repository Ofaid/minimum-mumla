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

## Prior milestones

- T99 hardware/ADB/input investigation and sanitized profile documentation.
- MediaSession PTT bridge and 120-second fail-safe watchdog.
- Automatic certificate generation on first run.
- Boot auto-start and simulated T99 boot verification.
- Zello user-0 removal and guarded repeatable removal script.
- Static GitHub Pages configuration backend and workflow.
