package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

/**
 * The old {@code map.json}. Four of its fields survive the conversion — the identifier, the name, the
 * world directory and the boost tuning.
 *
 * <p>{@code displayName} and {@code author} are deliberately absent from this record:
 * {@code MapDefinition} has nowhere to put them, and presentation is something the rebuild has not
 * designed yet. The {@code portals} array inside {@code map.json} is empty in every file in the
 * repository — the rings live in {@code portals.json} beside it.
 *
 * <p>{@code boostConfig} <em>is</em> read, and was not in the first version of this converter. That
 * omission is why the committed course shipped with no boost tuning at all and had to be corrected;
 * see {@link LegacyBoostConfig} for the two shapes it arrives in and which parts of it are kept.
 */
public record LegacyMapFile(@Nullable LegacyUuid uuid, @Nullable LegacyKey name, @Nullable String world,
        @Nullable LegacyBoostConfig boostConfig) {
}
