---
name: ECS migration documentation pattern
description: Structure used for documenting event-driven-to-ECS migrations in Voyager research papers
type: project
---

Voyager migrates Minestom event-driven gameplay handlers (Netty thread) into tick-bound ECS systems. Papers documenting such migrations should include:

1. A thread-ownership table (which thread writes/reads each field) — this is the load-bearing artifact for the architecture.
2. A pre/post comparison table (shared state items, sync primitives, handler lines, coverage, ordering).
3. An explicit system-ordering justification relative to `ElytraPhysicsSystem` and `RingCollisionSystem`.
4. A references section citing Minestom docs, Nystrom Game Programming Patterns, Herlihy/Shavit, and Manson/Pugh/Adve JMM paper (IEEE format).

**Why:** The recurring architectural question in these migrations is thread-safety + ordering, not algorithmic. The above tables make the invariants legible to reviewers.

**How to apply:** When a future migration paper is requested (e.g., ring-collision, scoring, cup-flow moving from events into ECS), reuse this structure and cite the firework-boost paper as prior internal work.
