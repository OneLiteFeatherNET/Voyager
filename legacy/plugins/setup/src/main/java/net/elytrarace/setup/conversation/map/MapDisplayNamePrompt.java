package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MapDisplayNamePrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        String name = Optional.ofNullable((Key) context.getSessionData("name")).map(Key::value).orElse("No name");
        return Component.translatable("prompt.map.displayname").arguments(Component.text(name));
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
        context.setSessionData("displayName", MiniMessage.miniMessage().deserialize(trimmedInput));
        return new MapAuthorPrompt();
    }
}
