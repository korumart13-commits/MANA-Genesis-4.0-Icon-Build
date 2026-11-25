# MANA Genesis 4.0 — Live Mode and Launcher

## Required behavior
- MANA Home is an Android HOME-role candidate. The owner must select it in Android Home settings; the app cannot silently replace the stock launcher.
- The native home activity is the home surface, not a redirect page. A Samsung One UI-inspired visual direction means a clean status/header area, adaptive app grid, dock, widgets/shortcuts, app drawer, search and launcher settings.
- The persistent avatar is a user-started foreground service with an ongoing notification and a floating overlay. Android's Display over other apps permission is required.
- The avatar service does not capture microphone audio. Speech recognition begins only after an explicit user action; the exact phrase "Оживи" activates Live Mode in that session.
- Live Mode checks modules and permissions, analyzes locally stored development ideas, and reports unavailable integrations. It never grants permissions, silently changes system settings, bypasses Android security, flashes firmware or self-installs code.
- Elevated or system-wide changes remain proposals requiring owner approval and successful tests.

## Acceptance checks
1. MANA Home is registered with MAIN/HOME/DEFAULT and can be selected by the user.
2. A user can start and stop the persistent avatar.
3. Overlay permission is requested only when needed.
4. The avatar stays visible above other apps while the foreground service runs.
5. An ongoing notification and close control stop the service.
6. "Оживи" is handled only in a user-started speech-recognition session; no always-on microphone claim.
7. Automated tests and APK assembly pass before distributing an APK.

## Android boundary
The HOME role, overlay permission, foreground-service policy and system permissions remain controlled by Android. A normal app cannot obtain unrestricted system privileges because a voice command was spoken.
