package net.elytrarace.setup.conversation.map;

import net.elytrarace.setup.platform.BukkitConversationOwner;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class MapNamePrompt extends StringPrompt {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,64}$");

    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.map.name");
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null) {
            return this;
        }
        if (input.isEmpty()) {
            return this;
        }
        if (!NAME_PATTERN.matcher(input).matches()) {
            context.getForWhom().sendMessage(Component.translatable("error.map.name.invalid"));
            return this;
        }
        var plugin = ((BukkitConversationOwner) context.getOwner()).getPlugin();
        if (plugin == null) {
            return this;
        }
        if (plugin instanceof ElytraRace elytraRace) {
            var mapService = elytraRace.getMapService();
            var mapDTO = mapService.getMaps().stream()
                    .filter(cup -> cup.name().value().equalsIgnoreCase(input))
                    .findFirst();
            if (mapDTO.isPresent()) {
                context.getForWhom().sendMessage(Component.translatable("error.map.name.exists"));
                return this;
            }
        }
        var trimmedInput = input.trim();
        context.setSessionData("name", Key.key("map", trimmedInput));
        return new MapDisplayNamePrompt();
    }
}
