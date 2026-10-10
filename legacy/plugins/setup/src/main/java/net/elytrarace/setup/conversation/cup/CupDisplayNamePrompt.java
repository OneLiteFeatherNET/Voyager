package net.elytrarace.setup.conversation.cup;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CupDisplayNamePrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        String name = Optional.ofNullable((Key) context.getSessionData("name")).map(Key::value).orElse("No name");
        return Component.translatable("prompt.cup.displayname").arguments(Component.text(name));
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null || input.isEmpty()) {
            return this;
        }
        var trimmedInput = input.trim();
        context.setSessionData("displayname", MiniMessage.miniMessage().deserialize(trimmedInput));
        return new CupSetupFinish();
    }
}
