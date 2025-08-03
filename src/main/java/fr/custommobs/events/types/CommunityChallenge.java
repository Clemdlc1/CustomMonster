package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom; /**
 * Défi Communautaire
 */
public class CommunityChallenge extends ServerEvent {
    private final Map<String, Integer> objectives = new HashMap<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<UUID, Integer> individualContributions = new HashMap<>();

    public CommunityChallenge(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "community_challenge", "Défi Communautaire",
                EventType.COMMUNITY, 24 * 60 * 60); // 24 heures
    }

    @Override
    protected void onStart() {
        setupObjectives();

        Bukkit.broadcastMessage("§b§l🌟 DÉFI COMMUNAUTAIRE ! 🌟");
        Bukkit.broadcastMessage("§e§lTravaillez ensemble pour atteindre les objectifs !");

        for (Map.Entry<String, Integer> objective : objectives.entrySet()) {
            Bukkit.broadcastMessage("§7§l• " + objective.getKey() + ": §f0/" + objective.getValue());
        }

        startProgressTracking();
    }

    private void setupObjectives() {
        objectives.put("Blocs minés", 500000);
        objectives.put("Monstres tués", 5000);
        objectives.put("Votes serveur", 100);
        objectives.put("Transactions HDV", 1000);
    }

    private void startProgressTracking() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }

                updateProgress();

                // Vérifier si tous les objectifs sont atteints
                if (allObjectivesCompleted()) {
                    forceEnd();
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Toutes les 5 minutes
    }

    private void updateProgress() {
        // Mise à jour fictive du progrès (à remplacer par de vraies données)
        for (String objective : objectives.keySet()) {
            int currentProgress = progress.getOrDefault(objective, 0);
            int target = objectives.get(objective);

            if (currentProgress < target) {
                int increment = ThreadLocalRandom.current().nextInt(100) + 50;
                progress.put(objective, Math.min(currentProgress + increment, target));
            }
        }

        // Annonce du progrès toutes les 10 vérifications
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            announceProgress();
        }
    }

    private void announceProgress() {
        Bukkit.broadcastMessage("§b§l[DÉFI] §7Progrès communautaire:");

        for (Map.Entry<String, Integer> objective : objectives.entrySet()) {
            String name = objective.getKey();
            int target = objective.getValue();
            int current = progress.getOrDefault(name, 0);
            double percentage = (double) current / target * 100;

            String bar = createProgressBar(percentage);
            Bukkit.broadcastMessage("§7• " + name + ": " + bar + " §f" + current + "/" + target);
        }
    }

    private String createProgressBar(double percentage) {
        int bars = (int) (percentage / 10);

        return "§a" + "█".repeat(Math.max(0, bars)) +
                "§7" +
                "█".repeat(Math.max(0, 10 - bars));
    }

    private boolean allObjectivesCompleted() {
        for (Map.Entry<String, Integer> objective : objectives.entrySet()) {
            int current = progress.getOrDefault(objective.getKey(), 0);
            if (current < objective.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onEnd() {
        boolean success = allObjectivesCompleted();

        if (success) {
            Bukkit.broadcastMessage("§a§l[DÉFI] §2Tous les objectifs atteints ! Bravo !");
            distributeSuccessRewards();
        } else {
            Bukkit.broadcastMessage("§c§l[DÉFI] §7Temps écoulé ! Objectifs non atteints...");
            distributeParticipationRewards();
        }
    }

    private void distributeSuccessRewards() {
        // Récompenses pour tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .beacons(500)
                    .tokens(25000)
                    .addItem(prisonHook.createKey("legendary"))
                    .addItem(prisonHook.createKey("crystal"));

            prisonHook.giveEventReward(player, reward);
        }

        // Boost serveur temporaire
        Bukkit.broadcastMessage("§6§l[BONUS] §eBoost serveur activé pendant 2 heures !");
    }

    private void distributeParticipationRewards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .beacons(100)
                    .tokens(5000)
                    .addItem(prisonHook.createKey("rare"));

            prisonHook.giveEventReward(player, reward);
        }
    }

    @Override
    protected void onCleanup() {
        objectives.clear();
        progress.clear();
        individualContributions.clear();
    }
}
