---
name: Authority-model divergence paper pattern
description: Documentation structure for papers where a client/server authority mismatch caused a gameplay-feel bug (elytra velocity, future movement/input issues)
type: project
---

When Voyager ships a fix that reassigns authority for a replicated quantity (velocity, rotation, health, score) between client and server, the research paper reads best when structured as:

1. **Authority model in vanilla** — cite the exact vanilla method and whether it runs client-side or server-side.
2. **Platform API mechanics** — cite the Minestom setter and confirm whether a "silent" variant exists. If not, every write is observable.
3. **Platform player-movement semantics** — note whether Minestom discards simulated physics for `Player` (it does).
4. **Divergence mechanism** — derive the drift algebraically in terms of network latency *L* ticks; avoid purely empirical framing.
5. **External-force carve-out** — list which vanilla systems legitimately emit the packet (firework boost, explosions, knockback, rings) so the fix preserves them.

**Why:** The 2026-04-24 elytra velocity paper proved this five-finding skeleton explains the symptom, the fix scope, and the remaining anti-cheat work in one pass. Reviewers did not need a supplementary packet trace.

**How to apply:** Reuse this skeleton for any future Voyager paper involving replicated-state authority (e.g., rotation smoothing, score reconciliation, HUD-driven effects). Always cite vanilla source and Minestom source side-by-side in References; the contrast is the core evidence.
