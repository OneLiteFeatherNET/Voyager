# Design: per-map boost and ring effects

Status: planning only. No production code is changed by this change until the owner accepts ADR-0032 and the open
questions at the end. Every number below is a proposal to be checked by a Red test, not a measurement.

## Why one change

The four parts (a) ring effects, (b) rockets, (c) documented burn and cooldown, (d) speed cap share one format step: the
`boostConfig` and `ringEffects` records, their adapters, the writer and `map.schema.json`. Splitting would change that
format twice. The types agree: all behaviour is `feat(race)`, and the documentation is `docs`, which is a separate commit
under the same change, as the lobby change does.

Split option if the owner wants a smaller first PR: `per-map-ring-effects` (a and d, both touch velocity through the
ring-crossing step) and `per-map-rockets` (b, touches `Racers` and `Rockets` only, and depends on the format step from
the first). Part (c) is documentation and goes with whichever PR lands first. The split is not taken here.

## Current state (verified in the tree at origin/main 6635220)

- `RingEffectRegistry.DefaultRingEffectRegistry` hard-codes `BOOST_MULTIPLIER = 1.5` and `SLOW_MULTIPLIER = 0.5`. Its
  only callers are its own tests. Nothing in `voyager-platform` reads an effect.
- `Racers.ROCKETS = 64` is handed out by `equip` and never spent. `Rockets.FLIGHT_DURATION = 3` is written into the item.
- `Rockets.fire` spawns a real firework entity whose burn is removed after `burnDurationTicks` (server-counted). The
  impulse itself is Vanilla's, applied by the client to its own player. The server sends no velocity for it.
- `FireworkBoostTracker` holds burn and cooldown per player. `CupSession.requestBoost` reads the map's `BoostConfig`.
- `FlightTickDriver` holds the server's shadow velocity per player (`FlightTracker`). Nothing in the platform writes it
  except the flight step itself. `VelocityExit.send` is the only door to `Player#setVelocity`, and it is called with zero
  from `Racers.standDown` and `RunResetter`.
- Tick order (`CupBeans.tickPipeline`): flight sample, then boost burn advance, then phase advance. Ring crossings happen
  inside the phase step (`CupSession.enterMap` loop, `runs.advance`, then `run.justPassed()` for announcements).
- `map.schema.json` caps `schemaVersion` at 1 and sets `additionalProperties: false` at the root and on rings, but not on
  `boostConfig`. `MapDefinitionAdapter.boostConfig` requires both fields on purpose.
- `RingDefaults.BOOST = new BoostConfig(30, 40)` is the setup default for a new draft.

## Decisions

### D1. Format: new fields are optional, additive, and work in v1 and v2

The two required fields stay required. New fields, all optional:

```json
"boostConfig": {
  "burnDurationTicks": 30,
  "cooldownTicks": 40,
  "rocketsPerRacer": 64,
  "maxSpeedBlocksPerTick": 3.5
},
"ringEffects": {
  "boostMultiplier": 1.5,
  "slowMultiplier": 0.5
}
```

| Field | Type and bounds | Absent means |
|---|---|---|
| `boostConfig.rocketsPerRacer` | integer 1 to 64 (the item stack limit) | 64 |
| `boostConfig.maxSpeedBlocksPerTick` | finite number greater than 0 | no cap |
| `ringEffects` (object) | optional | both factors below |
| `ringEffects.boostMultiplier` | finite number, 0 or more | 1.5 |
| `ringEffects.slowMultiplier` | finite number, 0 or more | 0.5 |

Defaults equal today's constants and today's count, so a file without the fields plays exactly as before for everything
except the ring effects. The ring effects were not connected before, so the default values turn BOOST and SLOW on for
any map that lists them. That is the owner's decision (a), not a regression, and the proposal says so.

Version handling: this change does not bump `schemaVersion`. The loader reads the new fields in files declaring version
1 (or none) and in version 2. The `add-oval-and-tilted-rings` change introduces version 2 and must rebase onto this
change's `map.schema.json`. Whichever merges second rebases.

Risk recorded in ADR-0032: an older server ignores unknown fields inside `boostConfig` (Gson), so it would load a file
with a cap or a multiplier and silently play it without them. The mitigation is deployment order: the server that reads
the new fields is deployed before a map file that uses them is committed. An alternative, refusing a version 1 file that
carries the new fields, is listed as an open question.

`MapDefinitionAdapter`, `MapDraftAdapter` and `MapDraftJsonWriter` read and write the new fields. The writer emits a
field only when it differs from its default, so existing committed files do not change on a save.

### D2. A per-map registry, not a fixed table

`RingEffectRegistry`'s javadoc says there is no `register` and the mapping is fixed. That was right when there was one
caller and one set of constants. Now the multipliers come from a map, so the registry is built per map:
`RingEffectRegistry.create(RingEffectTuning)`, where `RingEffectTuning(boostMultiplier, slowMultiplier)` is a record in
`voyager-api` with a `DEFAULT`. `create()` with no argument stays and returns `create(RingEffectTuning.DEFAULT)`, so
existing tests still compile. The "no register" rule is reopened with its caller in hand, as the javadoc asks. Design
rule 3 (one `final Default*` backed by an immutable map) still holds.

`SpeedMultiplierEffect` keeps its existing checks (finite, not negative).

### D3. Where a ring effect is applied in the tick

The effect is applied in the phase step, in `CupSession`, on the tick a racer crosses a ring, right after `runs.advance`:

1. The racer must be gliding (`isFlyingWithElytra()`), hold an unfinished run both before and after the tick, and not be
   reset on this tick (`sendBackIfLostOrLanded` returns a different run). A reset cancels the effect of that tick.
2. For each ring newly passed on this tick, in ring order, multiply the velocity by that ring's factor. The result is
   the velocity the racer leaves the ring with.
3. Apply the speed cap (D6) to that result.
4. Write the result to the shadow simulation through a new seam on `FlightTickDriver` and `FlightTracker`
   (`scaleVelocity(UUID, Vec3)`, package-private to `platform.flight`), and send it to the client through
   `VelocityExit.send`. Both writes happen on the same tick, so the shadow and the client agree on the next tick, up to
   the network delay below.

The seam is needed. Without it the shadow keeps the unboosted velocity and the next boost, cap or sample is computed
from the wrong state. `MinestomVelocityAutoSyncTest` already shows that a velocity given to Minestom decays on the
server's copy, so the shadow is the only place the effect persists in the server's own model.

### D4. Stacking: BOOST then SLOW in quick succession

Effects compose multiplicatively in the order the rings were crossed, each applied to the velocity the previous one left.
A BOOST at 1.5 followed by a SLOW at 0.5 leaves 0.75 times the original. Two BOOST rings in one tick give 2.25 times,
capped by D6 if the cap is set. Several rings crossed on one tick produce one `VelocityExit` write, not one per ring.
Several rings on one tick are possible only if rings overlap in time, so the coalescing is a correctness rule for that
case, not a performance one.

### D5. Rockets: count per map, flight duration derived, strength not per map

- **Count.** `Racers.equip` takes the count from the map's `rocketsPerRacer`. A reset gives the stack again, as today.
  The count is read at the map's start; `Racers.faceCourse` already receives the map.
- **Flight duration.** The item's firework flight duration is derived from the map's `burnDurationTicks` as
  `clamp(round(burnDurationTicks / 10), 1, 3)`, so the thirty-tick burn shows 3, as now. Vanilla's flight duration sets
  only the rocket's lifetime, and the server already overrides lifetime with the burn. The per-map "flight duration" the
  owner asked for is therefore the burn itself, which already exists per map. `RocketsTest` assertion about
  `10 * item duration = burn` is rewritten to the derived rule.
- **Strength: not per map in this change.** The impulse is Vanilla's formula applied by the client. Making it per map
  would need the server to push an impulse on each burn tick, which is a fourth external force through `VelocityExit`.
  That contradicts the direction of ADR-0028 and the velocity-authority section in CLAUDE.md. A later change can add it
  if a playtest shows the Vanilla impulse is wrong for a course, and this change leaves room for it (a `rocketImpulse`
  field would scale the server's push, not the client's).

### D6. Speed cap: what it limits, and why not measured flight speed

Options:

- **A. Hard cap on measured speed.** On each tick where the server's shadow speed exceeds the cap, send a clamped velocity
  to the client. Rejected for now. The shadow lags the client by about half a round trip (`LivePlayerSampler` says so), so
  the cap acts on a speed the racer no longer has. The client then re-accelerates, and the server corrects again. The
  result is a saw-tooth speed at the cap and visible stutter, which is rubber-banding. It also makes the server override
  normal flight, which the velocity-authority rule forbids. The dive speed of Vanilla elytra flight has not been measured
  against 3.5 blocks per tick, so a cap at that value could fire on legitimate flight.
- **B. The cap clamps server-applied velocity only.** Chosen. The cap applies to the velocity a ring effect writes (D3
  step 3), and nothing else. It cannot change normal flight, because the server writes nothing there. Its effect is
  deterministic and testable with explicit ticks. Limit: it does not limit rockets, which the client applies. The
  rocket's own ceiling is the fixed point of Vanilla's impulse, `v = 1.7 * look` (the formula in `ElytraSimulator`
  settles at about 1.67 blocks per tick for a single boost), so a rocket alone cannot push a racer past about
  1.7 blocks per tick. The cap's real job is ring stacking, where 1.5 times 1.5 would reach 2.25 times the speed a racer
  already had.
- **C. Measure first.** Record the maximum shadow speed per map in the map-start or end log and in `describe()`. Use the
  numbers from a playtest to decide whether A is ever needed. Part of B's task list.

Semantics of B: the cap is a limit on the magnitude of the velocity written by a ring effect. A map with a cap of 3.5
and a BOOST of 1.5 on a racer at 2.5 blocks per tick writes 3.5, not 3.75. The shipped map has no cap, so its
behaviour is the multiplier alone.

### D7. The velocity-authority list does not grow

`VelocityExit`'s javadoc lists two external forces: a zero (`standDown`, `RunResetter`) and a ring BOOST/SLOW effect,
"not built yet". This change builds the second one and does not add a third. The cap is applied before the write, inside
`race/effect`, so `ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom` continues to hold without a change. The
javadoc of `VelocityExit` and `Racers` is updated from "not built yet" to the built state.

### D8. The "source, not configuration" statement changes

The design spec, row (c) and the paragraph below the table, say boost cooldown is game-wide gameplay tuning, lives in git,
and is not configuration. Two parts of that stay true: the value lives in versioned files in git, and no operator
environment variable overrides it. The part that changes is "game-wide". Once a map carries its own cooldown and
multipliers, the cooldown is per-map content, like ring points and the spawn. The proposed amendment moves burn, cooldown,
rocket count, cap and ring factors from row (c) to row (d), "map and cup definitions", and keeps ring points, medal brackets
and plausibility tolerances in (c). The comparability argument in the paragraph still holds per map: a record is comparable
only with records on the same map file version. The amendment is a task that waits for the owner's acceptance.

### D9. Setup

Setup must be able to carry these fields now, and it does: `DraftEditor` rebuilds a `MapDefinition` from the draft, and
`MapDraftAdapter` and `MapDraftJsonWriter` read and write it. Without this change those paths would drop the new fields on
a save, which would silently reset a designer's tuning. Editing the fields in setup is a follow-up, with its own change
and a scope decision. `RingDefaults.BOOST = new BoostConfig(30, 40)` keeps compiling through the two-argument constructor.

### D10. Visibility (part c)

- A reference page under `docs/reference/` documents every boost field, its type, bounds and default, with a short
  example. Diátaxis quadrant: reference.
- `map.schema.json` gets descriptions for every new field.
- The map-start log line gains the map's tuning: burn, cooldown, rockets, cap, and the two factors. This line appears in
  the golden transcripts (see Golden master), so it is a deliberate, reviewed golden change, not a side effect.

## Dependency direction and module placement

Clean Architecture: dependencies point inward, from the composition root and platform towards the API.

| Type or change | Module | Package | Depends on |
|---|---|---|---|
| `BoostConfig` (two new fields; two-argument constructor kept) | voyager-api | `net.elytrarace.voyager.api.race` | none |
| `RingEffectTuning` (record, `DEFAULT`) | voyager-api | `net.elytrarace.voyager.api.race.effect` | none |
| `MapDefinition` (new component `ringEffects`; seven-argument constructor kept) | voyager-api | `net.elytrarace.voyager.api.race` | `RingEffectTuning` |
| `InvalidRingEffectException` | voyager-api | `net.elytrarace.voyager.api.race.effect.exception`, with `package-info.java` | none |
| `InvalidBoostConfigException` factories for the two new fields | voyager-api | `net.elytrarace.voyager.api.race.exception` | none |
| `RingEffectRegistry.create(RingEffectTuning)` | voyager-race | `net.elytrarace.voyager.race.effect` | api |
| `SpeedCap` (pure function, `Vec3` in, `Vec3` out) | voyager-race | `net.elytrarace.voyager.race.effect` | api |
| Adapters, writer, `DraftEditor` pass-through | voyager-platform, voyager-setup | catalog, mapsetup | api |
| `Racers.equip(player, count)`, `Rockets.item` derivation | voyager-platform | `platform.flight` | api |
| Velocity seam `scaleVelocity` | voyager-platform | `platform.flight` | api |
| Ring-effect step and map-start log | voyager-platform | `platform.cup` | race, api |
| Composition root | voyager-server | `server` | none changed (no new bean) |

No new module, so `FitnessCoverageTest` needs no entry. Rules that already cover the changes: `ApiPurityTest` (no Minestom
in the API; the velocity door), `DesignRuleTest` (exception suffix, `RuntimeException` base), `NullabilityConventionTest`
(`package-info.java` in the new exception package). The new `race/effect` code depends only on the API, which the
existing slice rules already require.

## Golden master

- The five committed transcripts under `voyager/server/src/test/resources/golden/cup-session/` build every ring as
  `RingType.STANDARD` (`CupSessionGoldenMasterTest`, its single ring factory). No ring effect runs in them, so the
  effect wiring does not change them.
- Their map-start `log` line does change, because D10 adds the tuning to it. That is the only expected golden change,
  and each transcript diff is reviewed line by line. The golden test never writes a file, so the regeneration is a
  manual step with a reviewer.
- The shipped map's BOOST rings (indices 0, 18, 27) change velocity at runtime. No committed golden flies the shipped
  map through the platform (`ShippedCatalogueTest` and `CommittedMapDataTest` check data, not flight), so no golden
  changes for them. Their behaviour is tested by new tests with explicit ticks: crossing ring 0 multiplies the shadow
  velocity and the written velocity by 1.5.
- The physics traces under `voyager/physics/src/test/resources/traces/` test the rocket impulse only, so they are
  unaffected.
- If a golden changes for any reason other than the log line, stop and treat it as an unexpected path.

## Known discrepancies (accepted, to be measured)

- A ring effect is computed on the shadow velocity, which is current to the tick's sample. The client receives the write
  about half a round trip later. Between those two moments a rocket impulse the client applies can be missing from the
  server's view, so the two may differ by up to one rocket impulse per ring. A playtest measures this.
- A rocket and a ring on one tick: the shadow applies the impulse inside the flight step, before the ring multiplier, so
  the multiplier scales the impulse. On the client the impulse keeps arriving on the client's own tick, so the override
  may land before or after it. This is the same one-impulse uncertainty as above.
- Effects apply only while gliding. A ring crossed on foot does nothing.

## Risks

- Behaviour change on the shipped map: the BOOST rings now speed racers up. Intended, but it changes the lap times the
  shipped map's records were set on. Mitigation: the reference time and records are compared per map version, and the
  owner decides whether to reset the shipped map's records (open question).
- Older servers silently ignore the new fields (D1). Mitigation is deployment order, recorded in ADR-0032.
- Cap semantics could surprise a designer who expects a cap on speed in general. Mitigation: the reference page states
  the rule in its first sentence.

## Open questions for the owner

1. Accept the amendment of the design spec's "source, not configuration" row (D8)?
2. A v1 file that carries the new fields: accept them (this proposal) or refuse them until a schemaVersion 2 file?
3. Strength per map: defer (this proposal) or add a server push per burn tick now, as a fourth `VelocityExit` force?
4. Cap: option B (proposal) or wait for measurements before any cap at all?
5. Reset the shipped map's lap records when the BOOST rings start to work?
6. One PR, or the split in "Why one change"?
