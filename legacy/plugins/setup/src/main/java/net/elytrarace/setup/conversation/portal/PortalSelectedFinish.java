package net.elytrarace.setup.conversation.portal;

import com.fastasyncworldedit.core.regions.selector.PolyhedralRegionSelector;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.RegionSelector;
import net.elytrarace.api.conversation.BooleanPrompt;
import net.elytrarace.api.conversation.ConversationContext;
import net.elytrarace.api.conversation.Prompt;
import net.elytrarace.setup.model.SetupHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PortalSelectedFinish extends BooleanPrompt {
    @Override
    public @NotNull Component getPromptText(@NotNull ConversationContext context) {
        return Component.translatable("prompt.portal.selected.finish");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, boolean input) {
        var forWhom = context.getForWhom();
        if (!(forWhom instanceof SetupHolder setupHolder)) {
            return END_OF_CONVERSATION;
        }
        Player player = setupHolder.getPlayer();
        var actor = BukkitAdapter.adapt(player);
        var localSession = actor.getSession();
        var world = actor.getWorld();
        RegionSelector regionSelector = localSession.getRegionSelector(world);
        if (!input) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.selected.finish.false"));
            regionSelector.clear();
            return this;
        }
        if (!(regionSelector instanceof PolyhedralRegionSelector polyhedralRegionSelector)) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.selected.finish.invalid-region.type"));
            localSession.setRegionSelector(world, new PolyhedralRegionSelector(world));
            return this;
        }
        if (polyhedralRegionSelector.getVertices().size() <= 3) {
            context.getForWhom().sendMessage(Component.translatable("error.portal.selected.finish.invalid-region"));
            return this;
        }
        context.setSessionData("region", polyhedralRegionSelector.getRegion());
        return new PortalIndex();
    }
}
