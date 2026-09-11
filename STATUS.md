# Voyager Rebuild — Status Board

Local tracking for the greenfield rebuild. Nothing is mirrored to GitHub Issues; this file is the
single place where progress is recorded.

- **Design:** [`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`](docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md) — approved
- **Tickets in full:** [`docs/superpowers/specs/2026-09-09-voyager-greenfield-epics.md`](docs/superpowers/specs/2026-09-09-voyager-greenfield-epics.md)
- **E1 implementation plan:** [`docs/superpowers/plans/2026-09-09-e1-foundation.md`](docs/superpowers/plans/2026-09-09-e1-foundation.md)

Last updated: 2026-09-09

## Where we are

E1 is implemented on `feat/greenfield-e1-foundation` and awaiting merge: 24 commits, 36 files,
`./gradlew build` green across both trees. The rebuild's package root is `net.elytrarace.voyager`.
E1.6 (CI wiring for the dual tree) was not part of the executed plan and remains open.

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

### E1 — Foundation — COMPLETE (branch `feat/greenfield-e1-foundation`, not yet merged)
Done when: ArchUnit imports every module and no rule runs empty. **Met, and exceeded:** the coverage test now also proves each module is actually named by a rule, not merely on the classpath.

- [x] E1.1 buildSrc convention plugins
- [x] E1.2 Eight-module Gradle skeleton and settings wiring
- [x] E1.3 `voyager-api` core types
- [x] E1.4 `voyager-fitness` ArchUnit rule suite
- [x] E1.5 `CLAUDE.md` rewrite (D12) — needs approval before merge
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

## Open pull requests — merge order, rehearsed locally 2026-09-10

Every PR below was merged and built in throwaway worktrees outside the repository
(`voyager-int-greenfield`, `voyager-int-deps`, `voyager-int-redcheck`). Nothing was pushed and no
PR was merged on GitHub. The point was to learn what the individual green checkmarks cannot say:
these PRs were each tested against a `main` that did not contain the others.

**Batch 1 — the greenfield stack, in this order.** #236, then #238 (stacked on #236, not on `main`).
Merges clean on `main` @7cca737, full build green, 1019 tests, 0 failures.
Note: #236 has two unpushed local commits — the E2a and E2b plan documents, 2193 lines of Markdown,
no code. They belong to no PR right now.

**Batch 2 — 21 dependency PRs, safe together.**
`110 111 189 198 208 209 210 212 213 215 216 221 222 223 224 227 228 229 230 232 237`
Twelve needed trivial conflict resolution — adjacent lines in `settings.gradle.kts`, which carries
the version catalogue programmatically, so most Renovate PRs pass through that one file. Full build
green on `main`, and green again when merged on top of batch 1 (verified separately: 1019 tests,
Gradle 9.6.0 from #210).

**#198 is not the Minestom risk it looks like.** It moves only the old tree's pin
(2026.04.13 -> 2026.05.11) and never touches the rebuild's `2026.08.28-26.2`.

**Held back, with reasons:**

| PR | Why | What it needs |
|---|---|---|
| #203 + #204 | Adventure 5.x removes `TranslationRegistry` (now `TranslationStore`) and drops `UTF8ResourceBundleControl`. Used by `shared/common`'s `PluginTranslationRegistry` and `LanguageServiceImpl`, and by `plugins/setup/.../ElytraRace.java:52-53`. 18 compile errors. | A migration, or deferral until the old tree is cut. **Never merge #203 alone** — `shared/common` pins `adventure-bom:4.26.1` and declares `adventure-api` without a version, so the BOM decides and #203 looks inert until someone bumps the BOM for an unrelated reason. |
| #214 vs #208 | Both pin `actions/checkout`, to different targets (v4-digest vs v5-digest). | A human picks one. |
| #234 vs #209 | Both pin `actions/setup-java`, v5-digest vs v6-digest. | A human picks one. |
| #231 | `package-lock.json` has a real transitive conflict from the semantic-release major (undici 6->7). | A proper `npm install` regeneration, not a hand splice. |
| #211 | `run-paper` 3.1.0 needs Gradle plugin API 9.7.0; the wrapper is on 9.5.1. **#210 does not fix this** — it only reaches 9.6.0. | Gradle 9.7. |
| #218 | `aonyx-bom` 0.7.3 forces Minestom 2026.05.17, which removed `MinestomAdventure.AUTOMATIC_COMPONENT_TRANSLATION`, used at `server/.../VoyagerServer.java:82`. | Deferral until the cut is cheapest — `voyager-server` is written fresh against 26.2 where the constant is gone anyway. |
| #225 | Shadow 9.5.0 finalises `java.toolchain.languageVersion` during plugin apply, before `server/build.gradle.kts:39` sets it. | An ordering fix, not a version fix: `server` applies shadow in its `plugins` block and sets the toolchain in the script body, which runs later. `buildSrc`'s `voyager.java-conventions` sets the same property from a convention plugin, i.e. before shadow — so the rebuild is *predicted* safe, unverified until `voyager-server` gets its fat JAR. |

**Adventure escaped the version catalogue.** It is hardcoded in `shared/common/build.gradle.kts:8`
and `shared/conversation-api/build.gradle.kts:6-7`, and those two lines pin different versions of
the same library family — `adventure-api:4.26.1` beside `adventure-text-minimessage:4.21.0`.

**The Adventure migration is not a rename.** `PluginTranslationRegistry` deliberately disables the
MessageFormat path, which is why every translation uses MiniMessage `<arg:N>` and why `{N}` renders
as literal text. Whatever replaces it on `TranslationStore` has to re-establish that, or every
placeholder in the project silently degrades to plain text.

**27 tests are disabled in the old tree** — `RingCollisionSystemTest`, `GameOrchestratorTest`,
`OutOfBoundsSystemTest`, `GameEntityFactoryTest`. Pre-existing, not caused by any merge. They cover
exactly the systems the rebuild replaces, so for ring collision, out-of-bounds and the game loop
there is no live test coverage in the old tree to compare the rebuild against. Relevant to E4/E5
planning: "the old tree is the reference" does not hold for those four areas.
