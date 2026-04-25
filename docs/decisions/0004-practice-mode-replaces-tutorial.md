# ADR-0004: Tutorial Mode is redesigned as Practice Mode

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Voyager game design team
- Voyager game psychology review

## Context and problem statement

Voyager originally scoped a Tutorial Mode: a one-time, linear sequence of lessons that teach elytra flight, ring chaining, boost timing, and cup flow. The mode was intended only for first-time players. The game psychology review (VOYAGER score 3 of 7) flagged that a one-time tutorial collapses retention after completion, because veteran players have no reason to return to that content surface and the engineering investment serves only the first session per player.

Voyager needs a content surface that teaches new players the mechanics and that veterans return to for skill drills. Doubling the content investment by shipping both a tutorial and a separate practice mode is not affordable.

## Decision drivers

- Every content surface must serve more than a single session per player
- New-player onboarding must remain explicit and structured, not hidden behind a sandbox
- Veteran retention requires repeatable, measurable skill challenges
- Engineering and design budget allows one tutorial-shaped content investment, not two
- The mode must produce comparable per-player numbers so leaderboards and personal-best tracking are meaningful

## Considered options

- Option A: Redesign Tutorial Mode as Practice Mode — repeatable lessons with medal tiers and per-lesson leaderboards
- Option B: Ship the original one-time Tutorial Mode unchanged
- Option C: Ship both a one-time Tutorial Mode and a separate Practice Mode

## Decision outcome

Chosen option: **Option A — redesign as Practice Mode**, because the same lesson content powers both onboarding and veteran skill practice when each lesson is repeatable, timed, and ranked.

Practice Mode replaces Tutorial Mode with these properties:

- Lessons are permanent and repeatable; players can re-enter any lesson any number of times
- Each lesson awards a medal tier per attempt: Bronze, Silver, Gold, or Diamond
- Each lesson maintains a per-lesson leaderboard scoped to that lesson only
- Personal-best times are recorded per player per lesson
- Medal thresholds are designer-set per lesson, mirroring the time-bracket system from Race Mode
- New players still see lessons in a recommended order on first entry, but they are not gated

### Consequences

- Good, because the same lesson content serves first-session onboarding and long-tail veteran retention
- Good, because medal thresholds give veterans a measurable goal beyond completion
- Good, because per-lesson leaderboards create social comparison without the noise of a global ladder
- Bad, because per-lesson leaderboard infrastructure (storage, query, display) costs more than a one-time tutorial completion flag
- Bad, because designers must tune medal thresholds per lesson, adding ongoing balancing work
- Neutral, because the core lesson content (ring layouts, boost drills, cornering exercises) is identical to the original tutorial scope

### Confirmation

- A Practice Mode session can be entered, exited, and re-entered with no per-player gating
- Each lesson records `(playerId, lessonId, attemptTimeMillis, medalTier)` and surfaces a personal best
- A lesson leaderboard query returns the top N players by best time for that lesson
- Medal tier boundaries are loaded from per-lesson configuration and validated at startup

## Pros and cons of the options

### Option A: Practice Mode

- Good, because dual-purpose: onboarding plus retention
- Good, because each lesson has a medal goal that pulls players back
- Bad, because per-lesson leaderboards and personal-best storage add database scope
- Bad, because designers must maintain medal thresholds per lesson

### Option B: One-time Tutorial Mode

- Good, because the simplest scope; one completion flag per player per lesson
- Good, because no leaderboard infrastructure needed
- Bad, because game psychology review predicts D7 retention collapse after completion
- Bad, because the engineering investment serves only the first session per player

### Option C: Both Tutorial Mode and Practice Mode

- Good, because clean separation of "learn" and "improve" surfaces
- Bad, because doubles the content investment for the same core lesson material
- Bad, because two surfaces compete for the same player time and the same designer attention
- Bad, because veterans have no reason to return to the tutorial half once it is complete

## More information

- The medal-tier mechanism mirrors the bracket scoring from [ADR-0003: Race Mode uses fixed time-bracket scoring per map](0003-race-mode-time-bracket-scoring.md)
- The mode discriminator that separates Practice Mode behavior from Race Mode behavior is defined in [ADR-0005: GameMode enum as session discriminator](0005-gamemode-enum-session-discriminator.md)
- The auto-tuning reference-time epic referenced from ADR-0003 also applies to medal thresholds in Practice Mode and is tracked separately
