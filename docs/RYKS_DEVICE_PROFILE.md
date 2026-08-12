# RYKS device profile

สถานะเอกสาร: commissioning profile สำหรับเครื่อง RYKS ที่ตรวจเมื่อ 2026-08-12

## Identity and platform

- Android build identity: manufacturer `ELINK`, model `ym_258`, device `ELINK`
- Android 8.1.0 / API 27
- Display: 160 × 128 px at 120 dpi
- Minimum hardware/model profile: `ryks` / `ryks-elink-ym-258`
- Location tracking remains disabled until a separate real-device acceptance is completed.

Do not commit the unit's ADB serial, Minimum Device ID, private configuration, room token or server
certificate data. `scripts/prepare-ryks.ps1` selects the stable manufacturer/model pair and delegates
to the guarded shared provisioner.

## Input inventory

The GPIO/rotary rows below come from the kernel/device-tree and OEM framework. The front-label rows
are Minimum's conservative CALL/MENU/BACK policy and remain subject to the physical trace below:

| Control path | Linux scan/code | Android path | Minimum behavior |
|---|---:|---|---|
| `gpio_keys/ptt_btn1` | 216 / `KEY_CHAT` | vendor keyCode 285 plus `com.zello.ptt.down/up` | hold-to-talk PTT |
| `gpio_keys/ptt_btn2` | 249 / `KEY_CHAT` | vendor keyCode 285 plus the same OEM broadcasts | secondary hold-to-talk PTT |
| remaining lower side key | 65 / `KEY_F7` | `KEYCODE_F7` 137 | hold 1 s to select/join the next configured room |
| rotary clockwise | 115 / `KEY_VOLUMEUP` | Android volume up | raise media/playback volume |
| rotary counter-clockwise | 114 / `KEY_VOLUMEDOWN` | Android volume down | lower media/playback volume |
| green | Android `KEYCODE_CALL` 5 | foreground Activity | confirm/rejoin selected room |
| three-line menu | Android `KEYCODE_MENU` 82 | foreground Activity | hold 1 s to toggle Device ID |
| red/back | Android `KEYCODE_BACK` 4 | foreground Activity | hold 5 s to open recovery dashboard |

The two `CHAT` GPIOs must remain PTT. The OEM PhoneWindowManager broadcasts the same action for
both and does not include a scan-code extra, so assigning scan 249 to channel selection would allow
an unintended transmission. Minimum therefore uses scan 65 as the only side-key room action.

The rotary is left on Android's native media-volume path. Minimum does not consume those events.

## Screen-off PTT path

The firmware reports `ro.build.ptt_type=ANYPTT`. Its PhoneWindowManager maps vendor keyCode 285 to:

- `com.zello.ptt.down`
- `com.zello.ptt.up`

`RadioHardwareKeyReceiver` accepts these actions only when the detected hardware profile is RYKS
and forwards them into `MumlaService.ACTION_RADIO_PTT_DOWN/UP`. This preserves the service-owned
room-ready gate, release handling, disconnect safety and 120-second watchdog. Duplicate raw-key and
broadcast DOWN events are idempotent in the service.

An injected keyCode-285 test on the physical unit proved that PhoneWindowManager emits both OEM
actions and Minimum remains foreground without a crash. A labelled-button operator trace with an
explicitly verified display-off state is still required before physical screen-off PTT is marked
accepted.

## Installation and provisioning

The factory PackageManager rejects third-party installs while `ro.build.install` is absent or `0`.
On this debuggable image the shell can set the absent read-only property once per boot:

```powershell
adb shell setprop ro.build.install 1
adb install -r minimum-foss.apk
```

The one-shot provisioner performs and verifies this step only after detecting `ELINK/ym_258`.
`prepare-ryks.ps1` then grants microphone permission, removes Zello for user 0 while retaining the
read-only system APK, requests the Minimum recovery shortcut and verifies that Android HOME does
not show a chooser. The property resets with a reboot and is not written to the system partition.

## Web configuration

The portal model value is `ryks`. Its model-owned hardware data includes vendor PTT keyCode 285,
PTT scans 216/249, F7 scan 65, front-key Android codes and `locationTrackingSupported=false`.
Selecting RYKS in the portal preserves connections/channels while applying these model values and
disabling tracking/APRS.

## Remaining physical acceptance

- Capture each labelled button in order to confirm the front-label-to-Android mapping on this exact
  enclosure revision.
- Verify real PTT DOWN/UP while RadioShell is Ready, first with display on and then after confirming
  `Display Power: state=OFF`.
- Confirm both PTT GPIOs release TX normally and do not leave a stuck state.
- Verify F7 room selection with at least two live configured rooms.
- Verify green confirm, menu identity overlay, five-second red recovery and rotary volume by direct
  operator use.
- Reboot after portal provisioning and verify unattended return to the selected Ready room.
