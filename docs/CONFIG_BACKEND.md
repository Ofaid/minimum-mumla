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
worker-thread `refresh(deviceId, modelProfile)` method. `RadioConfigUpdater` schedules a best-effort
refresh every six hours from `MumlaApplication` without delaying startup. `RadioShellActivity`
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
- Schema version must be 1 and config version must be positive.
- Device IDs must be `*` or six uppercase alphanumeric characters with at least one letter and digit.
- Mumble port must be 1..65535.
- Host names contain only DNS-safe letters, digits, dots and hyphens.
- Room paths are absolute, normalized, no longer than 512 characters and limited to 16 presets.
- Optional `serverCertificateSha256` is exactly 64 hexadecimal digits (separators are accepted by
  the Android client). It pins one managed/self-signed Mumble server certificate; a mismatch fails
  closed.
- PTT maximum is 1..120 seconds.
- `releaseOnNetworkLoss` must be true.
- Device-specific files are optional; missing files are ignored.
- The active cache is in app-private `files/radio-config/active-config.json`.
- Before activation, the old active file is kept as `previous-config.json`.
- A lower `configVersion` is rejected while a valid newer active config exists.
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

## Backend change checklist

1. Edit the appropriate JSON and keep it schema-valid.
2. Run JSON parsing and schema checks locally.
3. Keep `autoConnect` false until the endpoint and room policy are reviewed.
4. Do not add protected tokens to a public commit.
5. Deploy only through a reviewed GitHub PR.
