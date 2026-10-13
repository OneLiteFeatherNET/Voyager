# Research 008: the lobby count, the full-lobby countdown and the boss bar

Status: finding recorded with the change `add-lobby-world` (ADR-0029). The memory cost of the lobby world on a
production node is not measured; it is operator task 2.3 and is open.

## Question

How should waiting racers see the number of racers online and the start countdown, once they stand in a lobby world
rather than on the first map (ADR-0029), and what in the rebuild makes each choice cheap or expensive?

## Method

- Read the tree being replaced for the old lobby behaviour: the join message key and the countdown wording.
- Read the rebuild's cup and phase code (`platform.cup.XerusPhaseDriver`, `race.flow.StartGate`,
  `platform.lobby.WaitingRoom`) to see where a countdown is held and what a change to it touches.
- Relied on the boss bar path `RaceHud` already uses (an Adventure `BossBar` shown to a Minestom player). This change
  did not re-read that path in the pinned versions; the packet behaviour is the one `LobbyHud` assumes and its tests
  check at the decision level only.
- Checked the approach against the golden transcripts: `CupTranscript` renders chat, titles, subtitles and the action
  bar, and it does not render boss bar packets.
- Tests were written against the rules, not against a live client. No client run measured the bar.

## Findings

### F1. The join line is a chat line with the count and the maximum

The old game sent every player a chat line on each join, with the racer's name and the online count. The rebuild keeps
that shape, `<name> joined the game (<online>/<maximum>)`, built only in `platform.text.Messages`. The maximum is soft:
a join past it is announced past it (`17/16` is valid), because the late-joiner rule of the waiting lobby is an owner
decision, and a hard cap would contradict it.

### F2. The full-lobby countdown is a pure rule, and the phase can be shortened

When the server reaches its maximum during a countdown, the countdown shortens to a fixed 10 s. The rule lives in
`StartGate.shortenedCountdown` as a pure function with no clock, so one table covers the cases: a committed start is
never shortened, a countdown already at or under 10 s is never lengthened, and a lobby below the maximum is untouched.

The phase length can be changed on a running lobby. `XerusPhaseDriver` is a ticked phase with no Xerus timer; it reads
its lobby length on every tick, so a shorter length applies on the next tick. This was checked by reading the phase
driver and by the shortening test that drives explicit ticks. It was not a separate benchmark.

### F3. A boss bar is cheaper than a second HUD subsystem, and is only sent on change

The owner's wording allowed a scoreboard or a boss bar. A scoreboard would be a new subsystem with its own per-racer
lifecycle and no existing code in the rebuild. A boss bar reuses the mechanism `RaceHud` already uses, one bar per racer.
`LobbyHud` sends an update only when the text or the progress changes. This is a design property, pinned by a test
that counts no update for an unchanged state. The packet rate during a countdown was not measured on a server.

### F4. The bar's text is the same in the lobby and in the fallback

With no lobby world the racers wait in the first map, and the same bar is shown there until the start is committed. The
bar then gives way to the race bar. The decision is one condition in `WaitingRoom`, so the two placements cannot drift
apart.

### F5. Boss bars are invisible to the golden transcripts

`CupTranscript` renders no boss bar packet. The lobby bar therefore does not change any of the four frozen transcripts,
and it does not appear in the lobby golden either. The golden checks the lobby transfers and the cup's per-tick
behaviour; the bar is checked by unit tests on `LobbyHud` and `WaitingRoom`. A visual check in a playtest is still
needed.

## Limits

- No client run. The bar's look, the fill and the timing of the countdown digits are unverified on a real client.
- No memory or packet measurement. The held lobby world's cost is operator task 2.3.
- The fill is the fraction of the lobby currently played. After a shortening the fill jumps; this was accepted, not
  smoothed.

## Consequences

- The join line and the countdown wording live in one bundle and one factory each, so a change of wording is one edit.
- A future hard cap on racers is a change to the join path, not to the count or the bar.
- The shortening relies on the phase reading its length every tick. A future phase that caches its length would need
  the same rule, and the test that drives explicit ticks would catch it.
