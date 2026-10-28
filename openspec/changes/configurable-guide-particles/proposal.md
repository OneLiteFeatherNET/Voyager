# Proposal

**Type and scope:** `feat(catalog)`. Ships as one code change under this title. It sets which guide particle and colour each
shipped map names. It is a catalogue change, not a race change, so it is a third change and not part of `competitive-race-feedback`
(`feat(race)`). The renderer already draws what the catalogue names.

**Depends on:** `add-catalogue-keys-and-authors` (`feat(catalog)`, schema version 2, code not yet merged). That change renames the
shipped maps to keys and rewrites their files to version 2. This change edits the same files, so its code pull request is rebased on
that one and uses the keys. This change adds no field and no particle value, so it needs neither version 2 nor version 3 for itself.

## Why

The owner's playtest of the three-map `legacy_cup` found that the white guide line works on some maps and not on others. The line is
drawn with END_ROD, a white sparkle, which is the default. White is almost invisible on a bright map and clear on a dark one.

The catalogue already lets a map choose. The map file takes `particle` (`END_ROD` or `DUST`) and `dustColor` (`#RRGGBB`) in
`guideLine` (`voyager/platform/src/main/resources/schema/map.schema.json:157-165`, from ADR-0030 and the `catalog/map-file-format`
requirement "The guide line may name its mode, particle and colour"). The shipped maps name neither, so every shipped map is drawn in
white.

## What Changes

- **palm_beach is drawn in deep violet.** The map names `DUST` and `#7A1FB8`. The owner chose this for the bright map.
- **Every other shipped map names END_ROD explicitly.** dragon_lobby, blue_and_red and memoria keep the white line, now stated in their
  files instead of left to the default.
- **The default stays END_ROD.** A map that names nothing is drawn as today.
- **The setup server keeps the choice.** Saving a map keeps `particle` and `dustColor`. The writer already writes them
  (`MapDraftJsonWriter.java:113-117`), and a test pins it.
- **The evaluation is recorded** in `docs/research/`, with the method, the table and the playtest result.

No particle value is added. The renderer's mapping is unchanged (`GuideLineRenderer.java:209-212`).

## The shipped maps, by key

| File today | Key after `add-catalogue-keys-and-authors` | Particle |
|---|---|---|
| `palmbeach` | `elytrarace:palm_beach` | `DUST`, `#7A1FB8` |
| `dragonlobby` | `elytrarace:dragon_lobby` | `END_ROD` |
| `elytraraceblueandred` | `elytrarace:blue_and_red` | `END_ROD` |
| `memoria` | `elytrarace:memoria` | `END_ROD` |

The file names in the table are the ones on `origin/main` today. The rebase uses the key column, and the code PR checks that the
renamed files are the ones it edits.

## Capabilities

### New Capabilities

- `catalog/guide-particles`: the shipped maps name their particle and colour, the default for a map that names none, and the setup
  server's keeping of the two fields.

### Modified Capabilities

None. `catalog/map-file-format` already defines the fields, and this change relies on them as written.

## Out of scope

- A new particle value (ELECTRIC_SPARK, GLOW, FIREFLY or DUST_COLOR_TRANSITION). The evaluation rejects TRAIL for the line and rates
  ELECTRIC_SPARK no better than END_ROD on bright ground. A new value would be a separate change with a schema version.
- The refresh rates and the reach of the line.
- The guide line's shape, look-ahead and spacing.
- Any change to the HUD or to messages.
