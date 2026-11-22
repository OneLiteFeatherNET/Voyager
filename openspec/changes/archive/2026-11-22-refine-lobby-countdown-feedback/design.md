# Design

## Context

Both changes are one-line decisions inside the existing `race` slice's platform adapter. No type is added.

| Behaviour | Site (at `origin/main` 6007352 plus the release) | Today |
|---|---|---|
| Full-lobby sound key | `voyager/platform/src/main/java/net/elytrarace/voyager/platform/lobby/sound/LobbyCountdownSounds.java`, `FULL_LOBBY` | `block.note_block.xylophone` |
| Full-lobby sound played | `WaitingRoom.shortenIfFull` (`lobby/WaitingRoom.java`) | `LobbyCountdownSounds.fullLobbySound()` once per shortening |
| Item removal rule | `WaitingRoom.announce` (`lobby/WaitingRoom.java`), `takeItems` | removed for `COMMITTED` and `RUNNING` |
| Race reset sound | `hud/RaceFeedback.java`, `RESET_CHIME` (`block.note_block.chime`) | in `sampleKeysLobbyMustNotShare()` |
| Distinctness test | `lobby/sound/LobbySoundsDistinctTest.java` | fails once the lobby sound is the chime, unless the exception is written |

## Decisions

### 1. The full lobby plays the chime, and the chime is a named exception to the distinctness rule

The owner asked for `block.note_block.chime`. The chime is already `RaceFeedback`'s reset sound, which the lobby rule
(`race/lobby-feedback`, "No lobby sound shares a meaning with a race sound") forbids for lobby sounds. The rule exists so that
a sound keeps one meaning. Here the two meanings are heard in different phases: the full waiting room, and a racer's reset
during a race. The exception is limited to the full-lobby sound, and `LobbySoundsDistinctTest` keeps guarding every other lobby
sound. The reset sound itself does not change.

Alternatives considered:
- Keep the xylophone. Rejected: the owner asked for the chime.
- Give the full lobby a new sound. Rejected: the owner asked for the Cygnus chime specifically, and a new sample needs a new
  sound review.

### 2. The items leave on the GO tick, not on the commit

`WaitingRoom.announce` derives `takeItems` from the situation. The fix changes the condition to `situation == RUNNING` only.
The situation `RUNNING` begins on the GO tick (`CupSession.situation`, the same tick the racer enters the first map), so the
removal and the GO tick coincide. Until then `LobbyItems.give` keeps the items in place, as it does while waiting.

## Dependency direction

`WaitingRoom` (platform, lobby) calls `LobbyCountdownSounds` and `LobbyItems` (platform, lobby). Both are in the adapter ring;
neither depends on `voyager-race` types beyond those it already uses. No new package, no new type, no new dependency.

## Fitness

No new `voyager-fitness` rule. The existing `PicaBoundaryTest` and the platform's package rules still cover these classes.
`LobbySoundsDistinctTest` is the mechanical guard for the sound rule, and it gains the exception.

## Golden masters

The golden lobby transcript records action bar lines, not sounds or inventory, so the chime and the item timing do not change
it. If the golden run shows a difference, the change is a defect and the PR stops until it is explained.
