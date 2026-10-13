# ADR-0028: Racers take off themselves at GO and after a reset; the server gives no launch impulse

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `remove-start-launch` (OpenSpec, design D9).
Status moves to Accepted only on the owner's word.

Note (2026-10-13): [ADR-0031](0031-checkpoint-resets-with-a-deployed-elytra.md) amends the reset part of this record. A reset to a
checkpoint places the racer gliding, with the elytra deployed by the server and no impulse, and a reset to the spawn stays on foot.
The decision of no launch impulse at GO and at a reset is unchanged.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). Decided on 2026-10-12 that the server-side launch is unnecessary, and answered the four open
  questions of the change's design (clock, reset velocity, mid-air spawns, GO hint).
- Drafted with the change `remove-start-launch`, after reading the tree being replaced (the racer stood on the spawn with elytra
  and rockets, jumped off and fired a rocket) and the velocity boundary in `voyager/platform`'s `VelocityExit`.

## Context and problem statement

Since the rebuild's first flight, `Racers.launch` has started every glide for the racer. At the GO tick of a map,
`CupSession.mapStarted` turned the racer toward the first ring, set the gliding flag and sent a launch impulse (0.8 blocks per
tick forward, 1.2 up) through `VelocityExit`. A course reset relaunched the racer with the same impulse.

That is a server override of the client-authoritative flight that the project commits to: normal elytra flight is the client's, as
in Vanilla, and the server sends a velocity only for external forces (see `VelocityExit`). The impulse numbers were never flown; they
are seeds. The tree being replaced never launched anyone. Its racers took off by themselves.

The question is whether a server-side launch is needed for a racer to take off, and whether a reset needs one to put the racer back
in the air.

## Decision drivers

- Normal flight is the client's. A velocity leaves the server only for an external force that the project has named.
- A racer must be able to take off with what they are given: the elytra and the rockets, which are given during the countdown.
- A reset must not leave a racer falling into the target with a velocity they did not choose.
- Nothing is measured yet that a racer needs the impulse. The numbers were never flown.

## Considered options

1. **Keep the launch at GO and at a reset.** Status quo. Rejected: it overrides the client's flight at a named moment that nobody
   has shown to be needed, and the racer's own take-off works in the tree being replaced.
2. **Keep the launch at GO, drop it on a reset.** Rejected: the GO impulse is the same override, and a reset would still differ from
   a map start for no reason the owner has stated.
3. **Drop the launch at GO and at a reset (chosen).** The racer is released at GO with the elytra and the rockets in hand, and takes
   off by jumping and deploying the elytra. A reset places the racer with the same equipment, not gliding, and zeroes the velocity so
   a fall is not carried into the target. A GO subtitle tells the racer to jump and deploy.
4. **Replace the launch with an automatic elytra deployment.** Rejected: it is a server-side flight start under another name, and it
   removes the take-off the racer is meant to make.
5. **Keep a gentle upward nudge at GO.** Rejected: it is still a server-side flight override, and nothing has measured that a racer
   needs one (design D1).

## Decision outcome

Chosen option: **3**.

- `CupSession.mapStarted` keeps its transition and the GO feedback, and no longer calls `Racers.launch`. The racer is not gliding and
  has zero velocity on the GO tick. Flight is detected from the client's own deployment through `isFlyingWithElytra()`.
- `RunResetter.reset` places the racer with `Racers.place` (equipment, facing, not gliding) and sends `Vec3.ZERO` through
  `VelocityExit`. No impulse is sent.
- `VelocityExit` names two remaining senders: a zero when a map ends or a racer is reset, and the ring BOOST/SLOW effect, which is not
  built yet. `ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom` keeps its rule.
- The rocket is used after the elytra is deployed. A firework boost is refused unless the racer is gliding, so the take-off order is:
  jump, deploy the elytra, fire a rocket.
- A racer who spawns in the air and falls is not reset: a landing needs a previous glide. The server does not push them.
- The race clock starts at GO, as before. A racer who waits before deploying loses that time.

## Consequences

- Positive: the server no longer overrides the client's flight at GO or at a reset. One velocity sender is removed. The take-off is the
  same as in the tree being replaced.
- Positive: a reset racer does not carry a fall into the target, because the velocity is zeroed.
- Negative: a racer who does not deploy the elytra stalls at the spawn. The clock keeps running and the map's cap still ends the phase.
  The GO subtitle is the mitigation; it is copy, not a rule.
- Negative: a map whose spawn is in the air makes a racer fall until they deploy. The catalogue validator is not changed by this record.
  The committed spawn is probably grounded (design D3), which is not verified.
- Open until the owner's playtest (tasks 6.2 and 6.3 of the change): the time from GO to the first glide, whether the committed spawn is
  grounded, and whether a reset racer arrives at the ring centre with the elytra in hand and takes off by themselves. The result is
  recorded in `docs/research/` and in this section when it is in.
- Reversibility: high. The launch is a few lines in `Racers`, and the two constants and the impulse are kept in this record's history.

## Confirmation

- `RacersTest`, `RunResetterTest` and `CupSessionTest` pin the behaviour: no impulse at GO, the equipment kept, the reset placed without
  a glide and with a zero velocity, a deployment after GO seen by the race tick.
- `ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom` keeps the sole sender rule.
- The golden transcripts of `CupSessionGoldenMasterTest` record the GO tick without a glide, the seeded simulation from zero velocity
  and the GO subtitle. They were regenerated under this record and nowhere else.

## More information

- Change: `openspec/changes/remove-start-launch/` (proposal, design D1 to D10, the two spec deltas `race/waiting-lobby` and
  `race-course-reset`).
- Supersedes nothing. It reverses the launch recorded in the archived change `add-out-of-bounds-reset` (design D5), which had no ADR.
- Related: `docs/reference/course-reset.md`, `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (velocity authority).
