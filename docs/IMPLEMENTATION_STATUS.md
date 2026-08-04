# Minimum / T99 implementation status

เอกสารนี้เป็นสถานะจริงของงาน Public PoC Radio Client เพื่อไม่ให้ทำงานที่เสร็จแล้วซ้ำ

## Baseline ที่มีอยู่แล้ว

- Upstream: `https://gitlab.com/quite/mumla.git`
- Baseline commit ก่อนการแก้: `477b337`
- JDK 21, AGP 8.13.2, compile/target SDK 36, min SDK 21
- NDK 25.1.8937393
- `D:\mumla-dev` เป็น junction ไปยัง working tree เดียวกับ `D:\VR Android App\mumla` เพื่อให้ NDK build ได้
- Full FOSS debug build ผ่าน และติดตั้งบน T99 ผ่าน ADB แล้ว
- ADB authorization ของ T99 ใช้งานได้
- ข้อมูลฮาร์ดแวร์อยู่ใน [T99_DEVICE_PROFILE.md](T99_DEVICE_PROFILE.md)
- แบบฟอร์มเก็บข้อมูล T88 อยู่ใน [T88_DEVICE_PROFILE.md](T88_DEVICE_PROFILE.md)

## งานที่ทำแล้วและไม่ต้องทำซ้ำ

- ตรวจ USB/ADB, input devices, display, audio, network และ package ที่สำคัญของ T99
- สร้าง profile T99 และตัดข้อมูลระบุตัวเครื่องออกจากเอกสารสาธารณะ
- เพิ่ม MediaSession bridge สำหรับ media-style PTT ที่ทำงานผ่าน Android public API
- Activity ไม่ประมวลผล media key ซ้ำกับ MediaSession
- เพิ่ม fail-safe TX watchdog 120 วินาที: หยุดส่งเมื่อ timeout, connection หลุด หรือ service ถูกทำลาย และต้องปล่อยปุ่มก่อนเริ่มใหม่
- ปรับ first-run certificate ให้สร้างอัตโนมัติแบบไม่เปิด dialog และ retry เมื่อสร้างไม่สำเร็จ
- เพิ่ม `RadioDeviceProfile` ที่รู้จัก T99 และเตรียม T88 โดยไม่ hard-code key mapping ที่ยังไม่ได้ตรวจจากเครื่องจริง

## ยังไม่อ้างว่าเสร็จ

- F1/F2/raw GPIO screen-off PTT: ต้องจับ event จริงและอาจต้องใช้ OEM integration
- RadioActivity/single-screen UX
- auto-connect จาก local/remote config
- Android-side GitHub Pages config consumer, cache, schema validation และ rollback
- room preset/full-path resolver และ access-token resolver
- hidden diagnostics และ boot/network lifecycle hardening

T88 ยังไม่มีข้อมูล runtime จากเครื่องจริง จึงยังไม่บันทึก keycode/scancode หรือ USB profile ของ T88 จนกว่าจะเสียบเครื่องและเก็บข้อมูลจริง

## GitHub configuration backend

มี static backend ใน `backend/` พร้อม manifest, default config, model profiles สำหรับ T99/T88 และ
JSON Schema รวมถึง GitHub Pages workflow `deploy-pages.yml` โดย default config ปิด auto-connect
และใช้ host placeholder จนกว่าจะใส่ Mumble server จริงอย่างตั้งใจ ตอนนี้ GitHub Pages ของ
`awatchar/minimum` ตั้ง Source เป็น `GitHub Actions` แล้ว

## แนวทางลดขนาดของ Radio build

ลด UI surface ก่อนลด protocol core: Radio build จะไม่แสดง server browser, channel/user tree, chat, token editor, certificate dialog หรือ settings ทั่วไป แต่ยังคง Humla/Mumble core, TLS, Opus, audio, foreground service และ connection observer ไว้ เพราะเป็นส่วนที่ทำให้ voice client ใช้งานได้จริง

ฟีเจอร์ที่ brief ระบุว่าไม่อยู่ใน MVP และจะไม่เพิ่มเข้ามา ได้แก่ GitHub login/write, web admin, chat/DM, recording, whisper, dispatcher, firmware flashing, root, OTA APK และ protocol rewrite

## ลำดับถัดไป

1. สร้าง radio interface/flavor แยก application ID โดยไม่กระทบ standard Mumla build
2. ต่อ `DeviceIdentityManager` เข้ากับ startup และแสดง Device ID ใน radio screen
3. เพิ่ม local config model ก่อน แล้วค่อย remote fetch/cache/rollback
4. เพิ่ม room preset และ token resolver บนเส้นทาง Authenticate เดิม
5. ทำ diagnostics และทดสอบ T99 foreground/background/screen-off ก่อนประกาศความสามารถ
