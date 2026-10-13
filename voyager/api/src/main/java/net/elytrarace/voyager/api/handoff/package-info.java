/**
 * The hub handoff port: the one move the server asks of the network, and the slot the CloudNet bridge fills when it starts
 * (ADR-0033). The server names only {@link net.elytrarace.voyager.api.handoff.PlayerHandoff} and the holder, never a
 * CloudNet type; the bridge names this package and nothing else of Voyager (ADR-0026, amended 2026-10-13).
 */
@NotNullByDefault
package net.elytrarace.voyager.api.handoff;

import org.jetbrains.annotations.NotNullByDefault;
