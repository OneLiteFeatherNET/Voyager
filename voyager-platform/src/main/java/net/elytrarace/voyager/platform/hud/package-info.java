/**
 * What a racer sees and hears while a race is running: the flight HUD, the boss bar, the start
 * countdown and the sounds that acknowledge a ring.
 *
 * <p>The race computes a value and this package renders it. Nothing here knows how a race works —
 * {@code RaceHud} is handed a {@code HudState} and turns it into an action bar and a boss bar, and
 * {@code RaceFeedback} is the one file that names a sound or a title. When the game feels wrong,
 * these are the two files to open.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.hud;

import org.jetbrains.annotations.NotNullByDefault;
