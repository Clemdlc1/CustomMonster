package fr.custommobs.listeners;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class MobControlListener implements Listener {

    private final CustomMobsPlugin plugin;

    public MobControlListener(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Empêche les monstres de faire du grief (détruire des blocs)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // Bloque les explosions qui détruisent les blocs selon la config
        if (plugin.getConfig().getBoolean("prevent-mob-grief", true)) {

            // Bloque toutes les explosions de monstres
            if (entity instanceof Monster || entity instanceof Creeper ||
                    entity instanceof Wither || entity instanceof WitherSkull ||
                    entity instanceof Fireball || entity instanceof TNTPrimed) {

                // Garde les dégâts aux entités mais retire la destruction de blocs
                event.blockList().clear();

                plugin.getLogger().fine("Explosion bloquée de: " + entity.getType());
            }

            // Cas spécial pour nos monstres custom
            if (CustomMob.isCustomMob(entity)) {
                String mobId = CustomMob.getCustomMobId(entity);

                // Autorise certaines explosions custom selon la config
                if (!plugin.getConfig().getBoolean("custom-mobs.allow-block-damage", false)) {
                    event.blockList().clear();
                }
            }
        }
    }

    /**
     * Contrôle le ciblage des monstres custom
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();

        if (!CustomMob.isCustomMob(entity)) {
            return;
        }

        // Empêche les monstres custom de se cibler entre eux
        if (event.getTarget() instanceof LivingEntity target) {

            if (CustomMob.isCustomMob(target)) {
                event.setCancelled(true);
                return;
            }

            // Empêche de cibler les joueurs en créatif/spectateur
            if (target instanceof Player player) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                        player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Gère la persistence des monstres custom lors du déchargement des chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Empêche le déchargement des chunks contenant des monstres custom importants
        for (Entity entity : event.getChunk().getEntities()) {
            if (CustomMob.isCustomMob(entity)) {
                String mobId = CustomMob.getCustomMobId(entity);

                // Les boss et monstres avancés ne disparaissent pas
                if (mobId != null && (mobId.contains("dragon") ||
                        mobId.contains("necromancer") ||
                        mobId.contains("titan"))) {

                    // Force la persistence
                    if (entity instanceof LivingEntity) {
                        ((LivingEntity) entity).setRemoveWhenFarAway(false);
                    }
                }
            }
        }
    }

    /**
     * Nettoie les entités inutiles pour l'optimisation
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityTarget(org.bukkit.event.entity.EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        // Limite le nombre d'entités non-vivantes pour l'optimisation
        if (!(entity instanceof LivingEntity)) {

            // Limite les projectiles
            if (entity instanceof Projectile) {
                long projectileCount = entity.getWorld().getEntities().stream()
                        .filter(e -> e instanceof Projectile)
                        .count();

                if (projectileCount > plugin.getConfig().getInt("optimization.max-projectiles", 100)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Limite les items au sol
            if (entity instanceof Item) {
                long itemCount = entity.getWorld().getEntities().stream()
                        .filter(e -> e instanceof Item)
                        .count();

                if (itemCount > plugin.getConfig().getInt("optimization.max-items", 200)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
