---
name: elytra-authority-model
description: Voyager's elytra flight is client-authoritative; only specific systems send velocity to the client
type: project
---

Normal elytra flight on the Voyager server is client-authoritative (vanilla parity). `ElytraPhysicsSystem` runs `ElytraPhysics.computeNextVelocity` every tick to keep a server-tracked velocity for downstream systems, but does NOT call `player.setVelocity()`.

Only these paths push `Entity Velocity` to the client:
- `FireworkBoostSystem` — every burn tick while a boost is active
- `RingEffectSystem` — on `BOOST` or `SLOW` ring effect, guarded by a pre/post equality check
- `OutOfBoundsSystem.resetPlayer` — sends `Vec.ZERO` before teleporting

Unit convention: `ElytraFlightComponent` and the physics formulas use blocks/tick; `Player.setVelocity` expects blocks/second. Always multiply by `20.0` at the boundary.

Recorded in ADR-0002 (`docs/decisions/0002-elytra-flight-client-authority.md`). Physics reference is `docs/elytra-physics-reference.md` (note: lives at docs/ root, NOT under docs/reference/).

**Why:** Several docs (reference, ADR-0001 firework boost, any future boost/ring/physics doc) touch this model. Getting the authority direction wrong will produce docs that contradict reality.

**How to apply:** When writing any doc that mentions server-sent velocity, check whether the described code path is one of the three above. If not, the doc is probably wrong — normal flight does not push velocity. When documenting velocity values, check and state the unit (blocks/tick vs blocks/second) explicitly.
