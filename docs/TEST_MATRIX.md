# Minimum acceptance matrix

| Area | Current result | Evidence / next action |
|---|---|---|
| FOSS debug unit tests | PASS | `:app:testFossDebugUnitTest` |
| FOSS debug APK build | PASS | `:app:assembleFossDebug` |
| T99 ADB install | PASS | T99 serial `12344321` |
| T99 Device ID format/persistence | PASS | `DeviceIdentityManagerTest`; startup integration added |
| Automatic certificate generation | IMPLEMENTED | Must rerun only on a disposable fresh app-data test |
| Media-style PTT foreground | IMPLEMENTED | Service MediaSession path |
| T99 F1/F2 foreground PTT | IMPLEMENTED | Automatic profile mapping; F1 observed, F2 still needs live press |
| T99 F1/F2 screen-off PTT | NOT PROVEN | Raw gpio-keys path may require OEM/privileged integration |
| T99 raw GPIO screen-off PTT | NOT PROVEN | OEM/input integration may be required |
| Media/headset screen-off PTT | IMPLEMENTED IN CODE | MediaSession path; verify with T99 Button Jack event |
| PTT watchdog | IMPLEMENTED | 120-second service safety path; add long manual test |
| Disconnect releases TX | IMPLEMENTED | Service lifecycle path; add manual screen-off test |
| Boot receiver registration | PASS | Manifest and receiver present |
| T99 simulated boot launch | PASS | Activity appeared after valid simulated broadcast |
| Newer OEM boot policy | OPEN | Add notification/foreground-service fallback |
| Zello user-0 removal | PASS | `pm uninstall --user 0 com.loudtalks` verified |
| Zello repeat script dry run | PASS | `remove-zello-t99.ps1 -WhatIf` |
| Static backend JSON | PASS | Parsed with PowerShell `ConvertFrom-Json` |
| GitHub Pages workflow | CONFIGURED | Deploy occurs after workflow reaches `main` |
| Android config embedded fallback | PASS | Asset + validation in repository |
| Android remote config fetch/cache | IMPLEMENTED | Background six-hour refresh; T99 correctly falls back on old CA failure |
| Remote room path selection | OPEN | Extend URL/server connection contract |
| Remote access-token resolver | OPEN | Keep token local-only |
| Radio shell | FOUNDATION ONLY | Non-launcher activity; exercise on T99 |
| T88 profile | OPEN | Capture actual T88 runtime data |

## Release gate

Do not call the Minimum radio PoC radio-ready until screen-off PTT, network-loss TX release,
certificate first-run, boot behavior and T88/T99 profile selection have each passed on the target
hardware or been explicitly marked as unsupported.
