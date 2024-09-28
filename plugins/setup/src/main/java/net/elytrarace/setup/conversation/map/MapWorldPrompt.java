package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.Conversable;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.model.SetupHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.generator.WorldInfo;
import org.bukkit.metadata.FixedMetadataValue;
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
        Conversable forWhom = context.getForWhom();
        if (world == null) {
            forWhom.sendMessage(Component.translatable("error.map.world.not_found"));
            return this;
        }
        if (world.hasMetadata(ElytraRace.WORLD_SETUP.asString())) {
            forWhom.sendMessage(Component.translatable("error.map.world.in_use"));
            return this;
        }
        world.setMetadata(ElytraRace.WORLD_SETUP.asString(), new FixedMetadataValue(plugin, true));
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
