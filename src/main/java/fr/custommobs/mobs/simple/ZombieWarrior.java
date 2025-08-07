package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ZombieWarrior extends CustomMob {

    // --- Cooldowns pour les capacités (en millisecondes) ---
    private long lastChargeAttempt = 0;
    private final long CHARGE_COOLDOWN = 10000; // 10 secondes

    private long lastGroundSlam = 0;
    private final long GROUND_SLAM_COOLDOWN = 15000; // 15 secondes

    public ZombieWarrior(CustomMobsPlugin plugin) {
        super(plugin, "zombie_warrior");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 40.0; // Santé augmentée
        this.damage = 7.0;    // Dégâts de base augmentés
        this.speed = 0.28;   // Légèrement plus rapide
    }

    @Override
    public LivingEntity spawn(Location location) {
        Zombie zombie = location.getWorld().spawn(location, Zombie.class);

        // --- Équipement amélioré ---
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        zombie.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE)); // Amélioré
        zombie.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        zombie.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));

        // Empêche le drop de l'équipement
        zombie.getEquipment().setItemInMainHandDropChance(0.0f);
        zombie.getEquipment().setHelmetDropChance(0.0f);
        zombie.getEquipment().setChestplateDropChance(0.0f);
        zombie.getEquipment().setLeggingsDropChance(0.0f);
        zombie.getEquipment().setBootsDropChance(0.0f);

        zombie.setCustomName("§c§lGuerrier Zombie");
        zombie.setCustomNameVisible(true);

        zombie.setShouldBurnInDay(false);

        setupEntity(zombie);
        return zombie;
    }

    /**
     * Surcharge de la boucle de comportement pour une logique plus complexe et intelligente.
     */
    @Override
    protected void startBehaviors() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                // Utilise une méthode optimisée pour trouver le joueur le plus proche
                Player target = findNearestPlayerOptimized(16); // Rayon de détection de 16 blocs
                if (target != null) {
                    onPlayerNear(target);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // S'exécute chaque seconde
    }

    /**
     * Méthode principale de l'IA. Décide de l'action à entreprendre.
     * @param target Le joueur à cibler.
     */
    @Override
    protected void onPlayerNear(Player target) {
        ((Zombie) entity).setTarget(target);

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // --- Prise de décision ---
        // Priorité 1 : Frappe sismique si plusieurs joueurs sont proches (capacité multi-menace)
        if (currentTime - lastGroundSlam > GROUND_SLAM_COOLDOWN && distance < 6) {
            List<Player> nearbyPlayers = getNearbyPlayers(5);
            if (nearbyPlayers.size() > 1) {
                groundSlam(nearbyPlayers);
                lastGroundSlam = currentTime;
                return; // Action effectuée, fin du tour de décision
            }
        }

        // Priorité 2 : Charge si le joueur est à moyenne portée
        if (currentTime - lastChargeAttempt > CHARGE_COOLDOWN && distance > 5 && distance < 12) {
            charge(target);
            lastChargeAttempt = currentTime;
            return;
        }

        // Priorité 3 : Attaque de mêlée standard si assez proche
        if (distance < 3) {
            attack(target);
        }
    }

    /**
     * Attaque de mêlée de base. Applique désormais aussi un effet de faiblesse.
     * @param target Le joueur à attaquer.
     */
    @Override
    public void attack(Player target) {
        if (entity.hasLineOfSight(target)) {
            // Applique l'effet de Lenteur, comme avant
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0)); // Durée augmentée

            // 25% de chance d'appliquer également Faiblesse
            if (Math.random() < 0.25) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_VINDICATOR_HURT, 1.2f, 0.8f);
            }
        }
    }

    /**
     * Le Guerrier Zombie charge vers une cible.
     * @param target Le joueur à charger.
     */
    private void charge(Player target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.5f, 1.2f);
        final double originalSpeed = entity.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Condition d'arrêt : la charge dure 2s, ou le mob/la cible est mort/e
                if (ticks >= 40 || entity.isDead() || target.isDead() || entity.getWorld() != target.getWorld()) {
                    entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(originalSpeed); // Restaure la vitesse
                    cancel();
                    return;
                }

                // --- Animation et Effet ---
                entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);

                // --- Logique ---
                // Augmente la vitesse pour la charge
                entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed * 2.5);

                // Cible agressivement le joueur pour garder le focus
                if (entity instanceof Zombie) {
                    ((Zombie) entity).setTarget(target);
                }

                // Vérifie l'impact
                if (entity.getLocation().distance(target.getLocation()) < 2.0) {
                    target.damage(damage * 1.5, entity); // Dégâts augmentés à l'impact
                    target.setVelocity(entity.getLocation().getDirection().multiply(1.5).setY(0.4)); // Recul
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);

                    // Termine la charge après l'impact
                    entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(originalSpeed);
                    cancel();
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Le Guerrier Zombie frappe le sol, créant une attaque de zone (AoE).
     * @param playersToAffect La liste des joueurs à affecter.
     */
    private void groundSlam(List<Player> playersToAffect) {
        Location slamCenter = entity.getLocation();

        // --- Son et Animation ---
        slamCenter.getWorld().playSound(slamCenter, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.7f);

        // Particules de fissure au sol
        for (int i = 0; i < 360; i += 20) {
            double angle = Math.toRadians(i);
            for (double radius = 1; radius <= 4; radius += 0.5) {
                Location particleLoc = slamCenter.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                particleLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, particleLoc, 1, 0, 0, 0, 0, Material.DIRT.createBlockData());
            }
        }

        // --- Logique de l'effet ---
        for (Player p : playersToAffect) {
            if (p.getLocation().distance(slamCenter) <= 4.5) {
                p.damage(damage, entity);
                Vector knockup = new Vector(0, 0.5, 0); // Projette les joueurs en l'air
                p.setVelocity(p.getVelocity().add(knockup));
            }
        }
    }

    /**
     * Méthode optimisée pour trouver le joueur le plus proche dans un rayon donné.
     * @param radius Le rayon de recherche.
     * @return Le joueur le plus proche, ou null si aucun n'est trouvé.
     */
    protected Player findNearestPlayerOptimized(double radius) {
        // Trouve les joueurs dans un rayon carré, puis filtre par distance et ligne de vue
        return entity.getWorld().getPlayers().stream()
                .filter(p -> (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                        && p.getLocation().distanceSquared(entity.getLocation()) < radius * radius
                        && p.hasLineOfSight(entity))
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(entity.getLocation())))
                .orElse(null);
    }

    /**
     * Récupère une liste des joueurs proches.
     * @param radius Le rayon de recherche.
     * @return Une liste de joueurs.
     */
    protected List<Player> getNearbyPlayers(double radius) {
        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                .collect(Collectors.toList());
    }

    @Override
    public void specialAbility(Player target) {
        // Cette méthode est maintenant gérée par la logique de `onPlayerNear`,
        // mais nous pouvons la conserver pour des usages futurs ou pour déclencher une capacité aléatoirement.
        if (System.currentTimeMillis() - lastGroundSlam > GROUND_SLAM_COOLDOWN) {
            List<Player> nearbyPlayers = getNearbyPlayers(5);
            if (!nearbyPlayers.isEmpty()) {
                groundSlam(nearbyPlayers);
                lastGroundSlam = System.currentTimeMillis();
            }
        }
    }
}