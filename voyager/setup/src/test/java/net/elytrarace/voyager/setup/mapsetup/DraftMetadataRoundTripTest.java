package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftMetadataRoundTripTest {

    @TempDir
    Path directory;

    @Test
    void aMapWithANameAndAuthorsKeepsBothAfterAnEditIsSavedAndLoadedAgain() {
        MapId id = new MapId("blueandred");
        List<Author> authors = List.of(
                new Author(UUID.fromString("05bf52c6-7bb0-4f13-8951-0e1fd803df35"), "TheMeinerLP"),
                new Author(UUID.fromString("e1dff87a-1059-47f1-bc7d-533f072bf8c7"), "Seelenretterin"));
        MapDraft named = new MapDraft(id, id.value(), new Vec3(0, 64, 0), List.of(),
                RingDefaults.REFERENCE_TIME_SECONDS, RingDefaults.BOOST, RingDefaults.GUIDE_LINE,
                RingEffectTuning.DEFAULT, "Blue & Red", authors);
        Ring ring = new Ring(0, new Vec3(4, 66, 4), new Vec3(0, 0, 1), RingDefaults.RADIUS, 10, RingType.STANDARD);
        JsonDraftStore store = new JsonDraftStore(directory.resolve("data"), directory.resolve("worlds"));

        MapDraft edited = DraftEditor.withRingAppended(named, ring);
        store.save(edited);
        MapDraft loaded = store.load(id);

        assertThat(loaded.displayName()).isEqualTo("Blue & Red");
        assertThat(loaded.authors()).isEqualTo(authors);
        assertThat(loaded.rings()).hasSize(1);
    }
}
