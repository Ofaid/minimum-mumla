# Minimum acceptance matrix

| Area | Current result | Evidence / next action |
|---|---|---|
| FOSS debug unit tests | PASS | `:app:testFossDebugUnitTest` |
| FOSS debug APK build | PASS | `:app:assembleFossDebug` |
| T99 ADB install | PASS | T99 serial `12344321` |
| T99 Device ID format/persistence | PASS | `DeviceIdentityManagerTest`; startup integration added |
| Automatic certificate generation | IMPLEMENTED | Must rerun only on a disposable fresh app-data test |
| Media-style PTT foreground | IMPLEMENTED | Service MediaSession path |
| T99 physical PTT identity | PASS | Physical button is gpio-keys `KEYCODE_F1` 131, scan 59, deviceId 4, source 0x101; F2 is EXIT |
| T99 physical screen-off PTT | PASS (operator observed) | F1 path works with screen off; exact foreground Android metadata and release captured subsequently |
| T99 F2 EXIT isolation | PASS IN CODE/BEHAVIOR | T99 forcibly defaults to F1, rejects F2 as PTT and routes F2 to recovery dashboard |
| Media/headset screen-off PTT | IMPLEMENTED ALTERNATE | MediaSession remains active for headset/media PTT alternatives; not the labelled T99 PTT button |
| PTT watchdog | IMPLEMENTED | 120-second service safety path; add long manual test |
| Disconnect releases TX | IMPLEMENTED | Service lifecycle path; add manual screen-off test |
| Boot receiver registration | PASS | Manifest and receiver present |
| T99 simulated boot launch | PASS | Activity appeared after valid simulated broadcast |
| T99 radio dashboard pages | PASS | Installed APK: Minimum -> Settings swipe path |
| T99 ten-button map | PASS | Kernel capture plus Android metadata/keylayout: Power, F1 PTT, volumes, MENU/EXIT, up/down, green/red documented |
| T99 accidental-exit guard | PASS | Red is app-level `KEYCODE_BACK`/scan 60/gpio-keys; release-timestamp fallback makes a 5.06 s hold open dashboard even if UI callback is delayed; green reopens RadioShell |
| T99 room-key hold | PASS WITH ONE ROOM | Raw Up hold >1 s executed once and returned Ready; multi-room direction still needs live evidence |
| PTT dashboard recovery | HOTFIX INSTALLED / PHYSICAL RETEST REQUIRED | First physical run opened RadioShell but cross-window F1 started TX; release-required lockout now arms in service and Activity before launch, build passes and installed app is Ready |
| Managed-radio configured-room TX gate | PASS IN JVM / LIVE TRANSITION OPEN | Pure policy requires synchronized session, PTT mode and verified room readiness for every service input path |
| Arbitrary-app global F1 capture | UNSUPPORTED BY STANDARD API | Requires OEM/privileged input or a tested keylayout remap; MediaSession covers only media-style keys |
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
| Indefinite reconnect policy | PASS NETWORK/PROCESS / EXTENDED MATRIX OPEN | T99 30-second Wi-Fi/LTE outage restored settings and Ready; process watchdog restored PID in 23.9 s and Ready in 30.9 s; server restart/long outage remain |
| T99 process-death watchdog | PASS | Same-UID SIGKILL, no force-stop/manual relaunch/PTT; renewable 30-second lease bypassed OEM roughly 16-minute service restart backoff |
| Reconnect full-screen UI | IMPLEMENTED / T99 VISUAL OPEN | Whole-screen connecting/reconnecting state, attempt count and no false Ready before room join |
| PTT local-delivery warning | IMPLEMENTED / SUPERVISED TEST OPEN | Offline/no-encoded-packet tone and full-screen failure; server receipt still requires a second listener |
| Radio audio defaults | PASS IN CODE / DEVICE AUDIO OPEN | Preprocessor, half-duplex and TTS forced for T99/T88; normal PTT confirmation click forced off while failure alert remains; VAD setter and teardown unmute corrected |
| Thai TTS selection | IMPLEMENTED / DEVICE VOICE OPEN | Requests `th-TH` only when installed engine reports support; radio continues if voice data is missing |
| RX/TX display wake | IMPLEMENTED / T99 OPEN | Five-second wake on RX/TX/disconnect edge; OEM/API-22 behavior requires physical verification |
| Hardware-first RadioShell UI | PASS IN BUILD / T99 VISUAL OPEN | Touch PTT removed, compact 132dp layout, service-owned speaker list and TX elapsed timer |
| Managed self-signed TLS auto-trust | PASS | Cleared old app trust, provisioned no-pin config, app recreated private trust and returned to the exact ready room without a dialog |
| Optional self-signed TLS pin | PASS | Exact SHA-256 pin created app-private trust and connected; mismatch remains fail-closed |
| Active config downgrade rejection | PASS | Repository JVM tests reject a lower version and allow same/newer versions |
| Pending config idle gate | PASS IN JVM | Candidate blocked during RX, TX and connection transitions; service tracker covers talk/shout/whisper and disconnect clear |
| Pending config promotion/rollback | PROMOTION PASS T99 / FAILURE INJECTION OPEN | Active v1002, no pending and previous v1001 physically verified; failed-candidate rollback still needs live injection |
| Screen-off connection persistence | PASS | T99 display OFF/dozing for 10 seconds with `MumlaService` still started and no disconnect |
| Multi-room preset switching | IMPLEMENTED IN CODE | One-second Up/Down hold joins directly; requires a live config with at least two rooms |
| Dark mode default | PASS | Fresh/unset preference resolves to `forceDark`; update installed and visually checked on T99 |
| Radio shell | PASS ON T99 | Dark ready/RX/TX/status UI, full room path, Device ID/profile and enabled touch PTT verified |
| T88 profile | OPEN | Capture actual T88 runtime data |

## Release gate

Do not call the Minimum radio PoC radio-ready until screen-off PTT, network-loss TX release,
certificate first-run, boot behavior and T88/T99 profile selection have each passed on the target
hardware or been explicitly marked as unsupported.
