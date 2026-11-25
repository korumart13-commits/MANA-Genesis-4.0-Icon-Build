# MANA Genesis 4.0 — Linux kernel port для realme RMX5566 (RE645F)

**Статус: исследование завершено частично; прошиваемая сборка заблокирована до получения точных исходников и конфигурации устройства.**

## 1. Идентификация целевого устройства

Зафиксированные данные из аппаратного аудита владельца:
- Product/model: `RMX5566RU` / `RMX5566`
- Device: `RE645F`
- Board/platform: `bengal`
- SoC: `Qualcomm SM6225` (Snapdragon 685 family)
- ABI: `arm64-v8a`
- Текущая версия ядра: `5.15.197`
- Android: 16 (API 36, по диагностике владельца)
- Bootloader: locked; AVB: green; разметка: A/B и dynamic partitions (по диагностике владельца).

Внешние каталоги устройств также связывают RMX5566 с RE645F, SM6225 и ядром 5.15.197. Официальная страница realme C85 указывает Snapdragon 685 и Android 15 в исходной поставке; это не противоречит обновлению конкретного аппарата до Android 16, но точную ветку firmware необходимо брать из build fingerprint и OTA-версии самого телефона.

## 2. Найденные источники и ограничения

### Потенциально полезные, но НЕ подтверждённые как совместимые

- `cd-Crypton/android_kernel-manifest`, ветка `realme/sm6225`: manifest указывает на `carlodandan/android_kernel_realme_sm6225`, но его default revision — `master-kernel-build-2021`; это старый кандидат и он не подтверждает соответствие текущему ядру 5.15.197.
- `xiaomi-6225-AD`: опубликованы отдельные SM6225/Bengal 5.15 device-tree и module проекты. Это Xiaomi-specific деревья; переносить их на realme RMX5566 нельзя без сверки DTS, regulator/GPIO, panel, touch, camera, storage, modem, PMIC и vendor ABI.
- `android_kernel_xiaomi_topaz` / Redmi Note 12 4G: устройство использует Snapdragon 685/SM6225, но это другой производитель и другая плата. Его DTS и vendor blobs не являются готовой базой для RMX5566.
- Сообщество публикует Android 13/5.15 и mainline bring-up для отдельных SM6225 устройств. Они полезны как технические примеры, но не доказывают bootability на RE645F.

### Что пока НЕ найдено/не подтверждено

- Официальный исходный архив realme для точной модели RMX5566 / RE645F и точной версии прошивки.
- Точная defconfig/config fragment для работающего стокового ядра RMX5566.
- DTS/DTBO для платы RE645F и соответствующая таблица board-id/msm-id.
- Полные proprietary vendor modules и их точные symbol CRC/KMI требования.
- Совместимые vendor_boot/init_boot/boot header metadata и ramdisk layout из текущей прошивки.
- GPL source release tag/commit, совпадающий с kernel release `5.15.197`.

## 3. Вывод о совместимости

Целевая платформа — ARM64 Qualcomm Bengal/SM6225, Android GKI-поколения 5.15. Но одного совпадения SoC недостаточно. Для загрузки необходимы точный device tree, правильные kernel config, vendor modules с совместимым KMI, корректные boot image headers, ramdisk, DTBO и AVB chain.

**Запрещённые предположения в этом проекте:** использовать Xiaomi DTS/blobs как будто они realme; собирать generic Image и называть его готовым boot.img; подменять ядро 5.15.197 на 6.x; менять AVB/verity или обходить locked bootloader; публиковать flashable ZIP до проверки точной firmware.

## 4. План отдельной сборки

### Этап A — зафиксировать firmware baseline
1. Снять полный read-only аудит: `uname -a`, `uname -r`, `getprop ro.build.fingerprint`, `ro.build.version.incremental`, `ro.vendor.build.fingerprint`, `ro.boot.slot_suffix`, `ro.boot.verifiedbootstate`, `ro.boot.vbmeta.device_state`.
2. Получить точные названия и размеры разделов boot, init_boot, vendor_boot, dtbo, vendor_dlkm, odm_dlkm, vbmeta (только чтение).
3. Зафиксировать sha256 доступных стоковых образов и их происхождение. Не публиковать личные идентификаторы устройства.

### Этап B — найти и проверить исходники
1. Найти официальный realme GPL release для RMX5566/RE645F и точного OTA/build fingerprint.
2. Сопоставить release string ядра и git commit с `5.15.197`.
3. Получить matching defconfig, DTS/DTBO и vendor module list.
4. Сопоставить `Module.symvers`, KMI и symbol CRC с модулями из текущего vendor firmware.
5. Проверить лицензии и происхождение каждого дерева/патча.

### Этап C — reproducible build (только CI)
1. Создать отдельный kernel workspace, не смешивая его с Android app/core.
2. Зафиксировать manifest, все git commit SHA, toolchain и build container по digest.
3. Сначала собрать только `Image` и модули; сохранить полный build log, config, compiler version и hashes.
4. Запустить `dtbs_check`, config diff, module/KMI compatibility checks и сборку image metadata.
5. Упаковывать boot image только после доказательства точной схемы разделов и формата образа на этом firmware.

### Этап D — безопасная валидация
- До разрешения владельца — никаких fastboot, recovery flash, boot-from-RAM, unlock, vbmeta/AVB операций.
- Проверить артефакты статически: header, page size, header version, cmdline, ramdisk, DTB/DTBO, module dependencies, KMI и SHA-256.
- Первые аппаратные тесты — отдельный согласованный этап с планом восстановления и точным стоковым firmware. В текущем запросе он не выполняется.

## 5. Текущий статус артефактов

| Артефакт | Статус |
|---|---|
| Аппаратный профиль RMX5566/RE645F | Частично подтверждён |
| Точный официальный kernel source | Не подтверждён |
| Точный defconfig | Не найден |
| Точный DTS/DTBO | Не найден |
| Vendor modules + KMI baseline | Не собран |
| Kernel Image | Не собирался |
| boot.img / vendor_boot.img | Не создавались |
| Flashable ZIP | Не создавался и не должен создаваться на текущих данных |

**Решение:** не запускать kernel compilation на неподтверждённом Xiaomi/общем SM6225 дереве. Сначала получить точный firmware baseline и matching source release. Это предотвращает создание заведомо несовместимого образа и риск не загружаемого устройства.
