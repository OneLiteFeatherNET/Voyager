package net.elytrarace.voyager.race.flow;

import java.time.Duration;

/**
 * How long each {@link RacePhase} lasts before {@link RaceStateMachine#advance} moves past it —
 * except {@code GAME}, which can also end early when every player finishes.
 */
public record RaceTimings(Duration lobby, Duration race, Duration end) {

    /**
     * Matches the old tree's defaults: lobby 120 s, race 300 s (the old
     * {@code DEFAULT_RACE_DURATION_TICKS} of 6000 at 20 TPS), end 100 s.
     */
    public static final RaceTimings DEFAULT =
            new RaceTimings(Duration.ofSeconds(120), Duration.ofSeconds(300), Duration.ofSeconds(100));

    public RaceTimings {
        if (lobby.isNegative() || race.isNegative() || end.isNegative()) {
            throw new IllegalArgumentException(
                    "race timings must not be negative, was lobby=%s race=%s end=%s".formatted(lobby, race, end));
        }
    }
}
