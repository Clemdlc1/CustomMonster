package fr.custommobs.events;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.types.*;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Listener principal pour gérer les interactions des événements
 */
public class EventListener implements Listener {

    private final CustomMobsPlugin plugin;
    private final EventScheduler scheduler;

    public EventListener(CustomMobsPlugin plugin, EventScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /**
     * Gère les dégâts aux entités pour les événements
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (!(event.getDamager() instanceof Player)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();
        Player player = (Player) event.getDamager();

        // Brèche - Monstres de brèche
        if (entity.hasMetadata("breach_mob") || entity.hasMetadata("breach_boss")) {
            ServerEvent breachEvent = scheduler.getActiveEvent("breach_containment");
            if (breachEvent instanceof BreachContainmentEvent) {

            }
        }
        

        // Boss Quotidien
        if (entity.hasMetadata("daily_boss")) {
            ServerEvent bossEvent = scheduler.getActiveEvent("daily_boss");
            if (bossEvent instanceof DailyBossEvent) {
                ((DailyBossEvent) bossEvent).onBossDamaged(player, event.getFinalDamage());
            }
        }
    }

    /**
     * Gère la mort des entités pour les événements
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // Brèche - Mort d'un monstre de brèche
        if (entity.hasMetadata("breach_mob") || entity.hasMetadata("breach_boss")) {
            ServerEvent breachEvent = scheduler.getActiveEvent("breach_containment");
            if (breachEvent instanceof BreachContainmentEvent) {
                ((BreachContainmentEvent) breachEvent).onMobKilled(entity, killer);
            }
        }

        // Boss Quotidien - Mort du boss
        if (entity.hasMetadata("daily_boss")) {
            ServerEvent bossEvent = scheduler.getActiveEvent("daily_boss");
            if (bossEvent instanceof DailyBossEvent) {
                ((DailyBossEvent) bossEvent).onBossKilled(killer);
            }
        }

        // Mise à jour du compteur global de monstres pour les défis communautaires
        if (CustomMob.isCustomMob(entity)) {
            // Ajouter à un compteur global si nécessaire
        }
    }

    /**
     * Gère la mort des joueurs pour les événements
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Brèche - Marquer le joueur comme non-survivant
        ServerEvent breachEvent = scheduler.getActiveEvent("breach_containment");
        if (breachEvent instanceof BreachContainmentEvent) {
            ((BreachContainmentEvent) breachEvent).onPlayerDeath(player);
        }

        // Autres événements peuvent également gérer la mort des joueurs
        // Par exemple, pour des malus de performance ou des mécaniques spéciales
    }

    /**
     * Gère les interactions avec les blocs (coffres de trésor)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();

        // Chasse au Trésor - Coffres cachés
        if (event.getClickedBlock().hasMetadata("treasure_chest")) {
            ServerEvent treasureEvent = scheduler.getActiveEvent("treasure_search");
            if (treasureEvent instanceof TreasureSearchEvent) {
                ((TreasureSearchEvent) treasureEvent).onTreasureFound(player, event.getClickedBlock().getLocation());
            }
        }

        // Largage - Coffres de largage
        if (event.getClickedBlock().hasMetadata("supply_drop")) {
            // Le joueur récupère automatiquement le contenu
            player.sendMessage("§c§l[LARGAGE] §aVous avez récupéré un coffre de largage ! 🎉");
        }
    }

    /**
     * Gère le chat pour les questions spontanées
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Vérifier s'il y a une question spontanée active
        for (ServerEvent activeEvent : scheduler.getActiveEvents()) {
            if (activeEvent instanceof SpontaneousQuestionEvent) {
                ((SpontaneousQuestionEvent) activeEvent).onPlayerAnswer(player, message);
                break;
            }
        }
    }

    /**
     * Classe pour gérer les récompenses et le système de score global
     */
    public static class EventRewardsManager {
        private final CustomMobsPlugin plugin;
        private final PrisonTycoonHook prisonHook;

        // Système de score global pour événements futurs
        private final java.util.Map<java.util.UUID, PlayerEventStats> globalPlayerStats = new java.util.concurrent.ConcurrentHashMap<>();

        public EventRewardsManager(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook) {
            this.plugin = plugin;
            this.prisonHook = prisonHook;
        }

        /**
         * Ajoute des points au score global d'un joueur
         */
        public void addGlobalScore(Player player, int points, String eventType) {
            java.util.UUID playerId = player.getUniqueId();
            PlayerEventStats stats = globalPlayerStats.computeIfAbsent(playerId, k -> new PlayerEventStats());

            stats.addScore(points);
            stats.addEventParticipation(eventType);

            plugin.getLogger().info("Score global mis à jour pour " + player.getName() +
                    ": +" + points + " points (" + eventType + ")");
        }

        /**
         * Récupère le score global d'un joueur
         */
        public int getGlobalScore(java.util.UUID playerId) {
            PlayerEventStats stats = globalPlayerStats.get(playerId);
            return stats != null ? stats.getTotalScore() : 0;
        }

        /**
         * Récupère les statistiques complètes d'un joueur
         */
        public PlayerEventStats getPlayerStats(java.util.UUID playerId) {
            return globalPlayerStats.get(playerId);
        }

        /**
         * Récupère le classement global des joueurs
         */
        public java.util.List<java.util.Map.Entry<java.util.UUID, PlayerEventStats>> getGlobalLeaderboard(int limit) {
            return globalPlayerStats.entrySet().stream()
                    .sorted(java.util.Map.Entry.<java.util.UUID, PlayerEventStats>comparingByValue(
                            (stats1, stats2) -> Integer.compare(stats2.getTotalScore(), stats1.getTotalScore())))
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        }

        /**
         * Reset le score global d'un joueur (pour les événements spéciaux)
         */
        public void resetGlobalScore(java.util.UUID playerId) {
            globalPlayerStats.remove(playerId);
        }

        /**
         * Reset tous les scores globaux (pour les resets de saison)
         */
        public void resetAllGlobalScores() {
            globalPlayerStats.clear();
            plugin.getLogger().info("Tous les scores globaux ont été reset!");
        }

        /**
         * Sauvegarde les statistiques (peut être appelé périodiquement)
         */
        public void saveStats() {
            // Ici on pourrait implémenter la sauvegarde dans une base de données ou un fichier
            // Pour l'instant, les stats sont en mémoire uniquement
            plugin.getLogger().info("Statistiques sauvegardées pour " + globalPlayerStats.size() + " joueurs");
        }

        /**
         * Classe pour stocker les statistiques d'un joueur
         */
        public static class PlayerEventStats {
            private int totalScore = 0;
            private int eventsParticipated = 0;
            private final java.util.Map<String, Integer> eventTypeParticipations = new java.util.HashMap<>();
            private final java.util.Map<String, Integer> eventTypeScores = new java.util.HashMap<>();
            private long lastEventTime = 0;

            public void addScore(int points) {
                this.totalScore += points;
            }

            public void addEventParticipation(String eventType) {
                this.eventsParticipated++;
                this.eventTypeParticipations.merge(eventType, 1, Integer::sum);
                this.lastEventTime = System.currentTimeMillis();
            }

            public void addEventScore(String eventType, int points) {
                this.eventTypeScores.merge(eventType, points, Integer::sum);
                addScore(points);
            }

            // Getters
            public int getTotalScore() { return totalScore; }
            public int getEventsParticipated() { return eventsParticipated; }
            public java.util.Map<String, Integer> getEventTypeParticipations() { return new java.util.HashMap<>(eventTypeParticipations); }
            public java.util.Map<String, Integer> getEventTypeScores() { return new java.util.HashMap<>(eventTypeScores); }
            public long getLastEventTime() { return lastEventTime; }

            public int getParticipationsForEvent(String eventType) {
                return eventTypeParticipations.getOrDefault(eventType, 0);
            }

            public int getScoreForEvent(String eventType) {
                return eventTypeScores.getOrDefault(eventType, 0);
            }

            /**
             * Calcule un score moyen par événement
             */
            public double getAverageScore() {
                return eventsParticipated > 0 ? (double) totalScore / eventsParticipated : 0.0;
            }

            /**
             * Vérifie si le joueur est actif (a participé dans les 7 derniers jours)
             */
            public boolean isActivePlayer() {
                long weekInMillis = 7 * 24 * 60 * 60 * 1000L;
                return (System.currentTimeMillis() - lastEventTime) <= weekInMillis;
            }

            @Override
            public String toString() {
                return "PlayerEventStats{" +
                        "totalScore=" + totalScore +
                        ", eventsParticipated=" + eventsParticipated +
                        ", averageScore=" + String.format("%.1f", getAverageScore()) +
                        ", active=" + isActivePlayer() +
                        '}';
            }
        }
    }
}