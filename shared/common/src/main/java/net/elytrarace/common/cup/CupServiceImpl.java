package net.elytrarace.common.cup;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.utils.GsonUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class CupServiceImpl implements CupService {

    private final CupProvider cupProvider;

    CupServiceImpl(@NotNull JavaPlugin plugin) {
        this.cupProvider = new CupProvider(GsonUtil.GSON, plugin.getDataPath(), () -> List.of(new FileCupDTO("default", "default", List.of(new UUID(
                -4276615277451065986L,
                -5133830059633988266L
        )))));
    }

    @Override
    public CompletableFuture<CupDTO> getRandomCup() {
        return CompletableFuture.supplyAsync(() -> {
            var cups = this.cupProvider.getCupsAsList();
            var newCups = new ArrayList<>(cups);
            Collections.shuffle(newCups);
            if (newCups.isEmpty()) {
                throw new IllegalStateException("There are no cups available");
            }
            return newCups.getFirst();
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
