# ADR-0030: The racing line shows the whole course, with a per-map style and a packet budget

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it, and after the
owner has reviewed the open questions in the change's design.md (Q1 to Q7). The change that implements it is
`full-course-guide-line`. It does not edit ADR-0018 or any D-decision of the greenfield design.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided that the in-game guide line must show the whole course, as the old
  game did, because the current line (a window of two rings, about 90 blocks) is not usable.
- Drafted with the change `full-course-guide-line`. The budget figures are estimates and are replaced by measured
  figures in the change's spike (tasks 1.2 and 7.1).

## Context and problem statement

The racing line is drawn by `GuideLineRenderer` for every racer, every tick, from the ring the racer heads for
`lookAheadRings` rings further on. The shipped course has 35 rings and is about 1,588 blocks long; with two rings of
look-ahead a racer sees about 90 blocks of it, in amber dust of scale 1.0 that is redrawn every four ticks. The owner
finds that the line does not tell a racer enough about the course to be used.

The old game drew the whole spline with END_ROD every five ticks for every player. That is cheap to write and expensive
to send: a particle is one packet per position, so a whole course at one block a particle is about 1,700 packets per
racer per redraw.

`GuideLine`'s Javadoc and the renderer's Javadoc say why the course is drawn as a window ("a ball of string" that does
not say which strand is next), and why colour and particle size are constants rather than map data. The owner has
overruled the first argument for this course. The second argument no longer holds for the particle and colour, because
the owner wants per-map choice.

## Decision drivers

- A racer must see where the course goes, not only the next two rings.
- The packets sent must have a stated bound for the largest race the server runs (sixteen racers, on the shipped course
  and on a longer one), so the cost of a course is known before it is played.
- A map file that loads today must load to the same course. Existing files must not need an edit to load.
- Scoring, ring crossing, boost and physics must not change, and the golden transcripts must stay byte-identical.
- The window must not be lost for courses where the whole line is too much.

## Considered options

1. **Keep the window as the only policy.** Rejected by the owner.
2. **Draw the whole course for everyone, at one block spacing and four ticks.** Rejected: about 136,000 packets per second
   for sixteen racers, roughly 6.8 MB per second on the wire. Over any reasonable budget.
3. **Whole course per map, a per-map style and spacing, a per-particle refresh and a distance cut-off, with the window kept
   as a per-map mode (chosen).** Packets are bounded by the spacing, the refresh and the local density; the shipped course
   at spacing 2.0 with END_ROD every 10 ticks is about 27,200 packets per second for sixteen racers with no cut-off.
4. **A free list of Minecraft particle names in the map file.** Rejected: DUST takes a colour and a scale and the others
   take none, so every name would need its own data type and an unknown name would fail at run time on the server.
5. **Redraw from one shared packet list for the whole instance (`sendGroupedPacket`).** Rejected for now: it sends the far
   part of the course to every racer, which the distance cut-off exists to avoid. Its saving is in serialisation, which
   is small next to the bandwidth.

## Decision outcome

Chosen option: **3**, with these rules.

- A map's `guideLine` may name `mode` (`FULL`, the default, or `WINDOW`), `particle` (`END_ROD`, the default, or `DUST`) and
  `dustColor` (`#RRGGBB`, default `#FFC800`, valid only with `DUST`). All three are optional. A file with none of them
  loads to the same course as before, drawn as a whole line.
- `FULL` draws the whole line from the first ring to the last, including the part already flown. `WINDOW` keeps today's
  policy.
- A racer is sent only the points within 160 blocks of them. The cut-off is a server constant.
- The line is redrawn at most every half of the particle's shortest life: every 4 ticks for DUST, and for END_ROD every
  10 ticks if its shortest life is at least 20 ticks, which the change verifies before the value is fixed.
- The spacing between points, which already exists in the file, is the density. The shipped course proposes 2.0 blocks
  (from 1.0).
- Budget: at most 30,000 particle packets per second in total and 2,000 for any one racer, at sixteen racers on the
  35-ring map.
- No `schemaVersion` change. An older server ignores the new fields, as it ignores every unknown field, and draws the
  line as it did. Nothing scores or places a racer differently, so no server plays a wrong course.

## Consequences

- Good: a racer sees the whole course, as the old game showed it, and can plan the next strand.
- Good: a course's cost is stated by its spacing, refresh and length, and checked by a test against the budget.
- Good: colour and particle are per course, with one default that every course shares.
- Good: the window stays, per map, for a course where the whole line is too much.
- Good: no map file that loads today needs an edit to load, and no ring, score or physics value changes.
- Bad: the whole course is more packets than the window. DUST costs 2.5 times END_ROD at the same spacing, because its
  refresh is four ticks, so a DUST map has to use spacing 4.0 or accept a cut-off.
- Bad: cost grows with course length. A 3,000-block course at spacing 2.0 needs about 48,000 packets per second without a
  cut-off, so the budget must be checked for each new course.
- Bad: the rationale in `GuideLine`'s and `GuideLineRenderer`'s Javadoc is reversed, which is a change of record, not of code.
- Neutral: `lookAheadRings` is still required in a `FULL` file, though it has no effect there. Making it optional would
  change a refusal the owner has not asked to change.
- Neutral: the `map.schema.json` edit conflicts in form with the oval change's byte-identical claim; the two changes are
  coordinated in the design (D7).

## Confirmation

- `GuideLineStyleTest` covers the defaults, the refusals and the three-argument constructor.
- `RacingLineTest` covers the whole line.
- `MapDefinitionAdapterTest`, `MapDraftAdapterTest`, `MapDraftJsonRoundTripTest` and `FileSchemaTest` cover the reading,
  writing and schema of the new fields, and that a file written before them loads to the same course.
- `GuideLineRendererTest` covers the whole line, the window, the cut-off, the colour, the refresh per particle, and the
  budget for sixteen racers on the shipped course.
- `CupSessionGoldenMasterTest` stays byte-identical, since no transcript records a particle.
- Before acceptance, the owner reviews a rendered screenshot of the shipped course, and Pulse reviews the whole-course
  decision for player retention and information load (design Q6).

## More information

- Change: `openspec/changes/full-course-guide-line/` (proposal, design with the budget table, delta specs, tasks).
- Related decisions, none changed here: the greenfield design's D-decisions (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`).
- Map file format: `docs/reference/map-and-cup-files.md`.
- Old tree: `legacy/server/src/main/java/net/elytrarace/server/ecs/system/SplineVisualizationSystem.java` (END_ROD, every
  five ticks, every player). Not changed.
