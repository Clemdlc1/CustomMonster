package fr.custommobs.mobs.advanced;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DragonFire extends CustomMob {

    private boolean isPerformingAbility = false;
    private final Random random = new Random();

    // --- Cooldowns ---
    private long lastBreath = 0;
    private final long BREATH_COOLDOWN = 12000;

    private long lastSwoop = 0;
    private final long SWOOP_COOLDOWN = 15000;

    private long lastMeteorShower = 0;
    private final long METEOR_SHOWER_COOLDOWN = 25000;

    public DragonFire(CustomMobsPlugin plugin) {
        super(plugin, "dragon_fire");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 250.0;
        this.damage = 16.0;
        this.speed = 0.3;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Blaze blaze = location.getWorld().spawn(location, Blaze.class);
        blaze.setCustomName("§4§lDrake Cendré");
        blaze.setCustomNameVisible(true);

        setupEntity(blaze);
        startVisualEffects();
        return blaze;
    }

    @Override
    protected void onPlayerNear(Player target) {
        ((Blaze) entity).setTarget(target);

        if (isPerformingAbility || entity.isDead()) return;

        long currentTime = System.currentTimeMillis();
        List<Player> nearbyPlayers = getNearbyPlayers(25);

        // --- IA Aérienne ---
        // 1. Multi-cible : Pluie de météores sur un groupe
        if (nearbyPlayers.size() > 1 && currentTime - lastMeteorShower > METEOR_SHOWER_COOLDOWN) {
            meteorShower(nearbyPlayers);
            return;
        }

        // 2. Mobilité/Attaque : Piqué sur une cible isolée
        if (currentTime - lastSwoop > SWOOP_COOLDOWN) {
            infernalSwoop(target);
            return;
        }

        // 3. Pression continue : Souffle de feu
        if (currentTime - lastBreath > BREATH_COOLDOWN) {
            incineratingBreath(target);
            return;
        }
    }

    @Override
    public void attack(Player target) {
        // L'attaque de base est maintenant le souffle
        if (System.currentTimeMillis() - lastBreath > BREATH_COOLDOWN) {
            incineratingBreath(target);
        }
    }

    /**
     * Bombarde une zone avec des projectiles enflammés.
     */
    private void meteorShower(List<Player> targets) {
        isPerformingAbility = true;
        lastMeteorShower = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 1.0f);

        // Calcule le point central du groupe
        Vector centerPoint = targets.stream()
                .map(p -> p.getLocation().toVector())
                .reduce(Vector::add)
                .map(v -> v.multiply(1.0 / targets.size()))
                .orElse(targets.getFirst().getLocation().toVector());

        new BukkitRunnable() {
            int waves = 0;
            @Override
            public void run() {
                if (waves >= 3 || entity.isDead()) {
                    isPerformingAbility = false;
                    cancel();
                    return;
                }

                for (int i = 0; i < 5; i++) {
                    Location targetLoc = centerPoint.toLocation(entity.getWorld());
                    targetLoc.add((random.nextDouble() - 0.5) * 20, 0, (random.nextDouble() - 0.5) * 20);
                    targetLoc = entity.getWorld().getHighestBlockAt(targetLoc).getLocation();

                    // Marqueur au sol
                    drawCircle(targetLoc, 2.5, Particle.FLAME);

                    // Fait tomber le météore après un délai
                    Location meteorSpawn = targetLoc.clone().add(0, 20, 0);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            SmallFireball meteor = meteorSpawn.getWorld().spawn(meteorSpawn, SmallFireball.class);
                            meteor.setDirection(new Vector(0, -1, 0));
                            meteor.setShooter(entity);
                            meteor.setIsIncendiary(true);
                        }
                    }.runTaskLater(plugin, 30L); // 1.5s de délai
                }
                waves++;
            }
        }.runTaskTimer(plugin, 20L, 30L);
    }

    /**
     * Effectue une charge en piqué sur la cible.
     */
    private void infernalSwoop(Player target) {
        isPerformingAbility = true;
        lastSwoop = System.currentTimeMillis();

        Location start = entity.getLocation();
        Location end = target.getLocation();
        Vector direction = end.toVector().subtract(start.toVector()).normalize();

        entity.getWorld().playSound(start, Sound.ENTITY_GHAST_WARN, 2.0f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 10 || entity.isDead()) {
                    isPerformingAbility = false;
                    cancel();
                    return;
                }

                entity.setVelocity(direction.clone().multiply(1.5));

                // Laisse une traînée de feu
                Location trailLoc = entity.getLocation();
                if (trailLoc.getBlock().getRelative(0, -1, 0).getType().isSolid()) {
                    trailLoc.getWorld().spawnParticle(Particle.FLAME, trailLoc, 10, 0.5, 0.1, 0.5, 0.05);
                    // Bonus : met le feu au sol temporairement
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Canalise un souffle de feu dévastateur.
     */
    private void incineratingBreath(Player target) {
        isPerformingAbility = true;
        lastBreath = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 80 || entity.isDead() || target.isDead()) {
                    isPerformingAbility = false;
                    cancel();
                    return;
                }

                Vector direction = target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize();
                for (double i = 1; i <= 18; i += 0.5) {
                    Location particleLoc = entity.getEyeLocation().add(direction.clone().multiply(i));
                    particleLoc.getWorld().spawnParticle(Particle.FLAME, particleLoc, 5, 0.4, 0.4, 0.4, 0.01);

                    for (Player p : getPlayersInCone(entity.getEyeLocation(), direction, i, 2.0)) {
                        p.setFireTicks(60);
                        p.damage(1.0, entity);
                    }
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void startVisualEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) { cancel(); return; }
                entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation(), 2, 0.5, 0.5, 0.5, 0);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // --- Fonctions Utilitaires ---
    protected List<Player> getNearbyPlayers(double radius) {
        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && (((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE))
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    private List<Player> getPlayersInCone(Location start, Vector direction, double maxDistance, double spread) {
        List<Player> players = new ArrayList<>();
        for(Player p : start.getWorld().getPlayers()) {
            if (p.getLocation().distance(start) > maxDistance) continue;
            Vector toPlayer = p.getEyeLocation().toVector().subtract(start.toVector()).normalize();
            if(toPlayer.dot(direction) > Math.cos(Math.toRadians(spread * 10))) {
                players.add(p);
            }
        }
        return players;
    }

    private void drawCircle(Location center, double radius, Particle particle) {
        for (double i = 0; i < 360; i += 10) {
            Location loc = center.clone().add(radius * Math.sin(i), 0.1, radius * Math.cos(i));
            center.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void specialAbility(Player target) {
        // Géré par la logique avancée de onPlayerNear
    }
}