package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

/**
 * The old format's namespaced identifier, as Adventure's {@code Key} serialised it — for example
 * {@code {"namespace": "map", "value": "elytraraceblueandred"}}.
 */
public record LegacyKey(@Nullable String namespace, @Nullable String value) {
}
