# Voyager Rebuild — Status Board

Local tracking for the greenfield rebuild. Nothing is mirrored to GitHub Issues; this file is the
single place where progress is recorded.

- **Design:** [`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`](docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md) — approved
- **Tickets in full:** [`docs/superpowers/specs/2026-09-09-voyager-greenfield-epics.md`](docs/superpowers/specs/2026-09-09-voyager-greenfield-epics.md)
- **E1 implementation plan:** [`docs/superpowers/plans/2026-09-09-e1-foundation.md`](docs/superpowers/plans/2026-09-09-e1-foundation.md)

Last updated: 2026-09-09

## Where we are

Design approved, epics broken out, E1 planned to the level of individual test cases. **No production
code has been written yet.** Execution of E1 is not authorised.

The tree being replaced still runs; production itself still runs the 2023 Kotlin build, so nothing
is under outage pressure.

## Decisions

| # | Decision |
|---|---|
| D1 | Greenfield rebuild, not incremental hardening |
| D2 | Same repository, new module tree alongside the old |
| D3 | Physics core first, then race loop |
| D4 | Parity proven by trace comparison against real Vanilla flights |
| D5 | Client keeps movement authority; server validates plausibility |
| D6 | Setup server moves to Minestom; Paper dropped entirely |
| D7 | Target Minecraft 26.2 now, follow to 26.3 later |
| D8 | Domain-oriented module cut (eight modules) |
| D9 | Existing ADRs are not binding for the new stack |
| D10 | `io.airlift:guice:10`, DI annotations confined to the composition roots |
| D11 | No legacy data import; the rebuild starts with an empty database |
| D12 | `CLAUDE.md` is superseded by the design spec |

## Stage board

### E1 — Foundation
Done when: ArchUnit imports every module and no rule runs empty.

- [ ] E1.1 buildSrc convention plugins
- [ ] E1.2 Eight-module Gradle skeleton and settings wiring
- [ ] E1.3 `voyager-api` core types
- [ ] E1.4 `voyager-fitness` ArchUnit rule suite
- [ ] E1.5 `CLAUDE.md` rewrite (D12) — needs approval before merge
- [ ] E1.6 CI wiring for the dual tree

### E2 — Vanilla parity (carries the project's central risk)
Done when: the trace suite is green within tolerance.

- [ ] E2.1 Recorder prototype spike — time-boxed, gates E2.2
- [ ] E2.2 Vanilla recorder, production build
- [ ] E2.3 26.2 decompile check: does `air_drag_modifier` touch the elytra drag path
- [ ] E2.4 Trace fixture capture, eight flight profiles
- [ ] E2.5 `voyager-physics` core implementation
- [ ] E2.6 Trace comparison harness and threshold calibration
- [ ] E2.7 Contract tests for the step-decomposition hierarchy

### E3 — Race core
Done when: a full race is playable without a server.

- [ ] E3.1 Race state machine (pure domain)
- [ ] E3.2 Ring collision, segment-plane intersection
- [ ] E3.3 Sealed strategy hierarchies
- [ ] E3.4 Map/cup/ring/boost model and loader
- [ ] E3.5 `TuningConfig` and per-race immutability snapshot
- [ ] E3.6 Injectable entity factory
- [ ] E3.7 End-to-end race test, fitness rules populated

### E4 — Platform and server — first flyable build
Done when: a player can fly a race. **This plus a green E2 is the cut-over gate.**

- [ ] E4.0 Spike: confirm pinned CloudNet RC and BOM coordinates
- [ ] E4.1 `Vec3`↔`Vec` conversion, Minestom `CollisionSpace`
- [ ] E4.2 Velocity exit (single class) and NaN boundary
- [ ] E4.3 Flight detection via real Minestom signals
- [ ] E4.4 Plausibility check, log-only in v1
- [ ] E4.5 Xerus tick driver
- [ ] E4.6 World management via Anvil loader
- [ ] E4.7 HUD output
- [ ] E4.8 Guice DI composition root
- [ ] E4.9 Configuration resolver
- [ ] E4.10 CloudNet v4 integration
- [ ] E4.11 Minestom hazard mitigations (#2017, #2267)
- [ ] E4.12 Spike: CloudNet node-to-service environment propagation
- [ ] E4.13 Decision: is `voyager-cloudnet-bridge` needed
- [ ] E4.14 Secrets bootstrap and the `VOYAGER_PERSISTENCE=off` path

### E5 — Persistence
Done when: records and profiles survive a restart.

- [ ] E5.1 Entity model and `V1__baseline.sql`
- [ ] E5.2 Repository ports, `StatelessSession` only
- [ ] E5.3 Read path: HQL constructor expressions
- [ ] E5.4 Write path: atomic upsert, one-transaction race completion
- [ ] E5.5 Tick-safe submission
- [ ] E5.6 `ProfileSlot` and join-time profile load
- [ ] E5.7 HikariCP configuration
- [ ] E5.8 Testcontainers integration suite
- [ ] E5.9 Rule 7 injectables
- [ ] E5.10 Rule 8 `*Adapter` scope extension — see note below

### E6 — Setup on Minestom — BLOCKED
Done when: a map is configurable without Paper.

- [ ] E6.0 **Research: FastAsyncWorldEdit replacement** — blocks everything below, needs review
- [ ] E6.1 Setup server bootstrap
- [ ] E6.2 Map editing tool
- [ ] E6.3 Conversation API verification
- [ ] E6.4 Cross-server Anvil compatibility

### E7 — Cut-over
Done when: the old tree is gone and every module is on Java 25.

- [ ] E7.1 Remove old module tree
- [ ] E7.2 Archive old ADRs, start a fresh series
- [ ] E7.3 Replace `docs/migration/status.md`
- [ ] E7.4 Delete old Flyway migrations
- [ ] E7.5 Final Java-25-everywhere pass
- [ ] E7.6 Cut-over announcement

## Blockers and open questions

| # | Question | Blocks | Needed by |
|---|---|---|---|
| 1 | FastAsyncWorldEdit has no Minestom equivalent | All of E6 | Before E6 is planned |
| 2 | ~~Does `air_drag_modifier` alter the 26.2 elytra drag path?~~ **Answered: no.** See [`docs/reference/elytra-physics-26.2.md`](docs/reference/elytra-physics-26.2.md) | — | Closed 2026-09-09 |
| 3 | Does the CloudNet wrapper propagate node environment variables to every service? | Where DB credentials can live | Before E5 |
| 4 | Which CloudNet RC does the organisation pin? | E4.10 coordinates | Before E4 |
| 5 | Is `voyager-cloudnet-bridge` needed for post-cup lobby routing? | Whether a ninth module exists | During E4 planning |
| 6 | Trace tolerances (`1e-6` per tick, `0.01` over 200 ticks) are assumptions | E2 acceptance | Calibrate on first real traces |
| 7 | Plausibility thresholds must not reject legitimate fast pilots | E4.4 arming | Measure before arming |

## Awaiting a decision

- **E1 execution mode** — subagent-driven or inline. E1 does not start until this is answered.
- **Elytra reconstruction from Paper 26.2** — in progress, see below.

## In progress

- ~~Paper 26.2 decompile~~ — **done**. Verified reference written to
  [`docs/reference/elytra-physics-26.2.md`](docs/reference/elytra-physics-26.2.md). It corrects four
  claims in the existing reference and documents three fidelity traps that would pass a formula
  review and fail a trace comparison.

## Deferred cleanup

- **Style sweep over the tree being replaced.** The project settled two rules during E1 —
  messages built with `String.formatted` rather than concatenation, and exceptions living in their
  own `exception` subpackage. Both are applied throughout `voyager-*`. The old tree has 31
  concatenation sites (`shared/common` 9, `plugins/setup` 8, `server` 6, `plugins/game` 5,
  `shared/database` 2, `shared/conversation-api` 1) and keeps its exceptions beside the code.
  Deliberately deferred: that code is deleted at E7, and a parallel effort is working in `server/`.
  Worth doing only if `server/` turns out to live materially longer than the cut-over plan assumes.

## Notes to resolve

- **E5.10 may be redundant.** It amends `CLAUDE.md` rule 8 to cover row-to-record adapters, but D12
  supersedes that file and E1.5 rewrites it. The extension should simply be part of the E1.5
  rewrite, and E5.10 should then be dropped rather than carried for four stages.
