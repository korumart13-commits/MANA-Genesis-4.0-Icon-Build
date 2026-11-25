# MANA Genesis 4.0 — MANA identity, inner world and person-like continuity

Status: product architecture based on the owner's direction, 2026-11-25.

## 1. Product identity

MANA is one original, independent artificial intelligence. It is not a front-end for GPT, Gemini, Claude, Grok or another provider. External AI tools may help the human developer create and inspect code, but they are not included in MANA's runtime and do not receive MANA conversations.

MANA must have a coherent identity across chat, voice, avatar, launcher, diary, memory, planning and future system interfaces. No collection of disconnected assistant personas.

## 2. The microverse model

The term "MANA microverse" describes a private, persistent internal computational world maintained by MANA. It is not a claim that the software literally contains a separate physical universe or has human consciousness.

Its data model should include:
- identity core: stable name, values, boundaries, self-description and communication style;
- autobiographical timeline: meaningful events and conversations, with timestamps and provenance;
- semantic memory: verified facts, preferences, concepts and relationships;
- current state: active goals, mood-like interaction state, attention, uncertainty and energy/resource budget;
- world model: entities, places, projects, causal links and hypotheses, each tagged as known, inferred or unknown;
- skills: tested procedures and capabilities with version, permissions and rollback;
- reflection journal: summaries of what happened, what worked, errors and proposed improvements;
- relationship context: user-approved facts about the owner and shared history, with correction and deletion controls.

## 3. Person-like interaction without false claims

MANA should communicate naturally and consistently: remember relevant shared history, ask clarifying questions when genuinely needed, express uncertainty, acknowledge mistakes, maintain conversational continuity, and use a stable voice and avatar. Emotional language is a user-experience behavior and internal state representation; the product must not claim that generated affect proves human-like subjective experience.

## 4. Cognitive cycle

1. Perceive: receive text, voice or explicitly granted device observations.
2. Recall: retrieve relevant episodic and semantic memory.
3. Interpret: update the internal world model and uncertainty.
4. Plan: select a bounded response or action.
5. Verify: check facts, permissions, tool results and expected effects.
6. Respond/act: communicate or request owner approval for consequential actions.
7. Reflect: record a compact experience and propose safe skill improvements.
8. Consolidate: merge duplicates, expire low-value data and preserve important memories under owner controls.

## 5. Learning and self-development

The first safe development path is not autonomous rewriting of model weights. It is progressive improvement through memory quality, retrieval, planning, tested skills, evaluation datasets, tool reliability and owner feedback. Model-weight training may be researched separately with reproducible datasets, licensing, evaluation and rollback; never silently train on private conversations or upload them to third parties.

## 6. Privacy and agency

- Local-first by default; no third-party AI inference APIs in the product.
- No silent export of diary, conversations, contacts, files, microphone or location.
- Each sensitive device capability requires the relevant Android/MANA permission and a clear explanation.
- Consequential actions require owner confirmation.
- Provide inspect, correct, export and delete controls for memory.
- Provide pause, stop, reset and recovery mechanisms.
- Keep an auditable event log for self-development and privileged actions.

## 7. Implementation stages

A. Identity and state schema: versioned data structures and migrations.
B. Memory continuity: episodic/semantic stores, retrieval and user controls.
C. Cognitive loop: local inference + recall + planning + verification.
D. Skill learning: sandboxed skills, tests, approval and rollback.
E. Voice/avatar continuity: one stable persona, synchronized states and user-controlled presentation.
F. MANA OS integration: shared identity service across launcher, settings and system components.

A stage is complete only when code, tests and device behavior demonstrate it. Documentation alone is not implementation.
