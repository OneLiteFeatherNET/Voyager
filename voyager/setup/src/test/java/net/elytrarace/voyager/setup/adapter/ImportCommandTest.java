package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.Census;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.ImportReport;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.StatedSpawn;
import net.elytrarace.voyager.api.mapsetup.WorldFolderName;
import net.elytrarace.voyager.api.mapsetup.WorldImporter;
import net.elytrarace.voyager.api.mapsetup.WorldLayout;
import net.elytrarace.voyager.api.mapsetup.exception.DraftNotFoundException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException.Reason;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.ImportGuard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /map import} as a plain command: the permission, the id and folder rules, the refusals and the reply. The
 * importer and the store are fakes, so no test here touches the file system; the copy itself is tested in the platform.
 */
class ImportCommandTest {

    private static final MapId SKYCUBE = new MapId("skycube");
    private static final String FOLDER = "elytrarace-cube";
    private static final PermissionPolicy EVERYTHING = (subject, node) -> true;
    private static final PermissionPolicy NOTHING = (subject, node) -> false;
    private static final PermissionPolicy USE_ONLY = (subject, node) -> "voyager.setup.use".equals(node);

    private final RecordingStore store = new RecordingStore();
    private final FakeImporter importer = new FakeImporter();
    private final ImportGuard guard = new ImportGuard();
    private final List<Component> replies = new ArrayList<>();

    private ImportCommand command(PermissionPolicy policy) {
        return new ImportCommand(store, importer, guard, policy, Runnable::run, Runnable::run);
    }

    private void run(PermissionPolicy policy, String folder, String id) {
        command(policy).run(new PermissionSubject.Console(), folder, id, replies::add);
    }

    private List<String> keys() {
        return replies.stream()
                .filter(TranslatableComponent.class::isInstance)
                .map(line -> ((TranslatableComponent) line).key())
                .toList();
    }

    private List<String> argumentsOf(String key) {
        return replies.stream()
                .filter(TranslatableComponent.class::isInstance)
                .map(TranslatableComponent.class::cast)
                .filter(line -> line.key().equals(key))
                .findFirst()
                .orElseThrow()
                .arguments().stream()
                .map(argument -> ((TextComponent) argument.value()).content())
                .toList();
    }

    @Test
    void aBuilderWithoutTheSetupNodeChangesNothingAndIsTold() {
        run(NOTHING, FOLDER, "skycube");

        assertThat(keys()).containsExactly("voyager.command.denied");
        assertThat(importer.imported).isEmpty();
        assertThat(guard.isClaimed(SKYCUBE)).isFalse();
    }

    @Test
    void aBuilderWithTheSetupNodeButNotTheImportNodeChangesNothingAndIsTold() {
        run(USE_ONLY, FOLDER, "skycube");

        assertThat(keys()).containsExactly("voyager.command.denied");
        assertThat(importer.imported).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    void anIdThatAlreadyHasADraftIsRefusedAndTheReplyNamesIt() {
        store.drafts.put(SKYCUBE, DraftEditor.skeleton(SKYCUBE));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).containsExactly("voyager.setup.map.exists");
        assertThat(argumentsOf("voyager.setup.map.exists")).containsExactly("skycube");
        assertThat(importer.imported).isEmpty();
    }

    @Test
    void anIdWhoseImportIsAlreadyRunningIsRefusedAsBusy() {
        guard.claim(SKYCUBE);

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).containsExactly("voyager.setup.import.busy");
        assertThat(argumentsOf("voyager.setup.import.busy")).containsExactly("skycube");
        assertThat(importer.imported).isEmpty();
    }

    @Test
    void aMissingImportRootIsRefusedWithTheAbsolutePathItExpected() {
        importer.refusal = WorldImportRefusedException.because(Reason.ROOT_MISSING, "/srv/no-imports");

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).containsExactly("voyager.setup.import.started", "voyager.setup.import.root.missing");
        assertThat(argumentsOf("voyager.setup.import.root.missing")).containsExactly("/srv/no-imports");
        assertThat(guard.isClaimed(SKYCUBE)).as("the id is released after a refusal").isFalse();
    }

    @Test
    void aFolderNameWithTraversalIsRefusedBeforeAnyFileIsRead() {
        run(EVERYTHING, "../secret", "skycube");

        assertThat(keys()).containsExactly("voyager.setup.import.folder.invalid");
        assertThat(argumentsOf("voyager.setup.import.folder.invalid")).containsExactly("../secret");
        assertThat(importer.imported).isEmpty();
    }

    @Test
    void aSuccessfulImportSavesADraftWithTheStatedSpawnAndFreesTheId() {
        importer.report = report(new StatedSpawn(120, 70, -40, StatedSpawn.Source.MODERN), new Census(2, 0, 0, 0));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(store.saved).hasSize(1);
        MapDraft saved = store.saved.get(0);
        assertThat(saved.id()).isEqualTo(SKYCUBE);
        assertThat(saved.world()).isEqualTo("skycube");
        assertThat(saved.spawn()).isEqualTo(new Vec3(120.5, 70, -39.5));
        assertThat(saved.rings()).isEmpty();
        assertThat(keys()).contains("voyager.setup.import.done", "voyager.setup.import.census");
        assertThat(argumentsOf("voyager.setup.import.done")).containsExactly("skycube", "1", "8192");
        assertThat(guard.isClaimed(SKYCUBE)).isFalse();
    }

    @Test
    void aFailedSaveRemovesTheCopiedWorldAndReportsTheFailure() {
        importer.report = report(null, new Census(1, 0, 0, 0));
        store.saveFailure = DraftWriteFailedException.of(SKYCUBE, new IOException("disk full"));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(importer.discarded).containsExactly(SKYCUBE);
        assertThat(store.saved).isEmpty();
        assertThat(keys()).containsExactly("voyager.setup.import.started", "voyager.setup.import.failed");
        assertThat(guard.isClaimed(SKYCUBE)).isFalse();
    }

    @Test
    void aSpawnOnTheDefaultColumnGetsTheHintToCheckIt() {
        importer.report = report(new StatedSpawn(0, 65, 0, StatedSpawn.Source.LEGACY), new Census(1, 0, 0, 0));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).contains("voyager.setup.import.spawn.hint");
        assertThat(argumentsOf("voyager.setup.import.spawn.hint")).containsExactly("0", "65", "0");
    }

    @Test
    void aSpawnAwayFromTheDefaultColumnGetsNoHint() {
        importer.report = report(new StatedSpawn(120, 70, -40, StatedSpawn.Source.MODERN), new Census(1, 0, 0, 0));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).doesNotContain("voyager.setup.import.spawn.hint");
    }

    @Test
    void aPartialWorldIsImportedWithItsPercentageAndWarningAndNoRefusal() {
        importer.report = report(null, new Census(1, 1, 0, 0));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(store.saved).hasSize(1);
        assertThat(keys()).contains("voyager.setup.import.partial.warning", "voyager.setup.import.done");
        assertThat(argumentsOf("voyager.setup.import.partial.warning")).containsExactly("1", "50");
        assertThat(importer.discarded).isEmpty();
    }

    @Test
    void aWorldWithoutAStatedSpawnSavesADraftWithoutOneAndSaysSo() {
        importer.report = report(null, new Census(1, 0, 0, 0));

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(store.saved.get(0).spawn()).isNull();
        assertThat(keys()).contains("voyager.setup.import.spawn.none");
    }

    @Test
    void aNewerDataVersionIsRefusedAndNamesBothVersions() {
        importer.refusal = WorldImportRefusedException.because(Reason.DATA_VERSION_NEWER, "4904", "4903");

        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).containsExactly("voyager.setup.import.started", "voyager.setup.import.dataversion.newer");
        assertThat(argumentsOf("voyager.setup.import.dataversion.newer")).containsExactly("4904", "4903");
        assertThat(store.saved).isEmpty();
    }

    @Test
    void theIdIsFreeAgainAfterAFailedImport() {
        importer.refusal = WorldImportRefusedException.because(Reason.LAYOUT_UNSUPPORTED);
        run(EVERYTHING, FOLDER, "skycube");
        importer.refusal = null;
        importer.report = report(null, new Census(1, 0, 0, 0));

        replies.clear();
        run(EVERYTHING, FOLDER, "skycube");

        assertThat(keys()).doesNotContain("voyager.setup.import.busy");
        assertThat(keys()).contains("voyager.setup.import.done");
    }

    private static ImportReport report(@Nullable StatedSpawn spawn, Census census) {
        return new ImportReport(WorldLayout.REGION_AT_ROOT, 1, 8192, List.of(), spawn, census);
    }

    /** A store held in memory: a draft is found by its id, and a save can be made to fail. */
    private static final class RecordingStore implements DraftStore {

        final Map<MapId, MapDraft> drafts = new HashMap<>();
        final List<MapDraft> saved = new ArrayList<>();
        @Nullable RuntimeException saveFailure;

        @Override
        public MapDraft create(MapDraft skeleton) {
            throw new UnsupportedOperationException("the import does not create a world through the store");
        }

        @Override
        public MapDraft load(MapId id) {
            MapDraft draft = drafts.get(id);
            if (draft == null) {
                throw DraftNotFoundException.of(id);
            }
            return draft;
        }

        @Override
        public void save(MapDraft draft) {
            if (saveFailure != null) {
                throw saveFailure;
            }
            saved.add(draft);
        }
    }

    /** An importer that records what it was asked, and returns or throws what the test sets. */
    private static final class FakeImporter implements WorldImporter {

        final List<MapId> imported = new ArrayList<>();
        final List<MapId> discarded = new ArrayList<>();
        ImportReport report = report(null, new Census(1, 0, 0, 0));
        @Nullable RuntimeException refusal;

        @Override
        public ImportReport importWorld(WorldFolderName folder, MapId id) {
            imported.add(id);
            if (refusal != null) {
                throw refusal;
            }
            return report;
        }

        @Override
        public void discard(MapId id) {
            discarded.add(id);
        }
    }
}
