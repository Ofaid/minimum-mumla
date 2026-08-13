# Minimum existing-device updater

`Update Minimum Device.cmd` is for a supported radio that already has Minimum identity and an
active managed configuration. Use `Provision Minimum Device.cmd` for a factory-reset, new or
unregistered radio. The updater never registers a device in the Portal, reprovisions Wi-Fi,
removes OEM apps, reopens Location consent or reapplies unrelated device settings.

## Requirements and trust boundary

- Windows 10 or 11 and Android Platform Tools (`adb.exe`) in `PATH`.
- One complete, extracted `minimum-provisioning-<tag>.zip` from a reviewed GitHub Release.
- The separately published ZIP `.sha256` must match before extraction. The updater then verifies
  the exact file allowlist and hashes in `RELEASE-MANIFEST.json`, the APK checksum file, binary APK
  package/version, and APK signer. This makes bundle verification repeatable on an operator's
  existing workstation; a freshly installed or otherwise "clean" workstation is not required.
- USB debugging must be enabled and authorized. The normal path accepts exactly one device in the
  Android `device` state. Offline, unauthorized, recovery, unknown and ambiguous targets stop
  before mutation.

No source checkout, Gradle, Android Studio, signing key, Portal login or app-data export is used.
Do not use a bundle whose ZIP checksum does not match the checksum on its exact Release.

## Normal update

1. Verify the downloaded ZIP SHA-256, then extract the entire ZIP.
2. Connect one already-provisioned supported radio and authorize USB debugging.
3. Ensure it is not transmitting, then double-click `Update Minimum Device.cmd`.
4. Type `UPDATE` only after checking the physical radio is not transmitting.
5. Keep it connected until `PASS` or an actionable `FAIL` appears.

The updater inventories battery/power, supported model, installed version, Device ID, managed
configuration and Ready state. It compares the installed APK certificate with the bundled APK
certificate before any installation. It rejects an unintended downgrade. It uses only an in-place
`adb install -r` (or explicitly authorized `-r -d`) and contains no uninstall or clear-data path.

`PASS` means the exact target package/version is installed and the original Device ID, non-pending
managed configuration, last-known-good evidence and Ready state were verified. If the manifest
requires reboot, or `-FullRebootAcceptance` is requested, PASS also requires the same supported
profile and Device ID to return to Ready after reboot. `ALREADY_OK` is a successful idempotent
recheck of an already-installed exact version.

## Safe advanced modes

From PowerShell, optional modes include:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\update-minimum-device.ps1 -ReportOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\update-minimum-device.ps1 -WhatIf
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\update-minimum-device.ps1 -UpdateSession
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\update-minimum-device.ps1 -FullRebootAcceptance
```

`-UpdateSession` handles one physical radio at a time, requires disconnection before continuing,
retains earlier results after a later failure, and prints totals. Use `-Serial` or `-TransportId`
only for an intentional advanced selection. Reports are written under the current user's local app
data by default; they include Device ID for fleet correlation but exclude Android/USB serials,
subscriber identifiers, credentials, certificate fingerprints, private coordinates, app data and
logs.

Downgrade is intentionally refused unless `-AllowDowngrade` is supplied. That switch produces a
prominent warning and does not bypass signer verification, change identity, uninstall Minimum or
clear data. Use it only with an explicitly reviewed recovery plan. The report states that rollback
is not automated; the old APK is not included in the bundle.

## Failure and recovery

- `SIGNER_MISMATCH`: stop. A debug-signed APK cannot be replaced in place by a differently signed
  Release APK. The updater has made no uninstall/data-clear attempt. Preserve the device and seek an
  explicitly reviewed migration decision.
- `DOWNGRADE_REFUSED`: obtain the correct newer reviewed bundle; do not bypass the gate casually.
- `BUNDLE_*`, `MANIFEST_*`, `APK_*`: discard the extraction, re-download the exact Release, verify
  its published ZIP checksum and retry.
- `INSUFFICIENT_STORAGE`: free non-Minimum storage and rerun. Do not clear Minimum data.
- `IDENTITY_UNREADABLE` or `CONFIG_UNVERIFIED`: do not update. Relaunch the existing app, restore
  connectivity if safe, and use the sanitized report for diagnosis.
- `READY_TIMEOUT`, `BOOT_TIMEOUT`, `REBOOT_TARGET_AMBIGUOUS`: keep the intended unit isolated,
  restore USB authorization/connectivity and rerun. A successful install alone is never PASS.
- Interrupted USB: reconnect the same radio and rerun. Verification and migrations are designed to
  report `ALREADY_OK` where the intended state is already present.

Attach the generated `.txt` and matching `.json` report when requesting help. Never attach a full
bugreport, app-data backup, raw `dumpsys`, or unsanitized ADB log.

## Migration and physical-acceptance policy

Migration entries are keyed by installed/target version and supported model. An unknown manifest
migration is refused; it is not silently skipped. The current extension point intentionally has no
cellular migration. Issue #11 behavior may be added only after its policy and device acceptance are
reviewed, with an idempotent model-gated handler and tests.

The known E7ROW7 T56 has a debug-signed build. Do not try to install a Release-signed APK on it.
Physical updater acceptance without reset must instead use two reviewed APK versions signed by the
same debug key: record same ID/config/Ready on version A, update in place to version B, verify the
same ID/config/Ready, optionally reboot for same-ID Ready, then rerun version B to prove
`ALREADY_OK`. This is debug-channel updater evidence only; it does not prove Release-signer
acceptance or authorize a data-destructive signing-key transition.
