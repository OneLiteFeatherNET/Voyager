# Design

## Context: what the catalogue and the renderer already do

- The map file accepts `guideLine.particle` (`END_ROD` or `DUST`) and `guideLine.dustColor` (`#RRGGBB`). `dustColor` is refused
  unless the particle is `DUST` (`voyager/platform/src/main/resources/schema/map.schema.json:157-165`; `catalog/map-file-format`).
- The domain type `GuideParticle` has two values (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/GuideParticle.java:13-17`).
  The default colour is `0xFFC800` (`GuideLine.java:59`). A map that names no particle gets END_ROD (`MapDefinitionAdapter.java:137`).
- The renderer maps the two values to Minestom particles (`render/GuideLineRenderer.java:209-212`). DUST is drawn at scale 1.0 (`:94`),
  refreshed every 4 ticks (`:85`), and END_ROD every 6 ticks (`:79`).
- The shipped map files name neither field, so every shipped map is drawn as END_ROD.
- The setup server writes both fields when it saves a map (`catalog/writer/MapDraftJsonWriter.java:113-117`).

## Corrected premise: the fields already exist

The fields are not new. They are version 1 fields, shipped in the current format, and they keep their meaning in version 2. Nothing
this change needs has to be put into version 2 or version 3.

## Schema version

| Case | Version this change needs |
|---|---|
| Shipped maps name `END_ROD` or `DUST` and a colour (as proposed) | None: version 1 fields, unchanged. |
| A new particle value is added later (for example `ELECTRIC_SPARK`) | The value is declared under the version that introduces it: version 2 if `add-catalogue-keys-and-authors` has not shipped its code when that change's code pull request opens, otherwise version 3. A version 1 file that names it is refused, naming `guideLine.particle` and the version it needs. |

## Dependency on `add-catalogue-keys-and-authors`

That change renames the four shipped maps to keys (`palm_beach`, `dragon_lobby`, `blue_and_red`, `memoria`) and rewrites their
files to version 2. This change edits the same files. Its code pull request is rebased after that one lands, and it uses the keys.
Each edit is two or three lines per map, and it does not touch the rewritten keys. The code PR checks that the renamed file is the one
it edits, by its key.

## Evaluation

### Method

Visibility was assessed on the nominal colours, not in a client. A guide particle is a small sprite whose rendered colour depends on
client lighting and the sprite's transparency. So this is a first estimate, good enough to rank candidates and choose what the playtest
tests, but not a verdict.

The measure is the WCAG 2.x contrast ratio between the particle's colour and eight representative ground tones: bright sky `#8EC9F5`,
sand `#E8D6A0`, snow `#F2F6FA`, grass `#6A9E3F`, stone `#7D7D7D`, night `#1A1A1A`, deep water `#1E4D7A` and dark foliage `#2E4A2A`. WCAG 1.4.11
sets 3:1 as the threshold for graphical objects. The tones are not measured from the three maps: the worlds are read-only under `run/`.
The tones stand for kinds of ground. The playtest checks the maps.

The calculation is a short script kept outside the repository. Its inputs are the colours below.

### Candidates

| Candidate | Colour (nominal) | Min on bright (sky, sand, snow) | Min on dark (night, deep water, foliage) | Min over all eight | Verdict |
|---|---|---|---|---|---|
| END_ROD (today's default) | white `#FFFFFF` | 1.09 | 8.76 | 1.09 | Good on dark and mid ground; lost on bright ground and snow. |
| DUST, amber (the DUST default colour) | `#FFC800` | 1.08 | 5.64 | 1.08 | Like END_ROD, weaker on bright ground. Not a fix. |
| DUST, deep violet | `#7A1FB8` | **4.35** | 1.13 | 1.13 | Clear on bright ground (sky 4.35, sand 5.36, snow 7.12). Lost on dark ground. |
| ELECTRIC_SPARK | pale yellow `#FFF27A`, assumed | 1.06 | 7.60 | 1.06 | Same weakness as END_ROD on bright ground. Needs a new enum value. |
| TRAIL | (per particle) | not rated | not rated | not rated | Rejected for a line (below). |

Extra colours measured for the record: hot pink `#FF4FA0` min bright 1.72; magenta `#FF2BD6` min bright 1.80; cyan `#00E5FF` min bright 1.07.
Mid tones are END_ROD's best case (grass 3.19, stone 4.12) and the violet's worst (2.42, 1.88). No single colour is above 3:1 on every ground,
so the choice is per map.

### Why TRAIL is rejected for the line

`Particle.TRAIL` travels from its origin to a target position over a duration, in a colour (`Particle$Trail` in Minestom 2026.09.12-26.2).
A racing line needs a steady sequence of stationary points. A trail per point needs a target per point, which doubles the packets, and draws
a moving dot where the line needs a fixed mark.

### Other values in the pinned Minestom (not evaluated)

`GLOW`, `FIREFLY` and `DUST_COLOR_TRANSITION` also exist. They are not evaluated here, because the table already decides the question. They
would need the same schema decision as ELECTRIC_SPARK.

### Cost

DUST is redrawn every four ticks and END_ROD every six (`GuideLineRenderer.java:79`, `:85`). At the same spacing a DUST line sends about 1.5
times the packets per second of an END_ROD line. The racing line's budget (`race/racing-line-display`, "Budget is stated for the shipped course")
is checked after the change, in the code PR.

## Recommendation, as decided

- **palm_beach: DUST `#7A1FB8`.** The owner's decision for the bright map. It is the only candidate above 3:1 on bright ground.
- **dragon_lobby, blue_and_red, memoria: END_ROD, named explicitly.** The owner's decision. END_ROD is the best candidate on dark and mid
  ground.
- **The default stays END_ROD.** It is unchanged for any map that names nothing.

## Architecture

The change is data and tests. It adds no type and no module boundary.

| Change | Module | Location |
|---|---|---|
| `particle` and `dustColor` in the four shipped maps | `voyager-server` (resources) | `src/main/resources/maps/*.json` |
| Setup writer keeps the two fields (test) | `voyager-platform` | `catalog/writer` (`MapDraftJsonWriter`) |
| Evaluation record | repository | `docs/research/` |

No fitness rule is affected.

## Open questions

1. **A new particle value.** Does the owner want ELECTRIC_SPARK, GLOW, FIREFLY or DUST_COLOR_TRANSITION? If yes, it is a separate change with a schema
   version. Recommendation: no, until the playtest shows the violet and the white are not enough.
2. **The playtest.** The owner confirms palm_beach in the client and records the other three maps' verdicts (task 4.2).
