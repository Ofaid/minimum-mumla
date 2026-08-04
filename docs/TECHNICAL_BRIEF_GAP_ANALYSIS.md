# Technical Brief gap analysis

Last reviewed: 2026-08-04

This document maps the supplied Public PoC Radio Client Technical Brief to the code that actually
exists. `PROJECT_STATUS.md` remains the short hand-off source of truth; this file is the detailed
implementation checklist. A checked item means the behavior exists and has proportionate evidence,
not merely that a class or placeholder exists.

## Executive result

Minimum has a useful foundation, but it is not yet the Technical Brief MVP. The strongest completed
areas are build/branding, persistent public device identity, remote-config storage/validation,
automatic certificates, boot/provisioning for T99, and service-owned PTT safety. The critical path
still missing is:

```text
active radio config
  -> create/select the Mumble server
  -> resolve public access tokens
  -> connect automatically
  -> resolve and join a room by full path
  -> expose RX/TX/connection state in the radio UI
  -> change room from physical preset keys
```

Screen-off PTT for T99's OEM F1/F2 path is also unproven and must remain labelled unsupported until
a real screen-off input trace succeeds.

## Phase-by-phase status

| Brief phase | Status | Evidence already present | Remaining work |
| --- | --- | --- | --- |
| 0 — Baseline | Partial | Upstream/Humla retained; FOSS debug tests and APK build; Minimum name/icon | Record a clean upstream voice/PTT baseline; choose a distinct application ID; decide radio/diagnostic flavor structure |
| 1 — Radio Shell | Partial | `RadioShellActivity` has Device ID/profile and touch press-and-hold PTT; T99 dashboard is recoverable | Make it the real radio screen; connect from config; join default room; show connection/RX/TX states; remove normal Mumla navigation from the radio build |
| 2 — Device Identity | Mostly complete | Six-character `SecureRandom` identity, persistence, validation and tests | Add protected admin UI/gesture for regeneration and acceptance tests across update/reboot/clear-data |
| 3 — Remote Configuration | Partial | Embedded fallback, HTTPS fetch, default/model/device merge, limits, validation, active/previous/temp files, six-hour best-effort refresh, JSON Schema | Add repository unit tests; reject config downgrade; explicit rollback API; signature verification; network-return trigger; apply only while RX/TX idle |
| 4 — Rooms and Tokens | Partial | Existing Mumla token transport remains available; tested `AccessTokenResolver` handles public token cleanup/deduplication without exposing protected references | Integrate resolver with connection, model room presets, full-path channel lookup, P1/P2/P3 selection, idle reconnect on token change, permission-denied/default-room fallback |
| 5 — Hardware PTT | Partial | T99/T88/generic profiles, multi-key defaults, Activity bridge, MediaSession path, key-up/disconnect/service-destroy release, 120 s watchdog | Hardware diagnostics, scancode/source capture UI, key bounce tests, F2 live test, foreground/background/screen-off matrix, OEM/vendor path if T99 F-keys do not reach the service |
| 6 — Hardening | Partial | Automatic certificate, auto-start, T99 provisioning and launcher recovery | Network-change recovery/backoff evidence, battery-optimization handling, voice prompts, protected-token store, signature/downgrade protection, sanitized diagnostics/security review |
| 7 — Release | Early | Public GitHub repo, draft PR, GPLv3 source base, architecture/runbook/hardware docs, Pages deployment workflow | Android CI, signed APK, release notes, known-limitations report, instrumentation tests, device video and LTE/Wi-Fi/screen-off/reconnect report |

## Acceptance-test coverage

- Device identity: validation/unit coverage exists; lifecycle acceptance matrix remains manual.
- Config: happy-path implementation exists, but rollback, downgrade, idle apply and failure-mode tests
  are incomplete.
- Rooms/tokens: not integrated end to end.
- PTT: core service safety exists; T99 screen-on F1 was observed. F2 and screen-off OEM paths remain
  open. Media/headset keys have an Android MediaSession path but still require device evidence.
- Audio: no maintained LTE/Wi-Fi/Bluetooth/manual test report yet.
- Lifecycle: T99 boot/dashboard path is verified; process death, long network outage and newer Android
  boot restrictions remain open.

## Implementation order from here

1. Complete and unit-test pure config helpers: access-token resolution, config downgrade rejection,
   explicit previous-config rollback, and validation failure cases.
2. Add a radio connection coordinator that loads Last Known Good config immediately and adapts it to
   the existing `ServerConnectTask`/`MumlaService`; do not rewrite Mumble or Opus.
3. Resolve the configured default room by full channel path after synchronization, then add preset
   selection and safe fallback without allowing room changes during TX.
4. Replace the placeholder radio shell with the single dark radio status screen and make it the
   T99/T88 Minimum action target only after auto-connect is reliable.
5. Add hidden key/config/audio diagnostics before claiming support for additional cheap-radio models.
6. Run the hardware/lifecycle/security acceptance matrix, then introduce the dedicated application
   ID/flavor and release pipeline in an isolated commit. The application ID must be chosen before
   production provisioning because changing it later creates a separate Android app/data identity.

## Explicit non-goals retained from the brief

No web admin backend, GitHub login/token in the APK, chat, direct message, public server browser,
channel tree in radio UI, recording, firmware flashing/root, Mumble/Opus rewrite, bridge, or OTA APK
update is part of the MVP.
