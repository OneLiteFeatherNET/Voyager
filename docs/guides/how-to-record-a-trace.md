# How to record a trace

This guide explains how to record an E2a flight-profile fixture: a scripted elytra glide flown on
a real Paper server, sampled tick by tick, and written to a JSON file which is then committed to
`voyager-physics/src/test/resources/traces/` — the fixtures' one home, next to the tests that
replay them. `voyager-physics`'s `VanillaParityTest` replays every one of them as a one-step
residual check against the ported `ElytraSimulator` — recorded state at tick `k`, one tick
computed, held against tick `k + 1` — at a bound of **exactly zero**.

**What a fixture proves, and what it does not.** A trace validates the physics *formula* — whether
`ElytraSimulator.tick` reproduces the same velocity Vanilla produced from the same starting state.
It says nothing about the client-server path: packet timing, input lag, or how a real player's
client-authoritative movement reconciles with the server's tracked velocity. Do not read a passing
trace suite as proof the network path is correct; it only proves the arithmetic is.

## 1. Get a matching Paper build

The recorder has to run the exact Minecraft version `voyager-physics` targets — `26.2`, per this
repository's `CLAUDE.md`. Download it from PaperMC's Fill API
(`https://fill.papermc.io/v3/projects/paper`; the v2 API is sunset) and accept the EULA
(`eula.txt` containing `eula=true`) before the server will start.

**A Minecraft version bump invalidates every fixture in
`voyager-physics/src/test/resources/traces/`.** The
recorded numbers are Vanilla `26.2`'s output; if Mojang changes the elytra formula, a constant, or
even an unrelated tick-order detail that this recorder happens to depend on, every existing trace
now describes a version of the game that no longer exists on the server that would replay it
against a live comparison. When the target version changes, re-record all nine profiles against
the new build before trusting the suite again — do not assume old fixtures still apply.

## 2. The activation-range setting, and why it is not optional

Set this in the server's `spigot.yml` **before recording anything**, then restart the server:

```yaml
world-settings:
  default:
    entity-activation-range:
      monsters: 0
```

Without it, a recording silently stops progressing at entity age (`Entity#tickCount`) 200 — not a
crash, not an error, just `tickCount` climbing while `travel()` never runs again, which
`TraceCollector` eventually catches as a stall and aborts.

The cause: Paper's `ActivationRange#checkIfActive` grants every entity a hard 200-tick grace period
after spawn — below `tickCount` 200 it returns active unconditionally. From tick 200 on,
`activateEntities` derives `activatedTick` **only** from `Level#players()`. A recording driven from
the server console (or RCON, as this guide uses) has no player online, so the glider can never
become active again through that path. A `(currentTick - activatedTick - 1) % 20 == 0` fallback
wakes it irregularly, which is why a stalled recording's gap size varies while the failure point
(tick 200) does not.

Setting `monsters: 0` makes `initializeEntityActivationState` set `defaultActivationState = true`
for the glider (a `Zombie` wearing an elytra — see `GliderRunner`), bypassing the check without
needing a player. This was measured directly during E2a: 600 ticks clean, `entityTick` gapless,
and a 250-tick probe recorded with the setting is bit-identical, tick for tick, to a stock recording
up to the point the stock one stalls. **The setting does not change the physics** — it only removes
an artifact of recording from the console. Every profile in this repository runs well past 200
ticks (`wall-graze` at 220), so every one of them needs this setting.

## 3. Build and install the plugin

```bash
./gradlew :tools:trace-recorder:shadowJar
cp tools/trace-recorder/build/libs/trace-recorder-<version>-all.jar <paper-server>/plugins/
```

Restart the server (or start it for the first time) so the plugin loads. On enable it registers
`/record <profile>` behind the `trace-recorder.record` permission — RCON and the console both
carry full permissions by default, so no explicit grant is needed to drive a recording headlessly.

Scripts live in the plugin's data folder, not on the classpath: copy every `.txt` file from
`tools/trace-recorder/scripts/` into `<paper-server>/plugins/trace-recorder/scripts/` before
recording. `/record <profile>` reads `plugins/trace-recorder/scripts/<profile>.txt`; a missing file
fails with "No script found at ...".

## 4. Recording without a client

There is no Minecraft client in this loop. `/record` is driven over RCON, from the console, using
`execute positioned` — a bare console/RCON `CommandSourceStack` has no standing location of its
own (it defaults to a fixed point in the world), so every invocation must supply one explicitly:

```
execute positioned <x> <y> <z> run record <profile>
```

The glider spawns 2 blocks above the given point. `/record` returns immediately — it schedules a
multi-tick process and the RCON response carries no useful status — so poll for the output file
instead of trusting the RCON reply:

```
<paper-server>/plugins/trace-recorder/traces/<profile>.json
```

A 200-tick profile takes roughly 10 real seconds at 20 TPS, plus the fixed 3-tick settle described
in `GliderRunner`'s Javadoc (`input[0]`'s rotation is held for 2 unrecorded ticks, plus one more
inside the first `driveTick`, before trace index 0 is sampled) — wait at least that long, with
margin, before concluding a recording has failed rather than merely not finished yet.

Only one recording runs at a time (`RecorderPlugin.recordingInProgress`); do not fire a second
`/record` before the previous profile's file has appeared.

## 5. What each profile needs from the world

Seven of the nine profiles fly through open sky and need no terrain at all — but *placement still
matters*. Every non-terrain profile in this repository is recorded at `x=0 z=0`, all flying along
`+Z` or turning from it; a leftover block anywhere near that column silently produces an
unscripted collision, not a script bug. Pick spawn columns with sky verified clear along the whole
flight path (`worldSlice: 0` in the resulting fixture is that verification, after the fact — see
§6), and keep terrain profiles in their own columns, well clear of every other profile's path.

| Profile | World | Recorded at |
|---|---|---|
| `steady-glide` | open sky | `x=0 y=300 z=0` |
| `climb-into-stall` | open sky | `x=0 y=300 z=0` |
| `dive-and-pull-out` | open sky | `x=0 y=300 z=0` |
| `single-boost` | open sky | `x=0 y=300 z=0` |
| `chained-boosts` | open sky | `x=0 y=300 z=0` |
| `pitch-extremes` | open sky | `x=0 y=300 z=0` |
| `sustained-turn` | open sky | `x=0 y=300 z=0` |
| `wall-graze` | stone wall, `x=990..1010 y=250..310`, 1 block thick at `z=90` | `x=1000 y=300 z=0` |
| `landing` | flat stone floor, `x=1990..2010 z=-10..300` at `y=265` | `x=2000 y=300 z=0` |

Build terrain with `/fill` before recording (`/forceload add <x1> <z1> <x2> <z2>` first, if the
target chunks are not already loaded — a `/fill` into unloaded chunks fails with "not loaded", and
one whose Y range exceeds the world's build height fails with "out of this world" **but Minecraft
has been observed to partially apply such a fill up to the valid height before reporting the
error** — always verify with a matching `/fill ... air` or a probe recording rather than trusting
the error message to mean nothing happened. `wall-graze` and `landing` each got their own X column,
1000 blocks apart, specifically so neither profile's flight path can ever cross the other's
geometry — the first attempt at `landing` was recorded sharing `wall-graze`'s column and picked up
an extra, unscripted horizontal collision with the wall on the way down, ten ticks or so before it
ever reached the floor a `worldSlice` inspection was the only reason that was caught.

Each script's `# world:` comment line states exactly what its terrain must be, in absolute
coordinates — treat that line as authoritative and build precisely that, not an approximation.

## 6. Verifying a fixture

### Structural checks (every profile)

- `ticks` has exactly as many entries as the script's total tick count (sum of every `hold`/`ramp`
  span, plus one per `boost`), indices consecutive from `0`.
- `entityTick` increases by exactly `1` between consecutive samples. `TraceCollector.record` enforces
  this at recording time — a fixture that made it to disk already satisfies it — but re-check after
  hand-editing a fixture for any reason.
- `metadata.gravity` is `0.08` (the default `Attribute.GRAVITY`, read live from the spawned
  entity, not hardcoded) unless the profile deliberately used a different gravity modifier.
- `worldSlice` is `[]` for every open-sky profile, and **non-empty** for `wall-graze` and
  `landing`. A non-empty slice for an open-sky profile means the flight path clipped something it
  should not have — treat the recording as contaminated and re-record after clearing the world, not
  as a passing fixture with bonus data.
- For `wall-graze` and `landing`, the `worldSlice` boxes' coordinates must fall inside the geometry
  the script's `# world:` line describes. Compare `minX`/`minY`/`minZ`/`maxX`/`maxY`/`maxZ` against
  the documented range by eye. A slice with the wrong coordinates is worse than an empty one: a
  replay would resolve collisions against geometry that was never actually there.

### Behavioural checks (does it do what its name says)

- `steady-glide`: `yaw` and `pitch` constant, position drifting smoothly, no discontinuities.
- `climb-into-stall`: horizontal speed (`hypot(velX, velZ)`) rises during the trim phase, then
  visibly drops once the climb pitch takes hold.
- `dive-and-pull-out`: horizontal speed rises substantially during the dive, peaking after the
  pull-out ramp completes.
- `single-boost`: a sharp jump in horizontal speed at the boost tick (`fireworkBoostActive: true`),
  decaying over the following ticks as the rocket burns out (`fireworkTicksRemaining` counting down
  to `0`).
- `chained-boosts`: a second `fireworkBoostActive: true` tick appears while
  `fireworkTicksRemaining` on the *previous* sample was still greater than `0` — check the ticks
  around both boosts by hand; a second boost that lands after the first one's countdown already hit
  zero is not exercising the "still active" case the profile is for.
- `pitch-extremes`: no `NaN`/`Infinity` anywhere, including at the exact `±90` holds where
  `lookHorLength` is genuinely zero.
- `wall-graze`: a hard discontinuity in the horizontal velocity component facing the wall (here,
  `velZ`) at one specific tick, dropping to (numerically) `0`, with position pinned at the wall face
  afterward.
- `landing`: `onGround` flips from `false` to `true` at one tick and stays `true` through the end of
  the recording, vertical velocity settling near `0`.
- `sustained-turn`: `yaw` changes across the recording (not constant like every other profile), and
  `velX` is non-zero somewhere. This is the one acceptance condition that spans the whole set, not
  a single fixture — see the next section.

A profile that does not show its behaviour is a broken script, not a broken port: fix the script
and re-record. Do not adjust the physics or the recorder to force a particular-looking result out
of a script that is not actually exercising what it claims to.

### The one check that spans all nine fixtures

Every profile except `sustained-turn` flies at `yaw = 0`. At `yaw = 0` the look vector's `x`
component is exactly zero, and with it the entire `x` axis of the tick's arithmetic — `velX` stays
`0.0` for the whole recording regardless of whether the port's yaw handling is correct. Before
trusting the suite as a set, confirm across all nine fixtures that `yaw` takes more than one value
somewhere and that `velX` is non-zero somewhere — `sustained-turn` is what supplies both. A
reference suite that happened to lose this profile would silently calibrate the whole `x` axis
against a constant.

### The one-step residual check

`voyager-physics`'s `VanillaParityTest` replays every fixture under `/traces/` on its test
classpath — discovery, not a list, so a tenth profile is one committed file and no code change. It
runs two forms, and only the first sets the bound:

- **One-step residual.** From the recorded state at tick `k`, run `ElytraSimulator.tick` for exactly
  one tick with tick `k + 1`'s rotation and boost state, and compare against the recorded state at
  `k + 1`. This isolates one tick's arithmetic from any accumulated drift.
- **Free-running replay.** Seed once from tick 0 and never correct. This is the only form that can
  see an error which compounds — and the only form that can invent one: while the recorder's
  first-tick transient was unfixed, a fixture that is bit-exact step by step still showed `8.2e-02`
  of free-running drift, purely as the decaying echo of one bad transition. Never calibrate against
  it alone.

**Every comparable tick of all nine fixtures is bit-exact** — position, velocity and `onGround`,
with no tolerance at all. The per-profile figures are in
[docs/reference/elytra-physics-26.2.md](../reference/elytra-physics-26.2.md#measured-parity-e2b-task-7);
that document is the record, this guide does not duplicate it.

Reaching that took three corrections to the port, all found by these recordings and all invisible at
the `1e-6` bound the plan had assumed: the firework impulse ran before the steps instead of after the
move, `LivingEntity.aiStep`'s `0.003` movement deadzone was missing entirely, and the entity box used
`double` literals where Vanilla halves and widens a `float`.

**So if a profile you record comes back non-exact, that is a signal, not an expectation.** Two
things it is worth ruling out before suspecting the port:

- **Did the script record cleanly?** Run §6's structural checks first. A stalled tick, an unscripted
  collision from a leftover block near the flight column, or a rotation that never took effect all
  look like physics defects from the residual alone.
- **Is the profile asking the fixture format a question it cannot answer?** Two ticks are compared
  on position only, both for reasons that belong to the recording rather than to the port, and both
  documented in the reference: the ticks after a touchdown, where Vanilla has stopped gliding
  altogether, and the ticks of a burn with a second ignition, where the format's single boolean
  cannot say how many rockets were firing.

## 7. When to re-record

- **A Minecraft version change** — re-record all nine, per §1. There is no partial-validity state:
  a fixture recorded against a different Vanilla build is testing a formula that may no longer
  match.
- **A script changes** — the script is the source of truth, not the recording. If
  `tools/trace-recorder/scripts/<profile>.txt` changes for any reason (a tuning adjustment, a fix
  to a profile that was not exercising its intended behaviour), the corresponding fixture is stale
  the moment the script is edited and must be re-recorded before the next fixture-consuming test
  run. Never hand-edit a `.json` fixture to make a test pass — the whole point of this pipeline is
  that the numbers come from Vanilla, not from whoever is looking at a failing assertion.
- **The recorder itself changes** in a way that could affect sampling (the settle-tick count, the
  stall-detection thresholds, `WorldSliceCollector`'s radius) — re-record and re-verify at least
  `steady-glide` and one terrain profile to confirm nothing shifted.

## 8. Known format defect: the rocket count

`GliderSample` carries one `fireworkBoostActive` boolean and one `fireworkTicksRemaining` that
`GliderRunner.sampleGlider` computes as the **maximum** over every attached rocket. One rocket and
three therefore record identically, while Vanilla applies one impulse per rocket per tick. That makes
`chained-boosts` — the profile whose whole purpose is a second ignition into a live burn — the one
fixture whose recorded *velocity* cannot be compared through its burn; `VanillaParityTest` derives
those ticks from the fixture and compares them on position only, and
`theOnlyUnaccountableBoostWindowIsTheOneWithASecondIgnition` fails the moment that stops being true
of exactly one profile.

**The fix is in the recorder, not in the test:** sample `activeFireworks.size()` alongside the
existing fields, bump `formatVersion` to `2`, re-record `single-boost` and `chained-boosts`, and
delete the carve-out rather than keep it. A second, smaller artefact is already compensated for and
needs no format change, but is worth knowing about when reading a fixture by hand: because the sample
for tick *n* is read at the start of tick *n+1*, a rocket that boosted during tick *n* and detonated
at the end of it is already filtered out, so the flag is `false` on the last boosted tick of every
burn.
