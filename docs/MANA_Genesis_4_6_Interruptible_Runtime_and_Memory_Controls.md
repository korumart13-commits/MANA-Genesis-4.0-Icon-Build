# MANA Genesis 4.6 — Interruptible Runtime and Owner Memory Controls

## Status

This document describes the runtime contract implemented in the integration source. It is not evidence that a live model provider, cloud service, or device-control adapter is already connected.

## Owner-controlled stop signal

- `ManaCancellationSignal` is a host-owned, thread-safe cancellation signal.
- The owner-facing host calls `requestStop()`. Only the first request changes the signal and returns `true`.
- Model and tool adapters may register an `onStop` callback. They must close the returned `AutoCloseable` when the operation finishes.
- Adapters should cancel in-flight HTTP requests, close streams, interrupt cancellable jobs, and stop before starting any further side effect.
- Cancellable model adapters implement `ManaCancellableReasoningModel`; cancellable tool adapters implement `ManaCancellableCognitiveTools`.
- The runtime checks the signal before starting, between reasoning cycles, before tool policy checks, before tool execution, and after cancellable operations return.
- If stop is already requested, the runtime does not recall memory, call the model, execute tools, or save a new interaction summary.

Legacy model/tool adapters remain supported for compatibility. They can only be stopped at runtime checkpoints; they cannot be forcibly interrupted while a blocking call is in progress. A future adapter must implement the cancellable contract before it is described as immediately interruptible.

## Memory and dialogue controls

- The home screen exposes a local memory review dialog and a two-step clear confirmation.
- Clearing memory removes persisted cognitive-memory entries and clears the current in-memory dialogue context.
- Dialogue state is bounded and is supplied to the reasoning adapter as untrusted context, never as policy instructions.
- Local memory remains app-private storage. This implementation does not claim application-level encryption or cloud synchronization.
- The basic secret-marker filter is a safeguard, not a complete secret detector. Users should not store passwords, recovery codes, API keys, or other credentials in MANA memory.

## Security boundaries

- Cancellation does not grant or revoke Android permissions; Android remains authoritative.
- Unknown and sensitive tool actions remain default-deny or require owner confirmation.
- Stop callbacks must not be used to bypass owner confirmation or continue work in a detached background process.
- No hidden persistence, self-replication, privilege escalation, firmware flashing, or silent installation is permitted.

## Verification still required

- Compile and unit-test the exact branch head in CI.
- Add adapter-level tests proving that a registered callback aborts a real network request or other long-running operation.
- Wire the host UI's stop control to a `ManaCancellationSignal` instance.
- Test stop races on a physical device before claiming end-to-end interruption.
