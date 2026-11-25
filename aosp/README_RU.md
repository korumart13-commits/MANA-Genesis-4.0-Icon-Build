# MANA Genesis 4.0 — AOSP integration status (RU)

## Что уже находится в репозитории

Ветка mana-avatar-voice-server содержит Android-приложение MANA и Kotlin-модули: главный экран, чат, LiteRT-LM adapter, локальную память, журнал опыта и подтверждённые навыки, дневник, Life Manager, безопасный слой Android-инструментов и пользовательский аватар.

Генератор Android-проекта копирует эти исходники в Gradle-проект. Он также регистрирует MANA как обычное приложение и как альтернативный HOME launcher. После установки пользователь сам выбирает MANA в системном диалоге домашнего приложения. Это не делает APK привилегированным системным пакетом.

## Что добавлено для перехода к ОС

Требования к сохранению совместимости приложений Android 16, APK/DEX, Google Play и лицензированию GMS описаны в [ANDROID_16_COMPATIBILITY_AND_GOOGLE_ECOSYSTEM_RU_EN.md](ANDROID_16_COMPATIBILITY_AND_GOOGLE_ECOSYSTEM_RU_EN.md). Этот документ использует официальные материалы Android и отделяет AOSP от закрытых компонентов Google.

Каталог aosp/ хранит продуктовую спецификацию, точку подключения MANA в AOSP и аудит device bring-up для realme RMX5566. Product makefile подключает пакет MANA только если в конкретной сборке AOSP действительно определён Soong-модуль ManaHome.

## Почему прошивка RMX5566 пока не является готовым артефактом

Известные параметры устройства: RMX5566 / RE645F, Qualcomm SM6225, arm64, Android 16, ядро 5.15.197, A/B и dynamic partitions. Эти сведения недостаточны для безопасной сборки и прошивки.

Перед device build необходимы проверенные OEM kernel source именно для данного SKU и kernel release; defconfig, DTS/DTBO и vendor kernel modules с совместимым KMI; vendor/odm blobs, firmware и proprietary build files; точные VINTF manifests/matrices и HAL compatibility; partition sizes/layout, boot/vendor_boot/init_boot/recovery details; AVB/vbmeta chain и разрешённые владельцем ключи подписи.

Нельзя заменять отсутствующие vendor-компоненты случайными файлами или отключать Verified Boot. Нельзя прошивать generic AOSP image на реальное устройство.

## Целевой порядок интеграции

1. Сборка и тесты Android APK на CI.
2. Soong-модуль MANA (ManaHome) с ресурсами, локальным аватаром и LiteRT-LM runtime.
3. AOSP product overlay: MANA как HOME/launcher по умолчанию только в пользовательском product config.
4. Системные интеграции через публичные Android APIs и явно выданные permissions.
5. После получения полного device tree/vendor source — отдельная сборка device target и тесты на эмуляторе, затем проверка на RMX5566 без обхода AVB.
6. Только после полного тестирования — recovery/OTA package и инструкции возврата.

## Текущий статус

- Android app source: есть в этой ветке.
- MANA local-first architecture: реализована на уровне исходников; модель загружается отдельно, не вшита в APK.
- APK последней ревизии: не подтверждён успешной CI-сборкой.
- Полный AOSP source tree в этом репозитории: отсутствует.
- RMX5566 device tree/vendor blobs/kernel source: не подтверждены.
- Готовый flashable ZIP/IMG: не создан и не должен заявляться как готовый.