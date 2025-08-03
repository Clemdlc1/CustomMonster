package fr.custommobs.api;

import fr.prisontycoon.api.PrisonTycoonAPI;
import fr.custommobs.CustomMobsPlugin;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hook pour intégrer avec l'API de PrisonTycoon
 */
public class PrisonTycoonHook {

    private static PrisonTycoonHook instance;
    private final CustomMobsPlugin plugin;
    private PrisonTycoonAPI api;
    private boolean enabled = false;

    public PrisonTycoonHook(CustomMobsPlugin plugin) {
        this.plugin = plugin;}

    /**
     * Initialise le hook avec PrisonTycoon
     */
    public static PrisonTycoonHook initialize(CustomMobsPlugin plugin) {
        if (instance == null) {
            instance = new PrisonTycoonHook(plugin);
            instance.setupHook();
        }
        return instance;
    }

    /**
     * Récupère l'instance du hook
     */
    public static PrisonTycoonHook getInstance() {
        return instance;
    }

    /**
     * Configure le hook avec PrisonTycoon
     */
    private void setupHook() {
        Plugin prisonPlugin = plugin.getServer().getPluginManager().getPlugin("PrisonTycoon");

        if (prisonPlugin != null && prisonPlugin.isEnabled()) {
            if (PrisonTycoonAPI.isAvailable()) {
                api = PrisonTycoonAPI.getInstance();
                enabled = true;
                plugin.getLogger().info("§aHook PrisonTycoon activé avec succès!");
            } else {
                plugin.getLogger().warning("§cPrisonTycoon trouvé mais API non disponible.");
            }
        } else {
            plugin.getLogger().info("§ePrisonTycoon non trouvé - Fonctionnalités événements désactivées.");
        }
    }

    /**
     * Vérifie si le hook est activé
     */
    public boolean isEnabled() {
        return enabled && api != null;
    }

    // ===============================
    // MÉTHODES COINS
    // ===============================

    public boolean addCoins(Player player, long amount) {
        return isEnabled() && api.addCoins(player, amount);
    }

    public boolean removeCoins(Player player, long amount) {
        return isEnabled() && api.removeCoins(player, amount);
    }

    public long getCoins(Player player) {
        return isEnabled() ? api.getCoins(player) : 0;
    }

    public boolean hasCoins(Player player, long amount) {
        return isEnabled() && api.hasCoins(player.getUniqueId(), amount);
    }

    // ===============================
    // MÉTHODES TOKENS
    // ===============================

    public boolean addTokens(Player player, long amount) {
        return isEnabled() && api.addTokens(player, amount);
    }

    public boolean removeTokens(Player player, long amount) {
        return isEnabled() && api.removeTokens(player, amount);
    }

    public long getTokens(Player player) {
        return isEnabled() ? api.getTokens(player) : 0;
    }

    public boolean hasTokens(Player player, long amount) {
        return isEnabled() && api.hasTokens(player.getUniqueId(), amount);
    }

    // ===============================
    // MÉTHODES BEACONS
    // ===============================

    public boolean addBeacons(Player player, long amount) {
        return isEnabled() && api.addBeacons(player, amount);
    }

    public boolean removeBeacons(Player player, long amount) {
        return isEnabled() && api.removeBeacons(player, amount);
    }

    public long getBeacons(Player player) {
        return isEnabled() ? api.getBeacons(player) : 0;
    }

    public boolean hasBeacons(Player player, long amount) {
        return isEnabled() && api.hasBeacons(player.getUniqueId(), amount);
    }

    // ===============================
    // MÉTHODES RÉPUTATION
    // ===============================

    public int getReputation(Player player) {
        return isEnabled() ? api.getReputation(player.getUniqueId()) : 0;
    }

    public void modifyReputation(Player player, int amount, String reason) {
        if (isEnabled()) {
            api.modifyReputation(player.getUniqueId(), amount, reason);
        }
    }

    public ReputationLevel getReputationLevel(Player player) {
        if (!isEnabled()) return ReputationLevel.NEUTRAL;

        int reputation = getReputation(player);
        if (reputation >= 1000) return ReputationLevel.VERY_POSITIVE;
        if (reputation >= 500) return ReputationLevel.POSITIVE;
        if (reputation <= -1000) return ReputationLevel.VERY_NEGATIVE;
        if (reputation <= -500) return ReputationLevel.NEGATIVE;
        return ReputationLevel.NEUTRAL;
    }

    // ===============================
    // MÉTHODES ITEMS SPÉCIAUX
    // ===============================

    public ItemStack createKey(String keyType) {
        if (!isEnabled() || keyType == null || keyType.isEmpty()) return null;
        return api.createKey(keyType);
    }

    public ItemStack createCristalVierge(int niveau) {
        if (!isEnabled() || niveau <= 0) return null;
        return api.createCristalVierge(niveau);
    }

    public ItemStack createAutominer(String type) {
        if (!isEnabled() || type == null) return null;
        try {
            AutominerType autominerType = AutominerType.valueOf(type.toUpperCase());
            return api.createAutominer(autominerType);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type d'autominer invalide : " + type);
            return null;
        }
    }

    public ItemStack createContainer(int tier) {
        if (!isEnabled() || tier <= 0) return null;
        return api.createContainer(tier);
    }

    public ItemStack createBoostItem(String type, int durationMinutes, double bonusPercentage) {
        if (!isEnabled() || type == null) return null;
        try {
            BoostType boostType = BoostType.valueOf(type.toUpperCase());
            return api.createBoostItem(boostType, durationMinutes, bonusPercentage);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de boost invalide: " + type);
            return null;
        }
    }

    public ItemStack createVoucher(String type, int tier) {
        if (!isEnabled() || type == null) return null;
        try {
            VoucherType voucherType = VoucherType.valueOf(type.toUpperCase());
            return api.createVoucher(voucherType, tier);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de voucher invalide: " + type);
            return null;
        }
    }

    public ItemStack createUniqueEnchantmentBook(String enchantId) {
        if (!isEnabled() || enchantId == null) return null;
        return api.createUniqueEnchantmentBook(enchantId);
    }

    // ===============================
    // MÉTHODES GANGS
    // ===============================

    public boolean isInGang(Player player) {
        return isEnabled() && api.getPlayerGang(player.getUniqueId()) != null;
    }

    public String getGangName(Player player) {
        if (!isEnabled()) return null;
        var gang = api.getPlayerGang(player.getUniqueId());
        return gang != null ? gang.getName() : null;
    }

    public boolean isGangLeader(Player player) {
        return isEnabled() && api.isGangLeader(player.getUniqueId());
    }

    // ===============================
    // MÉTHODES BONUS GLOBAUX
    // ===============================

    public double getTotalBonusMultiplier(Player player, String category) {
        if (!isEnabled()) return 1.0;
        try {
            return api.getTotalBonusMultiplier(
                    player,
                    fr.prisontycoon.managers.GlobalBonusManager.BonusCategory.valueOf(category.toUpperCase())
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Catégorie de bonus invalide: " + category);
            return 1.0;
        }
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    public boolean isVip(Player player) {
        return isEnabled() && api.isVip(player);
    }

    public int getPrestigeLevel(Player player) {
        return isEnabled() ? api.getPrestigeLevel(player.getUniqueId()) : 0;
    }

    public void savePlayerData(Player player) {
        if (isEnabled()) {
            api.savePlayerData(player.getUniqueId());
        }
    }

    /**
     * Effectue une transaction de récompense complète
     */
    public void giveEventReward(Player player, EventReward reward) {
        if (!isEnabled() || player == null || reward == null || !reward.hasAnyReward()) return;

        UUID playerUUID = player.getUniqueId();

        // Monnaies
        if (reward.getCoins() > 0) api.addCoins(playerUUID, reward.getCoins());
        if (reward.getTokens() > 0) api.addTokens(playerUUID, reward.getTokens());
        if (reward.getBeacons() > 0) api.addBeacons(playerUUID, reward.getBeacons());
        if (reward.getExperience() > 0) api.addExperience(playerUUID, reward.getExperience());

        // Réputation
        if (reward.getReputation() != 0) {
            api.modifyReputation(playerUUID, reward.getReputation(), reward.getReputationReason());
        }

        // Niveaux de Prestige
        if (reward.getPrestigeLevels() > 0) {
            int currentLevel = api.getPrestigeLevel(playerUUID);
            api.setPrestigeLevel(playerUUID, currentLevel + reward.getPrestigeLevels());
        }

        // Items
        for (ItemStack item : reward.getItems()) {
            if (item != null) {
                // Tente d'ajouter à l'inventaire, sinon drop au sol.
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    player.sendMessage("§cVotre inventaire est plein ! Un item a été déposé à vos pieds.");
                }
            }
        }

        // Message de récompense
        if (reward.hasAnyReward()) {
            player.sendMessage("§a§l[RÉCOMPENSE] §7Vous avez reçu: " + reward.getDisplayText());
        }
    }

    /**
     * Enum pour les niveaux de réputation
     */
    public enum ReputationLevel {
        VERY_NEGATIVE("§4§lTrès Négative", -2),
        NEGATIVE("§c§lNégative", -1),
        NEUTRAL("§7§lNeutre", 0),
        POSITIVE("§a§lPositive", 1),
        VERY_POSITIVE("§2§lTrès Positive", 2);

        private final String displayName;
        private final int level;

        ReputationLevel(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getLevel() {
            return level;
        }

        public boolean isPositive() {
            return level > 0;
        }

        public boolean isNegative() {
            return level < 0;
        }

        public boolean isNeutral() {
            return level == 0;
        }
    }

    /**
     * Classe pour représenter une récompense d'événement
     */
    public static class EventReward {
        private long coins = 0;
        private long tokens = 0;
        private long beacons = 0;
        private long experience = 0;
        private int reputation = 0;
        private int prestigeLevels = 0;
        private String reputationReason = "Récompense d'événement";
        private final List<ItemStack> items = new ArrayList<>();

        public EventReward() {}

        // --- Méthodes Builder ---
        public EventReward withCoins(long amount) { this.coins = amount; return this; }
        public EventReward withTokens(long amount) { this.tokens = amount; return this; }
        public EventReward withBeacons(long amount) { this.beacons = amount; return this; }
        public EventReward withExperience(long amount) { this.experience = amount; return this; }
        public EventReward withPrestigeLevels(int levels) { this.prestigeLevels = levels; return this; }

        public EventReward withReputation(int amount, String reason) {
            this.reputation = amount;
            if (reason != null && !reason.isEmpty()) this.reputationReason = reason;
            return this;
        }

        public EventReward addItem(ItemStack item) {
            if (item != null && item.getAmount() > 0) this.items.add(item.clone());
            return this;
        }

        public EventReward addItems(ItemStack... items) {
            for (ItemStack item : items) addItem(item);
            return this;
        }

        // --- Getters ---
        public long getCoins() { return coins; }
        public long getTokens() { return tokens; }
        public long getBeacons() { return beacons; }
        public long getExperience() { return experience; }
        public int getReputation() { return reputation; }
        public int getPrestigeLevels() { return prestigeLevels; }
        public String getReputationReason() { return reputationReason; }
        public List<ItemStack> getItems() { return new ArrayList<>(items); }

        public boolean hasAnyReward() {
            return coins > 0 || tokens > 0 || beacons > 0 || experience > 0 || reputation != 0 || prestigeLevels > 0 || !items.isEmpty();
        }

        /**
         * Génère un texte descriptif de toutes les récompenses contenues.
         */
        public String getDisplayText() {
            List<String> parts = new ArrayList<>();
            if (coins > 0) parts.add("§6" + coins + " Coins");
            if (tokens > 0) parts.add("§e" + tokens + " Tokens");
            if (beacons > 0) parts.add("§b" + beacons + " Beacons");
            if (experience > 0) parts.add("§2" + experience + " Expérience");
            if (reputation > 0) parts.add("§a+" + reputation + " Réputation");
            if (reputation < 0) parts.add("§c" + reputation + " Réputation");
            if (prestigeLevels > 0) parts.add("§d+" + prestigeLevels + " Niveau(x) de Prestige");
            if (!items.isEmpty()) {
                long totalItems = items.stream().mapToLong(ItemStack::getAmount).sum();
                parts.add("§f" + totalItems + " Item(s)");
            }
            return parts.isEmpty() ? "Rien de spécial." : String.join("§7, ", parts);
        }

        /**
         * Applique un multiplicateur aux récompenses numériques (coins, tokens, beacons, xp).
         * Ne multiplie PAS la réputation, les items, les permissions ou les prestiges.
         */
        public EventReward multiply(double multiplier) {
            if (multiplier <= 0) return this;
            this.coins = (long) (this.coins * multiplier);
            this.tokens = (long) (this.tokens * multiplier);
            this.beacons = (long) (this.beacons * multiplier);
            this.experience = (long) (this.experience * multiplier);
            return this;
        }

        /**
         * Fusionne une autre récompense dans celle-ci, en additionnant les valeurs.
         */
        public EventReward merge(EventReward other) {
            if (other == null) return this;
            this.coins += other.coins;
            this.tokens += other.tokens;
            this.beacons += other.beacons;
            this.experience += other.experience;
            this.reputation += other.reputation;
            this.prestigeLevels += other.prestigeLevels;
            this.items.addAll(other.items);
            return this;
        }
    }
}