package net.elytrarace.setup.conversation.cup;

import net.elytrarace.api.conversation.BooleanPrompt;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CupPrompt extends BooleanPrompt {

    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.cup");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, boolean input) {
        if (input) {
            return new CupNamePrompt();
        }
        return END_OF_CONVERSATION;
    }
}
