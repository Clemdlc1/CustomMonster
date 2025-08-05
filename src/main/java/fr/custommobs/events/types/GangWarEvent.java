package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventConfigManager;
import fr.custommobs.events.EventListener;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Guerre des Gangs - Weekend complet de combat entre gangs
 * Système de points basé sur les kills avec scaling selon le score du gang ennemi
 */
public class GangWarEvent extends ServerEvent {

    // Maps pour le tracking
    private final Map<String, Integer> gangScores = new ConcurrentHashMap<>(); // Gang -> Score total
    private final Map<UUID, String> playerGangs = new ConcurrentHashMap<>(); // Player -> Gang
    private final Map<UUID, Integer> playerScores = new ConcurrentHashMap<>(); // Player -> Score individuel
    private final Map<UUID, GangWarStats> playerStats = new ConcurrentHashMap<>(); // Statistiques détaillées

    // Avant-postes (pour future implémentation)
    private final Map<String, String> outpostControllers = new ConcurrentHashMap<>(); // OutpostId -> GangName
    private final Map<String, Long> outpostCaptureTime = new ConcurrentHashMap<>(); // OutpostId -> Timestamp

    // Configuration
    private final EventConfigManager configManager;
    private BossBar gangWarBossBar;
    private BukkitTask leaderboardTask;
    private BukkitTask outpostTask;
    private boolean debugEnabled;

    public GangWarEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook,
                        EventListener.EventRewardsManager rewardsManager, EventConfigManager configManager) {
        super(plugin, prisonHook, rewardsManager, "gang_war", "Guerre des Gangs",
                EventType.COMPETITIVE, 165600); // 46 heures (vendredi 20h -> dimanche 18h)
        this.configManager = configManager;
        this.debugEnabled = configManager.getEventsConfig().getBoolean("debug.log_gang_war", true);
    }

    @Override
    protected void onStart() {
        // Messages d'annonce
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("§4§l           GUERRE DES GANGS");
        Bukkit.broadcastMessage("§4§l         WEEKEND DE COMBAT");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("§c§lLes gangs s'affrontent pour 46 heures !");
        Bukkit.broadcastMessage("§7§lObjectifs: PvP, chasse aux monstres, contrôle d'avant-postes");
        Bukkit.broadcastMessage("§e§lCommande: §f/event gangwar §e§lpour voir les scores");
        Bukkit.broadcastMessage("");

        // Jouer un son pour tous les joueurs
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        }

        // Initialiser le système
        setupGangWar();
        createBossBar();
        startPeriodicTasks();

        if (debugEnabled) {
            plugin.getLogger().info("§4[GANG_WAR] Événement démarré - Durée: " + (durationSeconds / 3600) + " heures");
        }
    }

    private void setupGangWar() {
        // Initialiser les gangs actifs et leurs joueurs
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (prisonHook.isInGang(player)) {
                String gangName = prisonHook.getGangName(player);
                if (gangName != null) {
                    playerGangs.put(player.getUniqueId(), gangName);
                    gangScores.putIfAbsent(gangName, 0);
                    playerScores.putIfAbsent(player.getUniqueId(), 0);
                    playerStats.putIfAbsent(player.getUniqueId(), new GangWarStats());

                    // Ajouter comme participant
                    addParticipant(player);
                }
            }
        }

        if (debugEnabled) {
            plugin.getLogger().info("§4[GANG_WAR] " + gangScores.size() + " gangs initialisés, " +
                    participants.size() + " joueurs participants");
        }
    }

    private void createBossBar() {
        gangWarBossBar = Bukkit.createBossBar(
                "§4§lGuerre des Gangs - §c§l" + formatTimeRemaining(),
                BarColor.RED,
                BarStyle.SEGMENTED_10
        );

        // Ajouter tous les joueurs participants
        for (UUID participantId : participants) {
            Player player = Bukkit.getPlayer(participantId);
            if (player != null && player.isOnline()) {
                gangWarBossBar.addPlayer(player);
            }
        }
    }

    private void startPeriodicTasks() {
        // Mise à jour du leaderboard toutes les 30 minutes
        int leaderboardInterval = configManager.getEventsConfig()
                .getInt("advanced-settings.gang_war.notifications.leaderboard_interval", 1800) * 20;

        leaderboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive()) {
                    broadcastLeaderboard();
                }
            }
        }.runTaskTimer(plugin, leaderboardInterval, leaderboardInterval);
        tasks.add(leaderboardTask);

        // Mise à jour de la boss bar toutes les 5 secondes
        BukkitTask bossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isActive() && gangWarBossBar != null) {
                    updateBossBar();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
        tasks.add(bossBarTask);

        // Tâche pour les avant-postes (quand implémentés)
        if (configManager.getEventsConfig().getBoolean("advanced-settings.gang_war.outposts.enabled", true)) {
            outpostTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (isActive()) {
                        processOutpostControl();
                    }
                }
            }.runTaskTimer(plugin, 1200L, 1200L); // Toutes les minutes
            tasks.add(outpostTask);
        }
    }

    /**
     * Appelé quand un joueur tue un monstre
     */
    public void onMonsterKilled(LivingEntity monster, Player killer) {
        if (!isActive() || killer == null) return;

        // Vérifier que le joueur est dans un gang
        String killerGang = playerGangs.get(killer.getUniqueId());
        if (killerGang == null) {
            if (debugEnabled) {
                killer.sendMessage("§c§l[GANG_WAR] §cVous devez être dans un gang pour gagner des points !");
            }
            return;
        }

        // Calculer les points
        int basePoints = configManager.getEventsConfig()
                .getInt("advanced-settings.gang_war.scoring.monster_kill", 10);

        addPointsToPlayer(killer, killerGang, basePoints);

        // Stats
        GangWarStats stats = playerStats.get(killer.getUniqueId());
        if (stats != null) {
            stats.monsterKills++;
        }

        if (debugEnabled && basePoints > 0) {
            plugin.getLogger().info("§4[GANG_WAR] " + killer.getName() + " (" + killerGang +
                    ") a tué un monstre: +" + basePoints + " points");
        }
    }

    /**
     * Appelé quand un joueur tue un autre joueur
     */
    public void onPlayerKilled(Player killer, Player victim) {
        if (!isActive() || killer == null || victim == null) return;

        String killerGang = playerGangs.get(killer.getUniqueId());
        String victimGang = playerGangs.get(victim.getUniqueId());

        // Vérifier que le killer est dans un gang
        if (killerGang == null) {
            if (debugEnabled) {
                killer.sendMessage("§c§l[GANG_WAR] §cVous devez être dans un gang pour gagner des points !");
            }
            return;
        }

        // Pas de points si la victime n'est pas dans un gang
        if (victimGang == null) {
            return;
        }

        // Friendly fire penalty
        if (killerGang.equals(victimGang)) {
            int penalty = configManager.getEventsConfig()
                    .getInt("advanced-settings.gang_war.restrictions.friendly_fire_penalty", -25);
            addPointsToPlayer(killer, killerGang, penalty);

            killer.sendMessage("§c§l[GANG_WAR] §cPénalité pour friendly fire: " + penalty + " points !");
            return;
        }

        // Calculer les points avec scaling
        int basePoints = configManager.getEventsConfig()
                .getInt("advanced-settings.gang_war.scoring.player_kill_base", 50);

        int victimGangScore = gangScores.getOrDefault(victimGang, 0);
        double multiplier = 1.0 + (victimGangScore * configManager.getEventsConfig()
                .getDouble("advanced-settings.gang_war.scoring.gang_score_multiplier", 0.1));

        double maxMultiplier = configManager.getEventsConfig()
                .getDouble("advanced-settings.gang_war.scoring.max_multiplier", 5.0);
        multiplier = Math.min(multiplier, maxMultiplier);

        int finalPoints = (int) (basePoints * multiplier);

        addPointsToPlayer(killer, killerGang, finalPoints);

        // Stats
        GangWarStats killerStats = playerStats.get(killer.getUniqueId());
        if (killerStats != null) {
            killerStats.playerKills++;
        }

        // Messages
        killer.sendMessage("§a§l[GANG_WAR] §a+" + finalPoints + " points §7(Kill sur " + victimGang +
                ", x" + String.format("%.1f", multiplier) + ")");

        if (configManager.getEventsConfig().getBoolean("advanced-settings.gang_war.notifications.announce_kills", true)) {
            Bukkit.broadcastMessage("§4§l[GANG_WAR] §c" + killer.getName() + " §7(" + killerGang +
                    ") §ca tué §c" + victim.getName() + " §7(" + victimGang +
                    ") §7- §a+" + finalPoints + " points");
        }

        if (debugEnabled) {
            plugin.getLogger().info("§4[GANG_WAR] PvP Kill: " + killer.getName() + " -> " + victim.getName() +
                    " | Points: " + finalPoints + " (x" + String.format("%.1f", multiplier) + ")");
        }
    }

    /**
     * Ajoute des points à un joueur et son gang
     */
    public void addPointsToPlayer(Player player, String gangName, int points) {
        if (points == 0) return;

        // Ajouter aux scores
        playerScores.merge(player.getUniqueId(), points, Integer::sum);
        gangScores.merge(gangName, points, Integer::sum);

        // Ajouter au système global de récompenses
        rewardsManager.addGlobalScore(player, points, "gang_war");

        // Son pour points positifs
        if (points > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }

    /**
     * Système d'avant-postes
     */
    private void processOutpostControl() {
        // Cette méthode sera implémentée plus tard avec les hooks
        // Pour l'instant, elle gère les points passifs des avant-postes contrôlés

        int pointsPerMinute = configManager.getEventsConfig()
                .getInt("advanced-settings.gang_war.scoring.outpost_hold_per_minute", 5);

        for (Map.Entry<String, String> entry : outpostControllers.entrySet()) {
            String outpostId = entry.getKey();
            String controllingGang = entry.getValue();

            // Ajouter des points passifs au gang qui contrôle
            if (gangScores.containsKey(controllingGang)) {
                gangScores.merge(controllingGang, pointsPerMinute, Integer::sum);

                if (debugEnabled) {
                    plugin.getLogger().info("§4[GANG_WAR] Avant-poste " + outpostId +
                            " génère " + pointsPerMinute + " points pour " + controllingGang);
                }
            }
        }
    }

    /**
     * Met à jour la boss bar
     */
    private void updateBossBar() {
        String timeRemaining = formatTimeRemaining();
        String leadingGang = getLeadingGang();

        String title = "§4§lGuerre des Gangs - §c§l" + timeRemaining;
        if (leadingGang != null) {
            title += " §7| §e§lLeader: §6§l" + leadingGang;
        }

        gangWarBossBar.setTitle(title);

        // Progression basée sur le temps restant
        double progress = Math.max(0.0, Math.min(1.0, (double) getRemainingSeconds() / durationSeconds));
        gangWarBossBar.setProgress(progress);
    }

    /**
     * Diffuse le classement actuel
     */
    public void broadcastLeaderboard() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§4§l⚔════ CLASSEMENT DES GANGS ════⚔");

        List<Map.Entry<String, Integer>> sortedGangs = gangScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .toList();

        if (sortedGangs.isEmpty()) {
            Bukkit.broadcastMessage("§7Aucun gang n'a encore marqué de points");
        } else {
            for (int i = 0; i < sortedGangs.size(); i++) {
                Map.Entry<String, Integer> entry = sortedGangs.get(i);
                String position = getPositionDisplay(i + 1);

                Bukkit.broadcastMessage(position + " §e" + entry.getKey() + " §7- §a" +
                        entry.getValue() + " points");
            }
        }

        Bukkit.broadcastMessage("§7Temps restant: §f" + formatTimeRemaining());
        Bukkit.broadcastMessage("");
    }

    /**
     * Retourne les scores pour la commande /event gangwar
     */
    public Map<String, Object> getScoresData(Player player) {
        Map<String, Object> data = new HashMap<>();

        // Classement des gangs
        List<Map.Entry<String, Integer>> sortedGangs = gangScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        data.put("gangRankings", sortedGangs);

        // Info du joueur
        String playerGang = playerGangs.get(player.getUniqueId());
        int playerScore = playerScores.getOrDefault(player.getUniqueId(), 0);
        GangWarStats playerStats = this.playerStats.get(player.getUniqueId());

        data.put("playerGang", playerGang);
        data.put("playerScore", playerScore);
        data.put("playerStats", playerStats);

        // Info générale
        data.put("timeRemaining", formatTimeRemaining());
        data.put("totalParticipants", participants.size());
        data.put("activeGangs", gangScores.size());

        return data;
    }

    private String getLeadingGang() {
        return gangScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String getPositionDisplay(int position) {
        return switch (position) {
            case 1 -> "§6§l1er";
            case 2 -> "§7§l2ème";
            case 3 -> "§c§l3ème";
            default -> "§f§l" + position + "ème";
        };
    }

    private String formatTimeRemaining() {
        int seconds = getRemainingSeconds();
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "min";
        } else {
            return minutes + "min";
        }
    }

    @Override
    protected void onEnd() {
        // Calculer les résultats finaux
        List<Map.Entry<String, Integer>> finalRankings = gangScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Annonces finales
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("§4§l     GUERRE DES GANGS TERMINÉE");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");

        if (!finalRankings.isEmpty()) {
            Bukkit.broadcastMessage("§6§l🏆 RÉSULTATS FINAUX 🏆");
            for (int i = 0; i < Math.min(3, finalRankings.size()); i++) {
                Map.Entry<String, Integer> entry = finalRankings.get(i);
                String position = getPositionDisplay(i + 1);
                Bukkit.broadcastMessage(position + " §e§l" + entry.getKey() +
                        " §7- §a§l" + entry.getValue() + " points");
            }
        }

        Bukkit.broadcastMessage("§7Total participants: §f" + participants.size());
        Bukkit.broadcastMessage("");

        // Distribuer les récompenses
        distributeRewards(finalRankings);

        if (debugEnabled) {
            plugin.getLogger().info("§4[GANG_WAR] Événement terminé - " + finalRankings.size() +
                    " gangs participants");
        }
    }

    private void distributeRewards(List<Map.Entry<String, Integer>> rankings) {
        // Les récompenses seront distribuées via le système de récompenses existant
        // basé sur la configuration dans events.yml

        if (debugEnabled) {
            plugin.getLogger().info("§4[GANG_WAR] Distribution des récompenses pour " +
                    rankings.size() + " gangs");
        }
    }

    @Override
    protected void onCleanup() {
        // Nettoyer la boss bar
        if (gangWarBossBar != null) {
            gangWarBossBar.removeAll();
            gangWarBossBar = null;
        }

        // Nettoyer les données
        gangScores.clear();
        playerGangs.clear();
        playerScores.clear();
        playerStats.clear();
        outpostControllers.clear();
        outpostCaptureTime.clear();

        if (debugEnabled) {
            plugin.getLogger().info("§4[GANG_WAR] Nettoyage effectué");
        }
    }

    /**
     * Quand un joueur rejoint pendant l'événement
     */
    @Override
    public void onPlayerJoin(Player player) {
        super.onPlayerJoin(player);

        if (prisonHook.isInGang(player)) {
            String gangName = prisonHook.getGangName(player);
            if (gangName != null) {
                playerGangs.put(player.getUniqueId(), gangName);
                gangScores.putIfAbsent(gangName, 0);
                playerScores.putIfAbsent(player.getUniqueId(), 0);
                playerStats.putIfAbsent(player.getUniqueId(), new GangWarStats());

                // Ajouter à la boss bar
                if (gangWarBossBar != null) {
                    gangWarBossBar.addPlayer(player);
                }

                player.sendMessage("§4§l[GANG_WAR] §cVous rejoignez la guerre avec le gang §e" + gangName + " !");
            }
        }
    }

    /**
     * Classe pour stocker les statistiques détaillées d'un joueur
     */
    public static class GangWarStats {
        public int monsterKills = 0;
        public int playerKills = 0;
        public int outpostCaptures = 0;
        public int friendlyFireKills = 0;

        public int getTotalKills() {
            return monsterKills + playerKills;
        }
    }
}