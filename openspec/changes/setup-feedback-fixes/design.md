# Design

## Context

`SetupCommands` runs each subcommand with a `try` around the domain call. Five catch blocks take the domain exception
and send `SetupMessages.refused(exception.getMessage())`. The exception message is built in the domain
(`DraftNotFoundException.java:14`) and is English. The bundle wraps it in "Abgelehnt: <arg:0>", so the German builder reads
English inside a German sentence.

`ImportCommand.refusalLine` (`ImportCommand.java:182-194`) already maps each `WorldImportRefusedReason` to a key with its
arguments, and no raw text reaches a builder from the import path except `importFailed` (`:143`). This change applies the
same pattern to the draft and map commands.

## Goals / Non-Goals

Goals:

- Every refusal a builder can cause is one key with arguments, in both languages.
- No `refused` call takes raw text; the overload is removed when no caller remains.
- A wand click outside the open world is answered, once per 5 seconds per map.

Non-goals:

- Changing the domain exceptions or their messages.
- Changing command syntax or permissions.

## Decisions

### Map each exception type in the adapter

A private method per command maps a domain exception to a `Component` by type, for example
`DraftNotFoundException` to `SetupMessages.draftNotFound(id, hint)`. The mapping lives in the adapter, next to the
command, because it is presentation. The domain stays free of text.

Alternative considered: give each domain exception a stable error code and map codes in one table. Pro: one table, no
per-command switch. Con: it changes the domain's exception API for a presentation concern and touches the API module,
which this change does not need. Rejected for this change; it can come later if a second adapter needs the codes.

### Remove the raw overload

`SetupMessages.refused(String)` is removed when the five call sites are gone, so the compiler rejects any new raw-text
refusal. An ArchUnit rule in `voyager-fitness` also forbids any class in the setup adapter from calling `refused` with the
result of `getMessage()`; the rule is `allowEmptyShould(false)`, so a renamed method fails loudly instead of passing.

### Wand answer throttle

`WandListener` keeps, per builder UUID, the instant and map id of the last "wrong world" answer. A second answer for the same map within 5 seconds is suppressed. The clock is injected (`java.time.Clock`), so the test advances a fake clock instead of sleeping. The memory is in the listener, dropped when the builder disconnects, and holds one entry per builder.

## Dependency direction and modules

- `SetupCommands`, `WandListener` (adapter, `voyager/setup`) depend on the domain exceptions in `voyager/api` and on
  `SetupMessages` in `platform/text`. Nothing in `api` depends on the adapter.
- The new `Clock` is injected through the composition root `voyager-setup`; DI annotations stay there (ADR-0016).

## Architecture rules

- New boundary: "no `refused` call takes the message of an exception in the setup adapter". Covered by a new ArchUnit
  rule in `voyager/fitness`, `allowEmptyShould(false)`.
- Existing `setupDomainOnlyDependsOnApiAndJdk` and `MessageBundleParityTest` keep covering the rest.

## Open questions

- `ImportCommand` line 143 passes `String.valueOf(failure.getMessage())` into `importFailed`. Fix it in this change, or in
  a follow-up? Recommendation: follow-up, because the import reasons already have keys and the fallback is rare.
- Should the wand "wrong world" line name the open map's world or only the map id? Recommendation: the map id, which
  the builder typed.
