/**
 * Every user-facing string the game produces, and the colours it produces them in.
 *
 * <p>Nothing outside this package builds a sentence for a player. {@code Messages} is the only place
 * that names a translation key, {@code Palette} is the only place that names a colour, and
 * {@code VoyagerTranslator} is what turns the first into the second on the way out. A fitness rule
 * holds that boundary: no class in the rebuild outside this package hands Adventure a literal string
 * to show somebody.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.text;

import org.jetbrains.annotations.NotNullByDefault;
