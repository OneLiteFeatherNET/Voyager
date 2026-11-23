# Design

## Dependency direction

The COM brain is pure in `voyager-race` (`net.elytrarace.voyager.race.com`). It already takes the boost as a parameter, a
`ComSpeedEffect`, and its own comment says "the composition root can bridge the real effect later"
(`voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComSpeedEffect.java:8-10`). This change is that bridge. The map's
factor lives in `voyager-api` (`RingEffectTuning.boostMultiplier`, carried by `MapDefinition.ringEffects()`), so the bridge is built
in `voyager-platform` where the map is known, and passed down. `voyager-race` gains no platform type.

```
api:      MapDefinition.ringEffects() -> RingEffectTuning.boostMultiplier
platform: ComField (knows the map per racer) --builds--> ComSpeedEffect.multiplying(factor)
platform: ComField --ComBrain.step(state, tuning, path, tick, boost)--> PilotBrain --> race: ComPilot.step (pure)
```

## Changes by module

| Module | Type or method | Change |
|---|---|---|
| `voyager-race` | `ComSpeedEffect` | Add `static ComSpeedEffect multiplying(double factor)`, returning `targetSpeed -> targetSpeed * factor`. The factor is validated by `RingEffectTuning` already (finite, zero or more); the factory adds no new check. `NONE` stays. |
| `voyager-race` | `ComPilot` | Unchanged. The five-argument `step` already applies the effect on the due tick (`ComPilot.java:99-101`). |
| `voyager-platform` | `ComBrain.step` (package-private interface, `platform.com`) | Gains a `ComSpeedEffect boost` parameter. |
| `voyager-platform` | `PilotBrain` | Passes the parameter to the five-argument `ComPilot.step` instead of the four-argument overload. |
| `voyager-platform` | `ComField` (`ComField.java:134`) | Passes `ComSpeedEffect.multiplying(map.ringEffects().boostMultiplier())` for the racer's map. The map is known where the racer's path is set for the map; the effect is stored with the racer beside `tuning` and `path`. |

`ComPilot.step` and `ComSpeedEffect.NONE` are unchanged for every caller that does not pass a boost.

## Boundary rules

- `ComSpeedEffect.multiplying` is a pure factory in the `race` module, on an `@ApiStatus.Internal` interface. It adds no type and
  no exception.
- The factor comes from an `api` record that is already validated. No new fitness rule: the existing `voyager-fitness` rules for
  `race` and `platform` cover the change.

## Test seam

`ComField` already takes its brain through a package-private constructor (`ComField.java:87-88`, the `ComBrain brain` overload), so
a test puts a fake brain in place that records the effect it receives. `PilotBrain` is tested directly with explicit ticks and a
recording `ComSpeedEffect`.

## Decisions

No ADR. The change uses the effect the brain already declares for this purpose; it adds no architecture. The balance consequence
is an owner decision, recorded in the proposal's open questions.
