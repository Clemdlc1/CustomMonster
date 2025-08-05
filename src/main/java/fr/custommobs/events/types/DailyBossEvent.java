package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventConfigManager;
import fr.custommobs.events.EventListener;
import fr.custommobs.managers.BossStatsManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss Quotidien - Version améliorée avec configuration et intégration BossStatsManager
 */
public class DailyBossEvent extends ServerEvent {

    private final EventConfigManager configManager;
    private BossStatsManager bossStatsManager;

    private LivingEntity boss;
    private Location bossLocation;
    private EventConfigManager.EventLocationConfig selectedArena;
    private EventConfigManager.EventMobConfig selectedBossConfig;
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();

    private final int duration; // <-- SOLUTION PART 1: Add field to store duration
    private boolean bossSpawned = false;
    private boolean bossKilled = false;
    private Player bossKiller = null;

    public DailyBossEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook,
                          EventListener.EventRewardsManager rewardsManager, EventConfigManager configManager,
                          BossStatsManager bossStatsManager) {
        super(plugin, prisonHook, rewardsManager, "daily_boss", "Boss Quotidien",
                EventType.COOPERATIVE, configManager.getEventSchedule("daily_boss").duration());

        this.configManager = configManager;
        this.bossStatsManager = bossStatsManager;
        // <-- SOLUTION PART 2: Initialize the duration field
        this.duration = configManager.getEventSchedule("daily_boss").duration();
    }

    @Override
    protected void onStart() {
        // Sélectionner l'arène depuis la configuration
        selectedArena = selectBossArena();
        if (selectedArena == null) {
            plugin.getLogger().severe("§cAucune arène de boss configurée! Événement annulé.");
            forceEnd();
            return;
        }

        // Sélectionner le type de boss depuis la configuration
        selectedBossConfig = selectBossType();
        if (selectedBossConfig == null) {
            plugin.getLogger().severe("§cAucun boss configuré! Événement annulé.");
            forceEnd();
            return;
        }

        bossLocation = selectedArena.getCenterLocation(plugin);
        if (bossLocation == null) {
            plugin.getLogger().severe("§cMonde de l'arène introuvable! Événement annulé.");
            forceEnd();
            return;
        }

        // Annonce de l'événement avec le nom du boss et de l'arène
        Bukkit.broadcastMessage("§0§l💀 BOSS QUOTIDIEN APPARAÎT ! 💀");
      Bukkit.broadcastMessage("§7§lRendez-vous en §e" + selectedArena.displayName());
        Bukkit.broadcastMessage("§7§lPréparation: §c60 secondes");

        // Effets de préparation dans l'arène
        spawnPreparationEffects();

        // Spawner le boss après 1 minute
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive()) {
                    spawnBoss();
                }
            }
        }.runTaskLater(plugin, 1200L); // 60 secondes

        // Alertes de temps
        scheduleTimeAlerts();
    }

    /**
     * Sélectionne une arène de boss depuis la configuration
     */
    private EventConfigManager.EventLocationConfig selectBossArena() {
        // Assurez-vous d'avoir ajouté la méthode getRandomLocationConfig à EventConfigManager
        return configManager.getRandomLocationConfig("boss-arenas");
    }

    /**
     * Sélectionne un type de boss depuis la configuration
     */
    private EventConfigManager.EventMobConfig selectBossType() {
        List<EventConfigManager.EventMobConfig> bosses = configManager.getEventMobsInCategory("daily-bosses");
        if (bosses.isEmpty()) {
            return null;
        }

        // Sélection pondérée
        int totalWeight = bosses.stream().mapToInt(EventConfigManager.EventMobConfig::weight).sum();
        if (totalWeight <= 0) {
            return bosses.get(ThreadLocalRandom.current().nextInt(bosses.size()));
        }
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

        int currentWeight = 0;
        for (EventConfigManager.EventMobConfig bossConfig : bosses) {
            currentWeight += bossConfig.weight();
            if (randomWeight < currentWeight) {
                return bossConfig;
            }
        }

        return bosses.getFirst(); // Fallback
    }

    /**
     * Effets de préparation dans l'arène
     */
    private void spawnPreparationEffects() {
        new BukkitRunnable() {
            int countdown = 60;

            @Override
            public void run() {
                if (!isActive() || bossLocation == null || bossLocation.getWorld() == null) {
                    cancel();
                    return;
                }

                // Effets visuels dans l'arène
                bossLocation.getWorld().spawnParticle(Particle.PORTAL, bossLocation, 20, 2, 2, 2, 0.5);

                // Alertes aux moments clés
                if (countdown == 30 || countdown == 10 || (countdown <= 5 && countdown > 0)) {
                    Bukkit.broadcastMessage("§c§l[BOSS] §7Apparition dans §c" + countdown + " secondes§7!");

                    // Son d'alerte
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    }
                }

                countdown--;
                if (countdown < 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Programme les alertes de temps
     */
    private void scheduleTimeAlerts() {
        int totalDuration = this.duration; // <-- SOLUTION PART 3: Use the local field

        // Alerte à 15 minutes restantes
        if (totalDuration > 900) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isActive() && !bossKilled) {
                        Bukkit.broadcastMessage("§e§l[BOSS] §715 minutes restantes pour vaincre le boss!");
                    }
                }
            }.runTaskLater(plugin, (totalDuration - 900) * 20L);
        }

        // Alerte à 5 minutes restantes
        if (totalDuration > 300) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isActive() && !bossKilled) {
                        Bukkit.broadcastMessage("§c§l[BOSS] §7§lDERNIÈRES 5 MINUTES ! Dépêchez-vous !");

                        // Effets dramatiques
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                        }
                    }
                }
            }.runTaskLater(plugin, (totalDuration - 300) * 20L);
        }
    }

    /**
     * Fait apparaître le boss
     */
    private void spawnBoss() {
        try {
            // Spawner le boss customisé
            boss = plugin.getMobManager().spawnCustomMob(selectedBossConfig.id(), bossLocation);

            if (boss != null) {
                // Métadonnées pour l'identification
                boss.setMetadata("daily_boss", new FixedMetadataValue(plugin, true));
                boss.setMetadata("boss_event_id", new FixedMetadataValue(plugin, getId()));


                // Nom customisé
                boss.setCustomNameVisible(true);

                // Empêcher la despawn
                boss.setRemoveWhenFarAway(false);
                boss.setPersistent(true);

                bossSpawned = true;

                // Démarrer le tracking des statistiques avec BossStatsManager
                if (bossStatsManager != null) {
                    bossStatsManager.startBossFight(boss, selectedBossConfig.id());
                    plugin.getLogger().info("§a[BOSS EVENT] Tracking des statistiques démarré pour: " + selectedBossConfig.id());
                }

                // Annonces
                Bukkit.broadcastMessage("§0§l[BOSS] §8" + " §8§lest apparu !");
                Bukkit.broadcastMessage("§7§lLocalisation: §e" + selectedArena.displayName());

                // Effets dramatiques
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
                }

                if(bossLocation.getWorld() != null) {
                    bossLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bossLocation, 100, 5, 5, 5, 0.3);
                    bossLocation.getWorld().spawnParticle(Particle.DRAGON_BREATH, bossLocation, 50, 3, 3, 3, 0.1);
                }

            } else {
                plugin.getLogger().severe("§cÉchec du spawn du boss: " + selectedBossConfig.id());
                forceEnd();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("§cErreur lors du spawn du boss: " + e.getMessage());
            e.printStackTrace();
            forceEnd();
        }
    }

    /**
     * Gère les dégâts infligés au boss
     */
    public void onBossDamaged(Player attacker, double damage) {
        if (!bossSpawned || boss == null || boss.isDead() || bossKilled) return;

        addParticipant(attacker);
        damageDealt.merge(attacker.getUniqueId(), damage, Double::sum);

        // Intégration avec BossStatsManager
        if (bossStatsManager != null) {
            bossStatsManager.recordDamageToBoss(boss, attacker, damage);
        }

        // Message de feedback occasionnel
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            double totalDamage = damageDealt.get(attacker.getUniqueId());
            attacker.sendMessage("§0§l[BOSS] §7Dégâts total: §c" + String.format("%.1f", totalDamage));
        }

        // Affichage de la barre de vie du boss
        updateBossHealthDisplay();
    }

    /**
     * Gère les dégâts subis par un joueur du boss
     */
    public void onPlayerDamagedByBoss(Player player, double damage) {
        if (!bossSpawned || boss == null || boss.isDead() || bossKilled) return;

        // Intégration avec BossStatsManager
        if (bossStatsManager != null) {
            bossStatsManager.recordDamageFromBoss(boss, player, damage);
        }
    }

    /**
     * Met à jour l'affichage de la vie du boss
     */
    private void updateBossHealthDisplay() {
        if (boss == null || boss.isDead() || boss.getAttribute(Attribute.MAX_HEALTH) == null) return;

        double currentHealth = boss.getHealth();
        double maxHealth = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthPercent = (currentHealth / maxHealth) * 100;

        // Barre de progression
        int barLength = 20;
        int filledBars = (int) (healthPercent / 100 * barLength);

        StringBuilder healthBar = new StringBuilder();
        healthBar.append("█".repeat(barLength));

        // Couleur selon le pourcentage de vie
        String healthColor;
        if (healthPercent < 25) healthColor = "§c";
        else if (healthPercent < 50) healthColor = "§e";
        else if (healthPercent < 75) healthColor = "§6";
        else healthColor = "§a";

        // Colorize the health bar itself
        String finalHealthBar = healthColor + healthBar.substring(0, filledBars) + "§8" + healthBar.substring(filledBars);


        String healthDisplay = String.format("%s%.1f§7/§c%.1f §7PV (%s%.1f%%§7)",
                healthColor, currentHealth, maxHealth, healthColor, healthPercent);

        // Afficher à tous les participants
        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                participant.sendActionBar(" " + finalHealthBar + " " + healthDisplay);
            }
        }
    }

    /**
     * Gère la mort du boss
     */
    public void onBossKilled(Player killer) {
        if (bossKilled) return; // Éviter les doubles exécutions

        bossKilled = true;
        bossKiller = killer;

        // Intégration avec BossStatsManager - terminer le tracking
        if (bossStatsManager != null && boss != null) {
            bossStatsManager.endBossFight(boss, true);
        }

        // Annonces de victoire
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§a§l🏆 BOSS VAINCU ! 🏆");
        Bukkit.broadcastMessage("§a§lTueur final: §e§l" + killer.getName());
        Bukkit.broadcastMessage("§7§lParticipants: §f" + getParticipantCount());
        Bukkit.broadcastMessage("");

        // Effets de victoire
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            if (getParticipants().contains(player.getUniqueId()) && player.getWorld() != null) {
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 20, 1, 1, 1, 0.1);
            }
        }

        // Distribuer les récompenses
        distributeRewards();

        // Terminer l'événement après 30 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive()) {
                    forceEnd();
                }
            }
        }.runTaskLater(plugin, 600L);
    }

    /**
     * Distribue les récompenses selon la configuration
     */
    private void distributeRewards() {
        EventConfigManager.EventRewardConfig rewards = configManager.getEventRewards("daily_boss");
        if (rewards == null) {
            plugin.getLogger().warning("Aucune configuration de récompense pour daily_boss");
            return;
        }

        int participationReward = rewards.getReward("participation");
        int killerBonus = rewards.getReward("killer-bonus");

        // Récompenses par paliers de dégâts
        int bronzeThreshold = rewards.getTieredReward("damage-tiers", "bronze-threshold");
        int silverThreshold = rewards.getTieredReward("damage-tiers", "silver-threshold");
        int goldThreshold = rewards.getTieredReward("damage-tiers", "gold-threshold");
        int bronzeReward = rewards.getTieredReward("damage-tiers", "bronze-reward");
        int silverReward = rewards.getTieredReward("damage-tiers", "silver-reward");
        int goldReward = rewards.getTieredReward("damage-tiers", "gold-reward");

        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                double totalReward = participationReward;

                // Bonus selon les dégâts
                double damage = damageDealt.getOrDefault(participantId, 0.0);
                if (damage >= goldThreshold && goldThreshold > 0) {
                    totalReward += goldReward;
                    participant.sendMessage("§6§l[RÉCOMPENSE] §ePalier OR atteint !");
                } else if (damage >= silverThreshold && silverThreshold > 0) {
                    totalReward += silverReward;
                    participant.sendMessage("§7§l[RÉCOMPENSE] §fPalier ARGENT atteint !");
                } else if (damage >= bronzeThreshold && bronzeThreshold > 0) {
                    totalReward += bronzeReward;
                    participant.sendMessage("§c§l[RÉCOMPENSE] §6Palier BRONZE atteint !");
                }

                // Bonus pour le tueur final
                if (participant.equals(bossKiller)) {
                    totalReward += killerBonus;
                    participant.sendMessage("§a§l[RÉCOMPENSE] §2Bonus tueur final !");
                }

                // Donner la récompense
                if (totalReward > 0 && prisonHook != null) {
                    prisonHook.addCoins(participant, (long) totalReward);
                    participant.sendMessage("§a§l[BOSS] §7Récompense totale: §a" + String.format("%.0f", totalReward) + "§7 pièces");
                }
            }
        }
    }

    @Override
    protected void onEnd() {
        if (!bossKilled) {
            // Boss pas tué - timeout
            Bukkit.broadcastMessage("§c§l[BOSS] §7Temps écoulé ! " + " §7le boss s'échappe...");

            if (bossStatsManager != null && boss != null) {
                bossStatsManager.endBossFight(boss, false);
            }

            // Récompenses de participation seulement
            distributeFallbackRewards();
        }

        // Nettoyer le boss s'il existe encore
        if (boss != null && !boss.isDead()) {
            boss.remove();
        }
    }

    /**
     * Distribue des récompenses de fallback si le boss n'est pas tué
     */
    private void distributeFallbackRewards() {
        EventConfigManager.EventRewardConfig rewards = configManager.getEventRewards("daily_boss");
        if (rewards == null) return;

        int participationReward = rewards.getReward("participation") / 2; // Récompense réduite

        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline() && participationReward > 0 && prisonHook != null) {
                prisonHook.addCoins(participant, participationReward);
                participant.sendMessage("§7§l[BOSS] §7Récompense de participation: §7" + participationReward + "§7 pièces");
            }
        }
    }

    @Override
    protected void onCleanup() {
        // Cleanup spécifique
        damageDealt.clear();
        playerDeaths.clear();
        bossSpawned = false;
        bossKilled = false;
        bossKiller = null;
        selectedArena = null;
        selectedBossConfig = null;
        boss = null;
    }

    // ===============================
    // GETTERS
    // ===============================

    public LivingEntity getBoss() {
        return boss;
    }

    public String getArenaDisplayName() {
        return selectedArena != null ? selectedArena.displayName() : "Arène Inconnue";
    }

    public boolean isBossSpawned() {
        return bossSpawned;
    }

    public Map<UUID, Double> getDamageDealt() {
        return Collections.unmodifiableMap(damageDealt);
    }
}