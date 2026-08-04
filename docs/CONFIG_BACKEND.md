# Configuration backend and client contract

## Public files

The checked-in public distribution is under `backend/` and is published by GitHub Pages:

```text
/manifest.json
/default.json
/models/{modelProfile}.json
/devices/{deviceId}.json
```

Expected base URL after Pages deploy: `https://awatchar.github.io/minimum/`.

The Android implementation is `RadioConfigRepository`. It exposes `loadActiveOrDefault()` and a
worker-thread `refresh(deviceId, modelProfile)` method. The user-facing device "Config Profile" is
the same six-character `deviceId` lookup key used by `/devices/{deviceId}.json`; it is independent
from both the hardware model profile and the Mumble login name. `RadioConfigUpdater` schedules a best-effort
refresh every six hours and whenever the network returns, without delaying startup. `RadioShellActivity`
loads the Last Known Good result immediately, connects through the existing foreground service,
passes resolved public tokens at authentication time and joins the configured default room by its
exact full path.

The T99 is Android 5.1/API 22 and its bundled CA store currently rejects the GitHub Pages
certificate in the live-device test. This is handled as an ordinary refresh failure: no insecure
TLS fallback is allowed, and the embedded/cache configuration remains in use. Re-test on T88 and a
modern Android device before choosing whether a reviewed CA-store update is needed.

## Validation and rollback rules

- Base URL must be HTTPS and end in `/`; redirects are not followed.
- Each response is limited to 262,144 bytes.
- Schema version must be 2 and config version must be positive. Version 2 makes
  `mumble.username` mandatory so the server login is never inferred from the config lookup key.
- Device IDs must be `*` or six uppercase alphanumeric characters with at least one letter and digit.
- Mumble username must be nonblank, at most 128 characters and contain no control characters.
- Mumble port must be 1..65535.
- Host names contain only DNS-safe letters, digits, dots and hyphens.
- Room paths are absolute, normalized, no longer than 512 characters and limited to 16 presets.
- `autoTrustServerCertificate` is boolean and defaults to true. After normal Android TLS trust
  fails, the configured endpoint's presented leaf certificate is stored app-privately and the
  connection is retried without an operator dialog.
- Optional `serverCertificateSha256` is exactly 64 hexadecimal digits (separators are accepted by
  the Android client). It is a stricter policy than auto-trust: when present, a mismatch fails
  closed.
- PTT maximum is 1..120 seconds.
- `releaseOnNetworkLoss` must be true.
- Device-specific files are optional; missing files are ignored.
- A validated download is staged as app-private `files/radio-config/pending-config.json`; download
  success alone never changes the running Last Known Good config.
- Pending config is trialled only while no connection transition, RX or TX is active. It becomes
  `active-config.json` only after the candidate connects and joins its selected room.
- On successful promotion, the old active file is retained as `previous-config.json`. A failed
  candidate is discarded and the radio reconnects with active; explicit previous rollback is also
  available in the repository.
- A lower `configVersion` is rejected while a valid newer active config exists.
- Changed content at the same `configVersion` is rejected; every effective backend change must
  advance the version.
- A bad cache falls back to the embedded asset `app/src/main/assets/radio/default.json`.

## Secrets and test server

The supplied test endpoint is:

```text
Host: tsecloud.in.th
Port: 64738
Room: /Amateur Radio/HS1AB - ICOM RS-BA1
```

The access token is intentionally not written here, in GitHub, in logs or in the public backend.
Keep it in the local Mumla database or another device-local secret store. The existing Mumble URL
parser currently handles host/port but not room-path selection; room and token resolver work is
now handled by the managed radio shell. The successful T99 test config remains outside the
repository and is installed into app-private storage with `prepare-t99.ps1 -RadioConfigPath`.

For the current T99, the provisioned Config Profile/device lookup key is `GYZ3DE`, while
`mumble.username` is `E25FGL-T99`. The operator's separate Mumble account `GY3ZDE` is not used as
the managed device login. Future backend/database implementations must preserve these distinct
fields instead of deriving one from another. The public, non-secret identity override is
`backend/devices/GYZ3DE.json`; server endpoint, room access and token data remain device-private.

Automatic certificate trust intentionally makes the validated external configuration the trust
boundary for the Mumble endpoint. The current config transport is HTTPS but config signatures are
not implemented yet; compromising the config source could redirect a device to another server.

## Backend change checklist

1. Edit the appropriate JSON and keep it schema-valid.
2. Advance `configVersion` for every effective change and run JSON/schema checks locally.
3. Keep `autoConnect` false until the endpoint and room policy are reviewed.
4. Do not add protected tokens to a public commit.
5. Deploy only through a reviewed GitHub PR.
