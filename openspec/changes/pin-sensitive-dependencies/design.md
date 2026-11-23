# Design

## Context

The rebuild's Minestom comes from the `aonyx-bom` platform, which imports `mycelium-bom` (`voyager/platform/build.gradle.kts:4-7`
comment). The test environment's `net.minestom:testing` is not managed by any OneLiteFeather BOM and is pinned at
`voyager/platform/build.gradle.kts:14`. The LuckPerms API and loader are pinned in `settings.gradle.kts:111-112`, with the loader's
floor in a comment at `:109-110`. Renovate is configured in `renovate.json`, which extends three organisation presets and has no
package rules. CI workflows are in `.github/workflows/` and the existing check names are required by the main-branch ruleset
(`.github/workflows/build.yml` header), so a new job is not required until the owner adds it.

## Goals and non-goals

- Goal: an update to a sensitive dependency waits for the owner's dashboard approval.
- Goal: the test pin and the BOM agree, or the build says so.
- Goal: a pull request that changes the build boots the shipped JAR.
- Non-goal: changing any version; the boot check is a gate, not an upgrade.
- Non-goal: changing the organisation presets.

## Decisions

### D1. The approval gate is a Renovate package rule

`renovate.json` gains `packageRules` that match `net.minestom` (and its `minestom-extensions` group), `net.luckperms` `api` and
`minestom-loader`, and set `dependencyDashboardApproval: true`. The rule lives in this repository, not in the presets, so it is
reviewed with the code.

### D2. The agreement check compares resolved versions

A Gradle verification task in `voyager/platform` reads the resolved version of `net.minestom:minestom` from the runtime
configuration and compares it with `minestomTestingVersion`. It fails with both versions named. It is a check, not a second pin:
the pin stays where it is, and the check makes a drift visible.

If the BOM publishes no test artifact that matches its own Minestom version, the check still compares the test pin with the main
artifact. The gap is recorded as a risk, not fixed here.

### D3. The boot check runs the shipped JAR

A CI job runs after the build, on `ubuntu-latest`, for pull requests whose changed files match the build files or `voyager/**`
and not only docs. It builds `:voyager:server:shadowJar`, starts the JAR with a temporary data directory and
`VOYAGER_CONFIG_CHECK` unset, and waits for the ready line within 120 seconds. On timeout or exit it prints the last 200 lines of
output and fails. It stops the server with its stop command. This is an integration check, so wall-clock waiting is allowed there;
the unit suites stay free of sleeps.

The check uses only the shipped configuration defaults, so it needs no secret and no database. Persistence is off by default and
the boot must succeed without it, which the existing boot tests already assert in-process.

### D4. Documentation names the resolved version

`CLAUDE.md:43` and `CLAUDE.md:111` (the rebuild target) are corrected to the resolved version, and `openspec/config.yaml:8` is
corrected in a later change because it lies outside this change's directory. The docs commit is separate and typed `docs`.

## Module map

No Java module changes except the verification task in `voyager/platform` (build logic). The CI job lives in `.github/workflows/`.

## Dependency direction

Build logic depends on the version catalog and the BOM; no runtime module depends on build logic.

## Fitness

Build and CI files are outside the ArchUnit scope. The verification task is its own test: it fails when the pins disagree, and the
spec scenario is asserted by a fixture build in a test source set with a fixed BOM version, so it is repeatable.

## Test plan (F.I.R.S.T.)

- Build: `MinestomPinAgreementTest` with a fixed resolved version and a fixed test pin: passes when equal, fails with both versions
  when not. No network.
- Renovate: validate `renovate.json` with the Renovate config validator in the code PR's CI.
- CI: the boot job is itself the test; it asserts the ready line, and its failure output is the evidence.

## Architecture decision

No ADR. The decisions are operational (D1 to D4). The owner may ask for an ADR on the approval gate.

## Risks

- The BOM and the test artifact may diverge for good. Mitigation: the check reports it; the pin is then owner-approved.
- The boot job adds minutes to build-touching pull requests. Mitigation: it runs only on matching paths.
- A new job is not a required check until the owner adds it to the ruleset. The owner decides (open question).
- Renovate's dashboard approval only holds if the presets do not override it. Mitigation: the rule is in this repository, and the
  presets' rules are reviewed when the change lands.
