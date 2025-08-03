package fr.custommobs.events;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.events.types.*;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.managers.BossStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire principal des événements programmés - Version améliorée
 */
public class EventScheduler {

    private final CustomMobsPlugin plugin;
    private final PrisonTycoonHook prisonHook;
    private final EventConfigManager configManager;
    private final BossStatsManager bossStatsManager;

    private final Map<String, ScheduledEvent> scheduledEvents;
    private final Map<String, ServerEvent> activeEvents;
    private final Set<BukkitTask> schedulerTasks;
    private final EventListener.EventRewardsManager rewardsManager;
    private final EventStatisticsManager statisticsManager;

    // Statistiques globales pour certains événements
    private long totalBlocksMined = 0;
    private final Object blockCountLock = new Object();

    public EventScheduler(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.prisonHook = PrisonTycoonHook.getInstance();
        this.configManager = new EventConfigManager(plugin);
        this.bossStatsManager = plugin.getBossStatsManager();

        this.scheduledEvents = new ConcurrentHashMap<>();
        this.activeEvents = new ConcurrentHashMap<>();
        this.schedulerTasks = new HashSet<>();
        this.rewardsManager = new EventListener.EventRewardsManager(plugin, prisonHook);
        this.statisticsManager = new EventStatisticsManager(plugin);

        initializeEventsFromConfig();
        startScheduler();
    }

    /**
     * Initialise tous les événements depuis la configuration
     */
    private void initializeEventsFromConfig() {
        scheduledEvents.clear();

        for (EventConfigManager.EventScheduleConfig scheduleConfig : configManager.getAllEventSchedules()) {
            if (!scheduleConfig.isEnabled()) {
                plugin.getLogger().info("§7Événement désactivé: " + scheduleConfig.getId());
                continue;
            }

            ScheduledEvent scheduledEvent = new ScheduledEvent(
                    scheduleConfig.getId(),
                    scheduleConfig.getName(),
                    scheduleConfig.getDays(),
                    scheduleConfig.getTime().getHour(),
                    scheduleConfig.getTime().getMinute(),
                    scheduleConfig.getDuration(),
                    () -> createEventInstance(scheduleConfig.getId())
            );

            scheduledEvents.put(scheduleConfig.getId(), scheduledEvent);
            plugin.getLogger().info("§aÉvénement programmé: " + scheduleConfig.getName() +
                    " (" + scheduleConfig.getId() + ")");
        }

        plugin.getLogger().info("§a" + scheduledEvents.size() + " événements programmés initialisés depuis la config!");
    }

    /**
     * Crée une instance d'événement selon son ID
     */
    private ServerEvent createEventInstance(String eventId) {
        return switch (eventId) {
            case "breach_containment" -> new BreachContainmentEvent(plugin, prisonHook, rewardsManager, configManager, bossStatsManager);
            case "treasure_hunt" -> new TreasureHuntEvent(plugin, prisonHook, rewardsManager);
            case "daily_boss" -> new DailyBossEvent(plugin, prisonHook, rewardsManager, configManager, bossStatsManager);
            case "treasure_search" -> new TreasureSearchEvent(plugin, prisonHook, rewardsManager);
            case "gang_war" -> new GangWarEvent(plugin, prisonHook, rewardsManager);
            default -> {
                plugin.getLogger().warning("Type d'événement inconnu: " + eventId);
                yield null;
            }
        };
    }

    /**
     * Démarre le système de planification
     */
    private void startScheduler() {
        // Vérification toutes les minutes
        BukkitTask mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkScheduledEvents();
                manageActiveEvents();
                checkTriggerEvents();
            }
        }.runTaskTimer(plugin, 20L, 1200L); // Toutes les minutes

        schedulerTasks.add(mainTask);

        // Événements spontanés (questions chat) - selon config
        boolean spontaneousEnabled = configManager.getEventsConfig().getBoolean("advanced-settings.spontaneous-events.enabled", true);
        if (spontaneousEnabled) {
            int intervalMinutes = configManager.getEventsConfig().getInt("advanced-settings.spontaneous-events.interval-minutes", 120);

            BukkitTask spontaneousTask = new BukkitRunnable() {
                @Override
                public void run() {
                    double chance = configManager.getEventsConfig().getDouble("advanced-settings.spontaneous-events.chance", 0.3);
                    if (Math.random() < chance) {
                        startSpontaneousEvent();
                    }
                }
            }.runTaskTimer(plugin, 20L * 60 * 30, 20L * 60 * intervalMinutes);

            schedulerTasks.add(spontaneousTask);
        }

        // Sauvegarde des statistiques - toutes les 10 minutes
        BukkitTask saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                statisticsManager.saveStatistics();
            }
        }.runTaskTimer(plugin, 20L * 60 * 10, 20L * 60 * 10);

        schedulerTasks.add(saveTask);

        plugin.getLogger().info("§aEvent Scheduler démarré avec " + schedulerTasks.size() + " tâches!");
    }

    /**
     * Vérifie si des événements programmés doivent commencer
     */
    private void checkScheduledEvents() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek currentDay = now.getDayOfWeek();
        int hour = now.getHour();
        int minute = now.getMinute();

        for (ScheduledEvent scheduled : scheduledEvents.values()) {
            if (scheduled.shouldStart(currentDay, hour, minute) && !isEventActive(scheduled.getId())) {
                startEvent(scheduled);
            }
        }
    }

    /**
     * Gère les événements actifs
     */
    private void manageActiveEvents() {
        Iterator<Map.Entry<String, ServerEvent>> iterator = activeEvents.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, ServerEvent> entry = iterator.next();
            ServerEvent event = entry.getValue();

            if (event.isFinished() || event.isCancelled()) {
                // Intégration spéciale pour les boss
                if (event instanceof DailyBossEvent && bossStatsManager != null) {
                    handleBossEventEnd((DailyBossEvent) event);
                }

                event.cleanup();
                iterator.remove();
                plugin.getLogger().info("§eÉvénement terminé: " + event.getName());
            }
        }
    }

    /**
     * Gère la fin d'un événement de boss pour l'intégration avec BossStatsManager
     */
    private void handleBossEventEnd(DailyBossEvent bossEvent) {
        try {
            // Récupérer les statistiques du boss depuis l'événement
            if (bossEvent.getBoss() != null && !bossEvent.getBoss().isDead()) {
                // Boss non tué - timeout
                plugin.getLogger().info("§6[BOSS EVENT] Boss timeout - pas de victoire");
            } else if (bossEvent.getBoss() != null) {
                // Boss tué - récupérer les stats finales
                String bossUUID = bossEvent.getBoss().getUniqueId().toString();
                plugin.getLogger().info("§6[BOSS EVENT] Boss vaincu - statistiques finales disponibles");

                // Forcer l'affichage des résultats si pas déjà fait
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (bossStatsManager != null) {
                        bossStatsManager.debugActiveBosses();
                    }
                }, 20L);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'intégration boss stats: " + e.getMessage());
        }
    }

    /**
     * Vérifie les événements déclenchés par conditions
     */
    private void checkTriggerEvents() {
        // Largages - Déclenchés par seuil de blocs minés (configurable)
        int blockThreshold = configManager.getEventsConfig().getInt("trigger-events.supply-drop.block-threshold", 1000000);

        synchronized (blockCountLock) {
            if (totalBlocksMined >= blockThreshold && !isEventActive("supply_drop")) {
                totalBlocksMined = 0; // Reset compteur
                startSupplyDrop();
            }
        }

        // Défis communautaires - Vérifier les conditions
        checkCommunityChallenge();
    }

    /**
     * Lance un événement programmé
     */
    private void startEvent(ScheduledEvent scheduled) {
        try {
            ServerEvent event = scheduled.createEvent();
            if (event == null) {
                plugin.getLogger().severe("§cImpossible de créer l'événement: " + scheduled.getId());
                return;
            }

            activeEvents.put(scheduled.getId(), event);

            // Annonce globale selon config
            boolean announceStart = configManager.getEventsConfig().getBoolean("advanced-settings.notifications.announce-start", true);
            if (announceStart) {
                broadcastEventStart(event, scheduled);
            }

            // Démarrer l'événement
            event.start();

            plugin.getLogger().info("§aÉvénement démarré: " + event.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du démarrage de l'événement " + scheduled.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Annonce le démarrage d'un événement
     */
    private void broadcastEventStart(ServerEvent event, ScheduledEvent scheduled) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§6§l▓▓§e§l    ÉVÉNEMENT COMMENCÉ !    §6§l▓▓");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§e§l» " + event.getName());
        Bukkit.broadcastMessage("§7§l» Durée: §f" + formatDuration(scheduled.getDurationSeconds()));
        Bukkit.broadcastMessage("§7§l» Type: §f" + event.getType().getDisplayName());

        // Ajouter des informations spécifiques selon le type d'événement
        if (event instanceof DailyBossEvent) {
            String arenaName = ((DailyBossEvent) event).getArenaDisplayName();
            if (arenaName != null) {
                Bukkit.broadcastMessage("§7§l» Localisation: §f" + arenaName);
            }
        }

        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("");
    }

    /**
     * Lance un largage d'urgence
     */
    private void startSupplyDrop() {
        try {
            SupplyDropEvent dropEvent = new SupplyDropEvent(plugin, prisonHook, rewardsManager);
            activeEvents.put("supply_drop", dropEvent);

            Bukkit.broadcastMessage("§c§l⚠ LARGAGE D'URGENCE DÉTECTÉ ! ⚠");
            Bukkit.broadcastMessage("§e§lObjectif collectif atteint!");
            Bukkit.broadcastMessage("§a§lCoffres largués ! Dépêchez-vous !");

            dropEvent.start();

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur largage: " + e.getMessage());
        }
    }

    /**
     * Lance un événement spontané (question chat)
     */
    private void startSpontaneousEvent() {
        try {
            SpontaneousQuestionEvent questionEvent = new SpontaneousQuestionEvent(plugin, prisonHook, rewardsManager);
            activeEvents.put("spontaneous_" + System.currentTimeMillis(), questionEvent);

            questionEvent.start();

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur événement spontané: " + e.getMessage());
        }
    }

    /**
     * Vérifie et gère les défis communautaires
     */
    private void checkCommunityChallenge() {
        boolean challengeEnabled = configManager.getEventsConfig().getBoolean("community-challenges.enabled", false);

        if (challengeEnabled && !isEventActive("community_challenge")) {
            if (shouldStartCommunityChallenge()) {
                CommunityChallenge challenge = new CommunityChallenge(plugin, prisonHook, rewardsManager);
                activeEvents.put("community_challenge", challenge);
                challenge.start();
            }
        }
    }

    /**
     * Détermine si un défi communautaire doit commencer
     */
    private boolean shouldStartCommunityChallenge() {
        double chance = configManager.getEventsConfig().getDouble("community-challenges.trigger-chance", 0.1);
        return Math.random() < chance;
    }

    // ===============================
    // MÉTHODES PUBLIQUES
    // ===============================

    /**
     * Ajoute des blocs minés au compteur global
     */
    public void addMinedBlocks(long count) {
        synchronized (blockCountLock) {
            totalBlocksMined += count;
        }
    }

    /**
     * Force le démarrage d'un événement
     */
    public boolean forceStartEvent(String eventId) {
        ScheduledEvent scheduled = scheduledEvents.get(eventId);
        if (scheduled != null && !isEventActive(eventId)) {
            plugin.getLogger().info("§6[FORCE START] Démarrage forcé de l'événement: " + eventId);
            startEvent(scheduled);
            return true;
        }
        return false;
    }

    /**
     * Force l'arrêt d'un événement
     */
    public boolean forceStopEvent(String eventId) {
        ServerEvent event = activeEvents.get(eventId);
        if (event != null) {
            plugin.getLogger().info("§6[FORCE STOP] Arrêt forcé de l'événement: " + eventId);

            // Intégration spéciale pour les boss
            if (event instanceof DailyBossEvent && bossStatsManager != null) {
                handleBossEventEnd((DailyBossEvent) event);
            }

            event.forceEnd();
            activeEvents.remove(eventId);
            return true;
        }
        return false;
    }

    /**
     * Recharge les événements depuis la configuration
     */
    public void reloadEvents() {
        plugin.getLogger().info("§6[RELOAD] Rechargement des événements...");

        // Sauvegarder les événements actifs
        Map<String, ServerEvent> currentlyActive = new HashMap<>(activeEvents);

        // Recharger la config
        configManager.loadEventsConfig();

        // Réinitialiser les événements programmés
        initializeEventsFromConfig();

        // Restaurer les événements actifs si ils sont toujours dans la config
        for (Map.Entry<String, ServerEvent> entry : currentlyActive.entrySet()) {
            if (scheduledEvents.containsKey(entry.getKey())) {
                activeEvents.put(entry.getKey(), entry.getValue());
            } else {
                // Événement supprimé de la config - l'arrêter
                entry.getValue().forceEnd();
                plugin.getLogger().info("§e[RELOAD] Événement supprimé de la config: " + entry.getKey());
            }
        }

        plugin.getLogger().info("§a[RELOAD] Rechargement terminé!");
    }

    /**
     * Vérifie si un événement est actif
     */
    public boolean isEventActive(String eventId) {
        return activeEvents.containsKey(eventId);
    }

    /**
     * Récupère un événement actif
     */
    public ServerEvent getActiveEvent(String eventId) {
        return activeEvents.get(eventId);
    }

    /**
     * Récupère tous les événements actifs
     */
    public Collection<ServerEvent> getActiveEvents() {
        return new ArrayList<>(activeEvents.values());
    }

    /**
     * Récupère les événements programmés
     */
    public Collection<ScheduledEvent> getScheduledEvents() {
        return new ArrayList<>(scheduledEvents.values());
    }

    /**
     * Récupère le prochain événement programmé
     */
    public ScheduledEvent getNextScheduledEvent() {
        LocalDateTime now = LocalDateTime.now();
        ScheduledEvent nextEvent = null;
        long minTimeUntil = Long.MAX_VALUE;

        for (ScheduledEvent scheduled : scheduledEvents.values()) {
            long timeUntil = scheduled.getTimeUntilNext(now);
            if (timeUntil < minTimeUntil) {
                minTimeUntil = timeUntil;
                nextEvent = scheduled;
            }
        }

        return nextEvent;
    }

    /**
     * Récupère le gestionnaire de configuration
     */
    public EventConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Récupère le gestionnaire de récompenses
     */
    public EventListener.EventRewardsManager getRewardsManager() {
        return rewardsManager;
    }

    /**
     * Récupère le gestionnaire de statistiques
     */
    public EventStatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    /**
     * Récupère le gestionnaire de stats de boss
     */
    public BossStatsManager getBossStatsManager() {
        return bossStatsManager;
    }

    /**
     * Arrête le scheduler
     */
    public void shutdown() {
        plugin.getLogger().info("§6[SHUTDOWN] Arrêt du scheduler...");

        // Arrêter toutes les tâches
        for (BukkitTask task : schedulerTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        schedulerTasks.clear();

        // Terminer tous les événements actifs avec intégration boss
        for (ServerEvent event : activeEvents.values()) {
            if (event instanceof DailyBossEvent && bossStatsManager != null) {
                handleBossEventEnd((DailyBossEvent) event);
            }
            event.forceEnd();
            event.cleanup();
        }
        activeEvents.clear();

        // Sauvegarder les statistiques
        statisticsManager.saveStatistics();
        configManager.saveEventsConfig();

        plugin.getLogger().info("§cEvent Scheduler arrêté.");
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return String.format("%dh%02dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    /**
     * Classe pour représenter un événement programmé
     */
    public static class ScheduledEvent {
        private final String id;
        private final String name;
        private final List<DayOfWeek> days;
        private final int hour;
        private final int minute;
        private final int durationSeconds;
        private final EventFactory eventFactory;
        private LocalDateTime lastRun;

        public ScheduledEvent(String id, String name, List<DayOfWeek> days, int hour, int minute,
                              int durationSeconds, EventFactory eventFactory) {
            this.id = id;
            this.name = name;
            this.days = new ArrayList<>(days);
            this.hour = hour;
            this.minute = minute;
            this.durationSeconds = durationSeconds;
            this.eventFactory = eventFactory;
        }

        public boolean shouldStart(DayOfWeek currentDay, int currentHour, int currentMinute) {
            if (!days.contains(currentDay)) return false;
            if (currentHour != hour || currentMinute != minute) return false;

            // Éviter de relancer le même événement dans la même minute
            LocalDateTime now = LocalDateTime.now();
            if (lastRun != null && lastRun.toLocalDate().equals(now.toLocalDate()) &&
                    lastRun.getHour() == hour && lastRun.getMinute() == minute) {
                return false;
            }

            return true;
        }

        public ServerEvent createEvent() {
            lastRun = LocalDateTime.now();
            return eventFactory.create();
        }

        public long getTimeUntilNext(LocalDateTime from) {
            LocalDateTime next = getNextOccurrence(from);
            return java.time.Duration.between(from, next).getSeconds();
        }

        private LocalDateTime getNextOccurrence(LocalDateTime from) {
            LocalDateTime candidate = from.toLocalDate().atTime(hour, minute);

            // Si c'est aujourd'hui mais l'heure est passée, ou ce n'est pas un jour valide
            if (candidate.isBefore(from) || !days.contains(candidate.getDayOfWeek())) {
                candidate = candidate.plusDays(1);
            }

            // Chercher le prochain jour valide
            while (!days.contains(candidate.getDayOfWeek())) {
                candidate = candidate.plusDays(1);
            }

            return candidate;
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public List<DayOfWeek> getDays() { return new ArrayList<>(days); }
        public int getHour() { return hour; }
        public int getMinute() { return minute; }
        public int getDurationSeconds() { return durationSeconds; }
        public LocalDateTime getLastRun() { return lastRun; }

        public String getScheduleDisplay() {
            String daysStr = days.stream()
                    .map(day -> day.name().substring(0, 3))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Aucun");
            return String.format("%s à %02d:%02d", daysStr, hour, minute);
        }
    }

    /**
     * Interface pour créer des événements
     */
    @FunctionalInterface
    public interface EventFactory {
        ServerEvent create();
    }
}