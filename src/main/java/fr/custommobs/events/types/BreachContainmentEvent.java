package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventConfigManager;
import fr.custommobs.events.EventListener;
import fr.custommobs.managers.BossStatsManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Événement Contenir la Brèche - Version améliorée avec localisations configurables
 */
public class BreachContainmentEvent extends ServerEvent {

    private final EventConfigManager configManager;
    private final BossStatsManager bossStatsManager;

    private final Map<UUID, Integer> mobKills = new HashMap<>();
    private final List<Location> activeBreaches = new ArrayList<>();
    private final Set<LivingEntity> spawnedMobs = new HashSet<>();

    private int totalMobsKilled = 0;
    private final int mobsToContain;
    private final int duration;
    private boolean breachesContained = false;

    public BreachContainmentEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook,
                                  EventListener.EventRewardsManager rewardsManager, EventConfigManager configManager,
                                  BossStatsManager bossStatsManager) {
        super(plugin, prisonHook, rewardsManager, "breach_containment", "Contenir la Brèche",
                EventType.COOPERATIVE, configManager.getEventSchedule("breach_containment").getDuration());

        this.configManager = configManager;
        this.bossStatsManager = bossStatsManager;
        this.mobsToContain = calculateMobsToContain();
        this.duration = configManager.getEventSchedule("breach_containment").getDuration();
    }

    @Override
    protected void onStart() {
        // Sélectionner les zones de brèche depuis la configuration
        List<EventConfigManager.EventLocationConfig> breachAreas = getBreachAreas();
        if (breachAreas.isEmpty()) {
            plugin.getLogger().severe("§cAucune zone de brèche configurée ('breach-areas')! Événement annulé.");
            forceEnd();
            return;
        }

        // Sélectionner 2-4 zones de brèche aléatoirement
        int breachCount = ThreadLocalRandom.current().nextInt(2, Math.min(5, breachAreas.size() + 1));
        Collections.shuffle(breachAreas);

        for (int i = 0; i < breachCount; i++) {
            EventConfigManager.EventLocationConfig area = breachAreas.get(i);
            Location breachLocation = area.getRandomLocation(plugin); // <-- FIX 1
            if (breachLocation != null) {
                activeBreaches.add(breachLocation);
                createBreach(breachLocation, area.getDisplayName());
            }
        }

        if (activeBreaches.isEmpty()) {
            plugin.getLogger().severe("§cImpossible de créer des brèches! Événement annulé.");
            forceEnd();
            return;
        }

        // Annonces
        Bukkit.broadcastMessage("§5§l🌀 ALERTE BRÈCHE DIMENSIONNELLE ! 🌀");
        Bukkit.broadcastMessage("§d§l" + activeBreaches.size() + " brèches détectées !");
        Bukkit.broadcastMessage("§7§lObjectif: §cContenir §4" + mobsToContain + " entités§7!");
        Bukkit.broadcastMessage("§7§lLieux: " + getBreachLocationNames());

        // Programmer les vagues de monstres
        scheduleMonsterWaves();

        // Programmer les alertes de temps
        scheduleTimeAlerts();
    }

    /**
     * Récupère les zones de brèche depuis la configuration
     */
    private List<EventConfigManager.EventLocationConfig> getBreachAreas() {
        // REFACTORED: Remove hardcoded logic and use the config manager
        return configManager.getEventLocationConfigs("breach-areas");
    }

    /**
     * Calcule le nombre de monstres à contenir selon le nombre de joueurs
     */
    private int calculateMobsToContain() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int baseMobs = configManager.getEventsConfig().getInt("event-settings.breach_containment.base-mobs", 30);
        int mobsPerPlayer = configManager.getEventsConfig().getInt("event-settings.breach_containment.mobs-per-player", 3);
        int playerThreshold = configManager.getEventsConfig().getInt("event-settings.breach_containment.player-threshold", 5);

        int additionalMobs = Math.max(0, (onlinePlayers - playerThreshold) * mobsPerPlayer);
        return baseMobs + additionalMobs;
    }

    /**
     * Crée une brèche à l'emplacement spécifié
     */
    private void createBreach(Location location, String areaName) {
        if (location.getWorld() == null) return;
        // Effets visuels de la brèche
        location.getWorld().spawnParticle(Particle.PORTAL, location, 100, 3, 3, 3, 1.0);
        location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 50, 2, 2, 2, 0.1);

        // Son dramatique
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < 10000) { // 100 blocks
                player.playSound(location, Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.5f);
            }
        }

        plugin.getLogger().info("§5[BRÈCHE] Brèche créée à " + areaName +
                " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
    }

    /**
     * Récupère les noms des localisations des brèches
     */
    private String getBreachLocationNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < activeBreaches.size(); i++) {
            names.add("Zone " + (i + 1)); // Placeholder name, could be improved
        }
        return String.join("§7, §e", names);
    }

    /**
     * Programme les vagues de monstres
     */
    private void scheduleMonsterWaves() {
        // Vagues programmées
        new BukkitRunnable() {
            int waveNumber = 1;

            @Override
            public void run() {
                if (!isActive() || breachesContained) {
                    cancel();
                    return;
                }
                spawnWave(waveNumber);
                waveNumber++;
            }
        }.runTaskTimer(plugin, 100L, 2400L); // Starts after 5s, then every 2 minutes
    }

    /**
     * Fait apparaître une vague de monstres
     */
    private void spawnWave(int waveNumber) {
        List<EventConfigManager.EventMobConfig> breachMobConfigs = getBreachMobConfigs();
        if (breachMobConfigs.isEmpty()) {
            plugin.getLogger().warning("Aucun monstre de brèche configuré ('breach-mobs')!");
            return;
        }

        int mobsPerBreach = Math.min(8, 2 + waveNumber); // Starts at 3, caps at 8
        boolean isIntensive = waveNumber > 4;

        if (isIntensive) {
            Bukkit.broadcastMessage("§c§l[BRÈCHE] §4§lVAGUE INTENSIVE " + (waveNumber - 4) + " ! Les brèches se déstabilisent !");
        } else {
            Bukkit.broadcastMessage("§5§l[BRÈCHE] §dVague " + waveNumber + " détectée !");
        }

        for (Location breachLocation : activeBreaches) {
            spawnMobsAtBreach(breachLocation, mobsPerBreach, breachMobConfigs);
            if (isIntensive && breachLocation.getWorld() != null) {
                breachLocation.getWorld().spawnParticle(Particle.LAVA, breachLocation, 30, 2, 1, 2, 0);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), isIntensive ? Sound.ENTITY_RAVAGER_ROAR : Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, isIntensive ? 0.5f : 0.7f);
        }
    }

    /**
     * Fait apparaître des monstres à une brèche
     */
    private void spawnMobsAtBreach(Location breachLocation, int mobCount, List<EventConfigManager.EventMobConfig> mobConfigs) {
        if (breachLocation.getWorld() == null) return;

        for (int i = 0; i < mobCount; i++) {
            EventConfigManager.EventMobConfig selectedMob = selectWeightedMob(mobConfigs);
            if (selectedMob == null) continue;

            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = 3 + ThreadLocalRandom.current().nextDouble() * 7;

            Location spawnLocation = breachLocation.clone().add(
                    Math.cos(angle) * distance,
                    1, // Spawn slightly above the ground
                    Math.sin(angle) * distance
            );

            try {
                LivingEntity mob = plugin.getMobManager().spawnCustomMob(selectedMob.getId(), spawnLocation);
                if (mob != null) {
                    mob.setMetadata("breach_mob", new FixedMetadataValue(plugin, true));
                    mob.setMetadata("breach_event_id", new FixedMetadataValue(plugin, getId()));
                    mob.setCustomNameVisible(true);
                    spawnedMobs.add(mob);
                    spawnLocation.getWorld().spawnParticle(Particle.PORTAL, spawnLocation.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.5);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du spawn du monstre de brèche " + selectedMob.getId() + ": " + e.getMessage());
            }
        }
    }

    private List<EventConfigManager.EventMobConfig> getBreachMobConfigs() {
        return configManager.getEventMobsInCategory("breach-mobs");
    }

    private EventConfigManager.EventMobConfig selectWeightedMob(List<EventConfigManager.EventMobConfig> mobs) {
        if (mobs.isEmpty()) return null;

        int totalWeight = mobs.stream().mapToInt(EventConfigManager.EventMobConfig::getSpawnWeight).sum();
        if (totalWeight <= 0) {
            return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size()));
        }
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

        int currentWeight = 0;
        for (EventConfigManager.EventMobConfig mob : mobs) {
            currentWeight += mob.getSpawnWeight();
            if (randomWeight < currentWeight) {
                return mob;
            }
        }
        return mobs.get(0); // Fallback
    }

    /**
     * Programme les alertes de temps
     */
    private void scheduleTimeAlerts() {
        int totalDuration = this.duration; // <-- FIX 2

        // Alerte à la moitié du temps
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive() && !breachesContained) {
                    int remaining = getRemainingMobs();
                    Bukkit.broadcastMessage("§e§l[BRÈCHE] §7Mi-temps ! Encore §c" + remaining + " entités§7 à contenir !");
                }
            }
        }.runTaskLater(plugin, (long) (totalDuration / 2) * 20L);

        // Alerte finale
        if (totalDuration > 300) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isActive() && !breachesContained) {
                        Bukkit.broadcastMessage("§c§l[BRÈCHE] §4§lDERNIÈRES 5 MINUTES ! Les brèches vont se stabiliser !");
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.8f);
                        }
                    }
                }
            }.runTaskLater(plugin, (totalDuration - 300) * 20L);
        }
    }

    /**
     * Gère la mort d'un monstre de brèche
     */
    public void onMobKilled(LivingEntity mob, Player killer) {
        if (!spawnedMobs.remove(mob)) return;

        addParticipant(killer);
        mobKills.merge(killer.getUniqueId(), 1, Integer::sum);
        totalMobsKilled++;

        if (mob.getLocation().getWorld() != null) {
            mob.getLocation().getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);
        }
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        int remaining = getRemainingMobs();
        if (remaining > 0) {
            if (remaining <= 10 || totalMobsKilled % 25 == 0) {
                Bukkit.broadcastMessage("§5§l[BRÈCHE] §d" + totalMobsKilled + "§7/§c" + mobsToContain + " §7entités contenues");
            }
        } else {
            onBreachesContained();
        }
    }

    /**
     * Gère la réussite de l'événement
     */
    private void onBreachesContained() {
        if (breachesContained) return;
        breachesContained = true;

        Bukkit.broadcastMessage("\n§a§l🎉 BRÈCHES CONTENUES ! 🎉");
        Bukkit.broadcastMessage("§2§lToutes les entités ont été neutralisées !");
        Bukkit.broadcastMessage("§7§lParticipants: §f" + getParticipantCount() + "\n");

        closeAllBreaches();
        distributeRewards();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive()) {
                    forceEnd();
                }
            }
        }.runTaskLater(plugin, 400L); // 20 secondes
    }

    /**
     * Ferme toutes les brèches avec des effets
     */
    private void closeAllBreaches() {
        for (Location breachLocation : activeBreaches) {
            if (breachLocation.getWorld() == null) continue;
            breachLocation.getWorld().spawnParticle(Particle.REVERSE_PORTAL, breachLocation, 150, 2, 2, 2, 0.2);
            breachLocation.getWorld().playSound(breachLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
        }
    }

    /**
     * Distribue les récompenses
     */
    private void distributeRewards() {
        EventConfigManager.EventRewardConfig rewards = configManager.getEventRewards("breach_containment");
        if (rewards == null) {
            plugin.getLogger().warning("Aucune configuration de récompense pour breach_containment");
            return;
        }

        int participationReward = rewards.getReward("participation");
        int bonusPerKill = rewards.getReward("bonus-per-kill");
        int topKillerBonus = rewards.getReward("top-killer-bonus");

        UUID topKillerId = mobKills.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                double totalReward = participationReward;
                int kills = mobKills.getOrDefault(participantId, 0);
                totalReward += (double) kills * bonusPerKill;

                if (participantId.equals(topKillerId)) {
                    totalReward += topKillerBonus;
                    participant.sendMessage("§a§l[RÉCOMPENSE] §2Bonus: Meilleur chasseur de brèche !");
                }

                if (totalReward > 0 && prisonHook != null) {
                    prisonHook.addCoins(participant, (long) totalReward);
                    participant.sendMessage("§5§l[BRÈCHE] §7Récompense: §a" + String.format("%.0f", totalReward) + "§7 pièces (§d" + kills + " kills§7)");
                }
            }
        }
    }

    @Override
    protected void onEnd() {
        if (!breachesContained) {
            Bukkit.broadcastMessage("§c§l[BRÈCHE] §7Temps écoulé! Les brèches se stabilisent naturellement...");
            Bukkit.broadcastMessage("§7§l" + totalMobsKilled + "§7/§c" + mobsToContain + " §7entités neutralisées.");
            closeAllBreaches();
            distributeFallbackRewards();
        }
        cleanupRemainingMobs();
    }

    private void distributeFallbackRewards() {
        EventConfigManager.EventRewardConfig rewards = configManager.getEventRewards("breach_containment");
        if (rewards == null || prisonHook == null) return;

        int participationReward = rewards.getReward("participation") / 2; // Reduced reward
        int bonusPerKill = rewards.getReward("bonus-per-kill");

        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                int kills = mobKills.getOrDefault(participantId, 0);
                double totalReward = participationReward + ((double) kills * bonusPerKill);
                if (totalReward > 0) {
                    prisonHook.addCoins(participant, (long) totalReward);
                    participant.sendMessage("§7§l[BRÈCHE] §7Récompense partielle: §7" + String.format("%.0f", totalReward) + "§7 pièces");
                }
            }
        }
    }

    private void cleanupRemainingMobs() {
        for (LivingEntity mob : spawnedMobs) {
            if (mob != null && !mob.isDead()) {
                mob.remove();
            }
        }
        spawnedMobs.clear();
    }

    @Override
    protected void onCleanup() {
        mobKills.clear();
        activeBreaches.clear();
        spawnedMobs.clear();
        breachesContained = false;
        totalMobsKilled = 0;
    }

    // GETTERS
    public int getRemainingMobs() {
        return Math.max(0, mobsToContain - totalMobsKilled);
    }
}