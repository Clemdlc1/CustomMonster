package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class EndermanShadow extends CustomMob {

    // --- Cooldowns ---
    private long lastShadowStrike = 0;
    private final long SHADOW_STRIKE_COOLDOWN = 8000; // 8 secondes

    private long lastVoidGrasp = 0;
    private final long VOID_GRASP_COOLDOWN = 15000; // 15 secondes

    private long lastClone = 0;
    private final long CLONE_COOLDOWN = 18000; // 18 secondes

    private LivingEntity shadowClone = null;
    private final Random random = new Random();

    public EndermanShadow(CustomMobsPlugin plugin) {
        super(plugin, "enderman_shadow");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 50.0; // Plus résistant
        this.damage = 7.0;     // Dégâts de base
        this.speed = 0.32;     // Très rapide
    }

    @Override
    public LivingEntity spawn(Location location) {
        Enderman enderman = location.getWorld().spawn(location, Enderman.class);

        enderman.setCarriedBlock(Material.CRYING_OBSIDIAN.createBlockData()); // Matériau plus menaçant
        enderman.setCustomName("§5§lSpectre des Ténèbres");
        enderman.setCustomNameVisible(true);

        setupEntity(enderman);
        startShadowAura();
        return enderman;
    }

    @Override
    protected void onPlayerNear(Player target) {

        ((Enderman) entity).setTarget(target);

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // --- IA Décisionnelle ---
        // 1. Crée un clone pour semer la confusion si la capacité est disponible
        if (currentTime - lastClone > CLONE_COOLDOWN && (entity.getHealth() < maxHealth * 0.75 || Math.random() < 0.1)) {
            createShadowClone(target);
            lastClone = currentTime;
            return;
        }

        // 2. Utilise la Poigne du Vide sur un joueur à distance
        if (currentTime - lastVoidGrasp > VOID_GRASP_COOLDOWN && distance > 7 && distance < 20) {
            voidGrasp(target);
            lastVoidGrasp = currentTime;
            return;
        }

        // 3. Utilise la Frappe de l'Ombre pour une attaque surprise
        if (currentTime - lastShadowStrike > SHADOW_STRIKE_COOLDOWN && distance > 4) {
            shadowStrike(target);
            lastShadowStrike = currentTime;
            return;
        }

        // 4. Attaque de base au corps-à-corps
        if (distance <= 3.5) {
            attack(target);
        }
    }

    @Override
    public void attack(Player target) {
        // Attaque de mêlée qui inflige Cécité (Blindness)
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0)); // Durée courte mais déroutante
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.2f, 0.9f);
    }

    /**
     * Capacité de confusion : crée un clone illusoire.
     * @param target La cible à qui le clone fera face.
     */
    private void createShadowClone(Player target) {
        if (shadowClone != null && !shadowClone.isDead()) return;

        Location spawnLoc = findSafeTeleportLocation(entity.getLocation(), 4, false);
        if (spawnLoc == null) spawnLoc = entity.getLocation().add(random.nextBoolean() ? 2 : -2, 0, random.nextBoolean() ? 2 : -2);

        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        spawnLoc.getWorld().spawnParticle(Particle.PORTAL, spawnLoc, 50, 0.5, 1, 0.5, 0.5);

        Enderman clone = spawnLoc.getWorld().spawn(spawnLoc, Enderman.class);
        clone.setCustomName("§5Spectre des Ténèbres");
        clone.setCustomNameVisible(true);
        // Marque l'entité comme un clone pour la gérer dans un listener
        clone.setMetadata("shadow_clone", new FixedMetadataValue(plugin, true));
        clone.setTarget(target);
        this.shadowClone = clone;

        // Le clone disparaît après 8 secondes ou s'il est touché
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!clone.isDead()) {
                    clone.getWorld().spawnParticle(Particle.SMOKE, clone.getLocation(), 20);
                    clone.remove();
                }
            }
        }.runTaskLater(plugin, 160L); // 8 secondes
    }

    /**
     * Capacité de contrôle : Immobilise temporairement le joueur.
     * @param target La cible à immobiliser.
     */
    private void voidGrasp(Player target) {
        Location targetLoc = target.getLocation();
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WITHER_SKELETON_HURT, 1.5f, 0.5f);

        // Effet visuel de vrilles sortant du sol
        for (int i = 0; i < 15; i++) {
            double x = (random.nextDouble() - 0.5) * 3;
            double z = (random.nextDouble() - 0.5) * 3;
            target.getWorld().spawnParticle(Particle.SQUID_INK, targetLoc.clone().add(x, 0.5, z), 1, 0, 0, 0, 0);
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4)); // 3s de quasi-immobilisation
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
    }

    /**
     * Attaque principale : se téléporte près de la cible et frappe.
     * @param target La cible de l'attaque.
     */
    public void shadowStrike(Player target) {
        Location safeLoc = findSafeTeleportLocation(target.getLocation(), 4, true);
        if (safeLoc == null) return; // Pas de lieu sûr trouvé, on annule l'attaque

        // Effets visuels de départ
        entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation(), 30, 0.5, 1, 0.5, 0.5);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        entity.teleport(safeLoc);

        // Effets visuels à l'arrivée
        entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, safeLoc, 30, 0.5, 1, 0.5, 0.5);
        entity.getWorld().playSound(safeLoc, Sound.ENTITY_ENDERMAN_SCREAM, 1.2f, 1.2f);

        // Attaque rapide après la téléportation
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isDead() && entity.getLocation().distanceSquared(target.getLocation()) < 16) {
                    target.damage(damage, entity);
                    attack(target); // Applique aussi la cécité
                }
            }
        }.runTaskLater(plugin, 5L); // 1/4 de seconde
    }

    /**
     * Algorithme amélioré pour trouver une position de téléportation sûre.
     * @param center Le centre de la recherche (un joueur ou une position).
     * @param radius Le rayon de recherche.
     * @param behindPlayer Tente de se téléporter derrière le joueur si possible.
     * @return Une Location sûre, ou null si aucune n'est trouvée.
     */
    private Location findSafeTeleportLocation(Location center, double radius, boolean behindPlayer) {
        List<Location> possibleLocations = new ArrayList<>();

        if (behindPlayer && center.getPitch() != 0) { // Tente de se mettre derrière
            Vector direction = center.getDirection().setY(0).normalize().multiply(-2.5);
            possibleLocations.add(center.clone().add(direction));
        }

        for (int i = 0; i < 16; i++) { // Ajoute des points aléatoires
            double angle = random.nextDouble() * 2 * Math.PI;
            double r = 2 + (random.nextDouble() * (radius - 2));
            possibleLocations.add(center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
        }

        Collections.shuffle(possibleLocations); // Aléatoirise l'ordre

        for (Location loc : possibleLocations) {
            loc = loc.getWorld().getHighestBlockAt(loc).getLocation().add(0, 1, 0);
            if (isLocationSafe(loc)) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Vérifie si un emplacement est sûr pour se téléporter.
     * @param loc L'emplacement à vérifier.
     * @return true si l'emplacement est sûr.
     */
    private boolean isLocationSafe(Location loc) {
        if (loc.getBlockY() > loc.getWorld().getMaxHeight() || loc.getBlockY() < 1) return false;

        Block ground = loc.clone().subtract(0, 1, 0).getBlock();
        Block body = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block head2 = loc.clone().add(0, 2, 0).getBlock(); // Endermen font 3 blocs de haut

        // Le sol doit être solide et non dangereux
        if (!ground.getType().isSolid() || ground.getType().name().contains("LAVA") || ground.getType() == Material.MAGMA_BLOCK) {
            return false;
        }
        // Le corps et la tête doivent être dans des blocs non solides (air, herbe, etc.)
        return !body.getType().isSolid() && !head.getType().isSolid() && !head2.getType().isSolid();
    }

    private void startShadowAura() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation().add(0, 1.5, 0), 5, 0.5, 1, 0.5, 0.1);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    @Override
    public void specialAbility(Player target) {
        // Géré par onPlayerNear pour une meilleure logique
    }
}