# Pull request text for add-waiting-lobby

Title (a valid Conventional Commit, the squash-merge commit on main):

    feat(race): wait in a lobby until enough racers have joined

Body:

Starts a cup only once enough racers are online (`VOYAGER_MIN_PLAYERS`, default 2, or 1 under `voyager.dev`).
Waiting racers stand on the first map's spawn without elytra or rockets. The start countdown is the cup's own first
lobby: a drop below the minimum cancels it until its last three seconds, after which the start is committed. A cup
aborts when its last racer leaves, a finished cup returns the room to waiting, and a racer who joins a running cup is
told to join at the next map. The pure rule is `race.flow.StartGate`; the Minestom glue is the new `platform.lobby`
slice. The countdown is shown in whole seconds (closes #101 for the rebuild). Refines #170 (the per-map time limit
and production exposure of `/race start` are separate changes).

BREAKING: a single-player production server no longer starts a cup on its own. Set `VOYAGER_MIN_PLAYERS=1` or use
`/race start` to restore it.

Decision record: ADR-0022 (status Proposed). Specification and design: `openspec/changes/add-waiting-lobby`.

https://claude.ai/referral/m5Ak2Sa7aQ
https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz
