# Engineering decisions

## D-001: Keep Humla/Mumla voice core

The radio client reuses Mumble protocol, TLS, Opus, audio and the existing foreground service. A
small UI does not justify rewriting a proven voice path or creating two PTT safety implementations.

## D-002: Device ID is an app identity, not a hardware identifier

Minimum uses a generated six-character ID persisted in app preferences. It avoids leaking IMEI,
serial or MAC information and is suitable for public device configuration lookup. Clearing app data
generates a new ID.

## D-003: Public config contains policy, not protected secrets

GitHub Pages is public and reviewable. Protected access tokens remain device-local. Future server
authentication must use a secure reference or provisioning flow rather than committing a token.

## D-004: PTT safety is service-owned

The service releases TX on key-up, disconnect, service destruction and watchdog timeout. UI and
hardware bridges call the service but do not duplicate its timing or safety state.

## D-005: Do not guess OEM key mappings

T99 exposes several Linux input devices and Android may suppress or transform events. A mapping is
added only after a live foreground/background/screen-off trace. Unknown keys remain unclaimed.

## D-006: User-0 Zello uninstall is the reversible practical operation

The T99 system partition is not modified. `pm uninstall --user 0` removes Zello for the working user
and the script verifies the exact package/device before acting. Factory reset may restore it.

## D-007: Radio dashboard is explicit; Launcher3 is the recovery HOME

T99/T88 devices need a visible route to Minimum even when the OEM Launcher3 workspace only contains
Settings. `MinimumHomeActivity` provides one large action per swipe page for Minimum and Settings.
T99's OEM resolver does not offer the data-installed Minimum activity as a usable default HOME and
therefore displays a broken chooser. Minimum does not register as HOME. Boot and provisioning open
the dashboard explicitly, while a legacy shortcut adds Minimum to Launcher3 for recovery. The
script fails if system HOME still exposes ResolverActivity.

## D-008: Embedded default always exists

Remote configuration is optional. The app must start with a validated embedded configuration even if
GitHub Pages, DNS, or the network is unavailable.

## D-009: Radio PTT defaults are automatic, but screen-off capability is evidence-based

Supported radio profiles initialize PTT mode and known alternatives at application startup. Live T99
capture overrides the earlier generic assumption: F1 is PTT and F2 is EXIT. T99 therefore forces F1
and rejects F2 regardless of stale preferences. Media/headset keys use MediaSession as alternates;
T88 remains provisional until its own trace exists.

## D-010: Validated external config authorizes automatic server-certificate trust

Minimum first uses Android's normal TLS trust. If it rejects a configured Mumble server and
`autoTrustServerCertificate` is true (the default), the presented leaf certificate is placed in the
existing app-private BKS trust store and the connection is retried without an operator dialog. The
external config is the intended trust boundary for choosing the server. An optional configured
SHA-256 fingerprint is stricter: when present, it must match and automatic trust cannot bypass it.
No system CA store or global TLS verifier is disabled. This policy means config-source compromise
can redirect devices; signed config remains future hardening.

## D-011: Downloaded config is not Last Known Good until the radio proves it

A validated remote response is staged as pending and trialled only when connection state, RX and TX
are idle. It becomes active only after Minimum connects and joins the configured room. Connection,
permission, room or TLS failure discards the candidate and reconnects with the prior active config.
Promotion retains the old active file as previous for explicit rollback. This separates transport
and schema validity from operational validity and prevents a bad backend edit from bricking a radio.

## D-012: Managed radios retry indefinitely, except across a trust-policy failure

Transport, server reject, kick and other unexpected disconnects use capped exponential backoff and
continue until Mumble synchronizes. Network return attempts immediately, with a timer fallback for
OEM broadcast loss, and process death redelivers the connection intent. A certificate pin or trust-
policy mismatch is not treated as availability failure: retry is held to avoid weakening fail-closed
TLS behavior.

## D-013: PTT delivery warning is local evidence, not a server receipt

Minimum warns when PTT is pressed while unsynchronized or when no encoded audio packet is handed to
the synchronized Mumble connection within the confirmation window. Mumble voice has no handset-
visible per-packet acknowledgement, so end-to-end acceptance still requires a second client or
server-side observer. Documentation and UI must not claim that local packet handoff proves a remote
listener heard the audio.

## D-014: Dedicated radios use a renewable process watchdog lease

`START_REDELIVER_INTENT` remains the first service-recovery mechanism, but OEM firmware may impose a
restart backoff that is unacceptable for PTT availability. T99/T88 services therefore renew a
30-second AlarmManager lease every 10 seconds. Lease expiry explicitly reopens RadioShell and
re-arms recovery until the service heartbeat resumes. The lease is profile-gated and modern Android
uses an inexact permission-free alarm fallback rather than requiring special exact-alarm access.
