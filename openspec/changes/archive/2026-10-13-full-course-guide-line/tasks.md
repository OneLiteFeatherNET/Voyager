# Tasks

Commits follow the types the change names: `feat(race)` for every code change and its tests, `docs(openspec)` for
artifact edits, `docs` for `docs/` pages and ADRs. Each behaviour gets a failing test (Red) before its production code
(Green). Every test follows F.I.R.S.T.: no sleeps, no system time, `@TempDir` for files, a fresh Minestom instance per
test with explicit ticks, no shared static state. Test names state the behaviour.

## 1. Decisions and measurements

- [x] 1.1 Owner answers Q1 to Q7 in design.md and amends ADR-0030 (`docs(openspec)` for the answers; `docs` for the
  ADR text). Recorded on 2026-10-13: see design.md "Owner decisions".
- [x] 1.2 Spike, no commit on main (recorded in design D4 and D6; END_ROD life not verifiable, 6 ticks used): read END_ROD's and DUST's shortest particle life from the best source reachable (the
  decompiled 26.2 source, or the Minestom and vanilla particle data, or minecraft.wiki), the client's live-particle
  cap, and the wire size of one `ParticlePacket`; count points of the shipped line at spacing 1.0, 2.0 and 3.0 with
  `RacingLine.of`; measure the share of points within 160 blocks of sixteen racers placed along the course; record all of
  it in design.md D4 and D6 with the source of each figure (`docs(openspec)`). If END_ROD's life cannot be read from a
  source, the 6-tick refresh is used and the choice is recorded.
- [x] 1.3 Replaced by the owner's playtest (owner decision, 2026-10-13). No render spike is run; the owner plays the
  shipped course and the result is recorded in ADR-0030's review before it is accepted.

## 2. api: the guide line's style

- [x] 2.1 Red: `GuideLineStyleTest` names the behaviours "a guide line with no style names mode FULL, particle END_ROD and
  the amber colour", "an unknown mode is refused and named", "an unknown particle is refused and named", "a colour that is
  not six hexadecimal digits is refused", "a colour on END_ROD is refused and names both fields", and "the three-argument
  constructor keeps its meaning" (`feat(race)`)
- [x] 2.2 Green: add `GuideLineMode` and `GuideParticle` enums in `net.elytrarace.voyager.api.race` with `VALUES` and
  `byName` returning `Optional`; add `mode`, `particle` and `dustRgb` to `GuideLine` with a compatibility constructor; add
  the four factories to `InvalidGuideLineException` (`feat(race)`)

## 3. race: the stretch from the next ring

`RacingLine.between(firstRing, lastRing)` already exists and returns the points between two rings. The FULL stretch is
`between(passedCount, ringCount - 1)`, so no accessor is added to `voyager-race`.

- [x] 3.1 Red: `RacingLineTest` names the behaviour "a stretch from a ring to the last ring runs from that ring's centre to
  the last ring's centre" and "the stretch from the next ring is a suffix of the whole line" (`feat(race)`). If these pass
  at once because `between` already behaves so, the test is kept as a pin and the commit says so.
- [x] 3.2 Green: no production change is expected. If 3.1 fails, fix `between` and record why (`feat(race)`)

## 4. catalogue: reading and writing the optional fields

- [x] 4.1 Red: `MapDefinitionAdapterTest` names the behaviours "a guide line with no style loads as FULL, END_ROD and amber",
  "a guide line with the three fields loads them", "a file written before this change loads to the same course", "an
  unknown mode is refused and names the field", and "a colour on END_ROD is refused" (`feat(race)`)
- [x] 4.2 Green: read the three optional fields in `MapDefinitionAdapter.guideLine`; add them as optional properties to
  `voyager/platform/src/main/resources/schema/map.schema.json` (`feat(race)`)
- [x] 4.3 Red: `MapDraftAdapterTest` and `MapDraftJsonRoundTripTest` name the behaviour "a draft keeps the guide line style
  through a load and a save" (`feat(race)`)
- [x] 4.4 Green: `MapDraftAdapter` and `MapDraftJsonWriter` read and write the three fields (`feat(race)`)
- [x] 4.5 Red: `FileSchemaTest` names the behaviour "the schema accepts a guide line with and without the style fields and
  refuses an unknown particle name" (`feat(race)`)
- [x] 4.6 Green: the schema test passes with the 4.2 properties (`feat(race)`)

## 5. platform: the renderer

- [x] 5.1 Red: `GuideLineRendererTest` names the behaviours "a full line sends the stretch from the next ring to the last",
  "a full line sends nothing behind the next ring", "a point more than 160 blocks away is not sent", "a window line sends
  the stretch ahead as it does today", "a dust line uses the map's colour", "an end rod line uses END_ROD", "a dust line
  redraws every 4 ticks", "an end rod line redraws every 6 ticks", and "a last-ring window sends nothing" (`feat(race)`)
- [x] 5.2 Green: `GuideLineRenderer` reads the map's mode, particle and colour; applies the 160-block cut-off; takes the
  refresh from the particle; maps `GuideParticle` to Minestom's particle types in `platform.render`; the amber, scale and
  refresh constants move out (`feat(race)`)
- [x] 5.3 Update `GuideLineRenderer`'s and `GuideLine`'s Javadoc to ADR-0030, reversing the rationale for whole-course
  and map-level colour (`feat(race)`, in the same commit as 5.2). `RacingLine`'s Javadoc is not changed, since the
  stretch it describes is unchanged.

## 6. shipped map and its tests

- [x] 6.1 Red: `CommittedMapDataTest` names the behaviours "the shipped map loads in FULL mode with END_ROD", "the shipped
  map's rings, boost tuning and guide points are unchanged by this change", and "the shipped map's particle spacing is
  2.0" (`feat(race)`)
- [x] 6.2 Green: edit `voyager/server/src/main/resources/maps/elytraraceblueandred.json`: `particleSpacing` set to 2.0. No
  `mode` and no `particle` field is added, since the defaults give FULL and END_ROD (owner decision). Rewrite the `notes`
  seed for the guide line to give the budget and say `lookAheadRings` serves WINDOW only (`feat(race)`)
- [x] 6.3 Verify, no commit: `CupSessionGoldenMasterTest` and every file under `voyager/server/src/test/resources/golden`
  stay byte-identical. A changed transcript stops this change until the cause is found (design D9)

## 7. Budget check

- [x] 7.1 Red: a Fast test in `GuideLineRendererTest` names the behaviours "sixteen racers on the shipped course at the
  chosen spacing send no more than 30,000 packets per second in total", "no racer on the shipped course is sent more than
  2,000 packets per second", and "the uncut stretch of sixteen racers at the first ring is within the budget". It counts
  packets from the renderer on a fixed set of positions and the explicit tick clock, with no network (`feat(race)`)
- [x] 7.2 Green: adjust the spacing or the cut-off only if 7.1 fails, and only with the owner's agreement; record the
  result in design D6 (`feat(race)` if the code changes, `docs(openspec)` if only the record changes)

## 8. Fitness and build

- [x] 8.1 Verify, no commit: `ApiPurityTest`, `NullabilityConventionTest`, `DesignRuleTest`, `SliceBoundaryRulesTest` and
  `FitnessCoverageTest` pass with the new api types and the platform renderer changes, and no fitness rule needed adding
  (design "Architecture")
- [x] 8.2 Verify, no commit: `./gradlew build` passes for both trees (`./gradlew :voyager:race:test :voyager:platform:test
  :voyager:server:test` first, then the full build)

## 9. Documentation

- [x] 9.1 Update `docs/reference/map-and-cup-files.md`: the three optional `guideLine` fields, their defaults and refusals,
  and the note that `lookAheadRings` serves WINDOW (`docs`)
- [x] 9.2 Write the measured budget into a research note under `docs/research/` in the research paper style, English
  (Lumen's artifact). The visual verdict is the owner's playtest, recorded when it happens (`docs`)
- [x] 9.3 ADR-0030 stays Proposed (owner decision). Its Confirmation section names the owner's playtest in place of the
  Pulse review, and the PR lists the open questions (`docs`)

## 10. Ship

- [x] 10.1 Open the pull request for branch `feat/full-course-guide-line`. Title:
  `feat(race): draw the whole course as the racing line`. Body: the budget numbers from 1.2 and 7.1, the open questions
  that remain (the owner's playtest and ADR-0030's acceptance), and these two final lines, in this order:
  `https://claude.ai/referral/m5Ak2Sa7aQ`
  `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`
