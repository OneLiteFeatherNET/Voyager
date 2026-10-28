# Proposal

**Type and scope:** `feat(catalog)`. Ships as one code change under this title. It changes which guide particle and colour
each shipped map names, and it pins the catalogue's default. It is a catalogue change, not a race change, so it is a third
change and not part of `competitive-race-feedback` (`feat(race)`). The renderer already draws what the catalogue names.

**Depends on:** `add-catalogue-keys-and-authors` (`feat(catalog)`, schema version 2, code not yet merged). That change rewrites
the same shipped map files to version 2 and renames their ids to keys. This change's data edit is made after that rewrite, so
its code pull request is rebased on it. This change adds no new field and no new particle value, so it does not need version 2
or version 3 itself. See the design, "Schema version".

## Why

The owner's playtest of the three-map `legacy_cup` found that the white guide line works on some maps and not on others. The
line is drawn with END_ROD, a white sparkle, which is the default. White is almost invisible on a bright map, and it is clear
on a dark one.

The catalogue already lets a map choose. The map file takes `particle` (`END_ROD` or `DUST`) and `dustColor`
(`#RRGGBB`) in `guideLine` (`voyager/platform/src/main/resources/schema/map.schema.json:157-165`, from ADR-0030 and the
`catalog/map-file-format` requirement "The guide line may name its mode, particle and colour"). The shipped maps name neither
(each has only `lookAheadRings` and `particleSpacing`), so every shipped map is drawn in white. The owner's request is therefore
mostly a data decision, backed by a visibility evaluation the repository did not have.

## What Changes

- **Shipped maps name their particle.** Each shipped map states `particle` and, for `DUST`, a `dustColor` chosen against the
  map's background, so that no shipped map relies on the default.
- **The default stays END_ROD.** A map that names nothing is drawn as today. The evaluation below shows END_ROD is the best of
  the four candidates on dark and mid-tone ground, so the default is not changed.
- **The setup server keeps the choice.** Saving a map in the setup server keeps `particle` and `dustColor`. The writer already
  writes them (`MapDraftJsonWriter.java:113-117`), and a test pins it, so that a later change cannot drop them silently.
- **The evaluation is recorded** in `docs/research/`, with the table and the method, and the playtest matrix.

No particle value is added in this change. The renderer's mapping is unchanged (`GuideLineRenderer.java:209-212`).

## Capabilities

### New Capabilities

- `catalog/guide-particles`: the shipped maps name their particle and colour, the default for a map that names none, and the
  setup server's keeping of the two fields.

### Modified Capabilities

None. `catalog/map-file-format` already defines the fields and their refusals, and this change relies on them as written.

## Out of scope

- A new particle value (ELECTRIC_SPARK, GLOW, FIREFLY or TRAIL). The evaluation rejects TRAIL for the line and rates
  ELECTRIC_SPARK no better than END_ROD on bright ground. If the owner wants one anyway, it is a new change with a schema
  version (`design.md`, "Schema version").
- The refresh rates and the reach of the line (`GuideLineRenderer`). They belong to the renderer and do not change here.
- The guide line's shape, its look-ahead and its spacing.
- Any visible change to the racer's HUD or messages.

## Impact

- `voyager/server/src/main/resources/maps/*.json`: the shipped maps gain `particle` and, where DUST is chosen, `dustColor`.
  The shipped maps are the four files in that directory; `add-catalogue-keys-and-authors` changes them first.
- `voyager/platform`: a test for the setup writer's keeping of the two fields. No production code, unless the test finds the
  writer drops them.
- `docs/research/`: the evaluation. `docs/reference/`: the guide line's options for a map designer.
