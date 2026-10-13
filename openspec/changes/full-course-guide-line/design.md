# Design

## Context

- See proposal.md for why. The decision record is `docs/decisions/0030-full-course-guide-line.md` (Proposed).
- `RacingLine.of(map)` samples the line through ring centres and guide points at `particleSpacing`. The committed course
  is 35 rings and about 1,588 blocks long, and the sampled line has about 1,700 points at spacing 1.0 (its Javadoc).
- `RacingLine.between(firstRing, lastRing)` already returns the points of a stretch between two rings, both included.
  The racer's stretch under this change is `between(passedCount, lastRing)`; no new accessor is needed in `voyager-race`.
- `GuideLineRenderer.render` runs in `CupSession.raceTick` for every racer, every tick. It is cheap on a tick where it
  does nothing (`gameTick % 4`), and it sends `ParticlePacket`s, one per point, to that racer only.
- The renderer's current policy: from the ring the racer heads for, `lookAheadRings` further. Its colour (amber),
  scale (1.0), particle (DUST), refresh (4 ticks) are constants, and `GuideLine`'s Javadoc says why they are not map data.
  This change moves particle and colour into the map file and reverses that reasoning for the policy (ADR-0030).
- Minecraft particles are one packet per position. A line of N points costs N packets to each client that is sent it.
  That is the constraint every number below comes from.
- `MapDefinitionAdapter` ignores unknown keys (its Javadoc: "Unknown fields in general are ignored rather than
  rejected"). `schemaVersion` is 1 for every committed map.

## Goals / Non-Goals

**Goals:**
- A racer sees the course ahead of them, from the ring they are heading for to the last ring, at a packet cost the
  server can state.
- The window remains a per-map choice, and every map file that loads today loads to the same course.
- Colour and particle are per map, with one default that every course shares.

**Non-Goals:**
- A player-facing setting per racer (the server decides; no client option).
- Particle types beyond END_ROD and DUST, and per-point particle counts (`count`) in the file.
- Any change to ring geometry, scoring, boost, physics or the golden transcripts.

## Owner decisions (2026-10-13)

The owner answered the open questions. The answers are recorded here and in ADR-0030, and the decided text replaces the
earlier open-question text wherever it contradicted it.

| Question | Decision |
|---|---|
| Q1. Shipped spacing | **2.0** (from 1.0). The budget in D6 is the reason; the owner accepts it. |
| Q2. END_ROD's shortest life | Verified in the spike (task 1.2), from the sources it can reach. D4 gives the rule and the fallback. |
| Q3. Show the part already flown | **Hide it.** A racer is shown the line from the ring they are heading for to the last ring. The part already flown is not drawn. This replaces the earlier "keep showing" default. |
| Q4. Keep `WINDOW` | **Yes**, as a per-map option. |
| Q5. Order with the oval change | **Moot.** The oval change (`add-oval-and-tilted-rings`) is not on main and is ignored by this change. |
| Q6. Pulse review | **Closed.** The owner playtests the shipped course instead. ADR-0030 stays Proposed until that playtest. |
| Q7. Cut-off radius | **160 blocks**, as proposed. |

The owner also decided, and the artifacts now say:
- FULL is the default and shows the whole course from the racer's next ring, as the old game did. WINDOW is per map.
- END_ROD is the default particle. DUST is selectable per map, with `dustColor` `#FFC800`.
- `particleSpacing` 2.0. The optional `guideLine` fields need no `schemaVersion` bump.
- Refresh: every 10 ticks for END_ROD and every 4 ticks for DUST, subject to the END_ROD lifetime check in D4.
- Budget: at most 30,000 particle packets per second in total and 2,000 per racer, for sixteen racers on the 35-ring map.
  Task 7.1 asserts both.
- The live render spike (task 1.3) is replaced by the owner's playtest. The spike still measures the packet numbers (1.2).

## Decisions

### D1. Whole course is the default, from the next ring; the window stays as an option

Per map, `mode` is `FULL` or `WINDOW`. Absent means `FULL`.

- `FULL` draws the line from the ring the racer is heading for to the last ring. The part already flown is not drawn.
  This is `RacingLine.between(passedCount, ringCount - 1)`. Once every ring is passed, nothing is drawn.
- `WINDOW` is today's policy: from the ring the racer heads for, `lookAheadRings` further, and nothing once the last
  ring is passed.

Hiding the flown part makes the packet count fall as a racer progresses. The worst case is a racer at the first ring,
which is what the budget test measures (D6).

*Alternatives:* (a) remove the window. Rejected: it is a cheap, tested code path and a long course with a dense
guide may still want it; removing it is a one-way door for a choice the owner can make per map. (b) keep `WINDOW` as
the default and make `FULL` opt-in. Rejected: the owner's decision is that the course is shown whole. (c) keep the flown
part drawn. Rejected by the owner (Q3): it spends packets on the one part of the course that cannot help.

### D2. Particle: END_ROD by default, DUST with a colour as the only alternative

- `particle`: `END_ROD` (default) or `DUST`. Any other value is refused, naming the field and the value.
- `dustColor`: `#RRGGBB`, default `#FFC800` (the current amber). Only valid with `DUST`; with `END_ROD` it is refused,
  because a field with no effect is a mistake the loader should name.
- END_ROD has no colour of its own. A course that wants a colour uses DUST.

*Alternatives:* a free list of Minecraft particle names. Rejected: the loader would need every particle's data type
(DUST takes colour and scale; others take none), and an unknown name would reach the server as a runtime error.
Two named values cover the owner's request with one data type each.

### D3. Density is `particleSpacing`, which already exists

Density is the spacing between points, in blocks, and it is already a required field with a floor of 0.25. This change
does not add a second density knob. Changing `count` per packet would raise the client's particle load without saving
any packet, so it stays 1.

Shipped value: **2.0** (from 1.0), owner decision (Q1).

### D4. Refresh interval belongs to the particle, not the map

The line is redrawn every `REFRESH_TICKS[particle]` ticks. The rule is that the refresh is at most half the particle's
shortest life, so one redraw always lands before the previous points die:

- DUST: 4 ticks. Its shortest life is 8 ticks (vanilla `8 / (random * 0.8 + 0.2)`, from the existing renderer's
  rationale), and 8 / 2 = 4.
- END_ROD: **10 ticks, owner's value, subject to the lifetime check in task 1.2.** The check needs END_ROD's shortest
  life to be at least 20 ticks. If the shortest life is 12 to 19 ticks, the refresh is 6 ticks and D6's fallback
  applies. Under 12 ticks, no refresh in the budget works at spacing 2.0, and the owner decides on spacing 4.0 or a
  reopened design.

The check is recorded in the spike notes in D6 with the source it came from. If no source can be reached, the fallback
of 6 ticks is used and the choice is documented there, as the owner asked.

It is not a map field. The refresh rate is a budget decision about the server, and a map author has nothing to say
about it, as the current Javadoc already argues.

### D5. Distance cutoff, and per-racer sending

- Points more than **160 blocks** from the racer are not sent. 160 sits below the 256-block long-distance draw the
  renderer requests (`LONG_DISTANCE`), so no sent particle is out of range for the client, and it leaves 96 blocks of
  margin for a racer who moves between redraws (about 20 blocks in 10 ticks at elytra speed, see D6).
- The cutoff is a server constant, not a map field. It bounds the packets by the local density of the course, not by
  its length.
- Sending stays per racer (`player.sendPackets`). Racers are at different points of the course, so their cut-offs
  differ. Sending the same packet list to the whole instance once (`Instance.sendGroupedPacket`) would save the
  per-connection serialisation but would send the far part of the course to everybody, which the cut-off exists to
  avoid. The serialisation cost at the budget below is small compared with the bandwidth, so the trade is taken
  for the cut-off. The spike confirms the serialisation cost; if it is high, the grouped send can be reconsidered for a
  course that does not need the cut-off.

### D6. Packet budget (estimates, to be measured in the spike)

Inputs: N points at spacing s is about 1,700 / s (1,700 at s = 1.0, from `RacingLine`'s Javadoc). Packets per second
for one racer is N × 20 / P. A `ParticlePacket` is estimated at about 50 bytes on the wire (position as three doubles,
offset and speed as floats, count, id, flags, framing). All numbers assume no cut-off and a racer at the first ring,
which is the worst case because the stretch is then the whole line. The cut-off and the hidden flown part lower the
numbers for everyone else. The spike measures the cut-off's share.

| Spacing s | Points N | Refresh P (ticks) | Packets/s, one racer | Packets/s, 16 racers | Wire, 16 racers |
|---|---|---|---|---|---|
| 1.0 (today's value) | 1,700 | 4 (DUST) | 8,500 | 136,000 | about 6.8 MB/s |
| 1.0 | 1,700 | 10 | 3,400 | 54,400 | about 2.7 MB/s |
| 2.0 (shipped) | 850 | 10 (END_ROD, owner's value; check in 1.2) | 1,700 | **27,200** | about 1.4 MB/s |
| 2.0 | 850 | 6 (END_ROD fallback) | 2,833 | 45,333 | about 2.3 MB/s |
| 3.0 | 567 | 6 (END_ROD fallback) | 1,890 | 30,240 | about 1.5 MB/s |
| 4.0 | 425 | 4 (DUST) | 2,125 | 34,000 | about 1.7 MB/s |

**Budget (owner's decision):** at 16 racers on the 35-ring map, at most **30,000 packets/s** in total and **2,000
packets/s** for any one racer. The shipped default (2.0, END_ROD, 10 ticks) is 27,200 in total and 1,700 for one racer
at the first ring, with no cut-off: inside both limits.

**Why DUST costs more:** its refresh is 4 ticks, so the same course costs 2.5 times the packets of END_ROD at 10. A
DUST map therefore needs spacing 4.0 to fit the budget without a cut-off, and it is the reason DUST is an option and
not the default.

**Client load:** live particles per client are about N_in_view × L / P, where L is the particle's mean life. For DUST
(mean about 16 ticks) at spacing 2.0 with no cut-off that is 850 × 16 / 4 = 3,400 live particles. Vanilla clients
cap live particles (ParticleEngine; the cap is believed to be 16,384 and is to be verified in the spike). END_ROD's
life is checked in the spike, as D4 says.

**Growth:** N grows with course length. A 3,000-block course at spacing 2.0 is about 1,500 points, 48,000 packets/s
for 16 racers without a cut-off. The cut-off is therefore not optional for longer courses; the budget is stated per
course length in the spike report.

**Fallback if END_ROD's shortest life is 12 to 19 ticks:** spacing 3.0, refresh 6: 30,240 packets/s, at the budget line.
Below 12 ticks, spacing 4.0 and refresh 4 (34,000 packets/s) is over the budget, so the owner decides again.

### D7. File format: optional fields, no version bump

- The three fields are optional in `guideLine`. A file without them loads to `FULL`, `END_ROD` and the amber default.
  `lookAheadRings` and `particleSpacing` stay required, so the loader's refusal of a missing window or spacing does
  not change, though `lookAheadRings` has no effect in `FULL` mode and the Javadoc says so.
- No `schemaVersion` bump, as the owner confirmed. A field that changes what is drawn but not where anything is, read by
  an older server, makes the older server show the window it always showed. Nothing scores or places a racer
  differently, so no version-1 server can play a wrong course.
- `map.schema.json` gains the three properties, as optional ones. The oval change is not on main and is ignored here
  (owner decision), so this edit has no coordination step.
- `MapDraftAdapter` and `MapDraftJsonWriter` read and write the optional fields, so a draft round trip keeps them.

### D8. The shipped map

`elytraraceblueandred.json` sets only `particleSpacing` to **2.0**. It names no `mode` and no `particle`: the defaults
give `FULL` and `END_ROD`, which is what the owner asked for. The owner's instruction is to add a field only if one is
needed, and none is. Its `notes` seed for the guide line is rewritten to say that the spacing is a budget decision with
the numbers in D6, and that `lookAheadRings` serves `WINDOW` only.

### D9. Golden master

The golden transcripts (`voyager/server/src/test/resources/golden/cup-session/*.txt`) contain no `particle` and no
`guide` text (checked on main, 0 matches). The renderer's packets are not written there. The change must therefore keep
`CupSessionGoldenMasterTest` byte-identical. A failing transcript would mean a scoring or movement change leaked in, and
the change stops until that is found.

### D10. What the ADR reverses

`GuideLine`'s Javadoc ("Why the colour and the particle size are not in here") and `GuideLineRenderer`'s Javadoc argued
against the whole course ("a ball of string") and against per-map colour. ADR-0030 supersedes those two reasons. The
Javadoc is rewritten in the same commit as the code, so the code does not keep an argument it no longer follows.

## Architecture

Dependency direction (Clean Architecture; inward only):

```
voyager-server (composition only, no change)
  -> voyager-platform   GuideLineRenderer, MapDefinitionAdapter, MapDraftAdapter, map.schema.json
       -> voyager-race  RacingLine (existing between(), no change)
            -> voyager-api  GuideLine (adds style), GuideLineMode, GuideParticle, InvalidGuideLineException factories
```

| New type or change | Module and package | Notes |
|---|---|---|
| `GuideLineMode` (enum: `FULL`, `WINDOW`) | voyager-api `net.elytrarace.voyager.api.race` | DSL enum: `VALUES`, `byName` returns `Optional`. |
| `GuideParticle` (enum: `END_ROD`, `DUST`) | voyager-api `api.race` | Same DSL. |
| `GuideLine` gains `mode`, `particle`, `dustRgb` | voyager-api `api.race` | Still a plain record. A compatibility constructor keeps the three-argument form and defaults the new values. |
| `InvalidGuideLineException` factories | voyager-api `api.race.exception` | `unknownMode`, `unknownParticle`, `dustColorWithoutDust`, `badDustColor`. |
| `RacingLine.between` | voyager-race `race.line` | Existing method, used for the FULL stretch. No `whole()` accessor is added. |
| Style mapping to Minestom `Particle` | voyager-platform `platform.render` | The only place that imports Minestom particle types. |

Boundary rules that already hold, and the rule that covers each:

- api has no implementation and no platform imports: `ApiPurityTest`.
- Every api package carries `@NotNullByDefault` (`NullabilityConventionTest`): `api.race` already does.
- Exception names end in `Exception` and sit in their domain's `exception` subpackage: `DesignRuleTest`.
- The platform render code does not leak into race or api: the existing ring-and-slice rules (`SliceBoundaryRulesTest`).

No new fitness rule is added. The one judgement not covered mechanically (the renderer reads the style from the map and
does not hold its own constants) is a review point in the tech-writer and Red/Green reviews, per the java-style skill.
Module coverage is unchanged, so `FitnessCoverageTest` is not affected.

## Risks / Trade-offs

- **Budget estimates may be wrong.** The packet size, END_ROD's life and the client cap are estimates. The spike (task
  1.2) measures them. The shipped spacing is the owner's decision on the numbers it reports.
- **The whole course may be less useful than the window.** The old `GuideLine` argument (a ball of string does not say
  which strand is next) still holds. The owner has overruled it. The mitigation is the per-map `WINDOW` mode and the
  owner's playtest of the shipped course, which replaces the Pulse review (Q6).
- **DUST is expensive.** Its 4-tick refresh is the cost of a short life. A DUST map has to spend spacing or accept a cut-off.
- **Visual density.** A 2-block dotted line of END_ROD may read as dots rather than a line. The owner's playtest decides
  this; the live render spike is not part of the work (owner decision).

## Open questions

- **Q2 (remaining).** The exact END_ROD shortest life is recorded by task 1.2. If it is below 20 ticks, D4 applies.
- **Visual density.** Open until the owner's playtest. ADR-0030 stays Proposed until then.
