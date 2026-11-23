# Proposal

**Type and scope:** `feat(catalog)`. The change is content and calibration: map files, one cup file and the notes that describe
them. It touches the catalogue slice (`catalog`, `voyager/server/src/main/resources/maps`, `cups`) and the process that measures
reference times. It changes no engine code. Ships as one code change under the title
`feat(catalog): calibrate the shipped catalogue from human runs`. Breaking: no engine change, but existing standings and medal
tiers on the shipped maps change once the reference times move, which the owner must accept (see Trade-offs). Depends on
`start-solo-practice`, which adds the first practice cup and changes the cup-count requirement this change also edits.

## Why

The shipped content is a first draft, and the medals, the boost economy and the cup arc all depend on numbers that were guessed.
The evidence is in the map files and the engine:

- The alpha cup plays one map (`voyager/server/src/main/resources/cups/alpha_cup.json:7-9`). The three maps converted from the tree
  being replaced stay out until their worlds and spawns exist (`alpha_cup.json:11` notes), so there is no cup arc.
- Three maps carry reference times that are guesses. `palm_beach.json:432` says "referenceTimeSeconds 80.0 is a guess",
  `dragon_lobby.json:495` says 115.0 is a guess, and `memoria.json:447` says 85.0 is a guess. Each note derives the guess from path
  length divided by 20 blocks per second.
- The alpha map's reference time is a seed: `blue_and_red.json:15` is 60.0 and the note at `blue_and_red.json:720` says
  "PROVISIONAL seed of 60.0 s, not a measurement".
- Medal tiers are computed from those times with multipliers 1.00, 1.10, 1.25 and 1.50 (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/MedalBrackets.java:15`).
  A guessed reference time therefore decides the medal a racer sees.
- Every ring of the alpha map is worth 10 points (35 `"points": 10` entries in `blue_and_red.json`, with the note at `:721`
  calling it a seed). No shipped map uses a SLOW ring (zero `"SLOW"` entries in `voyager/server/src/main/resources/maps/`), and
  no shipped map uses an oval ring (zero matches for "oval"). BOOST appears on three rings of the alpha map only (`:730`).
- The boost economy does not bite. A map that states no `rocketsPerRacer` keeps the 64-rocket default (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/BoostConfig.java:51-52,65`),
  the alpha map sets a 40-tick cooldown and a 30-tick burn (`blue_and_red.json:16-19`), so rockets cannot run out within a 60 to
  80 second race, and a reset refills them (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/RunResetter.java:68`).
- BOOST multiplies velocity by 1.5 (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/effect/RingEffectTuning.java:19`) and
  no map sets `maxSpeedBlocksPerTick` (zero matches in the maps), so three boosts in a row take a racer from about 1.7 to about
  5.7 blocks per tick (1.7 × 1.5³).
- Two map notes are stale. `palm_beach.json:432`, `dragon_lobby.json:495` and `memoria.json:447` each say "all rings STANDARD",
  while each file has three CHECKPOINT rings (`palm_beach.json:154,282,410` among them). The alpha map does not make that claim;
  it has four CHECKPOINT rings (`blue_and_red.json:282,410,538,666`).

## What Changes

- **Calibration process.** A reference time is replaced by the top-quartile median of at least ten human runs on that map. The sample
  count and the date are written into the map's notes. A map with fewer than ten runs keeps its reference time marked provisional.
- **Provisional until calibrated.** Every seeded value keeps its provisional mark until its calibration record exists.
- **A development cup.** A three-map cup (`elytrarace:dev_cup`) ordered by difficulty (nether_sprint, frozen_cathedral, skyward_drift
  in the proposed order), shipped once those three maps have worlds and spawns. Its arc is the first real rotation of the content.
- **Varied rings.** The alpha map gets narrow rings of 25 points (within the bounds of `race/ring-shape`), one off-path bonus ring in
  place of a line ring so the ring count stays 35, and one SLOW ring in a chicane. The BOOST pair at indices 0, 18 and 27 is kept.
- **Per-map rocket count.** `rocketsPerRacer` is set per map: 4 to 6 for 60 to 80 second maps, 8 for dragon_lobby.
- **Per-map speed cap.** `maxSpeedBlocksPerTick` is set per map from the reference speed, proposed as 1.7 blocks per tick for the
  alpha map. Without a cap, a chain of three BOOST rings reaches about 5.7 blocks per tick. The cap value is confirmed from each
  map's path length divided by its reference time, not assumed.
- **Notes fixed.** The stale "all rings STANDARD" notes are corrected to name the actual ring types.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `catalog/shipped-catalogue`: the cup count gains `dev_cup` once its maps have worlds; the BOOST-ring scenario allows the SLOW ring and
  the bonus ring; the provisional requirement ends at a calibration record; calibration, notes accuracy, and per-map rocket and speed
  settings are added.

## Decisions taken in this proposal

- **No engine change.** Every knob used here already exists in the map format (`rocketsPerRacer`, `maxSpeedBlocksPerTick`, ring
  `points`, ring `type`). The change is data and process.
- **Ten runs, top quartile.** Ten is the smallest sample that makes a median of the top quartile meaningful; the top quartile is
  used because a reference time is the pace of a good run, not of an average one.
- **The alpha map keeps its BOOST pair and its checkpoints.** The existing spec pins them, and changing them is not needed to make
  medals meaningful.

## Trade-offs

### Pros

- Medals become meaningful: a bronze is a pace a human racer can name, not a guess.
- The cup gets a real arc, and the ordering is decided by play rather than by the author's intuition.
- Decisions about boost, rings and rockets are taken on numbers, and the boost economy is visible in the notes.
- The notes stop claiming things the file does not do.

### Cons

- Calibration needs playtests and worlds. Without them, nothing in this change can be measured.
- Recalibrating a reference time changes existing standings and medal tiers. Records already held by racers are not rewritten, but
  new medals are judged against the new time.
- The testers are a biased sample: the people who will play a playtest are the people who already race.
- Reference times drift as players improve. A map needs a recalibration rule, which this change sets as a date-stamped record, not
  a schedule.

## Out of scope

- Engine changes to ring effects, speed, rockets or scoring. Every change here uses existing fields.
- The solo practice cup and the grace window (see `start-solo-practice` and `end-stuck-map-waits`).
- Adding maps beyond the four already in the repository, and creating worlds.
- The checkpoint placement rule of the shipped maps, which stays as the owner decided on 2026-10-13.
- Persisting calibration data in PostgreSQL; the notes are the record for now.

## Open questions for the owner

- **Playtest pool.** Who plays the ten or more runs per map? Options: the owner's friends, the community on Discord, or a timed open
  test. Each gives a different bias.
- **Cap value.** Proposed 1.7 blocks per tick for the alpha map. Confirm per map from path length divided by reference time, or
  accept a single shipped value?
- **Dev cup.** Ship it in the catalogue as soon as the worlds exist, or keep it as a playtest-only cup and out of rotation?
