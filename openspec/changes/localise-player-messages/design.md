# Design: localise-player-messages

## Context

The rebuild renders every player-facing line from a translation key. `Messages` (40 factories) and `SetupMessages` (21)
are the only places that name a key, and `VoyagerTranslator` is the `GlobalTranslator` source that turns a key into
MiniMessage at render time with `<arg:N>` bound (`VoyagerTranslator.java:191-205`). `Palette` supplies the `<c-*>` and
`<m-*>` tags. Minestom renders per player when `ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION` is on, and the server
refuses to boot without it.

What is missing is a second locale and a real `Locale` argument. Today `translate(key, locale)` and
`canTranslate(key, locale)` ignore the locale, so the English value is returned for everybody.

## Dependency direction

```
voyager-server, voyager-setup   (composition roots: choose the bundle resource, call install())
        |
        v
voyager-platform :: text        (Messages, SetupMessages, Palette, VoyagerTranslator, MessageCatalogue, LocaleChain)
        |
        v
Adventure (text, translation, minimessage)   — no Minestom import in the catalogue or the chain
voyager-api, voyager-race, voyager-physics  — untouched; they never see a key or a locale
```

- `LocaleChain` (new, pure): given a requested `Locale` and the set of loaded tags, returns the ordered candidates
  (`de_DE`, `de`, then the default `en_US`). No I/O, no Minestom. Lives in `net.elytrarace.voyager.platform.text`.
- `MessageCatalogue` (new, pure): holds one `ResourceBundle` per locale tag, looks a key up along a `LocaleChain`,
  and reports, per key, which locale answered. Lives in `net.elytrarace.voyager.platform.text`.
- `VoyagerTranslator` (changed): delegates `canTranslate` and `translate` to `MessageCatalogue` with the player's locale.
  It stays the one `GlobalTranslator` source and keeps the `{N}` refusal.
- The Minestom adapter that reads `Player.getLocale()` is not new: Minestom already does it inside
  `PlayerSocketConnection.writePacketSync` when the flag is on. Nothing in the rebuild has to call it.

## Translation-source architecture

**Storage: one `ResourceBundle` per locale.** Each file is a `PropertyResourceBundle` read through an explicit UTF-8
`Reader`, not `InputStream`, so that `ä ö ü ß` survive whatever the JDK's default properties decoding is. The base names
are unchanged: `voyager_en_US.properties` and `voyager_de_DE.properties` for the server, and
`voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties` for the setup server.

We do not use `ResourceBundle.getBundle(...)` with the JDK candidate list. Its fallback goes through the JVM default
locale, and on a German host that would silently pick German for a player whose client sends nothing. The chain is ours
and explicit.

**Fallback chain (decided per key, not per file):**

1. The requested tag. For a client on `de_AT` the chain is `de_AT`, `de`, `en_US`; for `en_GB` it is `en_GB`, `en`,
   `en_US`.
2. The language-only bundle, if it exists.
3. `en_US`, which is always present and is the one bundle the boot refuses to start without.

If the key is missing in the chosen locale, the English value is used, and a single warning per key and locale is logged
at boot by the catalogue, never per message. A test (below) makes a missing key a build failure, so the runtime
fallback is a second line of defence, not the plan.

**Locale source.** `Player.getLocale()` is the client's locale from its settings packet. Before the client sends settings,
Minestom returns its default, which comes from `MinestomAdventure.getDefaultLocale()` and is initially the JVM default
(DeepWiki answer, to be verified against the Minestom source by `voyager-minestom-expert`). Task 2.4 checks whether
that default can be set to `en_US` at boot. If it can, a German host no longer sends German to a player whose settings
have not arrived yet. If it cannot, that is recorded as a known limit in ADR-0038.

**Placeholder convention.** Unchanged: `<arg:N>`, zero-indexed, MiniMessage. `{N}` stays refused at load by
`VoyagerTranslator.of`. Two additions:

- A value's placeholder indices and palette tags must equal the English value for the same key. A translator can
  reword a line but cannot drop an argument or recolour it.
- Plurals use two keys, `<key>.one` and `<key>.other`, chosen by the factory in `Messages` from the count. MiniMessage has
  no plural rule, and German needs one for `1 Ring` and `2 Ringe`. The fitness test requires both forms in every locale
  for any key that has a `.one` sibling in English.

**Colour.** Palette tags only. No hex in any bundle (already stated in the bundle header). The palette itself is not
changed here.

## Owner decisions this change needs

- ADR-0038 is the record of the architecture. It is Proposed until the owner accepts it. No code merges before that.
- OQ-1 to OQ-4 in `proposal.md`. The proposal assumes: console stays English (OQ-1); `du` (OQ-2); `.` decimal for race
  times (OQ-3); Minestom's own command errors are checked at task 2.5 and reported back (OQ-4).

## Options considered (summary; full text in ADR-0038)

1. **Per-locale `ResourceBundle` read by our own chain, rendered by Adventure's `GlobalTranslator` (chosen).** It keeps
   the existing `<arg:N>` path and the existing test, adds a locale without a new library, and keeps the chain in our
   hands.
2. **Adventure `TranslationStore` with `MessageFormat`.** Rejected, as already documented in `VoyagerTranslator`:
   `{0}` is MessageFormat syntax and the project writes `<arg:N>`.
3. **Vanilla client lang files.** Rejected for our keys: the client only knows vanilla keys, and a custom resource pack is
   a distribution cost. Still open for Minestom's own keys (OQ-4).
4. **An external translation service.** Deferred: two languages, a small volume, and a service would make the key
   parity test a network test.

## Fitness and tests

**Architecture rule (ArchUnit, `voyager-fitness`).** The existing rule
`ApiPurityTest.onlyTheTextPackageBuildsAUserFacingString` already covers every `net.elytrarace.voyager..` package,
including `voyager-setup` and `voyager-cloudnet-bridge`, and it matches `Component.text` by owner. This change extends
it, because the gaps are real:

- `Component.translatable(String, ...)` with a literal key outside `platform.text` (a key named outside `Messages`).
- `MiniMessage.miniMessage().deserialize(...)` outside `platform.text` (a literal string with tags, built in a call site).
- `CommandSender.sendMessage(String)`, `Audience.sendActionBar(String)`, and `Title`/`showTitle` with a `String`, if
  Minestom exposes overloads that take one (to be checked by Helix, task 2.5).

Each extension has an explicit `allowEmptyShould(false)` and its own test that proves it matches a seeded violation in
a test source set, so that a rule which matches nothing is a failure, as the project requires.

`FitnessCoverageTest` already requires the rule to name the `net.elytrarace.voyager` prefix; the existing test satisfies
that, and no new module is added, so no coverage map changes.

**Parity test (`voyager-fitness`, new: `MessageBundleParityTest`).** Its classpath holds both the server and the setup
resources, because `voyager-fitness` depends on every module carrying production sources. For each product (server,
setup):

- the set of keys in `en_US` equals the union of keys in each other locale's bundle (no key missing, no extra key);
- for each key, the multiset of `<arg:N>` indices and of palette tags is equal across locales;
- no value contains `{` followed by a digit and `}`;
- every key a `Messages` or `SetupMessages` factory names is present in every locale (the factories' key constants are
  read by reflection on the constants, not by parsing source);
- every key with a `.one` sibling in English has `.one` and `.other` in each locale.

F.I.R.S.T.: the test reads classpath resources only, touches no clock, installs nothing into `GlobalTranslator` (the
pure `MessageCatalogue` is used, never `VoyagerTranslator.install()`, because `GlobalTranslator` is a process-wide
singleton), and uses no temp directory. Each assertion names the key and the locale in its message.

**Unit tests (`voyager-platform`, Red first).** `LocaleChainTest`: `de_AT` → `de_AT, de, en_US`; `en_GB` → `en_GB, en,
en_US`; `en_US` → `en_US` only; an unknown language → `en_US`; a null or empty locale is refused by `@NotNull` contract,
not handled. `MessageCatalogueTest`: a German key answers German; a key only in English answers English and reports the
fallback; the catalogue never throws for a missing key. `VoyagerTranslatorTest` gains the case that `translate(component,
Locale.GERMANY)` returns the German rendering.

**Setup-bug test (`voyager-setup`, Red first).** `SetupCommandsTest` already exists. A new case renders
`voyager.command.denied` through the setup server's translator and asserts that the result is the English sentence, not
the dotted key. It fails today.

**Golden masters.** `CupSessionGoldenMasterTest` and its transcripts are expected to stay byte-identical, because the
transcript records keys and arguments. Task 5.2 runs them and records the result in the PR. A diff there means a key or
an argument changed, which this change must not do.

## Risks

- The German translation is medium effort and needs native review. Mitigation: owner review of `de_DE` before merge, and
  the parity test stops a missing line, not a wrong one.
- `Player.getLocale()` before settings may be the JVM default. Mitigation: task 2.4 and the recorded limit.
- Minestom's automatic translation may render `Component.translatable` keys with arguments that are themselves
  components. Mitigation: the existing `AutomaticComponentTranslationTest` already covers the flag; a German case is added.
