package net.elytrarace.setup.conversation.portal;

import net.elytrarace.setup.platform.BukkitConversationOwner;

import net.elytrarace.api.conversation.BooleanPrompt;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.model.SetupHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class PortalPrompt extends BooleanPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.portal");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, boolean input) {
        if (!input) {
            return END_OF_CONVERSATION;
        }
        var who = context.getForWhom();
        if (!(who instanceof SetupHolder)) {
            return END_OF_CONVERSATION;
        }
        var plugin = ((BukkitConversationOwner) context.getOwner()).getPlugin();
        if (plugin == null) {
            who.sendMessage(Component.translatable("error.plugin"));
            return END_OF_CONVERSATION;
        }

        World world = ((SetupHolder) who).getPlayer().getWorld();
        if (!world.getPersistentDataContainer().has(ElytraRace.WORLD_SETUP)) {
            who.sendMessage(Component.translatable("error.world.not_setup").arguments(Component.text(world.getName())));
            return END_OF_CONVERSATION;
        }
        if (!(plugin instanceof ElytraRace elytraRace)){
            return END_OF_CONVERSATION;
        }
        Optional<MapDTO> mapDTOOptional = elytraRace.getMapService().getMaps().stream().filter(mapDTO -> mapDTO.world().equalsIgnoreCase(world.getName())).findFirst();
        if (mapDTOOptional.isEmpty()) {
            who.sendMessage(Component.translatable("error.map.not_found").arguments(Component.text(world.getName())));
            return END_OF_CONVERSATION;
        }
        mapDTOOptional.ifPresent(mapDTO -> context.setSessionData("map", mapDTO));
        return new PortalInformationFawePrompt();

    }
}
