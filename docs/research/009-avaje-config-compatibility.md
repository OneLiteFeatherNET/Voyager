# Research 009: compatibility of the configuration loader on avaje-config 5.2

Status: measured with the change `adopt-avaje-config` (ADR-0036). The loader replaces four call sites that read the JVM
directly. This note records how avaje-config 5.2 resolves a value, what the migration had to preserve, and the measured
compatibility of the configuration check.

## Question

Can the server's settings move to avaje-config without a change in any existing deployment: the same keys, names, defaults,
refusals and configuration-check report for every input the release accepts? If so, what does avaje-config add, and which of
its behaviours had to be pinned?

## Method

1. **Spike on the library source.** The avaje-config 5.2 sources jar, read rather than assumed. The builder, the value
   lookups, the environment-name mapping and the logging path were read; each finding is recorded in the change's
   `design.md`, section "Spike findings".
2. **Characterisation before the change.** Four refusal behaviours were pinned against the release code
   (`ConfigCharacterisationTest`), so they could not change silently.
3. **Golden matrix.** Thirty-three configuration-check runs (`voyager/server/src/test/resources/config-golden/run-matrix.sh`):
   defaults, each key set and unset, invalid numbers, blank values, both directories, the cup, the lobby pair, dev mode, the
   command-line arguments, an unrelated property, and a `${HOME}` value. Each run uses an empty environment, a fresh work
   directory and normalised output. The transcripts were generated once from the release jar of `main` and committed.
4. **Smoke boots.** The new server jar was booted twice on the same data and worlds directories: once with system
   properties, once with the same values as environment variables. Both runs were stopped with `stop`.

## Findings from the library

- A value set in a system property or in the environment overrides a file value of the same key. A key that no file sets is
  looked up when first read, with the same order: system property, then environment. So the precedence is the one the design
  asks for, and no merge was written.
- The environment name of a key is its name in upper case with dots turned into underscores. Every existing environment name
  of the release (`VOYAGER_VELOCITY_SECRET`) is therefore unchanged, and the bind keys gain `SERVICE_BIND_HOST` and
  `SERVICE_BIND_PORT`.
- File values are trimmed. Values from the environment and from system properties are not. The release did not trim either,
  so a blank value keeps its meaning.
- `${...}` is expanded only in values the library holds in its source map. For a key that no file sets, the library returns a
  system property or environment value as written. The golden case `dollar-brace-value` checks this against the release.
- The library's own messages went to the JDK logger on stderr. That is a visible change to the configuration check's output,
  and it was found by the golden matrix, not by reading the code. The loader routes the library's log to the server's
  logger, at debug for routine messages and at warn for warnings.
- Titan's caveat about enumerated sections does not apply: the loader reads named keys only.

## Results

- Golden matrix, release jar against the new jar: identical. All 33 transcripts match byte for byte after normalisation. Four
  cases exit 0 and 29 exit 1, because the shipped catalogue has two cups and the repository world set is partial; both
  outcomes are part of the expected output.
- Smoke boots, system properties and environment: both start, print the same "Voyager (rebuild)" line, log "Voyager started"
  and exit 0 on `stop`. The lines are compared byte for byte in the pull request.
- The configuration check's report, for every case that exits with a problem, names the source of each setting. For a setting
  from the environment it names the variable; for a setting from a system property it names the property, as before.

## Limits

- The matrix covers the configuration check, not a boot. The boot path is covered by the smoke runs and by the child-JVM
  precedence tests.
- The matrix runs with one catalogue and one world, linked under every map's name. Its region data is the same for each name.
  A check on real, distinct worlds is operator work and is not claimed here.
- The environment names are new readers of old names. An operator who sets `VOYAGER_CUP` or `SERVICE_BIND_PORT` in the
  environment for another reason now changes the server's behaviour. The configuration check shows the source of each value.

## Consequence

The move is compatible for every input in the matrix. The loader is the one place that reads the JVM, the velocity secret keeps
its documented order, and the library's behaviours that the design does not want are pinned by tests rather than by
convention.
