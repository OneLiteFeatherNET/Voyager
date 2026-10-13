# ADR-0035: Computer racers ("COM n") race the course on the server, follow the racing line, and are never scored or stored

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `com-racers` (OpenSpec). The owner's decisions on OQ1 to OQ10 are recorded in its design.md.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 asked for computer opponents named "COM 1", "COM 2" and so on, as in old video
  games, and for a design before any build.
- Drafted with the change `com-racers`. Its design.md records the owner's decisions on OQ1 to OQ10.

## Context and problem statement

A cup starts only with `VOYAGER_MIN_PLAYERS` human racers online. A server with one or two players has either nothing to
race or an empty course. The owner wants computer racers that fill the grid, race the same course and appear in the
ranking.

Two facts shape the choice. First, normal elytra flight is client-authoritative (ADR-0028 and the greenfield design, D5),
so a computer racer's position cannot come from the client. The server has to decide where it is. Second, every per-racer
step of a cup iterates the online players, and a computer racer built as a Minestom `Player` would be counted as online,
would count toward the start gate and the join line, and would trigger the join path that resolves permissions and the hub
handoff. Minestom `2026.10.07-26.2` has no fake player class (checked in its sources jar), so a Player for a bot would need
a stub connection.

## Decision drivers

- A computer racer must never count as an online player, never start a cup, and never trigger a permission or hub call.
- A computer racer must never change a human's score, placement or stored record.
- The movement must be deterministic for a seed, testable as a pure function, and plausible against the vanilla glide range.
- The zero-count default must leave every existing golden transcript byte-identical.
- The pure brain must have no Minestom import, and the cup must have no compile path to a Minestom entity.

## Considered options

Movement:

1. **Arc-length follower on the racing line (chosen).** The brain moves a scalar distance along the line at a bounded
   speed, with a seeded lateral offset.
2. **Steering AI toward the next ring.** Harder to tune and to keep deterministic.
3. **Replay of a recorded line.** Needs a file per map and must be recorded again when the map changes.

Entity:

1. **A plain `Entity` of `EntityType.PLAYER` with a tab-list entry (chosen).** Shows as a player; not counted as online.
   Client visibility is an owner playtest item.
2. **A `Player` with a stub `PlayerConnection`.** Rejected: counted as online, triggers join events.
3. **An armour stand or another entity.** The fallback if the owner's playtest finds that the client refuses a PLAYER
   entity without a connection (S2).

Results and stats:

1. **Ranked and shown; never scored, placed for points or stored (chosen).**
2. **Full scoring and placement bonus.** Rejected for now: a computer racer would take a human's placement bonus.
3. **Hidden from results.** Rejected: loses the old-game feel.

Join policy:

1. **Fill the grid to the configured count at cup start; the start gate counts humans only (chosen).**
2. **Add computer racers only while humans are below `VOYAGER_MIN_PLAYERS`.** Rejected by the owner (OQ1): it would need the
   gate to count computer racers.
3. **An operator command only.** Not chosen in this change.

## Decision outcome

Chosen options: movement 1, entity 1 (with option 3 as the fallback), results 1, join 1, and settings as two global
server settings, `VOYAGER_COM_COUNT` (0 to 15, default 0) and `VOYAGER_COM_DIFFICULTY` (`EASY`, `MEDIUM`, `HARD`, default
`MEDIUM`).

- The server places each computer racer by arc length along the course's racing line. The line is the one humans see,
  taken from the domain. The brain is pure and lives in `voyager-race` (`race.com`). The Minestom body, packets and names
  live in `voyager-platform` (`platform.com`).
- `platform.cup` owns a port, `CompetitorField`, that the cup calls at map start, once per tick and at cup end. The cup
  never depends on `platform.com`.
- A computer racer is never a `Player`, and never appears in the online-player set that the start gate, the join line,
  the boss bar, the waiting room, LuckPerms and the hub handoff read.
- A missed ring costs time: the racer returns through the same ring's disc. A racer with no progress for 400 ticks is
  removed as did-not-finish.
- Computer racers take no cup points, no placement bonus, no medal and no stored record. They appear in the ranking table
  as `COM n`, with their time. Stats record humans only; a bot is counted only as a bot_count number.
- With `VOYAGER_COM_COUNT=0` the no-op field is wired, and every existing golden transcript stays byte-identical.

Dependency direction: `api.race.com` ← `race.com` ← `platform.cup` (port) ← `platform.com` (implements the port),
with `server.config` and `server.inject` as the only places that read the settings.

## Consequences

- Good: a lone human gets a grid and a course; computer racers cannot change scores or stored data; the pure brain can be
  tested for a whole lap with no server; the zero-count default changes nothing.
- Good: the fitness rules F1 to F4 in `voyager-fitness` name the boundary, so a later change cannot quietly import
  Minestom into the brain, make the cup depend on the body, or feed a computer racer into the flight tracker.
- Bad: the start gate does not change, so a server with one human and a minimum of two still does not race (owner
  decision OQ1).
- Bad: the difficulty values are starting points. The owner's playtest tunes them; MEDIUM is the default until then.
- Bad: one more adapter to keep in step with Minestom's entity and packet API, and a fallback path if the owner playtest finds the body hidden (S2).
- Bad: bots in the tab list and the results table mean a later persistence change must filter them out with one flag
  (`ComNames.isComputer`). The filter is that change's job, not this one's.
- Neutral: the speed and turn bounds (cap 1.7 blocks per tick, floor 0.8) are taken from the physics reference's glide
  figures. Spike 0.3 replaces them with measured values before the implementation is merged.

## Confirmation

- `ComPilotTest`, `ComPathTest`, `ComTuningTest` and `ComRosterTest` cover the brain, including a whole lap with no server.
- `ComBodyTest`, `ComTabListTest` and `ComFieldTest` cover the adapter on a fresh Minestom instance with explicit ticks.
- `CupSessionCompetitorTest` covers the port calls, and `CupSessionComGoldenTest` replays a committed transcript
  `com-field.txt`. The existing five golden transcripts are byte-identical.
- `ComBoundaryTest` in `voyager-fitness` holds rules F1 to F4 with `allowEmptyShould(false)` and no freeze.
- The owner's playtest (design.md, "Owner playtest") confirms the body is visible (S2) and the tab entries are clean after
  the cup. The smoke boot in task 10.1 covers boot only.

## Pros and cons of the options

### Movement 1, arc-length follower (chosen)

- Good: follows the same line humans see, deterministic, cheap, works on any map.
- Bad: tight guide-point turns can look like rails, so the turn bound and the seeded wander are needed.

### Movement 2, steering AI

- Good: looks more like a real racer on tight turns.
- Bad: harder to tune, and gains are a source of oscillation.

### Movement 3, replay

- Good: exact to a recording.
- Bad: a recording per map, redone on every change.

### Entity 1, plain entity of PLAYER type (chosen)

- Good: a name, a skin slot and a tab-list entry, with no connection and no online count.
- Bad: the client's handling of a PLAYER entity without a connection is not verified by a test. It is an owner playtest
  item (S2, design.md D2); the armour stand is the fallback.

### Entity 2, a Player with a stub connection

- Good: the most faithful player.
- Bad: counted by the online set, and triggers join events, permissions and the hub. This breaks the owner's rule.

## More information

- `openspec/changes/com-racers/`: the proposal, design (D1 to D15 and the owner questions), delta specs and tasks.
- ADR-0028: racers take off themselves; the server gives no launch impulse. Computer racers follow the same rule: the
  server does not simulate their flight.
- ADR-0029: the lobby world. The computer racer count follows the same pattern: global server settings, no catalogue change.
- ADR-0022 and ADR-0029 are the nearest prior records for the lobby and join line.
- The planned changes `per-map-boost`, `checkpoint-resets`, `full-course-guide-line` and `lobby-cup-display-and-hub-handoff`
  are merged on `main`. design.md D12 says what each means for computer racers.
- Numbering: on `main` the highest ADR is 0029, and this record takes 0035 as the owner asked.
