package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom; /**
 * Chasse au Trésor
 */
public class TreasureSearchEvent extends ServerEvent {
    private final List<Location> treasureLocations = new ArrayList<>();
    private final Map<UUID, Integer> treasuresFound = new HashMap<>();
    private final Set<Location> foundTreasures = new HashSet<>();

    public TreasureSearchEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventListener.EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "treasure_search", "Chasse au Trésor",
                EventType.NEUTRAL, 30 * 60);
    }

    @Override
    protected void onStart() {
        Bukkit.broadcastMessage("§6§l🗝 CHASSE AU TRÉSOR COMMENCÉE ! 🗝");
        Bukkit.broadcastMessage("§e§lCoffres cachés dans les mines standard !");
        Bukkit.broadcastMessage("§7§lCherchez les indices et trouvez les trésors !");

        placeTreasures();
        startHintSystem();
    }

    private void placeTreasures() {
        // Placer 1-3 coffres par mine
        for (int mine = 0; mine < 5; mine++) { // 5 mines d'exemple
            int treasureCount = 1 + ThreadLocalRandom.current().nextInt(3);

            for (int i = 0; i < treasureCount; i++) {
                Location treasureLoc = generateTreasureLocation(mine);
                treasureLocations.add(treasureLoc);
                placeTreasureChest(treasureLoc);
            }
        }

        Bukkit.broadcastMessage("§6§l[TRÉSOR] §e" + treasureLocations.size() + " coffres ont été cachés !");
    }

    private void placeTreasureChest(Location location) {
        Block block = location.getBlock();
        block.setType(Material.CHEST);

        Chest chest = (Chest) block.getState();
        chest.setMetadata("treasure_chest", new FixedMetadataValue(plugin, true));

        // Remplir le coffre
        Inventory inv = chest.getInventory();
        inv.addItem(
                prisonHook.createKey("rare"),
                new ItemStack(Material.DIAMOND, ThreadLocalRandom.current().nextInt(3) + 1),
                new ItemStack(Material.EMERALD, ThreadLocalRandom.current().nextInt(5) + 2)
        );

        // Chance d'objets spéciaux
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            inv.addItem(prisonHook.createKey("legendary"));
        }

        chest.update();

        // Effets visuels subtils
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
    }

    private void startHintSystem() {
        new BukkitRunnable() {
            int hintCount = 0;
            @Override
            public void run() {
                if (!active || hintCount >= 6) {
                    cancel();
                    return;
                }

                giveHint();
                hintCount++;
            }
        }.runTaskTimer(plugin, 300L, 300L); // Toutes les 5 minutes
    }

    private void giveHint() {
        if (treasureLocations.isEmpty()) return;

        Location randomTreasure = treasureLocations.get(ThreadLocalRandom.current().nextInt(treasureLocations.size()));
        if (foundTreasures.contains(randomTreasure)) return;

        String hint = generateHint(randomTreasure);
        Bukkit.broadcastMessage("§6§l[INDICE] §e" + hint);
    }

    private String generateHint(Location location) {
        String[] hints = {
                "Un coffre brille près d'une source de lumière...",
                "Des murmures étranges résonnent dans les profondeurs...",
                "L'air scintille d'une aura magique quelque part...",
                "Un trésor attend près de la roche solide...",
                "Les anciens ont caché leurs richesses dans l'ombre..."
        };
        return hints[ThreadLocalRandom.current().nextInt(hints.length)];
    }

    public void onTreasureFound(Player player, Location chestLocation) {
        if (!treasureLocations.contains(chestLocation) || foundTreasures.contains(chestLocation)) {
            return;
        }

        foundTreasures.add(chestLocation);
        addParticipant(player);

        int playerTreasures = treasuresFound.merge(player.getUniqueId(), 1, Integer::sum);

        if (playerTreasures <= 3) { // Max 3 coffres par joueur
            Bukkit.broadcastMessage("§6§l[TRÉSOR] §a" + player.getName() + " §ea trouvé un coffre ! (" + playerTreasures + "/3)");

            // Effets de célébration
            player.getWorld().spawnParticle(Particle.FIREWORK, chestLocation, 30, 1, 1, 1, 0.2);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else {
            player.sendMessage("§c§l[TRÉSOR] §7Vous avez déjà trouvé 3 coffres (limite atteinte) !");
        }
    }

    @Override
    protected void onEnd() {
        // Nettoyer les coffres restants
        for (Location loc : treasureLocations) {
            if (!foundTreasures.contains(loc)) {
                loc.getBlock().setType(Material.AIR);
            }
        }

        calculateResults();

        Bukkit.broadcastMessage("§6§l[TRÉSOR] §eChasse au trésor terminée !");
    }

    private void calculateResults() {
        Bukkit.broadcastMessage("§6§l=== RÉSULTATS CHASSE AU TRÉSOR ===");

        List<Map.Entry<UUID, Integer>> ranking = treasuresFound.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();

        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            UUID playerId = ranking.get(i).getKey();
            int count = ranking.get(i).getValue();
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Joueur déconnecté";

            Bukkit.broadcastMessage("§7" + (i + 1) + ". §e" + name + " §7- §6" + count + " coffre(s)");
        }
    }


    private Location generateTreasureLocation(int mineId) {
        // Générer une location aléatoire dans une mine
        World world = Bukkit.getWorlds().getFirst();
        return new Location(world,
                ThreadLocalRandom.current().nextInt(-100, 100),
                ThreadLocalRandom.current().nextInt(10, 50),
                ThreadLocalRandom.current().nextInt(-100, 100)
        );
    }

    @Override
    protected void onCleanup() {
        treasureLocations.clear();
        treasuresFound.clear();
        foundTreasures.clear();
    }
}
