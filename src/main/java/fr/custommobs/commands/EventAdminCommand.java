package fr.custommobs.commands;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.events.EventScheduler;
import fr.custommobs.events.types.ServerEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EventAdminCommand implements CommandExecutor, TabCompleter {

    private final CustomMobsPlugin plugin;
    private final EventScheduler eventScheduler;

    public EventAdminCommand(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.eventScheduler = plugin.getEventScheduler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cVous n'avez pas la permission d'administrer les événements!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
            case "force":
                return handleStart(sender, args);
            case "stop":
                return handleStop(sender, args);
            case "list":
                return handleList(sender);
            case "active":
                return handleActive(sender);
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cUsage: /eventadmin start <eventId>");
            sender.sendMessage("§7Événements disponibles: §e" + getAvailableEvents());
            return true;
        }

        String eventId = args[1].toLowerCase();

        if (eventScheduler.isEventActive(eventId)) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cL'événement §e" + eventId + " §cest déjà actif!");
            return true;
        }

        boolean started = eventScheduler.forceStartEvent(eventId);
        if (started) {
            sender.sendMessage("§a§l[ÉVÉNEMENTS] §aÉvénement §e" + eventId + " §adémarré avec succès!");

            // Log l'action
            plugin.getLogger().info("§6[ADMIN] " + sender.getName() + " a forcé le démarrage de l'événement: " + eventId);
        } else {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cImpossible de démarrer l'événement §e" + eventId + "§c!");
            sender.sendMessage("§7Vérifiez que l'ID est correct: §e" + getAvailableEvents());
        }

        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cUsage: /eventadmin stop <eventId>");
            sender.sendMessage("§7Événements actifs: §e" + getActiveEvents());
            return true;
        }

        String eventId = args[1].toLowerCase();

        boolean stopped = eventScheduler.forceStopEvent(eventId);
        if (stopped) {
            sender.sendMessage("§a§l[ÉVÉNEMENTS] §aÉvénement §e" + eventId + " §aarrêté avec succès!");

            // Log l'action
            plugin.getLogger().info("§6[ADMIN] " + sender.getName() + " a forcé l'arrêt de l'événement: " + eventId);
        } else {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cImpossible d'arrêter l'événement §e" + eventId + "§c!");
            sender.sendMessage("§7Événements actifs: §e" + getActiveEvents());
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§6§l           ÉVÉNEMENTS PROGRAMMÉS");
        sender.sendMessage("§6§l═══════════════════════════════════");

        eventScheduler.getScheduledEvents().forEach(scheduled -> {
            boolean isActive = eventScheduler.isEventActive(scheduled.getId());
            String status = isActive ? "§a§lACTIF" : "§7Programmé";

            sender.sendMessage("§e§l» " + scheduled.getName() + " §7(" + scheduled.getId() + ")");
            sender.sendMessage("  §7Statut: " + status);
            sender.sendMessage("  §7Planning: §f" + scheduled.getScheduleDisplay());
            sender.sendMessage("  §7Durée: §f" + formatDuration(scheduled.getDurationSeconds()));
            sender.sendMessage("");
        });

        return true;
    }

    private boolean handleActive(CommandSender sender) {
        var activeEvents = eventScheduler.getActiveEvents();

        if (activeEvents.isEmpty()) {
            sender.sendMessage("§e§l[ÉVÉNEMENTS] §7Aucun événement actif actuellement.");
            return true;
        }

        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§6§l           ÉVÉNEMENTS ACTIFS");
        sender.sendMessage("§6§l═══════════════════════════════════");

        activeEvents.forEach(event -> {
            int remaining = event.getRemainingSeconds();

            sender.sendMessage("§a§l» " + event.getName() + " §7(" + event.getId() + ")");
            sender.sendMessage("  §7Type: §f" + event.getType().getDisplayName());
            sender.sendMessage("  §7Participants: §f" + event.getParticipantCount());
            sender.sendMessage("  §7Temps restant: §f" + formatDuration(remaining));
            sender.sendMessage("");
        });

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        try {
            // Recharger la config
            plugin.reloadConfig();
            plugin.getConfigManager().loadEventSchedules();

            sender.sendMessage("§a§l[ÉVÉNEMENTS] §aConfiguration rechargée avec succès!");
            plugin.getLogger().info("§6[ADMIN] " + sender.getName() + " a rechargé la configuration des événements");
        } catch (Exception e) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cErreur lors du rechargement: " + e.getMessage());
            plugin.getLogger().severe("Erreur lors du rechargement de config par " + sender.getName() + ": " + e.getMessage());
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cUsage: /eventadmin info <eventId>");
            return true;
        }

        String eventId = args[1].toLowerCase();
        ServerEvent event = eventScheduler.getActiveEvent(eventId);

        if (event == null) {
            sender.sendMessage("§c§l[ÉVÉNEMENTS] §cÉvénement §e" + eventId + " §cnon trouvé ou inactif!");
            return true;
        }

        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§6§l           INFORMATIONS ÉVÉNEMENT");
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§e§lNom: §f" + event.getName());
        sender.sendMessage("§e§lID: §f" + event.getId());
        sender.sendMessage("§e§lType: §f" + event.getType().getDisplayName());
        sender.sendMessage("§e§lStatut: §a§lACTIF");
        sender.sendMessage("§e§lParticipants: §f" + event.getParticipantCount());
        sender.sendMessage("§e§lTemps restant: §f" + formatDuration(event.getRemainingSeconds()));
        sender.sendMessage("§e§lDébut: §f" + event.getStartTime().toString());
        sender.sendMessage("§e§lFin prévue: §f" + event.getEndTime().toString());

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§6§l         ADMINISTRATION ÉVÉNEMENTS");
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§e§l/eventadmin start <eventId> §7- Force le démarrage d'un événement");
        sender.sendMessage("§e§l/eventadmin stop <eventId> §7- Force l'arrêt d'un événement");
        sender.sendMessage("§e§l/eventadmin list §7- Liste tous les événements programmés");
        sender.sendMessage("§e§l/eventadmin active §7- Liste les événements actifs");
        sender.sendMessage("§e§l/eventadmin info <eventId> §7- Informations détaillées");
        sender.sendMessage("§e§l/eventadmin reload §7- Recharge la configuration");
        sender.sendMessage("");
        sender.sendMessage("§7Événements disponibles: §e" + getAvailableEvents());
    }

    private String getAvailableEvents() {
        return eventScheduler.getScheduledEvents().stream()
                .map(EventScheduler.ScheduledEvent::getId)
                .collect(Collectors.joining("§7, §e"));
    }

    private String getActiveEvents() {
        return eventScheduler.getActiveEvents().stream()
                .map(ServerEvent::getId)
                .collect(Collectors.joining("§7, §e"));
    }

    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh%02dm%02ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm%02ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Stream.of("start", "stop", "list", "active", "reload", "info")
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "start":
                case "force":
                    return eventScheduler.getScheduledEvents().stream()
                            .map(EventScheduler.ScheduledEvent::getId)
                            .filter(id -> !eventScheduler.isEventActive(id))
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "stop":
                case "info":
                    return eventScheduler.getActiveEvents().stream()
                            .map(ServerEvent::getId)
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}