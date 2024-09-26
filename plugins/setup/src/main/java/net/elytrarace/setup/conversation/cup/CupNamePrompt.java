package net.elytrarace.setup.conversation.cup;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CupNamePrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.cup.name");
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null || input.isEmpty()) {
            return this;
        }

        context.setSessionData("name", input);
        return new CupDisplayNamePrompt();
    }
}
