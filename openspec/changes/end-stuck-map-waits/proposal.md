# Proposal

**Type and scope:** `feat(race)`. The change ends a map when a racer is stuck and gives every racer a way out of a wait they
do not want. It touches the race slice's map flow and scoring (`race.flow`, `race.scoring`), the platform lobby that hands
racers to the hub (`platform.lobby`), the lobby items and the race HUD. Ships as one code change under the title
`feat(race): end a map after a grace window and let racers retire`. Breaking: no for RACE play by default, but the between-maps
"no leave" rule is lifted, which is a visible change. The owner decides the grace formula before the code PR (see Open questions).

## Why

A map currently ends only when every racer has finished or when the race cap is reached. The cap is 300 seconds
(`voyager/race/src/main/java/net/elytrarace/voyager/race/flow/RaceTimings.java:24` and the `DEFAULT` at `:53-54`), and the
comment next to it says a shorter cap is "tied to a racer being able to end their own run, which does not exist yet"
(`RaceTimings.java:46-48`). A single racer who is lost, stuck on a ring or simply slow therefore holds every other racer in the
map for up to five minutes, while the finished racers wait:

- No retire or forfeit path exists in source: there is no action, command or item that ends a racer's run. A DNF is only
  scored when the cap ends the map (`voyager/race/src/main/java/net/elytrarace/voyager/race/scoring/MapScorer.java:18`).
- Racers who finished are held in place until they are placed on the next map (`openspec/specs/race/finish-glide-out/spec.md`,
  requirement "After its glide-out, a racer is held in place until placed on the next map").
- The waiting-lobby spec marks a per-map limit as Won't in this round (`openspec/specs/race/waiting-lobby/spec.md`, requirement
  "The map time limit stays at the cup's race cap"). That was a deliberate owner choice, and this proposal asks the owner to
  overturn it.
- `/hub` is refused during a race (`openspec/specs/race/lobby-items/spec.md`, requirement "The leave command and the hub command
  do what the item does"), and the leave item is removed at the GO tick (requirement "The items are removed when the race starts").
  Between two maps a racer who wants to stop has no way out except disconnecting.
- A racer who joins a running cup is told "You join at the next map." (`voyager/server/src/main/resources/voyager_en_US.properties:45`,
  set in `voyager/platform/src/main/java/net/elytrarace/voyager/platform/lobby/WaitingRoom.java:201-203`) with no sense of how long
  that is, although the cup's timings are known (`RaceTimings`).

## What Changes

- **Retire.** A racer can retire from a map with one action (a `/retire` command and an item in the hotbar, both translated). A
  retired run is scored DNF through the existing `MedalTier.DNF` path: ring points only, no medal points. The racer is then
  between maps, not kicked.
- **Grace window.** When the first racer finishes a map, a grace window starts. Its length is proposed as
  `max(30 s, 1.5 × the map's reference time)`. Racers still racing when it ends are scored DNF and the map ends. The window is
  shown on the racer's boss bar as remaining time (`race/race-hud`). The open question is which finisher starts it.
- **Leaving between maps and after finishing.** The leave item and `/hub` work while a racer waits between two maps and after a racer
  has finished its own run on the current map. They stay refused while a racer is still racing. The leave item comes back in the
  between-maps wait and goes away again at the next GO. Finished racers keep the held-in-place rule of `race/finish-glide-out`.
- **Joiner's wait.** A racer who joins a running cup is told the time to their map as an upper bound, for example "up to 4 min until
  your map", computed from the remaining race and wait durations in `RaceTimings`.
- The per-map limit is no longer a Won't: the cap of 300 s stays as the hard cap, and the grace window ends a map earlier.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `race/waiting-lobby`: the per-map limit becomes the grace window (the old requirement is removed); a retire action is added; the
  joiner's wait is shown.
- `race/lobby-items`: the items are held out of the race but return between maps and for a finished racer; `/hub` and `/leave` follow
  the same rule.
- `race/race-hud`: the boss bar shows the grace window to a racer still racing.

## Decisions taken in this proposal

- **Retire is DNF, not a new result.** It reuses `MedalTier.DNF`, so the standings, the medal logic and the statistics stay as they are.
- **Finished racers are not held against their will.** They may leave between maps, which is what they asked for; they are still
  held in place during their own glide-out, as before.
- **The hard cap stays at 300 s.** The grace window is an earlier end, never a longer one.

## Trade-offs

### Pros

- Removes dead time: a map with one lost racer ends after the grace window instead of after five minutes.
- Retire is a release valve for a stuck racer and an honest opt-out, scored as DNF, so nobody is punished twice.
- Leaving between maps gives every racer autonomy over their own session, without the lobby exit being hidden.
- The joiner's time estimate turns "next map" into a number a racer can plan around.

### Cons

- A grace window can cut off a slow but honest racer, who is then scored DNF on a map they were still flying. This is the fairness
  cost, and it is the reason the formula is an open question.
- It overturns a deliberate Won't in `race/waiting-lobby`. That is an owner decision, and the proposal asks for it.
- The rule for "racing" versus "finished" must be exact: a racer who has finished must not be able to leave mid-flight, and a racer
  who is racing must not be able to leave at all. The spec pins this, but the implementation has to honour it at the item and the
  command.
- The reference time that sets the window is an estimate on most shipped maps (see `calibrate-shipped-catalogue`). A window built
  on a guessed reference time is only as fair as that guess.

## Out of scope

- Rings, boost, scoring and medal brackets (see `calibrate-shipped-catalogue`).
- A spectator mode, rejection of late joiners, and a configurable per-map limit. The configurable limit is not built here.
- Persisting retired runs in PostgreSQL, beyond the DNF already recorded.
- Changing the held-in-place rule of `race/finish-glide-out`, unless the owner chooses to.

## Open questions for the owner

- **Grace formula.** Proposed `max(30 s, 1.5 × reference time)`. Alternatives: a fixed 45 s; `2 × reference time`; or no grace until
  the last finisher.
- **Which finisher starts the grace?** Proposed: the first finisher. The alternative, the last finisher, keeps the cap for every
  racer who is racing and removes the cutting risk, but also removes most of the time saving.
- **Is the Won't in `race/waiting-lobby` overturned?** This proposal assumes yes. If not, the grace window and the retire action are
  dropped and only the joiner's estimate and the between-maps leave remain.
