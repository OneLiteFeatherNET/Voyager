package net.elytrarace.setup.conversation.portal;

import net.elytrarace.api.conversation.BooleanPrompt;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PortalSetupNextPrompt extends BooleanPrompt {

    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.portal.setup.next");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, boolean input) {
        if (input) {
            context.setSessionData("continue", true);
            return new PortalPrompt();
        }
        context.getForWhom().sendMessage(Component.translatable("prompt.portal.setup.finish"));
        return END_OF_CONVERSATION;
    }
}
