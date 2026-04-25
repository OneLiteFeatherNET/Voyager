---
name: gamemode-and-scoring-decisions
description: Canonical scoring brackets, Practice Mode design, and the GameMode enum decided in ADRs 0003 0004 0005
type: project
---

ADR-0003, ADR-0004, ADR-0005 were accepted on 2026-04-25 and define the gameplay-mode foundation.

**Race Mode scoring (ADR-0003):** Per-map time brackets relative to a designer-set reference time:

- Diamond at or under 100 percent reference time: 60 pts
- Gold 101 to 110 percent: 45 pts
- Silver 111 to 125 percent: 30 pts
- Bronze 126 to 150 percent: 15 pts
- Finish over 150 percent: 5 pts
- DNF: 0 pts

Bracket score adds to existing ring points and position bonus. Auto-tuning the reference time to the playerbase median is a separate future epic — out of scope.

**Practice Mode (ADR-0004):** Replaces the originally scoped one-time Tutorial Mode. Lessons are repeatable, award per-attempt medals (Bronze/Silver/Gold/Diamond) using designer-set thresholds per lesson, and have per-lesson leaderboards plus personal-best tracking. Driven by game psychology review (one-time tutorial scored 3/7 VOYAGER, predicted D7 retention collapse).

**GameMode enum (ADR-0005):** `GameMode { RACE, PRACTICE }` lives in the server module. Stored as a `final` field on `GameSession`, set at construction, read on the tick thread by `GameOrchestrator`, phases, `ScoringService`, `HudComponent`, and the persistence layer. MVP keeps one session per server process — concurrent sessions live in separate CloudNet processes.

**Why:** These three decisions are interlinked — any future doc about scoring, practice content, session lifecycle, or HUD layout should cite the relevant ADR rather than re-deriving the rationale.

**How to apply:** When writing reference docs, how-to guides, or migration notes that touch scoring, lessons, or session mode, link the ADR instead of restating the rule. When numbers change (bracket cutoffs, point values, medal thresholds), they must be changed in the ADR via a superseding ADR — never edited in place.
