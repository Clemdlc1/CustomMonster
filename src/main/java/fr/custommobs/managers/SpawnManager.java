package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import fr.custommobs.managers.CaveMobCounter;
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
    private static final int MASTER_TICK_INTERVAL = 40; // 2 secondes
    private static final int MAX_SPAWN_ATTEMPTS = 10;
    private static final double MIN_PLAYER_DISTANCE_SQUARED = 12 * 12;
    private static final int MAX_MONSTERS_IN_CAVE = 200;

    public SpawnManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.spawnZones = new HashMap<>();
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
        if (masterSpawnTask != null) {
            masterSpawnTask.cancel();
        }

        // CORRIGÉ : La tâche principale tourne maintenant sur le thread principal (synchrone)
        // pour pouvoir appeler les méthodes de l'API Bukkit en toute sécurité.
        this.masterSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                processAllSpawns();
            }
        }.runTaskTimer(plugin, 100L, MASTER_TICK_INTERVAL);
    }

    /**
     * Logique principale qui tourne sur le thread principal.
     * Elle vérifie les conditions et délègue la recherche de position à une tâche asynchrone.
     */
    private void processAllSpawns() {
        cleanMobLists();

        long currentTick = Bukkit.getServer().getCurrentTick();

        for (Map.Entry<String, SpawnZone> entry : spawnZones.entrySet()) {
            String zoneId = entry.getKey();
            SpawnZone zone = entry.getValue();
            World world = Bukkit.getWorld(zone.worldName());

            if (!zone.enabled() || world == null) continue;

            if (currentTick - zoneCooldowns.getOrDefault(zoneId, 0L) < zone.spawnInterval() * 20L) {
                continue;
            }

            int currentWorldMonsters = -1;
            if ("Cave".equalsIgnoreCase(world.getName())) {
                currentWorldMonsters = CaveMobCounter.getCurrentCount(world);
                if (currentWorldMonsters >= MAX_MONSTERS_IN_CAVE) {
                    continue;
                }
            }

            int mobsInZone = spawnedMobsByZone.get(zoneId).size();
            if (mobsInZone >= zone.maxMobs()) {
                continue;
            }

            int maxToSpawn = zone.groupSize();
            maxToSpawn = Math.min(maxToSpawn, zone.maxMobs() - mobsInZone);
            if (currentWorldMonsters != -1) {
                maxToSpawn = Math.min(maxToSpawn, MAX_MONSTERS_IN_CAVE - currentWorldMonsters);
            }

            if (maxToSpawn <= 0) continue;

            zoneCooldowns.put(zoneId, currentTick);

            // CORRIGÉ : On délègue la recherche de position (lourde) à une tâche asynchrone.
            for (int i = 0; i < maxToSpawn; i++) {
                String mobType = zone.getRandomMobType();
                if (mobType == null) continue;

                // Lancement de la recherche en asynchrone pour ne pas lagger le serveur
                findAndSpawnMobAsync(zone, zoneId, mobType);
            }
        }
    }

    /**
     * Lance une recherche de position en asynchrone, puis fait spawner le mob sur le thread principal.
     */
    private void findAndSpawnMobAsync(final SpawnZone zone, final String zoneId, final String mobType) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Étape 1: Trouver un emplacement (Asynchrone et sûr)
                final Location spawnLocation = findValidSpawnLocation(zone, mobType);

                if (spawnLocation != null) {
                    // Étape 2: Spawner l'entité sur le thread principal
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            World world = spawnLocation.getWorld();
                            if(world == null) return;

                            // Ultime vérification de sécurité juste avant le spawn.
                            if ("Cave".equalsIgnoreCase(world.getName()) && CaveMobCounter.getCurrentCount(world) >= MAX_MONSTERS_IN_CAVE) {
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
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Compte toutes les entités considérées comme "monstres" dans un monde.
     * Doit être appelée depuis le thread principal.
     */
    private int countAllMonstersInWorld(World world) {
        int caveCount = CaveMobCounter.getCurrentCount(world);
        if (caveCount >= 0) {
            return caveCount;
        }
        int count = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;
            if (entity instanceof Monster || entity instanceof IronGolem) {
                count++;
            }
        }
        return count;
    }

    /**
     * Nettoie les listes de mobs en supprimant les entités mortes ou invalides.
     */
    private void cleanMobLists() {
        for (List<LivingEntity> mobList : spawnedMobsByZone.values()) {
            mobList.removeIf(mob -> mob == null || mob.isDead() || !mob.isValid());
        }
    }

    // ... Le reste du fichier (findValidSpawnLocation, isSafeForMob, etc.) ne change pas ...
    private Location findValidSpawnLocation(SpawnZone zone, String mobType) {
        World world = Bukkit.getServer().getWorld(zone.worldName());
        if (world == null) return null;

        BoundingBox mobBoundingBox = getMobBoundingBox(mobType);

        for (int attempt = 0; attempt < SpawnManager.MAX_SPAWN_ATTEMPTS; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(zone.minX(), zone.maxX() + 1);
            int z = ThreadLocalRandom.current().nextInt(zone.minZ(), zone.maxZ() + 1);

            for (int y = zone.maxY(); y >= zone.minY(); y--) {
                Location potentialLocation = new Location(world, x + 0.5, y, z + 0.5);
                // isChunkLoaded est une vérification peu coûteuse et importante en asynchrone.
                if (!world.isChunkLoaded(potentialLocation.getBlockX() >> 4, potentialLocation.getBlockZ() >> 4)) {
                    continue;
                }
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
        // isNearPlayer est souvent appelé en async, Bukkit.getOnlinePlayers() est thread-safe.
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < MIN_PLAYER_DISTANCE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private BoundingBox getMobBoundingBox(String mobId) {
        String normalizedMobId = mobId.toLowerCase().trim();
        return switch (normalizedMobId) {
            case "zombie", "witch", "evoker" -> new BoundingBox(0, 0, 0, 0.6, 1.95, 0.6);
            case "skeleton" -> new BoundingBox(0, 0, 0, 0.6, 1.99, 0.6);
            case "creeper" -> new BoundingBox(0, 0, 0, 0.6, 1.7, 0.6);
            case "enderman" -> new BoundingBox(0, 0, 0, 0.6, 2.9, 0.6);
            case "spider" -> new BoundingBox(0, 0, 0, 1.4, 0.9, 1.4);
            case "blaze" -> new BoundingBox(0, 0, 0, 0.6, 1.8, 0.6);
            case "ravager" -> new BoundingBox(0, 0, 0, 1.95, 2.2, 1.95);
            case "iron_golem" -> new BoundingBox(0, 0, 0, 1.4, 2.7, 1.4);
            case "shulker" -> new BoundingBox(0, 0, 0, 1.0, 1.0, 1.0);
            case "wither" -> new BoundingBox(0, 0, 0, 0.9, 3.5, 0.9);
            case "baby_zombie" -> new BoundingBox(0, 0, 0, 0.3, 0.975, 0.3);
            default ->
                // Cette partie est problématique en asynchrone. On retourne une valeur par défaut.
                // Le spawn d'entité pour mesurer sa box ne peut pas se faire en async.
                    new BoundingBox(0, 0, 0, 0.8, 1.9, 0.8);
        };
    }


    public void stopAllSpawning() {
        if (masterSpawnTask != null) {
            masterSpawnTask.cancel();
            masterSpawnTask = null;
        }

        // Le reste de la fonction est ok
        for (List<LivingEntity> mobs : spawnedMobsByZone.values()) {
            mobs.forEach(mob -> {
                if (mob != null && !mob.isDead()) {
                    CaveMobCounter.onEntityRemoved(mob);
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