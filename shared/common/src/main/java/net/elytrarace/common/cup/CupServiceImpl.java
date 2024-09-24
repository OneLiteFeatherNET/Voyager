package net.elytrarace.common.cup;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.utils.GsonUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

class CupServiceImpl implements CupService {

    private final CupProvider cupProvider;

    CupServiceImpl(@NotNull JavaPlugin plugin) {
        this.cupProvider = new CupProvider(GsonUtil.GSON, plugin.getDataPath(), ArrayList::new);
    }

    @Override
    public CompletableFuture<CupDTO> getRandomCup() {
        return CompletableFuture.supplyAsync(() -> {
            var cups = this.cupProvider.getCupsAsList();
            var newCups = new ArrayList<FileCupDTO>();
            Collections.copy(cups, newCups);
            Collections.shuffle(newCups);
            return newCups.get(0);
        });
    }

    @Override
    public CompletableFuture<CupDTO> getCupByName(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> this.cupProvider.getCups()
                .stream()
                .filter(cup -> cup.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The cup with the name " + name + " does not exist")));
    }
}
