# MANA Presence Engine — persistent avatar and phone integration

Status: implementation specification and current Android prototype behavior, 2026-11-25.

## Purpose

MANA has one persistent identity and one original avatar. The avatar is the visible presence of the same MANA intelligence used by chat, voice, memory, diary, planning and device interaction. It is not a separate assistant or a decorative character.

## Android prototype

The current project already contains:
- `ManaAvatarService`: foreground service started by the owner from MANA Home;
- `SYSTEM_ALERT_WINDOW` permission flow: the owner grants Android permission before the avatar can appear over other apps;
- `WindowManager` application overlay containing the bundled local avatar WebView;
- persistent foreground notification with a stop action;
- explicit stop control in MANA Home and overlay close control;
- no continuous microphone capture.

Recent implementation adds:
- drag the floating avatar to a different screen position;
- tap the avatar to open MANA Home;
- keep the avatar visible while the owner switches to a book reader, browser or another app, as long as Android allows the foreground service and overlay to remain active.

The service is opt-in and user-started. It must not silently launch on boot, evade Android background restrictions, hide its notification, or keep microphone/camera active without an explicit user action and permission.

## Interaction design

- Idle: avatar remains visible, subtle idle animation, no unsolicited audio.
- Tap: open MANA full interface.
- Drag: move the avatar to a preferred edge/corner.
- Close: hide the overlay.
- Notification: persistent status and stop action; tapping it should return to MANA.
- Listening: only after explicit voice action or configured wake interaction with transparent microphone indication.
- Speaking: synchronize avatar mouth/expressions with MANA voice output.
- Thinking: display a quiet visual state while local reasoning is running.
- Interrupted: stop speech and return to idle when the owner cancels.

## Long-term MANA OS integration

On a future MANA-native OS build, Presence Engine should be a first-party system component, coordinated with the lock screen, home screen, app lifecycle, accessibility, notification shade and power management. System-level privileges are not available to an ordinary APK and cannot be simulated by adding manifest flags. Until the device-specific OS build is real and verified, the Android overlay remains the supported prototype.

## Device integration roadmap

1. Validate foreground-service startup and overlay behavior on RMX5566 / Android 16.
2. Add robust avatar position persistence and edge snapping.
3. Connect avatar states to the same MANA cognitive runtime and speech events.
4. Add owner-controlled wake gesture / quick tile / notification action; no always-on microphone by default.
5. Build the MANA-native system service only with a valid device-specific AOSP base, vendor interfaces and signed system image.
6. Test battery use, memory pressure, process recreation, screen rotation, lock/unlock and accessibility conflicts.

## Acceptance tests

- User can enable/disable overlay from MANA Home.
- Android permission is requested through the official system settings screen.
- Avatar can be dragged and tapped.
- Notification clearly identifies active presence and provides stop action.
- Overlay is removed when stopped or permission is revoked.
- No hidden audio capture occurs.
- MANA's avatar and dialogue share the same local identity and memory.
- Behavior is measured on RMX5566; documentation alone does not mean the device test passed.
