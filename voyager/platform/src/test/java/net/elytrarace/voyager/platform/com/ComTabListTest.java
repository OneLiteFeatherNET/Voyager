package net.elytrarace.voyager.platform.com;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The computer racers' tab-list entries, sent to the human players only. Each test gets a fresh {@link Env}, its own
 * connections and its own instance, and drives every tick explicitly.
 */
@EnvTest
class ComTabListTest {

    private static final UUID FIRST = UUID.fromString("0c0ffee0-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("0c0ffee0-0000-4000-8000-000000000002");

    @Test
    void everyHumanGetsTheAddEntryBeforeABodySpawns(Env env) {
        Instance instance = flatInstance(env);
        TestConnection connection = env.createConnection();
        Player human = connection.connect(instance, new Pos(0, 41, 0));
        Collector<ServerPacket> received = connection.trackIncoming();
        ComTabList tabList = new ComTabList(() -> List.of(human));
        ComBody body = new ComBody(FIRST, "COM 1");

        tabList.add(FIRST, "COM 1");
        body.spawn(instance, new Pos(4, 41, 9));
        env.tick();

        List<ServerPacket> packets = received.collect();
        int addAt = indexOfAddEntry(packets, FIRST);
        int spawnAt = indexOfSpawn(packets, FIRST);
        assertThat(addAt)
                .as("the human is sent an add entry for the computer racer")
                .isGreaterThanOrEqualTo(0);
        assertThat(spawnAt)
                .as("the human is sent the body's spawn packet")
                .isGreaterThanOrEqualTo(0);
        assertThat(addAt)
                .as("the add entry arrives before the body spawns, so the client can name it")
                .isLessThan(spawnAt);
    }

    @Test
    void theAddEntryHasTheDefaultSkinAndNoTextures(Env env) {
        Instance instance = flatInstance(env);
        TestConnection connection = env.createConnection();
        Player human = connection.connect(instance, new Pos(0, 41, 0));
        Collector<PlayerInfoUpdatePacket> received = connection.trackIncoming(PlayerInfoUpdatePacket.class);
        ComTabList tabList = new ComTabList(() -> List.of(human));

        tabList.add(FIRST, "COM 1");
        env.tick();

        PlayerInfoUpdatePacket.Entry entry = entryFor(received.collect(), FIRST);
        assertThat(entry.properties())
                .as("a computer racer carries no texture property, so the client draws the default skin")
                .isEmpty();
        assertThat(entry.username())
                .as("the entry is named after the seat")
                .isEqualTo("COM 1");
    }

    @Test
    void aHumanWhoJoinsMidCupGetsTheEntriesOfTheCurrentBodies(Env env) {
        Instance instance = flatInstance(env);
        List<Player> humans = new ArrayList<>();
        ComTabList tabList = new ComTabList(() -> humans);
        tabList.add(FIRST, "COM 1");
        tabList.add(SECOND, "COM 2");
        TestConnection latecomer = env.createConnection();
        humans.add(latecomer.connect(instance, new Pos(0, 41, 0)));
        Collector<PlayerInfoUpdatePacket> received = latecomer.trackIncoming(PlayerInfoUpdatePacket.class);

        tabList.sync();
        env.tick();

        assertThat(addedIds(received.collect()))
                .as("the late human is sent the entries of both bodies that are already on the grid")
                .containsExactlyInAnyOrder(FIRST, SECOND);
    }

    @Test
    void dismissSendsTheRemoveEntries(Env env) {
        Instance instance = flatInstance(env);
        TestConnection connection = env.createConnection();
        Player human = connection.connect(instance, new Pos(0, 41, 0));
        ComTabList tabList = new ComTabList(() -> List.of(human));
        tabList.add(FIRST, "COM 1");
        tabList.add(SECOND, "COM 2");
        Collector<PlayerInfoRemovePacket> received = connection.trackIncoming(PlayerInfoRemovePacket.class);

        tabList.dismiss();
        env.tick();

        assertThat(received.collect().stream().flatMap(packet -> packet.uuids().stream()).toList())
                .as("the human is told to remove every computer racer's entry")
                .containsExactlyInAnyOrder(FIRST, SECOND);
    }

    private static Instance flatInstance(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return instance;
    }

    private static int indexOfAddEntry(List<ServerPacket> packets, UUID id) {
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i) instanceof PlayerInfoUpdatePacket update
                    && update.entries().stream().anyMatch(entry -> entry.uuid().equals(id))) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfSpawn(List<ServerPacket> packets, UUID id) {
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i) instanceof SpawnEntityPacket spawn && spawn.uuid().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static PlayerInfoUpdatePacket.Entry entryFor(List<PlayerInfoUpdatePacket> packets, UUID id) {
        return packets.stream()
                .flatMap(packet -> packet.entries().stream())
                .filter(entry -> entry.uuid().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no add entry was sent for %s".formatted(id)));
    }

    private static List<UUID> addedIds(List<PlayerInfoUpdatePacket> packets) {
        return packets.stream()
                .flatMap(packet -> packet.entries().stream())
                .map(PlayerInfoUpdatePacket.Entry::uuid)
                .filter(id -> id.equals(FIRST) || id.equals(SECOND))
                .toList();
    }
}
