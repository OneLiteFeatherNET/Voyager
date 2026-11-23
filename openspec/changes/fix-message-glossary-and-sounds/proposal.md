# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title. It brings two race surfaces in line with specs that
already exist: the race wording uses the glossary's word for a person in a race, and each sound means one thing.

**Depends on:** nothing.

## Why

The message style fixes one word for a person taking part in a race: `racer` (`openspec/specs/message-style/spec.md:42-49`).
The lobby's waiting line still says `player`:

- `voyager/server/src/main/resources/voyager_en_US.properties:54-55`: "Need N more players to start" and "Need 1 more player to start".
- `voyager/server/src/main/resources/voyager_de_DE.properties:39-40`: "Noch N Spieler bis zum Start" and "Noch 1 Spieler bis zum Start".

The glossary requirement is not enforced. `voyager/fitness/src/test/java/net/elytrarace/fitness/MessageStyleTest.java` checks the
accent tags, the colours and the named failure and refusal keys, and nothing about the glossary terms, so the drift went
unnoticed.

The race feedback spec says each sound means one thing: the last-ring chime only for a ring pass (`openspec/specs/race-feedback/spec.md:18-20`).
Three other moments play that same chime in `voyager/platform/src/main/java/net/elytrarace/voyager/platform/hud/RaceFeedback.java`:
the Diamond medal (`:236`, in `medalSound`), and first place at the cup's end (`:312`, in `placeSound`). A racer who wins a place
hears the sound of the last ring, and a racer who finishes with Diamond hears a ring pass.

## What Changes

- **Glossary wording.** Both lines use `racer` and `racers`, singular and plural, in English and German. The German wording is
  reviewed by a German speaker before merge.
- **Glossary check in the test suite.** `MessageStyleTest` gains a check that no race line uses `player` or `players` for a
  person taking part in a race, with the vanilla join phrasing excluded by its key list.
- **Distinct samples.** Diamond and first place each get a sound that is not the ring-pass chime. The sample is chosen from the
  26.2 sound registry, and its existence is checked by a test against the registry.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The glossary and the sound rules are already stated. The new rules are ADDED requirements, because the current specs say
what the wording and the sounds must be, but not how the glossary is checked or which sound the two medal and place moments use.

- `message-style`: ADDED a requirement that the glossary is checked by the fitness suite for every race line key.
- `race-feedback`: ADDED a requirement that the Diamond medal and first place have sounds that are neither the countdown beat
  nor the last-ring chime.

## Trade-offs

### Pros

- One vocabulary across every race line, and a test that keeps it there.
- Each sound means one thing; a racer who wins first place no longer hears the sound of a ring.
- Small: two resource files, one test, two sound choices.

### Cons

- The German wording needs a native review; a plausible translation can still read oddly in the lobby.
- The sound choice is by ear. The test can prove the sample exists, not that it is pleasant. The owner listens before merge.
- The glossary check is a text search over the bundles, so it can flag a legitimate use of `player` (for example, a vanilla
  join message). The key list in the test is the place to record such exceptions.

## Out of scope

- The other race lines beyond these two keys. A wider glossary sweep is a separate change if the check finds more.
- The message style guide's voice and timing rules.
- Medal and place rules (which place earns which medal, the title text).

## Open questions for the owner

- Which two sounds should Diamond and first place use? Candidates are listed in `design.md`; the owner chooses by ear.
- Is "Need N more racers to start" acceptable English, or should the line read "Waiting for N racers to start"?
