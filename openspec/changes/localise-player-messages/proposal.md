# Proposal: localise-player-messages

**Conventional Commits type and scope:** `feat(i18n)`. This change adds a second language and the locale resolution
that picks it. It changes no wording, no channel and no colour; those belong to `improve-message-ux`, which ships as
`feat(race)`. The two were one change until the type question forced the split (see the sibling proposal).

**Vertical slice touched:** the message layer of the rebuild, `voyager-platform` (`net.elytrarace.voyager.platform.text`),
with resources in `voyager-server` and `voyager-setup`, and a fitness test in `voyager-fitness`. No race, cup or physics
code changes. `voyager-api` is untouched.

**Decision altered:** none of the greenfield design spec's numbered decisions. It is governed by the new ADR-0038
(`docs/decisions/0038-player-message-i18n.md`, Proposed).

## Why

Every player-facing string is already a translation key (66 keys: 46 in `voyager_en_US.properties`, 20 in
`voyager_setup_en_US.properties`), but the bundle is English only. `VoyagerTranslator.translate(...)` ignores its
`Locale` argument, and its class documentation says "One language". Owner decision (2026-10-12): all player-facing text
is German and English, the language follows the player's client locale, and English is the fallback.

Three facts from the inventory make this more than adding a file:

1. **A real bug on the setup server.** The setup server loads only `voyager_setup_en_US.properties`
   (`SetupServer.java:100`), but two of its code paths send `Messages.commandDenied()`, which is the key
   `voyager.command.denied` defined only in the server bundle (`SetupCommands.java:88`, `WandListener.java:140`).
   An unauthorised setup user sees the dotted key on screen today.
2. **Two operator strings are English prose outside the translation layer.** `RaceCommand` prints
   `CupSession.describe()` (exempted by name in `ApiPurityTest`) and `WaitingRoom.describe()` (`WaitingRoom.java:115`),
   which the exemption does not name. The second one is invisible to the ArchUnit rule because it returns a `String`.
3. **The client cannot translate our keys.** With `minestom.automatic-component-translation` off, the client shows the
   dotted key. The server already refuses to boot without the flag (`VoyagerServer.java:151`). That makes server-side
   per-player rendering the only route, so the locale must come from the server.

## What changes

- Add `voyager_de_DE.properties` for the server and `voyager_setup_de_DE.properties` for the setup server, with the
  same keys and the same placeholder sets as the English files.
- Replace the single-bundle lookup with a per-locale catalogue and a fallback chain (requested tag, then language,
  then `en_US`, decided per key).
- Add a fitness test that every key exists in every locale, with the same `<arg:N>` indices and the same `<c-*>`/`<m-*>`
  tags, and that no value contains `{N}`.
- Extend the existing ArchUnit rule `onlyTheTextPackageBuildsAUserFacingString` to cover `Component.translatable` with a
  literal key and `MiniMessage.deserialize` outside `platform.text`.
- Give the two `describe()` operator strings a decision: either a key, or a named exemption that says why.
- Fix the setup-bundle gap by making `voyager.command.denied` a key the setup server defines.
- Record ADR-0038 and update `docs/guides/how-to-add-a-translation.md` for the two-locale workflow.

## Out of scope

- Any change to wording, channel, timing, sound or colour (`improve-message-ux`).
- Console and log output. It stays English (see design, open question OQ-1).
- Minestom's own command errors (Brigadier syntax text). Whether the client renders them is an open question (OQ-4).
- Number and time formatting per locale (OQ-3).
- Languages beyond `en_US` and `de_DE`.
- `voyager-persistence`, and therefore personal bests.

## Impact

- **Golden masters.** `CupTranscript` records each message as Adventure JSON, with its key and arguments, not as rendered
  text (`CupTranscript.java:37`). Translation therefore does not change any golden file. A key rename or an argument
  change would, and this change renames no key.
- **Runtime.** One extra map lookup per translated component. Translated packets are not cached (per Minestom's
  `PacketSendingUtils`), which is already the case today.
- **Setup server.** Its bundle gains the German file and one key. Its behaviour is otherwise unchanged.

## Owner questions

Answered 2026-10-13, see `design.md` "Decisions": OQ-1 console stays English; OQ-2 `du`; OQ-3 `.` decimal separator.
OQ-4 (Minestom's built-in command errors) is still open and is answered by task 2.5.
