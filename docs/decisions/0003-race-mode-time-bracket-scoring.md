# ADR-0003: Race Mode uses fixed time-bracket scoring per map

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Voyager game design team
- Voyager server team

## Context and problem statement

Race Mode awards points per map to drive cup standings. The scoring formula must rank players, reward skill, and remain understandable mid-flight so players know what they need to achieve before crossing the finish line. The existing implementation already grants ring points and a position bonus, but it lacks a time-based component that reflects how cleanly a player flew the map relative to its design target.

The chosen formula must be communicable on the in-game HUD in a single glance, must reward absolute improvement and not only relative placement, and must work for newcomers who finish behind veterans on the same map.

## Decision drivers

- Players must see, before crossing the finish line, which point tier their current time qualifies for
- Newcomers must earn meaningful points for personal improvement, even when they finish last
- The formula must be expressible in one sentence to a new player
- Map designers must own the difficulty target through a single number
- The scoring component must compose with the existing ring points and position bonus without rebalancing them

## Considered options

- Option A: Fixed time brackets relative to a designer-set reference time
- Option B: Continuous time decay using `points = max * e^(-k * t)`
- Option C: Relative-to-leader scoring where the fastest finisher anchors 100 percent

## Decision outcome

Chosen option: **Option A — fixed time brackets relative to a designer-set reference time**, because brackets are legible on the HUD, reward absolute skill against a fixed target, and let newcomers chase the next tier instead of chasing the leader.

Each map declares a reference time set by the designer. Per-map scoring uses five brackets and a DNF case:

| Bracket | Time relative to reference | Points |
|---|---|---|
| Diamond | At or under 100 percent | 60 |
| Gold | 101 to 110 percent | 45 |
| Silver | 111 to 125 percent | 30 |
| Bronze | 126 to 150 percent | 15 |
| Finish | Over 150 percent | 5 |
| DNF | Did not finish | 0 |

The bracket score adds to the existing ring points and position bonus. Auto-tuning the reference time to the playerbase median is out of scope and tracked as a separate future epic.

### Consequences

- Good, because players see the target time on the HUD and know exactly which bracket each second of flight costs them
- Good, because newcomers earn 5 to 30 points on their first attempts, which keeps them invested across a cup
- Good, because designers tune difficulty per map by editing one number
- Bad, because a poorly chosen reference time skews the entire map's scoring until the designer revises it
- Bad, because the bracket boundaries are sharp, so a player who misses Gold by 0.1 seconds drops 15 points
- Neutral, because the bracket score sits alongside the existing ring and position bonus, so no existing scoring code is rewritten

### Confirmation

- A `BracketScoringService` exists in the server module and is covered by unit tests for each bracket boundary, the DNF case, and the off-by-one edges at 100, 110, 125, and 150 percent
- The race HUD shows the current bracket and the time remaining until the next bracket downgrade
- Each map JSON declares a `referenceTimeMillis` field; loading a map without that field fails fast at startup
- The aggregate per-map score in tests equals `ringPoints + positionBonus + bracketPoints`

## Pros and cons of the options

### Option A: Fixed time brackets

- Good, because the tier system is familiar from speedrun and racing-game conventions
- Good, because the rule fits one HUD line: "Diamond under 1:30, Gold under 1:39, ..."
- Good, because each map has one tunable knob
- Bad, because hard boundaries create a sharp scoring discontinuity at each tier edge

### Option B: Continuous time decay

- Good, because no discontinuities; every saved millisecond translates to more points
- Good, because the formula self-balances around the decay constant `k`
- Bad, because the curve is opaque to players; "you got 47.3 points" carries no narrative
- Bad, because the HUD cannot show a meaningful target without a tier overlay anyway, which reintroduces brackets
- Bad, because `k` must be tuned per map to avoid extreme outliers, which is the same designer cost as setting a reference time

### Option C: Relative-to-leader scoring

- Good, because the formula auto-scales to the field strength
- Bad, because the winner of every race always earns the maximum, regardless of absolute skill
- Bad, because the slowest finisher always earns near zero, which destroys progress feedback for newcomers
- Bad, because joining a lobby of veterans punishes newcomers harder than joining a lobby of peers, which contradicts the onboarding goal

## More information

- The Practice Mode counterpart uses per-lesson medal tiers — see [ADR-0004: Tutorial Mode is redesigned as Practice Mode](0004-practice-mode-replaces-tutorial.md)
- The `GameMode` enum that distinguishes Race Mode scoring from Practice Mode scoring is defined in [ADR-0005: GameMode enum as session discriminator](0005-gamemode-enum-session-discriminator.md)
- Auto-tuning the reference time to the playerbase median is tracked as a separate epic and is intentionally out of scope for this ADR
