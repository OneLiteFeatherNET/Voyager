# Tasks

This change changes no production code and no behaviour, so no test is written first in it. The architecture tests are
the subject of follow-up 1 (`add-architecture-slice-rules`), which starts red-first. Every task below is documentation or
verification of documentation.

Status as of 2026-10-10. Tasks were checked against the code on `main` (commit `21b1ee0`), and the design, the specs and
this file were updated to the current code before the tasks were done.

## 1. Decisions and checkpoints

- [x] 1.1 Ask the user to approve the slice names `ring`, `cup`, `catalog` and `hud`, the move of `server/game` into `race.cup`, and the rename of `api.physics` to `api.flight`, as listed in the design's human checkpoints. Verify: the answers are written into `design.md` (Open Questions section), and no question there would change a spec.
  - Outcome: the cup move to `race.cup` and the fold of progress into `ring` were decided by the owner on 2026-10-09. The slice names `catalog` and `hud` are kept, and `api.physics` keeps its name; the rename to `api.flight` is optional in `flatten-api-by-slice`. These two were decided by default on 2026-10-10, and the owner may revise them. Recorded in `design.md` (Open Questions) and ADR-0017.
- [x] 1.2 Ask the user to accept or amend the four-ring model and the folding of progress into the `ring` slice. Verify: the answer is recorded in the "Decision Outcome" of ADR-0017 (task 2.1).
  - Outcome: the progress fold is decided by the owner (2026-10-09). The four-ring model is accepted by the owner's instruction on 2026-10-10 to finish the change with ADR-0017 Accepted. Recorded in the ADR's Decision outcome.

## 2. ADR, explanation and pointers

- [x] 2.1 Write `docs/decisions/0017-clean-architecture-with-vertical-slices.md` in MADR 4.0 with status `proposed`. It cites ADR-0016 (`switch-di-to-avaje-inject`) and greenfield decisions D8 and D10, and it records the four rings, the slice rules and the DI rules. Verify: the file has the MADR 4.0 sections (Context and Problem Statement, Decision Drivers, Considered Options, Decision Outcome, Consequences, Confirmation) and the status line reads `proposed`.
  - Outcome: the status line reads `Accepted`, as the owner instructed on 2026-10-10, not `proposed`. The other sections are present. Commit `docs(adr)`.
- [x] 2.2 Write `docs/explanation/architecture.md`, a new Diátaxis explanation page with the four rings, the slice table, the DI rules and the migration list, linking to the normative specs. Verify: every slice name from the table in `design.md` appears in the page, and every migration item (1 to 27, see `design.md`) appears or is linked to `design.md`.
  - Outcome: the page has the rings, the slice table, the DI rules, the enforcement mapping and the migration summary, which links to `design.md`. Items 1 to 27 are numbered in `design.md`; the page points there rather than copying them. Commit `docs(architecture)`.
- [x] 2.3 Add a cross-reference to `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` in "Module architecture": links to ADR-0017 and the explanation page, and a note that D8 gains slices as packages and that D10 gains the rule that DI annotations appear only in composition roots (no annotation in the platform). Verify: `grep -n "0017\|explanation/architecture" docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` shows both links.
- [x] 2.4 Add one pointer line to the "Architecture" section of `CLAUDE.md` to `docs/explanation/architecture.md` and ADR-0017, and change nothing else. Verify: `git diff --stat CLAUDE.md` shows one added line and no removed lines.
  - Outcome: one added line, no removed lines. The ECS, Elytra and Data Flow subsections describe the tree being replaced, so none of their text is replaced; they are kept.
- [x] 2.5 Update the rows "Clean Architecture" and "Vertical Slice Architecture (VSA)" in `docs/reference/semantic-anchors.md` to reference ADR-0017, and add no anchor. Verify: `git diff docs/reference/semantic-anchors.md` changes only those two rows, and the anchor names are unchanged.

## 3. Validation and pull request

- [x] 3.1 Once the user accepts the ADR (checkpoint 1.2), set its status to `accepted` in the same branch. Verify: the status line reads `accepted` and the commit is in the branch.
  - Outcome: accepted on 2026-10-10 by the owner's instruction; the status line is in the `docs(adr)` commit.
- [x] 3.2 Run `openspec validate define-clean-architecture-with-vertical-slices --strict` and fix every finding. Verify: the command exits with code 0.
  - Outcome: exit 0 on 2026-10-10 ("is valid"). `unify-catalog-loading --strict` also exits 0.
- [x] 3.3 Run `./gradlew build` from the repository root to confirm both trees are still green. Verify: the command exits with code 0. A failure is a pre-existing problem and is reported, not fixed in this change.
  - Outcome: `./gradlew build --continue --offline` exits 0 ("BUILD SUCCESSFUL") on 2026-10-10. No code changed in this change, so the build covers the docs commits only.
- [x] 3.4 Commit by Conventional Commits type: `docs(openspec)` for the change folder, `docs(architecture)` for the ADR, the explanation page, the pointers and the cross-references. Verify: `git log --oneline main..HEAD` lists only `docs(...)` subjects, and no commit mixes change folder files with other docs.
  - Outcome: the commits are `docs(adr)` (ADR-0017, as the owner instructed; it is not a `docs(architecture)` commit), `docs(architecture)` (the explanation page; the pointers and cross-references; the catalog row with research 005 Q1), `docs(openspec)` (unify-catalog-loading task 8.1 tick), and `docs(openspec)` (this change's folder). No commit mixes this change's folder with other docs.
- [x] 3.5 Open the pull request against `main` with the title `docs(architecture): define clean architecture with vertical slices and di`. The body lists the four rings, the four follow-up changes, the migration list summary and the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: `gh pr view --json title,baseRefName` returns that exact title and base `main`.
  - Outcome: merged locally on 2026-10-10 per owner decision; no PR.

## Workflow follow-up

- Create follow-up changes 1 to 4 with `openspec new change` after the user approves each: `add-architecture-slice-rules` (`test(fitness)`), `move-cup-flow-out-of-server` (`refactor(server)`), `regroup-platform-by-slice` (`refactor(platform)`) and `flatten-api-by-slice` (`refactor(api)`). A proposed follow-up 5, `regroup-setup-adapters`, needs its type chosen with the owner (item 26 of the migration list).
- Archive this change with the commit `docs(openspec): archive define-clean-architecture-with-vertical-slices`. This is the archive step, not a task of this change, and it is not ticked here.
- Update the slice table in `docs/explanation/architecture.md` and in `design.md` in the same pull request as each follow-up that moves a package.
