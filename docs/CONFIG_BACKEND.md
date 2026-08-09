# Configuration backend and client contract

## Production admin portal

The private configuration control plane is the Next.js application under `web/`. It is deployed
with Vercel's **Next.js framework preset** at `https://minimum.vra.or.th/`. GitHub Pages remains the
public, non-secret baseline; the portal owns administrator accounts, device records and private
schema-3 configuration.

Production persistence is Cloudflare KV accessed server-side through the REST API. The Vercel
project must provide `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN` and
`CLOUDFLARE_KV_NAMESPACE_ID`; `CLOUDFLARE_KV_API_BASE` is optional and is intended for a compatible
test endpoint. `SESSION_SECRET` and `DEVICE_TOKEN_HASH_SECRET` must each be at least 32 characters.
These values are deployment secrets and must never be committed or printed.

The first-run handoff is deliberately explicit: open the portal, create the administrator account,
then register each device and copy its one-time bearer token into device-private provisioning. For
credentials, the portal stores only the scrypt administrator hash and HMAC device-token hash (device
metadata and schema-3 config are also persisted); rotating a token revokes the previous token
immediately. Browser mutations are same-origin and BotID-protected in production.

The administrator edits config through a structured form, not raw JSON. Servers, per-server
credentials, channels, aliases, channel access, default/last channel behavior, PTT safety and
T56-only Location/APRS settings have dedicated controls. Raw Schema-3 JSON remains available only
under **Advanced** for exceptional fields. Model selection applies the model-owned service/hardware
overlay without discarding connection credentials, channel tokens or APRS Object name.

Portal-issued configurations begin at `configVersion` 7 because the embedded public baseline is v6;
this prevents a newly registered portal profile from looking like a downgrade to Android. Older
portal records are repaired against the canonical Schema-3 template and advanced monotonically when
an Android client fetches them or an administrator saves them. Effective changes advance the version
automatically; re-saving unchanged content does not. The operator reported completing administrator
setup and registering the development T56/T99 records on 2026-08-09. Tokens and private config values
remain outside this documentation.

## Public files

The checked-in public distribution under `backend/` remains a non-secret reference and recovery
artifact. Managed Android devices no longer depend on GitHub Pages during refresh; their complete
device-specific Schema-3 configuration is served by the production portal above:

```text
/manifest.json
/default.json
/models/{modelProfile}.json
/devices/{deviceId}.json
```

Expected base URL after Pages deploy: `https://awatchar.github.io/minimum/`.

The private Android endpoint is `GET https://minimum.vra.or.th/api/device-config/{deviceId}` and
requires `Authorization: Bearer <device token>`. The legacy equivalent
`/api/device/{deviceId}/config` remains available for compatibility. Missing, invalid, or rotated
tokens return the same generic `401 Unauthorized` response; device IDs are never authentication.

The Android implementation is `RadioConfigRepository`. It exposes `loadActiveOrDefault()` and a
worker-thread `refresh(deviceId, modelProfile)` method. A provisioned managed device downloads the
complete config directly from `/api/device-config/{deviceId}` instead of fetching and merging the
GitHub default/model files first. The user-facing device "Config Profile" is the same six-character
`deviceId` lookup key; it is independent
from both the hardware model profile and the Mumble login name. `RadioConfigUpdater` schedules a best-effort
refresh every six hours and whenever the network returns, without delaying startup. `RadioShellActivity`
loads the Last Known Good result immediately, restores the last selected channel ID, connects
through the existing foreground service with that channel's connection/password/tokens, and joins
the channel by its exact full path. If the saved ID no longer exists, it uses
`radio.defaultChannel`.

APRS tracking has its own canonical contract in [APRS_TRACKING.md](APRS_TRACKING.md). The public
backend may describe that tracking is disabled by default, but it must not become a credential store:
the APRS passcode, private endpoint overrides and last successful position remain app-private.
`tracking.aprs.objectName` may optionally provide a public operator-selected label; when absent the
client derives `VR-` plus the six-character Device ID locally.

T99 and T56 use Android 5.1/API 22 era trust stores. The Config transport combines the platform
trust manager with the bundled ISRG Root X1 and enables TLS 1.2 while preserving the default
`HttpsURLConnection` hostname verifier. Trust initialization fails closed; there is no trust-all or
hostname-verification bypass. The embedded/cache configuration remains the Last Known Good fallback.

## Validation and rollback rules

- The private endpoint is fixed to HTTPS and redirects are not followed.
- Each response is limited to 262,144 bytes.
- Schema version must be 3 and config version must be positive.
- `connections` is an object keyed by stable connection ID. Each complete connection has host,
  port and username; password and TLS policy are connection-scoped.
- Every `channels[]` entry has a stable ID, references an existing `connectionId`, contains an
  absolute Mumble path and owns its access token set. An optional `alias` (1-32 visible characters,
  no outer whitespace or control characters) is the short operator-facing name on RadioShell. It
  never changes the Mumble path or connection credentials; configs without it display `label`.
- Device IDs must be `*` or six uppercase alphanumeric characters with at least one letter and digit.
- Mumble username must be nonblank, at most 128 characters and contain no control characters.
- Mumble port must be 1..65535.
- Host names contain only DNS-safe letters, digits, dots and hyphens.
- Channel paths are absolute, normalized, no longer than 512 characters and limited to 16 presets.
- `autoTrustServerCertificate` is boolean and defaults to true. After normal Android TLS trust
  fails, the configured endpoint's presented leaf certificate is stored app-privately and the
  connection is retried without an operator dialog.
- Optional `serverCertificateSha256` is exactly 64 hexadecimal digits (separators are accepted by
  the Android client). It is a stricter policy than auto-trust: when present, a mismatch fails
  closed.
- PTT maximum is 1..120 seconds.
- `releaseOnNetworkLoss` must be true.
- `tracking` is optional and disabled by default. It is accepted by the Android client only on the
  immutable T56 hardware profile; T99 and generic devices ignore it. When enabled, `tracking.aprs`
  requires the source callsign, passcode, host and port. The default private endpoint is the
  APRS-IS advertised HTTPS send-only port (`ametx.com:8888`); it is not a public backend or proxy.
- APRS-IS passcodes are decimal strings whose numeric value is `0..32767`, matching the Android
  parser. APRS credentials are required only when `tracking.aprs.enabled` is explicitly true.
- The tracking schema remains deny-by-default. A device-private provisioned config may enable it on
  T56, but public backend JSON must not contain APRS passcodes, tokens, private endpoint overrides or
  cached positions. Disabling tracking clears the cached position.
- The API-22 APRS HTTPS client adds the bundled ISRG Root X1 trust anchor while preserving hostname
  verification; do not replace this with trust-all behavior or a public proxy.
- Optional `tracking.aprs.objectName` is 1-9 ASCII characters, has no outer whitespace, starts with
  an alphanumeric, and permits alphanumerics, internal spaces, `_` and `-`. Android uppercases and right-pads it to APRS' fixed
  nine-byte Object field. If absent, the Object name remains `VR-<DeviceID>`. Changing it is an
  effective config change and therefore requires a higher `configVersion`.
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
`connections.public-main.username` is `E25FGL-T99`. The operator's separate Mumble account `GY3ZDE` is not used as
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
4. Do not add server passwords or protected tokens to a public commit.
5. Deploy only through a reviewed GitHub PR.
