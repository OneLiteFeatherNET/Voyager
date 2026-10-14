package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.CupRacerResult;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RacerRun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds completions from plain values, so each test states only what it checks. Every instant is passed in
 * explicitly; nothing reads the wall clock.
 */
final class CompletionFixtures {

    static final String LAYOUT_VERSION = "0123456789abcdef";
    static final Instant START = Instant.parse("2026-10-14T10:00:00Z");
    static final Instant END = START.plusSeconds(90);

    private CompletionFixtures() {
    }

    /** A map with the given racers and bot count, cup-less, in {@code mode}. */
    static RaceCompletion race(String mapKey, GameMode mode, int botCount, RacerRun... racers) {
        return new RaceCompletion(mapKey, LAYOUT_VERSION, mode, null, START, END, botCount, List.of(racers));
    }

    /** A racer who reached the last ring: position, time, medal and the given counts. */
    static RacerRun finisher(UUID playerId, String name, int position, int timeMillis, MedalTier tier,
            int boosts, int resets, long flightMillis, List<Integer> splitTicks) {
        return new RacerRun(playerId, name, position, timeMillis,
                splitTicks.size() * 10, placementBonus(position), tier.medalPoints(), tier,
                splitTicks.size(), boosts, resets, flightMillis, splitTicks);
    }

    /** A racer who did not reach the last ring before the map ended. */
    static RacerRun dnf(UUID playerId, String name, List<Integer> splitTicks) {
        return new RacerRun(playerId, name, null, null,
                splitTicks.size() * 10, 0, MedalTier.DNF.medalPoints(), MedalTier.DNF,
                splitTicks.size(), 0, 0, 0L, splitTicks);
    }

    /** A cup of the given racers in {@code mode}. */
    static CupCompletion cup(String cupKey, GameMode mode, CupRacerResult... racers) {
        return new CupCompletion(cupKey, mode, START, END, List.of(racers));
    }

    /** A racer's standing over a whole cup. */
    static CupRacerResult cupResult(UUID playerId, String name, int position, int points, int maps,
            Integer bestTimeMillis, int diamonds) {
        return new CupRacerResult(playerId, name, position, points, maps, bestTimeMillis, diamonds, 0, 0, 0);
    }

    static UUID player(int number) {
        return new UUID(0L, number);
    }

    private static int placementBonus(int position) {
        return Math.max(0, 4 - position) * 5;
    }
}
