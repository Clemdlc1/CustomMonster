package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom; /**
 * Largage d'Urgence
 */
public class SupplyDropEvent extends ServerEvent {
    private final List<Location> dropLocations = new ArrayList<>();
    private final List<Block> droppedChests = new ArrayList<>();

    public SupplyDropEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "supply_drop", "Largage d'Urgence",
                EventType.COMPETITIVE, 10 * 60);
    }

    @Override
    protected void onStart() {
        dropSupplies();

        // Auto-terminer après 10 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                forceEnd();
            }
        }.runTaskLater(plugin, 12000L);
    }

    private void dropSupplies() {
        // Largage en mine PvP
        Location pvpMine = getPvpMineLocation();
        int chestCount = 1 + ThreadLocalRandom.current().nextInt(3); // 1-3 coffres

        for (int i = 0; i < chestCount; i++) {
            Location dropLoc = pvpMine.clone().add(
                    ThreadLocalRandom.current().nextInt(-20, 20),
                    5,
                    ThreadLocalRandom.current().nextInt(-20, 20)
            );

            dropLocations.add(dropLoc);
            createDropChest(dropLoc);
        }

        Bukkit.broadcastMessage("§c§l[LARGAGE] §e" + chestCount + " coffre(s) largué(s) en mine PvP !");
    }

    private void createDropChest(Location location) {
        Block block = location.getBlock();
        block.setType(Material.ENDER_CHEST);

        Chest chest = (Chest) block.getState();
        chest.setMetadata("supply_drop", new FixedMetadataValue(plugin, true));

        // Contenu du largage
        Inventory inv = chest.getInventory();

        // Beacons
        ItemStack beacons = new ItemStack(Material.BEACON, ThreadLocalRandom.current().nextInt(5) + 3);
        inv.addItem(beacons);

        // Tokens physiques (représentés par des items)
        inv.addItem(prisonHook.createKey("rare"));

        // Chance d'objets spéciaux
        if (ThreadLocalRandom.current().nextDouble() < 0.4) {
            inv.addItem(prisonHook.createKey("legendary"));
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            // Tête de joueur ou objet spécial
            inv.addItem(new ItemStack(Material.WITHER_SKELETON_SKULL));
        }

        chest.update();
        droppedChests.add(block);

        // Effets de largage
        location.getWorld().spawnParticle(Particle.FIREWORK, location, 50, 3, 3, 3, 0.3);
        location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
    }

    @Override
    protected void onEnd() {
        // Nettoyer les coffres restants
        for (Block chest : droppedChests) {
            if (chest.getType() == Material.ENDER_CHEST) {
                chest.setType(Material.AIR);
            }
        }

        Bukkit.broadcastMessage("§c§l[LARGAGE] §7Les coffres non récupérés ont disparu !");
    }

    private Location getPvpMineLocation() {
        // Retourne la location de la mine PvP
        return Bukkit.getWorlds().getFirst().getSpawnLocation().add(200, 0, 200);
    }

    @Override
    protected void onCleanup() {
        dropLocations.clear();
        droppedChests.clear();
    }
}
