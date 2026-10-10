/**
 * The per-tick loop: one driver advancing every flying player's simulated flight, one driver
 * advancing the race's phase through Xerus.
 *
 * <p>Ticking, timers and delays live here and nowhere else. {@code voyager-race} owns which
 * transitions exist and knows nothing about when they happen — that split is what keeps a whole cup
 * playable inside a JUnit run, and it is why Xerus, which is Minestom-bound, may not appear in
 * {@code voyager-race}.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.tick;

import org.jetbrains.annotations.NotNullByDefault;
