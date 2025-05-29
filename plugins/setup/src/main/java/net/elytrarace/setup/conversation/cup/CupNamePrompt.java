package net.elytrarace.setup.conversation.cup;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CupNamePrompt extends StringPrompt {

    private static final Predicate<String> NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$").asMatchPredicate();

    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.cup.name");
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null || input.isEmpty()) {
            return this;
        }
        if (!NAME_PATTERN.test(input)) {
            context.getForWhom().sendMessage(Component.translatable("error.cup.name.invalid"));
            return this;
        }
        var plugin = context.getPlugin();
        if (plugin == null) {
            return this;
        }
        if (plugin instanceof ElytraRace elytraRace) {
            var cupService = elytraRace.getCupService();
            var optionalCupDTO = cupService.getCups().stream()
                    .filter(cup -> cup.name().value().equalsIgnoreCase(input))
                    .findFirst();
            if (optionalCupDTO.isPresent()) {
                context.getForWhom().sendMessage(Component.translatable("error.cup.name.exists"));
                return this;
            }
        }

        context.setSessionData("name", Key.key("cup", input.toLowerCase(Locale.ROOT)));
        return new CupDisplayNamePrompt();
    }
}
