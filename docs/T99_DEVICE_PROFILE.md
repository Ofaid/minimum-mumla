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

T99 มีทางเดิน input มากกว่าหนึ่งแบบ:

1. media-style input (`KEY_MEDIA`) อาจเข้าผ่าน Android `MediaSession`
2. F1/F2 และ raw GPIO อาจเข้าผ่าน Activity, vendor broadcast หรือ privileged/OEM path
3. Foreground Service ไม่ได้รับ arbitrary `KeyEvent` ทุกชนิดโดยอัตโนมัติ

Mumla มี MediaSession bridge สำหรับ media-style PTT แล้ว และมี TX watchdog 120 วินาทีใน `MumlaService` แล้ว แต่ยัง **ไม่ประกาศว่ารองรับ screen-off PTT ของ F1/F2** จนกว่าจะจับ event บนเครื่องจริงได้ครบทั้ง foreground, background และ screen-off

## Software found on T99

พบ package `com.loudtalks` (Zello) version 5.9.1 / versionCode 2600751 ซึ่งมี component/filter ที่เกี่ยวข้องกับ PTT เช่น media button และ vendor-style PTT actions การมี filter ใน APK ไม่ใช่หลักฐานว่า firmware ของ T99 จะส่ง action เหล่านั้นให้แอปอื่น จึงใช้เป็นข้อมูลสำหรับ diagnostic และการทดลองเท่านั้น

## Development notes

- โปรเจคมี path build-safe `D:\mumla-dev` ซึ่งเป็น junction ไปยัง `D:\VR Android App\mumla` เดียวกัน
- Full FOSS debug build และติดตั้ง APK บน T99 สำเร็จแล้ว
- การทดสอบต่อไปต้องบันทึก `keyCode`, `scanCode`, action, repeat count, source device และ vendor broadcast action (ถ้ามี)
- หากต้องใช้ F1/F2 ตอนจอดับโดยไม่ผ่าน public Android API อาจต้องใช้ vendor permission/service หรือ firmware integration; อย่าแก้ด้วยการดัก event แบบกว้างที่ทำให้ปุ่มระบบเสีย
