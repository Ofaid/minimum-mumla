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

## D-007: Small-device home is optional and recoverable

T99/T88 devices need a visible route to Minimum even when the OEM Launcher3 workspace only contains
Settings. `MinimumHomeActivity` provides one large action per swipe page and an explicit System Home
fallback. Provisioning opens it but does not disable or silently replace Launcher3, because T99 API
22 has no reliable shell command for changing the default HOME handler.

## D-008: Embedded default always exists

Remote configuration is optional. The app must start with a validated embedded configuration even if
GitHub Pages, DNS, or the network is unavailable.

## D-009: Radio PTT defaults are automatic, but screen-off capability is evidence-based

Supported radio profiles initialize PTT mode and their known alternative keys at application startup.
F1/F2 use the normal key path when the Activity is focused, while media/headset keys use MediaSession
for the public screen-off path. F1/F2 are not advertised as screen-off capable until a real device
trace or an OEM/privileged bridge proves that the events reach the service.
