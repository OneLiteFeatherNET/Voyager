package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftLocationConflictException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftNotFoundException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.platform.catalog.writer.MapDraftJsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonDraftStoreTest {

    private static final MapId ID = new MapId("skyfortress");

    @TempDir
    Path root;

    private Path data;
    private Path worlds;

    private Path maps() {
        return data.resolve("maps");
    }

    private Path drafts() {
        return data.resolve("drafts");
    }

    private void folders() throws IOException {
        data = Files.createDirectories(root.resolve("data"));
        worlds = Files.createDirectories(root.resolve("worlds"));
    }

    private DraftStore store() {
        return new JsonDraftStore(data, worlds);
    }

    @Test
    void createWritesTheSkeletonToTheDraftsFolderAndNotToMaps() throws IOException {
        folders();

        store().create(DraftFixtures.skeleton(ID));

        assertThat(drafts().resolve("skyfortress.json")).exists();
        assertThat(maps().resolve("skyfortress.json")).doesNotExist();
    }

    @Test
    void createRefusesAnIdThatAlreadyHasAMapsFile() throws IOException {
        folders();
        write(maps().resolve("skyfortress.json"), DraftFixtures.complete(ID));

        assertThatThrownBy(() -> store().create(DraftFixtures.skeleton(ID)))
                .isInstanceOf(DraftAlreadyExistsException.class);
    }

    @Test
    void createRefusesAnIdThatAlreadyHasADraftsFile() throws IOException {
        folders();
        write(drafts().resolve("skyfortress.json"), DraftFixtures.skeleton(ID));

        assertThatThrownBy(() -> store().create(DraftFixtures.skeleton(ID)))
                .isInstanceOf(DraftAlreadyExistsException.class);
    }

    @Test
    void createRefusesAnIdThatAlreadyHasAWorldFolder() throws IOException {
        folders();
        Files.createDirectories(worlds.resolve("skyfortress"));

        assertThatThrownBy(() -> store().create(DraftFixtures.skeleton(ID)))
                .isInstanceOf(DraftAlreadyExistsException.class);
    }

    @Test
    void saveOfAGameLoadableDraftWritesTheMapsFileAndRemovesTheDraftsCopy() throws IOException {
        folders();
        DraftStore store = store();
        store.create(DraftFixtures.skeleton(ID));

        store.save(DraftFixtures.complete(ID));

        assertThat(maps().resolve("skyfortress.json")).exists();
        assertThat(drafts().resolve("skyfortress.json")).doesNotExist();
    }

    @Test
    void aDraftWithRingsButNoSpawnStaysInTheDraftsFolder() throws IOException {
        folders();
        MapDraft complete = DraftFixtures.complete(ID);
        MapDraft withoutSpawn = new MapDraft(ID, complete.world(), null, complete.rings(),
                complete.referenceTimeSeconds(), complete.boostConfig(), complete.guideLine());

        store().save(withoutSpawn);

        assertThat(drafts().resolve("skyfortress.json")).exists();
        assertThat(maps().resolve("skyfortress.json")).doesNotExist();
    }

    @Test
    void removingTheLastRingMovesTheFileBackToTheDraftsFolder() throws IOException {
        folders();
        DraftStore store = store();
        store.save(DraftFixtures.complete(ID));
        MapDraft noRings = new MapDraft(ID, ID.value(), new Vec3(109, -62, 54), List.of(), 60.0,
                new BoostConfig(30, 40), new GuideLine(List.of(), 2, 1.0));

        store.save(noRings);

        assertThat(drafts().resolve("skyfortress.json")).exists();
        assertThat(maps().resolve("skyfortress.json")).doesNotExist();
    }

    @Test
    void aSaveLeavesNoTemporaryFileBehind() throws IOException {
        folders();

        store().save(DraftFixtures.complete(ID));

        try (Stream<Path> files = Files.list(maps())) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactly("skyfortress.json");
        }
    }

    @Test
    void aFailedMoveKeepsThePreviousBytesAndRemovesItsOwnTemporaryFile() throws IOException {
        folders();
        store().save(DraftFixtures.complete(ID));
        byte[] before = Files.readAllBytes(maps().resolve("skyfortress.json"));
        MapDraft changed = new MapDraft(ID, ID.value(), new Vec3(1, 2, 3), DraftFixtures.complete(ID).rings(),
                61.0, new BoostConfig(30, 40), new GuideLine(List.of(), 2, 1.0));
        DraftStore failing = new JsonDraftStore(data, worlds, (source, target, options) -> {
            throw new IOException("the disk is full");
        });

        assertThatThrownBy(() -> failing.save(changed)).isInstanceOf(DraftWriteFailedException.class);

        assertThat(Files.readAllBytes(maps().resolve("skyfortress.json"))).isEqualTo(before);
        try (Stream<Path> files = Files.list(maps())) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactly("skyfortress.json");
        }
    }

    @Test
    void loadRefusesATwoCopiesOfOneDraftAndNamesBothFiles() throws IOException {
        folders();
        write(maps().resolve("skyfortress.json"), DraftFixtures.complete(ID));
        write(drafts().resolve("skyfortress.json"), DraftFixtures.skeleton(ID));

        assertThatThrownBy(() -> store().load(ID))
                .isInstanceOf(DraftLocationConflictException.class)
                .hasMessageContaining(maps().resolve("skyfortress.json").toString())
                .hasMessageContaining(drafts().resolve("skyfortress.json").toString());
    }

    @Test
    void loadOfAnUnknownIdIsRefused() throws IOException {
        folders();

        assertThatThrownBy(() -> store().load(ID)).isInstanceOf(DraftNotFoundException.class);
    }

    @Test
    void loadReadsTheDraftBack() throws IOException {
        folders();
        store().save(DraftFixtures.complete(ID));

        assertThat(store().load(ID)).isEqualTo(DraftFixtures.complete(ID));
    }

    private static void write(Path file, MapDraft draft) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, MapDraftJsonWriter.toJson(draft));
    }
}
