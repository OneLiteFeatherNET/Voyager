# Proposal

**Type and scope:** `ci(deps)`. The change is tooling: Renovate's configuration, a dependency check in the build, a boot smoke test in
CI, and one correction to `CLAUDE.md`. It touches the build and CI of the rebuild (`voyager/platform`, `voyager/server`) and no game
logic. Ships as code under `ci(deps)`; the `CLAUDE.md` correction is a separate `docs` commit in the same pull request. Breaking: no.

## Why

Two dependencies can break a live server without any code change, and the project has no mechanism that stops an automated update
from landing them.

- The LuckPerms loader has a floor. `settings.gradle.kts:109-110` says the loader must be 6.0.2 or newer, because 6.0.1 fails with
  `NoSuchMethodError` on boot under Minestom 2026.10.07, and that any bump needs owner approval. That rule lives only in a comment.
  `settings.gradle.kts:111` still pins `luckperms-api` at 6.0.1.
- The Minestom version is pinned twice. The BOM pins `net.minestom:minestom` through `aonyx-bom` (`voyager/platform/build.gradle.kts:7`
  comment), and the test environment pins `net.minestom:testing` by hand at `voyager/platform/build.gradle.kts:14`. Nothing checks that
  the two agree, so a bump in one can leave the other behind.
- `renovate.json:1-8` extends three organisation presets and has no `packageRules`. This repository therefore has no rule that asks for
  approval before a Minestom, extension or LuckPerms update opens. The owner's approval gate exists only as prose.
- The CloudNet deployment design says Renovate must not raise the `minestom-extensions` fork without a boot test
  (`openspec/changes/add-cloudnet-deployment/design.md:195-201`, risk R2). No such test exists. The release workflow builds the shadow
  JAR (`.github/workflows/release-please.yml:53`), but no pull request boots it: `build.yml` runs unit and integration suites only.
- `CLAUDE.md:43` names the rebuild's Minestom pin as `2026.09.12-26.2`, while the build resolves `2026.10.07-26.2`
  (`voyager/platform/build.gradle.kts:7` and `:14`). `openspec/config.yaml:8` and `CLAUDE.md:111` name `2026.08.28-26.2`. Three
  documents, three versions; a reader cannot tell which one runs.

## What Changes

- `renovate.json` gains `packageRules` that set `dependencyDashboardApproval` for Minestom, Minestom extensions and the LuckPerms API
  and loader, so each update waits for the owner's approval on the dependency dashboard.
- A build check, `verifyMinestomPins`, fails when the test environment's Minestom version differs from the version the BOM resolves
  for `net.minestom:minestom`. It is a check on resolved versions, not a second pin.
- A CI job boots the shadow JAR on each pull request that touches the build or `voyager/`: it starts the JAR, waits up to a fixed
  bound for the ready line, and stops it. A failure to boot fails the check. Only the boot is asserted; the JAR's behaviour is left to
  the existing suites.
- `CLAUDE.md` names the Minestom version the build resolves, and `openspec/config.yaml` is corrected in the same way in a later
  change (it is outside this change's directory).

## Capabilities

### New Capabilities

- `build/dependency-pinning`: the approval gate for sensitive dependencies, the agreement check between the BOM and the test pin,
  and the boot smoke test that a pull request must pass.

### Modified Capabilities

None.

## Trade-offs

### Pros

- The owner's approval gate is enforced by tooling, not by a comment that a bot can ignore.
- A boot failure surfaces on the pull request, not on the node after a CloudNet restart.
- The version documents stop disagreeing, because one check fails when they do.

### Cons

- More manual approvals for the owner. Each Minestom or LuckPerms update waits on the dashboard.
- The BOM may not publish a test artifact that matches its own Minestom version. Then `verifyMinestomPins` compares the test pin with
  the main artifact and cannot make the test artifact follow the BOM. The check is still useful, but it is a comparison, not a fix.
- The boot smoke test needs a runner that can start the server, and it adds a few minutes to pull requests that touch the build.

## Out of scope

- Changing the Minestom or LuckPerms versions. This change only gates and checks them.
- The CloudNet bridge's own pins and the `minestom-extensions` fork's compatibility beyond the boot test.
- Changes to the organisation's Renovate presets.
- Changes to the legacy tree's pins (`settings.gradle.kts` catalog `minestom` alias for `legacy/`).
- Making the smoke test a required check. The owner decides that in branch protection.

## Open questions for the owner

- Who approves: the owner alone, or a named maintainer list? The approval is recorded on the dashboard, so the answer is a Renovate
  configuration, which this change does not fix.
- Should the boot smoke test be a required status check for the default branch?
