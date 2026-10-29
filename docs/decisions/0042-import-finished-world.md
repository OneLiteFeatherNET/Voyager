# ADR-0042: An imported world is copied into the map's world folder

## Status

Proposed

The owner accepts or rejects this record. It moves to Accepted only on the owner's word. It belongs to the change
`import-finished-world` (OpenSpec, design decision 1).

## Date

2026-10-29

## Context

A builder's finished world sits in a folder outside the game and the setup server. The setup server must make it a map,
and the game server must play it. The game server reads one place for a map's world, `<worldsPath>/<id>`, and the setup
server's worlds folder is the only place it writes. The question is whether the map's world is a copy of the source, a
link to it, or a read of the source at the time of play.

## Decision drivers

- A race in progress must not change because a file in the import folder changed.
- The game server must not depend on a path outside its worlds folder.
- A failed import must leave nothing that the game could load.

## Decision

Copy the region files into `worlds/<id>`, so the map's world is a copy of the source from the moment of the import. The
copy is the map's world, as the void template already is, so the game server reads one kind of world. Its cost is disk
space: one import copies at most 256 MiB and 1024 region files by default.

*Rejected:* a symbolic link from `worlds/<id>` to the source folder, because the game server would follow a link out of
its worlds folder and would see every later change to the source.

*Rejected:* reading the import folder when the map is played, because the source would then be a runtime dependency of the
game, and a change or a removal during a race would reach the race.

## Consequences

- Good: the game server is unchanged, and it reads the same folder for every map.
- Good: a change to the source during or after the import does not reach a map.
- Bad: the disk holds one copy per import. The size limit bounds one import, not the total.
- Neutral: a builder who wants a newer version of the source imports again under a new id, or removes the old map first,
  as the existing `/map new` rules require.

## Confirmation

The copy tests of `FolderWorldImporterTest` show that only region files are copied and that a refused import leaves no
folder. The import integration test loads the copy through `MapInstances.checkWorld`, not the source.
