package net.elytrarace.setup.conversation.map;

import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.MessagePrompt;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class MapSetupFinish extends MessagePrompt {

    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.map.finish");
    }

    @Override
    protected @Nullable Prompt getNextPrompt(@NotNull ConversationContext context) {
        var plugin = context.getPlugin();
        if (plugin == null) {
            return END_OF_CONVERSATION;
        }
        if (plugin instanceof ElytraRace elytraRace) {
            var cup = (FileCupDTO) context.getSessionData("cup");
            var name = (Key) context.getSessionData("name");
            var displayName = (Component) context.getSessionData("displayName");
            var author = (Component) context.getSessionData("author");
            var world = (World) context.getSessionData("world");
            if (cup == null || name == null || displayName == null || author == null || world == null) {
                context.getForWhom().sendMessage(Component.translatable("error.map.finish.incomplete"));
                return END_OF_CONVERSATION;
            }

            var mapService = elytraRace.getMapService();
            var cupService = elytraRace.getCupService();

            MapDTO mapDTO = MapDTOBuilder.create().name(name)
                    .displayName(displayName)
                    .author(author)
                    .world(world.getName())
                    .generateUUID()
                    .build();
            var updatedCup = new FileCupDTO(cup.name(), cup.displayName(), new ArrayList<>(cup.maps()));
            updatedCup.maps().add(mapDTO.uuid());
            mapService.addMap(mapDTO)
                    .thenCompose(success -> {
                        if (success) {
                            context.getForWhom().sendActionBar(Component.translatable("setup.map.added").arguments(displayName));
                            return mapService.saveMaps();
                        } else {
                            world.removeMetadata(ElytraRace.WORLD_SETUP.asString(), plugin);
                            context.getForWhom().sendActionBar(Component.translatable("setup.map.failed").arguments(displayName));
                        }
                        return null;
                    })
                    .thenCompose(v -> cupService.updateCup(updatedCup))
                    .thenCompose(success -> {
                        if (success) {
                            context.getForWhom().sendActionBar(Component.translatable("setup.cup.updated").arguments(cup.displayName()));
                            return cupService.saveCups();
                        } else {
                            world.removeMetadata(ElytraRace.WORLD_SETUP.asString(), plugin);
                            context.getForWhom().sendActionBar(Component.translatable("setup.cup.failed").arguments(cup.displayName()));
                        }
                        return null;
                    })
                    .thenAccept(v -> {
                        world.removeMetadata(ElytraRace.WORLD_SETUP.asString(), plugin);
                        context.getForWhom().sendActionBar(Component.translatable("setup.map.saved").arguments(displayName));
                    });
        }
        return END_OF_CONVERSATION;
    }
}
