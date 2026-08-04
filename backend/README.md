# Minimum static configuration backend

This directory is the public, read-only configuration distribution for the Minimum radio client.
It is designed to be published as GitHub Pages and contains no GitHub token, password, protected
Mumble token, or device secret.

Config schema 2 requires `mumble.username`. This is the exact username sent to the Mumble server
and is deliberately separate from `deviceId`, which selects `/devices/{deviceId}.json`, and from
`hardware.profile`, which selects model behavior.

The Android client should use these endpoints after Pages is enabled:

```text
/manifest.json
/default.json
/models/{modelProfile}.json
/devices/{deviceId}.json
```

The checked-in defaults intentionally keep Mumble auto-connect disabled until a real public Mumble
host is configured. Replace the placeholder host in `default.json` only through a reviewed commit.
Public access tokens may be placed in config, but they are not secrets. Protected rooms must use a
future secure-store reference and must never put the token value in this directory.

`mumble.autoTrustServerCertificate` defaults to `true`. If normal Android TLS validation rejects a
configured Mumble endpoint, Minimum automatically stores the presented leaf certificate in its
app-private trust store and reconnects. The validated external config is therefore the trust root
for selecting the server host. `mumble.serverCertificateSha256` remains an optional stricter pin;
when present, a mismatch fails closed instead of using automatic trust.

Every effective configuration change must advance `configVersion`. Minimum stages validated
downloads as pending, waits for RX/TX and connection transitions to become idle, and promotes the
candidate only after it connects and joins its configured room. A failed candidate is discarded and
the device continues with its Last Known Good active config.

## GitHub Pages

The workflow in `.github/workflows/deploy-pages.yml` publishes this directory. The repository Pages
source is already configured as `GitHub Actions`; after the workflow reaches `main`, the expected
base URL is `https://awatchar.github.io/minimum/`.
