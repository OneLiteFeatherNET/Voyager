package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class MapAuthorPrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.map.author");
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null) {
            return this;
        }
        if (input.isEmpty()) {
            return this;
        }
        var trimmedInput = input.trim();
        var authorList = Stream.of(trimmedInput.split(","))
                .map(String::trim)
                .map(MiniMessage.miniMessage()::deserialize).toList();
        context.setSessionData("author", Component.join(JoinConfiguration.commas(false), authorList));
        return new MapWorldPrompt();
    }
}
