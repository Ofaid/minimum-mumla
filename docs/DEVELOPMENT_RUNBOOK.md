# Development runbook

## Build and test

Run Gradle from `D:\mumla-dev`, not from the path containing a space, because the current NDK
toolchain has already shown path-sensitive behavior.

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

## Boot auto-start check

The receiver is enabled by default through `Settings.PREF_AUTO_START`. A valid simulated check is:

1. Launch the app once so Android has started the package normally.
2. Kill the app process without stopping the package.
3. Send `android.intent.action.BOOT_COMPLETED`.
4. Check `dumpsys activity activities` for `se.lublin.mumla/.app.MumlaActivity`.

Android may refuse the Activity launch on newer OEM builds. That is a platform limitation, not proof
that the receiver is missing; add a foreground-service/notification fallback before claiming broad
new-device support.

## Zello removal on T99

The exact script is `scripts/remove-zello-t99.ps1`. It checks the serial, package name and system
APK path before doing a user-0 uninstall.

```powershell
Set-Location D:\mumla-dev
.\scripts\remove-zello-t99.ps1 -WhatIf
.\scripts\remove-zello-t99.ps1
```

This removes Zello for Android user 0. It does not erase the system APK from the read-only system
partition; a factory reset or OEM restore can make it reappear.

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
