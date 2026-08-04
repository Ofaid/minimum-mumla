# Technical Brief gap analysis

Last reviewed: 2026-08-04

This document maps the supplied Public PoC Radio Client Technical Brief to the code that actually
exists. `PROJECT_STATUS.md` remains the short hand-off source of truth; this file is the detailed
implementation checklist. A checked item means the behavior exists and has proportionate evidence,
not merely that a class or placeholder exists.

## Executive result

Minimum now has a working end-to-end radio-client slice on T99. Build/branding, persistent public
device identity, Last Known Good config storage/validation, automatic certificates, boot and
provisioning, service-owned PTT safety, token authentication, auto-connect and exact room join are
implemented. This critical path has passed a real server and reboot test:

```text
active radio config
  -> create/select the Mumble server
  -> resolve public access tokens
  -> connect automatically
  -> resolve and join a room by full path
  -> expose RX/TX/connection state in the radio UI
  -> change room from physical preset keys
```

The remaining MVP risk is hardware and lifecycle acceptance rather than basic connection wiring:
screen-off OEM PTT, T88 evidence, multi-room traffic tests, network-loss/recovery evidence and
release hardening.

Screen-off PTT for T99's OEM F1/F2 path is also unproven and must remain labelled unsupported until
a real screen-off input trace succeeds.

## Phase-by-phase status

| Brief phase | Status | Evidence already present | Remaining work |
| --- | --- | --- | --- |
| 0 — Baseline | Partial | Upstream/Humla retained; FOSS debug tests and APK build; Minimum name/icon | Record a clean upstream voice/PTT baseline; choose a distinct application ID; decide radio/diagnostic flavor structure |
| 1 — Radio Shell | Mostly complete | Dark `RadioShellActivity` loads LKG config, auto-connects/reconnects, joins the full-path default room, shows connection/RX/TX/access state and supports touch/physical PTT; real T99 boot-to-ready passed | Exercise multiple room presets and decide dedicated radio application ID/flavor |
| 2 — Device Identity | Mostly complete | Six-character `SecureRandom` identity, persistence, validation and tests | Add protected admin UI/gesture for regeneration and acceptance tests across update/reboot/clear-data |
| 3 — Remote Configuration | Partial | Embedded fallback, HTTPS fetch, default/model/device merge, limits, validation, active/previous/temp files, downgrade rejection/tests, six-hour best-effort refresh, JSON Schema | Explicit rollback API; signature verification; network-return trigger; apply only while RX/TX idle |
| 4 — Rooms and Tokens | Mostly complete | Resolved public tokens feed existing Humla authentication without DB/log persistence; typed presets, exact full-path lookup, default join and direction/select/default navigation; live nested-room T99 test passed | Multi-room live test, idle reconnect on token change and permission-denied/default-room fallback evidence |
| 5 — Hardware PTT | Partial | T99/T88/generic profiles, multi-key defaults, Activity bridge, MediaSession path, key-up/disconnect/service-destroy release, 120 s watchdog | Hardware diagnostics, scancode/source capture UI, key bounce tests, F2 live test, foreground/background/screen-off matrix, OEM/vendor path if T99 F-keys do not reach the service |
| 6 — Hardening | Partial | Automatic client certificate, auto-start, T99 provisioning/launcher recovery, config downgrade rejection and exact self-signed server-certificate pinning | Network-change recovery/backoff evidence, battery-optimization handling, voice prompts, protected-token store, config signatures, sanitized diagnostics/security review |
| 7 — Release | Early | Public GitHub repo, draft PR, GPLv3 source base, architecture/runbook/hardware docs, Pages deployment workflow | Android CI, signed APK, release notes, known-limitations report, instrumentation tests, device video and LTE/Wi-Fi/screen-off/reconnect report |

## Acceptance-test coverage

- Device identity: validation/unit coverage exists; lifecycle acceptance matrix remains manual.
- Config: parsing and downgrade tests exist; explicit rollback, signature, idle apply and broader
  failure-mode tests are incomplete.
- Rooms/tokens: integrated end to end and passed with one nested live room; multiple presets and
  denied-room behavior remain unproven.
- PTT: core service safety exists; T99 screen-on F1 was observed. F2 and screen-off OEM paths remain
  open. Media/headset keys have an Android MediaSession path but still require device evidence.
- Audio: no maintained LTE/Wi-Fi/Bluetooth/manual test report yet.
- Lifecycle: actual T99 reboot-to-ready-room and short display-off connection persistence are
  verified; process death, long network outage and newer Android boot restrictions remain open.

## Implementation order from here

1. Capture T99 F1/F2 while the display is off and decide whether an OEM/privileged bridge is needed;
   no public-API claim should precede evidence.
2. Capture the incoming T88 hardware profile and repeat provisioning, boot, room and PTT tests.
3. Exercise multiple room presets, denied-room fallback, network loss/recovery and watchdog release
   with an operator present.
4. Add explicit previous-config rollback, signed config support, idle-only config activation and
   network-return refresh/reconnect behavior.
5. Add hidden key/config/audio diagnostics before claiming support for additional cheap-radio models.
6. Complete the hardware/lifecycle/security acceptance matrix, then introduce the dedicated application
   ID/flavor and release pipeline in an isolated commit. The application ID must be chosen before
   production provisioning because changing it later creates a separate Android app/data identity.

## Explicit non-goals retained from the brief

No web admin backend, GitHub login/token in the APK, chat, direct message, public server browser,
channel tree in radio UI, recording, firmware flashing/root, Mumble/Opus rewrite, bridge, or OTA APK
update is part of the MVP.
