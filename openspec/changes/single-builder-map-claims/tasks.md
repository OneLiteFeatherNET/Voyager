# Tasks

Commit types and scope: every behaviour ships as `feat(setup)`, including the save guard in `platform/catalog`, because the
guard is part of the setup server's behaviour. `docs` for the how-to guides and the ADR move. `test` only for a test-only
commit that changes no behaviour. Each behaviour gets a failing test (Red) before its production code (Green). Every test
follows F.I.R.S.T.: a fresh `EditClaims` or `Env` per test, `@TempDir` for files, fixed UUIDs, no sleeps, no system-time
reads, no shared static state. Two `JsonDraftStore` instances over one directory stand for two setup servers. No question in
`design.md` blocks these tasks. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the claim registry and the save guard

- [ ] 1.1 Red: `EditClaimsTest` asserts that a free key is granted, a second builder is refused with the first holder's name, and the same builder's second claim is granted without a change of holder (`feat(setup)`)
- [ ] 1.2 Red: `EditClaimsTest` asserts that a release by a builder who does not hold the key changes nothing, and that releasing all keys of one builder frees only that builder's keys (`feat(setup)`)
- [ ] 1.3 Red: `EditClaimsTest` asserts that the key `map:alpha` and the key `cup:alpha` are independent claims (`feat(setup)`)
- [ ] 1.4 Red: `JsonDraftStoreTest` with two stores over one `@TempDir`: the second store saves a changed draft, then the first store's `saveIfUnchanged` refuses with `DraftChangedException`, and the file keeps the second store's bytes (`feat(setup)`)
- [ ] 1.5 Red: `JsonDraftStoreTest`: a stored copy that differs from the expected draft only in whitespace and key order is accepted, because the comparison is on canonical JSON (`feat(setup)`)
- [ ] 1.6 Red: `JsonDraftStoreTest`: `saveIfUnchanged` refuses with `DraftChangedException` when the stored copy is missing, and creates no file (`feat(setup)`)
- [ ] 1.7 Red: `EditClaimsTest` asserts that `unlock` of a held key frees it whoever holds it and returns the released holder, and that `unlock` of a free key returns nothing (`feat(setup)`)

## 2. Red: the commands and the session lifecycle

- [ ] 2.1 Red: Minestom test (`@EnvTest`): builder B's `/map open skyfortress` while builder A holds it is refused, the refusal names A, B has no session, and the draft file keeps its bytes (`feat(setup)`)
- [ ] 2.2 Red: Minestom test: the holder runs `/map open` for the map it already holds; the draft is not reloaded, the session object is the same, and the reply is the opened message (`feat(setup)`)
- [ ] 2.3 Red: Minestom test: builder A opens `skyfortress`, then `canyon`; builder B can then open `skyfortress` (`feat(setup)`)
- [ ] 2.4 Red: Minestom test: a refused `/map open` of `canyon` leaves builder A on `skyfortress` with its claim (`feat(setup)`)
- [ ] 2.5 Red: Minestom test: when builder A disconnects, builder B can open the map A held (`feat(setup)`)
- [ ] 2.6 Red: Minestom test: `/map close` releases the claim, removes the previews, leaves the builder with no open map, and replies with the closed message; `/map spawn` after it replies that no map is open (`feat(setup)`)
- [ ] 2.7 Red: Minestom test: a map whose world cannot be looked up takes no claim (`feat(setup)`)
- [ ] 2.8 Red: `SetupPermissionTest`: a builder without `voyager.setup.use` cannot run `/map open` or `/map close`, holds no claim and changes no file; the existing `/map new` test stays, and one test each is added for `open`, `close`, `unlock`, `spawn` and `status` (`feat(setup)`)
- [ ] 2.9 Red: `MapSession` test: a commit refused by the guard throws `DraftChangedException` and the session keeps the draft it had; the command replies with the save-changed message (`feat(setup)`)
- [ ] 2.10 Red: Minestom test (`@EnvTest`): a sender with `voyager.setup.unlock` runs `/map unlock skyfortress` while builder A holds it; the claim is released, A has no open map and its previews are gone, B can open `skyfortress`, and the reply names A (`feat(setup)`)
- [ ] 2.11 Red: Minestom test: a builder without `voyager.setup.unlock` runs `/map unlock skyfortress` while A holds it; the command is denied, A still holds the map with its session, and the draft file keeps its bytes (`feat(setup)`)
- [ ] 2.12 Red: Minestom test: `/map unlock canyon` for a map nobody holds replies that the map is not held and writes no file (`feat(setup)`)
- [ ] 2.13 Red: confirm 1.1 to 2.12 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 3. Green: the registry and the guard

- [ ] 3.1 Green: `EditClaims` with the nested `Holder` record in `net.elytrarace.voyager.setup.mapsetup`, with `package-info.java` already present (`feat(setup)`)
- [ ] 3.2 Green: `DraftChangedException` (final, `RuntimeException`) in `net.elytrarace.voyager.api.mapsetup.exception` with its factory method; `DraftStore.saveIfUnchanged(MapDraft expected, MapDraft next)` in the port (`feat(setup)`)
- [ ] 3.3 Green: `JsonDraftStore.saveIfUnchanged`: load the stored copy, compare the canonical JSON of both drafts with `MapDraftJsonWriter.toJson`, refuse on a difference or a missing copy, otherwise call `save` (`feat(setup)`)
- [ ] 3.4 Green: `MapSession.commit` calls `saveIfUnchanged(draft, next)`; `WandListener` and `SetupCommands.spawn` catch `DraftChangedException` before `DraftWriteFailedException` (`feat(setup)`)
- [ ] 3.5 Green: `BuilderSessions` takes `EditClaims` in its constructor; `open` releases the previous session's claim after the new one is granted; `forget` releases the builder's claim; `SetupBeans` creates the bean (`feat(setup)`)
- [ ] 3.6 Green: `SetupCommands.open` in the order of decision 3 of `design.md`: the same-map no-op, then load, world lookup, claim, install; `create` claims the new id through the same install step (`feat(setup)`)
- [ ] 3.7 Green: `/map close` as a gated subcommand: removes the previews, releases the claim, forgets the session (`feat(setup)`)
- [ ] 3.8 Green: messages `voyager.setup.claim.held` (arguments: map id, holder name), `voyager.setup.map.closed` (argument: map id), `voyager.setup.map.unlocked` (arguments: map id, holder name), `voyager.setup.map.unheld` (argument: map id) and `voyager.setup.save.changed` (argument: map id), added to `SetupMessages` in `platform/text` and to `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties`, the German in informal "du" (`feat(setup)`)
- [ ] 3.9 Refactor: update the tests that construct `BuilderSessions` with `new BuilderSessions()` to pass a fresh `EditClaims`; change no behaviour (`feat(setup)`)
- [ ] 3.10 Green: `PermissionNode.VOYAGER_SETUP_UNLOCK("voyager.setup.unlock")` in `api/permission`, with the existing `package-info.java` unchanged (`feat(setup)`)
- [ ] 3.11 Green: `EditClaims.unlock(key)`; `BuilderSessions` ends the session of the released holder; `/map unlock <id>` as a subcommand gated by `voyager.setup.unlock` alone, as decision 9 of `design.md` states (`feat(setup)`)

## 4. Docs and decision record

- [ ] 4.1 Move ADR-0044 from `design.md` to `docs/decisions/0044-single-editor-map-claims.md`, with status Proposed unless the owner accepts it (`docs`)
- [ ] 4.2 Add a section on sharing a map, on `/map close` and on `/map unlock` to `docs/guides/how-to-build-a-map-with-setup.md` (`docs`)
- [ ] 4.3 Document the LuckPerms group `builder` with the node `voyager.setup.use`, and the group that carries `voyager.setup.unlock`, in `docs/guides/how-to-grant-permissions.md`, as operator steps, not code (`docs`)

## 5. Verify

- [ ] 5.1 Run `./gradlew :voyager:setup:test :voyager:platform:test :voyager:fitness:test` and confirm `setupDomainOnlyDependsOnApiAndJdk` and `DesignRuleTest` pass (`test`)
- [ ] 5.2 Run `./gradlew build` on both trees (`test`)

## 6. Ship

- [ ] 6.1 Open the code pull request under the title `feat(setup): allow one builder to edit a map at a time`. Fill the sections of `.github/pull_request_template.md`, tell the story of the two builders from the proposal, list the messages and the failing tests of 2.13, and end with the referral line of the repository's pull request rule. Do not add a session link (`feat(setup)`)
