# ADR-0038: Player messages are translated per client locale, with English as the fallback

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The changes that
implement it are `localise-player-messages` (feat(i18n)) and, for the wording and colour rules it refers to, `improve-message-ux`
(feat(race)). Neither change is applied before this record is accepted.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided that all chat messages and all player-facing text are internationalised
  in German and English, that the language follows the player's client locale, and that English is the fallback.
- Drafted with the change `localise-player-messages`, from the inventory of 66 translation keys and the review of the
  rebuild's message layer at `origin/main` 6635220.

## Context and problem statement

The rebuild already renders every player-facing line from a translation key (`Messages`, `SetupMessages`) through an
Adventure `GlobalTranslator` source (`VoyagerTranslator`), with MiniMessage `<arg:N>` placeholders and palette tags
(`Palette`). The bundle is English only, and `VoyagerTranslator.translate(..., Locale)` ignores its locale argument.

Minestom renders a translatable component per player when `minestom.automatic-component-translation` is on, using the
player's client locale (DeepWiki, to verify in the source). Without the flag, the client receives dotted keys. The server
refuses to boot without the flag.

Two gaps make a second language more than a new file. The setup server sends a key (`voyager.command.denied`) that is
defined only in the server bundle, so its players see the dotted key. And a few operator strings are English prose outside
the translation layer, which no translator can reach.

We need to decide how the second language is stored, how the locale is chosen and what the fallback chain is, and how the
build keeps every locale complete.

## Decision drivers

- Every key defined in English exists in German, with the same placeholders and palette tags, and the build proves it.
- A player whose client sends no locale, or an unsupported one, is shown English, not the server host's JVM default.
- A missing German line shows English for that line, never the dotted key.
- The existing `<arg:N>` convention and the `{N}` refusal stay, because the project already relies on them.
- No new language or translation service for two languages.
- The golden masters keep recording keys and arguments, so a translation does not change them.
- The pure parts (fallback chain, catalogue) are testable without Minestom, with no global state in tests.

## Considered options

1. **One `ResourceBundle` per locale, read by our own fallback chain, rendered by Adventure's `GlobalTranslator` (chosen).**
   The existing render path and tests remain. Our chain is explicit: requested tag, then language, then `en_US`, per key.
2. **Adventure `TranslationStore` with `MessageFormat`.** Rejected. `{0}` is MessageFormat's placeholder, and the project
   writes `<arg:N>`. `VoyagerTranslator` documents why it refuses MessageFormat at load.
3. **`ResourceBundle.getBundle(...)` with the JDK candidate list.** Rejected. Its fallback goes through the JVM default
   locale, which would make a German host show German to a player whose settings have not arrived.
4. **Vanilla client lang files, or a resource pack.** Rejected for our keys. The client only knows vanilla keys, and a pack
   is a distribution cost. Still open for Minestom's own keys (see open items).
5. **An external translation service.** Deferred. Two languages and a small volume do not justify it, and a service would
   make the parity check a network test.

## Decision outcome

Chosen option: **1**, with these rules.

- Bundles: `voyager_en_US.properties` and `voyager_de_DE.properties` (server); `voyager_setup_en_US.properties` and
  `voyager_setup_de_DE.properties` (setup). Each is read as UTF-8 through an explicit `Reader`.
- Resolution: the player's locale tag; then its language alone; then `en_US`. Decided per key.
- A player whose settings have not arrived is shown `en_US`. Whether the Minestom default can be set at boot is verified
  before apply (open item 1).
- Placeholder and palette tags must match English for every key, in every locale. `{N}` is refused in every locale.
- Plurals are two keys, `<key>.one` and `<key>.other`, chosen by the factory from the count.
- A fitness test (`MessageBundleParityTest`, in `voyager-fitness`) fails the build for a missing key, an extra key, a
  changed placeholder or tag, a `{N}`, or a missing plural form. An ArchUnit rule (extension of
  `ApiPurityTest.onlyTheTextPackageBuildsAUserFacingString`) keeps literal player-facing strings and literal keys out of
  every module outside `platform.text`.
- Console and log output stays English (open item 2).

### Consequences

- Good: one render path, two languages, and a build that refuses an incomplete or incorrect translation.
- Good: the setup server's dotted-key bug is fixed by the same rule that keeps keys complete.
- Good: golden masters are unchanged, because they record keys and arguments.
- Bad: every new line needs a German value and a review. The parity test stops a missing line, not a wrong one.
- Bad: the fallback is per key, so a half-translated bundle looks mixed in play. Mitigated by the boot warning and by the
  owner's review before each release.
- Bad: `GlobalTranslator` is process-wide. Tests use the pure catalogue, and only the composition roots install the source.

## Open items

1. Whether `MinestomAdventure`'s default locale can be set to `en_US` at boot, so that a player with no settings yet is shown
   English on a German host. To verify in the Minestom 2026.08.28-26.2 source before apply. If it cannot, the limit is
   recorded here.
2. Whether console and log output follows the operator's locale. Proposal: English always.
3. Whether Minestom's built-in command errors are sent as vanilla translatable keys (rendered by the client) or as text.
4. Number and time formatting in German. Proposal: keep `.` as the decimal separator for race times.
5. Tone in German: `du` (proposal) or `Sie`.

## Links

- `openspec/changes/localise-player-messages/` (proposal, design, tasks, delta specs)
- `openspec/changes/improve-message-ux/` (the wording, colour and channel rules this record refers to)
- `docs/reference/message-style-guide.md` (the rules for authors and translators)
- `docs/guides/how-to-add-a-translation.md` (the workflow, updated by the implementing change)
