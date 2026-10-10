package net.elytrarace.api.conversation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlayerNamePrompt is the base class for any prompt that requires the player
 * to enter another player's name. Platform-specific implementations must
 * provide player lookup logic.
 */
public abstract class PlayerNamePrompt extends ValidatingPrompt {

    @Override
    protected abstract boolean isInputValid(@NotNull ConversationContext context, @NotNull String input);

    @Nullable
    @Override
    protected abstract Prompt acceptValidatedInput(@NotNull ConversationContext context, @NotNull String input);
}
