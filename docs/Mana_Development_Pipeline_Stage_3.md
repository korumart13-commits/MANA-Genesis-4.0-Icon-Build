# MANA Development Pipeline — Stage 3

## Purpose
A local, persistent state machine for each improvement proposal. It makes the lifecycle explicit and auditable instead of treating idea collection as completed self-development.

## Lifecycle
`IDEA → ANALYZED → APPROVAL_REQUIRED (when needed) → PATCH_DRAFTED → PATCH_VALIDATED → TESTS_PASSED → READY_FOR_OWNER_REVIEW`

A proposal can be rejected at each applicable stage or archived. There is deliberately no automatic deploy/install/firmware stage.

## Rules
- Persistent records are stored in app-private SharedPreferences.
- Each transition is validated against an allowlist.
- Moving from APPROVAL_REQUIRED to PATCH_DRAFTED requires an explicit ownerApproved argument.
- Patch references are required before validation stages.
- The component records a bounded local audit trail.
- It does not execute patches, invoke shell commands, change permissions, install APKs, or touch firmware.

## Integration status
The runtime is included in the Android build source overlay. It is a lifecycle engine, not yet connected to every dashboard control. CI compilation is required to confirm compatibility with the archived Android source.
