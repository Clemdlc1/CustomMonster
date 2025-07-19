package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SpawnManager {

    private final CustomMobsPlugin plugin;
    private final Map<String, SpawnZone> spawnZones;
    private final Map<String, BukkitTask> spawnTasks;
    private final Map<String, List<LivingEntity>> spawnedMobs;

    public SpawnManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.spawnZones = new HashMap<>();
        this.spawnTasks = new HashMap<>();
        this.spawnedMobs = new HashMap<>();
        loadSpawnZones();
        startSpawning();
    }

    /**
     * Charge les zones de spawn depuis la config
     */
    private void loadSpawnZones() {
        ConfigurationSection zonesSection = plugin.getConfig().getConfigurationSection("spawn-zones");
        if (zonesSection != null) {
            for (String zoneId : zonesSection.getKeys(false)) {
                ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
                if (zoneSection != null) {
                    try {
                        SpawnZone zone = SpawnZone.fromConfig(zoneSection);
                        spawnZones.put(zoneId, zone);
                        plugin.getLogger().info("Zone de spawn '" + zoneId + "' chargée!");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement de la zone '" + zoneId + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Démarre le système de spawn
     */
    private void startSpawning() {
        for (Map.Entry<String, SpawnZone> entry : spawnZones.entrySet()) {
            String zoneId = entry.getKey();
            SpawnZone zone = entry.getValue();

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (zone.isEnabled()) {
                        spawnMobsInZone(zoneId, zone);
                    }
                }
            }.runTaskTimer(plugin, 100L, zone.getSpawnInterval() * 20L);

            spawnTasks.put(zoneId, task);
        }
    }

    /**
     * Spawn des monstres dans une zone
     */
    private void spawnMobsInZone(String zoneId, SpawnZone zone) {
        // Nettoie les mobs morts
        List<LivingEntity> zoneMobs = spawnedMobs.computeIfAbsent(zoneId, k -> new ArrayList<>());
        zoneMobs.removeIf(LivingEntity::isDead);

        // Vérifie si on peut spawn plus de mobs
        if (zoneMobs.size() >= zone.getMaxMobs()) {
            return;
        }

        // Calcule combien de mobs spawner
        int toSpawn = Math.min(zone.getGroupSize(), zone.getMaxMobs() - zoneMobs.size());

        for (int i = 0; i < toSpawn; i++) {
            String mobType = zone.getRandomMobType();
            Location spawnLoc = zone.getRandomLocation();

            if (spawnLoc != null && spawnLoc.getWorld() != null) {
                LivingEntity mob = plugin.getMobManager().spawnCustomMob(mobType, spawnLoc);
                if (mob != null) {
                    zoneMobs.add(mob);

                    // Marque le mob avec l'ID de la zone
                    mob.setMetadata("spawn_zone", new org.bukkit.metadata.FixedMetadataValue(plugin, zoneId));
                }
            }
        }
    }

    /**
     * Arrête tout le système de spawn
     */
    public void stopAllSpawning() {
        for (BukkitTask task : spawnTasks.values()) {
            task.cancel();
        }
        spawnTasks.clear();

        // Supprime tous les mobs spawnés
        for (List<LivingEntity> mobs : spawnedMobs.values()) {
            mobs.forEach(mob -> {
                if (!mob.isDead()) {
                    mob.remove();
                }
            });
        }
        spawnedMobs.clear();
    }

    /**
     * Récupère les zones de spawn
     */
    public Map<String, SpawnZone> getSpawnZones() {
        return Collections.unmodifiableMap(spawnZones);
    }

    /**
     * Classe pour représenter une zone de spawn
     */
    public static class SpawnZone {
        private final String worldName;
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;
        private final List<String> mobTypes;
        private final int maxMobs;
        private final int groupSize;
        private final int spawnInterval;
        private final boolean enabled;

        public SpawnZone(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                         List<String> mobTypes, int maxMobs, int groupSize, int spawnInterval, boolean enabled) {
            this.worldName = worldName;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.mobTypes = mobTypes;
            this.maxMobs = maxMobs;
            this.groupSize = groupSize;
            this.spawnInterval = spawnInterval;
            this.enabled = enabled;
        }

        public static SpawnZone fromConfig(ConfigurationSection section) {
            String worldName = section.getString("world", "world");
            int minX = section.getInt("min-x");
            int minY = section.getInt("min-y");
            int minZ = section.getInt("min-z");
            int maxX = section.getInt("max-x");
            int maxY = section.getInt("max-y");
            int maxZ = section.getInt("max-z");
            List<String> mobTypes = section.getStringList("mob-types");
            int maxMobs = section.getInt("max-mobs", 10);
            int groupSize = section.getInt("group-size", 3);
            int spawnInterval = section.getInt("spawn-interval", 60);
            boolean enabled = section.getBoolean("enabled", true);

            return new SpawnZone(worldName, minX, minY, minZ, maxX, maxY, maxZ,
                    mobTypes, maxMobs, groupSize, spawnInterval, enabled);
        }

        public Location getRandomLocation() {
            World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) return null;

            Random random = new Random();
            int x = random.nextInt(maxX - minX + 1) + minX;
            int y = random.nextInt(maxY - minY + 1) + minY;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;

            return new Location(world, x, y, z);
        }

        public String getRandomMobType() {
            if (mobTypes.isEmpty()) return null;
            return mobTypes.get(new Random().nextInt(mobTypes.size()));
        }

        // Getters
        public String getWorldName() { return worldName; }
        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
        public List<String> getMobTypes() { return Collections.unmodifiableList(mobTypes); }
        public int getMaxMobs() { return maxMobs; }
        public int getGroupSize() { return groupSize; }
        public int getSpawnInterval() { return spawnInterval; }
        public boolean isEnabled() { return enabled; }
    }
}