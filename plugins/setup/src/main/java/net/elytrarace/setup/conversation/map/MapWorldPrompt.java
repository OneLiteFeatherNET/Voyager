package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.kyori.adventure.text.Component;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MapWorldPrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.map.world");
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null) {
            return this;
        }
        if (input.isEmpty()) {
            return this;
        }
        var plugin = context.getPlugin();
        if (plugin == null) {
            return this;
        }
        var world = context.getPlugin().getServer().getWorld(input);
        if (world == null) {
            context.getForWhom().sendMessage(Component.translatable("error.map.world.not_found"));
            return this;
        }
        context.setSessionData("world", world);
        return new MapSetupFinish();
    }

    @Override
    public Collection<String> suggestions(@NotNull ConversationContext context) {
        var plugin = context.getPlugin();
        if (plugin == null) {
            return super.suggestions(context);
        }
        var server = plugin.getServer();
        return server.getWorlds().stream().map(WorldInfo::getName).toList();
    }
}
