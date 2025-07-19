package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LootManager {

    private final CustomMobsPlugin plugin;
    private final File lootFile;
    private FileConfiguration lootConfig;
    private final Map<String, List<LootEntry>> mobLoots;

    public LootManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "loots.yml");
        this.mobLoots = new HashMap<>();
        loadLootConfig();
    }

    /**
     * Ajoute un loot à un monstre
     */
    public void addLoot(String mobId, ItemStack item, double chance) {
        mobLoots.computeIfAbsent(mobId.toLowerCase(), k -> new ArrayList<>())
                .add(new LootEntry(item.clone(), chance));
    }

    /**
     * Supprime un loot d'un monstre
     */
    public boolean removeLoot(String mobId, int index) {
        List<LootEntry> loots = mobLoots.get(mobId.toLowerCase());
        if (loots != null && index >= 0 && index < loots.size()) {
            loots.remove(index);
            return true;
        }
        return false;
    }

    /**
     * Récupère les loots d'un monstre
     */
    public List<LootEntry> getMobLoots(String mobId) {
        return mobLoots.getOrDefault(mobId.toLowerCase(), new ArrayList<>());
    }

    /**
     * Génère les loots pour un monstre mort
     */
    public void dropLoots(LivingEntity entity, String mobId) {
        List<LootEntry> loots = getMobLoots(mobId);

        for (LootEntry loot : loots) {
            if (Math.random() <= loot.getChance()) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), loot.getItem());
            }
        }
    }

    /**
     * Sauvegarde la configuration des loots
     */
    public void saveLootConfig() {
        try {
            lootConfig.set("loots.yml", null); // Reset

            for (Map.Entry<String, List<LootEntry>> entry : mobLoots.entrySet()) {
                String mobId = entry.getKey();
                List<LootEntry> loots = entry.getValue();

                for (int i = 0; i < loots.size(); i++) {
                    LootEntry loot = loots.get(i);
                    String path = "loots." + mobId + "." + i;

                    lootConfig.set(path + ".item", loot.getItem());
                    lootConfig.set(path + ".chance", loot.getChance());
                }
            }

            lootConfig.save(lootFile);
            plugin.getLogger().info("Configuration des loots sauvegardée!");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des loots: " + e.getMessage());
        }
    }

    /**
     * Charge la configuration des loots
     */
    public void loadLootConfig() {
        if (!lootFile.exists()) {
            plugin.saveResource("loots.yml", false);
        }

        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        mobLoots.clear();

        ConfigurationSection lootsSection = lootConfig.getConfigurationSection("loots.yml");
        if (lootsSection != null) {
            for (String mobId : lootsSection.getKeys(false)) {
                ConfigurationSection mobSection = lootsSection.getConfigurationSection(mobId);
                if (mobSection != null) {
                    List<LootEntry> loots = new ArrayList<>();

                    for (String index : mobSection.getKeys(false)) {
                        ConfigurationSection lootSection = mobSection.getConfigurationSection(index);
                        if (lootSection != null) {
                            ItemStack item = lootSection.getItemStack("item");
                            double chance = lootSection.getDouble("chance", 0.1);

                            if (item != null) {
                                loots.add(new LootEntry(item, chance));
                            }
                        }
                    }

                    mobLoots.put(mobId, loots);
                }
            }
        }

        plugin.getLogger().info("Configuration des loots chargée!");
    }

    /**
     * Récupère tous les monstres ayant des loots
     */
    public Set<String> getMobsWithLoots() {
        return mobLoots.keySet();
    }

    /**
     * Classe interne pour représenter un loot
     */
    public static class LootEntry {
        private final ItemStack item;
        private final double chance;

        public LootEntry(ItemStack item, double chance) {
            this.item = item;
            this.chance = Math.max(0.0, Math.min(1.0, chance)); // Entre 0 et 1
        }

        public ItemStack getItem() {
            return item.clone();
        }

        public double getChance() {
            return chance;
        }

        public String getDisplayName() {
            return item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();
        }
    }
}