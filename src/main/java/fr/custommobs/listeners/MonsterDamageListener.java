package fr.custommobs.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class MonsterDamageListener implements Listener {

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Nous récupérons l'attaquant.
        Entity damager = event.getDamager();
        // Nous récupérons l'entité qui subit les dégâts.
        Entity damaged = event.getEntity();

        // Nous vérifions si l'attaquant et la victime sont des monstres.
        if (damager instanceof Monster && damaged instanceof Monster) {
            // Si c'est le cas, nous annulons l'événement.
            event.setCancelled(true);
        }
    }
}