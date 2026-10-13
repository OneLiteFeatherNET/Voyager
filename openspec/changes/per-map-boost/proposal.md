# Proposal

**Conventional Commits:** `feat(race)`. Squash-merge PR title: `feat(race): tune boost and ring effects per map`.

All four parts ship under the type `feat`. The reference page and the ADR are `docs` commits in the same change. Nothing
here is a fix or a refactor on its own, so this is one change, not two. `design.md` section "Why one change" gives the
reasoning and the split that is available if the owner wants a smaller first PR.

**Record:** ADR-0032 (`docs/decisions/0032-per-map-boost-tuning.md`, Proposed). Numbers 0027 (reserved by the unmerged
`oval-tilted-rings` change), 0030 and 0031 are not used here.

**Existing decision this changes:** the greenfield design spec, section "Gameplay tuning", row (c), and the paragraph
"(c) is source, not configuration" (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, lines 818-828). It
says the boost cooldown is a game-wide value that belongs in git and is not configuration. Once boost tuning is per-map
content, the cooldown is no longer game-wide. The owner has to accept that. The spec amendment is a task that waits for
that acceptance; this proposal does not edit the spec.

## Why

The boost is half built. The per-map `boostConfig` (burn and cooldown) reaches the cup, but the two ring effects are not
connected: `RingEffectRegistry` is used only by its tests, and the multipliers 1.5 (BOOST) and 0.5 (SLOW) are code
constants. A racer carries 64 rockets that are never used up, and the rocket flight duration is hard-coded to 3. The
shipped map marks rings 0, 18 and 27 as BOOST, and the shipped-catalogue spec requires them, but they do nothing. The
owner decided on 2026-10-12 that boost is tuned per map and that BOOST and SLOW rings work.

## What Changes

- **(a) Ring effects connected.** A racer who crosses a `BOOST` or `SLOW` ring has the velocity multiplied by that map's
  factor, on the server's shadow simulation and on the client through `VelocityExit`. The factors come from the map file
  and default to today's constants, 1.5 and 0.5. An effect applies only to a gliding racer with an unfinished run.
- **(b) Rockets per map.** `rocketsPerRacer` sets the stack a racer is given (default 64, today's value). The item's
  flight duration is derived from the map's burn, so the tooltip and the server's burn agree. Rocket impulse strength is
  not made per-map (design D5).
- **(c) Burn and cooldown documented and visible.** `burnDurationTicks` and `cooldownTicks` keep their meaning and stay
  required. A reference page documents every boost field, the schema describes them, and the map-start log line names the
  map's tuning.
- **(d) Speed cap.** `maxSpeedBlocksPerTick` is optional. When present, it limits the velocity a ring effect writes, and
  nothing else. It never limits normal flight or rockets (design D6). No cap is the default, as today.
- **Format.** Every new field is optional and defaults to today's behaviour. The change reads the fields in schema version
  1 and version 2 files alike. `map.schema.json` and the `voyager-api` records are updated. Files without the new fields
  load unchanged.
- **Setup.** Draft storage keeps the new fields through a load and a save. A setup editor for them is a follow-up.

## Capabilities

### New Capabilities
- `race/ring-effects`: how BOOST and SLOW rings change a racer's velocity; stacking; the cap; interplay with rockets and
  with resets.
- `race/firework-boost`: rocket count; the rocket item's flight-duration claim; burn and cooldown visibility.

### Modified Capabilities
- `catalog/map-file-format`: adds the optional `ringEffects` object and the optional `boostConfig` fields.
- `catalog/file-schema`: the schema describes the optional fields.

## Impact

- Slices: `race/effect` (per-map registry, speed cap), `platform/flight` (rocket count and item, velocity seam),
  `platform/cup` (ring-effect step, map-start log), `platform/catalog` (adapters and writer), `api/race` (records and
  exception), `setup/mapsetup` (round trip only).
- Out of scope: a setup editor for these fields; per-map rocket impulse strength; a hard cap on measured flight speed; an
  operator environment override of any of these values (the design spec forbids it, and this change keeps that rule);
  changes to the tree being replaced (`legacy/`); the oval and tilted rings change; the shipped map's data (its BOOST ring
  indices and the burn and cooldown values stay as they are).
- Intended behaviour change: the shipped map's BOOST rings (0, 18, 27) and any SLOW ring now change the racer's velocity.
  Before this change they did nothing.
