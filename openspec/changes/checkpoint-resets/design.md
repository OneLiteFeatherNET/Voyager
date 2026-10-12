# Design

Type and scope: `feat(race)`. See the proposal for why this is a feature and not a fix.

## Context (verified in the tree at origin/main, 6635220)

- `RunReset.plan` (`voyager/race/.../reset/RunReset.java`) targets the last ring passed, of any type. It keeps the run's
  progress unchanged and sets `gliding=false` on the run it returns. With no ring passed, it targets the spawn and faces
  the first ring.
- `ProgressTracker.advance` records `RingProgress.lastCheckpointIndex` when a `CHECKPOINT` ring is passed. Nothing reads it.
- `ProgressTracker` tests only `rings.get(current.passedCount())`. Ordering and "one ring per tick" depend on that.
- `RunResetter.reset` (`voyager/platform/.../cup/RunResetter.java`): `runs.resetTo`, `boosts.cancelBurn`, `flight.forget`,
  `MapTransition.reposition`, `Racers.place` (which sets `setFlyingWithElytra(false)`), `VelocityExit.send(Vec3.ZERO)`, the
  title and the sound.
- `CupSession.sendBackIfLostOrLanded` checks out-of-bounds first, then `RunReset.isLanded(before, isFlyingWithElytra(),
  isOnGround())`. `isLanded` needs `before.gliding()`, so a racer placed without a glide cannot land.
- The committed map `voyager/server/src/main/resources/maps/elytraraceblueandred.json` has 35 rings: 32 `STANDARD`,
  3 `BOOST`, and no `CHECKPOINT`. The three legacy maps under `legacy/server/src/main/resources/maps/` carry only
  `STANDARD` rings in their portal data. The alpha cup plays only the committed map. So no shipped map exercises
  `CHECKPOINT` today.
- Minestom 2026.08.28-26.2 sources (the target version, read from the Gradle cache):
  - `Player.refreshOnGround(onGround)` sets `onGround`, and when it is `true` and the racer is flying with an elytra, it
    calls `setFlyingWithElytra(false)` and fires `PlayerStopFlyingWithElytraEvent`. `PlayerPositionListener` calls it from
    the movement packet handler (three call sites, lines 26, 100 and 105), so on every position update the client sends.
  - `EntityActionListener` handles `START_FLYING_ELYTRA` with `setFlyingWithElytra(true)` and no ground check.
  - `Player.updatePose()` sets the `FALL_FLYING` pose from the flag, so the flag is also what other players see.
  - Conclusion: the server can start a glide on a racer, but on a racer standing on the ground the first position packet
    ends it.
- Legacy `OutOfBoundsSystem` (`legacy/server/.../ecs/system/OutOfBoundsSystem.java`): out-of-bounds is never gated by a
  cooldown; a landing counts only after `MIN_AIR_TICKS_BEFORE_LAND_RESET = 20` airborne ticks; `RESET_COOLDOWN_TICKS = 40`
  suppresses the landing check after a reset. `resetPlayer` teleports to the spawn and clears velocity; it does not touch
  the ring tracker.
- Golden master: `voyager/server/src/test/resources/golden/cup-session/*.txt` log the gliding state per tick. None of the
  five transcripts contains a reset (`reset`, `landed` and `out of bounds` do not occur).
- Docs that this change makes wrong: `docs/reference/course-reset.md` lines 13, 23 (a reset racer "has not glided since"),
  30 (the target table: "None: the map's spawn"), and 56 (the "not implemented" list names the minimum air time and the
  cooldown).

## Decisions

### D1. The target is the last checkpoint passed, or the spawn

`RunReset.plan` targets the centre of the ring `lastCheckpointIndex` names, facing one step along its flow normal. When
`lastCheckpointIndex` is `-1`, it targets the spawn and faces the first ring centre, as today. The target is a `Ring`
of type `CHECKPOINT`, so the rule is pure and lives in `race`.

Dependency direction: `platform` (`CupSession`, `RunResetter`, `Racers`) depends on `race` (`RunReset`, `RaceRun`), which
depends on `api`. No new class is added; `RunReset.plan` and `RaceRun` change.

### D2. The racer keeps the rings up to the target, and loses the rest (OWNER TO CONFIRM)

Keeping the progress after a reset would let the racer skip rings. Suppose a racer has passed rings 0 to 34 and is reset to
checkpoint 10 with progress kept. `ProgressTracker` would next test ring 35, so the racer could fly from ring 10 straight
toward ring 35, past rings 11 to 34. Ordering is the rule that makes the race honest, so progress must be rolled back to the
target:

- With checkpoint `c` as the target, `passedCount` becomes `c + 1`, `lastCheckpointIndex` stays `c`, and
  `passedOnGameTick` is truncated to `c + 1` entries (`RaceRun`'s constructor requires one tick per ring passed).
- With the spawn as the target, `passedCount` becomes `0`, `lastCheckpointIndex` `-1` and `passedOnGameTick` is empty.
- Points come from the run through `MapScorer` (`RaceRun` javadoc), so a rolled-back ring stops scoring. A rolled-back
  ring scores again when it is flown again. This keeps the existing scenario "a ring after the target scores once".

This amends the Must requirement "A reset keeps every ring already passed". It is the one change in this proposal that
the owner must confirm before apply.

Consequence for the committed map: it has no `CHECKPOINT` ring, so every reset of a racer on it goes to the spawn with the
ring progress set to zero, while the race clock keeps running. That is a full restart of the ring run after any
out-of-bounds or landing reset. Alternatives:

- (a) Accept it. The rule is the owner's, and the playtest measures how often it happens.
- (b) Add `CHECKPOINT` rings to the committed map and to later maps. That is a map-content change in a separate change;
  the schema already allows the type, so no schema change is needed.
- (c) Keep progress at the spawn. Rejected: a racer could then fly from the spawn to any ring, skipping the whole course.

Recommendation: (a) for this change, and (b) as the follow-up that the playtest decides on. Pulse (`voyager-game-psychologist`)
reviews the restart before apply (task 1.2).

### D3. A reset racer is placed gliding; the glide is the server's flag, with fallbacks

`Racers.place` sets `setFlyingWithElytra(true)` in place of `false`, keeps the equipment and the facing, and sends no
velocity. `RunResetter` still sends `Vec3.ZERO` through `VelocityExit`. This is the only velocity sent. The reset run records
`gliding = true`, the state the racer is placed in.

Risks, to be checked in the playtest (task 6.3):

- R-glide-1 (client keeps the glide): the vanilla client's fall-flying state is driven by the shared flag and by whether
  the player is on the ground. Whether the client honours a server-set flag on its own player mid-air is not verified here.
  The current `Racers` javadoc claims it, and the owner has not flown it. Treat it as unverified.
- R-glide-2 (grounded placement loses the glide): `refreshOnGround(true)` clears the flag on the first position packet
  that reports the racer on the ground. A reset to the spawn is grounded, so the glide ends at once. A ring centre close to
  the ground does the same.

Fallbacks, in the order to try them:

- F1 (placement): place the racer 2 blocks behind the target ring along its normal (`centre - 2 * normal`), still facing
  along the normal, so the placement is clear of the ring's ground and the racer does not pass the ring again. Costs
  nothing in the rules: rings before the progress are not tested again.
- F2 (deferred flag): place the racer without the flag and set `setFlyingWithElytra(true)` on the first race tick at which
  the racer reports `onGround == false`. This needs a pending-glide state in `RunResetter`, not in the domain, and is the
  robust answer to R-glide-2. Cost: one tick of fall, which the owner has said is what the change removes, so F2 is the
  last resort.
- F3 (spawn): a spawn reset is grounded, so R-glide-2 always applies. Two options. S1: the spawn reset behaves as GO does
  (standing, facing the first ring, equipment, no glide; the racer jumps and deploys). S2: lift the spawn placement by 2
  blocks and glide. S1 is recommended: S2 invents a position that the map does not contain. Owner decision (task 1.3).

The take-off order does not change: the rocket is refused unless the racer is gliding (`FireworkBoostTracker`).

### D4. Landing grace and cooldown

`RaceRun` gains two integers, both in game ticks, and `RaceRun.advance` maintains them:

- `airborneTicks`: the number of consecutive race ticks on which the racer was not on the ground. A tick on the ground sets
  it to 0; an airborne tick adds 1.
- `resetCooldownTicks`: set to `C` by a reset; each race tick decrements it to a minimum of 0.

The landing rule (`RunReset.isLanded`) becomes: the previous run was gliding, the racer is not gliding now, the racer is on
the ground now, the run is not finished, `before.airborneTicks() >= N` and `before.resetCooldownTicks() == 0`.

Values, as seeds: `N = 20` ticks (1 s) and `C = 40` ticks (2 s). They are the tree being replaced's values, not measured in
this game. Out-of-bounds is evaluated first and is not gated by either counter, as in the tree being replaced.

The two gates overlap. After a reset a racer who stays airborne reaches `N` within `C`, so `N` matters only when the racer
touches the ground during the cooldown and then leaves it again. Both stay, because the values were chosen as a pair; the
playtest may drop one.

Why a reset cannot chain: a landing needs a glide, at least `N` airborne ticks and no cooldown. A reset that places the
racer on the ground has `airborneTicks = 0`, so it cannot be a landing for `N` ticks, and the cooldown blocks it for `C`.
Out-of-bounds can chain only if a placement lies outside the bounds of the dimension. The catalogue does not check that
today (out of scope), and task 2.6 pins the behaviour with a test instead.

### D5. Rockets, facing and equipment after a reset

- Burn: cancelled, as now (`boosts.cancelBurn`). The cooldown is kept, as now.
- Equipment: the elytra and 64 rockets, given by `Racers.equip` (already called by `place`).
- Facing: a checkpoint target faces one step along the flow normal, as a ring reset does today. A spawn target faces the first
  ring centre, as a map start does.
- The reset run's `gliding` is `true` (D3), and its `airborneTicks` is 0 and its cooldown is `C`.

### D6. Titles name the checkpoint

The out-of-bounds and landing titles name the target as the checkpoint number, or the start, for a spawn reset. The existing
reset sound is unchanged and is not played on a ring pass in that tick.

### D7. Golden master

The five golden transcripts contain no reset, so they are not regenerated by this change. The `RaceRun` counters are not
logged in them. Expected impact: none. Verify with `CupSessionGoldenMasterTest`. A new golden transcript pins one checkpoint
reset (task 2.8), named after its behaviour, so the reset sequence is visible in review.

### D8. ADR: a new record that amends ADR-0028

Recommendation: a new ADR, `docs/decisions/0031-checkpoint-resets-with-a-deployed-elytra.md` (MADR 4.0, status Proposed),
and a one-line status note in `docs/decisions/0028-racers-take-off-without-a-server-impulse.md` that points to 0031.

Why a new record and not an edit: ADR-0028 records a decision (no launch impulse, and no glide at a reset) that stands for
the impulse and is reversed for the glide. A new record keeps the reasoning of each decision, and it records the grace,
the cooldown and the progress rollback, which 0028 does not cover. Editing 0028 in place would also be allowed while 0028 is
Proposed. The tipping condition is the owner's acceptance of 0028: once it is Accepted, an accepted record is not edited, so
the new record is the only route. Numbers 0027 to 0030 are taken; 0031 is the next free one.

No ADR is accepted by this change. The owner accepts 0031.

### D9. Fitness

No new boundary is introduced. `RunReset`, `RaceRun` and `ProgressTracker` stay free of Minestom and of `platform` types, as
they are now. The existing rules in `voyager-fitness` cover them: `SliceBoundaryRulesTest` (no dependency from a slice on the
platform) and `ApiPurityTest` (the only velocity door, `VelocityExit`, stays the only sender). `./gradlew :voyager:fitness:test`
is the check. No rule is added or removed.

## Risks

- R1 (Pulse): a racer reset to a checkpoint loses the rings after it; a racer reset on a map with no checkpoint restarts its
  ring progress. Both can feel unfair. The playtest records how often each happens on the committed map (task 6.4).
- R2 (game feel): the 1 s grace means a racer who lands on a low ring after a reset is not reset and must jump again. That is
  the legacy behaviour; the playtest checks that it reads as a landing.
- R-glide-1 and R-glide-2 (D3): the client may cancel a server-set glide, and a grounded placement loses it at once.
- The catalogue does not check that a checkpoint lies inside the vertical bounds. A checkpoint outside them would reset
  every tick it is reached. Out of scope; noted for a catalogue change.

## Open questions for the owner

1. D2: confirm the progress rollback on a reset (the amendment of "keeps every ring"), and whether to add checkpoints to the
   committed map before or after this change lands.
2. D3 / F3: a spawn reset as at GO (S1, recommended) or a lifted spawn with a glide (S2).
3. D4: confirm `N = 20` and `C = 40` as seeds, to be tuned in the playtest.
4. D8: confirm the new ADR 0031 that amends 0028, rather than an edit of 0028.
5. D3 / F2: whether the deferred flag (F2) is acceptable as the fallback if F1 does not hold in the playtest.

## Dependency direction and module placement

| Type or change | Module | Depends on |
|---|---|---|
| `RunReset.plan` (target, rollback), `RunReset.isLanded` (grace, cooldown) | `voyager-race` | `voyager-api` |
| `RaceRun` (`airborneTicks`, `resetCooldownTicks`) | `voyager-race` | `voyager-api` |
| `RunResetter.reset` (zero velocity, no impulse, gliding) | `voyager-platform` | `voyager-race`, `voyager-api` |
| `Racers.place` (flag set) | `voyager-platform` | Minestom |
| `CupSession.sendBackIfLostOrLanded` (passes ground state and counters) | `voyager-platform` | `voyager-race` |

No type is added to `voyager-api`. Nothing in `voyager-server` changes except the reset title keys.
