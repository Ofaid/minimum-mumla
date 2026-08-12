Minimum device provisioning bundle
==================================

This bundle prepares one supported T99, T56 or RYKS radio on Windows.

Supported hardware identities
-----------------------------

- T56: UNIPRO / ZX
- T99: Youdotech / QM011
- RYKS: ELINK / ym_258

Unknown or ambiguous hardware is rejected before APK installation or provisioning changes.

Requirements
------------

- Windows 10 or Windows 11
- Android Platform Tools (adb.exe) available in PATH
- Internet access to https://minimum.vra.or.th/
- A Minimum Portal administrator account
- USB debugging enabled and authorized on the radio

Use
---

1. Extract the complete ZIP. Do not run files from inside the ZIP preview.
2. Connect and unlock one radio, then authorize USB debugging.
3. Double-click "Provision Minimum Device.cmd".
4. Press Enter for the recommended setup, or choose the custom menu.
5. Register the displayed Device ID and model in the Minimum Portal.
6. Wait while the radio fetches its configuration by Device ID. No device token is required.
7. Keep the radio connected until the workflow reports PASS. If you pass `-SkipReboot`, the
   workflow intentionally ends with `INCOMPLETE` (exit code 2); it never reports PASS because
   same-ID Ready after reboot was not verified.

PASS means that Minimum activated a managed configuration and reached Ready before reboot, then
returned to Ready with the same Device ID after reboot. It does not replace model-specific field
acceptance for PTT, audio, room switching, Location, or other hardware behavior.

T56 Location consent and manual safety boundaries
-------------------------------------------------

For T56, selecting "GPS + network consent" opens the Android/Google Location consent screen on
the radio. An operator must accept the on-device consent within 120 seconds; the workflow keeps
Minimum stopped until the decision is complete. The subsequent GPS/network state is allowed up to
another 120 seconds to stabilize. If consent is declined or times out, the script restores
device-only GPS mode and fails rather than silently enabling network Location.

The operator must intentionally unlock the radio, confirm the physical target, approve USB
debugging and any Location dialog, and keep only the intended unit connected during reboot
acceptance. The script does not claim PTT/audio/room-switching success, does not bypass Android
consent, and never uninstalls Minimum or clears app data automatically. APK signing changes remain
a manual, explicitly approved migration boundary.

ADB ports
---------

Port 5037 is the Android standard. Port 5041 is the Minimum lab alternative. The launcher detects
the port with an authorized device and presents a menu only when it cannot make a safe choice.

Security and safety
-------------------

- Configuration lookup uses the six-character Minimum Device ID; no bearer token is copied.
- The lab Wi-Fi credential is protected by Windows DPAPI for the current Windows account.
- Unknown hardware is inventory-reported and rejected before APK installation or provisioning.
- PASS requires managed config activation and Ready both before and after reboot.
- Ready messages before reboot are checkpoints only; the sole final PASS is emitted after the
  returning unit is identified and reaches Ready with the same Device ID.

An existing debug-signed Minimum APK cannot be upgraded in place by the release-signed APK. The
installer stops on a signature mismatch rather than clearing app data automatically. Preserve any
required device identity/config information and perform an explicitly approved uninstall before
switching a lab device from debug signing to release signing.

Checksum verification
---------------------

Download each .sha256 file beside its APK or ZIP. In PowerShell, compare the published value with:

    Get-FileHash -Algorithm SHA256 .\minimum-<version>-foss.apk
    Get-FileHash -Algorithm SHA256 .\minimum-provisioning-<version>.zip

The hexadecimal hashes must match exactly before provisioning.

Interrupted or failed setup
---------------------------

- Read the first error shown in the setup window and correct that condition before retrying.
- Do not uninstall Minimum or clear its data merely to retry; doing so can remove its Device ID and
  Last Known Good configuration.
- Reconnect and authorize exactly one intended radio, then run the launcher again. The guarded
  steps are designed to re-check state before continuing.
- If Android reports an APK signature mismatch, stop. Preserve required identity/configuration and
  obtain explicit approval before uninstalling a debug-signed build.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed APK and replacement use different
  signing keys. The provisioning script does not clear data; preserve identity/configuration and
  obtain explicit approval before uninstalling the old build.
- If Portal registration or configuration is incomplete, register the displayed Device ID and
  rerun; no token entry is required.

The included APK is signed and versioned by the GitHub Release workflow. Verify the SHA-256 files
on the Release page before use. Do not treat a new release as stable until physical provisioning,
Ready-before/after-reboot, Device-ID preservation, and the required model-specific acceptance have
passed on T56, T99, and RYKS hardware.
