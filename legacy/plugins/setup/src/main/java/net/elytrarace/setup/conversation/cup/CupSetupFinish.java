package net.elytrarace.setup.conversation.cup;

import net.elytrarace.setup.platform.BukkitConversationOwner;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.MessagePrompt;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.common.builder.CupDTOBuilder;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.key.Key;
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
        var name = (Key) context.getSessionData("name");
        var displayname = (Component) context.getSessionData("displayname");
        if (name == null || displayname == null) {
            return END_OF_CONVERSATION;
        }
        var plugin = ((BukkitConversationOwner) context.getOwner()).getPlugin();
        if (plugin instanceof ElytraRace elytraRace) {
            elytraRace.getCupService().addCup(CupDTOBuilder.create()
                    .name(name)
                    .displayName(displayname)
                    .build()
            ).thenCompose(success -> {
                if (success) {
                    context.getForWhom().sendActionBar(Component.translatable("setup.cup.added").arguments(displayname));
                    return elytraRace.getCupService().saveCups();
                } else {
                    context.getForWhom().sendActionBar(Component.translatable("setup.cup.failed").arguments(displayname));
                }
                return null;
            }).thenAccept(v -> {
                context.getForWhom().sendActionBar(Component.translatable("setup.cup.saved").arguments(displayname));
            });
        }
        return END_OF_CONVERSATION;
    }
}
