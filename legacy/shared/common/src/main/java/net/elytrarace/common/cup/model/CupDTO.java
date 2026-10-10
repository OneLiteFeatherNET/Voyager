package net.elytrarace.common.cup.model;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Data transfer object for a cup.
 */
public sealed interface CupDTO permits FileCupDTO, ResolvedCupDTO {

    /**
     * Get the name of the cup.
     *
     * @return The name of the cup.
     */
    Key name();

    /**
     * Get the display name of the cup.
     *
     * @return The display name of the cup.
     */
    Component displayName();

}
