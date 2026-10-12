# Design

## Context

A cup starts when `VOYAGER_MIN_PLAYERS` human racers are online (`WaitingRoom`, `StartGate`). Every per-racer step of a
cup iterates `ConnectionManager.getOnlinePlayers()` through the `players` supplier that `CupSession` receives, and each
racer is a Minestom `Player`: `FlightTickDriver` samples them, `RaceRuns` keys their runs by `UUID`, `CupRound` records
their maps, `CupAnnouncer` and `Racers` act on the `Player` object. Normal elytra flight is client-authoritative (D5 of the
greenfield design, ADR-0028); the server tracks a velocity but never sets one for a player's flight.

Computer racers need four things the server does not have: a body in the world, a position that the server decides, a
name, and a place in the cup's run and ranking bookkeeping that does not look like a human. The first three are platform
concerns. The fourth is the one that can go wrong quietly: if a bot is a `Player`, it counts toward the start gate, the
join line and the boss bar, and it triggers the permission and hub code that runs on player join. This design keeps bots
out of every `Player` collection and gives the cup a single extra port.

Ring passing already has everything a bot needs. `RingPass.crosses` is the disc test, `ProgressTracker.advance` tests only
the next ring, and `RacingLine` is the domain curve through the ring centres and guide points, computed once per map.

## Goals / Non-Goals

**Goals**

- Computer racers that look and move like racers, and that follow the same racing line humans are shown.
- A pure brain in `voyager-race` that a JUnit test can run for a whole lap with no server, no clock and no randomness
  other than a seed.
- Bots that cannot change any human's score, count, join line, permission or hub handoff.
- `VOYAGER_COM_COUNT=0` (the default) leaves every existing golden transcript byte-identical.

**Non-Goals**

- Elytra physics for bots (no `ElytraSimulator`, no `FlightSample`, no `VelocityExit`).
- Learning or adaptive AI, rubber-banding, or any bot that reads information a human could not (the brain reads only the
  public line, the ring list and its own state).
- Per-cup configuration, bots in the lobby world, a bot command, persistence of bot results.

## Decisions

### D1. Movement: an arc-length follower on the racing line (recommended)

**Recommended.** The brain precomputes the cumulative arc length of `RacingLine.points()` once per map (`ComPath`). Each
game tick it holds one scalar `s` (blocks along the line) and a speed `v` (blocks per tick). It moves `s` forward by
`v`, reads the point and tangent at `s` by binary search, adds a lateral offset `o(s)` perpendicular to the tangent in the
horizontal plane, and sets the body there. `v` moves toward a target speed `v*` at a bounded acceleration. The target is
`v* = factor(difficulty) × personal × referenceSpeed(s)`, where `referenceSpeed` is a constant in this change (see D14).
The lateral offset is `o(s) = A · sin(2π·s / λ + φ) + m`, with amplitude `A`, wobble period `λ`, a seeded phase `φ` and a
seeded miss term `m` (D13).

| | Recommended: arc-length follower | B: steering AI toward the next ring | C: replay of a recorded line |
|---|---|---|---|
| Follows the line humans see | Yes, same `RacingLine` | Approximately, through its own steering | Only the recording |
| Deterministic per seed | Yes, pure function of `(seed, tick)` | Yes, if the steering is pure | Yes |
| Cost per tick | One binary search and one ring test | Steering maths per tick | Array index |
| Works on any map without a recording | Yes | Yes | No, each map needs a file |
| Tuning surface | Four numbers (D3) | Steering gains, harder to reason about | Recording quality |
| Risk | Bot can look too clean on tight turns | Oscillation at guide points | Recording must be redone on every map change |

Contra for the recommendation: the bot follows the line exactly except for the offset, so tight guide-point turns look
like rails. D14 bounds the turn rate to keep that plausible; spike S1 measures it.

### D2. Entity: a plain `Entity` of `EntityType.PLAYER` with a tab-list entry (recommended)

**Recommended.** A computer racer is a Minestom `Entity` built with `new Entity(EntityType.PLAYER, uuid)` (the constructor
exists in Minestom `2026.10.07-26.2`, `Entity.java`), with a `PlayerMeta` for the skin parts and a custom name `COM n`
shown above the head. Each human gets one `PlayerInfoUpdatePacket` (add entry, with the default skin and no textures) for
the tab list before the body is spawned, and a `PlayerInfoRemovePacket` at cup end.

Checked in the sources of `minestom-2026.10.07-26.2-sources.jar`: Minestom has **no** fake player class. There is no
`net.minestom.server.entity.fakeplayer` package. The only player classes are `Player`, `PlayerSkin`, `PlayerHand`,
`PlayerMeta` and `PlayerInputs`.

| | Recommended: plain `Entity` of PLAYER type | B: a `Player` with a stub `PlayerConnection` | C: an armour stand or other entity |
|---|---|---|---|
| Shows as a player, with a name | Yes, through the tab-list entry and the meta | Yes | No, no skin, no tab list |
| Counted by `getOnlinePlayers()` | No | **Yes** (rejected) | No |
| Triggers join events, LuckPerms, hub handoff | No | **Yes** (rejected) | No |
| Animations for elytra pose | Not yet verified (spike S2) | Yes | No |
| Risk | Client may hide a PLAYER entity whose UUID is missing from the tab list. Verified by the spike, not assumed | Violates the owner's rule that bots are not online players | Looks like an armour stand, not a racer |

Option B is rejected on the owner's rule alone. Option C is the fallback if S2 finds that the client will not show a
PLAYER entity without a real connection; the rest of the design does not change, because the body sits behind
`ComBody`.

Skins: the default skin (no textures) for every computer racer. A fetched skin needs the network at runtime and makes
tests non-repeatable (OQ6).

### D3. Difficulty knobs

`ComTuning.of(ComDifficulty)` is a static factory over the enum (`api.race.com.ComDifficulty`, with `VALUES` and
`byName()`, as the enum DSL rule asks). The values below are **starting values to be tuned after playtests**, not measured
constants. Each racer also gets a seeded personal variation of plus or minus 2 per cent on the speed factor, so two racers
at the same level differ.

| Knob | EASY | NORMAL | HARD | Meaning |
|---|---|---|---|---|
| Speed factor | 0.85 | 0.93 | 1.00 | Share of the reference speed D14 |
| Line offset amplitude `A` (blocks) | 1.2 | 0.6 | 0.2 | How far the racer wanders off the line |
| Wobble period `λ` (blocks) | 40 | 60 | 80 | Length of one wander |
| Miss chance per ring | 0.08 | 0.02 | 0.00 | Chance a ring is missed once (D13) |
| Boost reaction delay (ticks) | 10 | 4 | 0 | Ticks before a boost ring's speed effect applies |

The boost delay is inert until the planned `per-map-boost` change adds BOOST and SLOW rings. It is implemented and tested
against a fake ring effect so that the knob is ready when the rings arrive. The effect values themselves are the same
values humans get from `RingEffectRegistry`; a bot never gets a different boost.

Constraints: `A` and the miss chance are non-negative; the speed factor is in `(0, 1.00]`, so no difficulty makes a bot
faster than the reference speed. The factor is capped at 1.00 on purpose: a HARD bot is the reference racer, not a cheat.

### D4. Determinism and time

- The brain takes **game ticks**, not wall time. `ComPilot.step` is a pure function of `(ComState, ComTuning, ComPath,
  tick)` and returns the next state. No `System` or `Instant` call is made in `race.com` (rule F1).
- The seed for a cup round is read from an injected `java.time.Clock` at round start (epoch milliseconds of the round)
  and logged, so a round can be replayed. Tests inject a fixed clock and pass the seed directly to `ComRoster.plan`.
- Each computer racer owns `new Random(seed)` built from `(roundSeed, seatIndex)`. No `Random` without a seed exists in
  the slice (rule F1).
- Wall-clock waits do not exist. Lobby and countdown time is already tick-driven through the cup's phase driver, and
  nothing in this change adds a timer.

### D5. Join policy: fill the grid at cup start, count humans only (recommended)

**Recommended.** When a cup starts (the start gate has committed with at least `VOYAGER_MIN_PLAYERS` humans), the
server adds computer racers until the grid holds `VOYAGER_COM_COUNT` racers in total, humans included. At each later map
start the grid is topped up the same way. A bot is never removed mid-map; a bot is removed when the cup ends.

The start gate, the join line (`x/y`), the boss bar and `VOYAGER_MIN_PLAYERS` read the human `players` supplier only. A
computer racer therefore never starts a cup and never raises a count.

| Option | Pro | Contra |
|---|---|---|
| **1. Fill to N at cup start, humans gate (recommended)** | Simple, deterministic, the grid looks full from the first tick. Gate unchanged | A lone human never starts a race with fewer than the minimum; bots do not help the start |
| 2. Add bots only while humans are below `VOYAGER_MIN_PLAYERS` | Lets a lone human race | The cup still needs the minimum to start, so the bots would only exist when the cup does not run. To make it work the gate must count bots, which contradicts the owner's rule (OQ1) |
| 3. Operator command `/race com <n>` only | Full control, no surprises | Nobody is there to type it on an empty server; no automatic experience |

Option 2 is not implementable without changing the gate. The owner should decide whether the minimum itself is relaxed
for bots (OQ1); this change does not relax it.

Mid-cup joiners wait in the lobby until the next map (the existing rule). When the next map starts, they count as humans
and the grid is topped up; nobody is removed.

### D6. Results and stats: ranked, shown, never scored or stored (recommended)

**Recommended.** Computer racers run the course with a `RaceRun` and a time, and appear in the cup's ranking table as
`COM n`, placed by time among all racers. They receive:

- no cup points and no placement bonus (`CupRound` does not record them, so no human's score or placement changes);
- a medal tier shown in the table as a display value only, never awarded or stored;
- no persisted record. The later persistence change must filter them out; this change adds the flag that the filter
  needs (`ComNames.isComputer(uuid)`), and the persistence change owns the filter.

A computer racer's finish may be announced in chat once, as one line. There are no per-ring messages.

| Option | Pro | Contra |
|---|---|---|
| **1. Ranked and shown, not scored or stored (recommended)** | Bots can never change a human's cup result or stats; the table reads like a race | Bots feel like scenery in the score, and a human can finish behind a bot without it counting |
| 2. Full scoring and placement bonus | Honest race, bots take places | A strong bot takes a human's placement bonus; humans who race bots may feel robbed |
| 3. Not in the results at all | Simplest | Loses the old-game feel the owner asked for |

Stats: bots are never stored in this change. The later Hibernate/PostgreSQL work stores humans only (OQ2).

### D7. Settings: two server settings, global (recommended)

**Recommended.** `VOYAGER_COM_COUNT` and `VOYAGER_COM_DIFFICULTY`, read in `ServerSettings` like the other `VOYAGER_*`
settings and checked by `ConfigCheck` (`server/com-settings`). The value is per deployment, as ADR-0029 chose for the
lobby, so the catalogue format does not change.

| Option | Pro | Contra |
|---|---|---|
| **1. Global settings (recommended)** | No catalogue change, one place to set it, checked at boot | Every cup on the server has the same field size |
| 2. Per cup, in the cup JSON | Each cup can have its own field | A map/cup format change with its own schema, loader and reload path; a later change |
| 3. Per cup, in a separate catalogue file | Editable without a restart | A new file kind for two values |

The maximum of `15` is the cap: with `VOYAGER_MAX_PLAYERS` at its default of 16, one human can join a grid of 16 with 15
computer racers. The cap is a proposal (OQ7).

### D8. Module placement and dependency direction

```
api.race.com      (voyager-api)       ComDifficulty                 pure enum
        ^
race.com          (voyager-race)      ComPilot, ComState, ComTuning, ComPath,
        ^                             ComRoster, exception.*         pure brain
        |
platform.cup      (voyager-platform)  CompetitorField (port), Competitor, CupSession (+ calls),
        ^                             RaceRuns, CupRound                 no reference to platform.com
        |
platform.com      (voyager-platform)  ComField (implements CompetitorField), ComBody,
        ^                             ComTabList, ComNames               the only Minestom bot code
        |
server.config, server.inject (voyager-server)   ServerSettings, ConfigCheck, wiring (the composition root)
```

Read the arrows as "depends on". `race.com` depends on `race.line`, `race.progress` and `race.run` (existing pure slices)
and on `api.race.com`. It does not depend on `platform` or Minestom. `platform.com` depends on `platform.cup` for the
port and on `race.com` for the brain. `platform.cup` depends on the port only, never on `platform.com`, so the cup has
no compile-time path to a Minestom entity. The composition root builds `ComField` when `VOYAGER_COM_COUNT > 0` and binds
the no-op `CompetitorField` otherwise.

The port, in `platform.cup`, uses only domain and JDK types:

```java
public interface CompetitorField {
    /** Adds the grid for this map and returns the ids the cup starts runs for. */
    List<UUID> prepare(int mapIndex, MapDefinition map);
    /** One tick of computer racers, after the human racers, in seat order. */
    List<Competitor> advance(RaceClock clock);
    /** Removes every body and tab-list entry. Called at cup end and on abort. */
    void dismiss();
    static CompetitorField none() { return NoCompetitors.INSTANCE; }
}
```

`Competitor` is a record `(UUID id, Vec3 position, boolean gliding)`. `CupSession` calls `RaceRuns.startFresh` and
`RaceRuns.advance` for each id, which are the public and package-private methods it already owns. `ComField` never touches
`RaceRuns`; it only returns positions and ids.

Where each new type lives:

| Type | Module and package | Why there |
|---|---|---|
| `ComDifficulty` | `voyager-api`, `net.elytrarace.voyager.api.race.com` | A value the settings and the brain both name |
| `ComPilot`, `ComState`, `ComTuning`, `ComPath`, `ComRoster` | `voyager-race`, `net.elytrarace.voyager.race.com` | Pure movement and planning, testable without a server |
| `InvalidComTuningException`, `InvalidComCountException` | `voyager-race`, `race.com.exception` | Domain exceptions with their own `package-info.java` |
| `CompetitorField`, `Competitor`, `NoCompetitors` | `voyager-platform`, `platform.cup` | The port the cup owns |
| `ComField`, `ComBody`, `ComTabList`, `ComNames` | `voyager-platform`, `platform.com` | Minestom entity, packets and names |
| `ServerSettings` additions, `ConfigCheck` additions | `voyager-server`, `server.config` | Settings are read in the composition root |

The `com` slice is added to `architecture/vertical-slices` as a slice of `race` and of `platform`.

### D9. Fitness rules for the new boundary

A new test class `ComBoundaryTest` in `voyager-fitness` holds these rules. Each uses `allowEmptyShould(false)`, and none is
frozen: the new code starts clean, so the rules are not added to `archunit_store`.

| Rule | Statement |
|---|---|
| F1 `comBrainIsPure` | Classes in `net.elytrarace.voyager.race.com..` do not depend on `net.minestom..` or `net.elytrarace.voyager.platform..`, and do not call `System.currentTimeMillis`, `System.nanoTime`, `Instant.now`, `Math.random` or the no-argument `Random` constructor |
| F2 `cupDoesNotDependOnComPlatform` | Classes in `net.elytrarace.voyager.platform.cup..` do not depend on `net.elytrarace.voyager.platform.com..` |
| F3 `comAdapterDoesNotSimulateFlight` | Classes in `net.elytrarace.voyager.platform.com..` and `net.elytrarace.voyager.race.com..` do not depend on `net.elytrarace.voyager.physics..` or `net.elytrarace.voyager.platform.flight..` (client authority; bots never feed the flight tracker) |
| F4 `onlyServerConfigReadsComSettings` | Classes outside `net.elytrarace.voyager.server.config..` and `server.inject..` do not reference `ServerSettings.COM_COUNT_PROPERTY` or `COM_DIFFICULTY_PROPERTY` |
| F5 `comSliceAcyclic` | Already covered by `SliceBoundaryRulesTest` r4 and r7, whose slice patterns match `platform.com` and `race.com` automatically. Recorded here so the review sees it is not a new gap |

F1 and F3 are the rules the owner is most likely to need to see. F2 keeps the cup from ever learning that a bot is a
Minestom entity. The existing `FitnessCoverageTest` needs no change: no module is added, and both modules already have
rules that name their package prefix.

### D10. Performance budget at 20 TPS

The tick is 50 ms. Per computer racer per tick the budget is:

| Step | Budget | Measured by |
|---|---|---|
| `ComPilot.step` (binary search, one ring test, a few vector operations) | 20 µs | Smoke boot tick log, not an assertion |
| `ComBody` move (one `teleport` per tick, one relative move packet per viewer) | 30 µs of server CPU | Smoke boot tick log |
| Total field of 15 | 0.75 ms (1.5 % of a tick) | Smoke boot, with `VOYAGER_COM_COUNT=0` and `15` compared |

Allocation: one `ComState` record and one `Vec3` per racer per tick, about 300 small objects per second for 15 racers.
That is negligible for ZGC (the same argument the greenfield design makes for components).

Memory: `ComPath` is built once per map from the `RacingLine` that the guide renderer already caches, and is shared by
every computer racer on that map. It is not copied per racer.

Network: worst case 15 racers × 20 ticks/s × about 10 bytes per relative move × 16 humans, about 48 KB/s of outgoing
movement. This is the figure the smoke boot checks against the server's own bandwidth log.

The budget is a claim to verify, not a test. The unit tests do not time the brain (that would break F.I.R.S.T.); the
smoke boot records the numbers in the PR description.

### D11. Golden master

- A new golden transcript `voyager/server/src/test/resources/golden/cup-session/com-field.txt` records one cup with
  `VOYAGER_COM_COUNT=3`, NORMAL, a fixed clock and a fixed round seed: each tick's bot positions, progress and finish
  ticks, through the existing `CupSessionGoldenMasterTest` harness.
- With `VOYAGER_COM_COUNT=0` (or with the no-op `CompetitorField`), every existing transcript (`lobby-and-start.txt`,
  `lobby-world-start.txt`, `racing-boost-disconnect.txt`, `restart-pending-reload.txt`, `skip-finish.txt`) must be
  byte-identical. The test for that is the proof that the zero-count path is inert.
- The new transcript is written red first (the file is absent, the test fails), then green.

### D12. Interaction with planned changes

These four changes are not merged. Each one is listed with what it changes for computer racers and which side moves
first.

| Planned change | What it changes | Effect on `com-racers` | Who moves first |
|---|---|---|---|
| `per-map-boost` (BOOST and SLOW rings, rocket config) | Adds ring effects and rocket settings | Bots take the same effect values; the boost delay (D3) becomes live. Rocket config does not affect bots, which have no rockets | This change adds the boost-delay knob with a fake effect. `per-map-boost` provides the real effect values. Either order works; the boost test is skipped until the effect exists |
| `checkpoint-resets` | Adds checkpoint resets for humans | Bots never reset (they follow the line). A bot that loses its way is handled by the watchdog (D13), not by a reset cause. If `checkpoint-resets` adds a reset cause, the watchdog may use it later | `com-racers` does not add a `ResetCause`. The watchdog removes the bot as DNF |
| `full-course-guide-line` | Changes what the line shows humans | Bots use `RacingLine` from the domain, not the renderer. A change to the rendered line does not change bot movement. A change to `RacingLine` itself would change bots, and that is intended: they follow the same line | `com-racers` depends on `RacingLine` as it is today. A change that alters the line's points changes the golden transcript and must update `com-field.txt` in the same PR |
| `lobby-cup-display-and-hub-handoff` | Shows the cup in the lobby and sends racers to a hub after a cup | Bots are not in the lobby (OQ4), are not sent to a hub, and do not appear in the display's player count. The display's standings may list them as `COM n` | `com-racers` adds `ComNames.isComputer` and the exclusion of bots from `players`. The hub change must use the human supplier, which it does already when it reads `players` |

No planned change is required for this one to ship. If `lobby-cup-display-and-hub-handoff` lands first, its hub handoff
must not be given the bot list; if this change lands first, the display must show bots only in the standings table.

### D13. Missed rings and the watchdog

Strict order is the hard constraint: `ProgressTracker` tests only the next ring, so a racer who passes a ring's plane
outside its disc is stuck until they cross that disc. Humans have the same constraint, and direction is not checked, so
a backwards pass through the disc counts.

A missed ring is therefore modelled as a delay, not a skip:

1. When the racer reaches the ring's arc position and the seeded miss roll succeeds, the lateral offset is set to
   `radius + 1.5` blocks on a seeded side, so the plane crossing is outside the disc.
2. The brain then routes the racer back: it moves to a point 6 blocks before the ring on the line and returns through the
   disc with zero offset. `RingPass` counts that crossing, and the run advances as it would for a human.
3. Each missed ring costs time, not progress. The number of ticks the detour takes is a deterministic function of the
   seed.

The watchdog: if a racer's `passedCount` has not moved for 400 ticks (20 s at 20 TPS) while the racer is inside the
course, the racer is removed from the race as DNF. This avoids inventing a reset cause in this change (D12), and it caps
the damage of any bug in the detour to one racer. The watchdog is tested with explicit ticks.

### D14. Vanilla plausibility: the speed and turn bounds

The speed of a computer racer is capped at the reference speed. The reference is taken from the physics reference
(`docs/reference/elytra-physics-26.2.md`): the rocket terminal velocity is `1.7 · lookAngle` (line 492), and the
`chained-boosts` profile records a steady `1.6862` blocks per tick. This change uses a **reference speed of 1.7 blocks
per tick** as the cap, so the target is `factor × personal × 1.7`, and a **floor of 0.8 blocks per tick** that the speed
never falls below, as starting values. With the EASY factor 0.85 the EASY target is about 1.45 blocks per tick.

These bounds are not final. Spike S1 reads the `steady-glide`, `dive-and-pull-out` and `chained-boosts` recordings and
records the measured per-tick horizontal speed and the sink rate. The floor and the cap are then set from the
measurement, and the golden transcript is regenerated in the same PR.

The turn rate is bounded so that yaw changes by at most `Y` degrees per tick and pitch by at most `P` degrees per tick.
`Y` and `P` are set in spike S1 from the same recordings. Until then they are not asserted in tests.

The per-tick vertical step is bounded by the reference's glide descent, the same spike.

### D15. ADR and numbering

`docs/decisions/0035-com-racers.md` is written in MADR 4.0 with status **Proposed**. On `main` the highest existing ADR is
0029, so 0030 to 0034 are free on `main`; the owner's note says 0030 to 0034 are taken on other branches, and 0035 is the
number the owner gave. The ADR is accepted only by the owner. The task that changes its status is in `tasks.md`.

## Risks / Trade-offs

- **The client may refuse a PLAYER entity without a live connection (S2).** Mitigation: `ComBody` hides the choice, and
  the fallback (D2, option C) keeps the rest of the design. The spike runs before any movement code is written.
- **Bots look like rails on tight guide-point turns (D1).** Mitigation: the turn bound (D14) and the seeded wander.
  Measured in S1, judged in playtest.
- **Bots make a lone human's race feel busy or cheap.** This is a design judgement, not a technical one. Pulse (psychology)
  and Drift (game design) review the difficulty table (D3) before NORMAL becomes the default. Neither review has run yet.
- **A missed ring with the strict-order rule could strand a bot (D13).** Mitigation: the detour returns through the disc,
  and the watchdog removes the bot after 20 s. Both are tested with explicit ticks.
- **Bots on the tab list and in the table may confuse the stats later.** Mitigation: `ComNames.isComputer` is the one flag,
  and the persistence change filters on it (D6).
- **More bodies, more packets.** Mitigation: the budget in D10 and the smoke-boot measurement.

## Owner questions

These are decided in this document as recommendations. The owner confirms or changes each one before implementation
starts.

- **OQ1 Join policy.** Recommended: fill to N at cup start, human gate (D5). Option 2 needs the gate to change.
- **OQ2 Results and stats.** Recommended: ranked and shown, never scored or stored (D6).
- **OQ3 Settings scope.** Recommended: global settings now, per cup later (D7).
- **OQ4 Lobby.** Recommended: bots appear only on the grid at cup start, not in the lobby world.
- **OQ5 Difficulty default.** Recommended: NORMAL. Also: is there a per-player difficulty choice? Not in this change.
- **OQ6 Skins.** Recommended: default skin for all. A fetched skin needs the network and breaks Repeatable tests.
- **OQ7 Maximum count.** Recommended: 15, so one human fits a 16-racer grid. Confirm the cap and the default of `0`.
- **OQ8 Tab list.** Recommended: computer racers are listed in the tab list, so the table and the list agree.
- **OQ9 Finish line.** Recommended: one chat line per computer racer's finish, none per ring.
- **OQ10 Psychology and design review.** Pulse and Drift have not reviewed D3 or D5. Recommended: review before NORMAL is the default.
