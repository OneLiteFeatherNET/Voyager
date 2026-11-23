# Tasks

Commit types and scope: every behaviour ships as `feat(setup)`; the how-to is `docs`. Each behaviour gets a failing test (Red) before
its production code (Green). Every test follows F.I.R.S.T.: plain JUnit for `GuidePlacement`, a fresh `Env` per Minestom test, a
fixed ring fixture from `DraftEditor`, no sleeps, no system-time reads. Task checkboxes are ticked in the follow-up pull request,
never in the code pull request.

## 1. Red: the placement and the refusal

- [ ] 1.1 Red: `GuidePlacementTest`: an eye on the segment from ring 2 to ring 3 derives an order index between 200 and 300 exclusive (`feat(setup)`)
- [ ] 1.2 Red: `GuidePlacementTest`: an eye beyond the end of a segment clamps `t` to 1 and derives 99 in the gap, never a ring's own slot (`feat(setup)`)
- [ ] 1.3 Red: `GuidePlacementTest`: a map with fewer than two rings is refused by name (`feat(setup)`)
- [ ] 1.4 Red: `GuidePlacementTest`: a hand-built candidate whose guide point lies after the last ring is refused with the bracket message, and the original draft is returned unchanged (`feat(setup)`)
- [ ] 1.5 Red: `GuidePlacementTest`: spacing 0.1 is refused with the 0.25 bound; look-ahead 0 is refused (`feat(setup)`)
- [ ] 1.6 Red: confirm 1.1 to 1.5 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 2. Green: the placement

- [ ] 2.1 Green: `GuidePlacement` in `mapsetup` with the derivation of `design.md` and the refusal through `MapDefinition`'s bracket check (`feat(setup)`)
- [ ] 2.2 Green: `DraftEditor.withGuidePoint`, `withoutGuidePoint`, `withSpacing` and `withLookAhead`, each returning a new `MapDraft` (`feat(setup)`)

## 3. Red: commands, markers and undo

- [ ] 3.1 Red: Minestom test: `/map guide add` next to ring 2 saves the draft before the marker appears, and `/map guide list` names the two rings (`feat(setup)`)
- [ ] 3.2 Red: Minestom test: `/map guide remove 2` removes the second point and writes the file (`feat(setup)`)
- [ ] 3.3 Red: Minestom test: `/map guide spacing 0.1` refuses with the bound and writes no file (`feat(setup)`)
- [ ] 3.4 Red: Minestom test: `/map guide lookahead 3` on a FULL map saves 3 and replies the WINDOW note (`feat(setup)`)
- [ ] 3.5 Red: Minestom test: closing the map removes every guide marker of that map (`feat(setup)`)
- [ ] 3.6 Red: Minestom test: `/map undo` after `/map guide add` removes the point (`feat(setup)`)
- [ ] 3.7 Red: confirm 3.1 to 3.6 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 4. Green: commands and markers

- [ ] 4.1 Green: `guide` subcommands in `SetupCommands`, each through `MapSession.commit` (`feat(setup)`)
- [ ] 4.2 Green: marker class in `adapter`, spawned on open and removed on close, beside the ring previews (`feat(setup)`)
- [ ] 4.3 Green: messages in `SetupMessages` (`platform/text`) and both bundles (`feat(setup)`)
- [ ] 4.4 Green: ArchUnit rule in `voyager/fitness` that only `DraftEditor` builds a `GuideLine` for a setup draft (`feat(setup)`)

## 5. Docs

- [ ] 5.1 Add a section "Author the racing line" to `docs/guides/how-to-build-a-map-with-setup.md`: where a guide point goes, what the bracket refusal means, and the spacing bound (`docs`)

## 6. Verify

- [ ] 6.1 Run `./gradlew :voyager:setup:test :voyager:fitness:test` and confirm `MessageBundleParityTest` and the new rule pass (`test`)
- [ ] 6.2 Run `./gradlew build` on both trees (`test`)
- [ ] 6.3 Check the markers in the setup world on a running `:voyager:setup:runSetupDev` before ticking 4.2 (`test`)

## 7. Pull request

- [ ] 7.1 Open the code pull request titled `feat(setup): author the racing line with guide commands` against `main` (`ci`)
