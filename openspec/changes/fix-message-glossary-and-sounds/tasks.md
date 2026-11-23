# Tasks

Commit type and scope: `fix(race)` for the code, the bundles and the tests, `docs` for the style guide. Each behaviour gets a failing
test (Red) before its production code (Green). Every test follows F.I.R.S.T.: the check reads the bundles from the classpath with
no network, no sleeps, a fresh fixture per test. Task checkboxes are ticked in the follow-up pull request, never in the code pull
request.

## 1. Red: glossary

- [ ] 1.1 Red: `MessageStyleTest` "a race line that names a person as a player fails the check and names the key" (`fix(race)`)
- [ ] 1.2 Red: `MessageStyleTest` "the English lobby waiting lines use racer or racers" (`fix(race)`)
- [ ] 1.3 Red: `MessageStyleTest` "the German lobby waiting lines use the German racer word and no Spieler" (`fix(race)`)
- [ ] 1.4 Red: `MessageStyleTest` "a key on the exception list passes, and its reason is not empty" (`fix(race)`)
- [ ] 1.5 Red: run 1.1 to 1.4 and confirm each fails for the stated reason; record the failure messages in the code pull request

## 2. Green: glossary

- [ ] 2.1 Green: change the English and German waiting lines to the glossary words (`fix(race)`)
- [ ] 2.2 Green: add the glossary check and its exception list to `MessageStyleTest` (`fix(race)`)
- [ ] 2.3 Green: make 1.1 to 1.4 pass (`fix(race)`)

## 3. Red and Green: sounds

- [ ] 3.1 Red: `RaceFeedbackSoundTest` "the Diamond medal sound is not the last-ring chime" (`fix(race)`)
- [ ] 3.2 Red: `RaceFeedbackSoundTest` "first place sound is not the last-ring chime and differs from the Diamond sound" (`fix(race)`)
- [ ] 3.3 Red: `RaceFeedbackSoundTest` "every chosen sound key exists in the 26.2 registry" (`fix(race)`)
- [ ] 3.4 Red: run 3.1 to 3.3 and confirm each fails for the stated reason
- [ ] 3.5 Owner choice: the owner picks the two samples by ear from the candidates in `design.md`; record the choice in the code
  pull request before 3.6
- [ ] 3.6 Green: replace the sample in `medalSound` (`DIAMOND`) and `placeSound` (`1`) with the chosen samples (`fix(race)`)

## 4. Documentation

- [ ] 4.1 Add the two sound choices and the glossary exception list to `docs/reference/message-style-guide.md` (`docs`, separate commit)

## 5. Verify

- [ ] 5.1 Run `./gradlew :voyager:platform:test :voyager:fitness:test` through `heavy` and confirm both pass
- [ ] 5.2 Run `./gradlew build` (both trees) through `heavy` before marking the change done

## 6. Open the code pull request

- [ ] 6.1 Open the code pull request titled `fix(race): use racer wording and give Diamond and first place their own sounds`,
  branched from the remote main branch that contains this proposal
