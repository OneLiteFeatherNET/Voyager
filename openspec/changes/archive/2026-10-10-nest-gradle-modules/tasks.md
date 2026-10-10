# Tasks

## 1. Baseline

- [x] 1.1 Keep `backup/main-before-nesting-2026-10-10` pointing at `main`, and work on `build/nest-gradle-modules`.
- [x] 1.2 Record the clean baseline: `CI=true ./gradlew clean build --continue` on `main` content, with test XML counts
      (tests, failures, errors, skipped) per module.

## 2. Proposal

- [x] 2.1 Commit this change as `docs(openspec): propose nest-gradle-modules`.

## 3. Nest the rebuild under voyager/

- [x] 3.1 `git mv` the seven `voyager-*` module directories into `voyager/`, and check that untracked build output moves with them.
- [x] 3.2 Update `settings.gradle.kts` includes to `voyager:api` and the other six, and add `base { archivesName = "voyager-<name>" }` to each rebuild module.
- [x] 3.3 Update every `project(":voyager-…")` dependency, the `tools/map-converter` dependency and the fitness module's dependencies to the new paths.
- [x] 3.4 Fix the path literals in code and resources: `FileSchemaTest`, the shipped map and cup `$schema` values and `$id` URLs,
      `ConverterOptionsTest`, and the comments and tasks that name `voyager-server/...`.
- [x] 3.5 Make the fitness build select rebuild modules by project path and pass the path with `/` for `:`; update `FitnessCoverageTest`'s map keys.
- [x] 3.6 Verify: the fitness and platform tests pass; the Golden Master and the ArchUnit store are byte-identical to `main`;
      the rebuild's test counts match the baseline. Commit as `build: nest the rebuild under voyager/`.

## 4. Nest the old tree under legacy/

- [x] 4.1 `git mv` `server`, `plugins/game`, `plugins/setup` and `shared/*` into `legacy/`, moving `plugins/setup/run/` with its module.
- [x] 4.2 Update `settings.gradle.kts` includes to `legacy:server`, `legacy:plugins:game`, `legacy:plugins:setup` and `legacy:shared:*`,
      and every `project(":shared:…")` and `project(":server")` reference inside the old tree.
- [x] 4.3 Update the CI workflows, `docker/server/Dockerfile` and `release-please.yml` (`:legacy:server:shadowJar` and the artifact paths).
- [x] 4.4 Verify: the old tree's test counts match the baseline; `./gradlew :legacy:server:shadowJar` produces `server-<version>.jar`.
      Commit as `build: nest the old tree under legacy/`.

## 5. Ignore rules

- [x] 5.1 Add `.idea/`, `*.iml`, `graphify-out/`, `.claude/scheduled_tasks.lock`, `/legacy/plugins/setup/run/` and
      `/docker/mariadb/mariadb/` to `.gitignore`, with a trailing newline. Commit as `chore(git): ignore ide state and tool output`.

## 6. Documentation

- [x] 6.1 Update `CLAUDE.md` (module table, build commands, the old tree's paths, module isolation), `README.md`, the active pages under
      `docs/**`, `.claude/skills`, `.claude/commands` and `openspec/config.yaml`. Archived changes, ADR texts and research history
      keep their texts apart from broken links. Commit as `docs: point the docs at the nested module paths`.

## 7. Validate and merge

- [x] 7.1 Run the verification set: `CI=true ./gradlew build` (both trees) with the test counts compared to the baseline, the fitness
      test, `validateCatalog`, the two smoke runs (`:voyager:server:runServer` on 25565 and `:voyager:setup:runSetupDev` on 25566), and
      `openspec validate nest-gradle-modules --strict` and `openspec validate --specs --strict`.
- [x] 7.2 Merge locally with `git merge --no-ff build/nest-gradle-modules -m "merge: nest-gradle-modules"`, archive the change with
      `openspec archive nest-gradle-modules --yes`, and commit `docs(openspec): archive nest-gradle-modules`.
- [x] 7.3 Open the pull request titled `build: nest the rebuild under voyager/ and the old tree under legacy/`. Not applicable: the owner
      decided to merge locally, so no pull request is opened.
