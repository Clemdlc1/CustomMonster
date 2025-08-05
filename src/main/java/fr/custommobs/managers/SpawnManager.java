package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnManager {

    private final CustomMobsPlugin plugin;
    private final Map<String, SpawnZone> spawnZones;
    private final Map<String, List<LivingEntity>> spawnedMobsByZone;
    private final Map<String, Long> zoneCooldowns;
    private BukkitTask masterSpawnTask;

    // Constantes
    private static final int MASTER_TICK_INTERVAL = 40;
    private static final int MAX_SPAWN_ATTEMPTS = 10;
    private static final double MIN_PLAYER_DISTANCE_SQUARED = 12*12;
    private static final int MAX_MONSTERS_IN_CAVE = 200;

    public SpawnManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.spawnZones = new HashMap<>();
        // Utilisation de collections thread-safe pour éviter les erreurs dans un contexte asynchrone
        this.spawnedMobsByZone = new ConcurrentHashMap<>();
        this.zoneCooldowns = new ConcurrentHashMap<>();
        loadSpawnZones();
        startSpawning();
    }

    private void loadSpawnZones() {
        ConfigurationSection zonesSection = plugin.getConfig().getConfigurationSection("spawn-zones");
        if (zonesSection != null) {
            for (String zoneId : zonesSection.getKeys(false)) {
                ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
                if (zoneSection != null) {
                    try {
                        SpawnZone zone = SpawnZone.fromConfig(zoneSection);
                        spawnZones.put(zoneId, zone);
                        spawnedMobsByZone.put(zoneId, new CopyOnWriteArrayList<>());
                        plugin.getLogger().info("Zone de spawn '" + zoneId + "' chargée !");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement de la zone '" + zoneId + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    public void startSpawning() {
        // Annule une tâche existante si on recharge le plugin
        if (masterSpawnTask != null) {
            masterSpawnTask.cancel();
        }

        this.masterSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Cette méthode est le coeur de la logique et tourne en asynchrone
                processAllSpawns();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, MASTER_TICK_INTERVAL);
    }

    private void processAllSpawns() {
        // 1. Nettoyer les listes et obtenir un décompte précis des mobs actuels par monde.
        Map<World, Integer> currentMobCounts = cleanAndCountAllMobs();

        long currentTick = Bukkit.getServer().getCurrentTick();

        // 2. Parcourir chaque zone pour décider si un spawn est nécessaire
        for (Map.Entry<String, SpawnZone> entry : spawnZones.entrySet()) {
            String zoneId = entry.getKey();
            SpawnZone zone = entry.getValue();
            World world = Bukkit.getWorld(zone.worldName());

            // --- Vérifications ---
            if (!zone.enabled() || world == null) continue;

            // Vérification du cooldown de la zone
            long intervalTicks = zone.spawnInterval() * 20L;
            if (currentTick - zoneCooldowns.getOrDefault(zoneId, 0L) < intervalTicks) {
                continue;
            }

            // Vérification de la limite globale pour le monde "Cave"
            if ("Cave".equalsIgnoreCase(world.getName()) && currentMobCounts.getOrDefault(world, 0) >= MAX_MONSTERS_IN_CAVE) {
                continue;
            }

            // Vérification de la limite de la zone
            int mobsInZone = spawnedMobsByZone.get(zoneId).size();
            if (mobsInZone >= zone.maxMobs()) {
                continue;
            }

            // --- Calcul du nombre de mobs à spawner ---
            int worldCapRoom = Integer.MAX_VALUE;
            if ("Cave".equalsIgnoreCase(world.getName())) {
                worldCapRoom = MAX_MONSTERS_IN_CAVE - currentMobCounts.getOrDefault(world, 0);
            }

            int zoneCapRoom = zone.maxMobs() - mobsInZone;
            int maxToSpawn = Math.min(zone.groupSize(), Math.min(zoneCapRoom, worldCapRoom));

            if (maxToSpawn <= 0) continue;

            // --- Lancement du spawn ---
            zoneCooldowns.put(zoneId, currentTick); // Mettre à jour le cooldown immédiatement

            for (int i = 0; i < maxToSpawn; i++) {
                String mobType = zone.getRandomMobType();
                if (mobType == null) continue;

                // La recherche de position reste asynchrone pour la performance
                Location spawnLocation = findValidSpawnLocation(zone, mobType, MAX_SPAWN_ATTEMPTS);

                if (spawnLocation != null) {
                    // Le spawn de l'entité doit se faire sur le thread principal
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // On re-vérifie la limite juste avant le spawn pour être 100% sûr
                            if ("Cave".equalsIgnoreCase(world.getName()) && cleanAndCountAllMobs().getOrDefault(world, 0) >= MAX_MONSTERS_IN_CAVE) {
                                return;
                            }
                            LivingEntity mob = plugin.getMobManager().spawnCustomMob(mobType, spawnLocation);
                            if (mob != null) {
                                spawnedMobsByZone.get(zoneId).add(mob);
                                mob.setMetadata("spawn_zone", new FixedMetadataValue(plugin, zoneId));
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }
    }

    /**
     * Nettoie les listes de mobs (supprime les morts) et renvoie un décompte à jour par monde.
     * C'est l'étape cruciale qui fiabilise tout le système.
     */
    private Map<World, Integer> cleanAndCountAllMobs() {
        Map<World, Integer> counts = new HashMap<>();
        for (List<LivingEntity> mobList : spawnedMobsByZone.values()) {
            // Utilise removeIf pour une suppression sûre tout en parcourant la liste
            mobList.removeIf(mob -> {
                if (mob == null || mob.isDead() || !mob.isValid()) {
                    return true; // Marque pour suppression
                }
                // Si le mob est valide, on l'ajoute au décompte de son monde
                counts.merge(mob.getWorld(), 1, Integer::sum);
                return false; // Garde l'élément
            });
        }
        return counts;
    }

    private Location findValidSpawnLocation(SpawnZone zone, String mobType, int maxAttempts) {
        World world = plugin.getServer().getWorld(zone.worldName());
        if (world == null) return null;

        BoundingBox mobBoundingBox = getMobBoundingBox(mobType);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(zone.minX(), zone.maxX() + 1);
            int z = ThreadLocalRandom.current().nextInt(zone.minZ(), zone.maxZ() + 1);

            for (int y = zone.maxY(); y >= zone.minY(); y--) {
                Location potentialLocation = new Location(world, x + 0.5, y, z + 0.5);
                Block groundBlock = potentialLocation.getBlock().getRelative(BlockFace.DOWN);

                if (isSolidAndSafeGround(groundBlock.getType())) {
                    if (isSafeForMob(potentialLocation, mobBoundingBox) && !isNearPlayer(potentialLocation)) {
                        return potentialLocation;
                    }
                    break;
                }
            }
        }
        return null;
    }

    private boolean isSafeForMob(Location location, BoundingBox mobBoundingBox) {
        double height = mobBoundingBox.getHeight();
        double width = Math.max(mobBoundingBox.getWidthX(), mobBoundingBox.getWidthZ());
        int checkRadius = (int) Math.ceil(width / 2.0);

        for (int y = 0; y < Math.ceil(height); y++) {
            for (int x = -checkRadius; x <= checkRadius; x++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    if (!block.isPassable() && block.getType() != Material.WATER) {
                        return false;
                    }
                }
            }
        }
        Block below = location.getBlock().getRelative(BlockFace.DOWN);
        return below.getRelative(BlockFace.DOWN, 5).getType().isSolid();
    }

    private boolean isSolidAndSafeGround(Material material) {
        if (!material.isSolid()) {
            return false;
        }
        return switch (material) {
            case LAVA, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH -> false;
            default -> !material.name().contains("PRESSURE_PLATE");
        };
    }

    private boolean isNearPlayer(Location location) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < MIN_PLAYER_DISTANCE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private BoundingBox getMobBoundingBox(String mobId) {
        String normalizedMobId = mobId.toLowerCase().trim();
        switch (normalizedMobId) {
            case "zombie": case "witch": case "evoker": return new BoundingBox(0, 0, 0, 0.6, 1.95, 0.6);
            case "skeleton": return new BoundingBox(0, 0, 0, 0.6, 1.99, 0.6);
            case "creeper": return new BoundingBox(0, 0, 0, 0.6, 1.7, 0.6);
            case "enderman": return new BoundingBox(0, 0, 0, 0.6, 2.9, 0.6);
            case "spider": return new BoundingBox(0, 0, 0, 1.4, 0.9, 1.4);
            case "blaze": return new BoundingBox(0, 0, 0, 0.6, 1.8, 0.6);
            case "ravager": return new BoundingBox(0, 0, 0, 1.95, 2.2, 1.95);
            case "iron_golem": return new BoundingBox(0, 0, 0, 1.4, 2.7, 1.4);
            case "shulker": return new BoundingBox(0, 0, 0, 1.0, 1.0, 1.0);
            case "wither": return new BoundingBox(0, 0, 0, 0.9, 3.5, 0.9);
            case "baby_zombie": return new BoundingBox(0, 0, 0, 0.3, 0.975, 0.3);
            default:
                try {
                    EntityType entityType = EntityType.valueOf(normalizedMobId.toUpperCase());
                    org.bukkit.entity.Entity entity = Bukkit.getWorlds().getFirst().spawnEntity(new Location(Bukkit.getWorlds().getFirst(), 0, -100, 0), entityType);
                    BoundingBox box = entity.getBoundingBox();
                    entity.remove();
                    return box;
                } catch (Exception e) {
                    // Si tout échoue, retourne une taille générique
                    return new BoundingBox(0, 0, 0, 0.8, 1.9, 0.8);
                }
        }
    }


    public void stopAllSpawning() {
        if (masterSpawnTask != null) {
            masterSpawnTask.cancel();
            masterSpawnTask = null;
        }

        for (List<LivingEntity> mobs : spawnedMobsByZone.values()) {
            mobs.forEach(mob -> {
                if (mob != null && !mob.isDead()) {
                    mob.remove();
                }
            });
            mobs.clear();
        }
        spawnedMobsByZone.clear();
        zoneCooldowns.clear();
        plugin.getLogger().info("La tâche de spawn a été arrêtée et les mobs supprimés.");
    }

    public Map<String, SpawnZone> getSpawnZones() {
        return Collections.unmodifiableMap(spawnZones);
    }

    public record SpawnZone(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                            List<String> mobTypes, int maxMobs, int groupSize, int spawnInterval, boolean enabled) {
            public SpawnZone(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, List<String> mobTypes, int maxMobs, int groupSize, int spawnInterval, boolean enabled) {
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
                return new SpawnZone(section.getString("world", "world"), section.getInt("min-x"), section.getInt("min-y"), section.getInt("min-z"), section.getInt("max-x"), section.getInt("max-y"), section.getInt("max-z"), section.getStringList("mob-types"), section.getInt("max-mobs", 10), section.getInt("group-size", 3), section.getInt("spawn-interval", 60), section.getBoolean("enabled", true));
            }

        @Override
        public List<String> mobTypes() {
            return Collections.unmodifiableList(mobTypes);
        }

            public String getRandomMobType() {
                if (mobTypes.isEmpty()) return null;
                return mobTypes.get(ThreadLocalRandom.current().nextInt(mobTypes.size()));
            }
        }
}