/**
 * The Minestom extension that connects CloudNet's permission checks to the pointer every Voyager player carries (ADR-0026).
 * The only package in the rebuild that names a {@code eu.cloudnetservice} type, and it loads from the node's extensions/
 * folder, never from a root's fat jar.
 */
@NotNullByDefault
package net.elytrarace.voyager.cloudnet.bridge;

import org.jetbrains.annotations.NotNullByDefault;
