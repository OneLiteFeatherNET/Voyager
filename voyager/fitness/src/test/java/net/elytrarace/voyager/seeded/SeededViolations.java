package net.elytrarace.voyager.seeded;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.command.CommandSender;

/**
 * Deliberate violations of the message-boundary rules, for {@code ApiPurityRulesSeededTest} to prove the rules are not
 * vacuous. This package is imported by that test alone: the architecture run excludes test classes.
 */
@SuppressWarnings("unused")
final class SeededViolations {

    private SeededViolations() {
    }

    static Component translationKeyNamedOutsideTheMessageLayer() {
        return Component.translatable("voyager.seeded.key");
    }

    static Component miniMessageSourceWrittenAtACallSite() {
        return MiniMessage.miniMessage().deserialize("<red>seeded");
    }

    static void stringSentToACommandSender(CommandSender sender) {
        sender.sendMessage("seeded");
    }
}
