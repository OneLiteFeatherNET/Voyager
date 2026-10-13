# Proposal

Conventional Commits: `feat(race)`, title `feat(race): draw the whole course as the racing line`. One type: every
part of the change is a racer-visible change to the racing line, and the platform, catalogue and API parts are the
adapters and data for that one behaviour. `feat(platform)` was considered and rejected: `add-lobby-world` and
`add-oval-and-tilted-rings` both ship platform and catalogue work under `feat(race)`, and a scope that names the
adapter would split one visible behaviour across two scopes.

## Why

The owner decided on 2026-10-12 that the in-game guide line is not usable today and must show the whole course, as the
old game did. Today a racer sees only the stretch from the ring they head for to `lookAheadRings` past it (two rings on
the shipped map, about 90 blocks), drawn as amber dust at scale 1.0. The old tree drew the whole spline with END_ROD for
every player. The rebuild's renderer (`GuideLineRenderer`, called from `CupSession.raceTick`) is wired, but its policy
is the window and its look is fixed, so the owner's request needs a policy change, a per-map style and a packet budget.

## What Changes

- A racer is shown the racing line of the map from the ring they are heading for to the last ring, instead of the
  window ahead. The part of the line already flown is not drawn (owner decision, 2026-10-13).
- The window stays available as a per-map choice, `mode: "WINDOW"`, with today's behaviour.
- Racers are shown only the points within 160 blocks of them, so the packet count is bounded by local density and not
  by course length.
- The line is drawn with END_ROD by default. A map may choose DUST and a colour instead.
- The map file's `guideLine` gains three optional fields: `mode`, `particle` and `dustColor`. Absent fields give the
  behaviour above, so every existing map file loads to the same course and the shipped map needs no edit to load.
- The shipped map sets `particleSpacing` to 2.0 (from 1.0), the owner's decision. It names no `mode` or `particle`:
  the defaults give the course's behaviour.
- The refresh interval is no longer one constant for every particle: it is set by the particle's shortest lifetime.
- The packet budget is set by the owner: 30,000 packets per second in total and 2,000 per racer, at sixteen racers on
  the 35-ring map. A test enforces it.
- ADR-0030 (Proposed) records the decision and reverses the rationale in `GuideLine`'s Javadoc that argued against
  drawing the whole course. It stays Proposed until the owner playtests the shipped course.

**BREAKING**: none for loading. A racer sees a different line on every map that does not set `mode: "WINDOW"`, which is
the intended change.

**Not breaking.** No ring, boost, guide point, scoring or physics value changes. The golden transcripts of a started cup
do not record particles (no `particle` or `guide` text in `voyager/server/src/test/resources/golden`), so they should
stay byte-identical; `CupSessionGoldenMasterTest` is the check.

**Out of scope:**
- Particle types other than END_ROD and DUST. The map file names a fixed list, not any Minecraft particle.
- Trimming the line by anything other than the racer's progress, such as distance behind the racer.
- The tree being replaced (`legacy/`, which gets no new features).
- Any change to the greenfield design's D-decisions, including D10.
- The oval and tilted-ring change (`add-oval-and-tilted-rings`, local branch). It is not on main and is ignored here
  (owner decision, 2026-10-13).
- A spectator or builder preview of the line. The renderer stays per racer.

## Capabilities

### New Capabilities
- `race/racing-line-display`: which part of the racing line a racer is shown, how it is drawn, how often it is redrawn,
  and how far from the racer it is sent. It owns the policy, not the course's shape, which `RacingLine` already owns.

### Modified Capabilities
- `catalog/map-file-format`: three optional `guideLine` fields, their defaults and their refusal rules. The required
  fields and the loading of existing files do not change.

## Impact

- **voyager-api** (`net.elytrarace.voyager.api.race`): `GuideLine` gains the style; new enums `GuideLineMode` and
  `GuideParticle`; `InvalidGuideLineException` gains factories for the two new refusals.
- **voyager-race** (`net.elytrarace.voyager.race.line`): no change. The racer's stretch uses the existing
  `RacingLine.between`.
- **voyager-platform**: `render.GuideLineRenderer` reads the map's style, applies the distance cutoff and the per-particle
  refresh interval; `catalog.adapter.MapDefinitionAdapter` and `MapDraftAdapter` read and write the optional fields;
  `map.schema.json` lists them as optional properties.
- **voyager-setup**: the draft round trip keeps the new fields, so a builder who opens and saves a draft does not lose
  them (`DraftEditor`, `MapDraftJsonWriter`).
- **voyager-server**: the shipped map file, `elytraraceblueandred.json`, gets `particleSpacing` 2.0, and its `notes` seed
  text is updated.
- **docs**: `docs/reference/map-and-cup-files.md`, ADR-0030.
- **Dependencies**: none. No new library, no new module, no change to `voyager-fitness`'s module list.
- **Fitness**: `ApiPurityTest`, `NullabilityConventionTest` and `DesignRuleTest` cover the new api types; the
  `GuideLineRenderer` boundary stays inside `platform`. See design.md "Architecture".
