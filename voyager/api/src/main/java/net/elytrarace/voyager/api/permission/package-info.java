/**
 * The permission port: who asks, which node is asked about, and the answer. Pure types with no backend, so a command
 * depends on the question and never on LuckPerms or on the fallback that answers when LuckPerms is absent.
 */
@NotNullByDefault
package net.elytrarace.voyager.api.permission;

import org.jetbrains.annotations.NotNullByDefault;
