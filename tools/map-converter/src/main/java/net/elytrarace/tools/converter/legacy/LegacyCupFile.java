package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One entry of the old {@code cups.json}: a namespaced name and the maps of its rotation, named by
 * UUID.
 *
 * <p>The old format carries no game mode — a cup was always played the same way — so the mode is
 * supplied by the converter rather than read from here.
 */
public record LegacyCupFile(@Nullable LegacyKey name, @Nullable List<LegacyUuid> maps) {
}
