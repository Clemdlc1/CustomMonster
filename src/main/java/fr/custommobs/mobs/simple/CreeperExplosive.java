package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class CreeperExplosive extends CustomMob {

    // --- Cooldowns ---
    private long lastExplosion = 0;
    private final long EXPLOSION_COOLDOWN = 5000; // 5 secondes

    private long lastLeap = 0;
    private final long LEAP_COOLDOWN = 12000; // 12 secondes

    // --- États ---
    private boolean isExploding = false;
    private boolean isLeaping = false;

    public CreeperExplosive(CustomMobsPlugin plugin) {
        super(plugin, "creeper_explosive");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 45.0; // Plus de vie car il ne meurt pas en explosant
        this.damage = 0;       // Les dégâts sont gérés par l'explosion
        this.speed = 0.28;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Creeper creeper = location.getWorld().spawn(location, Creeper.class);

        // On ne configure plus le rayon ou le temps de mèche vanilla
        creeper.setCustomName("§4§lDétonateur Ambulant");
        creeper.setCustomNameVisible(true);

        setupEntity(creeper);
        return creeper;
    }

    @Override
    protected void onPlayerNear(Player target) {

        ((Creeper) entity).setTarget(target);

        if (isExploding || isLeaping) return; // Ne fait rien s'il est déjà en action

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // Priorité 1 : Saut Explosif si la cible est à moyenne distance
        if (currentTime - lastLeap > LEAP_COOLDOWN && distance > 6 && distance < 15) {
            explosiveLeap(target);
            return;
        }

        // Priorité 2 : Explosion normale si la cible est très proche
        if (currentTime - lastExplosion > EXPLOSION_COOLDOWN && distance <= 4) {
            attack(target);
        }
    }

    /**
     * Déclenche la détonation contrôlée.
     * @param target Le joueur (peut être null).
     */
    @Override
    public void attack(Player target) {
        if (isExploding) return;
        isExploding = true;
        lastExplosion = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.5f, 1.0f);

        if (entity instanceof Creeper) {
            ((Creeper) entity).setIgnited(true); // Effet visuel du creeper qui s'apprête à exploser
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead()) {
                    isExploding = false;
                    return;
                }

                if (entity instanceof Creeper) {
                    ((Creeper) entity).setIgnited(false); // Annule l'effet visuel
                }

                // --- L'EXPLOSION PERSONNALISÉE ---
                // Crée une explosion qui ne détruit pas les blocs et ne met pas le feu.
                entity.getWorld().createExplosion(entity.getLocation(), 3.5F, false, false, entity);

                // Effet visuel supplémentaire
                entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 1);

                // Le creeper est projeté en arrière par son propre souffle
                Vector knockback = entity.getLocation().getDirection().multiply(-1).setY(0.5);
                entity.setVelocity(knockback);

                isExploding = false;
            }
        }.runTaskLater(plugin, 30L); // Délai de 1.5 secondes avant l'explosion
    }

    /**
     * Le Détonateur effectue un grand saut et explose à l'atterrissage.
     * @param target La cible du saut.
     */
    public void explosiveLeap(Player target) {
        if (isLeaping || isExploding) return;
        isLeaping = true;
        lastLeap = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SLIME_JUMP, 1.2f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks == 0) {
                    // Calcul du vecteur de saut au début
                    Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
                    direction.multiply(1.4).setY(0.8); // Ajuste la force et la hauteur du saut
                    entity.setVelocity(direction);
                }

                // Condition d'arrêt : atterrissage, mort ou trop de temps écoulé
                if ((ticks > 10 && entity.isOnGround()) || entity.isDead() || ticks > 80) {
                    // Déclenche l'explosion à l'atterrissage
                    if (!entity.isDead()) {
                        attack(null); // Déclenche l'explosion sans cible spécifique
                    }
                    isLeaping = false;
                    cancel();
                    return;
                }

                // Particules pendant le saut
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation(), 5, 0.3, 0.3, 0.3, 0);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void specialAbility(Player target) {
        // Cette méthode peut maintenant appeler la nouvelle capacité spéciale
        if (System.currentTimeMillis() - lastLeap > LEAP_COOLDOWN) {
            explosiveLeap(target);
        }
    }
}