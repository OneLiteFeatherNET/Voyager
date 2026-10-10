# Design

## Context

See proposal.md, Why. The rebuild's catalogue is read from `voyager-server/src/main/resources/{maps,cups}`, copied to
`run/run/data` by `prepareRunData`. A cup names maps by name. With more than one cup file and no cup named, boot refuses
as ambiguous (cup-validation). The worlds are read from the worlds path (`run/run/worlds` by default), and
`validateCatalog` runs the deep world check on each world a map names.

Findings (read-only investigation, plus the converter dry run from the earlier planning pass):

- **The shipped map.** `elytraraceblueandred.json` has 35 rings indexed 0..34, all `STANDARD`, 10 points each, spawn
  `(109, -62, 54)` read from `level.dat`, `boostConfig` burn 30 and cooldown 40 ticks, and 16 guide points. Its world is
  `ElytraraceBlueAndRed`.
- **The world is on the main checkout only.** `run/run/worlds/ElytraraceBlueAndRed` exists in the main checkout
  (gitignored). The worktree has no `run/` world. `validateCatalog` reads `-PworldsPath` (the server's
  `VOYAGER_WORLDS_PATH`), so the worktree points at the main checkout's worlds directory and copies nothing into git.
- **The three old maps are converted-ready, and blocked.** The earlier dry run with `tools/map-converter` produced
  `nether_sprint` (11 rings), `frozen_cathedral` (12) and `skyward_drift` (9), and the cup `alpha_cup` from them. The
  worlds are missing everywhere on this machine, and the spawns come from each world's `level.dat`. The owner has
  decided these stay out of the shipped catalogue. Their converted output is not committed.
- **Cup selection.** With two cup files and no `-Pcup` (`VOYAGER_CUP`), `CupResolution` refuses boot as ambiguous. With
  `alpha_cup` the only shipped cup, the default run needs no switch.
- **Ring effects are already modelled.** `RingType.BOOST` exists, and `RingEffectRegistry` maps it to a
  `SpeedMultiplierEffect` with a fixed multiplier of 1.5. A ring's type is its only per-ring data: the schema has no
  per-ring effect config. So placing BOOST rings needs no schema or data-model change, and nothing has to be stopped
  and reported.
- **Golden master.** `CupSessionGoldenMasterTest` builds its catalogue in memory (cups `grand_tour` and
  `grand_tour_two`, maps `ridge-run` and `dune-run`). It does not read `src/main/resources`, so changing shipped ring
  types or the shipped cup cannot change its golden files. Task 2.4 verifies this.
- **Tests that pin the shipped data.** `CommittedMapDataTest` pins every ring to `STANDARD` and the cup to `test_cup`.
  Several server tests use `ShippedCatalogue`, which copies `cups/test_cup.json` from the classpath, and pass
  `"test_cup"` as the played cup. Those change to `alpha_cup`. `CupBootValidationTest` and `BootRefusalTest` write their
  own `test_cup` fixtures and keep their name.
- **Dropped data.** `maxSpeedBlocksPerTick` (3.2 to 3.6 in the old files) has no field in `BoostConfig`, which carries
  burn and cooldown only. The old map's display name, author and the old cup's "First Flight Cup" have no field in the
  rebuild's definitions.

## Goals / Non-Goals

**Goals:**
- Ship one cup, `alpha_cup`, playing the one map `elytraraceblueandred`. It passes `validateCatalog` against the
  worlds path and boots without `-Pcup`.
- Exercise the BOOST ring path in the alpha with a small number of rings, chosen from geometry and marked provisional.
- Give the shipped catalogue a test that states the bar in code (Red first).

**Non-Goals:**
- Converting or shipping the three old maps; converter changes; checkpoints; display names; balancing the boost
  multiplier or burn (see proposal.md, Out of scope).

## Decisions

**1. Ship the existing rebuild map; do not convert.** The alpha's one map is already in the rebuild format, and it is the
course the owner has on this machine. Converting the old maps would add data the owner cannot yet play. The converted
output for the three old maps is not committed; its dry-run numbers are recorded in docs (task 4.1) as the follow-up.

**2. Replace `test_cup` with `alpha_cup` (owner-approved).** `alpha_cup` becomes the only cup, so the default run needs
no `-Pcup`. Keeping both cups would need `-Pcup=alpha_cup` on every dev run. The old `test_cup` is recoverable with one
`git revert` of the data commit.

**3. Choose the BOOST rings from geometry: the three longest straight segments.** The rule, applied to the map as
committed:
- A segment is the centre-to-centre run from ring `i` to ring `i+1`, for `i` in `0..33`. Ring 34 starts no segment, so it
  cannot be a BOOST ring by this rule (the "not the last ring" rule).
- A segment counts as straight only when no guide point lies in its gap (`orderIndex` strictly between `100*i` and
  `100*(i+1)`). A guide point bends the drawn racing line off the direct run, so a guided segment is not straight.
- Rank the straight segments by length, descending, and take those above the largest gap in the ranking.

Result (lengths in blocks, from the committed centres):

| Ring | Segment | Length | Guide in gap |
|---|---|---|---|
| 27 | 27 to 28 | 96.99 | none |
| 0 | 0 to 1 | 87.42 | none |
| 18 | 18 to 19 | 72.95 | none |
| 22 | 22 to 23 | 56.80 | none |
| 17 | 17 to 18 | 51.09 | none |

The largest gap in the straight ranking is between 72.95 and 56.80 (16.2 blocks; the next-largest gap is 14.5, between
87.42 and 72.95), so the rings are **0, 18 and 27**: three BOOST rings, within the 2 to 4 the owner set.

Rings rejected by the straight rule, for the record: ring 2 (segment 99.24, guide 250) and ring 1 (87.62, guide 150) are
the two longest segments overall. Their gaps carry a guide point, so the drawn line is not straight there; the owner's
rule is applied with the straight test, and this is the one place it departs from a bare "longest segment" reading.

**4. Keep the BOOST effect and the boost tuning as they are.** The BOOST ring multiplier (1.5) is the code constant in
`RingEffectRegistry`; the map's `boostConfig` (burn 30, cooldown 40 ticks) is the rocket tuning already in the file. The
alpha changes only the ring types. Changing the multiplier is a `feat(race)` change, not this one.

**5. Mark every choice provisional in the file.** The map's `notes` gain a line that names rings 0, 18 and 27 as
provisional BOOST rings, says how they were chosen, and says "tune after playtest". The cup's `notes` record the display
name "Alpha Cup" and that the cup plays one map.

**6. Keep the golden masters untouched.** They read an in-memory catalogue (Context, golden master finding). The change
cannot move their files, and task 2.4 checks that no file under `voyager-server/src/test/resources/golden` changes. If a
golden master did change, the change stops and reports instead of regenerating.

**7. Reset goes to the last passed ring; no checkpoints.** The owner decided this for the parallel out-of-bounds change.
The alpha map therefore needs no checkpoint rings, and none are added.

## Dependency direction and module placement

The shipped data lives in `voyager-server/src/main/resources`, the composition root, and is read by `voyager-platform`'s
`CatalogLoader` through `voyager-platform` adapters. The new test is in `voyager-server` and reads through
`CatalogLoader.load`, so it depends inward on the catalogue and does not reach into `race`. `voyager-fitness` needs no new
rule: no new boundary is introduced, and its coverage check already names `voyager-server`.

## Risks / Trade-offs

- [The worlds path must be given] → `validateCatalog` needs `-PworldsPath=<main checkout>/run/run/worlds` in this
  worktree, because the worktree has no world. Mitigation: the command is recorded in the status note (task 4.1). The
  deep IT (`CatalogValidationDeepIT`) skips when the world is absent and is not the acceptance check here.
- [`test_cup` removal breaks the dev workflow] → Mitigation: owner approval; the revert is one commit.
- [BOOST rings change play] → racers get a 1.5x speed multiplier at three rings, which is what the alpha is meant to
  exercise. Mitigation: the rings are provisional, and the playtest (task 5.1) is the first measurement.
- [Dropped `maxSpeedBlocksPerTick`] → the alpha boost speed comes from the rebuild's model. If the old value matters, a
  later change adds it to `BoostConfig` (a `feat(race)` change).
- [Stale run directory] → the next `prepareRunData` removes `run/run/data/cups/test_cup.json` (sync semantics).

## Migration Plan

1. Commit the revised planning artifacts (`docs(openspec): propose add-alpha-cup-content`).
2. Write `ShippedCatalogueTest`, run it and see it fail (Red).
3. Add `alpha_cup.json`, set the three BOOST rings, remove `test_cup.json`, update the named fixtures and
   `CommittedMapDataTest` (Green). Run the tests.
4. Run `validateCatalog` with `-PworldsPath` pointing at the main checkout's worlds, then the full build and the fitness
   suite with `CI=true`.
5. Commit `feat(content)` (data and tests) and `docs(migration)` (status note).

Rollback: `git revert` of the `feat(content)` commit restores `test_cup` and the single-type ring data.

## Open Questions

- Should the old three maps be shipped once their worlds arrive? That needs the owner's worlds and their `level.dat`
  spawns; it is a follow-up change, and its docs note records the converter dry-run numbers (11, 12 and 9 rings).
- Should `maxSpeedBlocksPerTick` return to `BoostConfig`? It is dropped on purpose for now; the playtest decides.
- Should the BOOST multiplier be per map instead of a code constant? That is a schema and `race` change, separate from
  this alpha.
