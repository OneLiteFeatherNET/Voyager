package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;

/**
 * Stores a player's score, split into ring points and position bonus.
 */
public class ScoreComponent implements Component {

    private int ringPoints;
    private int positionBonus;

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

    /**
     * Resets the score to zero.
     */
    public void reset() {
        this.ringPoints = 0;
        this.positionBonus = 0;
    }
}
