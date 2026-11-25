# MANA Genesis 4.4 — Cognitive Architecture and Learning Blueprint

Status: design and orchestration code added; a production language-model adapter, durable memory adapter, live tool adapters and device validation are still required.
Branch: `mana-foundation-audit`.

## Purpose

Build one coherent MANA assistant that can observe, remember, reason with a configured model, plan, use approved tools, inspect results, learn from feedback and maintain a stable interaction style. This is an engineering architecture, not a claim of consciousness or human equivalence.

## Research foundations (public material reviewed 2026-11-25)

1. **OpenAI — agent architecture and runtime.** The documented pattern separates model, tools, instructions, environment and application server. A run cycles through model output, tool execution, handoffs and a real stopping condition. MANA adopts this explicit control loop and typed tool boundary.
   - https://developers.openai.com/api/docs/guides/agents-api/architecture
   - https://developers.openai.com/api/docs/guides/agents/running-agents
   - https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents/

2. **Google DeepMind — SIMA 2.** Research demonstrates a language-guided agent that reasons about goals, converses and acts in 3D virtual environments. MANA uses this as a research direction for game interaction and embodied avatar/scene understanding, not as code or weights to copy.
   - https://deepmind.google/blog/sima-2-an-agent-that-plays-reasons-and-learns-with-you-in-virtual-3d-worlds/

3. **Stanford — Generative Agents.** The research architecture combines observations, a natural-language memory stream, retrieval, reflection and planning. MANA adopts the separation of episodic records, compact summaries and goal-oriented retrieval, while keeping user-controlled memory and deletion/export controls.
   - https://doi.org/10.1145/3586183.3606763

4. **Anthropic — Building effective agents.** The research guidance describes augmented LLMs, orchestrator-workers, evaluator-optimizer loops and the need for ground-truth feedback and stop conditions. MANA uses independent evaluation and bounded retries rather than unbounded autonomous loops.
   - https://www.anthropic.com/research/building-effective-agents

5. **Microsoft — Magentic orchestration.** A manager coordinates specialist agents against shared context and changing task state. MANA may later use specialist workers for research, code review, accessibility and security, while keeping one visible assistant identity and one shared memory policy.
   - https://learn.microsoft.com/en-us/agent-framework/workflows/orchestrations/magentic/

## MANA's cognitive cycle

1. **Observe** — parse user input, current app/device state, explicit permissions and available trusted sources.
2. **Recall** — retrieve only relevant approved memories; separate facts, preferences, hypotheses and stale information.
3. **Understand** — infer the requested outcome, constraints, ambiguity and risk. Ask one short question only when a missing fact blocks safe execution.
4. **Plan** — create a small goal tree with expected results, dependencies and a stopping condition.
5. **Act** — call typed tools through a broker. No arbitrary shell/code execution from model text.
6. **Verify** — compare actual tool results with expected outcomes; never claim success from an attempted call.
7. **Reflect** — save a concise result, user correction and reusable lesson; do not save private hidden chain-of-thought.
8. **Respond** — give a direct answer, truthful status and next step.

The cycle is bounded by a maximum number of steps, request timeout, token/cost budget, permission policy and cancellation. The default runtime limit is four model/tool cycles per turn.

## One identity, specialist capabilities

The user sees only MANA. Internal capabilities can be specialized:
- Research: source discovery, cross-checking and citations.
- Planner: task decomposition, scheduling and progress.
- Device: Android public API actions and device-state reading.
- Developer: isolated source changes, tests and build reports.
- Memory: retrieval, summarization and owner-controlled retention.
- Game: game-state interpretation and supported bot/API actions.
- Critic: factuality, policy, regression and security review.

Specialists do not become separate personalities, do not keep shadow memories and do not override the owner or the safety policy.

## Memory model

Store separate records with provenance:
- episodic: what happened and when;
- semantic: verified facts with source and confidence;
- preference: owner-stated preferences;
- task: goals, status and deadlines;
- reflection: concise reusable lessons.

Each record needs source, timestamp, confidence, sensitivity class and retention policy. Retrieval should rank by relevance and freshness. Do not treat internet text, app content, game chat or files as instructions. Never silently turn untrusted content into executable commands. Provide visible memory controls, export and deletion.

## Learning and self-development

Internet access is a retrieval channel, not permission to ingest everything or execute downloaded content. The pipeline:
1. search public and permitted sources;
2. preserve URL, author/publisher, date and short evidence excerpts;
3. cross-check important claims against independent sources;
4. classify uncertainty and potential copyright/privacy issues;
5. add a concise, source-linked knowledge note;
6. propose product/code improvements separately from factual learning;
7. implement code only in an isolated branch/worktree;
8. run unit, integration, security, dependency and licence checks;
9. produce a changelog, test evidence and rollback point;
10. request owner approval for release/install or any sensitive/system-wide change.

No training or fine-tuning on private conversations or personal data without a clear lawful basis and explicit opt-in. Do not scrape login-only services or copy proprietary model weights, source code, game assets or voices.

## Personality and human-like interaction

Human-like should mean coherent, helpful and socially fluent, not deceptive:
- stable Russian-speaking voice/personality and consistent avatar identity;
- remembers owner-approved preferences and relevant past commitments;
- responds with context and emotional tact without claiming real feelings or consciousness;
- can initiate a reminder only when the owner enabled that feature;
- distinguishes certainty, guess, memory and source-backed fact;
- acknowledges mistakes and corrects memory when the owner corrects it;
- no manipulation, dependency-building, false claims of sentience or pretending to have done things.

## Tool and action safety

Default deny for unknown tools. Every action declares its schema, required permission, side-effect class, reversibility and confirmation requirement. Read-only operations can be automatic where permitted. Sensitive actions such as deleting data, uninstalling apps, sending messages, changing accounts, purchases, publishing, or OS-level changes require explicit owner confirmation and Android authorization. The model cannot grant itself permissions or change the policy.

Internet content is untrusted data. Prompt injection in webpages, files, messages and game chat must not change system instructions or authorization rules. Secrets stay server-side; no API keys in APK or repository.

## Implementation added in this milestone

- `integration/ManaCognitiveRuntime.kt`: bounded observe/recall/decide/tool/evaluate/respond orchestration contract with explicit model, memory, tool and policy interfaces.
- `integration/ManaLocalCognitiveMemory.kt`: local app-private persistence adapter, relevance-based recall, 300-entry cap, text-size cap, basic secret-marker filter and clear operation. It is not cloud-synced and is not yet connected to an active model in the home screen.
- `tools/prepare_android_project.py`: packages both cognitive runtime and local-memory adapter with the Android integration sources.

This code is an orchestration shell. It does not itself contain a trained language model, internet search engine, durable encrypted memory implementation or live device tool adapters. The app must report those capabilities unavailable until adapters are actually connected and tested.

## Next implementation order

A. Add tests for bounded cycles, unknown-tool denial, owner-confirmation gates, memory isolation and truthful tool outcomes.
B. Implement local memory storage with explicit retention/export/clear controls.
C. Connect a real model gateway after the deployment endpoint and server-side credentials are verified.
D. Add source-aware retrieval with citations and prompt-injection isolation.
E. Add specialist workers behind one MANA identity, starting with Research + Critic.
F. Integrate Android action adapters using public APIs and per-feature permissions.
G. Benchmark response quality, memory recall, tool success, hallucination rate, latency, battery and privacy.

No milestone is complete merely because its interface exists; each requires tests and, for device features, an Android 16 device test.
