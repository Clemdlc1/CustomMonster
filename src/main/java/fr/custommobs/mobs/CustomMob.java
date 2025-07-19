package fr.custommobs.mobs;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import fr.custommobs.CustomMobsPlugin;

public abstract class CustomMob {

    protected final CustomMobsPlugin plugin;
    protected LivingEntity entity;
    protected String mobId;
    protected double maxHealth;
    protected double damage;
    protected double speed;

    public CustomMob(CustomMobsPlugin plugin, String mobId) {
        this.plugin = plugin;
        this.mobId = mobId;
        setDefaultStats();
    }

    /**
     * Définit les statistiques par défaut du monstre
     */
    protected abstract void setDefaultStats();

    /**
     * Spawn le monstre à la location donnée
     */
    public abstract LivingEntity spawn(Location location);

    /**
     * Comportement d'attaque du monstre
     */
    public abstract void attack(Player target);

    /**
     * Comportement spécial du monstre (optionnel)
     */
    public void specialAbility(Player target) {
        // Implémentation par défaut vide
    }

    /**
     * Configure l'entité avec les métadonnées custom
     */
    protected void setupEntity(LivingEntity entity) {
        this.entity = entity;

        // Marque l'entité comme monstre custom
        entity.setMetadata("custom_mob", new FixedMetadataValue(plugin, mobId));
        entity.setMetadata("custom_mob_id", new FixedMetadataValue(plugin, mobId));

        // Configure les attributs
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            entity.setHealth(maxHealth);
        }

        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damage);
        }

        if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        }

        // Empêche la disparition naturelle
        entity.setRemoveWhenFarAway(false);

        // Lance les comportements périodiques
        startBehaviors();
    }

    /**
     * Lance les comportements périodiques du monstre
     */
    protected void startBehaviors() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                // Trouve le joueur le plus proche
                Player nearestPlayer = findNearestPlayer(16);
                if (nearestPlayer != null && nearestPlayer.getLocation().distance(entity.getLocation()) <= 16) {
                    onPlayerNear(nearestPlayer);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Toutes les secondes
    }

    /**
     * Comportement quand un joueur est proche
     */
    protected void onPlayerNear(Player player) {
        // Comportement par défaut : attaquer
        if (Math.random() < 0.1) { // 10% de chance par seconde
            attack(player);
        }

        // Capacité spéciale rare
        if (Math.random() < 0.05) { // 5% de chance par seconde
            specialAbility(player);
        }
    }

    /**
     * Trouve le joueur le plus proche
     */
    protected Player findNearestPlayer(double radius) {
        Player nearest = null;
        double minDistanceSq = Double.MAX_VALUE; // On compare la distance au carré pour éviter les calculs de racine carrée

        for (Entity entity : entity.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                    continue;
                }

                double distanceSq = player.getLocation().distanceSquared(this.entity.getLocation());
                if (distanceSq < minDistanceSq) {
                    minDistanceSq = distanceSq;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    /**
     * Vérifie si une entité est un monstre custom
     */
    public static boolean isCustomMob(Entity entity) {
        return entity.hasMetadata("custom_mob");
    }

    /**
     * Récupère l'ID du monstre custom
     */
    public static String getCustomMobId(Entity entity) {
        if (entity.hasMetadata("custom_mob_id")) {
            return entity.getMetadata("custom_mob_id").get(0).asString();
        }
        return null;
    }

    // Getters
    public LivingEntity getEntity() {
        return entity;
    }

    public String getMobId() {
        return mobId;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getDamage() {
        return damage;
    }

    public double getSpeed() {
        return speed;
    }
}