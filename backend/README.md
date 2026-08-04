# Minimum static configuration backend

This directory is the public, read-only configuration distribution for the Minimum radio client.
It is designed to be published as GitHub Pages and contains no GitHub token, password, protected
Mumble token, or device secret.

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

## GitHub Pages

The workflow in `.github/workflows/deploy-pages.yml` publishes this directory. The repository Pages
source is already configured as `GitHub Actions`; after the workflow reaches `main`, the expected
base URL is `https://awatchar.github.io/minimum/`.
