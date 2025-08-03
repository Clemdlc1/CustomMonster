package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss Quotidien (Warden)
 */
public class DailyBossEvent extends ServerEvent {
    private LivingEntity boss;
    private Location bossLocation;
    private final Map<UUID, Double> damageDealt = new HashMap<>();

    public DailyBossEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "daily_boss", "Boss Quotidien",
                EventType.COOPERATIVE, 30 * 60);
    }

    @Override
    protected void onStart() {
        bossLocation = selectBossLocation();

        Bukkit.broadcastMessage("§0§l💀 BOSS QUOTIDIEN APPARAÎT ! 💀");
        Bukkit.broadcastMessage("§8§lUn Gardien des Abysses menace le serveur !");
        Bukkit.broadcastMessage("§7§lRendez-vous en §e" + getLocationName(bossLocation));

        // Spawner le boss après 1 minute
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnBoss();
            }
        }.runTaskLater(plugin, 1200L);
    }

    private void spawnBoss() {
        boss = plugin.getMobManager().spawnCustomMob("warden_boss", bossLocation);

        if (boss != null) {
            boss.setMetadata("daily_boss", new FixedMetadataValue(plugin, true));

            Bukkit.broadcastMessage("§0§l[BOSS] §8Le Gardien des Abysses est apparu !");

            // Effets dramatiques
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
            }

            bossLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bossLocation, 100, 5, 5, 5, 0.3);
        }
    }

    public void onBossDamaged(Player attacker, double damage) {
        if (boss == null || boss.isDead()) return;

        addParticipant(attacker);
        damageDealt.merge(attacker.getUniqueId(), damage, Double::sum);

        // Message de feedback occasionnel
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            attacker.sendMessage("§0§l[BOSS] §7Dégâts total: §c" + String.format("%.1f", damageDealt.get(attacker.getUniqueId())));
        }
    }

    public void onBossKilled(Player killer) {
        Bukkit.broadcastMessage("§a§l[BOSS] §2Le Gardien des Abysses a été vaincu !");
        Bukkit.broadcastMessage("§6§l[BOSS] §eVaincu par: §a" + killer.getName());

        // Terminer l'événement immédiatement
        forceEnd();
    }

    @Override
    protected void onEnd() {
        if (boss != null && !boss.isDead()) {
            boss.getWorld().spawnParticle(Particle.SMOKE, boss.getLocation(), 50);
            boss.remove();
        }

        calculateResults();
        distributeRewards();
    }

    private void calculateResults() {
        if (damageDealt.isEmpty()) return;

        List<Map.Entry<UUID, Double>> ranking = damageDealt.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();

        Bukkit.broadcastMessage("§0§l=== TOP COMBATTANTS BOSS ===");
        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            UUID playerId = ranking.get(i).getKey();
            double damage = ranking.get(i).getValue();
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Joueur déconnecté";

            Bukkit.broadcastMessage("§7" + (i + 1) + ". §e" + name + " §7- §c" + String.format("%.1f", damage) + " dégâts");
        }
    }

    private void distributeRewards() {
        boolean bossKilled = boss != null && boss.isDead();

        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .beacons(200 + ThreadLocalRandom.current().nextInt(300))
                    .tokens(10000 + ThreadLocalRandom.current().nextInt(15000))
                    .addItem(prisonHook.createKey("legendary"));

            if (bossKilled) {
                reward.multiply(1.5);
            }

            // Bonus top contributeurs
            double damage = damageDealt.getOrDefault(playerId, 0.0);
            if (damage >= 1000) {
                reward.addItem(prisonHook.createKey("crystal"));
            }

            prisonHook.giveEventReward(player, reward);
        }
    }

    private Location selectBossLocation() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation().add(100, 10, 100);
    }

    private String getLocationName(Location location) {
        return "Arena Boss";
    }

    @Override
    protected void onCleanup() {
        damageDealt.clear();
    }
}