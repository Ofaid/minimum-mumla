# T99 device profile

ชื่อเล่นของเครื่องเป้าหมายในโครงการนี้คือ **T99** อุปกรณ์ในภาพเป็นเครื่อง Android แบบวิทยุ PTT OEM/white-label ที่ใช้แบรนด์ Motorola ในการตลาด ไม่ใช่ Motorola รุ่นมาตรฐาน จึงต้องถือว่า input และ USB behavior เป็น vendor-specific

## ข้อมูลที่ตรวจจากเครื่องจริง

| รายการ | ค่า |
|---|---|
| Android | 5.1.1 / API 22 |
| Manufacturer | Youdotech |
| Model | QM011 |
| SoC | Qualcomm MSM8909 |
| ABI | `armeabi-v7a` |
| RAM ที่ระบบรายงาน | ประมาณ 404 MiB |
| จอจริง | 132 × 132, density ประมาณ 114 |
| USB VID/PID | `05C6:9091` |
| USB functions | `diag,serial_smd,rmnet_qti_bam,adb` |
| MTP | ไม่ได้เปิดเป็น USB function ที่ตรวจพบ |
| ADB | ทำงานและ authorize แล้ว |

ข้อมูลระบุตัวเครื่อง เช่น serial, IMEI, MAC address, IP และ public key ไม่เก็บไว้ในเอกสารสาธารณะนี้

## USB interfaces

Windows เห็น USB interface หลายตัวเป็นอุปกรณ์ชื่อ Android:

- `MI_00`: diagnostics
- `MI_01`: modem/serial
- `MI_02`: WWAN/application
- `MI_03`: ADB — ตัวนี้ใช้งานได้

อุปกรณ์สีเหลืองอีกสามตัวใน Device Manager จึงไม่ใช่ปัญหา ADB โดยตรง แต่เป็น driver ของ Qualcomm diagnostic/modem interfaces ที่ Windows ยังไม่มีหรือไม่จำเป็นต่อการควบคุมแอปผ่าน ADB

## Input inventory

ข้อมูลจาก `/proc/bus/input/devices` และ event inspection:

- `matrix_keypad.71`: `KEY_UP`, `KEY_DOWN`, `KEY_MENU`, `KEY_BACK`, `KEY_SELECT`
- `gpio-keys`: `KEY_F1`, `KEY_F2`, `KEY_VOLUMEUP`
- `qpnp_pon`: `KEY_VOLUMEDOWN`, `KEY_POWER`
- `msm8909-skua-snd-card Button Jack`: `KEY_VOLUMEDOWN`, `KEY_VOLUMEUP`, `KEY_MEDIA`, `BTN_4`, raw `0x0246`
- Headset Jack switch events

## PTT implications

Physical capture on 2026-08-05 proves that the labelled PTT button is raw `gpio-keys` F1, not F2
and not the Button Jack media path. Android delivered `KEYCODE_F1` (131), scanCode 59,
deviceId 4, source `0x00000101`, device `gpio-keys`, with normal DOWN/repeat/UP events. Minimum starts
TX only on repeat 0 and releases on UP. MediaSession keys remain safe alternate PTT inputs.

Physical EXIT is F2, so T99 explicitly rejects F2 as PTT even if a stale preference requests it.
The T99 application defaults overwrite the managed push key with F1 at every process startup.

## Verified ten-button map

| Physical label | Linux input | Android event | Input device | Minimum behavior |
|---|---|---|---|---|
| Power | `KEY_POWER` | `KEYCODE_POWER` / scan 116 | `qpnp_pon` | Android screen power |
| PTT | `KEY_F1` | `KEYCODE_F1` 131 / scan 59 | `gpio-keys` | PTT hold; F1 only on T99 |
| Volume + | `KEY_VOLUMEUP` | `KEYCODE_VOLUME_UP` 24 / scan 115 | `gpio-keys` | Android volume |
| Volume - | `KEY_VOLUMEDOWN` | `KEYCODE_VOLUME_DOWN` 25 / scan 114 | `qpnp_pon` | Android volume |
| MENU | `KEY_SELECT` | `KEYCODE_DPAD_CENTER` 23 / scan 353 | `matrix_keypad.71` | Open recovery dashboard from RadioShell |
| EXIT | `KEY_F2` | `KEYCODE_F2` 132 / scan 60 | `gpio-keys` | Open recovery dashboard; never PTT |
| Up | `KEY_UP` | `KEYCODE_DPAD_UP` 19 / scan 103 | `matrix_keypad.71` | Previous room/page |
| Down | `KEY_DOWN` | `KEYCODE_DPAD_DOWN` 20 / scan 108 | `matrix_keypad.71` | Next room/page |
| Green | `KEY_MENU` | `KEYCODE_MENU` 82 / scan 139 | `matrix_keypad.71` | Confirm/join selected room |
| Red | `KEY_BACK` | `KEYCODE_BACK` 4 / scan 158 | `matrix_keypad.71` | Back to recovery dashboard |

Activity diagnostics physically confirmed PTT, volume, direction and MENU metadata. EXIT/green/red
behavior was additionally verified with the captured kernel event, installed Android keylayout and
non-PTT ADB key injection. The app-private bounded trace is
`files/radio-diagnostics/key-events.log`; it records no text, config, token or audio data.

## Software found on T99

พบ package `com.loudtalks` (Zello) version 5.9.1 / versionCode 2600751 ซึ่งมี component/filter ที่เกี่ยวข้องกับ PTT เช่น media button และ vendor-style PTT actions การมี filter ใน APK ไม่ใช่หลักฐานว่า firmware ของ T99 จะส่ง action เหล่านั้นให้แอปอื่น จึงใช้เป็นข้อมูลสำหรับ diagnostic และการทดลองเท่านั้น

## Development notes

### Launcher and boot behavior

- OEM HOME is `com.android.launcher3/.Launcher`; its factory workspace originally showed only
  Settings.
- Registering a data-installed app as HOME causes the API-22 resolver dialog, but the OEM resolver
  does not expose Minimum as a reliable selectable/default candidate on the 132x132 layout.
- Minimum therefore uses an explicit two-page radio dashboard, launched by `MumlaBootReceiver`, and
  installs a legacy Minimum shortcut into Launcher3 as the recovery path.
- Physical reboot verification passed: no `ResolverActivity`, dashboard focused after boot, and the
  Launcher3 fallback visibly contains both Minimum and Settings.
- The dashboard supports non-touch operation: DPAD up/left and down/right change pages; DPAD center,
  Enter, Button Select (`KEY_SELECT`) and Call activate the visible page. On T99, F1 is PTT and F2
  is the labelled EXIT key; F2 is never accepted as PTT.

- โปรเจคมี path build-safe `D:\mumla-dev` ซึ่งเป็น junction ไปยัง `D:\VR Android App\mumla` เดียวกัน
- Full FOSS debug build และติดตั้ง APK บน T99 สำเร็จแล้ว
- T99 `keyCode`, `scanCode`, action, repeat count and source-device capture is complete. Repeat the
  same capture for T88 rather than copying the T99 mapping.
