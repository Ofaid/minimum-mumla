# Development runbook

## Build and test

Run Gradle from `D:\mumla-dev`, not from the path containing a space, because the current NDK
toolchain has already shown path-sensitive behavior.

Clone with submodules. The parent uses GitLab Mumla history while the customized Humla commit is
published on the `humla-minimum` branch of `awatchar/minimum`:

```powershell
git clone --recurse-submodules https://github.com/awatchar/minimum.git
```

```powershell
Set-Location D:\mumla-dev
.\gradlew.bat :app:testFossDebugUnitTest :app:assembleFossDebug --no-daemon
```

APK output:

```text
D:\mumla-dev\app\build\outputs\apk\foss\debug\mumla-foss-debug.apk
```

Do not stage the pre-existing untracked `DEV_ENVIRONMENT_REQUIREMENTS.md` unless the user asks for
that separate file to be included.

## T99 ADB session

The current workstation ADB server uses port `5041` and the authorized T99 serial is `12344321`.

```powershell
$adb = "adb -P 5041 -s 12344321"
& adb -P 5041 devices
& adb -P 5041 -s 12344321 install -r D:\mumla-dev\app\build\outputs\apk\foss\debug\mumla-foss-debug.apk
& adb -P 5041 -s 12344321 shell am start -n se.lublin.mumla/.app.MumlaActivity
& adb -P 5041 -s 12344321 logcat -d -t 300
```

Avoid `pm clear` on the working T99 unless a fresh first-run test is explicitly needed; it removes
the device's current certificates, favourites and test state.

If `adb devices` hangs instead of returning an offline/unauthorized/device row, stop every `adb.exe`
process and retry ports 5041 and 5037. Confirm that Windows still reports the MI_03 `ADB Interface`
with problem code 0. A T99 power cycle does not repair a wedged Windows USB stack; if both the
installed platform-tools and a known-good isolated ADB exhibit the same hang, reboot Windows before
changing the handset, driver or app. Never factory-reset the radio for this host-side symptom.

For the reversible network-loss/recovery acceptance test, use the guarded script below. It restores
and reads back the exact prior Wi-Fi/mobile-data state in a `finally` block, refuses ambiguous
original settings, and never presses PTT:

```powershell
.\scripts\test-radio-reconnect.ps1 -WhatIf
.\scripts\test-radio-reconnect.ps1 -Force -OutageSeconds 30
```

The full fault matrix and the distinction between local audio handoff and server receipt are in
`docs/RECONNECT_TEST_PLAN.md`.

T99/T88 builds keep a bounded app-private hardware trace. It contains key metadata only and can be
read without exposing radio config or tokens:

```powershell
& adb -P 5041 -s 12344321 shell run-as se.lublin.mumla `
    cat files/radio-diagnostics/key-events.log
```

T99's verified PTT is F1/scan 59; F2 is physical EXIT and must never be configured as T99 PTT.
Clear the trace only when beginning a deliberate commissioning capture. Do not commit raw device
logs; copy only the sanitized mapping into the device profile.

For process-death acceptance, first confirm Ready and record the exact PID. Kill only that process
through the debuggable app UID; never use force-stop or clear app data:

```powershell
$minimumProcess = & adb -P 5041 -s 12344321 shell ps | Select-String 'se.lublin.mumla$'
$minimumPid = (($minimumProcess.ToString().Trim()) -split '\s+')[1]
& adb -P 5041 -s 12344321 shell run-as se.lublin.mumla kill -9 $minimumPid
```

T99's normal service restart may be deferred by roughly 16 minutes. The dedicated-radio watchdog
must instead produce a new PID within about 30 seconds, open RadioShell automatically and return to
`minimum-state-ready`. Do not issue `am start` after the kill because that invalidates the test.

The radio config directory has three durable states: `active-config.json` is the Last Known Good,
`pending-config.json` is waiting for an idle trial, and `previous-config.json` is the rollback copy.
Do not overwrite active merely to test an update. Stage pending, start `RadioShellActivity`, and
verify it connects and joins the selected room before expecting promotion. The pending-available
broadcast receiver is app-internal/non-exported, so an ADB `am broadcast` is intentionally not a
valid trigger; startup and the updater both invoke the safe path.

## Boot auto-start check

The receiver is enabled by default through `Settings.PREF_AUTO_START`. A valid simulated check is:

1. Launch the app once so Android has started the package normally.
2. Kill the app process without stopping the package.
3. Send `android.intent.action.BOOT_COMPLETED`.
4. On T99/T88, check `dumpsys activity activities` for
   `se.lublin.mumla/.radio.RadioShellActivity`; generic Android retains
   `se.lublin.mumla/.app.MumlaActivity`.

Android may refuse the Activity launch on newer OEM builds. That is a platform limitation, not proof
that the receiver is missing; add a foreground-service/notification fallback before claiming broad
new-device support.

## T99 preparation and Zello removal

The canonical provisioning script is `scripts/prepare-t99.ps1`. The old
`scripts/remove-zello-t99.ps1` name remains as a compatibility wrapper. Preparation reports the
ADB serial, Android serial, USB gadget serial and Minimum app Device ID; it does not attempt a
root-only USB serial rewrite.

```powershell
Set-Location D:\mumla-dev
.\scripts\prepare-t99.ps1 -ReportOnly
.\scripts\prepare-t99.ps1 -WhatIf
.\scripts\prepare-t99.ps1
.\scripts\prepare-t99.ps1 -SkipMinimumHome
.\scripts\prepare-t99.ps1 -Force -DeviceProfile GYZ3DE `
    -RadioConfigPath C:\private\minimum-radio.json
```

The script also checks the lab SSID `..@EmergencyTU`. On a workstation's first use, create the
git-ignored DPAPI credential interactively; never put the password in a command, script or tracked
file:

```powershell
New-Item -ItemType Directory -Force .\scripts\.secrets | Out-Null
$labWifi = Get-Credential -UserName '..@EmergencyTU' `
    -Message 'Enter the Minimum T99 lab Wi-Fi password'
$labWifi | Export-Clixml .\scripts\.secrets\t99-lab-wifi.credential.xml
```

If the target is not connected, preparation builds and installs the standalone
`tools/t99-wifi-provisioner` helper, copies the credential through app-private storage, saves and
enables the WPA2 profile, then removes both helper and transient files. Use `-RefreshLabWifi` to
replace/recheck an existing profile or `-SkipLabWifi` to omit the step. The PSK is never printed or
committed. T99 physically passed profile refresh and automatic reconnection after a Wi-Fi radio
off/on cycle.

Provisioning does not currently change Android Location settings. The framework/GPS HAL exists,
but a window-side live test produced no satellite fix, no ephemeris and zero SNR; require an
open-sky fix before enabling Location as a fleet default.

If local PowerShell policy blocks scripts, run the same file with
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File`. Keep the supplied JSON outside the
repository: it may contain a public room token or a private server-certificate pin. The script
checks size, schema/config version, required Mumble/room fields and Device ID, then copies it to
app-private `files/radio-config/active-config.json` with directory mode 700 and file mode 600. It
does not display token values and removes the temporary ADB copy.

`-DeviceProfile` is the operator-facing name for the Technical Brief's six-character Device ID.
It assigns the stable `/devices/{deviceId}.json` lookup key after validating the same six-character
rules. It does not change the USB/ADB serial, hardware model profile or `mumble.username`. Config
schema 2 requires the latter explicitly; the current T99 uses Config Profile `GYZ3DE` and Mumble
username `E25FGL-T99`.

This removes Zello for Android user 0. It does not erase the system APK from the read-only system
partition; a factory reset or OEM restore can make it reappear. If several identical T99 devices
show the same ADB serial, use `adb devices -l` and select the unique `transport_id`:

```powershell
.\scripts\prepare-t99.ps1 -TransportId 1
```

Without `-RadioConfigPath`, preparation opens `MinimumHomeActivity` after the Device ID check. It
provides two swipe pages: the large Minimum icon and Android Settings. With a config, it opens
`RadioShellActivity` so connection and room join can be verified immediately. T99 firmware does
not accept the data-installed Minimum
activity as a usable default HOME choice, so the app deliberately does not register as HOME. The
script requests a legacy Minimum shortcut in Launcher3, launches a real system HOME intent and
fails if ResolverActivity appears. At boot, T99/T88 profiles launch the radio client directly;
generic Android continues to launch MumlaActivity. Back from the radio client opens the recovery
dashboard instead of exiting to an uncertain launcher state.

`-SkipMinimumHome` skips shortcut/dashboard provisioning. Launcher3 is retained as an emergency
fallback and should show both Minimum and Settings after preparation.

## T88 capture procedure

When T88 arrives, connect it without changing the T99 ADB server assumptions. Record:

- `getprop`, `wm size`, `wm density`, RAM/ABI and Android API level
- `getevent -il` before and during every physical key press
- `/proc/bus/input/devices`, `/proc/tty/driver/*` and `getprop sys.usb.config`
- `pm list packages`, audio devices and network interfaces
- whether the PTT event remains visible with the display off

Copy sanitized output to `docs/T88_DEVICE_PROFILE.md`. Never commit serials, certificates, tokens
or personal Wi-Fi details.

## Git workflow

```powershell
Set-Location D:\VR Android App\mumla
git status --short --branch
git diff --check
git add <only-intended-files>
git commit -m "<focused change>"
git push github agent/minimum-foundation
```

Keep `origin` pointing to GitLab upstream and use the `github` remote for this project. PR #1 is
draft; do not merge it automatically.
