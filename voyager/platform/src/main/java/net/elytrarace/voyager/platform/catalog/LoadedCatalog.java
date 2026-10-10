package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One catalogue a round can play: the snapshot that was read, the cup selected from it, and when it was
 * read.
 *
 * <p>The cup is resolved when this value is built, so a round never resolves a cup itself and a cup that
 * names a map nothing provides is refused here, as {@link UnresolvedCupMapException}, rather than on a
 * tick. Immutable: a round that pins one keeps exactly what it was given, whatever is offered later.
 *
 * @param snapshot the maps and cups that were read, unique and immutable
 * @param cup      the cup this catalogue plays, one of {@code snapshot.cups()}
 * @param loadedAt when the snapshot was read, from the injected clock
 */
public record LoadedCatalog(CatalogSnapshot snapshot, CupDefinition cup, Instant loadedAt) {

    public LoadedCatalog {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(cup, "cup must not be null");
        Objects.requireNonNull(loadedAt, "loadedAt must not be null");
        Optional<UnresolvedCupMapException> unresolved = CatalogConsistency.unresolvedCupMaps(
                snapshot.maps(), Map.of(cup.name(), cup));
        if (unresolved.isPresent()) {
            throw unresolved.get();
        }
    }

    /** The cup's maps in rotation order. Every name resolves: the constructor checked it. */
    public List<MapDefinition> rotation() {
        return cup.mapNames().stream()
                .map(name -> snapshot.mapByName(name).orElseThrow(() -> new IllegalStateException(
                        "cup '%s' plays a map named '%s' the catalogue does not hold".formatted(cup.name(), name))))
                .toList();
    }
}
