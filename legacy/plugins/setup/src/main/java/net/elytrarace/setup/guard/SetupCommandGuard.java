package net.elytrarace.setup.guard;

import net.elytrarace.setup.session.SetupSessionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.incendo.cloud.execution.postprocessor.CommandPostprocessingContext;
import org.incendo.cloud.execution.postprocessor.CommandPostprocessor;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.services.type.ConsumerService;

import java.util.Set;

/**
 * Cloud command postprocessor that verifies the executing player has an active
 * {@link net.elytrarace.setup.session.SetupSession} before allowing the command
 * to proceed.
 * <p>
 * Commands whose first literal (after "elytrarace") is in the {@link #ALLOWED_WITHOUT_SESSION}
 * set are exempt from this check. This includes "help", "setup", and "cancel" because
 * those commands must work without an active session.
 * <p>
 * <strong>Note:</strong> This guard is an additional safety layer. The existing
 * {@code SetupGuard.getSetupHolder(player)} checks in individual command handlers
 * are intentionally kept in place for now.
 */
public final class SetupCommandGuard implements CommandPostprocessor<Source> {

    /**
     * Literals (second token after "elytrarace") that do not require an active session.
     */
    private static final Set<String> ALLOWED_WITHOUT_SESSION = Set.of(
            "help",
            "setup",
            "cancel"
    );

    private final SetupSessionManager sessionManager;

    public SetupCommandGuard(SetupSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void accept(CommandPostprocessingContext<Source> context) {
        var sender = context.commandContext().sender();

        // Only restrict player commands — console can always execute
        if (!(sender instanceof PlayerSource playerSource)) {
            return;
        }

        // Check whether the command is in the allowlist
        if (isAllowedWithoutSession(context)) {
            return;
        }

        // Verify the player has an active session
        Player player = playerSource.source();
        if (sessionManager.get(player.getUniqueId()).isPresent()) {
            return;
        }

        // No session — reject the command
        player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
        ConsumerService.interrupt();
    }

    /**
     * Checks whether the command being executed is exempt from the session requirement.
     * <p>
     * Inspects the command's component tree to find the first literal after the root
     * "elytrarace" node. If there is no second component (bare "/elytrarace"), it maps
     * to the help handler and is allowed.
     */
    private boolean isAllowedWithoutSession(CommandPostprocessingContext<Source> context) {
        var command = context.command();
        var components = command.components();

        // components[0] is "elytrarace" (root). If there is only the root, it is the bare
        // /elytrarace handler which shows help.
        if (components.size() <= 1) {
            return true;
        }

        // components[1] is the first literal after "elytrarace"
        var firstLiteral = components.get(1).name();
        return ALLOWED_WITHOUT_SESSION.contains(firstLiteral);
    }
}
