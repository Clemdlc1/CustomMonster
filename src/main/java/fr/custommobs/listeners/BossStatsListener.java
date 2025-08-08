package fr.custommobs.listeners;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import fr.custommobs.managers.CaveMobCounter;

public class BossStatsListener implements Listener {

    private final CustomMobsPlugin plugin;

    public BossStatsListener(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère les dégâts pour les statistiques
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        // Cas 1: Un joueur attaque un boss
        if (CustomMob.isCustomMob(damaged) && damaged instanceof LivingEntity) {
            String mobId = CustomMob.getCustomMobId(damaged);
            Player attacker = getPlayerFromDamager(damager);

            if (attacker != null && isBoss(mobId)) {
                plugin.getBossStatsManager().recordDamageToBoss(
                        (LivingEntity) damaged, attacker, event.getFinalDamage()
                );
            }
        }

        // Cas 2: Un boss attaque un joueur
        if (damaged instanceof Player && CustomMob.isCustomMob(damager)) {
            String mobId = CustomMob.getCustomMobId(damager);

            if (isBoss(mobId) && damager instanceof LivingEntity) {
                plugin.getBossStatsManager().recordDamageFromBoss(
                        (LivingEntity) damager, (Player) damaged, event.getFinalDamage()
                );
            }
        }

        // Cas 3: Un boss sbire attaque un joueur
        if (damaged instanceof Player && CustomMob.isCustomMob(damager)) {
            String mobId = CustomMob.getCustomMobId(damager);

            if (isMinionOfBoss(damager) && damager instanceof LivingEntity) {
                LivingEntity boss = findNearbyBoss((LivingEntity) damager);
                if (boss != null) {
                    plugin.getBossStatsManager().recordDamageFromBoss(
                            boss, (Player) damaged, event.getFinalDamage()
                    );
                }
            }
        }
    }

    /**
     * Gère la mort des entités
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (CustomMob.isCustomMob(entity)) {
            String mobId = CustomMob.getCustomMobId(entity);

            // Si c'est un boss qui meurt
            if (isBoss(mobId)) {
                plugin.getBossStatsManager().endBossFight(entity, true);
                plugin.getBossBarManager().removeBossBar(entity);

                plugin.getLogger().info("Boss vaincu: " + mobId);
            }

            // Si c'est un sbire qui meurt
            else if (isMinionOfBoss(entity)) {
                Player killer = entity.getKiller();
                if (killer != null) {
                    LivingEntity boss = findNearbyBoss(entity);
                    if (boss != null) {
                        plugin.getBossStatsManager().recordMinionKill(boss, killer, mobId);
                    }
                }
            }
        }
    }

    /**
     * Gère la mort des joueurs
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Trouve si le joueur est mort à cause d'un boss nearby
        // (On utilise respawn car death event peut être compliqué)
        LivingEntity nearbyBoss = findNearbyBossToPlayer(player, 50);
        if (nearbyBoss != null) {
            plugin.getBossStatsManager().recordPlayerDeath(nearbyBoss, player);
        }
    }

    /**
     * Extrait le joueur responsable des dégâts
     */
    private Player getPlayerFromDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }

        // Dégâts par projectile
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        // Dégâts par TNT ou autres entités explosive
        if (damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player) {
                return (Player) tnt.getSource();
            }
        }

        return null;
    }

    /**
     * Vérifie si c'est un boss
     */
    private boolean isBoss(String mobId) {
        if (mobId == null) return false;
        return mobId.contains("boss");
    }

    /**
     * Vérifie si c'est un sbire d'un boss
     */
    private boolean isMinionOfBoss(Entity entity) {
        // Les sbires sont marqués avec des métadonnées spéciales par les boss
        return entity.hasMetadata("boss_minion") ||
                entity.hasMetadata("shadow_clone") ||
                entity.hasMetadata("sculk_minion") ||
                entity.hasMetadata("summoned_by_boss");
    }

    /**
     * Trouve un boss à proximité d'une entité
     */
    private LivingEntity findNearbyBoss(LivingEntity entity) {
        return entity.getNearbyEntities(30, 30, 30).stream()
                .filter(e -> e instanceof LivingEntity && CustomMob.isCustomMob(e))
                .map(e -> (LivingEntity) e)
                .filter(e -> isBoss(CustomMob.getCustomMobId(e)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Trouve un boss à proximité d'un joueur
     */
    private LivingEntity findNearbyBossToPlayer(Player player, double radius) {
        return player.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity && CustomMob.isCustomMob(e))
                .map(e -> (LivingEntity) e)
                .filter(e -> isBoss(CustomMob.getCustomMobId(e)))
                .findFirst()
                .orElse(null);
    }
}