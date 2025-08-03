package fr.custommobs.api;

import fr.prisontycoon.api.PrisonTycoonAPI;
import fr.custommobs.CustomMobsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
        this.plugin = plugin;
        initialize(plugin);
    }

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
        return isEnabled() ? api.createKey(keyType) : null;
    }

    public ItemStack createBoostItem(String type, int durationMinutes, double bonusPercentage) {
        if (!isEnabled()) return null;
        try {
            // Conversion du type string vers l'enum BoostType si nécessaire
            return api.createBoostItem(
                    fr.prisontycoon.boosts.BoostType.valueOf(type.toUpperCase()),
                    durationMinutes,
                    bonusPercentage
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de boost invalide: " + type);
            return null;
        }
    }

    public ItemStack createVoucher(String type, int tier) {
        if (!isEnabled()) return null;
        try {
            return api.createVoucher(
                    fr.prisontycoon.vouchers.VoucherType.valueOf(type.toUpperCase()),
                    tier
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de voucher invalide: " + type);
            return null;
        }
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
        if (!isEnabled() || reward == null) return;

        // Coins
        if (reward.getCoins() > 0) {
            addCoins(player, reward.getCoins());
        }

        // Tokens
        if (reward.getTokens() > 0) {
            addTokens(player, reward.getTokens());
        }

        // Beacons
        if (reward.getBeacons() > 0) {
            addBeacons(player, reward.getBeacons());
        }

        // Réputation
        if (reward.getReputation() != 0) {
            modifyReputation(player, reward.getReputation(), reward.getReputationReason());
        }

        // Items
        for (ItemStack item : reward.getItems()) {
            if (item != null) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
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
        private int reputation = 0;
        private String reputationReason = "Participation événement";
        private java.util.List<ItemStack> items = new java.util.ArrayList<>();

        public EventReward() {}

        public EventReward coins(long amount) {
            this.coins = amount;
            return this;
        }

        public EventReward tokens(long amount) {
            this.tokens = amount;
            return this;
        }

        public EventReward beacons(long amount) {
            this.beacons = amount;
            return this;
        }

        public EventReward reputation(int amount, String reason) {
            this.reputation = amount;
            this.reputationReason = reason;
            return this;
        }

        public EventReward addItem(ItemStack item) {
            if (item != null) {
                this.items.add(item.clone());
            }
            return this;
        }

        public EventReward addItems(ItemStack... items) {
            for (ItemStack item : items) {
                addItem(item);
            }
            return this;
        }

        // Getters
        public long getCoins() { return coins; }
        public long getTokens() { return tokens; }
        public long getBeacons() { return beacons; }
        public int getReputation() { return reputation; }
        public String getReputationReason() { return reputationReason; }
        public java.util.List<ItemStack> getItems() { return new java.util.ArrayList<>(items); }

        public boolean hasAnyReward() {
            return coins > 0 || tokens > 0 || beacons > 0 || reputation != 0 || !items.isEmpty();
        }

        public String getDisplayText() {
            java.util.List<String> parts = new java.util.ArrayList<>();

            if (coins > 0) parts.add("§6" + coins + " coins");
            if (tokens > 0) parts.add("§e" + tokens + " tokens");
            if (beacons > 0) parts.add("§b" + beacons + " beacons");
            if (reputation > 0) parts.add("§a+" + reputation + " réputation");
            if (reputation < 0) parts.add("§c" + reputation + " réputation");
            if (!items.isEmpty()) parts.add("§d" + items.size() + " item(s)");

            return String.join("§7, ", parts);
        }

        /**
         * Applique un multiplicateur à toutes les récompenses
         */
        public EventReward multiply(double multiplier) {
            this.coins = (long) (this.coins * multiplier);
            this.tokens = (long) (this.tokens * multiplier);
            this.beacons = (long) (this.beacons * multiplier);
            // Note: On ne multiplie pas la réputation ni les items
            return this;
        }

        /**
         * Fusionne cette récompense avec une autre
         */
        public EventReward merge(EventReward other) {
            if (other == null) return this;

            this.coins += other.coins;
            this.tokens += other.tokens;
            this.beacons += other.beacons;
            this.reputation += other.reputation;
            this.items.addAll(other.items);

            return this;
        }
    }
}