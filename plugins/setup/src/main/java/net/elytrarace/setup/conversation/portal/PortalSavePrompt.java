package net.elytrarace.setup.conversation.portal;

import net.elytrarace.setup.platform.BukkitConversationOwner;

import com.fastasyncworldedit.core.regions.PolyhedralRegion;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.MessagePrompt;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.builder.PortalDTOBuilder;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.setup.ElytraRace;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

public class PortalSavePrompt extends MessagePrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.portal.save");
    }

    @Override
    protected @Nullable Prompt getNextPrompt(@NotNull ConversationContext context) {
        var map = (FileMapDTO) context.getSessionData("map");
        var index = (Integer) context.getSessionData("index");
        var region = (PolyhedralRegion) context.getSessionData("region");
        if (map == null || index == null || region == null) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.save.no-data"));
            return END_OF_CONVERSATION;
        }
        List<LocationDTO> corners = region.getVertices().stream().map(vertx -> new LocationDTO(vertx.x(), vertx.y(), vertx.z(), false)).toList();
        var locations = new ArrayList<>(corners);
        Optional.ofNullable(region.getCenter()).map(center -> new LocationDTO(center.blockX(), center.blockY(), center.blockZ(), true)).ifPresent(locations::add);
        FilePortalDTO portalDTO = PortalDTOBuilder.create().index(index).locations(locations).build();
        var portals = new TreeSet<>(map.portals());
        portals.add(portalDTO);
        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();
        var plugin = ((BukkitConversationOwner) context.getOwner()).getPlugin();
        if (!(plugin instanceof ElytraRace elytraRace)) {
            return END_OF_CONVERSATION;
        }
        elytraRace.getMapService().updateMap(newMap)
                .thenCompose(success -> {
            if (success) {
                context.getForWhom().sendActionBar(Component.translatable("success.portal.save").arguments(newMap.displayName(), Component.text(index)));
                return elytraRace.getMapService().saveMaps();
            }
            context.getForWhom().sendMessage(Component.translatable("error.portal.save").arguments(newMap.displayName(), Component.text(index)));
            return null;
        });
        return new PortalSetupNextPrompt();
    }
}
