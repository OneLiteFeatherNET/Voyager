# Tasks

Commit types: `feat(server)` for the settings, the composition roots, the check and their tests; `docs(server)` for `docs/`. Each
behaviour gets a failing test (Red) before its production code (Green). Red and Green of one behaviour may share a commit, so
that every commit builds. Every test follows F.I.R.S.T.: no sleep, no system clock, no real environment or system property changed
(sources are passed as maps), files only in a `@TempDir`, a fresh fixture per test, log lines asserted through `LogCapture`. The
code pull request contains no file under `openspec/`, and no task is ticked in it.

## 1. Red: the secret file in the settings

- [ ] 1.1 Red: `ProxyForwardingSettingsFileTest` "a file's trimmed content is the secret, and the file beats the environment and the property" (`feat(server)`)
- [ ] 1.2 Red: `ProxyForwardingSettingsFileTest` "the environment's file key beats the property's file key" (`feat(server)`)
- [ ] 1.3 Red: `ProxyForwardingSettingsFileTest` "an empty file, or a file of whitespace, refuses the start, names the setting and the path, and never its content" (`feat(server)`)
- [ ] 1.4 Red: `ProxyForwardingSettingsFileTest` "a missing path, a directory and a file that is not UTF-8 each refuse the start and name the path, not the content" (`feat(server)`)
- [ ] 1.5 Red: `ProxyForwardingSettingsFileTest` "a blank file path refuses the start, naming its setting" (`feat(server)`)
- [ ] 1.6 Red: `ProxyForwardingSettingsFileTest` "a broken file is not bypassed by a valid environment secret" (`feat(server)`)
- [ ] 1.7 Red: `ProxyForwardingSettingsTest` "without a file, the environment still beats the property, as before" (regression, `feat(server)`)
- [ ] 1.8 Red: `ProxyForwardingSettingsTest` "the source of the secret is recorded: file, environment, property or none" (`feat(server)`)

## 2. Green: the settings

- [ ] 2.1 Green: `ProxyForwardingSettings` gets the file keys and `KEYS`, the `Source` enum and the second record component; `from` reads the file
  sources first; the file is read with `Files.readString` and `strip()`. `ProxySecretException` in `platform.proxy.exception`, with a
  `package-info` that declares `@NotNullByDefault`, extends `IllegalArgumentException` and has `setting()` (`feat(server)`)
- [ ] 2.2 Green: `ProxyForwardingSettings.fromLoader` reads `ProxyForwardingSettings.KEYS` through `loader.view` and the loader's
  environment (`feat(server)`)

## 3. Red: the warnings

- [ ] 3.1 Red: `ProxyForwardingWarningsTest` "a secret from the property yields one warning that names `VOYAGER_VELOCITY_SECRET_FILE` and never the value" (`feat(server)`)
- [ ] 3.2 Red: `ProxyForwardingWarningsTest` "a secret from a file or the environment yields no warning" (`feat(server)`)
- [ ] 3.3 Red: `ProxyForwardingWarningsTest` "no secret yields the offline warning, which names the file setting" (`feat(server)`)
- [ ] 3.4 Red: `VoyagerServerForwardingLogTest` "the boot logs the property warning through the server's logger, and no captured line contains the secret" (`LogCapture`, `feat(server)`)

## 4. Green: the warnings and the logs

- [ ] 4.1 Green: `ProxyForwardingSettings.warnings()` returns the WARN lines of the settings; `VoyagerServer.main` and `SetupServer`
  log each of them through their own logger, replacing the inline offline WARN (`feat(server)`)
- [ ] 4.2 Green: `VoyagerServer.logForwarding(ProxyForwardingSettings)` is the package-private method that 4.1 calls from `main`, so 3.4 can
  drive it (`feat(server)`)

## 5. Red: the configuration check

- [ ] 5.1 Red: `ConfigCheckForwardingTest` "a secret from the property is one WARNING under `voyager.velocity.secret`, and the report exits 0" (`feat(server)`)
- [ ] 5.2 Red: `ConfigCheckForwardingTest` "an unreadable secret file is one ERROR under `VOYAGER_VELOCITY_SECRET_FILE`, and the run exits 1" (`feat(server)`)
- [ ] 5.3 Red: `ConfigCheckForwardingTest` "the printed report for a broken file holds no part of the file's content" (`feat(server)`)
- [ ] 5.4 Red: `ConfigCheckForwardingTest` "a secret from a file produces no forwarding problem" (`feat(server)`)

## 6. Green: the configuration check

- [ ] 6.1 Green: `ConfigCheck.forwardingProblems(environment, properties, sources)` and `VoyagerServer` adds `ProxyForwardingSettings.KEYS`
  to `CHECK_KEYS` and passes the problems in the `extra` list of the check (`feat(server)`)

## 7. Docs

- [ ] 7.1 Write `docs/decisions/0048-velocity-secret-from-file.md` as Proposed, from D1 to D5 of design.md. The owner accepts it later (`docs(server)`)
- [ ] 7.2 `docs/reference/configuration.md`: add `voyager.velocity.secret.file` and `VOYAGER_VELOCITY_SECRET_FILE`; name the four
  sources in the velocity row and in "The one exception" section (`docs(server)`)
- [ ] 7.3 `docs/reference/config-check.md`: add the forwarding WARNING and ERROR lines (`docs(server)`)
- [ ] 7.4 `docs/guides/how-to-run-behind-a-proxy.md`: the four sources, a file with mode `0400` owned by the server user, the trimmed
  content, the new WARN line; no real secret in any sample output (`docs(server)`)
- [ ] 7.5 `docs/guides/how-to-deploy-on-cloudnet.md`: recommend a node-local file outside the service directory and the template, with
  the task environment variable that names it; keep `VOYAGER_VELOCITY_SECRET` as the alternative; replace the sentence that forbids a file in
  the service directory with the rule that the file lives outside it (`docs(server)`)

## 8. Verify

- [ ] 8.1 Targeted tests, one at a time under the shared lock: `flock ~/.cache/claude-heavy.lock ./gradlew :voyager:platform:test --tests '*ProxyForwarding*' :voyager:server:test --tests '*ConfigCheckForwarding*' --tests '*VoyagerServerForwardingLog*' --tests '*ConfigCharacterisation*'` (`feat(server)`)
- [ ] 8.2 `flock ~/.cache/claude-heavy.lock ./gradlew :voyager:fitness:test`; confirm `package-info` coverage for `platform.proxy.exception` (`feat(server)`)
- [ ] 8.3 `flock ~/.cache/claude-heavy.lock ./gradlew build` for both trees (`feat(server)`)
- [ ] 8.4 Smoke boot the shadow JAR in a scratch directory with the secret in a file (`VOYAGER_VELOCITY_SECRET_FILE`, mode `0400`), the
  apply-rules flags without `-Dvoyager.velocity.secret`, and `stop` on stdin. Expect `Listening on`, `Voyager started`, and exit 0. The
  secret value is never printed (`feat(server)`)
- [ ] 8.5 Configuration check on the same JAR with `voyager.config.check=true`: a file that is missing exits 1 and names the key; the
  property alone exits 0 with the WARNING. Quote the lines, without any value (`feat(server)`)

## 9. Ship

- [ ] 9.1 Open the code pull request under the title `feat(server): read the Velocity secret from a file`. Write the description as the
  operator sees it: the process list, the file, the refusal and the warning. Include the smoke lines (8.4) and the check lines (8.5). No file
  under `openspec/`, no session link (`feat(server)`)
