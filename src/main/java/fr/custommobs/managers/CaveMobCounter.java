package fr.custommobs.managers;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicInteger;

public final class CaveMobCounter {

    private static final String WORLD_NAME = "Cave";
    private static final String COUNTED_META = "cave_limit_counted";
    private static final int MAX_IN_CAVE = 200;

    private static final AtomicInteger caveCount = new AtomicInteger(0);
    private static volatile boolean initialized = false;
    private static BukkitTask resyncTask;

    private CaveMobCounter() {}

    public static void initialize(CustomMobsPlugin plugin) {
        if (initialized) return;
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world != null) {
            recalculateFromWorld(plugin, world);
        }
        // Démarre une tâche de resynchronisation périodique (toutes les 60s)
        if (resyncTask == null) {
            resyncTask = new BukkitRunnable() {
                @Override
                public void run() {
                    World w = Bukkit.getWorld(WORLD_NAME);
                    if (w != null) {
                        recalculateFromWorld(plugin, w);
                    }
                }
            }.runTaskTimer(plugin, 1200L, 1200L);
        }
        initialized = true;
    }

    public static boolean shouldBlockSpawn(LivingEntity entity) {
        World world = entity.getWorld();
        if (world == null) return false;
        if (!WORLD_NAME.equalsIgnoreCase(world.getName())) return false;
        if (!isCountedEntity(entity)) return false;
        return caveCount.get() >= MAX_IN_CAVE;
    }

    public static void onSuccessfulSpawn(LivingEntity entity, CustomMobsPlugin plugin) {
        World world = entity.getWorld();
        if (world == null) return;
        if (!WORLD_NAME.equalsIgnoreCase(world.getName())) return;
        if (!isCountedEntity(entity)) return;
        if (!entity.hasMetadata(COUNTED_META)) {
            entity.setMetadata(COUNTED_META, new FixedMetadataValue(plugin, true));
            caveCount.incrementAndGet();
        }
    }

    public static void onEntityRemoved(LivingEntity entity) {
        World world = entity.getWorld();
        if (world == null) return;
        if (!WORLD_NAME.equalsIgnoreCase(world.getName())) return;
        if (!isCountedEntity(entity)) return;
        if (entity.hasMetadata(COUNTED_META)) {
            caveCount.updateAndGet(current -> Math.max(0, current - 1));
            entity.removeMetadata(COUNTED_META, CustomMobsPlugin.getInstance());
        }
    }

    public static int getCurrentCount(World world) {
        if (world != null && WORLD_NAME.equalsIgnoreCase(world.getName())) {
            return caveCount.get();
        }
        return -1; // Non-Cave world indicator
    }

    public static int getMaxInCave() {
        return MAX_IN_CAVE;
    }

    private static boolean isCountedEntity(LivingEntity entity) {
        return (entity instanceof Monster) || (entity instanceof IronGolem);
    }

    private static void recalculateFromWorld(CustomMobsPlugin plugin, World world) {
        int count = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;
            if (isCountedEntity(entity)) {
                count++;
                if (!entity.hasMetadata(COUNTED_META)) {
                    entity.setMetadata(COUNTED_META, new FixedMetadataValue(plugin, true));
                }
            }
        }
        caveCount.set(count);
    }
}