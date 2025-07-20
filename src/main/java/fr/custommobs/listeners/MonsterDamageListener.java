package fr.custommobs.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class MonsterDamageListener implements Listener {

    /**
     * Cet événement empêche les créatures hostiles de s'infliger des dégâts entre elles,
     * que ce soit directement ou via des projectiles.
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Nous récupérons l'entité qui subit les dégâts.
        Entity damaged = event.getEntity();

        // Nous vérifions si la victime est une créature hostile (inclut Monster, Witch, Blaze, etc.).
        if (damaged instanceof Monster) {
            Entity damager = event.getDamager();
            Entity damagerSource = null;

            // Si l'attaquant est un projectile (ex: une flèche, une boule de feu de Blaze).
            if (damager instanceof Projectile) {
                Projectile projectile = (Projectile) damager;
                ProjectileSource shooter = projectile.getShooter();

                // Nous vérifions si l'entité qui a tiré le projectile est bien une entité.
                if (shooter instanceof Entity) {
                    damagerSource = (Entity) shooter;
                }
            } else {
                // L'attaquant est une entité qui attaque directement au corps à corps.
                damagerSource = damager;
            }

            // Nous vérifions si la source des dégâts (l'attaquant ou le tireur) est également une créature hostile.
            if (damagerSource instanceof Monster) {
                // Si la victime et l'attaquant sont tous deux hostiles, nous annulons les dégâts.
                event.setCancelled(true);
            }
        }
    }

    /**
     * Cet événement empêche les Golems de Fer de prendre pour cible les créatures hostiles.
     * Le golem restera donc passif envers elles.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        // Nous vérifions si l'entité qui cherche une cible est un Golem de Fer.
        if (event.getEntity() instanceof IronGolem) {
            // Nous vérifions si la cible potentielle est une créature hostile.
            if (event.getTarget() instanceof Monster) {
                // Si c'est le cas, nous annulons l'événement de ciblage.
                event.setCancelled(true);
            }
        }
    }
}