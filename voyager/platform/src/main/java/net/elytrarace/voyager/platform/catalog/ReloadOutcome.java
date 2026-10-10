package net.elytrarace.voyager.platform.catalog;

import java.util.List;

/**
 * What one catalogue reload came to. Never thrown: a bad edit is {@link Rejected}, with every problem found.
 */
public sealed interface ReloadOutcome permits ReloadOutcome.Applied, ReloadOutcome.Rejected {

    /**
     * The new catalogue is valid and every world it names is open. Offered to a {@link CatalogHolder}, where
     * it plays from the next round on.
     *
     * @param loaded   the catalogue to offer
     * @param warnings things the operator must know that do not block the reload, such as an open world
     *                 whose region files changed on disk and needs a restart
     */
    record Applied(LoadedCatalog loaded, List<String> warnings) implements ReloadOutcome {

        public Applied {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * The reload was refused. The current catalogue is unchanged.
     *
     * @param problems one line per problem, each in the same form the configuration check prints
     */
    record Rejected(List<String> problems) implements ReloadOutcome {

        public Rejected {
            problems = List.copyOf(problems);
        }
    }
}
