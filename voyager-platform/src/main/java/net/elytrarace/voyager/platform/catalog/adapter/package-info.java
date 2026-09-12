/**
 * The Gson deserialisers for the catalogue's file format — design rule 8: named {@code *Adapter},
 * {@code final}, and in an {@code adapter} subpackage.
 *
 * <p>They are public because they are registered from the package next door, and
 * {@code @ApiStatus.Internal} because that is the only reason: nothing outside
 * {@code net.elytrarace.voyager.platform.catalog} should hold one. Gson itself is an
 * {@code implementation} dependency of this module, so it never reaches a consumer's classpath —
 * the catalogue's public surface is two constructors taking a {@link java.nio.file.Path}.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.catalog.adapter;

import org.jetbrains.annotations.NotNullByDefault;
