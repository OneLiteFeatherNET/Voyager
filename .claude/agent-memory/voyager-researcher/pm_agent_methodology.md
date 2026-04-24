---
name: Project Manager Agent Methodology (Voyager)
description: Recommended methodology, responsibilities, and tooling for a Voyager Project Manager agent distinct from Compass (Product Manager)
type: project
---

Recommendation: Scrumban (1-week iterations + WIP limits + GitHub Projects v2 iteration field) for Voyager.
**Why:** Small AI agent team (~10-15), GitHub-native, indie game pace where physics/feel iteration dominates; Scrumban combines Scrum cadence for planning with Kanban flow for unplanned bugs (physics feel, multiplayer desync).
**How to apply:** When designing or invoking the PM agent, use 1-week iterations, per-agent WIP = 1-2, velocity measured in closed issues/week over rolling 3-iteration window (story points optional because GitHub Projects v2 lacks native velocity charts).

Split of ownership Compass (PM = Product Manager) vs. new PM agent (Project Manager, execution):
- Compass owns: scope, acceptance criteria, milestone definition, stakeholder ask, agent orchestration for decisions.
- Project Manager owns: sprint board, velocity, WIP limits, dependency graph, risk register, release checklist execution, burndown.
- Boundary rule: "what and why" = Compass; "when, in what order, at what risk" = Project Manager.

Known GitHub CLI gaps (as of 2026-04):
- `gh` has no native milestone subcommand; use `gh api repos/:owner/:repo/milestones` or the `gh-milestone` extension.
- `gh project item-edit --iteration-id` needs raw iteration ID; no `@current` / `@next` alias. Use `gh-iteration` extension or query via `gh api graphql`.
- GitHub Projects v2 has no native velocity/burndown charts — must compute from `gh issue list --state closed --search "closed:>=<iso>"` or external tool.

Decay trigger: GitHub Issue Dependencies leaving public preview (would replace hand-rolled "blocked by" labels); `gh` adding native milestone or iteration alias support.
