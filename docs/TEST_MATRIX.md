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
| T99 physical screen-off PTT | PASS (operator observed) | Minimum PttMediaSession is active; exact keyCode/source was not captured |
| T99 F1/F2 screen-off event identity | NOT CLASSIFIED | Capture whether the successful physical control arrives as KEY_MEDIA or raw gpio-keys F1/F2 |
| T99 raw GPIO screen-off PTT | NOT PROVEN | OEM/input integration may be required |
| Media/headset screen-off PTT | PASS/LIKELY PATH | Implemented MediaSession path plus successful physical test; capture exact event identity |
| PTT watchdog | IMPLEMENTED | 120-second service safety path; add long manual test |
| Disconnect releases TX | IMPLEMENTED | Service lifecycle path; add manual screen-off test |
| Boot receiver registration | PASS | Manifest and receiver present |
| T99 simulated boot launch | PASS | Activity appeared after valid simulated broadcast |
| T99 radio dashboard pages | PASS | Installed APK: Minimum -> Settings swipe path |
| T99 dashboard physical navigation | IMPLEMENTED | DPAD up/down page change; Select/Enter/Call activate; verify live green key |
| T99 system HOME chooser | PASS | Launcher3 opens directly; ResolverActivity absent |
| T99 Launcher3 recovery shortcut | PASS | Minimum and Settings icons visible after provisioning |
| T99 reboot radio client | PASS | Real reboot resumes `RadioShellActivity`, auto-connects and rejoins the exact configured room; no chooser |
| Newer OEM boot policy | OPEN | Add notification/foreground-service fallback |
| Zello user-0 removal | PASS | `pm uninstall --user 0 com.loudtalks` verified |
| Zello repeat script dry run | PASS | `remove-zello-t99.ps1 -WhatIf` |
| Static backend JSON | PASS | Parsed with PowerShell `ConvertFrom-Json` |
| GitHub Pages workflow | CONFIGURED | Deploy occurs after workflow reaches `main` |
| Android config embedded fallback | PASS | Asset + validation in repository |
| Android remote config fetch/cache | PASS IN JVM / DEVICE ACCEPTANCE OPEN | Six-hour and network-return refresh, in-flight guard, pending staging and LKG fallback |
| Public access-token resolver | PASS | JVM tests cover trimming, case, ordering, duplicates and malformed/protected entries |
| Remote room path selection | PASS | Exact full-path resolver JVM tests plus live T99 join to the supplied nested room |
| Connection-time token integration | PASS | Live T99 authentication through existing Humla extras; token remained local and was not logged |
| Config-driven auto-connect/reconnect | PASS | Live T99 ready state and automatic return after a real reboot |
| Indefinite reconnect policy | PASS IN JVM / T99 MATRIX OPEN | Retry-all policy plus capped 2–60 s backoff tests; network-return fallback and process-intent redelivery build successfully |
| Reconnect full-screen UI | IMPLEMENTED / T99 VISUAL OPEN | Whole-screen connecting/reconnecting state, attempt count and no false Ready before room join |
| PTT local-delivery warning | IMPLEMENTED / SUPERVISED TEST OPEN | Offline/no-encoded-packet tone and full-screen failure; server receipt still requires a second listener |
| Radio audio defaults | PASS IN CODE / DEVICE AUDIO OPEN | Preprocessor, PTT confirmation, half-duplex and TTS forced for T99/T88 profiles; VAD setter and teardown unmute corrected |
| Thai TTS selection | IMPLEMENTED / DEVICE VOICE OPEN | Requests `th-TH` only when installed engine reports support; radio continues if voice data is missing |
| RX/TX display wake | IMPLEMENTED / T99 OPEN | Five-second wake on RX/TX/disconnect edge; OEM/API-22 behavior requires physical verification |
| Hardware-first RadioShell UI | PASS IN BUILD / T99 VISUAL OPEN | Touch PTT removed, compact 132dp layout, service-owned speaker list and TX elapsed timer |
| Managed self-signed TLS auto-trust | PASS | Cleared old app trust, provisioned no-pin config, app recreated private trust and returned to the exact ready room without a dialog |
| Optional self-signed TLS pin | PASS | Exact SHA-256 pin created app-private trust and connected; mismatch remains fail-closed |
| Active config downgrade rejection | PASS | Repository JVM tests reject a lower version and allow same/newer versions |
| Pending config idle gate | PASS IN JVM | Candidate blocked during RX, TX and connection transitions; service tracker covers talk/shout/whisper and disconnect clear |
| Pending config promotion/rollback | PASS IN JVM / T99 OPEN | Atomic active/previous rotation and explicit rollback tests pass; physical candidate trial awaits a healthy ADB host |
| Screen-off connection persistence | PASS | T99 display OFF/dozing for 10 seconds with `MumlaService` still started and no disconnect |
| Multi-room preset switching | IMPLEMENTED IN CODE | Direction/select/default keys exist; requires a live config with at least two rooms |
| Dark mode default | PASS | Fresh/unset preference resolves to `forceDark`; update installed and visually checked on T99 |
| Radio shell | PASS ON T99 | Dark ready/RX/TX/status UI, full room path, Device ID/profile and enabled touch PTT verified |
| T88 profile | OPEN | Capture actual T88 runtime data |

## Release gate

Do not call the Minimum radio PoC radio-ready until screen-off PTT, network-loss TX release,
certificate first-run, boot behavior and T88/T99 profile selection have each passed on the target
hardware or been explicitly marked as unsupported.
