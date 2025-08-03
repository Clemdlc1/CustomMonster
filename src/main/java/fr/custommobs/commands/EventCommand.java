package fr.custommobs.commands;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.events.EventScheduler;
import fr.custommobs.events.types.GangWarEvent;
import fr.custommobs.events.types.ServerEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Commande /event pour les joueurs
 * Permet de consulter les informations des événements actifs
 */
public class EventCommand implements CommandExecutor, TabCompleter {

    private final CustomMobsPlugin plugin;
    private final EventScheduler eventScheduler;

    public EventCommand(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.eventScheduler = plugin.getEventScheduler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§l[ÉVÉNEMENT] §cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showActiveEvents(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gangwar":
                return handleGangWar(player);
            case "list":
                return showActiveEvents(player);
            case "help":
                return showHelp(player);
            default:
                player.sendMessage("§c§l[ÉVÉNEMENT] §cCommande inconnue. Utilisez §e/event help §cpour l'aide.");
                return true;
        }
    }

    private boolean handleGangWar(Player player) {
        ServerEvent gangWarEvent = eventScheduler.getActiveEvent("gang_war");

        if (gangWarEvent == null || !(gangWarEvent instanceof GangWarEvent)) {
            player.sendMessage("§c§l[ÉVÉNEMENT] §cAucune guerre des gangs n'est actuellement active!");
            player.sendMessage("§7Utilisez §e/event list §7pour voir les événements actifs.");
            return true;
        }

        GangWarEvent gangWar = (GangWarEvent) gangWarEvent;
        Map<String, Object> data = gangWar.getScoresData(player);

        showGangWarScores(player, data);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void showGangWarScores(Player player, Map<String, Object> data) {
        List<Map.Entry<String, Integer>> gangRankings = (List<Map.Entry<String, Integer>>) data.get("gangRankings");
        String playerGang = (String) data.get("playerGang");
        Integer playerScore = (Integer) data.get("playerScore");
        GangWarEvent.GangWarStats playerStats = (GangWarEvent.GangWarStats) data.get("playerStats");
        String timeRemaining = (String) data.get("timeRemaining");
        Integer totalParticipants = (Integer) data.get("totalParticipants");
        Integer activeGangs = (Integer) data.get("activeGangs");

        // En-tête
        player.sendMessage("");
        player.sendMessage("§4§l⚔═══════════════════════════════════⚔");
        player.sendMessage("§4§l           GUERRE DES GANGS");
        player.sendMessage("§4§l⚔═══════════════════════════════════⚔");

        // Informations générales
        player.sendMessage("§7Temps restant: §f" + timeRemaining);
        player.sendMessage("§7Participants: §f" + totalParticipants + " §7| Gangs actifs: §f" + activeGangs);
        player.sendMessage("");

        // Classement des gangs
        player.sendMessage("§6§l🏆 CLASSEMENT DES GANGS");
        if (gangRankings.isEmpty()) {
            player.sendMessage("§7Aucun gang n'a encore marqué de points");
        } else {
            for (int i = 0; i < Math.min(10, gangRankings.size()); i++) {
                Map.Entry<String, Integer> entry = gangRankings.get(i);
                String position = getPositionDisplay(i + 1);
                String gangName = entry.getKey();
                Integer points = entry.getValue();

                // Mettre en évidence le gang du joueur
                if (gangName.equals(playerGang)) {
                    player.sendMessage(position + " §e§l" + gangName + " §7- §a§l" + points + " points §e§l◄ VOTRE GANG");
                } else {
                    player.sendMessage(position + " §e" + gangName + " §7- §a" + points + " points");
                }
            }
        }

        player.sendMessage("");

        // Statistiques personnelles
        if (playerGang != null) {
            player.sendMessage("§a§l📊 VOS STATISTIQUES");
            player.sendMessage("§7Gang: §e" + playerGang);
            player.sendMessage("§7Votre score: §a" + playerScore + " points");

            if (playerStats != null) {
                player.sendMessage("§7Kills monstres: §f" + playerStats.monsterKills);
                player.sendMessage("§7Kills PvP: §f" + playerStats.playerKills);
                if (playerStats.outpostCaptures > 0) {
                    player.sendMessage("§7Avant-postes capturés: §f" + playerStats.outpostCaptures);
                }
                if (playerStats.friendlyFireKills > 0) {
                    player.sendMessage("§7Friendly fire: §c" + playerStats.friendlyFireKills + " §c(pénalités)");
                }
                player.sendMessage("§7Total kills: §f" + playerStats.getTotalKills());
            }

            // Position du joueur dans son gang (si on peut la calculer)
            int playerPosition = findPlayerPositionInGang(gangRankings, playerGang);
            if (playerPosition > 0) {
                player.sendMessage("§7Position de votre gang: §f" + playerPosition + getPositionSuffix(playerPosition));
            }
        } else {
            player.sendMessage("§c§l⚠ ATTENTION");
            player.sendMessage("§cVous n'êtes dans aucun gang !");
            player.sendMessage("§7Rejoignez un gang pour participer et gagner des points.");
        }

        player.sendMessage("");
        player.sendMessage("§7Utilisez §e/event gangwar §7pour actualiser les scores");
        player.sendMessage("§4§l⚔═══════════════════════════════════⚔");
    }

    private boolean showActiveEvents(Player player) {
        var activeEvents = eventScheduler.getActiveEvents();

        player.sendMessage("");
        player.sendMessage("§6§l═══════════════════════════════════");
        player.sendMessage("§6§l         ÉVÉNEMENTS ACTIFS");
        player.sendMessage("§6§l═══════════════════════════════════");

        if (activeEvents.isEmpty()) {
            player.sendMessage("§7Aucun événement actif actuellement.");
            player.sendMessage("§7Les événements sont programmés automatiquement.");
        } else {
            for (ServerEvent event : activeEvents) {
                int remaining = event.getRemainingSeconds();
                String timeStr = formatDuration(remaining);

                player.sendMessage("§a§l» " + event.getName());
                player.sendMessage("  §7Type: §f" + event.getType().getDisplayName());
                player.sendMessage("  §7Participants: §f" + event.getParticipantCount());
                player.sendMessage("  §7Temps restant: §f" + timeStr);

                // Commandes spéciales selon l'événement
                if (event instanceof GangWarEvent) {
                    player.sendMessage("  §7Commande: §e/event gangwar §7pour voir les scores");
                }

                player.sendMessage("");
            }
        }

        return true;
    }

    private boolean showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage("§6§l═══════════════════════════════════");
        player.sendMessage("§6§l            AIDE ÉVÉNEMENTS");
        player.sendMessage("§6§l═══════════════════════════════════");
        player.sendMessage("§e§l/event §7- Affiche les événements actifs");
        player.sendMessage("§e§l/event list §7- Liste tous les événements actifs");
        player.sendMessage("§e§l/event gangwar §7- Scores de la guerre des gangs");
        player.sendMessage("§e§l/event help §7- Affiche cette aide");
        player.sendMessage("");
        player.sendMessage("§7§lÉVÉNEMENTS DISPONIBLES:");
        player.sendMessage("§a• §fGuerre des Gangs §7- Combat PvP entre gangs");
        player.sendMessage("§a• §fContenir la Brèche §7- Événement coopératif");
        player.sendMessage("§a• §fBoss Quotidien §7- Combat de boss");
        player.sendMessage("§a• §fChasse au Trésor §7- Collecte d'objets");
        player.sendMessage("");
        return true;
    }

    private int findPlayerPositionInGang(List<Map.Entry<String, Integer>> rankings, String playerGang) {
        for (int i = 0; i < rankings.size(); i++) {
            if (rankings.get(i).getKey().equals(playerGang)) {
                return i + 1;
            }
        }
        return 0;
    }

    private String getPositionDisplay(int position) {
        return switch (position) {
            case 1 -> "§6§l🥇 1er";
            case 2 -> "§7§l🥈 2ème";
            case 3 -> "§c§l🥉 3ème";
            case 4 -> "§f§l4ème";
            case 5 -> "§f§l5ème";
            default -> "§8§l" + position + "ème";
        };
    }

    private String getPositionSuffix(int position) {
        return switch (position) {
            case 1 -> " 🥇";
            case 2 -> " 🥈";
            case 3 -> " 🥉";
            default -> "";
        };
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
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> options = List.of("gangwar", "list", "help");
            completions = options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}