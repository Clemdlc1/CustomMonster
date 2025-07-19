package fr.custommobs.mobs.advanced;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class GeodeAberration extends CustomMob {

    private final Random random = new Random();
    private boolean isVulnerable = true;

    // --- Cooldowns ---
    private long lastCrystalGrowth = 0;
    private final long CRYSTAL_GROWTH_COOLDOWN = 15000; // 15s

    private long lastGravityMissile = 0;
    private final long GRAVITY_MISSILE_COOLDOWN = 10000; // 10s

    public GeodeAberration(CustomMobsPlugin plugin) {
        super(plugin, "geode_aberration");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 200.0;
        this.damage = 12.0;
        this.speed = 0.0; // Les Shulkers ne bougent pas
    }

    @Override
    public LivingEntity spawn(Location location) {
        Shulker shulker = location.getWorld().spawn(location, Shulker.class);

        shulker.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Aberration Géodique");
        shulker.setCustomNameVisible(true);
        shulker.setColor(DyeColor.PURPLE); // Apparence d'améthyste
        shulker.setPeek(1.0f); // Commence en étant ouverte
        shulker.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));

        setupEntity(shulker);
        startAura();
        return shulker;
    }

    @Override
    protected void onPlayerNear(Player target) {
        ((Shulker) entity).setTarget(target);

        if (!isVulnerable || entity.isDead()) return;

        long currentTime = System.currentTimeMillis();

        // --- IA de Combat ---
        // Priorité 1 : Utiliser la capacité spéciale défensive si le cooldown est terminé
        if (currentTime - lastCrystalGrowth > CRYSTAL_GROWTH_COOLDOWN) {
            crystallineGrowth();
            return;
        }

        // Priorité 2 : Lancer le missile à lévitation
        if (currentTime - lastGravityMissile > GRAVITY_MISSILE_COOLDOWN) {
            gravityFluxMissile(target);
            return;
        }

        // Attaque par défaut : Volée d'éclats
        shardVolley(target);
    }

    @Override
    public void attack(Player target) {
        shardVolley(target);
    }

    private void shardVolley(Player target) {
        new BukkitRunnable() {
            int shots = 0;
            @Override
            public void run() {
                if (shots >= 3 || !isVulnerable || entity.isDead()) {
                    cancel();
                    return;
                }

                Location start = entity.getEyeLocation();
                Vector direction = target.getEyeLocation().subtract(start).toVector().normalize();

                drawProjectilePath(start, direction, Particle.CRIT);
                target.damage(damage / 3, entity);

                entity.getWorld().playSound(start, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.5f);
                shots++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void gravityFluxMissile(Player target) {
        lastGravityMissile = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.0f);

        ShulkerBullet bullet = entity.getWorld().spawn(entity.getEyeLocation(), ShulkerBullet.class);
        bullet.setShooter(entity);
        bullet.setTarget(target);
    }

    private void crystallineGrowth() {
        isVulnerable = false;
        lastCrystalGrowth = System.currentTimeMillis();

        Shulker shulker = (Shulker) entity;
        shulker.setPeek(0f); // Se ferme
        shulker.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 4)); // 6s de quasi-invincibilité

        Location center = entity.getLocation();
        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.8f);

        List<Block> crystalBlocks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double radius = 1 + random.nextDouble() * 5;
            double angle = random.nextDouble() * 2 * Math.PI;
            Location spikeLoc = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            spikeLoc = spikeLoc.getWorld().getHighestBlockAt(spikeLoc).getLocation();

            if (spikeLoc.getBlock().getType().isAir() && spikeLoc.clone().subtract(0,1,0).getBlock().getType().isSolid()) {
                spikeLoc.getBlock().setType(Material.AMETHYST_CLUSTER);
                crystalBlocks.add(spikeLoc.getBlock());
            }
        }

        for (Player p : getNearbyPlayers(6)) {
            p.damage(damage * 1.2, entity);
            p.setVelocity(p.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.5).setY(0.3));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : crystalBlocks) {
                    if (b.getType() == Material.AMETHYST_CLUSTER) {
                        b.setType(Material.AIR);
                        b.getWorld().spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 0.5, 0.5), 5, 0.1, 0.1, 0.1, 0);
                    }
                }

                if (!entity.isDead()) {
                    shulker.setPeek(1.0f);
                    isVulnerable = true;
                    entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.2f);
                }
            }
        }.runTaskLater(plugin, 120L); // Après 6 secondes
    }

    private void startAura() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                if (isVulnerable) {
                    entity.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, entity.getLocation().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void drawProjectilePath(Location start, Vector direction, Particle particle) {
        new BukkitRunnable() {
            double i = 0;
            @Override
            public void run() {
                if (i > 15) {
                    cancel();
                    return;
                }
                start.getWorld().spawnParticle(particle, start.clone().add(direction.clone().multiply(i)), 1, 0, 0, 0, 0);
                i += 1.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void specialAbility(Player target) {
        // Géré par l'IA cyclique
    }

    private List<Player> getNearbyPlayers(double radius) {
        if (entity == null) return new ArrayList<>();

        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && (((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE))
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }
}