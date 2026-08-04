# Minimum architecture

## Current component map

```text
MumlaApplication
  ├─ creates persistent Device ID
  └─ applies normal Mumla theme/locale

MumlaActivity (main radio client)
  ├─ normal Mumla navigation and server database
  ├─ automatic first-run certificate generation
  └─ foreground service binding

MumlaService (long-lived voice path)
  ├─ Humla/Mumble connection, TLS, Opus and audio
  ├─ MediaSession PTT bridge
  ├─ configured Activity key bridge
  ├─ service-owned remote RX session tracker
  ├─ indefinite managed-radio reconnect/backoff and process recovery
  ├─ bounded display wake plus local PTT-delivery warning
  └─ PTT watchdog and safety release

RadioConfigRepository
  ├─ embedded safe default
  ├─ GitHub Pages default/model/device overlays
  ├─ schema and safety validation
  └─ downgrade-protected pending/active/previous private cache

AccessTokenResolver
  ├─ reads public room tokens from a complete config
  ├─ trims and de-duplicates in first-seen order
  └─ excludes protected references until secure storage exists

MinimumHomeActivity (radio dashboard, not Android HOME)
  ├── one large Minimum icon per page
  ├── swipe: Minimum -> Settings
  └── tap: open the selected recovery/action target

RadioLauncherShortcutInstaller
  ├── requests a legacy Minimum shortcut in Launcher3
  └── runs at app startup and through the provisioning receiver

MumlaBootReceiver
  ├── T99/T88 -> RadioShellActivity
  └── generic Android -> MumlaActivity

RadioShellActivity (managed radio entry point)
  ├─ Device ID/profile display
  └─ large touch PTT bridge to MumlaService
```

The component map above preserves the original Mumla entry points for diagnostics. The managed
radio path is now `MumlaBootReceiver -> RadioShellActivity -> MumlaService`; T99/T88 no longer boot
to the recovery dashboard. `RadioConnectionConfig` provides the typed validated subset consumed by
the shell, and `RoomPathResolver` performs exact full-path channel lookup. `MinimumHomeActivity`
remains a deliberate recovery route to Minimum and Android Settings.

## Configuration precedence

The intended merge order is:

```text
embedded default -> remote default -> model profile -> device override
```

Object fields merge recursively. Arrays replace the previous array as a whole. A lower
`configVersion` cannot replace a valid newer active config, and changed content at the same version
is rejected.

```text
remote merge + validation
  -> pending-config.json
  -> wait until connection transition, RX and TX are idle
  -> trial candidate in memory
  -> connect and join the configured room
     -> success: active becomes previous; pending becomes active
     -> failure: discard pending; reconnect with active Last Known Good
```

Startup reads `active-config.json`. If it is corrupt and `previous-config.json` is valid, the
repository restores previous; otherwise it uses the embedded safe default. Network return forces a
new fetch, while an in-flight guard prevents overlapping refreshes.

## Radio connection and trust flow

```text
validated Last Known Good config
  -> ensure the existing Mumla client certificate
  -> resolve public access tokens without logging or database persistence
  -> connect through ServerConnectTask and MumlaService
  -> if normal TLS rejects the certificate, follow the validated config trust policy
  -> auto-trust and persist the presented leaf, or require the optional exact SHA-256 pin
  -> after ServerSync, resolve and join the exact full room path
```

Publicly trusted servers continue to use normal Android trust. For a configured self-signed server,
`autoTrustServerCertificate` defaults to true and stores the presented leaf in the existing
app-private BKS trust store before retrying. An optional SHA-256 pin overrides this permissive mode
and must match exactly. Trust is scoped to the app store and configured endpoint workflow; no
system CA store or global hostname verifier is changed.

## PTT lifecycle

```text
physical/media/touch down
  -> MumlaService.onTalkKeyDown()
  -> connected + PTT mode check
  -> TX starts + 120 s watchdog

physical/media/touch up
  -> MumlaService.onTalkKeyUp()
  -> TX stops and watchdog is cancelled

disconnect / service destroy / network loss
  -> releasePttForSafety()
  -> TX stops; a held key must be released before a new TX
```

The service owns the safety behavior. UI surfaces must call the service and must not implement a
second independent PTT timer.

Managed radios also enable Speex input preprocessing and half-duplex automatically. Half-duplex
mutes playback only while TX is active and explicitly unmutes during audio teardown, including a
disconnect during TX. The UI timer is display-only; the 120-second safety watchdog remains owned by
the service.

## Managed-radio reconnect lifecycle

```text
unexpected disconnect
  -> release TX and unmute playback
  -> wake full-screen reconnect status
  -> retry after 2/4/8/16/32/60 seconds (cap at 60 seconds)
  -> no network: wait for connectivity event plus 60-second fallback poll
  -> synchronized: reset backoff and join configured room
```

The Android service returns `START_REDELIVER_INTENT` only for the managed-radio connection mode so
process recovery receives the same validated connection intent. Certificate policy/pin mismatch is
the deliberate exception: it cancels retry and remains visibly fail-closed.

T99 firmware can nevertheless apply an OEM service-restart backoff of roughly 16 minutes after a
process kill. Dedicated T99/T88 profiles therefore maintain a process-independent AlarmManager
lease. A healthy service refreshes a 30-second lease every 10 seconds, so the alarm never fires in
normal operation. If the process dies, the retained alarm starts
`RadioProcessWatchdogReceiver`; it re-arms itself and opens `RadioShellActivity`. The receiver keeps
retrying if a background launch is blocked, while generic Android profiles do not arm this lease.

## Hardware strategy

`RadioDeviceProfile` identifies T99, T88 or generic hardware. The profile only selects a config
namespace. Actual keycode/scancode mappings remain data-driven because cheap radio firmware often
exposes the same physical button through different Linux input devices.

Known T99 input sources and pending mappings are in `docs/T99_DEVICE_PROFILE.md`. T88 starts from
`docs/T88_DEVICE_PROFILE.md` and must be filled from the real device.

`RadioPttKeyManager` applies radio defaults at process startup. It maps alternative keys to the same
service-owned PTT path. F1/F2 are foreground `gpio-keys` candidates; media/headset keys are the
screen-off-capable public Android path. The app must not claim screen-off F1/F2 support until a real
device trace proves that the OEM routes those events to the service.

## Standard build versus radio build

The current branch keeps one standard Mumla application and adds radio code under the normal source
set. T99's OEM resolver excludes the data-installed Minimum activity from its usable HOME choices,
so registering Minimum as HOME causes an unacceptable chooser. The radio dashboard is therefore an
explicit boot/provisioning activity, not an Android HOME handler. Launcher3 remains the system HOME
and receives a Minimum recovery shortcut. Provisioning verifies that system HOME opens without
ResolverActivity before returning to the dashboard. Do not fork the protocol/audio core or
duplicate PTT safety logic.
