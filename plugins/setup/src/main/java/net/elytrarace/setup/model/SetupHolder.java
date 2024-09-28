package net.elytrarace.setup.model;

import net.elytrarace.api.conversation.*;
import net.elytrarace.setup.utils.SetupMode;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class SetupHolder implements Conversable {

    private final ConversationTracker conversationTracker = new ConversationTracker();
    private final UUID uuid;
    private SetupMode setupMode;

    public SetupHolder(UUID uuid) {
        this.uuid = uuid;
    }

    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    @Override
    public void sendMessage(final @NotNull Identity source, final @NotNull Component message, final @NotNull MessageType type) {
        Optional.ofNullable(Bukkit.getPlayer(uuid))
                .ifPresent(player -> player.sendMessage(source, message, type));
    }

    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    @Override
    public void sendMessage(final @NotNull Identified source, final @NotNull Component message, final @NotNull MessageType type) {
        Optional.ofNullable(Bukkit.getPlayer(uuid))
                .ifPresent(player -> player.sendMessage(source, message, type));
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        Optional.ofNullable(Bukkit.getPlayer(uuid))
                .ifPresent(player -> player.sendActionBar(message));
    }

    @Override
    public boolean isConversing() {
        return this.conversationTracker.isConversing();
    }

    @Override
    public void acceptConversationInput(@NotNull String input) {
        this.conversationTracker.acceptConversationInput(input);
    }

    @Override
    public boolean beginConversation(@NotNull Conversation conversation) {
        return this.conversationTracker.beginConversation(conversation);
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation) {
        this.conversationTracker.abandonConversation(conversation, new ConversationAbandonedEvent(conversation, new ManuallyAbandonedConversationCanceller()));
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation, @NotNull ConversationAbandonedEvent details) {
        this.conversationTracker.abandonConversation(conversation, details);
    }

    @Override
    public void setCustomSuggestionToPlayer(Collection<String> completions) {
        Optional.ofNullable(Bukkit.getPlayer(uuid))
                .ifPresent(player -> player.setCustomChatCompletions(completions));
    }

    public ConversationTracker getConversationTracker() {
        return conversationTracker;
    }

    public void setSetupMode(SetupMode setupMode) {
        this.setupMode = setupMode;
    }

    public SetupMode getSetupMode() {
        return setupMode;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public UUID getUuid() {
        return uuid;
    }
}
