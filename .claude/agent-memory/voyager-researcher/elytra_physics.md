---
name: Elytra Physics Reference
description: Vanilla elytra flight per-tick formula, firework boost semantics, and Minestom/server-side caveats
type: reference
---

## Vanilla elytra per-tick physics (samsartor gist, 15w41b, stable since)
Each tick, given look vector (lookX, lookY, lookZ) from (yaw, pitch), hvel = sqrt(vx^2+vz^2), hlook = cos(pitch), sqrpitchcos = cos(pitch)^2:

1. Gravity with pitch lift: `vy += -0.08 + sqrpitchcos * 0.06`
2. Dive acceleration (when falling nose-down): if vy<0 and hlook>0: yacc = vy * -0.1 * sqrpitchcos; vy += yacc; vx/vz redistributed via lookX*yacc/hlook
3. Pitch-up deceleration: if pitch<0: yacc = hvel * -pitchsin * 0.04; vy += yacc*3.5; vx/vz lose lookX*yacc/hlook
4. **Steering blend (THE KEY):** if hlook>0:
     `vx += (lookX/hlook * hvel - vx) * 0.1`
     `vz += (lookZ/hlook * hvel - vz) * 0.1`
   i.e., 10% lerp of horizontal velocity toward current look direction, preserving horizontal speed.
5. Drag: `vx *= 0.99; vy *= 0.98; vz *= 0.99`

## Firework boost (vanilla)
- Applied **per-tick while firework is active** (not one-shot). Firework entity ticks for ~1.17/1.48/2.22 s for duration 1/2/3.
- Per-tick formula (decompiled vanilla FireworkRocketEntity.tick()):
     `motion.x += lookX * 0.1 + (lookX * 1.5 - motion.x) * 0.5`
     `motion.y += lookY * 0.1 + (lookY * 1.5 - motion.y) * 0.5`
     `motion.z += lookZ * 0.1 + (lookZ * 1.5 - motion.z) * 0.5`
  = steady 0.1*look push + 50% lerp toward 1.5*look. Both recomputed each tick with CURRENT look → steerable mid-boost.
- Steady-state horizontal speed ≈ 1.675 b/t ≈ 33.5 b/s (matches wiki).

## Minestom specifics
- Minestom has NO server-side elytra physics. Client predicts motion locally; server just relays position.
- `player.setVelocity(vec)` sends Entity Velocity packet (SetEntityMotion). Client applies it, then continues client-side elytra sim.
- One-shot setVelocity during elytra flight → client applies once, then drag (*0.99) and steering-blend lerp (*0.1 toward look) take over from current velocity. With 0.99 drag, high-magnitude impulses bleed off slowly but the look-lerp is only 10%, so steering feels stiff until magnitude decays.
- Correct pattern for steerable boost: send velocity packet each tick (20 TPS), recomputing target from current look direction. This matches how vanilla's FireworkRocketEntity operates on its rider.

## Source tiers
- Samsartor gist: T3 (community-reverse-engineered, consistent across sources)
- Wiki 33.5 b/s: T2 (official wiki, matches math)
- Minestom client-authoritative behavior: T3 (discussion #1427 + elytra-speed-cap mod note)
- Firework per-tick formula numbers (0.1, 1.5, 0.5): T4 — community-known but I could not re-verify against current Mojang decompiled source in this session. Treat as best-effort; verify against Yarn mappings before shipping.
