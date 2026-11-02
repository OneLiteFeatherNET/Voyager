# Design

## Context

### Facts the design relies on (read from the code on `main`, 4585754)

- `ProxyForwardingSettings` (`voyager/platform`, `platform.proxy`) is a record with one component, `secret`. Its `from(environment,
  properties)` reads `VOYAGER_VELOCITY_SECRET`, then `voyager.velocity.secret`, and refuses a set but blank value through
  `IllegalArgumentException`. Its `fromLoader(ConfigLoader)` passes the loader's environment and the property through `get`.
- `ConfigLoader` (`platform.config`) is the one place that builds an avaje-config `Configuration`. `get(key)` already resolves the
  environment variable of a key, so `VOYAGER_VELOCITY_SECRET_FILE` is found for the key `voyager.velocity.secret.file` with no
  new mapping. `view(keys)` returns the set keys and their values, and `environment()` returns the environment map.
- The composition roots that read the secret are `VoyagerServer.main` (`voyager/server`) and `SetupServer` (`voyager/setup`).
  Both call `ProxyForwardingSettings.fromLoader(...)`, pass the result to `VelocityAuth.of`, and log the offline WARN.
- `VoyagerServer.configCheck` is the validate-and-exit run. It prints `ConfigProblem`s through `ConfigCheck.report` and returns
  the exit code. Its key list is `CHECK_KEYS`, the server's and the database's keys. The forwarding keys are not in it today.
- `PersistenceSettings` (`voyager/server` `config`) is the precedent for a file-held secret: `VOYAGER_DB_PASSWORD_FILE` names a
  path, the content is read once, a blank or unreadable file becomes one message that names the path, and no value is printed.
- `InvalidSettingException` (`server.config.exception`) is the precedent for an exception that carries the setting it is about, so
  the configuration check can key its problem under that setting.
- `LogCapture` (`voyager/server` tests) attaches an appender to one class's logger for one test.

### Live facts

- The CloudNet task `ElytraRace` sets `VOYAGER_VELOCITY_SECRET` or `-Dvoyager.velocity.secret` through the node's JVM options, as
  `add-cloudnet-deployment` records. Removing the property would stop that service from starting, so the property stays.

## Goals and non-goals

Goals: the secret can come from a file with restrictive permissions; the file is the preferred source and is reported as such; a
broken file refuses the start; the property keeps working with a warning; the secret never appears in a log, a report or an
exception message.

Non-goals: a permission check on the file, a runtime reload, removing the property, changing the Velocity proxy's configuration,
and any change to the bridge or the CloudNet task.

## Decisions

### D1. Keys and precedence

| Rank | Source | Key |
|---|---|---|
| 1 | The file named by the environment variable | `VOYAGER_VELOCITY_SECRET_FILE` |
| 2 | The file named by the system property | `voyager.velocity.secret.file` |
| 3 | The environment variable | `VOYAGER_VELOCITY_SECRET` |
| 4 | The system property | `voyager.velocity.secret` |

The first source that is set decides. A lower source is not read once a higher one is set, so a file that is set and broken
refuses the start even when the environment variable holds a valid secret. This is the ADR-0025 rule: a misconfigured secret
never falls back.

### D2. Reading the file

`ProxyForwardingSettings` reads the file once, when the settings are built. The rules:

- The path is trimmed of surrounding whitespace. A blank path refuses the start and names the setting.
- The file is read as UTF-8 and trimmed of leading and trailing whitespace (`String.strip()`). The trimmed value is the secret.
- An empty result refuses the start. The message names the setting and the path.
- A missing file, a directory, a file the process cannot read, or content that is not UTF-8 refuses the start. The message names the
  setting, the path and the exception's simple class name. It never names the content, and the cause is attached only as the
  exception cause, which carries no content.

The refusal is an exception type `ProxySecretException`, which extends `IllegalArgumentException` as `InvalidSettingException`
does, and has `setting()` returning the setting's name. The exception is in `platform.proxy.exception`, with a `package-info` that
declares `@NotNullByDefault`.

### D3. The record keeps the origin of the secret

`ProxyForwardingSettings` gains a second component, `source`, of a nested enum `Source` (`NONE`, `FILE`, `ENVIRONMENT`,
`PROPERTY`). The two-argument record is the canonical form; the one-argument form is not kept, because only the class's own
factories construct it. The origin lets the composition roots and the configuration check say that the secret came from the
property. `toString` stays masked and names the origin only as `forwarding` or `offline`.

### D4. The warnings are strings the roots log, and the check labels

`ProxyForwardingSettings.warnings()` returns the WARN lines of the settings as strings, with no value in them:

- With no secret: the offline line, which now names `VOYAGER_VELOCITY_SECRET_FILE` with the other two sources.
- With the secret from the property: one line that names `voyager.velocity.secret` and recommends `VOYAGER_VELOCITY_SECRET_FILE`.
- With the secret from the file or the environment: none.

Both composition roots log these lines with their own logger, so the messages are the same and are written once. Platform does not
log; it returns the text. `ConfigCheck` builds a `ConfigProblem` of severity WARNING from the property line, keyed under
`voyager.velocity.secret` and labelled by the configuration loader's source, so the report can say where the property was set.

### D5. The configuration check

`ConfigCheck.forwardingProblems(environment, properties, sources)` builds the settings and returns:

- one ERROR, keyed under `ProxySecretException.setting()` and labelled by its source, when the settings refuse;
- one WARNING for a secret from the property, as in D4;
- nothing otherwise.

`VoyagerServer` adds `ProxyForwardingSettings.KEYS` to `CHECK_KEYS` and passes the forwarding problems into the same `extra` list
the database warnings use. Because `ConfigCheck.report` counts errors across that list, an unreadable file gives exit status 1 and
a property secret gives exit status 0 with the WARNING printed. `ServerSettings.KEYS` is not changed: the forwarding keys are not
server settings, and the check reads them through its own key list.

### D6. Where each type lives and what covers it

| Type | Module and package | Dependency direction |
|---|---|---|
| `ProxyForwardingSettings` (changed) | `voyager-platform`, `platform.proxy` | depends on `platform.config.ConfigLoader` and the JDK only |
| `ProxySecretException` (new) | `voyager-platform`, `platform.proxy.exception` | no dependency |
| `ConfigCheck.forwardingProblems` (changed) | `voyager-server`, `server.config` | server depends on platform |
| `VoyagerServer`, `SetupServer` (changed) | composition roots | depend on platform, never the reverse |

The fitness rules that apply are the existing ones: `ConfigurationBoundaryTest` (configuration is read once, through
`ConfigLoader`; the file read is a read of a secret, not of configuration, and is not a second configuration source), the
`NullabilityConventionTest` rule for the new package (`package-info` with `@NotNullByDefault`), and the slice boundary rules for
`platform.proxy`. No new fitness rule is added. A new `package-info` is required and is part of the change.

### D7. Testing

Every behaviour gets a failing test first. Tests pass the environment and the properties as maps, so no test changes the process
environment or a system property. Files are created in a `@TempDir`. The log assertion uses `LogCapture` on `VoyagerServer`'s
logger, and the test asserts that the secret's value appears in no captured line. The configuration check tests assert the printed
report, which is what an operator reads, and that the report holds no value. No test sleeps or reads the system clock.

### D8. Documentation

The configuration reference gains a row for `voyager.velocity.secret.file`, and the velocity row names the new precedence. The
proxy how-to lists the four sources and the file's permissions. The CloudNet how-to recommends a node-local file outside the
service directory, owned by the server user with mode `0400`, and names the task's environment variable that points at it. The
how-to says why the file is not placed in the template: the template is copied into every service directory, and each stop
removes that copy. ADR-0048 records the decision as Proposed.

## Risks

- A file the operator sets by mistake refuses the start. This is intended: the refusal is at boot, names the path, and the
  configuration check shows it before a start.
- Avaje-config may resolve a `application.yaml` value for the file key. The file key is then read from the file, and the
  environment still wins. The precedence test covers the environment and the property, and the working-directory file is named in
  the documentation.
- A file that is readable by other users is not refused. The documentation tells the operator to use mode `0400`; a warning is a
  follow-up.
