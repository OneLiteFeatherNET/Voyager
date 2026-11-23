# Design

## Dependency direction

The wording lives in resource bundles in `voyager-server` (`voyager_en_US.properties`, `voyager_de_DE.properties`), which the
`voyager-platform` message layer reads. The sound choice is in `voyager-platform` (`hud.RaceFeedback`). Neither change adds a
type, and no module boundary moves.

## Changes by module

| Module | File | Change |
|---|---|---|
| `voyager-server` | `voyager_en_US.properties:54-55` | `players` to `racers`, `player` to `racer`. |
| `voyager-server` | `voyager_de_DE.properties:39-40` | `Spieler` to the German word for racer used in the message style guide. The guide's German glossary is the source; the wording is reviewed by a German speaker. |
| `voyager-fitness` | `MessageStyleTest` | New `@Test` that scans every race line key for `player`/`players` and fails on a match outside an exception list. The list records the vanilla join keys with their reason. |
| `voyager-platform` | `RaceFeedback.medalSound` (`:236`), `RaceFeedback.placeSound` (`:312`) | Replace `CHALLENGE_COMPLETE` for `DIAMOND` and for `1` with the two chosen samples. |

## Sound candidates

The sample is chosen from the 26.2 registry and verified to exist before it is used. Candidates for the owner to choose by ear
(keys to be verified against the registry, not assumed):

- Diamond: a higher-pitched sample of the level-up family, or a sample from the totem family, as the medal's distinct sound.
- First place: a fanfare sample distinct from the medal sound, so that the place and the medal never share a sound.

The test for the registry lookup is the proof of existence; the choice itself is the owner's.

## Fitness

`MessageStyleTest` is the existing test for message style. The new check is a `@Test` in the same class, not a new rule in
`voyager-fitness`, because it inspects text resources rather than classes. The exception list is in the test, and each entry
names its reason.

## Decisions

No ADR. The glossary rule and the sound rule already exist in the message style guide and `race-feedback`; this change applies them.
