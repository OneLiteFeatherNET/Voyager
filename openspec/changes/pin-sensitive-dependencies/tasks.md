# Tasks

Commit types: `ci(deps)` for Renovate, the CI job and the verification task; `docs` for `CLAUDE.md`. Each behaviour gets a failing
test (Red) before its production code (Green). The verification task is a unit test with fixed versions: no network, no clock.
The pull request contains no file under `openspec/`, and no task is ticked in it.

## 1. Red then Green: the agreement check

- [ ] 1.1 Red: `MinestomPinAgreementTest` "equal versions pass" (`ci(deps)`)
- [ ] 1.2 Red: `MinestomPinAgreementTest` "different versions fail and the message names both" (`ci(deps)`)
- [ ] 1.3 Green: verification task `verifyMinestomPins` in `voyager/platform/build.gradle.kts` reads the resolved
  `net.minestom:minestom` and compares it with `minestomTestingVersion` (`ci(deps)`)

## 2. Renovate approval gate

- [ ] 2.1 Red: validate the current `renovate.json` with the Renovate config validator and record the result (`ci(deps)`)
- [ ] 2.2 Green: `packageRules` in `renovate.json` set `dependencyDashboardApproval: true` for `net.minestom`, the extension group,
  `net.luckperms:api` and `net.luckperms:minestom-loader` (`ci(deps)`)
- [ ] 2.3 Green: the validator passes on the new file; the rule's match is shown in the pull request description (`ci(deps)`)

## 3. Boot smoke test

- [ ] 3.1 Red: a deliberately broken JAR (a fixture that exits at start) makes the job fail, and the job log shows the last 200
  lines (`ci(deps)`)
- [ ] 3.2 Green: a job in `.github/workflows/` that runs after the build on changed build or `voyager/` paths, builds
  `:voyager:server:shadowJar`, starts the JAR with a temporary data directory, waits up to 120 seconds for the ready line, prints the
  last 200 lines on failure and stops the server (`ci(deps)`)
- [ ] 3.3 Green: a docs-only pull request skips the job (`ci(deps)`)

## 4. Documentation

- [ ] 4.1 `CLAUDE.md` names the Minestom version the build resolves in the Key Decisions and the Dependencies sections (`docs`)
- [ ] 4.2 `docs/reference/` names the approval gate and the boot check where contributors look for the build (`docs`)

## 5. Open the code pull request

- [ ] 5.1 Open the pull request under `ci(deps): pin sensitive dependencies behind owner approval`. It contains no file under
  `openspec/`, and no task is ticked in it.
- [ ] 5.2 Follow-up, owner-gated: correct `openspec/config.yaml:8` in its own change, since it lies outside this change (`docs`)
