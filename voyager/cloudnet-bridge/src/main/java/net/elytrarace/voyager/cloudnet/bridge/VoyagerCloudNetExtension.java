package net.elytrarace.voyager.cloudnet.bridge;

import eu.cloudnetservice.driver.registry.ServiceRegistry;
import eu.cloudnetservice.modules.bridge.impl.platform.minestom.MinestomPermissionChecker;
import eu.cloudnetservice.modules.bridge.player.PlayerManager;
import net.elytrarace.voyager.api.handoff.PlayerHandoffs;
import net.minestom.server.extensions.Extension;
import net.onelitefeather.minestom.extensions.processor.ExtensionInfo;

import java.util.UUID;

/**
 * Teaches CloudNet's bridge how Voyager resolves permissions (ADR-0026).
 *
 * <p>The bridge ships a default checker that reads the operator level, which is always zero here, so its maintenance bypass
 * and task checks would refuse every player. This extension registers {@link CloudNetPermissionChecker} as the default
 * checker instead. {@code CloudNet_Bridge} is declared as a dependency, so the loader starts this extension after the bridge
 * and in its class loader hierarchy. If the bridge is missing, the loader skips this extension with a log line, and the
 * server still starts.
 *
 * <p>This is the only class of the rebuild that calls CloudNet's {@link ServiceRegistry}.
 */
@ExtensionInfo(
        name = "VoyagerCloudNetPermissions",
        authors = "OneLiteFeather",
        dependencies = "CloudNet_Bridge"
)
public final class VoyagerCloudNetExtension extends Extension {

    /** The name the checker is registered under. The bridge keeps one default; the name identifies this one in its registry. */
    static final String REGISTRATION_NAME = "voyager-permissions";

    @Override
    public void initialize() {
        MinestomPermissionChecker checker = CloudNetPermissionChecker::allows;
        ServiceRegistry.registry()
                .registerProvider(MinestomPermissionChecker.class, REGISTRATION_NAME, checker)
                .markAsDefaultService();
        // The hub handoff the server asks for when a cup's results have lingered (ADR-0033). Installed here, so a node with
        // this extension sends racers to the hub and a node without it disconnects them.
        PlayerHandoffs.SLOT.install(new CloudNetPlayerHandoff(VoyagerCloudNetExtension::connectToFallback));
    }

    @Override
    public void terminate() {
    }

    /**
     * CloudNet's move of one player to the best matching fallback (bridge-api RC17). The one CloudNet call of the hub handoff.
     *
     * @param playerId the racer to connect
     */
    static void connectToFallback(UUID playerId) {
        ServiceRegistry.registry().defaultInstance(PlayerManager.class).playerExecutor(playerId).connectToFallback();
    }
}
