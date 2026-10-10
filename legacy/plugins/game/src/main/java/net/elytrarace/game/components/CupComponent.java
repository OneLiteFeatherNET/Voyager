package net.elytrarace.game.components;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.game.model.GameMapDTO;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Component that stores cup-related data.
 */
public record CupComponent(ResolvedCupDTO cup) implements Component {
    
    /**
     * Creates a new CupComponent with the given cup.
     */
    public static CupComponent create(ResolvedCupDTO cup) {
        return new CupComponent(cup);
    }
    
    /**
     * Creates a new CupComponent with a transformed cup.
     */
    public static CupComponent fromCupDTO(CupDTO cup) {
        if (cup instanceof ResolvedCupDTO resolvedCup) {
            var gameMaps = resolvedCup.maps().stream()
                    .map(GameMapDTO::fromMapDTO)
                    .collect(Collectors.toList());
            return new CupComponent(new ResolvedCupDTO(
                    resolvedCup.name(),
                    resolvedCup.displayName(),
                    gameMaps
            ));
        }
        return null;
    }
    
    /**
     * Gets the name of the cup.
     */
    public Key getName() {
        return cup.name();
    }
    
    /**
     * Gets the display name of the cup.
     */
    public net.kyori.adventure.text.Component getDisplayName() {
        return cup.displayName();
    }
    
    /**
     * Gets the maps in the cup.
     */
    public List<GameMapDTO> getMaps() {
        return cup.maps().stream()
                .map(map -> (GameMapDTO) map)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets a map by index.
     */
    public Optional<GameMapDTO> getMap(int index) {
        if (index < 0 || index >= cup.maps().size()) {
            return Optional.empty();
        }
        return Optional.of((GameMapDTO) cup.maps().get(index));
    }
    
    /**
     * Gets the first map in the cup.
     */
    public Optional<GameMapDTO> getFirstMap() {
        if (cup.maps().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((GameMapDTO) cup.maps().getFirst());
    }
}