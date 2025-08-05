package fr.custommobs.commands;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnMobCommand implements CommandExecutor, TabCompleter {

    private final CustomMobsPlugin plugin;

    public SpawnMobCommand(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String mobId = args[0].toLowerCase();

        // Vérifie si le monstre existe
        if (!plugin.getMobManager().isMobRegistered(mobId)) {
            player.sendMessage(ChatColor.RED + "Monstre '" + mobId + "' non trouvé!");
            player.sendMessage(ChatColor.YELLOW + "Monstres disponibles: " +
                    String.join(", ", plugin.getMobManager().getRegisteredMobIds()));
            return true;
        }

        Location spawnLocation;

        if (args.length >= 4) {
            // Coordonnées spécifiées
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                spawnLocation = new Location(player.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Coordonnées invalides!");
                return true;
            }
        } else {
            // Spawn à la position du joueur
            spawnLocation = player.getLocation();
        }

        // Spawn le monstre
        LivingEntity mob = plugin.getMobManager().spawnCustomMob(mobId, spawnLocation);

        if (mob != null) {
            player.sendMessage(ChatColor.GREEN + "Monstre '" + mobId + "' spawné avec succès!");
            player.sendMessage(ChatColor.GRAY + "Position: " +
                    String.format("%.1f, %.1f, %.1f", spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ()));
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors du spawn du monstre!");
        }

        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== SpawnMob - Commandes ===");
        player.sendMessage(ChatColor.YELLOW + "/spawnmob <monstre>" + ChatColor.GRAY + " - Spawn à votre position");
        player.sendMessage(ChatColor.YELLOW + "/spawnmob <monstre> <x> <y> <z>" + ChatColor.GRAY + " - Spawn aux coordonnées");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Monstres disponibles: " +
                String.join(", ", plugin.getMobManager().getRegisteredMobIds()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(plugin.getMobManager().getRegisteredMobIds());
        } else if (args.length >= 2 && args.length <= 4 && sender instanceof Player player) {
            Location loc = player.getLocation();

            switch (args.length) {
                case 2:
                    completions.add(String.valueOf((int) loc.getX()));
                    break;
                case 3:
                    completions.add(String.valueOf((int) loc.getY()));
                    break;
                case 4:
                    completions.add(String.valueOf((int) loc.getZ()));
                    break;
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}