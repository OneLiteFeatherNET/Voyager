package net.elytrarace.voyager.setup.adapter;

import static net.elytrarace.voyager.setup.adapter.AllowEveryone.ALLOW_ALL;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.EditClaims;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One editor per map, through the commands: a second builder is refused and told who holds the map, the claim is
 * released by close, by opening another map and by disconnect, and {@code /map unlock} is gated by its own node.
 * Every test wires a fresh registry, sessions and two builders, and drives the server tick by tick.
 */
@EnvTest
class MapClaimsTest {

    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BETA = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String USE = PermissionNode.VOYAGER_SETUP_USE.value();
    private static final String UNLOCK = PermissionNode.VOYAGER_SETUP_UNLOCK.value();

    @TempDir
    Path root;

    private Path data;
    private Path worlds;

    @Test
    void aSecondBuilderCannotOpenAMapAnotherBuilderHoldsAndTheRefusalNamesTheHolder(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        byte[] before = Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"));

        fixture.run(fixture.beta, "map open skyfortress");

        assertThat(fixture.sessions.find(BETA)).isEmpty();
        assertThat(fixture.said(fixture.beta)).contains(new Said("voyager.setup.claim.held", List.of("skyfortress", "Alpha")));
        assertThat(Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"))).isEqualTo(before);
    }

    @Test
    void theHolderOpeningTheSameMapAgainKeepsTheSessionObject(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        var session = fixture.sessions.find(ALPHA).orElseThrow();

        fixture.run(fixture.alpha, "map open skyfortress");

        assertThat(fixture.sessions.find(ALPHA)).containsSame(session);
    }

    @Test
    void theHolderOpeningTheSameMapAgainRepliesAsForAFreshOpen(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");

        fixture.run(fixture.alpha, "map open skyfortress");

        assertThat(fixture.said(fixture.alpha)).contains(new Said("voyager.setup.map.opened", List.of("skyfortress", "0")));
    }

    @Test
    void aBuilderMayOpenAnotherMapOnceTheFirstIsReleased(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        fixture.enter(fixture.alpha, "map new canyon");

        fixture.enter(fixture.beta, "map open skyfortress");

        assertThat(fixture.sessions.find(BETA).map(session -> session.id())).contains(new MapId("skyfortress"));
    }

    @Test
    void aRefusedOpenKeepsTheFirstClaimOfTheBuilder(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        fixture.enter(fixture.beta, "map new canyon");

        fixture.run(fixture.alpha, "map open canyon");
        fixture.run(fixture.beta, "map open skyfortress");

        assertThat(fixture.sessions.find(ALPHA).map(session -> session.id())).contains(new MapId("skyfortress"));
        assertThat(fixture.said(fixture.beta)).contains(new Said("voyager.setup.claim.held", List.of("skyfortress", "Alpha")));
    }

    @Test
    void aBuilderWhoDisconnectsReleasesTheMapForAnotherBuilder(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");

        env.process().eventHandler().call(new PlayerDisconnectEvent(fixture.alpha.player()));
        env.tick();
        fixture.enter(fixture.beta, "map open skyfortress");

        assertThat(fixture.sessions.find(BETA).map(session -> session.id())).contains(new MapId("skyfortress"));
    }

    @Test
    void closeReleasesTheMapForAnotherBuilder(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        fixture.run(fixture.alpha, "map close");

        fixture.enter(fixture.beta, "map open skyfortress");

        assertThat(fixture.sessions.find(BETA).map(session -> session.id())).contains(new MapId("skyfortress"));
    }

    @Test
    void closeRemovesThePreviewsOfTheMap(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        Instance world = fixture.alpha.player().getInstance();
        env.process().eventHandler().call(new PlayerUseItemEvent(fixture.alpha.player(), PlayerHand.MAIN, Wand.item(), 0));
        BoundedTicks.tickWhile(env, () -> displays(world).isEmpty());

        fixture.run(fixture.alpha, "map close");

        assertThat(displays(world)).isEmpty();
    }

    @Test
    void closeLeavesTheBuilderWithNoOpenMap(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");

        fixture.run(fixture.alpha, "map close");

        assertThat(fixture.sessions.find(ALPHA)).isEmpty();
    }

    @Test
    void closeRepliesThatTheMapIsClosed(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");

        fixture.run(fixture.alpha, "map close");

        assertThat(fixture.said(fixture.alpha)).contains(new Said("voyager.setup.map.closed", List.of("skyfortress")));
    }

    @Test
    void spawnAfterCloseRepliesThatNoMapIsOpen(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        fixture.enter(fixture.alpha, "map new skyfortress");
        fixture.run(fixture.alpha, "map close");

        fixture.run(fixture.alpha, "map spawn");

        assertThat(fixture.said(fixture.alpha)).contains(new Said("voyager.setup.map.none", List.of()));
    }

    @Test
    void aMapWhoseWorldCannotBeFoundTakesNoClaim(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL);
        new JsonDraftStore(data, worlds).save(DraftEditor.skeleton(new MapId("ghost")));

        fixture.run(fixture.alpha, "map open ghost");

        assertThat(fixture.said(fixture.alpha)).extracting(Said::key).contains("voyager.setup.refused");
        assertThat(fixture.claims.holder("map:ghost")).isEmpty();
    }

    @Test
    void unlockEndsTheSessionOfTheHolderAndFreesTheMap(Env env) throws IOException {
        Fixture fixture = wire(env, unlockOnlyForBeta());
        fixture.enter(fixture.alpha, "map new skyfortress");

        fixture.run(fixture.beta, "map unlock skyfortress");

        assertThat(fixture.sessions.find(ALPHA)).isEmpty();
        assertThat(fixture.claims.holder("map:skyfortress")).isEmpty();
    }

    @Test
    void unlockFreesTheMapForAnotherBuilder(Env env) throws IOException {
        Fixture fixture = wire(env, unlockOnlyForBeta());
        fixture.enter(fixture.alpha, "map new skyfortress");
        fixture.run(fixture.beta, "map unlock skyfortress");

        fixture.enter(fixture.beta, "map open skyfortress");

        assertThat(fixture.sessions.find(BETA).map(session -> session.id())).contains(new MapId("skyfortress"));
    }

    @Test
    void unlockRepliesWithTheNameOfTheHolderItReleased(Env env) throws IOException {
        Fixture fixture = wire(env, unlockOnlyForBeta());
        fixture.enter(fixture.alpha, "map new skyfortress");

        fixture.run(fixture.beta, "map unlock skyfortress");

        assertThat(fixture.said(fixture.beta)).contains(new Said("voyager.setup.map.unlocked", List.of("skyfortress", "Alpha")));
    }

    @Test
    void unlockWithoutTheUnlockNodeIsDeniedAndLeavesTheHolderInPlace(Env env) throws IOException {
        Fixture fixture = wire(env, ALLOW_ALL_BUT_UNLOCK);
        fixture.enter(fixture.alpha, "map new skyfortress");
        byte[] before = Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"));

        fixture.run(fixture.beta, "map unlock skyfortress");

        assertThat(fixture.said(fixture.beta)).extracting(Said::key).contains("voyager.command.denied");
        assertThat(fixture.claims.holder("map:skyfortress")).isPresent();
        assertThat(fixture.sessions.find(ALPHA).map(session -> session.id())).contains(new MapId("skyfortress"));
        assertThat(Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"))).isEqualTo(before);
    }

    @Test
    void unlockOfAMapNobodyHoldsRepliesThatItIsNotHeldAndWritesNoFile(Env env) throws IOException {
        Fixture fixture = wire(env, unlockOnlyForBeta());

        fixture.run(fixture.beta, "map unlock canyon");

        assertThat(fixture.said(fixture.beta)).contains(new Said("voyager.setup.map.unheld", List.of("canyon")));
        assertThat(data.resolve("drafts")).doesNotExist();
        assertThat(data.resolve("maps")).doesNotExist();
    }

    /** Both builders hold {@code voyager.setup.use}; only Beta holds {@code voyager.setup.unlock}. */
    private static PermissionPolicy unlockOnlyForBeta() {
        return (subject, node) -> node.equals(USE)
                || (node.equals(UNLOCK) && subject instanceof PermissionSubject.Player player && player.id().equals(BETA));
    }

    /** Every builder holds {@code voyager.setup.use}, and nobody holds {@code voyager.setup.unlock}. */
    private static final PermissionPolicy ALLOW_ALL_BUT_UNLOCK = (subject, node) -> node.equals(USE);

    private static List<Entity> displays(Instance world) {
        return world.getEntities().stream().filter(entity -> entity.getEntityType() == EntityType.BLOCK_DISPLAY).toList();
    }

    private Fixture wire(Env env, PermissionPolicy policy) throws IOException {
        data = Files.createDirectories(root.resolve("data"));
        worlds = Files.createDirectories(root.resolve("worlds"));
        DraftStore store = new JsonDraftStore(data, worlds);
        Instance start = env.createFlatInstance();
        EditClaims claims = new EditClaims();
        BuilderSessions sessions = new BuilderSessions(claims);
        env.process().command().register(
                new SetupCommands(store, sessions, worlds, new MapInstances(env.process().instance(), worlds), policy));
        new WandListener(sessions, policy).register(env.process().eventHandler());
        sessions.register(env.process().eventHandler());
        Builder alpha = builder(env, start, ALPHA, "Alpha");
        Builder beta = builder(env, start, BETA, "Beta");
        return new Fixture(env, sessions, claims, alpha, beta);
    }

    private static Builder builder(Env env, Instance start, UUID id, String name) {
        TestConnection connection = env.createConnection(new GameProfile(id, name));
        Player player = connection.connect(start, new Pos(0, 64, 0));
        return new Builder(player, connection.trackIncoming(SystemChatPacket.class));
    }

    /** One translation key with the plain text of its arguments, in the order the builder was told them. */
    record Said(String key, List<String> arguments) {
    }

    private record Builder(Player player, Collector<SystemChatPacket> chat) {
    }

    private record Fixture(Env env, BuilderSessions sessions, EditClaims claims, Builder alpha, Builder beta) {

        void run(Builder builder, String command) {
            env.process().command().execute(builder.player(), command);
            env.tick();
            env.tick();
        }

        /** Runs a command that opens a map, then ticks until the builder has arrived in that map's world. */
        void enter(Builder builder, String command) {
            run(builder, command);
            BoundedTicks.awaitArrival(env, sessions, builder.player());
        }

        List<Said> said(Builder builder) {
            return builder.chat().collect().stream()
                    .map(SystemChatPacket::message)
                    .filter(TranslatableComponent.class::isInstance)
                    .map(TranslatableComponent.class::cast)
                    .map(component -> new Said(component.key(), component.arguments().stream().map(Fixture::text).toList()))
                    .toList();
        }

        private static String text(TranslationArgument argument) {
            Object value = argument.value();
            return value instanceof TextComponent text ? text.content() : String.valueOf(value);
        }
    }
}
