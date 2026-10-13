package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Each parity rule, proven against an in-memory pair of tables that breaks exactly one thing. */
class BundleParityRulesTest {

    private static final Map<String, String> ENGLISH = Map.of(
            "voyager.lobby.waiting", "Waiting: <arg:0> of <arg:1> <c-dim>online",
            "voyager.lobby.cancelled", "Cancelled.");

    private static final Set<String> REQUIRED = ENGLISH.keySet();

    private static List<String> check(Map<String, String> german) {
        return BundleParity.violations("server", ENGLISH, german, REQUIRED);
    }

    @Test
    void aCompletePairPasses() {
        assertThat(check(Map.of(
                "voyager.lobby.waiting", "Warte: <arg:0> von <arg:1> <c-dim>online",
                "voyager.lobby.cancelled", "Abgebrochen."))).isEmpty();
    }

    @Test
    void aMissingKeyFailsAndNamesTheKeyAndTheLocale() {
        List<String> found = check(Map.of("voyager.lobby.waiting", "Warte: <arg:0> von <arg:1> <c-dim>online"));

        assertThat(found).anySatisfy(message -> assertThat(message)
                .contains("voyager.lobby.cancelled")
                .contains("de_DE"));
    }

    @Test
    void anExtraKeyFailsAndNamesIt() {
        List<String> found = check(Map.of(
                "voyager.lobby.waiting", "Warte: <arg:0> von <arg:1> <c-dim>online",
                "voyager.lobby.cancelled", "Abgebrochen.",
                "voyager.lobby.invented", "Erfunden."));

        assertThat(found).anySatisfy(message -> assertThat(message)
                .contains("voyager.lobby.invented")
                .contains("de_DE"));
    }

    @Test
    void aChangedPlaceholderFailsAndNamesTheKey() {
        List<String> found = check(Map.of(
                "voyager.lobby.waiting", "Warte: <arg:0> online",
                "voyager.lobby.cancelled", "Abgebrochen."));

        assertThat(found).anySatisfy(message -> assertThat(message).contains("voyager.lobby.waiting"));
    }

    @Test
    void aRecolouredPaletteTagFails() {
        List<String> found = check(Map.of(
                "voyager.lobby.waiting", "Warte: <arg:0> von <arg:1> <c-warn>online",
                "voyager.lobby.cancelled", "Abgebrochen."));

        assertThat(found).anySatisfy(message -> assertThat(message).contains("voyager.lobby.waiting"));
    }

    @Test
    void aMessageFormatPlaceholderFails() {
        List<String> found = check(Map.of(
                "voyager.lobby.waiting", "Warte: {0} von {1} <c-dim>online",
                "voyager.lobby.cancelled", "Abgebrochen."));

        assertThat(found).anySatisfy(message -> assertThat(message)
                .contains("voyager.lobby.waiting")
                .contains("MessageFormat"));
    }

    @Test
    void aPluralKeyNeedsBothFormsInEveryLocale() {
        Map<String, String> english = Map.of("voyager.ring.one", "1 ring", "voyager.ring.other", "<arg:0> rings");

        List<String> found = BundleParity.violations("server", english, Map.of("voyager.ring.one", "1 Ring"),
                Set.of("voyager.ring.one", "voyager.ring.other"));

        assertThat(found).anySatisfy(message -> assertThat(message).contains("voyager.ring.other"));
    }
}
