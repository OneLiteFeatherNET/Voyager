package net.elytrarace.setup.wizard;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WizardManager {

    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final Map<UUID, WizardStep> currentSteps = new ConcurrentHashMap<>();

    public boolean isActive(UUID playerId) {
        return activeBars.containsKey(playerId);
    }

    public void start(Player player) {
        if (activeBars.containsKey(player.getUniqueId())) return;
        var step = WizardStep.ENTER_SETUP;
        var bar = BossBar.bossBar(
                buildTitle(step),
                step.progress(),
                BossBar.Color.BLUE,
                BossBar.Overlay.NOTCHED_10
        );
        activeBars.put(player.getUniqueId(), bar);
        currentSteps.put(player.getUniqueId(), step);
        player.showBossBar(bar);
    }

    public void stop(Player player) {
        var bar = activeBars.remove(player.getUniqueId());
        currentSteps.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void advance(Player player) {
        var current = currentSteps.get(player.getUniqueId());
        if (current == null) return;
        var steps = WizardStep.values();
        int next = current.ordinal() + 1;
        if (next >= steps.length) {
            stop(player);
            player.sendActionBar(Component.translatable("wizard.complete"));
            return;
        }
        setStep(player, steps[next]);
    }

    public void setStep(Player player, WizardStep step) {
        var bar = activeBars.get(player.getUniqueId());
        if (bar == null) return;
        currentSteps.put(player.getUniqueId(), step);
        bar.name(buildTitle(step));
        bar.progress(step.progress());
    }

    public void remove(UUID playerId) {
        var player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player != null) {
            stop(player);
        } else {
            activeBars.remove(playerId);
            currentSteps.remove(playerId);
        }
    }

    public WizardStep currentStep(UUID playerId) {
        return currentSteps.get(playerId);
    }

    private Component buildTitle(WizardStep step) {
        int stepNum = step.ordinal() + 1;
        int total = WizardStep.values().length;
        return Component.translatable("wizard.bar.title")
                .arguments(
                        Component.text(stepNum),
                        Component.text(total),
                        Component.translatable(step.titleKey())
                )
                .append(Component.translatable("wizard.bar.separator"))
                .append(Component.translatable(step.hintKey()));
    }
}
