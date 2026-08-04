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
  └─ PTT watchdog and safety release

RadioConfigRepository
  ├─ embedded safe default
  ├─ GitHub Pages default/model/device overlays
  ├─ schema and safety validation
  └─ active/previous private cache

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
  ├── T99/T88 -> MinimumHomeActivity
  └── generic Android -> MumlaActivity

RadioShellActivity (future radio entry point)
  ├─ Device ID/profile display
  └─ large touch PTT bridge to MumlaService
```

## Configuration precedence

The intended merge order is:

```text
embedded default -> remote default -> model profile -> device override
```

Object fields merge recursively. Arrays replace the previous array as a whole. A bad remote
configuration must not replace the last valid active configuration; startup falls back to the
embedded default, and the previous active file is retained when a new config becomes active.

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
