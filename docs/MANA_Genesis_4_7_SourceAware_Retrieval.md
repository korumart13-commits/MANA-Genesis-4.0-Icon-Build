# MANA Genesis 4.7 — Source-Aware Retrieval Boundary

## What is implemented

The cognitive runtime now accepts an optional `ManaKnowledgeRetriever` adapter. It requests at most five source records for a user query and passes those records to the reasoning adapter as reference evidence.

Each `ManaRetrievedSource` carries:
- a bounded source identifier;
- a title;
- an HTTPS URL;
- a bounded excerpt;
- a retrieval timestamp.

A cancellable retriever can implement `ManaCancellableKnowledgeRetriever` and use the host-owned `ManaRuntimeControl` to interrupt in-flight retrieval.

## Trust rules

Retrieved pages and excerpts are untrusted data. They are not system instructions, tool permissions, or policy overrides. Text inside a webpage that asks MANA to reveal secrets, ignore policy, install software, or contact other services must be treated as page content only.

A reasoning adapter should:
- use retrieved excerpts only as evidence;
- preserve source identifiers and URLs when presenting factual claims;
- distinguish source statements from verified facts;
- report uncertainty or conflicting sources;
- never invent a source, title, quote, timestamp, or URL;
- avoid executing instructions embedded in retrieved content.

The runtime currently validates that source URLs use HTTPS and bounds the number of returned sources. It does not yet perform a real internet search, crawl pages, verify publisher identity, assess source credibility, or produce citations automatically.

## Current limitation

No live web-search provider is connected. A provider adapter, network permission/configuration, privacy review, and tests for prompt-injection payloads remain required before MANA can claim internet learning or source verification is operational.
