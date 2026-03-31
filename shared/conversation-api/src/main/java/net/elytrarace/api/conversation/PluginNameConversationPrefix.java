package net.elytrarace.api.conversation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

/**
 * PluginNameConversationPrefix is a {@link ConversationPrefix} implementation
 * that displays the owner name in front of conversation output.
 */
public class PluginNameConversationPrefix implements ConversationPrefix {

    protected String separator;
    protected TextColor prefixColor;
    protected ConversationOwner owner;

    private Component cachedPrefix;

    public PluginNameConversationPrefix(@NotNull ConversationOwner owner) {
        this(owner, " > ", NamedTextColor.LIGHT_PURPLE);
    }

    public PluginNameConversationPrefix(@NotNull ConversationOwner owner, @NotNull String separator, @NotNull TextColor prefixColor) {
        this.separator = separator;
        this.prefixColor = prefixColor;
        this.owner = owner;

        cachedPrefix = Component.text(owner.getName()).color(prefixColor).append(Component.text(separator)).color(NamedTextColor.WHITE);
    }

    @Override
    @NotNull
    public Component getPrefix(@NotNull ConversationContext context) {
        return cachedPrefix;
    }
}
