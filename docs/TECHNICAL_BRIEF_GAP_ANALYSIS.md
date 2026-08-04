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
T88 evidence, multi-room traffic tests, extended lifecycle/audio evidence and
release hardening.

T99 physical screen-off PTT has passed an operator test. Physical capture now identifies the
labelled PTT as raw gpio-keys `KEYCODE_F1` 131 / scan 59 / source 0x101 and identifies F2 as EXIT;
T99 rejects F2 as PTT. MediaSession remains an alternate headset/media path.

## Phase-by-phase status

| Brief phase | Status | Evidence already present | Remaining work |
| --- | --- | --- | --- |
| 0 — Baseline | Partial | Upstream/Humla retained; FOSS debug tests and APK build; Minimum name/icon | Record a clean upstream voice/PTT baseline; choose a distinct application ID; decide radio/diagnostic flavor structure |
| 1 — Radio Shell | Mostly complete | Dark `RadioShellActivity` loads LKG config, auto-connects/reconnects, joins the full-path default room, shows connection/RX/TX/access state and supports touch/physical PTT; real T99 boot-to-ready passed | Exercise multiple room presets and decide dedicated radio application ID/flavor |
| 2 — Device Identity | Mostly complete | Six-character `SecureRandom` identity, persistence, validation and tests | Add protected admin UI/gesture for regeneration and acceptance tests across update/reboot/clear-data |
| 3 — Remote Configuration | Mostly complete | Embedded fallback, HTTPS fetch, default/model/device merge, limits, validation, active/previous/pending cache, downgrade rejection, six-hour plus network-return refresh, service-owned RX/TX idle candidate trial, commit after room join, explicit rollback and JVM tests | Config signature verification and physical success/failure candidate acceptance |
| 4 — Rooms and Tokens | Mostly complete | Resolved public tokens feed existing Humla authentication without DB/log persistence; typed presets, exact full-path lookup, default join and one-second Up/Down hold-to-join; live nested-room T99 test passed | Multi-room live test, idle reconnect on token change and permission-denied/default-room fallback evidence |
| 5 — Hardware PTT | T99 core pass / T88 open | T99 ten-button kernel map, F1 Android metadata, F2 EXIT isolation, private bounded diagnostics, dashboard PTT recovery, immediate reconnect request, five-second accidental-exit guard, Activity/MediaSession paths, local warning, screen wake, TX timer, release paths and 120 s watchdog | T88 capture, physical dashboard-F1 acceptance, complete foreground/background/screen-off matrix and OEM/vendor/global path only if a target requires it |
| 6 — Hardening | Mostly implemented, acceptance incomplete | Automatic certificate/boot, indefinite capped reconnect, guarded T99 network-loss PASS, process watchdog SIGKILL PASS, automatic preprocessor/half-duplex/TTS, teardown unmute, config rollback/downgrade and managed self-signed trust with optional pin | Long-outage/server/audio/wake evidence, battery optimization, bundled voice prompts, protected-token store, config signatures and sanitized diagnostics/security review |
| 7 — Release | Early | Public GitHub repo, draft PR, GPLv3 source base, architecture/runbook/hardware docs, Pages deployment workflow | Android CI, signed APK, release notes, known-limitations report, instrumentation tests, device video and LTE/Wi-Fi/screen-off/reconnect report |

## Acceptance-test coverage

- Device identity: validation/unit coverage exists; lifecycle acceptance matrix remains manual.
- Config: parsing, downgrade, pending promotion, explicit rollback, refresh gating and RX/TX idle
  policy tests exist. Signature verification and physical candidate success/failure evidence remain.
- Rooms/tokens: integrated end to end and passed with one nested live room; multiple presets and
  denied-room behavior remain unproven.
- PTT: T99 physical screen-off PTT passed; the labelled button is captured as F1/scan 59/gpio-keys,
  F2 is EXIT and private key diagnostics are installed. Short accidental exits, five-second F2 hold
  and one-second Up hold passed without TX. Physical F1-from-dashboard and T88 remain open.
- Audio: no maintained LTE/Wi-Fi/Bluetooth/manual test report yet.
- Lifecycle: actual T99 reboot-to-ready-room, short display-off persistence, 30-second network loss
  and process-death watchdog recovery are verified; long outage and newer Android boot restrictions
  remain open.

## Implementation order from here

1. Continue `RECONNECT_TEST_PLAN.md` with server restart, long outage, reconnect visual, wake,
   half-duplex and supervised PTT-failure evidence; network and process-death cases already pass.
2. Capture the incoming T88 hardware profile and repeat provisioning, boot, room and PTT tests; do
   not copy T99 F1/F2 assumptions.
4. Exercise multiple room presets, denied-room fallback and watchdog release with an operator present.
5. Complete pending-config physical acceptance, then add signed config and hidden diagnostics.
6. Complete the hardware/lifecycle/security acceptance matrix, then introduce the dedicated application
   ID/flavor and release pipeline in an isolated commit. The application ID must be chosen before
   production provisioning because changing it later creates a separate Android app/data identity.

## Explicit non-goals retained from the brief

No web admin backend, GitHub login/token in the APK, chat, direct message, public server browser,
channel tree in radio UI, recording, firmware flashing/root, Mumble/Opus rewrite, bridge, or OTA APK
update is part of the MVP.
