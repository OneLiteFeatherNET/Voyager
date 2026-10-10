package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.game.scoring.MedalTier;
import org.jetbrains.annotations.Nullable;

/**
 * Stores a player's per-map score state: ring points, position bonus, completion
 * time, and the resulting medal tier.
 * <p>
 * The fields are written by different actors:
 * <ul>
 *   <li>{@code ringPoints} — incremented by {@link net.elytrarace.server.ecs.system.RingCollisionSystem}</li>
 *   <li>{@code positionBonus} — set by {@link net.elytrarace.server.phase.MinestomEndPhase}
 *       after ranking players by total score (RACE mode only)</li>
 *   <li>{@code completionTimeMs} — set by {@code CompletionDetectionSystem} when the player
 *       crosses the last ring</li>
 *   <li>{@code medalTier} — set by {@code CompletionDetectionSystem} when the run is
 *       classified against the map's bracket configuration</li>
 * </ul>
 */
public class ScoreComponent implements Component {

    /** Sentinel for a player that did not finish the current map. */
    public static final long NOT_FINISHED = -1L;

    private int ringPoints;
    private int positionBonus;
    private long completionTimeMs = NOT_FINISHED;
    private @Nullable MedalTier medalTier;

    /**
     * Returns the total score (ring points + position bonus).
     */
    public int getTotal() {
        return ringPoints + positionBonus;
    }

    public int getRingPoints() {
        return ringPoints;
    }

    public void addRingPoints(int points) {
        this.ringPoints += points;
    }

    public int getPositionBonus() {
        return positionBonus;
    }

    public void setPositionBonus(int positionBonus) {
        this.positionBonus = positionBonus;
    }

    public void addPositionBonus(int bonus) {
        this.positionBonus += bonus;
    }

    /**
     * Returns the player's map completion time in milliseconds, or
     * {@link #NOT_FINISHED} if they have not finished yet.
     */
    public long getCompletionTimeMs() {
        return completionTimeMs;
    }

    public void setCompletionTimeMs(long completionTimeMs) {
        this.completionTimeMs = completionTimeMs;
    }

    /**
     * Returns whether the player crossed the last ring on the current map.
     */
    public boolean hasFinished() {
        return completionTimeMs >= 0;
    }

    /**
     * Returns the medal tier earned on the current map, or {@code null} if the
     * map has not been classified yet.
     */
    public @Nullable MedalTier getMedalTier() {
        return medalTier;
    }

    public void setMedalTier(@Nullable MedalTier medalTier) {
        this.medalTier = medalTier;
    }

    /**
     * Resets the score for a new map. Clears ring points, position bonus,
     * completion time, and medal tier.
     */
    public void reset() {
        this.ringPoints = 0;
        this.positionBonus = 0;
        this.completionTimeMs = NOT_FINISHED;
        this.medalTier = null;
    }
}
