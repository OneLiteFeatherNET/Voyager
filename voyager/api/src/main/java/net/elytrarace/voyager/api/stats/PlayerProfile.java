package net.elytrarace.voyager.api.stats;

import net.elytrarace.voyager.api.race.GameMode;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A player's statistics as shown by {@code /stats}. Counts cover ranked play only.
 *
 * @param playerId        the player's UUID
 * @param lastKnownName   the last name seen, or {@code null} when the player has never been recorded
 * @param cupsPlayed      cups the player took part in, never negative
 * @param cupsWon         cups the player won, never negative and never more than {@code cupsPlayed}
 * @param diamonds        diamond medals over all ranked maps, never negative
 * @param golds           gold medals, never negative
 * @param silvers         silver medals, never negative
 * @param bronzes         bronze medals, never negative
 * @param flightMillis    total gliding time, never negative
 * @param personalBests   the player's fastest time per map, layout and mode
 */
public record PlayerProfile(UUID playerId, @Nullable String lastKnownName, int cupsPlayed, int cupsWon,
        int diamonds, int golds, int silvers, int bronzes, long flightMillis,
        List<PlayerProfile.PersonalBest> personalBests) {

    public PlayerProfile {
        if (playerId == null) {
            throw new IllegalArgumentException("player id must not be null");
        }
        if (cupsPlayed < 0 || cupsWon < 0 || diamonds < 0 || golds < 0 || silvers < 0 || bronzes < 0
                || flightMillis < 0) {
            throw new IllegalArgumentException(
                    "profile counts must not be negative, was cups %s, wins %s, medals %s/%s/%s/%s, flight %s"
                            .formatted(cupsPlayed, cupsWon, diamonds, golds, silvers, bronzes, flightMillis));
        }
        if (cupsWon > cupsPlayed) {
            throw new IllegalArgumentException(
                    "cup wins cannot exceed cups played, was %s of %s".formatted(cupsWon, cupsPlayed));
        }
        personalBests = List.copyOf(personalBests);
    }

    /**
     * A fastest time on one map.
     *
     * @param mapKey        the map's name in its file
     * @param layoutVersion the layout the time was set on
     * @param mode          the game mode the time was set in
     * @param bestTimeMillis the fastest finish, never negative
     * @param achievedAt    when the fastest finish happened
     */
    public record PersonalBest(String mapKey, String layoutVersion, GameMode mode, int bestTimeMillis,
            Instant achievedAt) {

        public PersonalBest {
            if (mapKey == null || mapKey.isBlank()) {
                throw new IllegalArgumentException("map key must not be blank");
            }
            if (bestTimeMillis < 0) {
                throw new IllegalArgumentException("best time must not be negative, was %s".formatted(bestTimeMillis));
            }
        }
    }
}
