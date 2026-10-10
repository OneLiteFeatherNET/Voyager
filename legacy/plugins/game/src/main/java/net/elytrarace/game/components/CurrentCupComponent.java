package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Component that stores the current cup information.
 * This is part of the flattened architecture, replacing part of the GameStateComponent.
 */
public record CurrentCupComponent(
    Key cupId,
    net.kyori.adventure.text.Component displayName,
    List<Key> mapIds
) implements Component {

    /**
     * Creates a new CurrentCupComponent with the given cup ID, display name, and map IDs.
     */
    public static CurrentCupComponent create(Key cupId, net.kyori.adventure.text.Component displayName, List<Key> mapIds) {
        return new CurrentCupComponent(cupId, displayName, mapIds);
    }

    /**
     * Creates a new CurrentCupComponent with an empty cup.
     */
    public static CurrentCupComponent createEmpty() {
        return new CurrentCupComponent(null, null, List.of());
    }

    /**
     * Checks if this component has a cup.
     */
    public boolean hasCup() {
        return cupId != null;
    }
}
