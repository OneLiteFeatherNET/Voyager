package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

/**
 * The old {@code map.json}. Only three of its fields survive the conversion — the identifier, the
 * name and the world directory.
 *
 * <p>{@code displayName}, {@code author} and {@code boostConfig} are deliberately absent from this
 * record: {@code MapDefinition} has nowhere to put them. Display name and author are presentation
 * the rebuild has not designed yet, and the boost configuration belongs to the physics stage's
 * tuning rather than to a racecourse. The {@code portals} array inside {@code map.json} is empty in
 * every file in the repository — the rings live in {@code portals.json} beside it.
 */
public record LegacyMapFile(@Nullable LegacyUuid uuid, @Nullable LegacyKey name, @Nullable String world) {
}
