package net.elytrarace.voyager.api.mapsetup;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What an import copied and found, shown to the builder before any ring is placed.
 *
 * @param layout           the layout the region files were read from
 * @param regionFiles      the number of region files copied
 * @param regionBytes      the bytes of the region files copied
 * @param ignoredDimensions the other dimensions that were present and not copied, such as {@code nether}; empty when none
 * @param spawn            the spawn the world states, or {@code null} when it states none
 * @param census           the chunk statuses of the copy
 */
public record ImportReport(
        WorldLayout layout,
        int regionFiles,
        long regionBytes,
        List<String> ignoredDimensions,
        @Nullable StatedSpawn spawn,
        Census census) {

    public ImportReport {
        ignoredDimensions = List.copyOf(ignoredDimensions);
    }
}
