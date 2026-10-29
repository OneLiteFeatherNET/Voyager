# Choosing the Guide-Line Particle per Map: Visibility on Bright Ground and the Packet Cost of DUST

**Authors:** Voyager Development Team | **Date:** 2026-10-29 | **Status:** Draft, awaiting the owner's playtest (task 4.2)

## Abstract

The racing line is drawn in white on every shipped map, because the particle default is END_ROD. White is nearly invisible on
the bright sand and sky of palm_beach. This note records how the particle and colour were chosen per map. A DUST line in deep
violet `#7A1FB8` is the only candidate above the WCAG 3:1 threshold on bright ground, and the owner chose it for palm_beach. The
other three shipped maps keep END_ROD, now named in their files. Measured in the renderer, the palm_beach DUST line exceeds the
per-racer packet limit of 2,000 per second: the worst of sixteen racers is sent 3,690, and the total is 29,800, under the
30,000 limit. The owner accepted that overage on 2026-10-29 and kept the spacing at 1.0.

## 1. Introduction

The guide line is a sequence of stationary particles, one packet per point per racer, redrawn at a fixed interval. Its particle
decides how it looks and how often it is redrawn. The map file chooses the particle and its colour (`guideLine.particle`,
`guideLine.dustColor`). The renderer maps the choice to a Minestom particle and does not change. This note asks only which
particle and colour each shipped map should name.

## 2. Method

**Contrast.** The visibility of each candidate was estimated from its nominal colour, not in a client. A guide particle is a
small sprite whose rendered colour depends on client lighting, so this is an estimate that ranks candidates, not a verdict.
The measure is the WCAG 2.x contrast ratio between the particle's colour and eight representative ground tones: bright sky
`#8EC9F5`, sand `#E8D6A0`, snow `#F2F6FA`, grass `#6A9E3F`, stone `#7D7D7D`, night `#1A1A1A`, deep water `#1E4D7A` and dark
foliage `#2E4A2A`. WCAG 1.4.11 sets 3:1 as the threshold for graphical objects. The tones stand for kinds of ground. The
worlds are read-only under `run/`, so the maps were not sampled.

**Packets.** The packet budget of the chosen particles was measured with the renderer's own output. Sixteen racers were placed at
evenly spaced rings in FULL mode, the 160-block reach applied, and the renderer ticked for three seconds. Each figure is the
packet count divided by three. The measurement is a test, `PalmBeachBudgetExceptionTest`, which pins the result.

## 3. Findings

### 3.1 Candidates

| Candidate | Colour (nominal) | Min on bright (sky, sand, snow) | Min on dark (night, deep water, foliage) | Min over all eight | Verdict |
|---|---|---|---|---|---|
| END_ROD (default) | white `#FFFFFF` | 1.09 | 8.76 | 1.09 | Good on dark and mid ground. Lost on bright ground and snow. |
| DUST, amber (the DUST default colour) | `#FFC800` | 1.08 | 5.64 | 1.08 | Like END_ROD, weaker on bright ground. Not a fix. |
| DUST, deep violet | `#7A1FB8` | **4.35** | 1.13 | 1.13 | Clear on bright ground (sky 4.35, sand 5.36, snow 7.12). Lost on dark ground. |
| ELECTRIC_SPARK | pale yellow `#FFF27A`, assumed | 1.06 | 7.60 | 1.06 | Same weakness as END_ROD on bright ground. Needs a new enum value. |
| TRAIL | per particle | not rated | not rated | not rated | Rejected for a line (3.2). |

Extra colours for the record: hot pink `#FF4FA0` has a bright minimum of 1.72, magenta `#FF2BD6` 1.80, cyan `#00E5FF` 1.07. Mid
tones are END_ROD's best case (grass 3.19, stone 4.12) and the violet's worst (2.42, 1.88). No single colour is above 3:1 on
every ground, so the choice is per map.

### 3.2 Why TRAIL is rejected for the line

`Particle.TRAIL` travels from its origin to a target over a duration. A racing line needs a steady sequence of stationary marks.
A trail per point needs a target per point, which doubles the packets, and draws a moving dot where the line needs a fixed mark.

### 3.3 Other values

`GLOW`, `FIREFLY` and `DUST_COLOR_TRANSITION` exist in the pinned Minestom but were not evaluated, because the table already
decides the question. Each would need its own schema decision.

### 3.4 Packet cost

DUST is redrawn every four ticks and END_ROD every six. At the same spacing a DUST line sends about 1.5 times the packets per
second of an END_ROD line. The measured palm_beach figures, at spacing 1.0, sixteen racers:

| Particle | Worst racer, packets per second | Total, packets per second | Limit (worst racer / total) |
|---|---|---|---|
| DUST, `#7A1FB8` (chosen) | 3,690 (measured) | 29,800 (measured) | 2,000 / 30,000 |
| END_ROD, same map (estimated) | about 2,460 (from the refresh ratio, not measured) | about 19,900 (estimated) | 2,000 / 30,000 |

The per-racer limit of `race/racing-line-display` is exceeded for the worst racer with either particle. The overage predates this
change, because the palm_beach line at spacing 1.0 already exceeds it as END_ROD. DUST adds about half as much again. The owner
was offered three options: set palm_beach's spacing to 2.0 as the shipped course has it, accept the overage, or change the
particle. The owner accepted the overage on 2026-10-29 and kept the spacing at 1.0.

The shipped course, blue_and_red, keeps the 2,000 limit. It is measured in `GuideLineRendererTest` and `CommittedMapDataTest`, and
it is not affected by this decision.

## 4. Decisions

- **palm_beach: DUST `#7A1FB8`.** The owner's decision for the bright map. The only candidate above 3:1 on bright ground.
- **dragon_lobby, blue_and_red, memoria: END_ROD, named explicitly.** The owner's decision. The best candidate on dark and mid
  ground.
- **The default stays END_ROD** for a map that names nothing.
- **palm_beach's budget exception** is accepted and pinned at 4,000 per racer and 30,000 in total (`PalmBeachBudgetExceptionTest`).
  A change that takes the course above either ceiling needs a new decision.

## 5. Limitations and open items

- The contrast figures are nominal. The owner's playtest of palm_beach and the other three maps, from the spawn, at ring 1 and at
  mid-course, is task 4.2 and is not recorded here. Its verdicts belong in a later revision of this note.
- The eight tones are not sampled from the three maps. They stand for kinds of ground.
- The END_ROD packet figures are estimated from the refresh ratio, not measured.
- A new particle value (ELECTRIC_SPARK, GLOW, FIREFLY, DUST_COLOR_TRANSITION) is out of scope and would need a schema decision.

## 6. References

- `openspec/changes/configurable-guide-particles/` (proposal, design, delta spec for `catalog/guide-particles`).
- `voyager/platform/src/main/java/net/elytrarace/voyager/platform/render/GuideLineRenderer.java`, the refresh constants and the
  reach.
- `docs/reference/map-and-cup-files.md`, the `particle` and `dustColor` fields.
- `docs/research/2026-10-13-full-course-guide-line-budget.md`, the budget that the overage is measured against.
