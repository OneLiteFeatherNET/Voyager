package net.elytrarace.setup.command;

import net.elytrarace.common.map.model.FilePortalDTO;

import java.util.UUID;

/**
 * Holds the state of a portal currently being edited by a builder.
 */
public record EditingContext(
        UUID mapUuid,
        int portalIndex,
        FilePortalDTO originalPortal
) {}
