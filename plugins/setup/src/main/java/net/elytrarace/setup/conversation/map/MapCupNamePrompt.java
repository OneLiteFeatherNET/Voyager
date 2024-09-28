package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.api.conversation.StringPrompt;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MapCupNamePrompt extends StringPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.map.cup_name");
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
        if (input == null) {
            return this;
        }
        if (input.isEmpty()) {
            return this;
        }
        var plugin = context.getPlugin();
        if (plugin instanceof ElytraRace elytraRace) {
            var inputCupName = input.trim();
            var cupDTO = elytraRace.getCupService().getCups()
                    .stream()
                    .filter(cup -> cup.name().asString().equalsIgnoreCase(inputCupName))
                    .findFirst();
            if (cupDTO.isPresent()) {
                context.setSessionData("cup", cupDTO.get());
                return new MapNamePrompt();
            }
            context.getForWhom().sendMessage(Component.translatable("error.map.cup_name.not_found").arguments(Component.text(inputCupName)));
        }
        return this;
    }

    @Override
    public Collection<String> suggestions(@NotNull ConversationContext context) {
        var plugin = context.getPlugin();
        if (plugin instanceof ElytraRace elytraRace) {
            return elytraRace.getCupService().getCups()
                    .stream()
                    .map(CupDTO::name)
                    .map(Key::asString)
                    .toList();
        }
        return super.suggestions(context);
    }
}
