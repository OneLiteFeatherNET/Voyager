package net.elytrarace.voyager.platform.rotation;

import net.elytrarace.voyager.api.race.CatalogKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Where a boot starts: the cup it plays first, the cups of the rotation in play order, the cups the boot check skipped,
 * and the warnings a boot logs about the choice.
 *
 * <p>A plan without a rotation ({@link #single}) names the cup the settings name, or none, and leaves the loader to pick the
 * catalogue's only cup, exactly as before the rotation existed. A planned rotation has every cup in {@link #rotation()}, in
 * the canonical key form, and a boot cup that is playable.
 *
 * @param boot     the cup the server plays first, or empty when the loader picks the catalogue's only cup
 * @param rotation the rotation's cups in play order, canonical names; empty without a rotation
 * @param skipped  the rotation's cups the boot check skipped for missing worlds
 * @param warnings what a boot logs about the choice, one line each
 */
public record RotationPlan(Optional<String> boot, List<String> rotation, Set<String> skipped, List<String> warnings) {

    public RotationPlan {
        Objects.requireNonNull(boot, "boot must not be null");
        rotation = List.copyOf(rotation);
        skipped = Set.copyOf(skipped);
        warnings = List.copyOf(warnings);
    }

    /**
     * The plan of a server without a rotation: the cup the settings name, or the catalogue's only cup.
     *
     * @param chosen the cup the settings name, or empty
     */
    public static RotationPlan single(Optional<String> chosen) {
        return new RotationPlan(chosen, List.of(), Set.of(), List.of());
    }

    /**
     * The plan of a rotation. The boot cup is the state file's cup when that cup is in the rotation and playable, and the first
     * playable cup otherwise. A state file that names a skipped or unknown cup is a warning, not a refusal.
     *
     * @param cups     the rotation's cups in play order, canonical names
     * @param skipped  the cups the boot check skipped
     * @param stored   the state file's name, or empty when there is none or it cannot be read
     * @param stateKept whether a state file is configured at all
     * @return the plan, with the warnings for the boot log
     * @throws IllegalArgumentException if no cup of the rotation is playable; the boot check refuses that case first
     */
    public static RotationPlan planned(List<String> cups, Set<String> skipped, Optional<String> stored, boolean stateKept) {
        List<String> playable = cups.stream().filter(cup -> !skipped.contains(cup)).toList();
        if (playable.isEmpty()) {
            throw new IllegalArgumentException("no cup of the rotation is playable");
        }
        String boot = playable.getFirst();
        List<String> warnings = new ArrayList<>();
        if (stored.isPresent()) {
            String name = CatalogKeys.normalise(stored.get());
            if (playable.contains(name)) {
                boot = name;
            } else if (cups.contains(name)) {
                warnings.add("the state file names cup '%s', which is skipped for this boot; the rotation starts at '%s'"
                        .formatted(name, boot));
            } else {
                warnings.add("the state file names cup '%s', which is not in the rotation; the rotation starts at '%s'"
                        .formatted(name, boot));
            }
        }
        if (!stateKept && cups.size() > 1) {
            warnings.add(("VOYAGER_CUP_ROTATION_STATE is not set, so the position of the rotation lives in memory: "
                    + "a restart or redeploy begins again at '%s'").formatted(boot));
        }
        return new RotationPlan(Optional.of(boot), cups, skipped, warnings);
    }
}
