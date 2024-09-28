package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.BooleanPrompt;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MapPrompt extends BooleanPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.map");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, boolean input) {
        if (!input) {
            return END_OF_CONVERSATION;
        }
        return new MapCupNamePrompt();
    }
}
