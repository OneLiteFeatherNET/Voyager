# Tasks

Commits: `docs(openspec)` for artifact edits, `feat(i18n)` for every behaviour change and its tests, `docs` for `docs/`
pages and the ADR. Every behaviour gets a failing test (Red) before its production code (Green). Every test follows
F.I.R.S.T.: no sleeps, no system clock, `GlobalTranslator` never installed by a test, resources read from the classpath.

Effort notes are for the Haiku implementation agents (owner instruction, 2026-10-12): `low` for mechanical edits, `medium`
for bounded code and bulk translation, `high` for anything that touches locale resolution or Minestom's rendering path.
Every agent works in its own worktree from the up-to-date `origin/main`.

## 1. Decisions

- [x] 1.1 Owner answers OQ-1 to OQ-4 in `proposal.md` and records them in `design.md` (`docs(openspec)`). Effort: low.
- [x] 1.2 Owner accepts ADR-0038 (Status Proposed to Accepted) (`docs`). Effort: low. Blocks 2.x.

## 2. Locale resolution (effort: high)

- [x] 2.1 Red: `LocaleChainTest` names the behaviours "a regional tag is tried before its language", "an unsupported language falls to en_US", "en_US is the only candidate for en_US", and "a language-only tag is tried once" (`feat(i18n)`).
- [x] 2.2 Green: add `LocaleChain` in `net.elytrarace.voyager.platform.text`, pure, no Minestom import (`feat(i18n)`).
- [x] 2.3 Red: `MessageCatalogueTest` names the behaviours "a German key answers in German", "a key only in English answers in English and reports the fallback", "a missing key never renders the dotted key", and "a catalogue holds one bundle per tag" (`feat(i18n)`). Uses in-memory `PropertyResourceBundle` values, no file I/O.
- [x] 2.4 Green: add `MessageCatalogue`, with the UTF-8 `Reader` loading and the per-key fallback (`feat(i18n)`).
- [x] 2.5 Spike, no commit: check in Minestom 2026.08.28-26.2 whether `MinestomAdventure.getDefaultLocale()` can be set at boot, and whether Minestom's built-in command errors are `Component.translatable` with vanilla keys (OQ-4); whether any `sendMessage`/`showTitle` overload takes a `String`. Record the answers in design.md "Locale source" and in ADR-0038. Owner: `voyager-minestom-expert`.
- [x] 2.6 Red: `VoyagerTranslatorTest` names the behaviour "a German player gets the German rendering of a key", and "the translator refuses a German value that drops an argument", both through `translate(TranslatableComponent, Locale)` (`feat(i18n)`).
- [x] 2.7 Green: `VoyagerTranslator` delegates `canTranslate` and `translate` to `MessageCatalogue` with the player's locale; keeps `{N}` refusal and the `install()` call only in composition roots (`feat(i18n)`).
- [x] 2.8 Green, if 2.5 says yes: set the default locale to `en_US` in `VoyagerServer` and `SetupServer` at boot, with a test that a player without settings is shown English (`feat(i18n)`).

## 3. Setup-server fix (effort: medium)

- [x] 3.1 Red: a case in `SetupCommandsTest` renders `voyager.command.denied` through the setup translator and asserts English text, not the dotted key. It fails today (`feat(i18n)`).
- [x] 3.2 Green: define `voyager.command.denied` in `voyager_setup_en_US.properties`, so the setup server's bundle holds every key it sends (`feat(i18n)`).

## 4. German bundles (effort: medium, bulk translation; owner reviews before merge)

- [x] 4.1 Create `voyager_de_DE.properties` with the 46 server keys, in the same order as the English file. Keep every `<arg:N>`, `<c-*>` and `<m-*>` tag; do not add or drop any. Use `du` unless OQ-2 says otherwise (`feat(i18n)`).
- [x] 4.2 Create `voyager_setup_de_DE.properties` with the 21 setup keys (including the 3.2 key), same rules (`feat(i18n)`).
- [ ] 4.3 Owner native review of both files, recorded in the PR description. No merge before (`docs` for the review record).

## 5. Fitness (effort: medium)

- [x] 5.1 Red: `MessageBundleParityTest` in `voyager/fitness`, with a seeded German bundle fixture in a test resource, names the behaviours "a missing key fails and names the key and the locale", "an extra key fails", "a changed placeholder fails", "a `{0}` fails", and "a complete pair passes" (`feat(i18n)`). Uses the pure catalogue, not `GlobalTranslator`.
- [x] 5.2 Green: implement the parity checks; the real bundles must pass (`feat(i18n)`). Then run `CupSessionGoldenMasterTest`, and record in the PR that the transcripts are byte-identical.
- [x] 5.3 Red: `ApiPurityTest` gains one seeded violation per extension (literal `Component.translatable` key; `MiniMessage.deserialize` outside `platform.text`), each proving its rule is not vacuous (`feat(i18n)`).
- [x] 5.4 Green: extend `onlyTheTextPackageBuildsAUserFacingString` to those two calls, and to `String` overloads if 2.5 found any (`feat(i18n)`).
- [x] 5.5 Decide the `WaitingRoom.describe()` exemption: name it in the rule with its reason, or turn it into a key. Red before Green, under the same commit type (`feat(i18n)`). Owner: OQ-1 answer applies.

## 6. Documentation (effort: low)

- [x] 6.1 Update `docs/guides/how-to-add-a-translation.md`: the two locales, the fallback chain, the parity test, and the plural keys (`docs`).
- [x] 6.2 Accept ADR-0038 after 1.2 and record the 2.5 findings in it (`docs`).

## 7. Verification

- [x] 7.1 `./gradlew build` (both trees) is green (`docs(openspec)` if only artifacts changed, otherwise no commit). Effort: low.
- [x] 7.2 `openspec validate localise-player-messages --strict` passes (`docs(openspec)`). Effort: low.
- [ ] 7.3 Boot smoke: `./gradlew :voyager:server:runServer` and `:voyager:setup:runSetupDev` with a German client and an English client; the German client reads German, the English client reads English, and no dotted key appears in the log of either. Recorded in the PR description. Effort: medium.

## 8. Pull request

- [ ] 8.1 Open the pull request. Title: `feat(i18n): localise player messages`. The PR body names this change, ADR-0038, the owner's review of the German files, and the golden-master result. Its footer is exactly these two lines:

  ```
  https://claude.ai/referral/m5Ak2Sa7aQ
  https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz
  ```

  Commits in the PR carry the trailer `Claude-Session: https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`. Effort: low.
