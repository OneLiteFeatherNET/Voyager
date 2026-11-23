# Spec Delta

## Purpose

The dependencies that can break a live server without a code change (Minestom, its extensions, and the LuckPerms loader) cannot be
updated by automation without the owner's approval. The build keeps the test environment's Minestom version equal to the production
version, and a dependency update cannot merge until the shipped server JAR has booted in CI.

## ADDED Requirements

### Requirement: Renovate waits for the owner's dashboard approval for sensitive pins
**MoSCoW:** Must. WHEN Renovate finds a newer version of the Minestom pin, a Minestom extension pin, or the LuckPerms loader pin,
THE SYSTEM SHALL NOT open an update pull request for it until the owner approves that update on the Renovate dependency dashboard.

#### Scenario: a Minestom update is found
- **WHEN** Renovate finds a newer `net.minestom` version
- **THEN** no update pull request opens until the owner approves it on the dependency dashboard

#### Scenario: a Minestom extension update is found
- **WHEN** Renovate finds a newer version of a Minestom extension pin
- **THEN** no update pull request opens until the owner approves it on the dependency dashboard

#### Scenario: a LuckPerms loader update is found
- **WHEN** Renovate finds a newer `net.luckperms` `minestom-loader` version
- **THEN** no update pull request opens until the owner approves it on the dependency dashboard

#### Scenario: an unrelated update is found
- **WHEN** Renovate finds a newer version of a dependency outside those pins
- **THEN** the update opens as it did before this change

### Requirement: The test Minestom version cannot differ from the production version
**MoSCoW:** Must. THE BUILD SHALL NOT allow the version of the test environment's `net.minestom:testing` to differ from the version of
`net.minestom:minestom` that the production classpath resolves; IF the two differ, THEN THE BUILD SHALL fail the verification task and
name both versions. The build MAY instead derive the test version from the resolved production version.

#### Scenario: the test pin matches production
- **WHEN** the test pin and the resolved production version are the same
- **THEN** the verification task passes

#### Scenario: the test pin differs from production
- **WHEN** the test pin is one version and the production classpath resolves another
- **THEN** the verification task fails and its message names both versions

#### Scenario: the test version is derived
- **WHEN** the build derives the test version from the resolved production version
- **THEN** the scenario above cannot occur, because the two are the same value by construction

### Requirement: CI boots the shipped JAR before a dependency update can merge
**MoSCoW:** Must. WHEN a pull request changes a dependency pin or any build file, THE SYSTEM SHALL build the shadow JAR of
`voyager-server`, start it, and report failure unless the server prints its ready line within a fixed bound, after which the server
is stopped, so that a dependency update is never merged with a JAR that does not boot.

#### Scenario: the JAR boots
- **WHEN** the shadow JAR starts and prints its ready line within the bound
- **THEN** the boot check passes and the server is stopped

#### Scenario: the JAR fails to boot
- **WHEN** the shadow JAR exits, or does not print its ready line within the bound
- **THEN** the boot check fails and the job log holds the last 200 lines of server output

#### Scenario: a documentation-only pull request
- **WHEN** a pull request changes only files under `docs/` or `openspec/`
- **THEN** the boot check is skipped
