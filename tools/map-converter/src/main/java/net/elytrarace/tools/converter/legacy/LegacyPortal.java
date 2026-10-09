package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One entry of the old {@code portals.json}: a one-based index, an unordered bag of block
 * coordinates of which exactly one is the centre, and a ring type name.
 *
 * <p>The old format stores neither a normal nor a radius — both are derived from the rim, which is
 * the whole reason this conversion exists rather than a runtime reader.
 */
public record LegacyPortal(@Nullable Integer index, @Nullable List<LegacyLocation> locations,
                           @Nullable String type) {
}
