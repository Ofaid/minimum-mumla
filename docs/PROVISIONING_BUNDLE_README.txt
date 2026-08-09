Minimum device provisioning bundle
==================================

This bundle prepares one supported T99 or T56 radio on Windows.

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
6. Copy the one-time device token and paste it into the hidden prompt.
7. Wait for PASS before disconnecting the radio.

ADB ports
---------

Port 5037 is the Android standard. Port 5041 is the Minimum lab alternative. The launcher detects
the port with an authorized device and presents a menu only when it cannot make a safe choice.

Security and safety
-------------------

- The device token is never printed or placed in an ADB command argument.
- Temporary token files are removed from Windows and the radio.
- The lab Wi-Fi credential is protected by Windows DPAPI for the current Windows account.
- Unknown hardware is inventory-reported and rejected before APK installation or provisioning.
- PASS requires managed config activation and Ready both before and after reboot.

An existing debug-signed Minimum APK cannot be upgraded in place by the release-signed APK. The
installer stops on a signature mismatch rather than clearing app data automatically. Preserve any
required device identity/config information and perform an explicitly approved uninstall before
switching a lab device from debug signing to release signing.

The included APK is signed and versioned by the GitHub Release workflow. Verify the SHA-256 files
on the Release page before use. This prerelease still requires physical acceptance on the target
T99/T56 hardware before it is promoted as a stable field release.
