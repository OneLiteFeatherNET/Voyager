package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftChangedException;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The session's save guard: a commit over a draft that changed on disk is refused, and the session keeps its draft. */
@EnvTest
class MapSessionTest {

    private static final MapId ID = new MapId("skyfortress");

    @TempDir
    Path root;

    @Test
    void aCommitOverADraftThatChangedOnDiskIsRefusedAndTheSessionKeepsItsDraft(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        DraftStore store = new JsonDraftStore(data, worlds);
        DraftStore otherServer = new JsonDraftStore(data, worlds);
        store.create(DraftEditor.skeleton(ID));
        MapDraft kept = store.load(ID);
        Instance world = env.createFlatInstance();
        Player builder = env.createConnection().connect(world, new Pos(0, 64, 0));
        MapSession open = new MapSession(builder, store, kept, world);
        otherServer.save(DraftEditor.withSpawn(kept, new Vec3(1, 64, 1)));

        assertThatThrownBy(() -> open.commit(DraftEditor.withSpawn(kept, new Vec3(2, 64, 2))))
                .isInstanceOf(DraftChangedException.class);

        assertThat(open.draft()).isEqualTo(kept);
    }
}
