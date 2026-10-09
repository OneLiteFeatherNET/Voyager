package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.server.game.exception.UnresolvedCupException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.Optional;

/**
 * Which cup this server plays.
 *
 * <p>Takes the {@link CatalogSnapshot} rather than the {@code CupCatalog} port for the same reason the
 * loader's consistency check does: the port is a one-method {@code byName} lookup and rightly offers
 * no way to enumerate, and "the catalogue's only cup" cannot be answered without enumerating. The
 * port is the right shape for everything that plays a cup; this is the one caller that has to choose
 * one.
 *
 * <p><strong>A cup of any length.</strong> Nothing here — and nothing downstream of it — looks at
 * how many maps the resolved cup has. The repository currently ships one cup listing one map, which
 * means the map-to-map advance this rebuild exists to fix cannot be demonstrated from the committed
 * data; it does not mean one map is a special case in the code.
 */
@ApiStatus.Internal
public abstract class CupResolution {

    private CupResolution() {
    }

    /**
     * Resolves the cup named by {@code chosen}, or the catalogue's only cup when nothing named one.
     *
     * @throws UnresolvedCupException if the name resolves to nothing, or if nothing named a cup and
     *     the catalogue does not hold exactly one
     */
    @Contract(pure = true)
    public static CupDefinition resolve(CatalogSnapshot catalog, Optional<String> chosen) {
        if (chosen.isPresent()) {
            String name = chosen.get();
            return catalog.cupByName(name)
                    .orElseThrow(() -> UnresolvedCupException.noSuchCup(name, catalog.cupNames()));
        }
        if (catalog.cupNames().size() != 1) {
            throw UnresolvedCupException.ambiguous(catalog.cupNames());
        }
        String only = catalog.cupNames().iterator().next();
        // byName cannot be empty here — the name came out of this same catalogue — but orElseThrow
        // with the real exception beats orElseThrow() with a bare NoSuchElementException if that ever
        // stops being true.
        return catalog.cupByName(only)
                .orElseThrow(() -> UnresolvedCupException.noSuchCup(only, catalog.cupNames()));
    }
}
