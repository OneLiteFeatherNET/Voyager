package net.elytrarace.setup.conversation.portal;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.NumericPrompt;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.common.map.model.FileMapDTO;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PortalIndex extends NumericPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.portal.index");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, @NotNull Number input) {
        int index = input.intValue();
        if (index <= 0) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.index.negative"));
            return this;
        }
        var map = (FileMapDTO) context.getSessionData("map");
        if (map == null) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.index.no-map"));
            return END_OF_CONVERSATION;
        }
        if (index > 1 && map.portals().isEmpty()) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.index.first"));
            return this;
        }
        var alreadyExists = map.portals().stream().anyMatch(portal -> portal.index() == index);
        if (alreadyExists) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.index.exists").arguments(map.displayName(), Component.text(index)));
            return this;
        }
        context.setSessionData("index", index);
        return new PortalSavePrompt();
    }
}
