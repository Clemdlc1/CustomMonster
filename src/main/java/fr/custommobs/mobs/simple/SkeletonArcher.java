package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public class SkeletonArcher extends CustomMob {

    // --- Type de flèche pour une gestion claire ---
    private enum ArrowType {
        NORMAL,
        CRIPPLING, // Incapacitante (ralentissement)
        EXPLOSIVE,
        FIRE
    }

    // --- Cooldowns pour les capacités ---
    private long lastArrowRain = 0;
    private final long ARROW_RAIN_COOLDOWN = 20000; // 20 secondes

    private long lastExplosiveShot = 0;
    private final long EXPLOSIVE_SHOT_COOLDOWN = 15000; // 15 secondes

    private long lastFireShot = 0;
    private final long FIRE_SHOT_COOLDOWN = 10000; // 10 secondes

    private long lastCripplingShot = 0;
    private final long CRIPPLING_COOLDOWN = 12000; // 12 secondes

    private long lastDisengageLeap = 0;
    private final long DISENGAGE_COOLDOWN = 6000; // 6 secondes

    // --- Constantes de comportement ---
    private final double ENGAGE_DISTANCE = 25.0; // Augmentation de la portée d'engagement
    private final double MELEE_DISTANCE = 5.0;   // Distance à laquelle il tente de reculer
    private final Random random = new Random();

    public SkeletonArcher(CustomMobsPlugin plugin) {
        super(plugin, "skeleton_archer");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 40.0; // Santé augmentée pour correspondre à sa dangerosité
        this.damage = 0;       // Les dégâts sont gérés par les flèches
        this.speed = 0.25;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Skeleton skeleton = location.getWorld().spawn(location, Skeleton.class);

        skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        skeleton.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
        skeleton.getEquipment().setHelmetDropChance(0.0f);

        skeleton.setCustomName("§c§lArcher Infernal");
        skeleton.setCustomNameVisible(true);
        skeleton.setShouldBurnInDay(false);

        setupEntity(skeleton);
        return skeleton;
    }

    /**
     * Surcharge de la boucle de comportement pour une IA plus tactique.
     */
    @Override
    protected void onPlayerNear(Player target) {
        ((Skeleton) entity).setTarget(target);

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // --- Prise de décision tactique ---
        // 1. Manœuvre d'évasion si le joueur est trop proche
        if (distance < MELEE_DISTANCE && currentTime - lastDisengageLeap > DISENGAGE_COOLDOWN) {
            disengageLeap();
            lastDisengageLeap = currentTime;
            return;
        }

        // 2. Comportement offensif si le joueur est à bonne distance
        if (distance <= ENGAGE_DISTANCE && entity.hasLineOfSight(target)) {
            // Priorité aux attaques spéciales les plus puissantes
            if (currentTime - lastArrowRain > ARROW_RAIN_COOLDOWN) {
                arrowRain(target);
                lastArrowRain = currentTime;
            } else if (currentTime - lastExplosiveShot > EXPLOSIVE_SHOT_COOLDOWN) {
                explosiveShot(target);
                lastExplosiveShot = currentTime;
            } else if (currentTime - lastCripplingShot > CRIPPLING_COOLDOWN) {
                cripplingShot(target);
                lastCripplingShot = currentTime;
            } else if (currentTime - lastFireShot > FIRE_SHOT_COOLDOWN) {
                fireShot(target);
                lastFireShot = currentTime;
            } else {
                // Attaque de base si aucune capacité n'est prête
                attack(target);
            }
        }
    }

    /**
     * Attaque de base : un seul tir normal.
     */
    @Override
    public void attack(Player target) {
        shootCustomArrow(target, ArrowType.NORMAL, 1.5);
    }

    /**
     * CAPACITÉ : Fait pleuvoir des flèches sur la cible.
     */
    private void arrowRain(Player target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GHAST_WARN, 1.5f, 1.0f);
        Location targetLocation = target.getLocation();

        new BukkitRunnable() {
            int arrowsFired = 0;
            @Override
            public void run() {
                if (arrowsFired >= 15 || entity.isDead()) { // 15 flèches au total
                    cancel();
                    return;
                }
                // Fait apparaître les flèches dans un rayon de 4 blocs autour de la cible
                double offsetX = (random.nextDouble() - 0.5) * 8;
                double offsetZ = (random.nextDouble() - 0.5) * 8;
                Location spawnLoc = targetLocation.clone().add(offsetX, 15, offsetZ);

                Arrow arrow = entity.getWorld().spawn(spawnLoc, Arrow.class);
                arrow.setShooter(entity);
                arrow.setVelocity(new Vector(0, -2, 0)); // Vitesse de chute
                arrow.setDamage(5.0); // Dégâts par flèche de la pluie
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

                arrowsFired++;
            }
        }.runTaskTimer(plugin, 20L, 3L); // Délai initial de 1s, puis une flèche toutes les 0.15s
    }

    /**
     * CAPACITÉ : Tire une flèche explosive.
     */
    private void explosiveShot(Player target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.2f, 1.2f);
        shootCustomArrow(target, ArrowType.EXPLOSIVE, 1.2);
    }

    /**
     * CAPACITÉ : Tire une flèche enflammée.
     */
    private void fireShot(Player target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 1.0f);
        shootCustomArrow(target, ArrowType.FIRE, 1.4);
    }


    /**
     * CAPACITÉ : Tire une flèche qui ralentit la cible.
     */
    private void cripplingShot(Player target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SKELETON_HURT, 1.2f, 0.8f);
        shootCustomArrow(target, ArrowType.CRIPPLING, 1.3);
    }

    /**
     * Capacité d'évasion : Fait un saut en arrière pour créer de la distance.
     */
    private void disengageLeap() {
        Player target = findNearestPlayer(MELEE_DISTANCE + 2);
        if (target == null) return;

        Vector direction = entity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        direction.setY(0.4);

        entity.setVelocity(direction.multiply(1.2));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SKELETON_STEP, 1.0f, 0.8f);
    }

    /**
     * Méthode centrale pour tirer tous les types de flèches.
     * La trajectoire est désormais simple et directe.
     *
     * @param target La cible.
     * @param type Le type de flèche (NORMAL, EXPLOSIVE, etc.).
     * @param speedMultiplier Multiplicateur de vitesse de la flèche.
     */
    private void shootCustomArrow(Player target, ArrowType type, double speedMultiplier) {
        if (entity.isDead() || target.isDead() || !target.isOnline()) {
            return;
        }

        Location startLoc = entity.getEyeLocation();
        World world = startLoc.getWorld();

        // Direction simple vers la cible
        Vector direction = target.getEyeLocation().subtract(startLoc).toVector().normalize();

        // Création et tir de la flèche
        Arrow arrow = world.spawn(startLoc, Arrow.class);
        arrow.setShooter(entity);
        arrow.setVelocity(direction.multiply(speedMultiplier * 1.5)); // Ajustement de la vitesse de base
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

        // Appliquer les effets spéciaux en fonction du type
        switch (type) {
            case CRIPPLING:
                arrow.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1), true); // 5s de ralentissement
                arrow.setColor(Color.GRAY);
                arrow.setDamage(4.0);
                break;

            case FIRE:
                arrow.setFireTicks(100); // Enflamme la cible pour 5 secondes
                arrow.setDamage(5.0);
                // Ajout d'un effet visuel pour la flèche
                addParticleTrail(arrow, Particle.FLAME);
                break;

            case EXPLOSIVE:
                arrow.setDamage(2.0); // La flèche elle-même fait peu de dégâts, l'explosion fait le reste
                // Ajout d'un effet visuel
                addParticleTrail(arrow, Particle.SMOKE);
                // Crée une explosion à l'impact
                handleExplosiveImpact(arrow);
                break;

            case NORMAL:
            default:
                arrow.setDamage(6.0); // Dégâts de base
                break;
        }

        world.playSound(startLoc, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    /**
     * Ajoute un effet de particule qui suit la flèche.
     */
    private void addParticleTrail(Arrow arrow, Particle particle) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround() || !arrow.isValid()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L); // Particule à chaque tick
    }

    /**
     * Gère l'impact d'une flèche explosive.
     */
    private void handleExplosiveImpact(Arrow arrow) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround() || !arrow.isValid()) {
                    // Crée une explosion non-destructrice pour les blocs
                    arrow.getWorld().createExplosion(arrow.getLocation(), 2.0f, false, false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Vérifie l'état de la flèche à chaque tick
    }

    @Override
    public void specialAbility(Player target) {
        // La logique est maintenant entièrement gérée dans onPlayerNear
        // On peut la laisser vide ou la lier à une attaque aléatoire pour des déclenchements externes.
        fireShot(target);
    }
}