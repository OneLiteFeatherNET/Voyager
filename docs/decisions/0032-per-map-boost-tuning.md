# ADR-0032: Boost and ring-effect tuning lives per map in the map file

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `per-map-boost` (OpenSpec). Status moves to
Accepted only on the owner's word.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided that boost is configurable per map and that the BOOST and SLOW ring
  effects must work.
- Drafted with the change `per-map-boost`, after reading `RingEffectRegistry`, `Racers`, `Rockets`, `FireworkBoostTracker`,
  `VelocityExit`, `FlightTickDriver` and the shipped map file.

## Context and problem statement

Three parts of the boost are in the map file or half in the code, and one is missing:

- The burn and cooldown are per map already (`boostConfig`).
- The BOOST and SLOW ring effects are not connected. `RingEffectRegistry` is used only by its tests, and its multipliers
  (1.5 and 0.5) are code constants.
- Every racer carries 64 rockets that are never used up, and the rocket flight duration is a hard-coded 3.
- The legacy tree capped the boost speed per map (`maxSpeedBlocksPerTick`, default 3.5). The rebuild has no cap, and its
  rockets are applied by the client, so a server-side cap has to decide what it can actually limit.

The greenfield design spec (row (c), lines 818-828) calls the boost cooldown "source, not configuration", meaning a
game-wide value in git and no operator override. Per-map tuning changes what that row means.

## Decision drivers

- Race fairness: a rocket or a ring must be worth the same on every run of the same map.
- Client authority: normal elytra flight belongs to the client. The server writes velocity only for external forces
  (`VelocityExit`), and the list of those forces should stay short.
- Data, not code: a designer changes a number in the map file without a compiler.
- Compatibility: a map file without the new fields must play as before, and the schema-version rule must keep an old
  server from misreading a new file.

## Considered options

1. Keep the constants in code and document them.
2. Per-map values in the map file, optional, with today's constants as defaults (chosen).
3. Operator environment overrides for the same values.
4. Push rocket impulses from the server on every burn tick, as a fourth external force.

## Decision outcome

Chosen: option 2.

- The map file gains optional `boostConfig.rocketsPerRacer`, optional `boostConfig.maxSpeedBlocksPerTick`, and optional
  `ringEffects` with `boostMultiplier` and `slowMultiplier`. Defaults are today's values, 64 rockets, no cap, 1.5 and 0.5.
  Burn and cooldown stay required.
- A ring's effect is applied on the tick the racer crosses it, to the server's simulation and to the client, through
  `VelocityExit`. Effects in quick succession compose in crossing order and are written once per tick.
- The speed cap limits only the velocity a ring effect writes. It never corrects normal flight or rockets.
- Rocket impulse strength stays Vanilla's and is not per map. The item's flight duration is derived from the burn.
- Operators get no environment override for any of these values.

### Amendment to the greenfield design spec (pending owner acceptance)

Row (c) and the paragraph "(c) is source, not configuration" are amended as follows: boost burn, cooldown, rocket count,
speed cap and ring factors move to row (d), map and cup definitions, which is per-map content. Ring points, medal
brackets and plausibility tolerances stay in (c). The rule that these values live in git and are not overridden by
operators stays. The "game-wide" wording goes, because a cooldown can now differ between maps.

### Consequences

- Good: a designer tunes a map by editing its file, and the file says what the map plays with.
- Good: the speed cap cannot cause the rubber-banding a hard cap would cause, because it acts only on server-written
  velocity.
- Good: no new external velocity force; `VelocityExit` keeps its two.
- Bad: the cap does not limit rockets. The natural limit of a single rocket is the fixed point of Vanilla's impulse
  (about 1.7 blocks per tick), so the cap's real effect is on ring stacking. A hard cap on measured speed would need its
  own decision after measurements.
- Bad: the shipped map's BOOST rings start to change speed. Its lap records were set without them, so the owner decides
  whether to reset them.
- Bad: an older server reads new fields with no error and ignores them, so a map using the new fields must not be
  deployed to a server that predates this change. The deployment order is the mitigation.
- Neutral: the record `RingEffectRegistry` gets a per-map factory. Its javadoc said the mapping was fixed because it had no
  caller that needed another value. The per-map caller now exists.

### Confirmation

- Red tests for the format, the registry, the cap, the rocket count and the cup's ring step, in the modules named by the
  change's tasks.
- Playtest of the shipped map's BOOST rings, with the maximum shadow speed read from the map-start log, before any hard cap
  is considered.
- `voyager-fitness` passes unchanged, which shows the module boundaries still hold.

## Pros and cons of the options

### 1. Keep the constants in code

- Good: nothing changes, no format work.
- Bad: the cooldown and factors cannot differ between maps, which the owner has ruled out. The ring effects stay
  unconnected unless wiring is added anyway.

### 2. Per-map values in the map file (chosen)

- Good: fits the existing `boostConfig` pattern, reviewed in git, per map.
- Bad: adds fields to a format that already has a version rule.

### 3. Operator environment overrides

- Good: runtime changes without a deploy.
- Bad: the same map plays differently on two servers, and records stop being comparable. Ruled out by the design spec.

### 4. Server push for rocket impulses

- Good: per-map strength would be possible.
- Bad: a fourth external velocity force, a server override of client-authoritative flight on every burn tick, and a
  rubber-banding risk. Deferred until a playtest shows the Vanilla impulse is wrong for a course.

## More information

- OpenSpec change: `openspec/changes/per-map-boost/` (proposal, design D1 to D10, tasks, delta specs).
- Related: ADR-0028 (racers take off without a server impulse), the velocity-authority section of `CLAUDE.md`,
  `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` lines 818-828.
- Reference page for the new fields: to be written under `docs/reference/` by the change's documentation task.
- ADR-0027 is reserved by the unmerged `oval-tilted-rings` change. ADR-0030 and ADR-0031 are not used here.
