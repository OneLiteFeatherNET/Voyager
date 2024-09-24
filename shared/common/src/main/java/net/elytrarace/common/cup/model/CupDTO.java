package net.elytrarace.common.cup.model;

/**
 * Data transfer object for a cup.
 */
public sealed interface CupDTO permits FileCupDTO, ResolvedCupDTO {

    /**
     * Get the name of the cup.
     *
     * @return The name of the cup.
     */
    String name();

    /**
     * Get the display name of the cup.
     *
     * @return The display name of the cup.
     */
    String displayName();

}
