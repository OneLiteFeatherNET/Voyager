package net.elytrarace.common.cup;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.file.FileHandler;
import net.elytrarace.common.file.GsonFileHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class CupProvider {

    private static final Logger CUP_LOGGER = LoggerFactory.getLogger(CupProvider.class);
    private static final String CUPS_FOLDER = System.getProperty("VOYAGER_CUPS_FOLDER", "cups");
    private static final String CUPS_FILE = "cups.json";

    private final FileHandler fileHandler;
    private final Path cupPath;
    private final Supplier<List<FileCupDTO>> defaultCups;
    private List<FileCupDTO> cups;

    CupProvider(@NotNull Gson gson, @NotNull Path voyagerPath, @NotNull Supplier<List<FileCupDTO>> defaultCups) {
        this.fileHandler = new GsonFileHandler(gson);
        this.cupPath = voyagerPath.resolve(CUPS_FOLDER);
        this.defaultCups = defaultCups;

        if (!Files.exists(this.cupPath)) {
            throw new IllegalStateException("The cup folder does not exist. Please name the cup folder " + CUPS_FOLDER);
        }
        loadCups();
    }

    public void loadCups() {
        CUP_LOGGER.info("Starting to load cups of the game");
        final Path mapFile = this.cupPath.resolve(CUPS_FILE);
        if (!Files.exists(mapFile)) {
            CUP_LOGGER.error("The cups file does not exist");
            CUP_LOGGER.info("Creating a new cups file");
            this.cups = this.defaultCups.get();
            return;
        }

        final Optional<List<FileCupDTO>> optionalMap = this.fileHandler.load(mapFile, (TypeToken<List<FileCupDTO>>)
                TypeToken.getParameterized(List.class, FileCupDTO.class));

        if (optionalMap.isEmpty()) {
            throw new IllegalStateException("The cups could not be loaded");
        }

        this.cups = optionalMap.orElse(this.defaultCups.get());
    }


    public void saveCups() {
        System.out.println("Test");
        this.fileHandler.save(this.cupPath.resolve(CUPS_FILE), cups, (TypeToken<List<FileCupDTO>>) TypeToken.getParameterized(List.class, FileCupDTO.class));
    }


    public void addCup(@NotNull FileCupDTO cup) {
        this.cups.add(cup);
    }

    public @NotNull Collection<FileCupDTO> getCups() {
        return this.cups;
    }

    public @NotNull List<FileCupDTO> getCupsAsList() {
        return Collections.unmodifiableList(Lists.newArrayList(this.cups));
    }

    public @NotNull Path getCupPath() {
        return this.cupPath;
    }



}
