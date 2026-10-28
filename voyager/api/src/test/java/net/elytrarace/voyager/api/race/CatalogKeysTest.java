package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidCatalogKeyException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogKeysTest {

    @Test
    void aFlatIdBecomesAKeyInTheElytraraceNamespace() {
        assertThat(CatalogKeys.of("blue_and_red").asString()).isEqualTo("elytrarace:blue_and_red");
    }

    @Test
    void theNamespaceOfEveryKeyIsElytrarace() {
        assertThat(CatalogKeys.of("alpha_cup").namespace()).isEqualTo("elytrarace");
    }

    @Test
    void aFlatIdAndItsKeyNormaliseToTheSameString() {
        assertThat(CatalogKeys.normalise("alpha_cup")).isEqualTo("elytrarace:alpha_cup");
        assertThat(CatalogKeys.normalise("elytrarace:alpha_cup")).isEqualTo("elytrarace:alpha_cup");
    }

    @Test
    void aValueWithCapitalsIsRefused() {
        assertThatThrownBy(() -> CatalogKeys.of("BlueAndRed"))
                .isInstanceOf(InvalidCatalogKeyException.class)
                .hasMessageContaining("BlueAndRed");
    }

    @Test
    void aValueOfThirtyThreeCharactersIsRefusedAndThirtyTwoIsKept() {
        String thirtyTwo = "a".repeat(32);

        assertThat(CatalogKeys.of(thirtyTwo).value()).isEqualTo(thirtyTwo);
        assertThatThrownBy(() -> CatalogKeys.of("a".repeat(33)))
                .isInstanceOf(InvalidCatalogKeyException.class);
    }

    @Test
    void aBlankValueIsRefused() {
        assertThatThrownBy(() -> CatalogKeys.of("   "))
                .isInstanceOf(InvalidCatalogKeyException.class);
    }

    @Test
    void aValueThatStartsWithAnUnderscoreIsRefused() {
        assertThatThrownBy(() -> CatalogKeys.of("_alpha"))
                .isInstanceOf(InvalidCatalogKeyException.class);
    }
}
