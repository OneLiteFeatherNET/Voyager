/**
 * Sends a finished cup's racers to the network hub through the handoff port of voyager-api, and disconnects them with a
 * translated message when no handoff runs or the handoff refuses (ADR-0033).
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.handoff;

import org.jetbrains.annotations.NotNullByDefault;
