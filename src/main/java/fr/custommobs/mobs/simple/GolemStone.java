package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GolemStone extends CustomMob {

    // --- Cooldowns ---
    private long lastSlam = 0;
    private final long SLAM_COOLDOWN = 8000;

    private long lastThrow = 0;
    private final long THROW_COOLDOWN = 6000;

    private long lastGrasp = 0;
    private final long GRASP_COOLDOWN = 18000; // Capacité puissante, long cooldown

    // --- État ---
    private boolean isPerformingAbility = false;

    public GolemStone(CustomMobsPlugin plugin) {
        super(plugin, "golem_stone");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 100.0; // Encore plus tanky
        this.damage = 12.0;
        this.speed = 0.22;
    }

    @Override
    public LivingEntity spawn(Location location) {
        IronGolem golem = location.getWorld().spawn(location, IronGolem.class);

        golem.setCustomName("§8§lGardien Tellurique");
        golem.setCustomNameVisible(true);

        setupEntity(golem);
        return golem;
    }

    @Override
    protected void onPlayerNear(Player target) {
        if (isPerformingAbility) return;

        ((IronGolem) entity).setTarget(target);

        long currentTime = System.currentTimeMillis();

        // --- IA STRATÉGIQUE ---
        List<Player> nearbyPlayers = getNearbyPlayers(20);

        // 1. Poigne Tellurique pour regrouper les cibles distantes
        if (nearbyPlayers.size() > 1 && currentTime - lastGrasp > GRASP_COOLDOWN) {
            telluricGrasp(nearbyPlayers);
            return;
        }

        // 2. Frappe sismique si des joueurs sont au corps-à-corps
        if (entity.getLocation().distanceSquared(target.getLocation()) < 25 && currentTime - lastSlam > SLAM_COOLDOWN) {
            seismicSlam();
            return;
        }

        // 3. Lancer de rocher sur une cible unique à distance
        if (entity.getLocation().distanceSquared(target.getLocation()) > 36 && currentTime - lastThrow > THROW_COOLDOWN) {
            throwRock(target);
            return;
        }
    }

    @Override
    public void attack(Player target) {
        // L'attaque de base est maintenant la frappe sismique si le joueur est proche
        // Correction de la faute de frappe ici : SLAM_Cooldown -> SLAM_COOLDOWN
        if (System.currentTimeMillis() - lastSlam > SLAM_COOLDOWN) {
            seismicSlam();
        }
    }

    /**
     * Une frappe violente qui projette les ennemis proches en l'air.
     */
    private void seismicSlam() {
        isPerformingAbility = true;
        lastSlam = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.5f);

        // Animation de préparation (lève les bras) - simulé par un délai
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead()) {
                    isPerformingAbility = false;
                    cancel();
                    return;
                }

                Location center = entity.getLocation();
                BlockData crackEffect = Material.STONE.createBlockData();
                center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
                center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.8f);

                for (Player p : getNearbyPlayers(5)) {
                    p.damage(damage, entity);
                    Vector knockup = p.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.8);
                    p.setVelocity(knockup.multiply(1.5));
                }
                isPerformingAbility = false;
            }
        }.runTaskLater(plugin, 15L); // 0.75s de préparation
    }

    /**
     * Lance un rocher avec une visée prédictive.
     */
    private void throwRock(Player target) {
        isPerformingAbility = true;
        lastThrow = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_GRASS_STEP, 1.5f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(entity.isDead()) { isPerformingAbility = false; return; }

                FallingBlock rock = entity.getWorld().spawnFallingBlock(
                        entity.getEyeLocation(),
                        Material.COBBLESTONE.createBlockData()
                );

                // Visée prédictive simple
                double distance = entity.getLocation().distance(target.getLocation());
                Vector direction = target.getEyeLocation().toVector().add(target.getVelocity().multiply(distance / 2.5)).subtract(entity.getEyeLocation().toVector()).normalize();

                rock.setVelocity(direction.multiply(1.2));
                rock.setDropItem(false);
                isPerformingAbility = false; // Permet au golem de bouger pendant que le rocher vole
            }
        }.runTaskLater(plugin, 10L);
    }

    /**
     * Attire plusieurs joueurs vers le Golem.
     */
    private void telluricGrasp(List<Player> targets) {
        isPerformingAbility = true;
        lastGrasp = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 2.0f, 0.6f);

        for (Player p : targets) {
            // Effet visuel sous le joueur
            p.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, p.getLocation(), 30, 0.5, 0.1, 0.5, 0.1, Material.DIRT.createBlockData());

            // Attire le joueur
            Vector pull = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
            p.setVelocity(pull.multiply(2.0).setY(0.4));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                isPerformingAbility = false;
            }
        }.runTaskLater(plugin, 30L);
    }

    /**
     * Crée un mur de pierre temporaire pour bloquer un joueur.
     */
    public void createEarthenBulwark(Player target) {
        isPerformingAbility = true;
        Location golemLoc = entity.getLocation();
        Location playerLoc = target.getLocation();

        Vector direction = playerLoc.toVector().subtract(golemLoc.toVector()).normalize();
        Vector perpendicular = direction.clone().rotateAroundY(Math.toRadians(90)); // Vecteur perpendiculaire

        // Point central du mur, à mi-chemin
        Location wallCenter = golemLoc.clone().add(direction.multiply(playerLoc.distance(golemLoc) / 2));

        golemLoc.getWorld().playSound(wallCenter, Sound.BLOCK_STONE_PLACE, 2.0f, 0.8f);

        List<Block> wallBlocks = new ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            for (int y = 0; y <= 1; y++) {
                Location blockLoc = wallCenter.clone().add(perpendicular.clone().multiply(i)).add(0, y, 0);
                if (blockLoc.getBlock().getType().isAir()) {
                    blockLoc.getBlock().setType(Material.COBBLESTONE);
                    wallBlocks.add(blockLoc.getBlock());
                }
            }
        }

        // Fait disparaître le mur après 6 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                for(Block b : wallBlocks) {
                    if (b.getType() == Material.COBBLESTONE) b.setType(Material.AIR);
                }
            }
        }.runTaskLater(plugin, 120L);

        new BukkitRunnable() { @Override public void run() { isPerformingAbility = false; } }.runTaskLater(plugin, 20L);
    }

    @Override
    public void specialAbility(Player target) {
        // Utilise la nouvelle capacité de muraille
        createEarthenBulwark(target);
    }

    protected List<Player> getNearbyPlayers(double radius) {
        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && (((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE))
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }
}