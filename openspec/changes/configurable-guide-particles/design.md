# Design

## Context: what the catalogue and the renderer already do

- The map file accepts `guideLine.particle` (`END_ROD` or `DUST`) and `guideLine.dustColor` (`#RRGGBB`); `dustColor` is refused
  unless the particle is `DUST` (`voyager/platform/src/main/resources/schema/map.schema.json:157-165`; `catalog/map-file-format`).
- The domain type is `GuideParticle` with two values (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/GuideParticle.java:13-17`).
  The default colour is `0xFFC800` (`GuideLine.java:59`). A map that names no particle gets END_ROD (`MapDefinitionAdapter.java:137`).
- The renderer maps the two values to Minestom particles (`render/GuideLineRenderer.java:209-212`). DUST is drawn at scale 1.0
  (`:94`), refreshed every 4 ticks (`:85`), and END_ROD every 6 ticks (`:79`, `END_ROD_REFRESH_TICKS`).
- The shipped map files name neither field. Each has `lookAheadRings` and `particleSpacing` only, so every shipped map is drawn
  as END_ROD, a white sparkle.
- The setup server writes both fields when it saves a map (`catalog/writer/MapDraftJsonWriter.java:113-117`), so saving does not
  drop them today.

## Corrected premise: the fields already exist

The owner asked for the particle fields to be part of schema version 2, if version 2 has not shipped. The fields are not new.
They are version 1 fields, shipped in the current format, and they keep their meaning in version 2. So nothing this change
needs has to be put into version 2, and nothing forces a version 3.

What this change does change is the data (which maps name a particle) and, if the owner wants one, the list of values.

## Schema version

| Case | Version this change needs |
|---|---|
| Shipped maps name `END_ROD` or `DUST` and a colour (this change, as proposed) | None: version 1 fields, unchanged. |
| A new particle value is added (for example `ELECTRIC_SPARK`) | The value must be declared by a file that uses it, under the version that introduces it: **version 2** if `add-catalogue-keys-and-authors` has not shipped its code when this change's code pull request opens, otherwise **version 3**. A version 1 file that names the new value is refused, naming `guideLine.particle` and the version it needs, as `catalog/file-versioning` requires for a newer schema. |

The version 2 question is therefore not blocking. It only matters if the owner adds a particle value, and the owner decides that
in open question 1.

### Dependency on `add-catalogue-keys-and-authors`

That change rewrites the four shipped map files to version 2 and renames their ids to keys (`elytrarace:palm_beach`, and so on).
This change edits the same files. Its code pull request is therefore rebased after that one lands. The edit is three lines per
map (`particle`, and `dustColor` where DUST is chosen), and it does not touch the rewritten keys.

## Evaluation

### Method

Visibility was assessed on the nominal colours, not in a client. A guide particle is a small sprite, and its rendered colour
depends on the client's lighting and on the sprite's own transparency. So this is a first estimate. It is good enough to rank
candidates and to choose which ones to test in the playtest. It is not a verdict.

The measure is the WCAG 2.x contrast ratio between the particle's colour and eight representative ground tones: bright sky
`#8EC9F5`, sand `#E8D6A0`, snow `#F2F6FA`, grass `#6A9E3F`, stone `#7D7D7D`, night `#1A1A1A`, deep water `#1E4D7A` and dark
foliage `#2E4A2A`. The threshold for graphical objects in WCAG 1.4.11 is 3:1. The background tones are not measured from the
three maps. The worlds are read-only under `run/`, and the owner's maps are what the playtest checks, so the tones here stand
for the kinds of ground, not for the maps.

The calculation is a short script kept outside the repository. Its inputs are the colours in the tables below, so it can be
rerun from them.

### Candidates and results

| Candidate | Colour (nominal) | Min on bright (sky, sand, snow) | Min on dark (night, deep water, foliage) | Min over all eight | Verdict |
|---|---|---|---|---|---|
| END_ROD (today's default) | white `#FFFFFF` | 1.09 | 8.76 | 1.09 | Good on dark and mid ground, lost on bright and snow. |
| DUST, amber (the DUST default colour) | `#FFC800` | 1.08 | 5.64 | 1.08 | Like END_ROD but weaker on bright ground. Not a fix. |
| DUST, deep violet | `#7A1FB8` | **4.35** | 1.13 | 1.13 | Clear on bright ground (sky 4.35, sand 5.36, snow 7.12). Lost on dark ground. |
| ELECTRIC_SPARK | pale yellow `#FFF27A`, assumed | 1.06 | 7.60 | 1.06 | Same weakness as END_ROD on bright ground. Needs a new enum value. |
| TRAIL | (colour per particle) | not rated | not rated | not rated | Rejected for the line (see below). |

Extra colours measured for the record (not candidates): hot pink `#FF4FA0` min bright 1.72; magenta `#FF2BD6` min bright 1.80;
cyan `#00E5FF` min bright 1.07. Mid tones (grass and stone) are the best case for END_ROD (3.19 and 4.12) and the worst for
the violet (2.42 and 1.88). No single colour is above 3:1 on every ground, so the choice is per map.

### Why TRAIL is rejected for the line

`Particle.TRAIL` is a particle that travels from its origin to a target position over a duration, in a colour
(`net.minestom.server.particle.Particle$Trail`, the `TRAIL` constant in `Particles`, Minestom 2026.09.12-26.2). A racing line
needs a steady sequence of stationary points. A trail per point needs a target per point, which doubles the packets, and it draws
a moving dot where the line needs a fixed mark. It answers a different need, such as a highlight that moves to the next ring.

### Other values in the same Minestom version (not evaluated)

`GLOW`, `FIREFLY` and `DUST_COLOR_TRANSITION` also exist in the pinned Minestom. They are not evaluated here, because the owner
asked for three or four and the table already covers the decision. They are candidates for a later pass, and they would need the
same schema decision as ELECTRIC_SPARK.

### Cost

DUST is redrawn every four ticks and END_ROD every six (`GuideLineRenderer.java:79`, `:85`). At the same spacing, a DUST line
sends about 1.5 times the packets per second of an END_ROD line (5 redraws a second against 3.3). The budget in
`race/racing-line-display` ("Budget is stated for the shipped course") must be checked after the shipped maps change, in the code
pull request.

## Recommendation

1. **Default: END_ROD, unchanged.** It is the best candidate on dark and mid-tone ground, and changing it would change every map
   that names nothing, with no evidence that it helps.
2. **Bright maps: DUST in `#7A1FB8`.** It is the only candidate above 3:1 on bright ground, and it keeps the sprite's size.
3. **Dark and mid maps: END_ROD, or DUST in the amber default** if the owner wants a coloured line on a dark map.
4. **Which maps are bright is the owner's call.** The shipped maps are not assigned a particle in this document by guesswork.
   Open question 2 asks the owner to describe the background of each map, and task 4.2 confirms each choice in the client.

## Architecture

The change is data and a test. It adds no type and no module boundary.

| Change | Module | Location |
|---|---|---|
| `particle` and `dustColor` in the shipped maps | `voyager-server` (resources) | `src/main/resources/maps/*.json` |
| Setup writer keeps the two fields (test) | `voyager-platform` | `catalog/writer` (`MapDraftJsonWriter`) |
| Evaluation record | repository | `docs/research/` |

No fitness rule is affected, because no code boundary changes.

## Open questions

1. **A new particle value.** Does the owner want ELECTRIC_SPARK, GLOW, FIREFLY or DUST_COLOR_TRANSITION added? If yes, it is a
   separate change with a schema version (see "Schema version"). Recommendation: no, until the playtest shows that the violet and
   the white are not enough.
2. **The background of each shipped map.** Which of palmbeach, dragonlobby and memoria is bright, mid or dark? The owner's
   description decides the choice in the table above. Recommendation: the owner describes them, and the playtest confirms.
3. **Amend `add-catalogue-keys-and-authors`?** The owner asked that the fields be part of v2. They are already v1 fields, so no
   amendment is needed for the fields. An amendment would be needed only for question 1.
4. **The evaluation record.** The table is a first estimate on nominal colours. The owner decides whether the playtest result
   replaces it in `docs/research/`, or is added beside it.
