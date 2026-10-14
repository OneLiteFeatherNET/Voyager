# Proposal

**Conventional Commits:** `feat(race)`. Squash-merge PR title: `feat(race): add computer racers named COM 1, COM 2 and so on`.

**Decision record:** `docs/decisions/0035-com-racers.md` (MADR, Proposed). Accepted only when the owner accepts it.

**Slices touched:** `race/com` (new, pure bot brain), `platform/com` (new, Minestom entity adapter), `platform/cup`
(one port, `CompetitorField`, and the calls the cup makes to it), `api/race/com` (one enum), `server` (two settings, the
configuration check, wiring), `fitness` (rules for the new boundary). The rings, the scoring rules, the catalogue format,
the flight simulation and the physics module are not touched.

**Depends on, merged:** `per-map-boost` (`99de1c0`), `checkpoint-resets` (`1c2828c`), `full-course-guide-line` (`35bcdb5`)
and `lobby-cup-display-and-hub-handoff` (`f000b6b`, `c28df3d`) are all merged on `main`, with their archives. Section
"Interaction with planned changes" in `design.md` (D12) says what each one means for computer racers. The only code
edit they require here is the bot exclusion in `WaitingRoom.handOff`.

## Why

Old racing games filled a race with computer opponents named "COM 1", "COM 2" and so on. The owner wants that here: a
server with one or two human racers should still feel like a race, and a lone racer should have something to chase. Today
a cup needs `VOYAGER_MIN_PLAYERS` human racers before it starts, and an empty course is an empty race.

## What Changes

- **Computer racers ("COM n").** When a cup starts, the server adds computer racers to the start grid, up to the total set
  by `VOYAGER_COM_COUNT`. Each one is named `COM 1` to `COM n` in grid order. They are visible in the world, in the tab
  list and above their head.
- **Movement.** A computer racer follows the course's `RacingLine` by arc length at a target speed. The line is the same
  one that humans see, from the domain, not from the renderer. Movement is deterministic for a given seed. No elytra
  physics runs for a computer racer: its position is set by the server, as the owner's design allows, because client
  authority applies to players only.
- **Difficulty.** `VOYAGER_COM_DIFFICULTY` (`EASY`, `MEDIUM`, `HARD`, default `MEDIUM`) sets the speed factor, the offset
  and wobble of the line, the chance of missing a ring, and the delay before a computer racer reacts to a boost ring.
  Each racer gets a seeded personal variation inside the level, so `COM 1` and `COM 2` differ.
- **Rings.** A computer racer passes rings with the same disc test, the same strict order and the same run as a human.
  A missed ring costs time: the racer turns back and crosses the same plane through the disc. It never skips a ring.
- **Joining.** Computer racers are never counted as online players. They never start a cup, never count toward
  `VOYAGER_MIN_PLAYERS`, and never appear in the `x/y` join line. The start gate, the join line and the boss bar read
  humans only, as now. No gate change (owner decision OQ1).
- **Results.** Computer racers appear in the cup's ranking table as `COM n` with their own time. They receive no cup
  points, no placement bonus and no medal, and no stored record. They never change a human's score, and they are never
  stored in stats (owner decision OQ2).
- **Lifecycle.** Computer racers are added at cup start, topped up to the count at each map start, never removed in the
  middle of a map, and removed when the cup ends. They do not take the hub handoff, and the server does not make a
  LuckPerms or CloudNet call for them.
- **Settings.** `VOYAGER_COM_COUNT` (integer, default `0`, range `0` to `15`) and `VOYAGER_COM_DIFFICULTY`, both global
  (OQ3). Both are read through the avaje-config `ConfigLoader` in `ServerSettings` (never raw `System.getProperty`) and
  checked by `ConfigCheck`. With `VOYAGER_COM_COUNT=0` the server behaves as it does today.

**BREAKING:** none. The default count is zero, so an existing deployment sees no change.

Out of scope: per-cup settings in the catalogue (a later format change, OQ3); bots in the lobby world (OQ4); bot skins
fetched from the network (OQ6); a player-facing command to add or remove bots; stats and persistence of
bots (the later Hibernate/PostgreSQL work records the human-only rule, it does not implement it); a per-player difficulty
choice (OQ5); a rubber-banding
mechanism; any change to the tree being replaced; any change to the physics module.

## Capabilities

### New Capabilities
- `race/com-racers`: the computer racer's movement, difficulty, ring passing, lifecycle, ranking and scoring exclusions.
- `server/com-settings`: the two settings, their defaults and ranges, their configuration check, and the zero-count
  guarantee.

### Modified Capabilities
- `architecture/vertical-slices`: adds the slice `com` to the list of slices in `race` and `platform`, and the port rule
  that keeps `platform/cup` free of `platform/com`.
- `architecture/architecture-enforcement`: adds the fitness rules that name the new boundary (design D12).
