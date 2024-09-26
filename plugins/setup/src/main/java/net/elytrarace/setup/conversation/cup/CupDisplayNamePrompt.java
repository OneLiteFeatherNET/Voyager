package net.elytrarace.setup.conversation.cup;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CupDisplayNamePrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        String name = Optional.ofNullable((String) context.getSessionData("cupName")).orElse("No name");
        return Component.translatable("prompt.cup.displayname").arguments(Component.text(name));
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null || input.isEmpty()) {
            return this;
        }
        context.setSessionData("displayname", input);
        return new CupSetupFinish();
    }
}
