package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CustomMobManager {

    private final CustomMobsPlugin plugin;
    private final Map<String, Class<? extends CustomMob>> registeredMobs;

    public CustomMobManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.registeredMobs = new HashMap<>();
    }

    /**
     * Enregistre un nouveau type de monstre custom
     */
    public void registerMob(String id, Class<? extends CustomMob> mobClass) {
        registeredMobs.put(id.toLowerCase(), mobClass);
        plugin.getLogger().info("Monstre '" + id + "' enregistré avec succès!");
    }

    /**
     * Crée et spawn un monstre custom
     */
    public LivingEntity spawnCustomMob(String mobId, Location location) {
        Class<? extends CustomMob> mobClass = registeredMobs.get(mobId.toLowerCase());
        if (mobClass == null) {
            plugin.getLogger().warning("Monstre '" + mobId + "' non trouvé!");
            return null;
        }

        try {
            Constructor<? extends CustomMob> constructor = mobClass.getConstructor(CustomMobsPlugin.class);
            CustomMob mob = constructor.newInstance(plugin);
            return mob.spawn(location);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du spawn du monstre '" + mobId + "': " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Vérifie si un monstre est enregistré
     */
    public boolean isMobRegistered(String mobId) {
        return registeredMobs.containsKey(mobId.toLowerCase());
    }

    /**
     * Récupère tous les IDs de monstres enregistrés
     */
    public Set<String> getRegisteredMobIds() {
        return registeredMobs.keySet();
    }

    /**
     * Récupère la classe d'un monstre
     */
    public Class<? extends CustomMob> getMobClass(String mobId) {
        return registeredMobs.get(mobId.toLowerCase());
    }
}