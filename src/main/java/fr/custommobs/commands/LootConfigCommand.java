package fr.custommobs.commands;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.managers.LootManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LootConfigCommand implements CommandExecutor, TabCompleter, Listener {

    private final CustomMobsPlugin plugin;
    private final LootManager lootManager;

    public LootConfigCommand(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.lootManager = plugin.getLootManager();

        // Enregistre les événements
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                return handleAdd(player, args);
            case "list":
                return handleList(player, args);
            case "save":
                return handleSave(player);
            case "load":
                return handleLoad(player);
            default:
                sendUsage(player);
                return true;
        }
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /lootconfig add <monstre> <chance>");
            return true;
        }

        String mobId = args[1].toLowerCase();
        double chance;

        try {
            chance = Double.parseDouble(args[2]);
            if (chance < 0 || chance > 1) {
                player.sendMessage(ChatColor.RED + "La chance doit être entre 0 et 1 (0% à 100%)");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Chance invalide! Utilisez un nombre décimal (ex: 0.5 pour 50%)");
            return true;
        }

        // Vérifie si le monstre existe
        if (!plugin.getMobManager().isMobRegistered(mobId)) {
            player.sendMessage(ChatColor.RED + "Monstre '" + mobId + "' non trouvé!");
            player.sendMessage(ChatColor.YELLOW + "Monstres disponibles: " +
                    String.join(", ", plugin.getMobManager().getRegisteredMobIds()));
            return true;
        }

        // Vérifie l'item en main
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Vous devez tenir un item en main!");
            return true;
        }

        // Ajoute le loot
        lootManager.addLoot(mobId, item, chance);

        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name();

        player.sendMessage(ChatColor.GREEN + "Loot ajouté avec succès!");
        player.sendMessage(ChatColor.GRAY + "Monstre: " + ChatColor.WHITE + mobId);
        player.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + itemName);
        player.sendMessage(ChatColor.GRAY + "Chance: " + ChatColor.WHITE + (chance * 100) + "%");

        return true;
    }

    private boolean handleList(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /lootconfig list <monstre>");
            return true;
        }

        String mobId = args[1].toLowerCase();

        if (!plugin.getMobManager().isMobRegistered(mobId)) {
            player.sendMessage(ChatColor.RED + "Monstre '" + mobId + "' non trouvé!");
            return true;
        }

        openLootGUI(player, mobId);
        return true;
    }

    private boolean handleSave(Player player) {
        lootManager.saveLootConfig();
        player.sendMessage(ChatColor.GREEN + "Configuration des loots sauvegardée!");
        return true;
    }

    private boolean handleLoad(Player player) {
        lootManager.loadLootConfig();
        player.sendMessage(ChatColor.GREEN + "Configuration des loots rechargée!");
        return true;
    }

    private void openLootGUI(Player player, String mobId) {
        List<LootManager.LootEntry> loots = lootManager.getMobLoots(mobId);

        Inventory gui = Bukkit.createInventory(null, 54,
                ChatColor.DARK_PURPLE + "Loots de " + mobId + " (SHIFT+clic pour supprimer)");

        for (int i = 0; i < Math.min(loots.size(), 45); i++) {
            LootManager.LootEntry loot = loots.get(i);
            ItemStack displayItem = loot.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();

            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.YELLOW + "Chance: " + ChatColor.WHITE + (loot.getChance() * 100) + "%");
                lore.add(ChatColor.RED + "SHIFT+clic pour supprimer");
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }

            gui.setItem(i, displayItem);
        }

        // Item d'info
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "Information");
            infoMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Monstre: " + ChatColor.WHITE + mobId,
                    ChatColor.GRAY + "Nombre de loots: " + ChatColor.WHITE + loots.size(),
                    "",
                    ChatColor.YELLOW + "SHIFT+clic sur un item pour le supprimer"
            ));
            info.setItemMeta(infoMeta);
        }
        gui.setItem(53, info);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.contains("Loots de ")) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() == null || event.getCurrentItem() == null) return;

        // Extrait l'ID du monstre du titre
        String mobId = title.replace(ChatColor.DARK_PURPLE + "Loots de ", "")
                .replace(" (SHIFT+clic pour supprimer)", "");

        int slot = event.getSlot();

        if (slot == 53) return; // Item d'info

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            // Suppression
            if (lootManager.removeLoot(mobId, slot)) {
                player.sendMessage(ChatColor.GREEN + "Loot supprimé avec succès!");

                // Rafraîchit l'interface
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openLootGUI(player, mobId), 1L);
            } else {
                player.sendMessage(ChatColor.RED + "Erreur lors de la suppression du loot!");
            }
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== LootConfig - Commandes ===");
        player.sendMessage(ChatColor.YELLOW + "/lootconfig add <monstre> <chance>" + ChatColor.GRAY + " - Ajoute l'item en main comme loot");
        player.sendMessage(ChatColor.YELLOW + "/lootconfig list <monstre>" + ChatColor.GRAY + " - Ouvre l'interface des loots");
        player.sendMessage(ChatColor.YELLOW + "/lootconfig save" + ChatColor.GRAY + " - Sauvegarde la configuration");
        player.sendMessage(ChatColor.YELLOW + "/lootconfig load" + ChatColor.GRAY + " - Recharge la configuration");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Chance: 0.0 = 0%, 0.5 = 50%, 1.0 = 100%");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("add", "list", "save", "load"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("list"))) {
            completions.addAll(plugin.getMobManager().getRegisteredMobIds());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            completions.addAll(Arrays.asList("0.1", "0.25", "0.5", "0.75", "1.0"));
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}