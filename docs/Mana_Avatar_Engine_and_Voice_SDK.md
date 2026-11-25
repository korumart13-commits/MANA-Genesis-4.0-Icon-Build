# ManaOS — Mana Avatar Engine & Voice SDK
Version: 1.0.0
Status: implementation contract; not an implemented engine or included 3D asset.

## Product requirement
Mana is the assistant identity. The 3D avatar is its primary embodied interface. It must be able to listen, speak, animate, express emotions, and be summoned from supported Android surfaces. It must not pretend to be conscious or claim abilities it does not have; it is an AI agent with visible controls and permission boundaries.

## Character identity
Create an original, redistributable character matching the owner's approved design brief:
- long black hair; pale complexion; dark-navy eyes;
- predominantly dark clothing; dark manicure;
- clothing, manicure, accessories and optional hand prop are independently changeable;
- preserve face, proportions, voice identity and core silhouette across outfits.
The character may be anime-inspired, but do not extract, redistribute, or ship a proprietary Mita game model, textures, voice recordings, or animations without an explicit license. Use an original model or a properly licensed user-provided asset. Keep the exact owner-approved square icon as a separate asset when supplied.

## Runtime and asset format
- Canonical avatar format: VRM 1.0; accept GLB/glTF 2.0 for compatible non-VRM assets.
- Animation interchange: VRMA / glTF animation clips.
- Humanoid rig must map standard bones, fingers, head, eyes, jaw, and optional secondary hair/cloth bones.
- Required facial expressions: neutral, happy, angry, sad, relaxed, surprised; procedural blink and gaze; mouth shapes aa, ih, ou, ee, oh; optional custom expressions (teasing, curious, embarrassed, concerned, sleepy, focused).
- Validate model at import: missing bones, invalid materials, excessive polygons/textures, absent expressions, broken spring bones, animation retargeting errors. Reject or downgrade safely rather than crash.
- Keep avatar assets modular: base model, outfit bundles, accessories, expression profiles, motion packs, voice profile. Every bundle carries version, license, hash, size and compatibility metadata.

## Animation engine
Use a layered animation graph, not one fixed looping clip:
1. Base idle: breathing, blinking, subtle posture shifts, eye focus.
2. Upper-body gesture layer: conversational hand/arm gestures, pointing, shrug, wave, thinking pose.
3. Locomotion/full-body layer: stand, sit, turn, step, walk-in-place where contextually appropriate.
4. Facial layer: emotion weights, gaze, blink and speech visemes with conflict/override handling.
5. Secondary motion: hair, clothing and accessories with bounded physics.
6. Interrupt/priority layer: safety stop, user command, speech start/stop, app lifecycle and low-performance fallback.
Blend transitions smoothly; never allow two clips to fight over the same bones without explicit blending rules. AI chooses from the available action catalog; it does not invent arbitrary joint values without validation.
Support motion packs recorded or authored in Blender/Unity/other compatible tools and imported as VRMA/glTF clips. Future motion capture can be added through a retargeting pipeline. “Any movement” is not guaranteed: the engine can perform motions for which valid clips/procedural controllers exist.

## Performance tiers
- Tier A (default mobile): 30 FPS target where device thermals allow; reduced render resolution, capped hair/cloth physics, limited simultaneous blendshapes.
- Tier B (high quality): higher render scale and richer secondary motion when thermal/battery budget permits.
- Tier C (low-power): static/2.5D portrait or low-poly avatar, reduced update rate, no expensive physics.
- Adaptive controller monitors frame time, thermal status where Android exposes it, battery saver, and memory pressure; degrade gracefully and notify the user. Never run an unbounded background render loop.
- Use Android-compatible real-time renderer such as Filament for glTF/GLB, or a validated Unity/UniVRM runtime if the project chooses a Unity-based avatar module. Keep engine choice behind AvatarRenderer interface so it can be changed without rewriting AI logic. Benchmark on RMX5566 before selecting default quality.

## Voice and conversation
- Voice pipeline: explicit microphone permission -> speech recognition -> conversation orchestrator -> selected AI provider -> text response -> TTS -> viseme/lip-sync controller.
- Support barge-in: user can interrupt speech; cancel playback and return to listening.
- Speak only when voice mode is enabled or a configured trigger permits it. Provide mute, push-to-talk, wake phrase/trigger settings where supported, quiet hours, volume and voice preview.
- Default voice should be a soft, expressive Russian anime-style feminine voice, but select only a voice distributed under a license that permits app use. Do not download/clone a real actor's voice or use an unlicensed game/character voice. First try available Android TTS voices and report what is actually installed; allow an optional licensed provider voice pack. Do not hardcode credentials.
- TTS fallback: Android TextToSpeech; provider-specific high-quality TTS is optional and must show network/data disclosure. If no suitable voice exists, show “voice pack unavailable” rather than silently substituting.
- Lip sync uses audio timing/viseme estimation mapped to VRM mouth expressions. Coordinate emotion and mouth weights to avoid facial mesh conflicts.
- Proactive speech must be opt-in, rate-limited, context-aware, and suppressible. No unsolicited speech during calls, media, sleep/quiet hours, or when the user disables it.

## Android integration
- In-app avatar view always supported.
- Floating avatar uses Android overlay permission only after an explanation and user-granted special access. Show a persistent/clear active indicator, drag-to-move, resize, dismiss, and per-app/quiet-hour visibility policy. Do not attempt to bypass Android restrictions or force an overlay on top of secure screens.
- Background listening is off by default; microphone use must be visible and governed by Android permission and foreground-service requirements.
- Provide quick tile/notification shortcut only where supported and user-added.
- If overlay permission is denied, avatar remains available in the app and launcher shortcut.

## AI action contract
The assistant returns a structured AvatarAction, validated against a local allow-list:
- expression: one known expression ID and bounded weight;
- motion: known motion clip ID, priority, fade-in/out;
- gaze: target category (user/camera/UI/neutral), not arbitrary coordinates from untrusted text;
- speech: text/audio stream and viseme timing;
- outfit/accessory: installed asset ID only;
- duration and interruption policy.
All action outputs are bounded, cancellable and logged without sensitive audio/transcripts by default. Unknown action IDs fall back to idle/neutral. User can disable proactive behavior and clear logs.

## Owner Idea & Update Inbox (the requested future-update file)
Implement a dedicated “Ideas & Future Updates” module separate from the private personal diary and separate from diagnostic reports.
- Add ideas by text or voice; store title, description, category, priority, date, attachments, status and owner notes.
- Status flow: Inbox -> Clarify -> Planned -> Prototype -> Tests -> Owner Review -> Approved for Release / Rejected / Deferred.
- Mana may summarize, group duplicates, estimate effort, identify dependencies and draft implementation plans.
- Mana may generate changes only in an isolated branch/sandbox. It must run build, tests, lint, dependency/license and security checks.
- No self-approval, no silent production update, no deletion of Android security protections, no privilege escalation. Owner approval is required before release installation.
- Preserve an immutable audit trail of proposals, test results, approvals and release notes. Allow export/delete under owner control.
- A separate “System Health & Reports” view may contain battery/thermal/memory/network diagnostics; do not mix it with the user's ideas inbox.

## Suggested module interfaces
- AvatarRenderer: loadModel(uri), setQuality(tier), renderFrame(), dispose()
- AvatarRig: validate(), mapHumanoidBones(), setExpression(id, weight), setGaze(target), applyMotion(clipId, blend)
- AvatarActionController: submit(action), interrupt(priority), stopAll(), currentState()
- VoiceSession: requestMicPermission(), listen(), stopListening(), speak(text), stopSpeaking(), setVoice(profile)
- LipSyncController: consumeAudioFrames(), setVisemes(weights), reset()
- MotionCatalog: listInstalled(), validateClip(), installSignedPack()
- OutfitCatalog: listInstalled(), equip(assetId), restoreDefault()
- ProactivePolicy: canSpeak(context), scheduleAllowedTriggers(), quietHours()
- IdeaInbox: submitIdea(), updateStatus(), summarize(), createSandboxPlan(), attachTestReport(), requestOwnerApproval()
All interfaces must be asynchronous, cancellable, typed, and covered by unit/integration tests.

## Acceptance tests
1. Load the approved original VRM model without crash; validate rig and expressions.
2. Play idle, wave, thinking, listening, speaking, happy, concerned and interrupted states.
3. Speak Russian using an actually installed/licensed voice; show graceful fallback if unavailable.
4. Mouth movement follows speech and stops immediately when speech is cancelled.
5. Avatar overlay can be summoned/dismissed after explicit Android approval; denial leaves in-app mode working.
6. Run 10-minute avatar session with frame-time, memory, battery and thermal monitoring; verify adaptive quality and no runaway background loop.
7. Add a voice/text idea, summarize it, create a sandbox plan, attach test results and require owner approval before release.
8. Confirm no model, voice, API secret or signing key with unclear license is bundled.

## Reference technologies (evaluate, do not blindly copy)
- VRM 1.0 and VRMA provide portable humanoid rigs, expressions, gaze and reusable animation clips.
- Google Filament is a real-time rendering engine designed for Android and other platforms.
- Android TextToSpeech is a built-in fallback; available voices vary by device and installed TTS engine.
- Unity + UniVRM + an animation controller is an alternative if the repository chooses a Unity-based runtime; it increases app size and build complexity.
Choose the runtime after a proof-of-concept benchmark on the target phone, not by marketing claims.
