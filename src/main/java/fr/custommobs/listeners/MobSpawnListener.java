package fr.custommobs.listeners;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class MobSpawnListener implements Listener {

    private final CustomMobsPlugin plugin;

    public MobSpawnListener(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Annule le spawn naturel des monstres pour les remplacer par nos custom
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();

        // Autorise les spawns de nos monstres custom
        if (CustomMob.isCustomMob(entity)) {
            return;
        }

        // Autorise les spawns par plugin/commande
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
            return;
        }

        // Bloque les spawns naturels des monstres hostiles
        if (entity instanceof Monster ||
                entity instanceof Slime ||
                entity instanceof Phantom ||
                entity instanceof Witch) {

            event.setCancelled(true);
            plugin.getLogger().fine("Spawn naturel bloqué: " + entity.getType() + " à " +
                    event.getLocation().getBlockX() + ", " +
                    event.getLocation().getBlockY() + ", " +
                    event.getLocation().getBlockZ());
        }

        // Retire certains mobs non-hostiles inutiles selon la config
        if (plugin.getConfig().getBoolean("remove-useless-mobs", true)) {
            if (entity instanceof Bat ||
                    entity instanceof Squid ||
                    entity instanceof Parrot) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Gère la mort des monstres custom pour les loots
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        event.getDrops().clear();
        event.setDroppedExp(0);
        // Vérifie si c'est un monstre custom
        if (!CustomMob.isCustomMob(entity)) {
            return;
        }
        String mobId = CustomMob.getCustomMobId(entity);
        if (mobId != null) {
            // Génère les loots custom
            plugin.getLootManager().dropLoots(entity, mobId);

            // XP custom basé sur la difficulté du monstre
            int customExp = calculateCustomExp(mobId);
            event.setDroppedExp(customExp);

            plugin.getLogger().fine("Monstre custom mort: " + mobId + " (XP: " + customExp + ")");
        }
    }

    /**
     * Calcule l'XP custom basé sur le type de monstre
     */
    private int calculateCustomExp(String mobId) {
        // Monstres simples
        if (mobId.contains("zombie") || mobId.contains("skeleton") ||
                mobId.contains("spider") || mobId.contains("creeper") ||
                mobId.contains("enderman") || mobId.contains("witch") ||
                mobId.contains("golem")) {
            return 15 + (int) (Math.random() * 10); // 15-25 XP
        }

        // Monstres avancés
        if (mobId.contains("dragon") || mobId.contains("necromancer") ||
                mobId.contains("titan")) {
            return 50 + (int) (Math.random() * 30); // 50-80 XP
        }

        return 10; // Défaut
    }

    /**
     * Gère l'effet d'épines de l'Aberration Géodique.
     */
    @EventHandler
    public void onGeodeAberrationDamage(EntityDamageByEntityEvent event) {
        // 1. On vérifie si l'entité qui subit les dégâts est bien notre Aberration Géodique.
        Entity damaged = event.getEntity();
        if (!CustomMob.isCustomMob(damaged) || !"geode_aberration".equals(CustomMob.getCustomMobId(damaged))) {
            return;
        }

        // 2. On vérifie si c'est bien un Shulker et s'il est en mode défensif (fermé).
        // La méthode getPeek() renvoie 0.0f quand le Shulker est fermé.
        if (!(damaged instanceof Shulker) || ((Shulker) damaged).getPeek() > 0.0f) {
            return;
        }

        // 3. On trouve qui est l'attaquant.
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // Si l'attaquant est un joueur, on lui renvoie des dégâts.
        if (attacker != null) {
            double damageTaken = event.getDamage();
            // On renvoie 50% des dégâts infligés. Vous pouvez ajuster ce multiplicateur.
            double reflectedDamage = damageTaken * 0.5;

            // Applique les dégâts à l'attaquant.
            attacker.damage(reflectedDamage);

            // Ajoute des effets visuels et sonores pour que le joueur comprenne ce qui se passe.
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 1.0f);
            attacker.getWorld().spawnParticle(Particle.WITCH, attacker.getEyeLocation(), 15, 0.5, 0.5, 0.5);
        }
    }
}