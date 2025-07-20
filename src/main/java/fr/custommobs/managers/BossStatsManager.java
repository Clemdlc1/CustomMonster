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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class BossStatsManager {

    private final CustomMobsPlugin plugin;

    // Stockage des statistiques par combat de boss
    private final Map<String, BossFightStats> activeBossFights; // bossUUID -> stats

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
            String bossName = boss.getCustomName() != null ? boss.getCustomName() : getBossDisplayName(mobId);

            // Vérifie si le boss n'est pas déjà tracké
            if (activeBossFights.containsKey(bossId)) {
                plugin.getLogger().warning("Boss déjà tracké: " + bossId);
                return;
            }

            activeBossFights.put(bossId, new BossFightStats(mobId, bossName));
            plugin.getLogger().info("Début du tracking pour le boss: " + mobId + " (" + bossName + ") UUID: " + bossId);

            // Annonce le début du combat
            announceBossFightStart(bossName);
        } else {
            plugin.getLogger().warning("Tentative de tracking d'un non-boss: " + mobId);
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
            plugin.getLogger().fine("Dégâts au boss enregistrés: " + player.getName() + " -> " + damage + " (Total: " + stats.damageToBoss.getOrDefault(player.getUniqueId(), 0.0) + ")");
        } else {
            plugin.getLogger().warning("Dégâts au boss non enregistrés - boss non tracké: " + bossId);
            // Tente de redémarrer le tracking
            String mobId = CustomMob.getCustomMobId(boss);
            if (isBoss(mobId)) {
                startBossFight(boss, mobId);
                recordDamageToBoss(boss, player, damage); // Réessaie
            }
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
            plugin.getLogger().fine("Dégâts du boss enregistrés: " + player.getName() + " <- " + damage);
        } else {
            plugin.getLogger().warning("Dégâts du boss non enregistrés - boss non tracké: " + bossId);
            // Tente de redémarrer le tracking
            String mobId = CustomMob.getCustomMobId(boss);
            if (isBoss(mobId)) {
                startBossFight(boss, mobId);
                recordDamageFromBoss(boss, player, damage); // Réessaie
            }
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
            plugin.getLogger().fine("Sbire tué enregistré: " + player.getName() + " -> " + minionType);
        } else {
            plugin.getLogger().warning("Mort de sbire non enregistrée - boss non tracké: " + bossId);
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
            plugin.getLogger().fine("Mort de joueur enregistrée: " + player.getName());
        } else {
            plugin.getLogger().warning("Mort de joueur non enregistrée - boss non tracké: " + bossId);
        }
    }

    /**
     * Termine un combat de boss et affiche les résultats
     */
    public void endBossFight(LivingEntity boss, boolean victory) {
        String bossId = boss.getUniqueId().toString();
        BossFightStats stats = activeBossFights.get(bossId);

        if (stats != null) {
            plugin.getLogger().info("Fin du combat de boss: " + stats.bossName + " (Victoire: " + victory + ") UUID: " + bossId);

            // Délai de 2 secondes pour que les joueurs voient d'abord la mort du boss
            new BukkitRunnable() {
                @Override
                public void run() {
                    displayResults(stats, victory);
                    if (victory) {
                        distributeRewards(stats);
                    }
                    activeBossFights.remove(bossId);
                    plugin.getLogger().info("Stats du boss supprimées: " + bossId);
                }
            }.runTaskLater(plugin, 40L); // 2 secondes
        } else {
            plugin.getLogger().warning("Aucune stats trouvées pour le boss: " + bossId);
            plugin.getLogger().info("Boss actifs trackés: " + activeBossFights.keySet());

            // Affiche quand même un message de victoire basique
            if (victory) {
                String mobId = CustomMob.getCustomMobId(boss);
                String bossName = boss.getCustomName() != null ? boss.getCustomName() : getBossDisplayName(mobId);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.broadcastMessage("");
                        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
                        Bukkit.broadcastMessage("§6§l▓▓§e§l        BOSS VAINCU !        §6§l▓▓");
                        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
                        Bukkit.broadcastMessage("§e§lBoss: " + bossName);
                        Bukkit.broadcastMessage("§a§lRésultat: §2§lVICTOIRE ! ✓");
                        Bukkit.broadcastMessage("§7§oLes statistiques n'ont pas pu être trackées");
                        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
                        Bukkit.broadcastMessage("");
                    }
                }.runTaskLater(plugin, 40L);
            }
        }
    }

    /**
     * Méthode pour débugger les boss actifs
     */
    public void debugActiveBosses() {
        plugin.getLogger().info("=== DEBUG BOSS STATS ===");
        plugin.getLogger().info("Nombre de boss actifs: " + activeBossFights.size());
        for (Map.Entry<String, BossFightStats> entry : activeBossFights.entrySet()) {
            BossFightStats stats = entry.getValue();
            plugin.getLogger().info("Boss: " + entry.getKey() + " -> " + stats.bossName + " (" + stats.mobId + ")");
            plugin.getLogger().info("  Participants: " + getAllParticipants(stats).size());
            plugin.getLogger().info("  Dégâts totaux: " + stats.damageToBoss.values().stream().mapToDouble(Double::doubleValue).sum());
        }
        plugin.getLogger().info("=========================");
    }

    /**
     * Annonce le début d'un combat de boss
     */
    private void announceBossFightStart(String bossName) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("§6§l        COMBAT DE BOSS COMMENCÉ !");
        Bukkit.broadcastMessage("§e§l           " + bossName);
        Bukkit.broadcastMessage("§6§l      Les statistiques sont trackées !");
        Bukkit.broadcastMessage("§4§l⚔═══════════════════════════════════⚔");
        Bukkit.broadcastMessage("");
    }

    /**
     * Affiche les résultats du combat - MÉTHODE AMÉLIORÉE
     */
    private void displayResults(BossFightStats stats, boolean victory) {
        Set<UUID> participants = getAllParticipants(stats);

        if (participants.isEmpty()) {
            Bukkit.broadcastMessage("§e[BOSS] Combat terminé mais aucune statistique enregistrée.");
            return;
        }

        // En-tête des résultats
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§6§l▓▓§e§l      RÉSULTATS DU COMBAT      §6§l▓▓");
        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("§e§lBoss: " + stats.bossName);
        Bukkit.broadcastMessage("§a§lRésultat: " + (victory ? "§2§lVICTOIRE ! ✓" : "§4§lDÉFAITE ✗"));
        Bukkit.broadcastMessage("§e§lDurée: " + formatDuration(stats.getDuration()));
        Bukkit.broadcastMessage("§e§lParticipants: §f" + participants.size());
        Bukkit.broadcastMessage("");

        // TOP 3 DPS
        Bukkit.broadcastMessage("§c§l⚔ TOP DPS (Dégâts infligés) ⚔");
        List<Map.Entry<UUID, Double>> topDps = stats.damageToBoss.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (topDps.isEmpty()) {
            Bukkit.broadcastMessage("  §7Aucun dégât enregistré");
        } else {
            for (int i = 0; i < topDps.size(); i++) {
                Player player = Bukkit.getPlayer(topDps.get(i).getKey());
                String playerName = player != null ? player.getName() : "Joueur Inconnu";
                String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
                Bukkit.broadcastMessage("  " + medal + " §e" + playerName + " §7- §c" +
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

        if (topTank.isEmpty()) {
            Bukkit.broadcastMessage("  §7Aucun dégât subi enregistré");
        } else {
            for (int i = 0; i < topTank.size(); i++) {
                Player player = Bukkit.getPlayer(topTank.get(i).getKey());
                String playerName = player != null ? player.getName() : "Joueur Inconnu";
                String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
                Bukkit.broadcastMessage("  " + medal + " §e" + playerName + " §7- §9" +
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

        if (topSlayer.isEmpty()) {
            Bukkit.broadcastMessage("  §7Aucun sbire éliminé");
        } else {
            for (int i = 0; i < topSlayer.size(); i++) {
                Player player = Bukkit.getPlayer(topSlayer.get(i).getKey());
                String playerName = player != null ? player.getName() : "Joueur Inconnu";
                String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
                Bukkit.broadcastMessage("  " + medal + " §e" + playerName + " §7- §5" +
                        topSlayer.get(i).getValue() + " sbires");
            }
        }

        // MVP GLOBAL
        Player mvp = determineMVP(stats);
        if (mvp != null) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§6§l★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
            Bukkit.broadcastMessage("§6§l★★★§e§l      MVP DU COMBAT      §6§l★★★");
            Bukkit.broadcastMessage("§6§l★§e§l  " + mvp.getName() + " - Champion absolu !  §6§l★");
            Bukkit.broadcastMessage("§6§l★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        }

        // Statistiques générales
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§e§l📊 STATISTIQUES GÉNÉRALES:");
        double totalDamageDealt = stats.damageToBoss.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalDamageTaken = stats.damageFromBoss.values().stream().mapToDouble(Double::doubleValue).sum();
        int totalMinionKills = stats.minionKills.values().stream().mapToInt(Integer::intValue).sum();
        int totalDeaths = stats.playerDeaths.values().stream().mapToInt(Integer::intValue).sum();

        Bukkit.broadcastMessage("  §7• Dégâts totaux infligés: §c" + String.format("%.1f", totalDamageDealt));
        Bukkit.broadcastMessage("  §7• Dégâts totaux subis: §9" + String.format("%.1f", totalDamageTaken));
        Bukkit.broadcastMessage("  §7• Sbires totaux éliminés: §5" + totalMinionKills);
        Bukkit.broadcastMessage("  §7• Morts totales: §4" + totalDeaths);

        Bukkit.broadcastMessage("§6§l▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
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

            // Malus pour morts subies (pénalité légère)
            score -= stats.playerDeaths.getOrDefault(playerId, 0) * 25;

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
    private void distributeRewards(BossFightStats stats) {
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
            case "necromancer_dark":
                giveItem(player, new ItemStack(Material.WITHER_SKELETON_SKULL, 2), "§5Crâne de Nécromancie §7(MVP)");
                giveItem(player, new ItemStack(Material.NETHERITE_INGOT, 3), "§8Lingot Maudit §7(MVP)");
                break;
            case "dragon_fire":
                giveItem(player, new ItemStack(Material.DRAGON_BREATH, 5), "§4Souffle de Dragon §7(MVP)");
                giveItem(player, new ItemStack(Material.BLAZE_ROD, 8), "§6Bâton de Flammes §7(MVP)");
                break;
            case "geode_aberration":
                giveItem(player, new ItemStack(Material.AMETHYST_SHARD, 12), "§dÉclat Cristallin §7(MVP)");
                giveItem(player, new ItemStack(Material.AMETHYST_BLOCK, 3), "§5Bloc d'Améthyste §7(MVP)");
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
        if (mobId == null) return false;

        // Boss explicites
        if (mobId.contains("boss")) {
            return true;
        }
        return false;
    }

    /**
     * Récupère le nom d'affichage du boss
     */
    private String getBossDisplayName(String mobId) {
        return switch (mobId) {
            case "wither_boss" -> "§5§lArchliche Nécrosis";
            case "warden_boss" -> "§0§lGardien des Abysses";
            case "ravager_boss" -> "§c§lDévastateur Primordial";
            case "necromancer_dark" -> "§5§lArchiliche";
            case "dragon_fire" -> "§4§lDrake Cendré";
            case "geode_aberration" -> "§d§lAberration Géodique";
            default -> "§6§lBoss Mystérieux";
        };
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