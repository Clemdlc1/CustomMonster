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

        // Nous vérifions si l'attaquant est un monstre. [2]
        if (damager instanceof Monster) {
            // Si c'est un monstre, nous annulons l'événement. [9, 16]
            event.setCancelled(true);
        }
    }
}