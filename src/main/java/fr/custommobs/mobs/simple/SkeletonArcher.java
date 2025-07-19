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

public class SkeletonArcher extends CustomMob {

    // --- Cooldowns pour les capacités ---
    private long lastVolleyShot = 0;
    private final long VOLLEY_COOLDOWN = 8000; // 8 secondes

    private long lastCripplingShot = 0;
    private final long CRIPPLING_COOLDOWN = 12000; // 12 secondes

    private long lastDisengageLeap = 0;
    private final long DISENGAGE_COOLDOWN = 6000; // 6 secondes

    // --- Constantes de comportement ---
    private final double ENGAGE_DISTANCE = 20.0;
    private final double MELEE_DISTANCE = 5.0; // Distance à laquelle il tente de reculer

    public SkeletonArcher(CustomMobsPlugin plugin) {
        super(plugin, "skeleton_archer");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 30.0; // Santé légèrement augmentée
        this.damage = 0;       // Les dégâts sont gérés par les flèches
        this.speed = 0.25;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Skeleton skeleton = location.getWorld().spawn(location, Skeleton.class);

        skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        skeleton.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET)); // Équipement amélioré
        skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
        skeleton.getEquipment().setHelmetDropChance(0.0f);

        skeleton.setCustomName("§6§lArcher d'Élite");
        skeleton.setCustomNameVisible(true);

        // Immunité au soleil
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
            // Priorise la Flèche Incapacitante si disponible
            if (currentTime - lastCripplingShot > CRIPPLING_COOLDOWN) {
                cripplingShot(target);
                lastCripplingShot = currentTime;
            }
            // Sinon, lance une Rafale si disponible
            else if (currentTime - lastVolleyShot > VOLLEY_COOLDOWN) {
                volleyShot(target);
                lastVolleyShot = currentTime;
            }
            // Sinon, effectue une attaque de base
            else {
                attack(target);
            }
        }
    }

    /**
     * Attaque de base : un seul tir avec visée prédictive.
     * @param target La cible du tir.
     */
    @Override
    public void attack(Player target) {
        shootPredictiveArrow(target, 1.0, 0, null);
    }

    /**
     * Capacité spéciale : Tire une rafale de 3 flèches.
     * @param target La cible de la rafale.
     */
    private void volleyShot(Player target) {
        new BukkitRunnable() {
            int arrowsFired = 0;
            @Override
            public void run() {
                if (arrowsFired >= 3 || entity.isDead() || target.isDead()) {
                    cancel();
                    return;
                }

                shootPredictiveArrow(target, 1.2, 0, null);
                arrowsFired++;
            }
        }.runTaskTimer(plugin, 0L, 5L); // Tire une flèche toutes les 5 ticks (0.25s)
    }

    /**
     * Capacité spéciale : Tire une flèche qui ralentit la cible.
     * @param target La cible du tir.
     */
    private void cripplingShot(Player target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SKELETON_HURT, 1.2f, 0.8f);
        shootPredictiveArrow(target, 0.9, 4, PotionEffectType.SLOWNESS);
    }

    /**
     * Capacité d'évasion : Fait un saut en arrière pour créer de la distance.
     */
    private void disengageLeap() {
        Player target = findNearestPlayer(MELEE_DISTANCE + 2);
        if (target == null) return; // Sécurité

        Vector direction = entity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        direction.setY(0.4); // Hauteur du saut

        entity.setVelocity(direction.multiply(1.2)); // Force du saut
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SKELETON_STEP, 1.0f, 0.8f);
    }

    /**
     * Méthode centrale pour tirer une flèche avec visée prédictive.
     * @param target La cible.
     * @param speedMultiplier Multiplicateur de vitesse de la flèche.
     * @param powerLevel Niveau de l'enchantement "Power" de la flèche (0 pour aucun).
     * @param effectType L'effet de potion à appliquer sur la flèche (ou null).
     */
    private void shootPredictiveArrow(Player target, double speedMultiplier, int powerLevel, PotionEffectType effectType) {
        Location startLoc = entity.getEyeLocation();
        World world = startLoc.getWorld();

        // --- Calcul de la visée prédictive ---
        double distance = startLoc.distance(target.getEyeLocation());
        double arrowSpeed = 2.0 * speedMultiplier;
        double timeToTarget = distance / arrowSpeed;

        // Position prédite de la cible
        Vector predictedTargetPos = target.getEyeLocation().toVector().add(target.getVelocity().multiply(timeToTarget));

        // Direction vers la position prédite
        Vector direction = predictedTargetPos.subtract(startLoc.toVector()).normalize();

        // --- Compensation de la gravité ---
        // Une simple approximation qui fonctionne bien à moyenne portée
        double gravityCompensation = timeToTarget * 0.15 * (distance / 10.0);
        direction.setY(direction.getY() + gravityCompensation);

        // --- Création et tir de la flèche ---
        Arrow arrow = world.spawn(startLoc, Arrow.class);
        arrow.setShooter(entity);
        arrow.setVelocity(direction.multiply(arrowSpeed));
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

        if (powerLevel > 0) {
            arrow.setDamage(arrow.getDamage() + (powerLevel * 0.5 + 0.5)); // Simule enchantement Power
        }

        if (effectType != null) {
            arrow.addCustomEffect(new PotionEffect(effectType, 100, 1), true); // 5s de ralentissement
            arrow.setColor(Color.GRAY); // Couleur pour la distinguer
        }

        world.playSound(startLoc, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    @Override
    public void specialAbility(Player target) {
        // Cette méthode est désormais gérée par la logique de `onPlayerNear`
        // mais on peut l'utiliser pour un déclenchement alternatif
        if (Math.random() < 0.2) { // 20% de chance
            volleyShot(target);
        }
    }
}