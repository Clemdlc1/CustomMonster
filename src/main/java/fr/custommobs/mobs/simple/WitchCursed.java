package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class WitchCursed extends CustomMob {

    private final Random random = new Random();
    private final List<PotionEffectType> curses = Arrays.asList(
            PotionEffectType.POISON,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.HUNGER,
            PotionEffectType.WITHER // Ajout d'une malédiction plus dangereuse
    );

    // --- Cooldowns ---
    private long lastRepel = 0;
    private final long REPEL_COOLDOWN = 10000; // 10s

    private long lastCauldron = 0;
    private final long CAULDRON_COOLDOWN = 12000; // 12s

    private long lastNexus = 0;
    private final long NEXUS_COOLDOWN = 20000; // 20s

    // --- État ---
    private boolean isCasting = false;

    public WitchCursed(CustomMobsPlugin plugin) {
        super(plugin, "witch_cursed");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 45.0; // Augmentation de la durabilité
        this.damage = 0;       // Les dégâts proviennent des sorts
        this.speed = 0.25;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Witch witch = location.getWorld().spawn(location, Witch.class);

        witch.setCustomName("§5§lSorcière Néfaste");
        witch.setCustomNameVisible(true);

        setupEntity(witch);
        startCurseAura();
        return witch;
    }

    @Override
    protected void onPlayerNear(Player target) {
        ((Witch) entity).setTarget(target);

        if (isCasting) return; // Empêche de lancer plusieurs sorts à la fois

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // --- IA TACTIQUE ---
        // 1. Priorité Défensive : Repousser les joueurs trop proches
        if (distance < 4 && currentTime - lastRepel > REPEL_COOLDOWN) {
            repellingJinx();
            return;
        }

        // 2. Priorité Multi-Cible : Lancer le Nexus sur un groupe
        List<Player> nearbyPlayers = getNearbyPlayers(15);
        if (nearbyPlayers.size() > 1 && currentTime - lastNexus > NEXUS_COOLDOWN) {
            maledictionNexus(nearbyPlayers);
            return;
        }

        // 3. Contrôle de zone : Placer un piège sur une cible éloignée
        if (distance > 5 && currentTime - lastCauldron > CAULDRON_COOLDOWN) {
            cauldronTrap(target);
            return;
        }

        // 4. Attaque par défaut
        attack(target);
    }

    @Override
    public void attack(Player target) {
        // Attaque de base : projectile de malédiction
        castDebuff(target);
    }

    /**
     * Lance un projectile de malédiction sur une cible unique.
     */
    private void castDebuff(Player target) {
        isCasting = true;
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0f, 1.2f);

        // Simule un projectile rapide
        new BukkitRunnable() {
            Location current = entity.getEyeLocation();
            Vector direction = target.getEyeLocation().subtract(current).toVector().normalize();
            double distanceTraveled = 0;

            @Override
            public void run() {
                if (distanceTraveled > 20 || entity.isDead()) {
                    isCasting = false;
                    cancel();
                    return;
                }

                current.add(direction.clone().multiply(1.5));
                current.getWorld().spawnParticle(Particle.WITCH, current, 1, 0, 0, 0, 0);

                if (current.distance(target.getEyeLocation()) < 1.5) {
                    PotionEffectType curse = curses.get(random.nextInt(curses.size()));
                    target.addPotionEffect(new PotionEffect(curse, 140, 0));
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 1.0f);
                    isCasting = false;
                    cancel();
                    return;
                }
                distanceTraveled += 1.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Crée une flaque toxique au sol.
     */
    private void cauldronTrap(Player target) {
        isCasting = true;
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 0.8f);

        // Crée un nuage d'effet persistant (comme un souffle de dragon)
        AreaEffectCloud cloud = entity.getWorld().spawn(target.getLocation(), AreaEffectCloud.class);
        cloud.setParticle(Particle.WITCH);
        cloud.setRadius(3.0f);
        cloud.setDuration(200); // 10 secondes
        cloud.setWaitTime(10);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0), true);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1), true);

        new BukkitRunnable() {
            @Override
            public void run() {
                isCasting = false;
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * Repousse violemment les ennemis proches.
     */
    private void repellingJinx() {
        isCasting = true;
        lastRepel = System.currentTimeMillis();

        Location loc = entity.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.5f, 0.5f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.5, 0.5, 0.5);

        for (Player p : getNearbyPlayers(4)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));

            Vector knockback = p.getLocation().toVector().subtract(loc.toVector()).normalize();
            knockback.setY(0.4); // Projette aussi un peu en l'air
            p.setVelocity(knockback.multiply(2.0));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                isCasting = false;
            }
        }.runTaskLater(plugin, 15L);
    }

    /**
     * Crée une zone de malédiction intense sur un groupe de joueurs.
     */
    private void maledictionNexus(List<Player> targets) {
        isCasting = true;
        lastNexus = System.currentTimeMillis();

        // Cible le centre du groupe de joueurs
        Vector centerVector = new Vector(0, 0, 0);
        for (Player p : targets) {
            centerVector.add(p.getLocation().toVector());
        }
        Location center = centerVector.multiply(1.0 / targets.size()).toLocation(entity.getWorld());

        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.8f, 1.2f);

        new BukkitRunnable() {
            int duration = 120; // 6 secondes
            @Override
            public void run() {
                duration -= 5;
                if (duration <= 0 || entity.isDead()) {
                    isCasting = false;
                    cancel();
                    return;
                }

                // Effets visuels du Nexus
                for (int i = 0; i < 5; i++) {
                    double angle = (System.currentTimeMillis() / 100.0) + (random.nextDouble() * Math.PI * 2);
                    double radius = random.nextDouble() * 5.0;
                    Location particleLoc = center.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
                    center.getWorld().spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                }

                // Applique les malédictions toutes les 20 ticks (1s)
                if (duration % 20 == 0) {
                    for (Player p : getNearbyPlayersAt(center, 5)) {
                        p.addPotionEffect(new PotionEffect(curses.get(random.nextInt(curses.size())), 80, 1));
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.8f, 1.5f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void startCurseAura() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) { cancel(); return; }
                entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // --- Fonctions utilitaires ---
    protected List<Player> getNearbyPlayers(double radius) {
        return getNearbyPlayersAt(entity.getLocation(), radius);
    }

    protected List<Player> getNearbyPlayersAt(Location location, double radius) {
        return location.getWorld().getNearbyEntities(location, radius, radius, radius).stream()
                .filter(e -> e instanceof Player && ( ((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE ) )
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    @Override
    public void specialAbility(Player target) {
        // Géré par la logique plus avancée de onPlayerNear()
    }
}