## Why

The first closed alpha needs one playable cup (GitHub issue #120). The rebuild ships one map,
`elytraraceblueandred` (35 rings, world `ElytraraceBlueAndRed`), and a single-map `test_cup` that plays it. The owner
has decided that the alpha starts with that map only: the three maps converted from the tree being replaced
(`nether-sprint`, `frozen-cathedral`, `skyward-drift`) have no worlds on this machine, so they stay out of the shipped
catalogue. Their converter dry run already worked, so they are recorded as converted-ready and blocked only on worlds.

The alpha also has to exercise the ring-effect path before it reaches players. The shipped map has no BOOST ring, so no
shipped race reaches the boost effect. This change gives the map a small number of BOOST rings, chosen from geometry.

## What Changes

- Ship one cup, `alpha_cup` (display name "Alpha Cup", recorded in its `notes`), that plays the single map
  `elytraraceblueandred`. The cup file is `voyager-server/src/main/resources/cups/alpha_cup.json`.
- **BREAKING (developer workflow, owner-approved):** remove `voyager-server/src/main/resources/cups/test_cup.json`, so the
  shipped catalogue holds exactly one cup. Two cup files with no cup named make boot refuse as ambiguous, so the dev run
  would need `-Pcup`; with one cup, boot needs no `-Pcup`. The shipped cup's name changes from `test_cup` to `alpha_cup`.
- Set three rings of `elytraraceblueandred.json` to `type: BOOST` (indices 0, 18 and 27). The choice is made from the
  map's geometry, not by taste, and is provisional until the playtest (design.md, Decision 3).
- Add `ShippedCatalogueTest` in `voyager-server`, which loads the shipped data directory through the real catalogue and
  asserts one cup named `alpha_cup`, the single map it plays, at least eight rings, at least two BOOST rings, and a clean
  load.
- Update the tests that name the shipped cup (`ConfigCheckRunTest`, `VoyagerGraphTest`, `TickPipelineOrderTest`,
  `VoyagerStartupTest`, the `ShippedCatalogue` helper) and the committed-data assertions in `CommittedMapDataTest` that
  pinned all rings to `STANDARD` and the cup to `test_cup`.
- Document the shipped cup, its BOOST rings and the converted-ready maps in `docs/migration/status.md`.

No converter change and no converter run are needed: the map already exists in the rebuild format. The three converted
maps are not added as data files.

## Capabilities

### New Capabilities
- `catalog/shipped-catalogue`: the content the server boots with by default: which cups ship, the map each cup plays,
  and what the shipped map states (spawn, boost tuning, ring count, ring types, clean load).

### Modified Capabilities
- None. `catalog/catalog-loading`, `catalog/map-file-format` and `cup-validation` keep their requirements. This change
  only supplies data that they already govern; `BOOST` is an existing `RingType` with an existing effect.

## Commit and PR

- Type and scope: `feat(content)` for the shipped data, the BOOST ring types and their tests, which land in one commit.
  The documentation is a separate `docs(migration)` commit, as the Conventional Commits rule requires one type per commit.
- PR title: `feat(content): ship the alpha cup with one playable map`. Pull requests are squash-merged, so this title is
  the commit that lands on main. The owner merges locally; no pull request is opened by the agent.
- Vertical slice: the `catalog` slice's shipped data (read by `voyager-platform`'s loader) and the composition root's
  resources and tests in `voyager-server`. No `race` or `cup` logic changes.

## Impact

- Affected files: `voyager-server/src/main/resources/cups/` (`alpha_cup.json` added, `test_cup.json` removed),
  `voyager-server/src/main/resources/maps/elytraraceblueandred.json` (three ring types), `voyager-server/src/test/java/...`
  (one new test class, updated fixtures), `docs/migration/status.md`.
- Run directories: `prepareRunData` uses sync semantics, so the removal also clears `run/run/data/cups/test_cup.json` on
  the next run.
- Not affected: `tools/map-converter`, `voyager-platform`, `voyager-race`, `voyager-api`, the old tree (`server/`,
  `plugins/*`), and the golden masters (design.md, Decision 6).
- Dependencies: the world `ElytraraceBlueAndRed` under the worlds path. It is present in the main checkout's
  `run/run/worlds` and is passed to `validateCatalog` with `-PworldsPath`. Without it the deep world check cannot run.

## Out of scope

- Converting, shipping or placing the three old maps (`nether-sprint`, `frozen-cathedral`, `skyward-drift`). They are
  converted-ready and blocked on worlds and spawns; a follow-up note in docs records them (no data files).
- Checkpoints. The owner decided that a reset after landing or out of bounds goes to the last passed ring, so the
  alpha needs no checkpoint rings.
- Display names and authors in the rebuild's definitions. `CupDefinition` and `MapDefinition` carry no such field; a
  schema change is a separate change.
- Boost multiplier and burn values. The BOOST multiplier (1.5) is a code constant in `RingEffectRegistry`; the burn and
  cooldown are the map's `boostConfig`. Both are balancing work for after the playtest.
- Converter changes, including the `maxSpeedBlocksPerTick` value the converter drops (see design.md).
- The two-player playtest itself (owner-run).
- Removing the old tree.
