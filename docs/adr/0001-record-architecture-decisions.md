# ADR 0001: Record Architecture Decisions

## Status

Accepted

## Date

2026-07-25

## Context

As InspectIQ evolves, we will make architectural and technical decisions that affect the system's structure, technology choices, and trade-offs. Without a lightweight record of these decisions, future contributors (or our future selves) will lack the context behind *why* things are the way they are — leading to repeated discussions, accidental reversals, or fear of changing code whose rationale is lost.

## Decision

We will use **Architecture Decision Records (ADRs)** as described by Michael Nygard in his [blog post](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions). Each ADR is a short Markdown file stored in `/docs/adr/` and numbered sequentially (`0001-...`, `0002-...`, etc.).

Each ADR follows this template:

- **Status** — Proposed, Accepted, Deprecated, or Superseded (with link to replacement).
- **Date** — When the decision was made.
- **Context** — The situation or problem that prompted the decision.
- **Decision** — What we decided to do.
- **Consequences** — The expected outcomes — both positive and negative.

## Consequences

- Every significant architectural or technology decision will be captured in a discoverable, version-controlled location.
- ADRs are intentionally brief — a few paragraphs, not a design document. If a decision needs more detail, link to a separate doc.
- Superseded ADRs are never deleted; they are marked as **Superseded** with a pointer to the replacement ADR, preserving the decision history.
