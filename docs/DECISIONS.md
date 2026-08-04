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

## D-007: Standard launcher stays stable during radio-shell work

The current Radio shell is non-launcher. This lowers regression risk while the T99/T88 hardware
contract and connection flow are still being measured. A dedicated flavor is a later deliberate
decision, not an accidental replacement of Mumla.

## D-008: Embedded default always exists

Remote configuration is optional. The app must start with a validated embedded configuration even if
GitHub Pages, DNS, or the network is unavailable.
