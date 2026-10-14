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

### D2. Entity: a plain `Entity` of `EntityType.PLAYER` with a tab-list entry (primary, decided; armour stand as fallback)

**Primary approach, as built.** A computer racer is a Minestom `Entity` built with `new Entity(EntityType.PLAYER, uuid)` (the constructor
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
| Animations for elytra pose | Not verified; owner playtest item S2 | Yes | No |
| Risk | Client may hide a PLAYER entity whose UUID is missing from the tab list. Checked by the owner in playtest (S2), not by a test | Violates the owner's rule that bots are not online players | Looks like an armour stand, not a racer |

Option B is rejected on the owner's rule alone. Option C is the fallback if the owner's playtest finds that the client
hides a PLAYER entity without a real connection; the rest of the design does not change, because the body sits behind
`ComBody`.

**Owner playtest item S2 (decided).** Whether a PLAYER-type entity without a live connection is shown by the client
cannot be tested without a real client. No spike test is written for it. The primary approach above is implemented. The
owner checks it in playtest (see "Owner playtest" below). If the body is hidden, the body switches to option C in the
same change before it ships.

Skins: the default skin (no textures) for every computer racer. A fetched skin needs the network at runtime and makes
tests non-repeatable (OQ6).

### D3. Difficulty knobs

The default difficulty is MEDIUM (OQ5). There is no per-player difficulty choice in this change.

`ComTuning.of(ComDifficulty)` is a static factory over the enum (`api.race.com.ComDifficulty`, with `VALUES` and
`byName()`, as the enum DSL rule asks). The values below are **starting values to be tuned after playtests**, not measured
constants. Each racer also gets a seeded personal variation of plus or minus 2 per cent on the speed factor, so two racers
at the same level differ.

| Knob | EASY | MEDIUM | HARD | Meaning |
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

### D5. Join policy: fill the grid at cup start, count humans only (decided, OQ1)

**Decided.** When a cup starts (the start gate has committed with at least `VOYAGER_MIN_PLAYERS` humans), the
server adds computer racers until the grid holds `VOYAGER_COM_COUNT` racers in total, humans included. At each later map
start the grid is topped up the same way. A bot is never removed mid-map; a bot is removed when the cup ends.

The start gate, the join line (`x/y`), the boss bar and `VOYAGER_MIN_PLAYERS` read the human `players` supplier only. A
computer racer therefore never starts a cup and never raises a count.

| Option | Pro | Contra |
|---|---|---|
| **1. Fill to N at cup start, humans gate (decided)** | Simple, deterministic, the grid looks full from the first tick. Gate unchanged | A lone human never starts a race with fewer than the minimum; bots do not help the start |
| 2. Add bots only while humans are below `VOYAGER_MIN_PLAYERS` | Lets a lone human race | The cup still needs the minimum to start, so the bots would only exist when the cup does not run. To make it work the gate must count bots, which contradicts the owner's rule (OQ1) |
| 3. Operator command `/race com <n>` only | Full control, no surprises | Nobody is there to type it on an empty server; no automatic experience |

Option 2 is rejected by the owner (OQ1): it would need a gate change, and there is none. Option 3 is not chosen in this
change.

Mid-cup joiners wait in the lobby until the next map (the existing rule). When the next map starts, they count as humans
and the grid is topped up; nobody is removed.

### D6. Results and stats: ranked, shown, never scored or stored (decided, OQ2)

**Decided.** Computer racers run the course with a `RaceRun` and a time, and appear in the cup's ranking table as
`COM n`, placed by time among all racers. They receive:

- no cup points and no placement bonus (`CupRound` does not record them, so no human's score or placement changes);
- no medal;
- no persisted record. The later persistence change must filter them out; this change adds the flag that the filter
  needs (`ComNames.isComputer(uuid)`), and the persistence change owns the filter.

Each computer racer finish is announced in chat as one line to every human racer, in both `de_DE` and `en_US` (German
with the informal "du"). There are no per-ring messages (OQ9).

| Option | Pro | Contra |
|---|---|---|
| **1. Ranked and shown, not scored or stored (decided)** | Bots can never change a human's cup result or stats; the table reads like a race | Bots feel like scenery in the score, and a human can finish behind a bot without it counting |
| 2. Full scoring and placement bonus | Honest race, bots take places | A strong bot takes a human's placement bonus; humans who race bots may feel robbed |
| 3. Not in the results at all | Simplest | Loses the old-game feel the owner asked for |

Stats: bots are never stored in this change. Stats work (`player-stats`, in progress in parallel) records humans only; a
bot is counted only as a bot_count number, never as a player record.

### D7. Settings: two server settings, global (decided, OQ3)

**Decided.** `VOYAGER_COM_COUNT` and `VOYAGER_COM_DIFFICULTY`, read in `ServerSettings` through the avaje-config
`ConfigLoader` (the one in `platform.config` and the server composition root, never raw `System.getProperty`) and checked
by `ConfigCheck` (`server/com-settings`). Per-cup settings are a later change. The value is per deployment, as ADR-0029 chose for the
lobby, so the catalogue format does not change.

| Option | Pro | Contra |
|---|---|---|
| **1. Global settings (decided)** | No catalogue change, one place to set it, checked at boot | Every cup on the server has the same field size |
| 2. Per cup, in the cup JSON | Each cup can have its own field | A map/cup format change with its own schema, loader and reload path; a later change |
| 3. Per cup, in a separate catalogue file | Editable without a restart | A new file kind for two values |

The maximum of `15` is the cap, confirmed by the owner (OQ7): with `VOYAGER_MAX_PLAYERS` at its default of 16, one human
can join a grid of 16 with 15 computer racers. The default is `0`.

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
  `VOYAGER_COM_COUNT=3`, MEDIUM, a fixed clock and a fixed round seed: each tick's bot positions, progress and finish
  ticks, through the existing `CupSessionGoldenMasterTest` harness.
- With `VOYAGER_COM_COUNT=0` (or with the no-op `CompetitorField`), every existing transcript (`lobby-and-start.txt`,
  `lobby-world-start.txt`, `racing-boost-disconnect.txt`, `restart-pending-reload.txt`, `skip-finish.txt`) must be
  byte-identical. The test for that is the proof that the zero-count path is inert.
- The new transcript is written red first (the file is absent, the test fails), then green.

### D12. Interaction with planned changes

All four planned changes are merged on `main`. Each row says what it is on `main` and what it means for computer racers.

| Planned change (merged) | What it is on `main` | Effect on `com-racers` |
|---|---|---|
| `per-map-boost` (`99de1c0`, archive `2157cf9`) | BOOST and SLOW ring effects per map, through `RingEffectRegistry` | Bots take the same effect values. The boost delay (D3) is wired to the real effect; the unit test still uses a fake ring effect |
| `checkpoint-resets` (`1c2828c`) | Human resets to the last checkpoint with a deployed elytra (`RunResetter`) | Bots never reset; they follow the line. A bot that loses its way is removed as DNF by the watchdog (D13). `com-racers` adds no `ResetCause` |
| `full-course-guide-line` (`35bcdb5`) | The whole course is drawn as the racing line | Bots use the domain `RacingLine`, as designed, not the renderer. A change to the line's points changes bot movement and the golden transcript `com-field.txt` in the same PR |
| `lobby-cup-display-and-hub-handoff` (`f000b6b`, `c28df3d`, archive `6abaa2f`) | `PlayerHandoff` is an API interface in `voyager/api/.../handoff/`, installed by `CloudNetPlayerHandoff` in the cloudnet bridge (`VoyagerCloudNetExtension`). `HubHandoff.send(List<Player>)` is called from `WaitingRoom.handOff`, with the human racers it holds | Bots never get the hub handoff. The exclusion goes in the list `WaitingRoom.handOff` builds (the `TODO(com-bots)` at `WaitingRoom.java:254`, which this change resolves). Bots are not in the lobby (OQ4), so they do not affect the display's player count; the standings may list them as `COM n` |

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

The speed of a computer racer is capped at the reference speed, and never falls below the floor. The target is
`factor × personal × cap`. Spike S1 (tasks 0.3) measured every value below from the recordings in
`voyager/physics/src/test/resources/traces/` (tick indices are the `index` field; the `ticks` array starts at line 9 of
each file). The physics reference (`docs/reference/elytra-physics-26.2.md`) gives the rocket terminal velocity
`1.7 · lookAngle` at line 492 and the steady boosted speed `1.6862` at line 495.

| Value | Unit | Decided | Measured | Source (file:line or tick) |
|---|---|---|---|---|
| Cap (reference speed) | blocks per tick | **1.7**, unchanged | Boosted plateau `1.68615` (peak, `chained-boosts`), `1.67161` (`single-boost`) | `chained-boosts.json` tick 45; `single-boost.json` tick 51; terminal `docs/reference/elytra-physics-26.2.md:492`; code `voyager/physics/.../ElytraSimulator.java:284-285` |
| Floor | blocks per tick | **0.70**, was 0.8 | Glide at pitch -5 plateaus at `0.7148` (ticks 150-199 p50 `0.7086`; `0.8` is never reached) | `steady-glide.json` ticks 150-199 |
| Yaw bound `Y` | degrees per tick | **0.91** | `0.9091` on every turning tick (99 of 200 ticks) | `sustained-turn.json` ticks 104, 123, 125 |
| Pitch bound `P` | degrees per tick | **7.23** | `7.2222` on the climb onset (9 ticks, peak tick 45); `5.0` on dive and pull-out ramps | `climb-into-stall.json` ticks 42-48; `dive-and-pull-out.json` ticks 17-19 |
| Vertical sink bound | blocks per tick, downward | **0.134** | `0.1337` maximum on steady glide | `steady-glide.json` tick 22; `sustained-turn.json` tick 22 |

The floor moved from 0.8 to 0.70: a steady glide never reaches 0.8, so a floor of 0.8 would push a gliding racer faster
than the vanilla glide. The floor changed, so the golden transcript is regenerated in the same PR. The cap is unchanged,
so the EASY target stays about `0.85 × 1.7 = 1.45` blocks per tick.

Two measurements are outside these bounds and are not modelled. A diving human reaches `2.35` blocks per tick without a
rocket (`dive-and-pull-out.json` tick 122; `pitch-extremes.json` peak `2.33`), and the pull-out sinks `2.11` blocks per
tick (`dive-and-pull-out.json` tick 100). A racer on the racing line does not dive, so the cap and the sink bound stay
as above. Owner check: confirm that a racer may not match a human dive.

The turn bound limits yaw to `Y` and pitch to `P` per tick. The vertical step is limited to `0.134` blocks per tick
downward. None of these is asserted in a test yet.

### D15. ADR and numbering

`docs/decisions/0035-com-racers.md` is written in MADR 4.0 with status **Proposed**. On `main` the highest existing ADR is
0029, so 0030 to 0034 are free on `main`; the owner's note says 0030 to 0034 are taken on other branches, and 0035 is the
number the owner gave. The ADR is accepted only by the owner. The task that changes its status is in `tasks.md`.

## Risks / Trade-offs

- **The client may refuse a PLAYER entity without a live connection (S2).** Owner playtest item (D2). Mitigation: `ComBody`
  hides the choice, and the fallback (D2, option C) keeps the rest of the design. No spike test exists for it; the owner's
  playtest decides.
- **Bots look like rails on tight guide-point turns (D1).** Mitigation: the turn bound (D14) and the seeded wander.
  Measured in S1, judged in playtest.
- **Bots make a lone human's race feel busy or cheap.** This is a design judgement, not a technical one. The owner's
  playtest replaces the Pulse (psychology) and Drift (game design) reviews (OQ10). The difficulty table (D3) is tuned from
  that playtest; MEDIUM is the default until then.
- **A missed ring with the strict-order rule could strand a bot (D13).** Mitigation: the detour returns through the disc,
  and the watchdog removes the bot after 20 s. Both are tested with explicit ticks.
- **Bots on the tab list and in the table may confuse the stats later.** Mitigation: `ComNames.isComputer` is the one flag,
  and the persistence change filters on it (D6).
- **More bodies, more packets.** Mitigation: the budget in D10 and the smoke-boot measurement.

## Owner decisions

The project owner decided the ten questions. Each decision is binding for the implementation and is reflected in the
sections above.

- **OQ1 Join policy.** When a cup starts (the start gate committed with at least `VOYAGER_MIN_PLAYERS` humans), fill the
  grid with computer racers up to `VOYAGER_COM_COUNT`. Only then. The start gate, the join line (`x/y`) and the boss bar
  count humans only. Top the grid up at each map start. Never remove a computer racer mid-map. Remove all of them at cup
  end. Computer racers never take the hub handoff (`PlayerHandoff`) and never touch LuckPerms. Option 2 is rejected; no
  gate change.
- **OQ2 Results.** Computer racers are ranked and shown in the results as "COM n". They get no points, no placement bonus
  and no medals. They are never stored in stats. Stats work (`player-stats`, in progress in parallel) records humans only;
  a bot is counted only as a bot_count number, never as a player record.
- **OQ3 Settings.** Global. `VOYAGER_COM_COUNT` (integer, default `0`, range `0` to `15`) and `VOYAGER_COM_DIFFICULTY`
  (`EASY`, `MEDIUM` or `HARD`, default `MEDIUM`). Both are read through the avaje-config `ConfigLoader` (the one in
  `platform.config` and the server composition root), never raw `System.getProperty`. Per-cup settings are a later change.
- **Difficulty names.** The owner's names are EASY, MEDIUM and HARD. NORMAL is renamed to MEDIUM everywhere in these
  artifacts. The default is MEDIUM. The numbers in the D3 table are unchanged for now.
- **OQ4 Lobby.** Computer racers appear only on the grid, never in the lobby world.
- **OQ5 Difficulty.** Default MEDIUM. No per-player difficulty choice in this change.
- **OQ6 Skins.** Default skin for every computer racer. No network fetch.
- **OQ7 Maximum count.** 15, confirmed. Default `0`.
- **OQ8 Tab list.** Computer racers are listed in the tab list.
- **OQ9 Finish.** One chat line per computer racer finish, in both `de_DE` and `en_US` (German with the informal "du").
  No per-ring messages.
- **OQ10 Reviews.** The Pulse (psychology) and Drift (game design) reviews are replaced by the owner's playtest.

## Owner playtest

These checks replace the real-client check that task 10.1 used to name. The owner runs them on the playtest server with
`VOYAGER_COM_COUNT` set above zero. They are not automated tests.

- **S2, body visibility (D2).** A PLAYER-type entity without a live connection is shown by the client. If it is hidden,
  switch the body to the armour stand (D2, option C) before shipping.
- Computer racers appear on the grid at cup start and not before, and not in the lobby world.
- Each one has the name `COM n` above its head and a tab-list entry (OQ8).
- They move along the racing line and look plausible on tight turns (D1, D14).
- The results table lists them by time at the finish, with no points and no medal (OQ2).
- One chat line per computer racer finish, in German and English (OQ9).
- They are removed at cup end, and the tab list is clean afterwards.
- The difficulty numbers in D3 feel right for EASY, MEDIUM and HARD. Tune them from this playtest (OQ10).
