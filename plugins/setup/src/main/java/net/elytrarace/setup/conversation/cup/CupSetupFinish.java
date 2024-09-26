package net.elytrarace.setup.conversation.cup;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.MessagePrompt;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.common.builder.CupDTOBuilder;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CupSetupFinish extends MessagePrompt {

    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.cup.finish");
    }

    @Override
    protected @Nullable Prompt getNextPrompt(@NotNull ConversationContext context) {
        var name = (String) context.getSessionData("name");
        var displayname = (String) context.getSessionData("displayname");
        if (name == null || displayname == null) {
            return END_OF_CONVERSATION;
        }
        Plugin plugin = context.getPlugin();
        if (plugin instanceof ElytraRace elytraRace) {
            elytraRace.getCupService().addCup(CupDTOBuilder.create()
                    .name(name)
                    .displayName(displayname)
                    .build()
            ).thenCompose(success -> {
                if (success) {
                    context.getForWhom().sendActionBar(Component.translatable("setup.cup.added").arguments(MiniMessage.miniMessage().deserialize(displayname)));
                    return elytraRace.getCupService().saveCups();
                } else {
                    context.getForWhom().sendActionBar(Component.translatable("setup.cup.failed"));
                }
                return null;
            }).thenAccept(v -> {
                context.getForWhom().sendActionBar(Component.translatable("setup.cup.saved"));
            });
        }
        return END_OF_CONVERSATION;
    }
}
