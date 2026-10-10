package net.elytrarace.setup.conversation.portal;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.MessagePrompt;
import net.elytrarace.api.conversation.Prompt;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PortalInformationFawePrompt extends MessagePrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        if (context.getAllSessionData().containsKey("continue")) {
            return Component.translatable("prompt.portal.fawe.continue");
        }
        return Component.translatable("prompt.portal.fawe");
    }

    @Override
    protected @Nullable Prompt getNextPrompt(@NotNull ConversationContext context) {
        return new PortalSelectedFinish();
    }
}
