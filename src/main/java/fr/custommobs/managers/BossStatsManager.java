package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class BossStatsManager {

    private final CustomMobsPlugin plugin;

    // Stockage des statistiques par combat de boss
    private final Map<String, BossFightStats> activeBossFights; // mobId -> stats

    public BossStatsManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.activeBossFights = new HashMap<>();
    }

    /**
     * Démarre le tracking d'un combat de boss
     */
    public void startBossFight(LivingEntity boss, String mobId) {
        if (isBoss(mobId)) {
            String bossId = boss.getUniqueId().toString();
            activeBossFights.put(bossId, new BossFightStats(mobId, boss.getCustomName()));
            plugin.getLogger().info("Début du tracking pour le boss: " + mobId);
        }
    }

    /**
     * Enregistre des dégâts infligés au boss
     */
    public void recordDamageToBoss(LivingEntity boss, Player player, double damage) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addDamageToBoss(player, damage);
        }
    }

    /**
     * Enregistre des dégâts subis par un joueur
     */
    public void recordDamageFromBoss(LivingEntity boss, Player player, double damage) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addDamageFromBoss(player, damage);
        }
    }

    /**
     * Enregistre la mort d'un sbire du boss
     */
    public void recordMinionKill(LivingEntity boss, Player player, String minionType) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addMinionKill(player, minionType);
        }
    }

    /**
     * Enregistre une mort de joueur
     */
    public void recordPlayerDeath(LivingEntity boss, Player player) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);
        if (stats != null) {
            stats.addPlayerDeath(player);
        }
    }

    /**
     * Termine un combat de boss et affiche les résultats
     */
    public void endBossFight(LivingEntity boss, boolean victory) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);

        if (stats != null) {
            displayResults(stats, victory);
            distributeRewards(stats, victory);
            activeBossFights.remove(bossId);
        }
    }

    /**
     * Affiche les résultats du combat
     */
    private void displayResults(BossFightStats stats, boolean victory) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l========== RÉSULTATS DU COMBAT ==========");
        Bukkit.broadcastMessage("§e§lBoss: " + stats.bossName);
        Bukkit.broadcastMessage("§a§lRésultat: " + (victory ? "§2VICTOIRE !" : "§4DÉFAITE"));
        Bukkit.broadcastMessage("§e§lDurée: " + formatDuration(stats.getDuration()));
        Bukkit.broadcastMessage("");

        // TOP 3 DPS
        Bukkit.broadcastMessage("§c§l⚔ TOP DPS (Dégâts infligés) ⚔");
        List<Map.Entry<UUID, Double>> topDps = stats.damageToBoss.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        for (int i = 0; i < topDps.size(); i++) {
            Player player = Bukkit.getPlayer(topDps.get(i).getKey());
            if (player != null) {
                String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
                Bukkit.broadcastMessage(medal + " §e" + player.getName() + " §7- §c" +
                        String.format("%.1f", topDps.get(i).getValue()) + " dégâts");
            }
        }

        Bukkit.broadcastMessage("");

        // TOP 3 TANK (Dégâts subis)
        Bukkit.broadcastMessage("§9§l🛡 TOP TANK (Dégâts encaissés) 🛡");
        List<Map.Entry<UUID, Double>> topTank = stats.damageFromBoss.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        for (int i = 0; i < topTank.size(); i++) {
            Player player = Bukkit.getPlayer(topTank.get(i).getKey());
            if (player != null) {
                String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
                Bukkit.broadcastMessage(medal + " §e" + player.getName() + " §7- §9" +
                        String.format("%.1f", topTank.get(i).getValue()) + " dégâts subis");
            }
        }

        Bukkit.broadcastMessage("");

        // TOP 3 SLAYER (Sbires tués)
        Bukkit.broadcastMessage("§5§l⚡ TOP SLAYER (Sbires éliminés) ⚡");
        List<Map.Entry<UUID, Integer>> topSlayer = stats.minionKills.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        for (int i = 0; i < topSlayer.size(); i++) {
            Player player = Bukkit.getPlayer(topSlayer.get(i).getKey());
            if (player != null) {
                String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
                Bukkit.broadcastMessage(medal + " §e" + player.getName() + " §7- §5" +
                        topSlayer.get(i).getValue() + " sbires");
            }
        }

        // MVP GLOBAL
        Player mvp = determineMVP(stats);
        if (mvp != null) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§6§l★★★ MVP DU COMBAT ★★★");
            Bukkit.broadcastMessage("§6§l" + mvp.getName() + " §e- Champion absolu !");
        }

        Bukkit.broadcastMessage("§6§l==========================================");
        Bukkit.broadcastMessage("");
    }

    /**
     * Détermine le MVP du combat
     */
    private Player determineMVP(BossFightStats stats) {
        Map<UUID, Double> mvpScores = new HashMap<>();

        // Calcul du score MVP
        for (UUID playerId : getAllParticipants(stats)) {
            double score = 0;

            // Points pour dégâts infligés (1 point par point de dégât)
            score += stats.damageToBoss.getOrDefault(playerId, 0.0);

            // Points pour sbires tués (50 points par sbire)
            score += stats.minionKills.getOrDefault(playerId, 0) * 50;

            // Bonus de survie (100 points si pas mort)
            if (stats.playerDeaths.getOrDefault(playerId, 0) == 0) {
                score += 100;
            }

            // Malus pour mort subis (pénalité légère)
            score -= stats.playerDeaths.getOrDefault(playerId, 0) * 0.1;

            mvpScores.put(playerId, score);
        }

        return mvpScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .orElse(null);
    }

    /**
     * Distribue les récompenses selon les performances
     */
    private void distributeRewards(BossFightStats stats, boolean victory) {
        if (!victory) return; // Pas de récompenses en cas de défaite

        // Récompenses pour le MVP
        Player mvp = determineMVP(stats);
        if (mvp != null) {
            giveMVPReward(mvp, stats.mobId);
        }

        // Récompenses pour les TOP 3 DPS
        List<UUID> topDps = stats.damageToBoss.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (int i = 0; i < topDps.size(); i++) {
            Player player = Bukkit.getPlayer(topDps.get(i));
            if (player != null) {
                giveDPSReward(player, i + 1, stats.mobId);
            }
        }

        // Récompenses de participation pour tous
        for (UUID playerId : getAllParticipants(stats)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                giveParticipationReward(player, stats.mobId);
            }
        }
    }

    /**
     * Récompense MVP
     */
    private void giveMVPReward(Player player, String mobId) {
        // Titre MVP
        player.sendTitle("§6§lMVP !", "§eChampion du combat !", 10, 70, 20);

        // Items spéciaux selon le boss
        switch (mobId) {
            case "wither_boss":
                giveItem(player, new ItemStack(Material.NETHER_STAR, 3), "§5Étoile du Néant §7(MVP)");
                giveItem(player, new ItemStack(Material.BEACON, 1), "§6Phare de Victoire §7(MVP)");
                break;
            case "warden_boss":
                giveItem(player, new ItemStack(Material.ECHO_SHARD, 5), "§0Éclat des Abysses §7(MVP)");
                giveItem(player, new ItemStack(Material.SCULK_CATALYST, 2), "§8Catalyseur Sculk §7(MVP)");
                break;
            case "ravager_boss":
                giveItem(player, new ItemStack(Material.TOTEM_OF_UNDYING, 2), "§cTotem de Bravoure §7(MVP)");
                giveItem(player, new ItemStack(Material.DIAMOND, 16), "§bDiamants de Victoire §7(MVP)");
                break;
            default:
                giveItem(player, new ItemStack(Material.DIAMOND, 10), "§bRécompense MVP §7(MVP)");
        }

        player.sendMessage("§6§l[MVP] §eVous avez reçu des récompenses spéciales !");
    }

    /**
     * Récompense DPS
     */
    private void giveDPSReward(Player player, int rank, String mobId) {
        String rankName = switch (rank) {
            case 1 -> "§6Or";
            case 2 -> "§7Argent";
            case 3 -> "§cBronze";
            default -> "§eDPS";
        };

        int amount = switch (rank) {
            case 1 -> 8;
            case 2 -> 5;
            case 3 -> 3;
            default -> 1;
        };

        giveItem(player, new ItemStack(Material.GOLD_INGOT, amount), "§6Médaille " + rankName + " DPS");
        player.sendMessage("§c⚔ §eVous avez terminé " + rank + (rank == 1 ? "er" : "ème") + " en DPS !");
    }

    /**
     * Récompense de participation
     */
    private void giveParticipationReward(Player player, String mobId) {
        // XP bonus
        player.giveExp(100 + (int)(Math.random() * 100));

        // Items de base
        giveItem(player, new ItemStack(Material.EMERALD, 3), "§aGemme de Participation");

        // Chance d'item bonus
        if (Math.random() < 0.3) { // 30% de chance
            Material[] bonusItems = {Material.ENCHANTED_GOLDEN_APPLE, Material.DIAMOND, Material.NETHERITE_SCRAP};
            Material bonus = bonusItems[(int)(Math.random() * bonusItems.length)];
            giveItem(player, new ItemStack(bonus, 1), "§dBonus de Chance");
            player.sendMessage("§d✨ Bonus de chance obtenu !");
        }
    }

    /**
     * Donne un item avec un nom custom
     */
    private void giveItem(Player player, ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList("§7Obtenu lors d'un combat de boss", "§7" + new Date().toString()));
            item.setItemMeta(meta);
        }

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("§e⚠ Item droppé au sol (inventaire plein) !");
        }
    }

    /**
     * Formate la durée du combat
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Récupère tous les participants du combat
     */
    private Set<UUID> getAllParticipants(BossFightStats stats) {
        Set<UUID> participants = new HashSet<>();
        participants.addAll(stats.damageToBoss.keySet());
        participants.addAll(stats.damageFromBoss.keySet());
        participants.addAll(stats.minionKills.keySet());
        participants.addAll(stats.playerDeaths.keySet());
        return participants;
    }

    /**
     * Vérifie si c'est un boss
     */
    private boolean isBoss(String mobId) {
        return mobId.contains("boss") ||
                mobId.equals("necromancer_dark") ||
                mobId.equals("dragon_fire") ||
                mobId.equals("geode_aberration");
    }

    /**
     * Classe interne pour stocker les statistiques d'un combat
     */
    private static class BossFightStats {
        public final String mobId;
        public final String bossName;
        public final long startTime;

        public final Map<UUID, Double> damageToBoss = new HashMap<>();
        public final Map<UUID, Double> damageFromBoss = new HashMap<>();
        public final Map<UUID, Integer> minionKills = new HashMap<>();
        public final Map<UUID, Integer> playerDeaths = new HashMap<>();

        public BossFightStats(String mobId, String bossName) {
            this.mobId = mobId;
            this.bossName = bossName != null ? bossName : mobId;
            this.startTime = System.currentTimeMillis();
        }

        public void addDamageToBoss(Player player, double damage) {
            damageToBoss.merge(player.getUniqueId(), damage, Double::sum);
        }

        public void addDamageFromBoss(Player player, double damage) {
            damageFromBoss.merge(player.getUniqueId(), damage, Double::sum);
        }

        public void addMinionKill(Player player, String minionType) {
            minionKills.merge(player.getUniqueId(), 1, Integer::sum);
        }

        public void addPlayerDeath(Player player) {
            playerDeaths.merge(player.getUniqueId(), 1, Integer::sum);
        }

        public long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }
}