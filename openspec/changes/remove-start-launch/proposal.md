# Proposal

Conventional Commits title: `fix(race): let racers take off themselves at GO and after a reset`

## Why

The rebuild starts every glide for the racer. At the GO tick `Racers.launch` sets the gliding flag and sends a
launch impulse (0.8 blocks per tick forward, 1.2 up) through `VelocityExit`, and a reset relaunches the racer with the
same impulse. The tree being replaced never did this: the racer stood on the spawn with elytra and rockets, jumped off
by themselves and fired a rocket. The server-side impulse overrides the client-authoritative flight that the project
commits to (CLAUDE.md, "Elytra velocity authority"), and the owner has decided it is unnecessary (2026-10-12). The
impulse numbers were never flown; they are seeds.

Removing it restores the old game's take-off and removes one of the three external velocity forces.

## What Changes

- **GO**: the racer is not turned into a glider and is given no velocity. They keep the elytra and rockets they were
  given during the countdown, and the race clock starts on the GO tick as it does today. Flight begins when the client
  deploys the elytra; the server sees it through `isFlyingWithElytra()`, as the greenfield design's flight-detection
  rule already requires.
- **Reset**: a reset places the racer at the reset target, facing the target's flow normal, with elytra and rockets, a
  cancelled burn and a forgotten flight state. It does not set gliding and does not send an impulse. The racer launches
  themselves.
- **Mid-air spawns**: a racer who is not gliding and falls is not a landing (a landing requires a previous glide), so
  nothing is reset; they glide as soon as they deploy the elytra.
- **VelocityExit**: its javadoc and the `ApiPurityTest` reason text stop naming the launch and the out-of-bounds
  reset as velocity sources. See design D6.
- **Greenfield design**: the velocity section (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`,
  about lines 411-416) is corrected. It lists firework boost, which is an entity and not a velocity, and out-of-bounds
  reset, which no longer sends one.
- **Removed**: `Racers.launch(Player, MapDefinition)`, `Racers.launch(Player, Vec3, Vec3)`, `launchVelocity`, and
  `LAUNCH_FORWARD_PER_TICK` / `LAUNCH_UP_PER_TICK` with the tests that pin them.
- **Kept**: `Racers.faceCourse`, `equip`, `hold`, `prepare`, `standDown`, the rocket and firework boost, the reset
  targets and penalties, the race clock, and every map file. No schema change.
- **ADR-0028** records the reversal of the launch decision (MADR, status Proposed until the owner accepts it).

This is a gameplay change for every racer. It is not a change to any public API.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race/waiting-lobby`: the requirement "The start countdown is the first map's lobby" loses the launch on the GO tick.
- `race-course-reset`: the requirement "A reset relaunches the racer with the launch of a map start" is replaced by a
  requirement that a reset returns the racer without an impulse.

## Scope and slices

- Spec text in the `race` slice. Code in the `cup` slice (`CupSession.mapStarted`, `RunResetter`), the `flight` slice
  (`Racers`) in `voyager-platform`, and `VelocityExit` in `platform.convert`.
- Out of scope: the rocket's cooldown and burn, the race-time limit, ring scoring, the boost and slow effects, map files
  and the catalogue, the reset feedback text, and any change to when the clock starts.

## Impact

- Code: `voyager/platform` (`Racers`, `CupSession`, `RunResetter`, `VelocityExit`, `RacersTest`, `RunResetter` tests),
  `voyager/fitness` (`ApiPurityTest` reason text only; no rule is added or removed).
- Docs: greenfield design velocity section; `docs/decisions/0028-*.md`.
- Specs: the two deltas above.
- Gameplay: racers who used to be launched now jump and deploy by themselves. The first-glide time is unknown and
  needs a playtest (tasks.md section 6).
