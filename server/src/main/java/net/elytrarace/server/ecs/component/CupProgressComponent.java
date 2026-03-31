package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.MapDefinition;

/**
 * Tracks the progress through a cup (sequence of maps) on the game entity.
 */
public class CupProgressComponent implements Component {

    private final CupDefinition cup;
    private int currentMapIndex;
    private boolean complete;

    public CupProgressComponent(CupDefinition cup) {
        this.cup = cup;
        this.currentMapIndex = 0;
        this.complete = false;
    }

    public CupDefinition getCup() {
        return cup;
    }

    public int getCurrentMapIndex() {
        return currentMapIndex;
    }

    /**
     * Returns the current map definition, or {@code null} if the cup is complete.
     */
    public MapDefinition getCurrentMap() {
        if (complete || currentMapIndex >= cup.maps().size()) {
            return null;
        }
        return cup.maps().get(currentMapIndex);
    }

    /**
     * Advances to the next map. Marks the cup as complete if there are no more maps.
     */
    public void advance() {
        currentMapIndex++;
        if (currentMapIndex >= cup.maps().size()) {
            complete = true;
        }
    }

    public boolean isComplete() {
        return complete;
    }

    /**
     * Returns the total number of maps in this cup.
     */
    public int totalMaps() {
        return cup.maps().size();
    }

    /**
     * Resets the cup progress back to the first map.
     */
    public void reset() {
        this.currentMapIndex = 0;
        this.complete = false;
    }
}
