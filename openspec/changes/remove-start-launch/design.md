# Design

Type and scope: `fix(race)`. The change corrects a deviation the project already committed against. CLAUDE.md states
that normal elytra flight is client-authoritative, matching vanilla, and that `Racers.launch` is the one exception the
server makes at a named moment. The owner's 2026-10-12 decision is that the exception is unnecessary, so removing it
returns the rebuild to the behaviour the project's own design and the old game already had. No new capability is added,
which rules out `feat`. The change is a reversal of a behaviour, not a refactor, because the observable flight changes.
The documentation and ADR ride along with the code under the same type, as the OpenSpec rules require when behaviour
changes. The PR title is `fix(race): let racers take off themselves at GO and after a reset`.

## Context

`Racers.launch` (`voyager/platform/.../flight/Racers.java`) does three things: turns the racer toward the ring, sets
`setFlyingWithElytra(true)`, and sends `launchVelocity` through `VelocityExit`. It is called from two places:
`CupSession.mapStarted` on the GO tick (after `transition.advanceTo`), and `RunResetter.reset` after the reposition.
`Racers.faceCourse` already equips the racer during the countdown and turns them toward the first ring, so the racer is
ready to act on GO without any impulse.

## Decisions

### D1. GO releases the racer; the server gives no impulse

`CupSession.mapStarted` keeps its `transition.advanceTo(map, racers)` and its `RaceFeedback.go`, and drops the
`Racers.launch` call. `Racers.faceCourse` is still the only place that equips and turns the racer. The racer is not
gliding and has zero velocity on the GO tick. Flight is detected from the client's own deployment through
`isFlyingWithElytra()`, which the greenfield design's flight-detection section already names as the real signal.

**Rejected:** keep a gentle nudge (a small upward impulse) at GO. It is still a server-side flight override, and nothing
has measured that a racer needs it.

### D2. Rockets are used after the elytra is deployed

A firework boost is refused unless the racer is gliding (`FireworkBoostTracker`, the gliding check). The take-off
sequence is therefore: jump, deploy the elytra with a second jump, then fire a rocket. The owner's wording "jumps off and
fires a rocket" matches this order. No code change follows from it; it is recorded here so the playtest checks it.

### D3. Spawns: falling is not a landing, so a racer is not reset

The landing rule is "was gliding on the previous race tick, is on the ground, is no longer gliding"
(`race-course-reset`). A racer who has not deployed the elytra never satisfies it, so a racer who spawns in the air and
falls is not reset. They keep falling until they deploy the elytra or land, and both are left alone. The server does not
push them.

Ground check: the committed `elytraraceblueandred` spawn is `(109, -62, 54)` and ring 0's centre is `(85, -54, 54)`, so
ring 0 is 8 blocks above the spawn. The spawn y is read from `level.dat` (SpawnY), which Minecraft sets to a standing
position, so the committed spawn is most likely on the ground. This pass did not read the world (`run/` is out of
bounds), so it is not verified. A mid-air spawn in another map would fall until the racer deploys the elytra; that is
acceptable under D3, and the catalogue validator is not changed by this proposal.

**Open:** is an unnecessary fall at GO acceptable for a map whose spawn is in the air? Owner call (see Open questions).

### D4. The race clock starts on GO, unchanged

The clock starts on the GO tick as it does now. A racer who waits before deploying the elytra loses that time. Changing
the clock to start at the first glide would need a new state machine and a new race-timing rule. It is out of scope and
listed as an open question.

### D5. Reset places the racer, equips them, and launches nothing

`RunResetter.reset` keeps `runs.resetTo`, `boosts.cancelBurn`, `flight.forget` and `MapTransition.reposition`. It drops
the `Racers.launch(racer, from, toward)` call and calls a new `Racers.place(player, from, toward)` instead, which
equips the racer, turns them toward `toward`, and does not set gliding and does not send velocity. The racer is placed at
the centre of the target ring and falls, or stays where they are, until they deploy the elytra. The reset title and
sound are unchanged.

A racer placed in the air and not gliding is not a landing, so the reset does not chain into a second reset.

**Open:** a racer falling at speed when reset keeps that momentum after the teleport. D6 proposes zeroing it.

### D6. VelocityExit keeps two sends, not three

`VelocityExit` currently names three sources: the launch, the ring BOOST/SLOW effect, and the out-of-bounds reset. After
D1 and D5 the launch is gone, and the out-of-bounds reset sends no velocity of its own. Two sends remain:

1. a ring `BOOST`/`SLOW` effect;
2. zeroing a racer's velocity, which `Racers.standDown` already sends (`Vec3.ZERO`) and which the javadoc never named.

D6 proposes a reset also sends `Vec3.ZERO` through `VelocityExit` so the racer does not carry a fall into the target.
This is a zero, not an impulse, and it is the only velocity a reset sends. The owner decides (see Open questions).

`ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom` stays as a rule. Its `because` text is updated to name the two
sources. No new ArchUnit rule is needed: the existing rule already forbids any other class calling `setVelocity` on a
Minestom type, and removing the launch makes that rule cover the racer's flight without change.

### D7. The greenfield design velocity section is corrected

The paragraph at `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (about lines 411-416) says the velocity
is emitted for "firework boost, ring BOOST/SLOW, out-of-bounds reset". That was already wrong for the firework: a rocket
is a real entity the client boosts with (`Rockets`, `VelocityExit`'s own javadoc). After this change the out-of-bounds
reset sends no velocity either. The corrected paragraph lists the two sends from D6, says the firework is an entity, and
says the take-off is the client's. The design spec is not edited beyond that paragraph.

The user-facing reference `docs/reference/course-reset.md` (lines 23 and 32) says the relaunch is not a landing and that
the racer is launched with the map-start impulse. Both sentences are rewritten to match D5.

### D8. Tests replace the impulse assertions

`RacersTest` (about lines 75-101) asserts the horizontal 16 and vertical 24 blocks per second, and that a launch starts
the glide. Those cases are removed with the methods they call. They are replaced by behaviour tests (tasks.md, section
2). Golden transcripts do not log velocity, so they are unaffected; the task confirms this.

### D9. ADR-0028 records the reversal

The launch decision is in the code and in an archived change's design (`add-out-of-bounds-reset`, D5), not in any ADR.
Reversing it is an architecture-level decision that spans the flight slice, the cup slice and the velocity boundary, so
it gets a short MADR (`docs/decisions/0028-racers-take-off-without-a-server-impulse.md`). Options considered: keep the
launch; keep it at GO and drop it on reset; drop it at GO and on reset (chosen); replace it with an auto-deploy. Status is
Proposed until the owner accepts it. Reversibility is high: the impulse is a few lines and the constants are retained in
the ADR's history.

## Dependency direction

```
race (rules, specs)           no change in code
cup  (CupSession, RunResetter) --> flight (Racers.place, faceCourse, standDown)
flight --> convert (VelocityExit)            only BOOST/SLOW and zeroing remain
```

Nothing new depends on anything new. `Racers.launch` and `launchVelocity` leave; `Racers.place` is added to `platform.flight`.
The `voyager-platform` module is the only one that imports `net.minestom.*`, and it stays so.

## Risks

- **A racer who does not deploy the elytra stalls.** Mitigation: the clock still runs and the map's cap still ends the
  phase. A title hint at GO (existing `RaceFeedback.go`) names the jump; the copy is a separate task, not a new rule.
- **Mid-air spawns fall in place.** Mitigation: D3; a racer who falls onto ground is not reset.
- **Reset racers fall through a ring they just left.** Mitigation: the reset target is the ring centre, and the racer
  faces the flow normal; the playtest checks it.

## Decisions recorded from the owner (2026-10-12)

1. **Clock (D4): decided.** The race clock starts at GO, as today. A racer who waits before deploying loses that time.
2. **Reset velocity (D6): decided.** A reset sends `Vec3.ZERO` through `VelocityExit`, so a reset racer does not carry a
   fall into the target. This is the only velocity a reset sends, and it is a zero, not an impulse.
3. **Mid-air spawns (D3): decided.** Accepted. A racer who falls is left alone and glides once they deploy the elytra.
   A catalogue ground check is out of scope for this change.
4. **GO hint (D10): decided.** A short title at GO tells racers to jump and deploy the elytra. Copy only; it is not a rule.

### D10. GO shows a jump-and-deploy title

`RaceFeedback.go` shows the existing GO title and adds a subtitle, `voyager.race.go.jump`, with the text "Jump, then deploy
your elytra." It is a MiniMessage translatable in `voyager/server/src/main/resources/voyager_en_US.properties`, with the
`<arg:N>` placeholder style if an argument is ever needed (none is). The subtitle is sent in the same tick as the GO
title. It is a presentation change in `platform.hud` and `platform.text`, not a rule.
