package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import fr.custommobs.events.types.DailyBossEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestionnaire de statistiques de boss amélioré avec intégration événements
 */
public class BossStatsManager {

    private final CustomMobsPlugin plugin;

    // Stockage des statistiques par combat de boss
    private final Map<String, BossFightStats> activeBossFights; // bossUUID -> stats
    private final Map<String, List<BossFightStats>> completedFights; // eventId -> historique

    public BossStatsManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.activeBossFights = new HashMap<>();
        this.completedFights = new HashMap<>();

        // Nettoyer les combats abandonnés toutes les 10 minutes
        startCleanupTask();
    }

    /**
     * Démarre le tracking d'un combat de boss
     */
    public void startBossFight(LivingEntity boss, String mobId) {
        if (isBoss(mobId)) {
            String bossId = boss.getUniqueId().toString();
            String bossName = boss.getCustomName() != null ? boss.getCustomName() : getBossDisplayName(mobId);

            // Vérifie si le boss n'est pas déjà tracké
            if (activeBossFights.containsKey(bossId)) {
                plugin.getLogger().warning("Boss déjà tracké: " + bossId);
                return;
            }

            BossFightStats stats = new BossFightStats(mobId, bossName);
            activeBossFights.put(bossId, stats);

            plugin.getLogger().info("§6[BOSS STATS] Début du tracking pour: " + mobId + " (" + bossName + ")");

            // Annonce le début du combat
            announceBossFightStart(bossName);
        } else {
            plugin.getLogger().warning("Tentative de tracking d'un non-boss: " + mobId);
        }
    }

    /**
     * Démarre le tracking spécifiquement pour un événement
     */
    public void startBossFightForEvent(LivingEntity boss, String mobId, String eventId) {
        startBossFight(boss, mobId);

        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.setEventId(eventId);
            plugin.getLogger().info("§6[BOSS STATS] Combat associé à l'événement: " + eventId);
        }
    }

    /**
     * Enregistre des dégâts infligés au boss
     */
    public void recordDamageToBoss(LivingEntity boss, Player player, double damage) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addDamageToBoss(player, damage);
            stats.updateLastActivity();

            plugin.getLogger().fine("§6[BOSS STATS] Dégâts enregistrés: " + player.getName() +
                    " -> " + damage + " (Total: " + stats.damageToBoss.getOrDefault(player.getUniqueId(), 0.0) + ")");
        } else {
            plugin.getLogger().fine("§7[BOSS STATS] Dégâts non trackés - boss non enregistré: " + bossId);

            // Tenter de redémarrer le tracking automatiquement
            String mobId = CustomMob.getCustomMobId(boss);
            if (isBoss(mobId)) {
                plugin.getLogger().info("§6[BOSS STATS] Redémarrage automatique du tracking pour: " + mobId);
                startBossFight(boss, mobId);
                recordDamageToBoss(boss, player, damage); // Réessaie
            }
        }
    }

    /**
     * Enregistre des dégâts subis par un joueur
     */
    public void recordDamageFromBoss(LivingEntity boss, Player player, double damage) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addDamageFromBoss(player, damage);
            stats.updateLastActivity();

            plugin.getLogger().fine("§6[BOSS STATS] Dégâts du boss enregistrés: " + player.getName() + " <- " + damage);
        } else {
            plugin.getLogger().fine("§7[BOSS STATS] Dégâts du boss non trackés - boss non enregistré: " + bossId);
        }
    }

    /**
     * Enregistre la mort d'un sbire du boss
     */
    public void recordMinionKill(LivingEntity boss, Player player, String minionType) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addMinionKill(player, minionType);
            stats.updateLastActivity();

            plugin.getLogger().fine("§6[BOSS STATS] Sbire tué: " + player.getName() + " -> " + minionType);
        } else {
            plugin.getLogger().fine("§7[BOSS STATS] Mort de sbire non trackée - boss non enregistré: " + bossId);
        }
    }

    /**
     * Enregistre une mort de joueur
     */
    public void recordPlayerDeath(LivingEntity boss, Player player) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addPlayerDeath(player);
            stats.updateLastActivity();

            plugin.getLogger().fine("§6[BOSS STATS] Mort de joueur: " + player.getName());
        } else {
            plugin.getLogger().fine("§7[BOSS STATS] Mort de joueur non trackée - boss non enregistré: " + bossId);
        }
    }

    /**
     * Termine un combat de boss
     */
    public void endBossFight(LivingEntity boss, boolean victory) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.remove(bossId);

        if (stats != null) {
            stats.endFight(victory);

            // Ajouter à l'historique
            String eventId = stats.getEventId() != null ? stats.getEventId() : "manual";
            completedFights.computeIfAbsent(eventId, k -> new ArrayList<>()).add(stats);

            plugin.getLogger().info("§6[BOSS STATS] Combat terminé: " + stats.bossName +
                    " - " + (victory ? "VICTOIRE" : "DÉFAITE"));

            // Afficher les résultats après un délai
            new BukkitRunnable() {
                @Override
                public void run() {
                    displayResults(stats, victory);
                }
            }.runTaskLater(plugin, 40L); // 2 secondes de délai

        } else {
            plugin.getLogger().warning("§7[BOSS STATS] Tentative de fin de combat pour boss non tracké: " + bossId);

            // Affichage générique si pas de stats
            String bossName = boss.getCustomName() != null ? boss.getCustomName() : "Boss Inconnu";
            new BukkitRunnable() {
                @Override
                public void run() {
                    displayGenericResults(bossName, victory);
                }
            }.runTaskLater(plugin, 40L);
        }
    }

    /**
     * Récupère les statistiques d'un combat actif
     */
    public BossFightStats getActiveBossFight(LivingEntity boss) {
        String bossId = boss.getUniqueId().toString();
        return activeBossFights.get(bossId);
    }

    /**
     * Récupère l'historique des combats pour un événement
     */
    public List<BossFightStats> getEventHistory(String eventId) {
        return completedFights.getOrDefault(eventId, new ArrayList<>());
    }

    /**
     * Récupère tous les combats actifs
     */
    public Collection<BossFightStats> getActiveFights() {
        return new ArrayList<>(activeBossFights.values());
    }

    /**
     * Méthode pour débugger les boss actifs
     */
    public void debugActiveBosses() {
        plugin.getLogger().info("§6=== DEBUG BOSS STATS ===");
        plugin.getLogger().info("§6Nombre de boss actifs: " + activeBossFights.size());

        for (Map.Entry<String, BossFightStats> entry : activeBossFights.entrySet()) {
            BossFightStats stats = entry.getValue();
            Set<UUID> participants = getAllParticipants(stats);
            double totalDamage = stats.damageToBoss.values().stream().mapToDouble(Double::doubleValue).sum();

            plugin.getLogger().info("§6Boss: " + entry.getKey() + " -> " + stats.bossName + " (" + stats.mobId + ")");
            plugin.getLogger().info("§6  Event: " + (stats.getEventId() != null ? stats.getEventId() : "Manual"));
            plugin.getLogger().info("§6  Participants: " + participants.size());
            plugin.getLogger().info("§6  Dégâts totaux: " + String.format("%.1f", totalDamage));
            plugin.getLogger().info("§6  Durée: " + formatDuration(System.currentTimeMillis() - stats.startTime));
        }

        plugin.getLogger().info("§6=========================");
    }

    /**
     * Affiche les résultats du combat - MÉTHODE AMÉLIORÉE
     */
    private void displayResults(BossFightStats stats, boolean victory) {
        Set<UUID> participants = getAllParticipants(stats);

        if (participants.isEmpty()) {
            displayGenericResults(stats.bossName, victory);
            return;
        }

        // En-tête des résultats
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§6§l▓▓§e§l      RÉSULTATS DU COMBAT      §6§l▓▓");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");

        Bukkit.broadcastMessage("§e§lBoss: " + stats.bossName);
        Bukkit.broadcastMessage("§a§lRésultat: " + (victory ? "§2§lVICTOIRE ! ✓" : "§c§lDÉFAITE ✗"));
        Bukkit.broadcastMessage("§7§lDurée: §f" + formatDuration(stats.getDuration()));
        Bukkit.broadcastMessage("§7§lParticipants: §f" + participants.size());

        if (stats.getEventId() != null) {
            Bukkit.broadcastMessage("§7§lÉvénement: §f" + stats.getEventId());
        }

        Bukkit.broadcastMessage("");

        // Top 3 DPS
        displayTopDamageDealer(stats);

        // Statistiques diverses
        displayMiscStats(stats);

        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("");
    }

    /**
     * Affiche le top des DPS
     */
    private void displayTopDamageDealer(BossFightStats stats) {
        List<Map.Entry<UUID, Double>> topDamagers = stats.damageToBoss.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (!topDamagers.isEmpty()) {
            Bukkit.broadcastMessage("§6§l🏆 TOP DÉGÂTS:");

            String[] medals = {"§e§l🥇", "§7§l🥈", "§6§l🥉"};
            for (int i = 0; i < topDamagers.size(); i++) {
                Map.Entry<UUID, Double> entry = topDamagers.get(i);
                Player player = Bukkit.getPlayer(entry.getKey());
                String playerName = player != null ? player.getName() : "Joueur Déconnecté";

                double damage = entry.getValue();
                double dps = damage / (stats.getDuration() / 1000.0);

                Bukkit.broadcastMessage(String.format("%s §f%s §7- §c%.1f §7dégâts (§e%.1f DPS§7)",
                        medals[i], playerName, damage, dps));
            }
            Bukkit.broadcastMessage("");
        }
    }

    /**
     * Affiche les statistiques diverses
     */
    private void displayMiscStats(BossFightStats stats) {
        // Dégâts totaux infligés
        double totalDamageDealt = stats.damageToBoss.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalDamageReceived = stats.damageFromBoss.values().stream().mapToDouble(Double::doubleValue).sum();

        Bukkit.broadcastMessage("§7§l📊 STATISTIQUES:");
        Bukkit.broadcastMessage("§7• Dégâts infligés: §c" + String.format("%.1f", totalDamageDealt));
        Bukkit.broadcastMessage("§7• Dégâts subis: §c" + String.format("%.1f", totalDamageReceived));

        // Morts
        int totalDeaths = stats.playerDeaths.values().stream().mapToInt(Integer::intValue).sum();
        if (totalDeaths > 0) {
            Bukkit.broadcastMessage("§7• Morts de joueurs: §c" + totalDeaths);
        }

        // Sbires
        int totalMinionKills = stats.minionKills.values().stream().mapToInt(Integer::intValue).sum();
        if (totalMinionKills > 0) {
            Bukkit.broadcastMessage("§7• Sbires éliminés: §a" + totalMinionKills);
        }

        Bukkit.broadcastMessage("");
    }

    /**
     * Affiche des résultats génériques quand les stats ne sont pas disponibles
     */
    private void displayGenericResults(String bossName, boolean victory) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§6§l▓▓§e§l      RÉSULTATS DU COMBAT      §6§l▓▓");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§e§lBoss: " + bossName);
        Bukkit.broadcastMessage("§a§lRésultat: " + (victory ? "§2§lVICTOIRE ! ✓" : "§c§lDÉFAITE ✗"));
        Bukkit.broadcastMessage("§7§oLes statistiques détaillées n'ont pas pu être trackées");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("");
    }

    /**
     * Démarre la tâche de nettoyage des combats abandonnés
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupAbandonedFights();
            }
        }.runTaskTimer(plugin, 12000L, 12000L); // Toutes les 10 minutes
    }

    /**
     * Nettoie les combats abandonnés (boss morts sans notification)
     */
    private void cleanupAbandonedFights() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, BossFightStats>> iterator = activeBossFights.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, BossFightStats> entry = iterator.next();
            BossFightStats stats = entry.getValue();

            // Si pas d'activité depuis 15 minutes
            if (currentTime - stats.getLastActivity() > 900000) {
                plugin.getLogger().info("§7[BOSS STATS] Nettoyage du combat abandonné: " + stats.bossName);
                iterator.remove();

                // Ajouter à l'historique comme défaite
                stats.endFight(false);
                String eventId = stats.getEventId() != null ? stats.getEventId() : "abandoned";
                completedFights.computeIfAbsent(eventId, k -> new ArrayList<>()).add(stats);
            }
        }
    }

    /**
     * Formate la durée du combat
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Récupère tous les participants du combat
     */
    private Set<UUID> getAllParticipants(BossFightStats stats) {
        Set<UUID> participants = new HashSet<>();
        participants.addAll(stats.damageToBoss.keySet());
        participants.addAll(stats.damageFromBoss.keySet());
        participants.addAll(stats.minionKills.keySet());
        participants.addAll(stats.playerDeaths.keySet());
        return participants;
    }

    /**
     * Vérifie si c'est un boss
     */
    private boolean isBoss(String mobId) {
        if (mobId == null) return false;

        // Boss explicites
        return mobId.contains("boss") || mobId.contains("dragon") || mobId.contains("warden") ||
                mobId.contains("wither") || mobId.contains("ravager") || mobId.contains("necromancer");
    }

    /**
     * Récupère le nom d'affichage du boss
     */
    private String getBossDisplayName(String mobId) {
        return switch (mobId) {
            case "wither_boss" -> "§5§lArchliche Nécrosis";
            case "warden_boss" -> "§0§lGardien des Abysses";
            case "ravager_boss" -> "§c§lDévastateur Primordial";
            case "necromancer_dark" -> "§5§lArchiliche";
            case "dragon_fire" -> "§4§lDrake Cendré";
            case "geode_aberration" -> "§d§lAberration Géodique";
            default -> "§6§lBoss " + mobId.replace("_", " ");
        };
    }

    /**
     * Annonce le début d'un combat de boss
     */
    private void announceBossFightStart(String bossName) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("§6§l        COMBAT DE BOSS COMMENCÉ !");
        Bukkit.broadcastMessage("§e§l           " + bossName);
        Bukkit.broadcastMessage("§6§l      Les statistiques sont trackées !");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("");
    }

    /**
     * Classe interne améliorée pour stocker les statistiques d'un combat
     */
    public static class BossFightStats {
        public final String mobId;
        public final String bossName;
        public final long startTime;
        private long endTime;
        private long lastActivity;
        private String eventId;
        private boolean victory;

        public final Map<UUID, Double> damageToBoss = new HashMap<>();
        public final Map<UUID, Double> damageFromBoss = new HashMap<>();
        public final Map<UUID, Integer> minionKills = new HashMap<>();
        public final Map<UUID, Integer> playerDeaths = new HashMap<>();

        public BossFightStats(String mobId, String bossName) {
            this.mobId = mobId;
            this.bossName = bossName != null ? bossName : "Boss Inconnu";
            this.startTime = System.currentTimeMillis();
            this.lastActivity = this.startTime;
        }

        public void addDamageToBoss(Player player, double damage) {
            damageToBoss.merge(player.getUniqueId(), damage, Double::sum);
            updateLastActivity();
        }

        public void addDamageFromBoss(Player player, double damage) {
            damageFromBoss.merge(player.getUniqueId(), damage, Double::sum);
            updateLastActivity();
        }

        public void addMinionKill(Player player, String minionType) {
            minionKills.merge(player.getUniqueId(), 1, Integer::sum);
            updateLastActivity();
        }

        public void addPlayerDeath(Player player) {
            playerDeaths.merge(player.getUniqueId(), 1, Integer::sum);
            updateLastActivity();
        }

        public void endFight(boolean victory) {
            this.endTime = System.currentTimeMillis();
            this.victory = victory;
        }

        public void updateLastActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        // Getters
        public long getDuration() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }

        public long getLastActivity() {
            return lastActivity;
        }

        public String getEventId() {
            return eventId;
        }

        public boolean isVictory() {
            return victory;
        }

        public boolean isFinished() {
            return endTime > 0;
        }
    }
}