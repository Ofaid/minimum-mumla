# T88 device profile (pending hardware capture)

T88 is a first-class target of the Minimum radio client, but the device has not yet been connected
to the development workstation. This file is intentionally a capture checklist, not a guessed
hardware specification.

When T88 arrives, record:

- Android release, API level, manufacturer, model and build fingerprint
- ABI, SoC, RAM and physical display size/density
- USB VID/PID, interface list and active USB functions
- `/proc/bus/input/devices` inventory
- key `ACTION_DOWN`/`ACTION_UP`, Android keyCode, Linux scanCode, repeat count and source device
- foreground/background/screen-off behavior for PTT and room keys
- audio input/output routes and Bluetooth behavior
- network interfaces and behavior during Wi-Fi/LTE transitions
- installed OEM radio/PTT services and exported broadcast actions

Do not copy T99 key mappings into this profile. The Android key path may differ even when the
physical buttons look similar.
