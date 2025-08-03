package fr.custommobs.events;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.events.types.*;
import fr.custommobs.api.PrisonTycoonHook;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire principal des événements programmés
 */
public class EventScheduler {

    private final CustomMobsPlugin plugin;
    private final PrisonTycoonHook prisonHook;
    private final Map<String, ScheduledEvent> scheduledEvents;
    private final Map<String, ServerEvent> activeEvents;
    private final Set<BukkitTask> schedulerTasks;
    private final EventRewardsManager rewardsManager;
    private final EventStatisticsManager statisticsManager;

    // Statistiques globales pour certains événements
    private long totalBlocksMined = 0;
    private final Object blockCountLock = new Object();

    public EventScheduler(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.prisonHook = PrisonTycoonHook.getInstance();
        this.scheduledEvents = new ConcurrentHashMap<>();
        this.activeEvents = new ConcurrentHashMap<>();
        this.schedulerTasks = new HashSet<>();
        this.rewardsManager = new EventRewardsManager(plugin, prisonHook);
        this.statisticsManager = new EventStatisticsManager(plugin);

        initializeEvents();
        startScheduler();
    }

    /**
     * Initialise tous les événements programmés
     */
    private void initializeEvents() {
        // 11.5 Contenir la Brèche - Bi-hebdomadaire (Mer, Dim 15h)
        scheduledEvents.put("breach_containment", new ScheduledEvent(
                "breach_containment",
                "Contenir la Brèche",
                Arrays.asList(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
                15, 0, // 15h00
                20 * 60, // 20 minutes
                () -> new BreachContainmentEvent(plugin, prisonHook, rewardsManager)
        ));

        // 11.6 Course au Butin - Hebdomadaire (Ven 18h)
        scheduledEvents.put("treasure_hunt", new ScheduledEvent(
                "treasure_hunt",
                "Course au Butin",
                List.of(DayOfWeek.FRIDAY),
                18, 0, // 18h00
                15 * 60, // 15 minutes
                () -> new TreasureHuntEvent(plugin, prisonHook, rewardsManager)
        ));

        // Boss quotidien (Warden) - Tous les jours à 20h
        scheduledEvents.put("daily_boss", new ScheduledEvent(
                "daily_boss",
                "Boss Quotidien",
                Arrays.asList(DayOfWeek.values()),
                20, 0, // 20h00
                30 * 60, // 30 minutes max
                () -> new DailyBossEvent(plugin, prisonHook, rewardsManager)
        ));

        // Chasse au Trésor - 2 fois par semaine (Mar, Sam 16h)
        scheduledEvents.put("treasure_search", new ScheduledEvent(
                "treasure_search",
                "Chasse au Trésor",
                Arrays.asList(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
                16, 0, // 16h00
                30 * 60, // 30 minutes
                () -> new TreasureSearchEvent(plugin, prisonHook, rewardsManager)
        ));

        // Guerre des Gangs - Weekend (Sam 19h)
        scheduledEvents.put("gang_war", new ScheduledEvent(
                "gang_war",
                "Guerre des Gangs",
                List.of(DayOfWeek.SATURDAY),
                19, 0, // 19h00
                2 * 60 * 60, // 2 heures
                () -> new GangWarEvent(plugin, prisonHook, rewardsManager)
        ));

        plugin.getLogger().info("§a" + scheduledEvents.size() + " événements programmés initialisés!");
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

        // Événements spontanés (questions chat) - toutes les 2-4 heures
        BukkitTask spontaneousTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (Math.random() < 0.3) { // 30% de chance
                    startSpontaneousEvent();
                }
            }
        }.runTaskTimer(plugin, 20L * 60 * 30, 20L * 60 * 60 * 2); // Toutes les 2h après 30min

        schedulerTasks.add(spontaneousTask);

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
                event.cleanup();
                iterator.remove();
                plugin.getLogger().info("§eÉvénement terminé: " + event.getName());
            }
        }
    }

    /**
     * Vérifie les événements déclenchés par conditions
     */
    private void checkTriggerEvents() {
        // Largages - Déclenchés par 1M blocs minés collectivement
        synchronized (blockCountLock) {
            if (totalBlocksMined >= 1000000 && !isEventActive("supply_drop")) {
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
            activeEvents.put(scheduled.getId(), event);

            // Annonce globale
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
            Bukkit.broadcastMessage("§6§l▓▓§e§l    ÉVÉNEMENT COMMENCÉ !    §6§l▓▓");
            Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
            Bukkit.broadcastMessage("§e§l» " + event.getName());
            Bukkit.broadcastMessage("§7§l» Durée: §f" + formatDuration(scheduled.getDurationSeconds()));
            Bukkit.broadcastMessage("§7§l» Type: §f" + event.getType().getDisplayName());
            Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
            Bukkit.broadcastMessage("");

            // Démarrer l'événement
            event.start();

            plugin.getLogger().info("§aÉvénement démarré: " + event.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du démarrage de l'événement " + scheduled.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Lance un largage d'urgence
     */
    private void startSupplyDrop() {
        try {
            SupplyDropEvent dropEvent = new SupplyDropEvent(plugin, prisonHook, rewardsManager);
            activeEvents.put("supply_drop", dropEvent);

            Bukkit.broadcastMessage("§c§l⚠ LARGAGE D'URGENCE DÉTECTÉ ! ⚠");
            Bukkit.broadcastMessage("§e§lObjectif collectif atteint: 1M blocs minés !");
            Bukkit.broadcastMessage("§a§lCoffres largués en mine PvP ! Dépêchez-vous !");

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
        // Ici on pourrait vérifier différents objectifs communautaires
        // Exemple: votes serveur, transactions HDV, têtes collectées, etc.

        if (!isEventActive("community_challenge")) {
            // Logique pour déterminer si un défi communautaire doit commencer
            // Basé sur les statistiques du serveur

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
        // Exemple: lance un défi si aucun n'a eu lieu dans les dernières 24h
        // et si certaines conditions sont remplies
        return Math.random() < 0.1; // 10% de chance pour l'exemple
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
            event.forceEnd();
            activeEvents.remove(eventId);
            return true;
        }
        return false;
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
     * Récupère le gestionnaire de récompenses
     */
    public EventRewardsManager getRewardsManager() {
        return rewardsManager;
    }

    /**
     * Récupère le gestionnaire de statistiques
     */
    public EventStatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    /**
     * Arrête le scheduler
     */
    public void shutdown() {
        // Arrêter toutes les tâches
        for (BukkitTask task : schedulerTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        schedulerTasks.clear();

        // Terminer tous les événements actifs
        for (ServerEvent event : activeEvents.values()) {
            event.forceEnd();
            event.cleanup();
        }
        activeEvents.clear();

        // Sauvegarder les statistiques
        statisticsManager.saveStatistics();

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