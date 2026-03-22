package net.elytrarace.common.cup;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.utils.GsonUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class CupServiceImpl implements CupService {

    private final CupProvider cupProvider;

    CupServiceImpl(@NotNull JavaPlugin plugin) {
        this.cupProvider = new CupProvider(GsonUtil.GSON, plugin.getDataPath(), ArrayList::new);
    }

    @Override
    public CompletableFuture<CupDTO> getRandomCup() {
        return CompletableFuture.supplyAsync(this.cupProvider::getCupsAsList)
                .thenApplyAsync(ArrayList<FileCupDTO>::new)
                .thenApplyAsync(cups -> {
                    Collections.shuffle(cups);
                    if (cups.isEmpty()) {
                        throw new IllegalStateException("There are no cups available");
                    }
                    return cups;
                })
                .thenApplyAsync(List::getFirst);
    }

    @Override
    public CompletableFuture<CupDTO> getCupByName(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> this.cupProvider.getCups()
                .stream()
                .filter(cup -> cup.name().asString().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The cup with the name " + name + " does not exist")));
    }

    @Override
    public CompletableFuture<Boolean> addCup(@NotNull CupDTO cupDTO) {
        return CompletableFuture.supplyAsync(() -> {
            if (this.cupProvider.getCups().stream().anyMatch(cup -> cup.name().equals(cupDTO.name()))) {
                return false;
            }
            if (cupDTO instanceof FileCupDTO fileCupDTO) {
                this.cupProvider.addCup(fileCupDTO);
                return true;
            }
            if (cupDTO instanceof ResolvedCupDTO resolvedCupDTO) {
                this.cupProvider.addCup(new FileCupDTO(resolvedCupDTO.name(), resolvedCupDTO.displayName(), resolvedCupDTO.maps().stream().map(FileMapDTO.class::cast).map(FileMapDTO::uuid).toList()));
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> removeCup(@NotNull CupDTO cupDTO) {
        return CompletableFuture.supplyAsync(() -> {
            if (cupDTO instanceof FileCupDTO fileCupDTO) {
                this.cupProvider.removeCup(fileCupDTO);
                return true;
            }
            if (cupDTO instanceof ResolvedCupDTO resolvedCupDTO) {
                this.cupProvider.removeCup(new FileCupDTO(resolvedCupDTO.name(), resolvedCupDTO.displayName(), resolvedCupDTO.maps().stream().map(FileMapDTO.class::cast).map(FileMapDTO::uuid).toList()));
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> updateCup(@NotNull CupDTO cupDTO) {
        return CompletableFuture.supplyAsync(() -> {
            if (cupDTO instanceof FileCupDTO fileCupDTO) {
                this.cupProvider.removeCup(fileCupDTO);
                this.cupProvider.addCup(fileCupDTO);
                return true;
            }
            if (cupDTO instanceof ResolvedCupDTO resolvedCupDTO) {
                this.cupProvider.removeCup(new FileCupDTO(resolvedCupDTO.name(), resolvedCupDTO.displayName(), resolvedCupDTO.maps().stream().map(FileMapDTO.class::cast).map(FileMapDTO::uuid).toList()));
                this.cupProvider.addCup(new FileCupDTO(resolvedCupDTO.name(), resolvedCupDTO.displayName(), resolvedCupDTO.maps().stream().map(FileMapDTO.class::cast).map(FileMapDTO::uuid).toList()));
                return true;
            }
            return false;
        });
    }

    @Override
    public List<FileCupDTO> getCups() {
        return this.cupProvider.getCupsAsList();
    }

    @Override
    public CompletableFuture<List<FileCupDTO>> getCupsAsync() {
        return CompletableFuture.supplyAsync(this::getCups);
    }

    @Override
    public CompletableFuture<Void> saveCups() {
        return CompletableFuture.runAsync(this.cupProvider::saveCups);
    }
}
