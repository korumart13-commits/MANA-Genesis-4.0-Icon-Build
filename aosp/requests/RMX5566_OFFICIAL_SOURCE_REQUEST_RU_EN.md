# MANA Genesis 4.0 — официальный аудит AOSP и запрос исходников RMX5566

Дата проверки: 2026-11-25

## Цель

Подготовить MANA как Android/AOSP-платформу с локальным интеллектом и единым приложением MANA, сохранив совместимость с реальным устройством RMX5566. Этот документ отделяет подтверждённые официальные сведения от данных, которые обязан подтвердить realme.

## Подтверждённые сведения AOSP

- AOSP — открытая кодовая база Android, которую можно модифицировать и портировать; наличие AOSP само по себе не означает сертификацию Android и не включает проприетарные сервисы Google.
- Для полной сборки текущей ветки AOSP Google указывает 64-bit x86 Linux, не менее 400 GB свободного места и 64 GB RAM. Следовательно, собирать полную прошивку на целевом телефоне с Android не является реалистичным планом.
- AOSP device bring-up требует продуктовых и device makefiles/Soong-модулей, а также согласования системного и vendor уровней.
- VINTF использует device/framework manifests и compatibility matrices. Их нельзя достоверно сгенерировать по одному названию SoC.
- Разделы system, boot, init_boot и vendor/vendor_boot/odm имеют разные назначения; системный образ нельзя безопасно подменять случайными vendor-файлами.

Официальные документы:
- https://source.android.com/docs/setup/start/requirements
- https://source.android.com/docs/setup/start
- https://source.android.com/docs/setup/create/new-device
- https://source.android.com/docs/core/architecture/vintf
- https://source.android.com/docs/core/architecture/partitions
- https://source.android.com/docs/core/architecture/partitions/generic-boot

## Данные целевого устройства

Переданы владельцем, но требуют сверки с OEM:
- Product/model: RMX5566 / RMX5566RU
- Device: RE645F
- SoC: Qualcomm SM6225, arm64
- Android: 16
- Kernel release: 5.15.197
- A/B, dynamic partitions
- Bootloader locked, AVB green

Это не доказывает, что доступен публичный kernel source именно для RMX5566/RE645F и точно соответствующей версии прошивки.

## Что запросить у realme

Попросить подтвердить и предоставить, если это возможно по лицензиям и политике компании:

1. Точное коммерческое имя, региональный SKU и codename для RMX5566 / RMX5566RU / RE645F.
2. Публичный URL исходников Linux kernel для этого SKU и Android 16 build, включая точный commit/tag и лицензионные файлы.
3. Использованный defconfig, kernel build instructions, toolchain/clang version, kernel config и сведения о KMI/GKI.
4. DTS/DTBO source, device tree overlays и список vendor kernel modules (с указанием, какие исходники публикуются).
5. Device tree / BoardConfig / product makefiles, если они доступны.
6. VINTF device manifest, framework/device compatibility matrices, HAL versions и target FCM level для заводской прошивки.
7. Публичные vendor interface/build metadata и перечень proprietary blobs/firmware, которые нельзя распространять.
8. Точные partition map/sizes и boot image format сведения для разработчиков; AVB signing/rollback policy — только в документированном безопасном объёме.
9. Есть ли официальная процедура разблокировки/тестирования для разработчиков и поддерживаемый путь восстановления заводской прошивки.
10. Подтвердить, разрешает ли realme community ROM/AOSP development для этой модели и какие ограничения действуют.

Не запрашивать и не публиковать персональные данные владельца, IMEI, serial number, Android ID, аккаунты или приватные диагностические идентификаторы.

## Готовое обращение в realme (English)

Subject: Developer documentation/source request — realme RMX5566 / RE645F (Android 16, Qualcomm SM6225)

Hello realme Support / Software Engineering Team,

I am the owner of a realme RMX5566 (regional model RMX5566RU, device identifier RE645F), running Android 16 with Qualcomm SM6225 and Linux kernel 5.15.197. I am developing an independent, non-commercial AOSP-based project called MANA Genesis 4.0.

I am not requesting proprietary signing keys, security bypasses, user data, or assistance with circumventing Verified Boot. I want to determine whether a safe and license-compliant device port is technically possible.

Could you please:
- confirm the exact device codename/SKU and corresponding software build;
- provide the official kernel source repository URL and exact branch/tag/commit for this model and build;
- provide the matching defconfig, build instructions, DTS/DTBO sources and published vendor kernel modules, where legally available;
- clarify availability of device tree/product configuration files and VINTF manifests/compatibility information;
- identify the correct public source-release portal or responsible engineering contact if this request is outside first-line support;
- explain the officially supported recovery and bootloader policy for this model.

I will keep the stock bootloader lock and Verified Boot intact and will not flash unverified images. Any source code will be used in accordance with its license.

Thank you for your time and for directing this request to the appropriate software/open-source compliance team.

Regards,
MANA Genesis 4.0 project maintainer

## Куда отправить

- realme Europe support email listed on official support pages: service.eu@realme.com
- Official contact page: https://www.realme.com/eu/support
- Official contact form: https://www.realme.com/global/support/email-us
- Official realme kernel-source GitHub organization: https://github.com/realme-kernel-opensource

The public GitHub organization contains many model-specific repositories, but a repository for another realme model or the same SoC is not proof of RMX5566 compatibility. Do not use an unrelated kernel tree as a substitute.

## Google / Android

AOSP source and technical documentation are publicly maintained at https://source.android.com/ and https://android.googlesource.com/. Google’s AOSP documentation describes the platform and build process; it does not promise custom-ROM engineering support or release OEM proprietary files for a realme device.

For platform questions, use the public AOSP documentation and Android Open Source Project community channels. Device-specific kernel/vendor files must come from the device OEM or a legally compatible public release.

## MANA engineering gates

- [ ] Confirm exact SKU/build fingerprint without exposing personal identifiers.
- [ ] Obtain and verify official kernel source provenance, license, branch and commit.
- [ ] Compare source kernel release/config with the reported 5.15.197.
- [ ] Obtain matching DT/DTBO and vendor modules; verify ABI/KMI.
- [ ] Obtain VINTF manifests/matrices and run compatibility checks.
- [ ] Obtain a complete, legally redistributable device tree and build metadata.
- [ ] Build and test generic AOSP separately; do not label it RMX5566 firmware.
- [ ] Build MANA as a Soong system app only after the module is complete.
- [ ] Run CTS/VTS and device-specific smoke tests where possible.
- [ ] Preserve AVB, SELinux enforcing, stock recovery path and rollback safety.

## Current status

No request has been sent by this repository commit. This document is a ready-to-send technical request. No OEM response or RMX5566 source release has been verified yet.
