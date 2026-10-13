#!/usr/bin/env bash
# The configuration golden matrix (adopt-avaje-config, task 2.3 and 5.1).
#
# Runs the configuration check of a server jar on every case below and writes one normalised transcript per case.
# The transcripts are committed in expected/, generated once from the release jar before the configuration loader
# existed. The compatibility claim is that the same matrix, run against the new jar, produces the same files.
#
#   usage: run-matrix.sh <server-jar> <output-dir>
#
# The runs use an empty environment (only PATH and HOME), so a VOYAGER_* variable on the machine cannot change a
# result. Each case runs in its own work directory with a catalogue copied from src/main/resources and a worlds
# directory in which every world the maps name, plus LobbyTest (a folder no map plays, so a lobby can be set), links to
# WORLD_SOURCE, the shipped world (run/ is git-ignored, so a git worktree has none: pass WORLD_SOURCE with the path in
# the main checkout). The region data is the same for every name; the matrix checks how settings are read, not how a
# world loads. Output is normalised: ANSI colours, the log timestamps and the work directory's
# path are removed, so the transcripts are the same on every machine.
set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "usage: $0 <server-jar> <output-dir>" >&2
    exit 2
fi

JAR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
OUT="$2"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOURCES="$HERE/../../../main/resources"
REPO="$(cd "$HERE/../../../../../.." && pwd)"
WORLD_SOURCE="${WORLD_SOURCE:-$REPO/run/run/worlds/ElytraraceBlueAndRed}"
JAVA_BIN="${JAVA_BIN:-java}"

# name | -D properties (space separated) | command-line arguments (space separated)
CASES=(
    "default||"
    "cup-alpha|-DVOYAGER_CUP=alpha_cup|"
    "cup-legacy|-DVOYAGER_CUP=legacy_cup|"
    "cup-unknown|-DVOYAGER_CUP=no_such_cup|"
    "cup-empty|-DVOYAGER_CUP=|"
    "min-two|-DVOYAGER_MIN_PLAYERS=2|"
    "min-zero|-DVOYAGER_MIN_PLAYERS=0|"
    "min-text|-DVOYAGER_MIN_PLAYERS=two|"
    "min-empty|-DVOYAGER_MIN_PLAYERS=|"
    "max-eight|-DVOYAGER_MAX_PLAYERS=8|"
    "max-text|-DVOYAGER_MAX_PLAYERS=lots|"
    "max-below-min|-DVOYAGER_MAX_PLAYERS=1 -DVOYAGER_MIN_PLAYERS=3|"
    "data-missing|-DVOYAGER_DATA_PATH=no-such-catalog|"
    "worlds-missing|-DVOYAGER_WORLDS_PATH=no-such-worlds|"
    "data-is-a-file|-DVOYAGER_DATA_PATH=worlds/ElytraraceBlueAndRed/level.dat|"
    "dev-mode|-Dvoyager.dev=true|"
    "dev-mode-min-two|-Dvoyager.dev=true -DVOYAGER_MIN_PLAYERS=2|"
    "bind-port-set|-Dservice.bind.port=25570|"
    "bind-port-text|-Dservice.bind.port=abc|"
    "bind-port-range|-Dservice.bind.port=70000|"
    "bind-port-empty|-Dservice.bind.port=|"
    "argument-port-text||0.0.0.0 abc"
    "argument-port-ok||127.0.0.1 25599"
    "lobby-world-only|-DVOYAGER_LOBBY_WORLD=LobbyTest|"
    "lobby-spawn-only|-DVOYAGER_LOBBY_SPAWN=0,64,0|"
    "lobby-ok|-DVOYAGER_LOBBY_WORLD=LobbyTest -DVOYAGER_LOBBY_SPAWN=0,64,0|"
    "lobby-bad-spawn|-DVOYAGER_LOBBY_WORLD=LobbyTest -DVOYAGER_LOBBY_SPAWN=0,x,0|"
    "lobby-played-map|-DVOYAGER_LOBBY_WORLD=ElytraraceBlueAndRed -DVOYAGER_LOBBY_SPAWN=0,64,0|"
    "lobby-missing-folder|-DVOYAGER_LOBBY_WORLD=NoSuchWorld -DVOYAGER_LOBBY_SPAWN=0,64,0|"
    "velocity-secret-set|-DVOYAGER_CUP=alpha_cup -Dvoyager.velocity.secret=s3cret|"
    "unrelated-property|-Dsome.other.setting=1|"
    "dollar-brace-value|-DVOYAGER_DATA_PATH=\${HOME}/no-such-catalog|"
    "everything-valid|-DVOYAGER_CUP=alpha_cup -DVOYAGER_MIN_PLAYERS=1 -DVOYAGER_DATA_PATH=catalog -DVOYAGER_WORLDS_PATH=worlds -Dvoyager.dev=true -Dservice.bind.port=25566|"
)

mkdir -p "$OUT"
WORK_ROOT="$(mktemp -d)"
trap 'rm -rf "$WORK_ROOT"' EXIT

for entry in "${CASES[@]}"; do
    IFS='|' read -r name props args <<< "$entry"
    work="$WORK_ROOT/$name"
    mkdir -p "$work/catalog" "$work/worlds"
    cp -r "$RESOURCES/maps" "$RESOURCES/cups" "$work/catalog/"
    for world in ElytraraceBlueAndRed DragonLobby Memoria PalmBeach LobbyTest; do
        ln -s "$WORLD_SOURCE" "$work/worlds/$world"
    done
    # One unquoted expansion on purpose: each property and argument is one word.
    # shellcheck disable=SC2086
    set +e
    (cd "$work" && env -i PATH="$PATH" HOME="$HOME" LANG=C.UTF-8 \
        "$JAVA_BIN" -Dvoyager.config.check=true $props -jar "$JAR" $args > "$work/raw.txt" 2>&1)
    code=$?
    set -e
    sed -E 's/\x1b\[[0-9;]*m//g; s/^[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} //' "$work/raw.txt" \
        | sed "s#$work#<WORK>#g" > "$OUT/$name.txt"
    echo "exit=$code" >> "$OUT/$name.txt"
    echo "case $name: exit $code"
done
