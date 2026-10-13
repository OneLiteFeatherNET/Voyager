# World lighting: stored light, computed light and the boot check

This page explains where a dark area in a racetrack or the lobby comes from, which light the server keeps, which light it
computes, and what the configuration check does not compute. It is an explanation. The decision record is
[ADR-0034](../decisions/0034-world-lighting.md), and the design with its measurements is the `world-lighting` change in
`openspec/changes/`.

## Where a dark area comes from

Every section of a chunk carries two light arrays, one for sky light and one for block light. Each array holds 2048
bytes, one nibble per block. The client draws a block with the light the server sends for its section. A section that
arrives with no light array is drawn dark.

The worlds in `run/` store almost no light. In the racetrack `ElytraraceBlueAndRed`, a read-only count found a stored
`SkyLight` in 2 277 of 343 048 sections and a stored `BlockLight` in 3 178 of them. About 99 % of sections store neither.
Stored light is therefore not enough to light a world, and the server has to compute the rest.

## Which light the server keeps

A stored light array is kept exactly as it was saved. The server does not recompute it at load time, because the light in
the region file is the light the world was built with. A section can keep its stored sky light and still have its block
light computed, if only one of the two arrays is missing.

An all-zero stored array is still a stored array. It is kept, and it is not computed.

## Which light the server computes

A light that has no stored array is marked for relight when its chunk loads. The light is computed the first time a viewer
is sent that chunk, by Minestom's light engine, `LightingChunk`. The computed light stays in memory for the life of the
server. It is never written to a region file, so every boot computes it again.

The server computes light lazily. It does not light the spawn area at boot. A racer who arrives at a chunk that nobody
has seen yet pays the cost when the chunk is sent. This was an owner decision: pre-lighting the spawn is possible later,
but it is not part of this change.

The lobby keeps sky light. No custom dimension type is used, so the lobby is lit by the same overworld rules as a map.

## The boot check computes no light

The configuration check reads every chunk of a world to find the holes in it. It loads each chunk and then releases it,
and it never sends one to a viewer. Light is computed only when a chunk is sent, so the check computes no light. Its time
and its peak memory do not include lighting, and the marking adds one flag write per light-less section.

## Writes

Nothing in the rebuild writes light to disk. The worlds are read-only and are never saved. A test checks that the region
file of a chunk is byte-identical after the server has sent that chunk.

## Cost

The numbers are in ADR-0034. In short: a viewer at the default view distance of 8 is sent at most 289 chunks, and the
computed light for a view of light-less sections is bounded by about 28 MB before packet caches. The real cost depends on
how much of each chunk lacks light, which the read-only count above shows is most of it.

## Where the code is

The marking lives in `net.elytrarace.voyager.platform.world.WorldLight`. `MapInstances` wraps the Falco loader of every
world it opens, and `TrackingInstance` builds its chunks as `LightingChunk`. The setup server opens its authoring worlds
through the same `MapInstances`, so its worlds are lit the same way.
