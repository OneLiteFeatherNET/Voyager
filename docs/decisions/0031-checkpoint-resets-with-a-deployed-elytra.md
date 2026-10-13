# ADR-0031: A reset returns a racer to the last checkpoint with the elytra deployed

## Status

Proposed

The owner accepts or rejects this record, after the playtest of the change (tasks 6.2 to 6.4 of `checkpoint-resets`). It amends
[ADR-0028](0028-racers-take-off-without-a-server-impulse.md) for the reset glide. Status moves to Accepted only on the owner's word.

## Date

2026-10-13

## Decision makers

- TheMeinerLP (project owner). On 2026-10-13 decided the reset target, the glide, the spawn behaviour, the landing grace and cooldown
  and the record's status, as listed under "Decision outcome".
- Drafted with the change `checkpoint-resets`, after reading `RunReset`, `RaceRun`, `RunResetter` and `Racers`, the Minestom 2026.08.28-26.2
  player sources (the ground check and the flying flag), and the legacy `OutOfBoundsSystem`.

## Context and problem statement

ADR-0028 removed the server-side launch at GO and at a reset. Two problems remain in the reset that it left:

- A reset returns the racer to the last ring passed, of any type. The owner wants the reset to return the racer to the last `CHECKPOINT`
  ring passed, and to the map's spawn when none was passed.
- ADR-0028 places a reset racer without a glide, and the landing rule needs a glide before it. A racer reset in mid-air therefore falls,
  and is never reset again by the fall. The owner wants the racer to arrive with the elytra deployed and to glide at once.

The landing rule has a third gap. It counts a touch of the ground after any glide, with no minimum time in the air. A racer placed close
to the ground who touches it after a glide is reset again at once, and the resets can chain. The tree being replaced counted a landing only
after 20 airborne ticks and suppressed the check for 40 ticks after a reset. The rebuild had neither.

The committed map `elytraraceblueandred` carries `CHECKPOINT` rings at indices 7, 15, 23 and 31 (since 2026-10-13). The other shipped maps
carry them too. A map with no checkpoint resets every racer to its spawn.

## Decision drivers

- A reset must return the racer to a place they earned, and must not let them skip rings. The ring order is the rule that makes the race honest.
- A reset racer should be able to fly on at once, without a fall and without a jump they must learn again.
- The server sends a velocity only for an external force that the project has named (`VelocityExit`). A reset adds no impulse.
- A landing must be a real landing: a racer placed close to the ground must not be reset again by touching it.
- Nothing about the glide flag, the grace or the cooldown has been measured by a player yet. The numbers are seeds.

## Considered options

1. **Keep the target as the last ring of any type, and keep the progress.** Status quo. Rejected by the owner: a reset to a `STANDARD` ring
   is not a place the course marks, and the reset racer falls.
2. **Target the last `CHECKPOINT` ring, roll the progress back to it, place the racer gliding with no impulse, and reset to the spawn on foot
   when no checkpoint was passed (chosen).** Each reset is to a checkpoint the course marks. The ring order is kept, because the rings after the
   checkpoint are removed from the run. A spawn reset behaves as GO does.
3. **Target the last checkpoint and keep the progress.** Rejected: a racer could fly from the checkpoint straight to a later ring and skip the
   rings in between. The ring order is what makes the race honest.
4. **Reset to the spawn and glide from a lifted position.** Rejected for now: the spawn is grounded and the lifted point is one the map does not
   contain. The owner chose the on-foot spawn reset.
5. **Defer the glide flag to the first airborne tick.** Kept as the fallback. It is the robust answer if the client does not keep a glide
   the server starts, but it costs a tick of fall, which this change removes.

## Decision outcome

Chosen option: **2**, with these parts, all decided by the owner on 2026-10-13.

- **Target.** A reset places the racer at a point one block behind the centre of the last `CHECKPOINT` ring passed, along its flow normal,
  and one block above it, facing one step along the normal. A racer who has passed no checkpoint goes to the map's spawn, facing the first ring.
- **Progress.** A checkpoint reset keeps the rings up to the checkpoint and removes the rings passed after it. A removed ring scores again when
  it is flown again. A spawn reset removes every ring. This amends the requirement "A reset keeps every ring already passed" in the
  `race-course-reset` capability.
- **Glide.** A checkpoint reset places the racer with the gliding flag set and the elytra and rockets in hand. No velocity is sent except a zero
  through `VelocityExit`. A spawn reset places the racer on foot, facing the first ring, not gliding, and the racer takes off by jumping and
  deploying the elytra, as at GO. This amends the "no glide at a reset" part of ADR-0028 only. ADR-0028's decision not to give a launch impulse
  is unchanged.
- **Landing.** A landing counts only after at least 20 consecutive airborne race ticks (one second), and not during the 40 race ticks (two
  seconds) after a reset. Out-of-bounds resets are not gated by either. The numbers are the values the tree being replaced used, and the
  playtest decides whether they stay.
- **Fallbacks, in order.** If the client does not keep a glide the server sets (risk R-glide-1), the owner accepts the deferred flag: the server
  sets the flag on the first race tick at which the racer is in the air. A grounded placement loses the glide at once, because the first position
  packet from the ground ends it (risk R-glide-2). A checkpoint placement sits one block above the ring centre, so it is in the air unless the
  ring is very low.

## Consequences

- Good: a reset goes to a checkpoint the course marks, and the racer keeps the rings before it.
- Good: a reset racer does not fall into the target. The take-off after a spawn reset is the one at GO.
- Good: a reset cannot chain on its own. A placed racer has no airborne ticks, and the cooldown blocks the landing rule for 40 ticks.
- Bad: a racer reset to a checkpoint loses the rings after it, and a racer reset to the spawn restarts the ring run. Both can feel unfair.
  The playtest records how often each happens (task 6.4).
- Bad: a glide the server sets is not verified. The client may end it at once. The fallback costs a tick of fall.
- Bad: the catalogue does not check that a checkpoint lies inside the vertical bounds. A checkpoint outside them would reset every tick it is
  reached. This is noted for a catalogue change and is not decided here.
- Open until the owner's playtest (tasks 6.2 to 6.4 of the change, written in [how to run a playtest](../guides/how-to-run-a-playtest.md)): whether
  a checkpoint reset glides at once, whether a spawn reset takes off as at GO, and how often resets and chains happen on the committed map. The
  result is recorded in `docs/research/` and in this section when it is in.
- Reversibility: high for the grace and the cooldown, which are two constants. Medium for the target rule, because the map files carry the
  checkpoint marks.

## Confirmation

- `RunResetTest` pins the target (the last checkpoint, or the spawn), the progress rollback, the gliding run with the cooldown and no airborne
  tick, and the landing grace and cooldown.
- `RaceRunTest` pins the airborne count and the cooldown countdown.
- `RacersTest` and `RunResetterTest` pin the glide flag of a placement, and that a reset adds no impulse.
- `CupSessionTest` and `CupPlaythroughTest` pin a landing after a reset that is not counted before the grace, and the score after a rollback.
- The golden transcript `checkpoint-reset.txt` of `CupSessionGoldenMasterTest` pins a checkpoint reset as a sequence.
- The owner's playtest (tasks 6.2 to 6.4) is the check for the client's behaviour, which no test can pin.

## More information

- Change: `openspec/changes/checkpoint-resets/` (proposal, design D1 to D9 and the owner's decisions, the spec deltas `race-course-reset` and
  `catalog/shipped-catalogue`).
- Amends: [ADR-0028](0028-racers-take-off-without-a-server-impulse.md), for the reset glide.
- Related: [course reset](../reference/course-reset.md), [map and cup files](../reference/map-and-cup-files.md).
