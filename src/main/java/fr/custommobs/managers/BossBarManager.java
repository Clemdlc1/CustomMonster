package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class BossBarManager {

    private final CustomMobsPlugin plugin;
    private final Map<LivingEntity, BossBar> bossBars;

    public BossBarManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.bossBars = new HashMap<>();
    }

    /**
     * Crée une barre de boss pour une entité
     */
    public void createBossBar(LivingEntity entity, String name, BarColor color) {
        if (bossBars.containsKey(entity)) {
            removeBossBar(entity); // Supprime l'ancienne si elle existe
        }

        BossBar bossBar = Bukkit.createBossBar(name, color, BarStyle.SOLID, BarFlag.PLAY_BOSS_MUSIC);
        bossBar.setProgress(1.0);

        // Ajoute tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(entity.getWorld()) &&
                    player.getLocation().distance(entity.getLocation()) <= 50) {
                bossBar.addPlayer(player);
            }
        }

        bossBars.put(entity, bossBar);
        plugin.getLogger().info("Boss bar créée pour: " + name);

        // Démarre la mise à jour de la barre
        startBossBarUpdater(entity, bossBar);
    }

    /**
     * Supprime une barre de boss
     */
    public void removeBossBar(LivingEntity entity) {
        BossBar bossBar = bossBars.get(entity);
        if (bossBar != null) {
            bossBar.removeAll();
            bossBars.remove(entity);
            plugin.getLogger().fine("Boss bar supprimée pour l'entité: " + entity.getUniqueId());
        }
    }

    /**
     * Met à jour une barre de boss
     */
    public void updateBossBar(LivingEntity entity, double health, double maxHealth) {
        BossBar bossBar = bossBars.get(entity);
        if (bossBar != null) {
            double progress = Math.max(0.0, Math.min(1.0, health / maxHealth));
            bossBar.setProgress(progress);

            // Change la couleur selon la vie
            if (progress > 0.7) {
                bossBar.setColor(BarColor.GREEN);
            } else if (progress > 0.4) {
                bossBar.setColor(BarColor.YELLOW);
            } else if (progress > 0.2) {
                bossBar.setColor(BarColor.RED);
            } else {
                bossBar.setColor(BarColor.PURPLE);
            }
        }
    }

    /**
     * Démarre la mise à jour automatique d'une barre de boss
     */
    private void startBossBarUpdater(LivingEntity entity, BossBar bossBar) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || !bossBars.containsKey(entity)) {
                    removeBossBar(entity);
                    cancel();
                    return;
                }

                // Met à jour la barre de vie
                updateBossBar(entity, entity.getHealth(), entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue());

                // Gère les joueurs qui entrent/sortent de la zone
                for (Player player : Bukkit.getOnlinePlayers()) {
                    double distance = player.getWorld().equals(entity.getWorld()) ?
                            player.getLocation().distance(entity.getLocation()) : Double.MAX_VALUE;

                    if (distance <= 50 && !bossBar.getPlayers().contains(player)) {
                        bossBar.addPlayer(player);
                    } else if (distance > 50 && bossBar.getPlayers().contains(player)) {
                        bossBar.removePlayer(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Toutes les 0.5 secondes
    }

    /**
     * Nettoie toutes les barres de boss
     */
    public void cleanup() {
        for (BossBar bossBar : bossBars.values()) {
            bossBar.removeAll();
        }
        bossBars.clear();
    }

    /**
     * Vérifie si une entité est un boss et crée automatiquement sa barre - MÉTHODE CORRIGÉE
     */
    public void checkAndCreateBossBar(LivingEntity entity, String mobId) {
        if (isBoss(mobId)) {
            String name = getBossDisplayName(mobId, entity);
            BarColor color = getBossColor(mobId);
            createBossBar(entity, name, color);
            plugin.getLogger().info("Boss bar auto-créée pour: " + mobId);
        }
    }

    /**
     * Vérifie si c'est un boss - MÉTHODE AMÉLIORÉE
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
     * Récupère le nom d'affichage du boss - MÉTHODE AMÉLIORÉE
     */
    private String getBossDisplayName(String mobId, LivingEntity entity) {
        // Utilise le nom custom de l'entité si disponible
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }

        // Sinon utilise un nom basé sur l'ID
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
     * Récupère la couleur de la barre du boss - MÉTHODE AMÉLIORÉE
     */
    private BarColor getBossColor(String mobId) {
        return switch (mobId) {
            case "wither_boss", "necromancer_dark" -> BarColor.PURPLE;
            case "warden_boss" -> BarColor.BLUE;
            case "ravager_boss", "dragon_fire" -> BarColor.RED;
            case "geode_aberration" -> BarColor.PINK;
            default -> BarColor.WHITE;
        };
    }
}