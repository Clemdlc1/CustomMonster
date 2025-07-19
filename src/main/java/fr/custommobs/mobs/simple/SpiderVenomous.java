package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class SpiderVenomous extends CustomMob {

    // --- Cooldowns ---
    private long lastLeap = 0;
    private final long LEAP_COOLDOWN = 8000;

    private long lastWebSpit = 0;
    private final long WEBSPIT_COOLDOWN = 12000;

    // --- État ---
    private boolean isPerformingAbility = false;

    public SpiderVenomous(CustomMobsPlugin plugin) {
        super(plugin, "spider_venomous");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 50.0; // Un peu plus robuste
        this.damage = 4.0;
        this.speed = 0.35;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Spider spider = location.getWorld().spawn(location, Spider.class);
        spider.setCustomName("§2§lTisseuse Malsaine");
        spider.setCustomNameVisible(true);

        setupEntity(spider);
        return spider;
    }

    @Override
    protected void onPlayerNear(Player target) {
        ((Spider) entity).setTarget(target);

        if (isPerformingAbility) return;

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // --- IA de Chasseuse ---
        // 1. Piège la cible à distance
        if (distance > 7 && distance < 15 && currentTime - lastWebSpit > WEBSPIT_COOLDOWN) {
            webSpit(target);
            return;
        }

        // 2. Saut d'embuscade pour désorienter
        if (distance > 4 && distance <= 9 && currentTime - lastLeap > LEAP_COOLDOWN) {
            ambushLeap(target);
            return;
        }

        // 3. Morsure si au contact
        if (distance <= 2.5) {
            attack(target);
        }
    }

    /**
     * Morsure paralysante qui applique poison et lenteur.
     */
    @Override
    public void attack(Player target) {
        if (entity.getLocation().distanceSquared(target.getLocation()) <= 6.25) { // 2.5 * 2.5
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2)); // Lenteur paralysante

            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1.2f, 1.8f); // Son de morsure aigu
            target.getWorld().spawnParticle(Particle.EGG_CRACK, target.getEyeLocation(), 10, 0.5, 0.5, 0.5);
        }
    }

    /**
     * Saute par-dessus la cible pour atterrir derrière elle.
     */
    private void ambushLeap(Player target) {
        isPerformingAbility = true;
        lastLeap = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 1.5f);

        // Calcule un point d'atterrissage derrière le joueur
        Vector behind = target.getLocation().getDirection().normalize().multiply(-3);
        Location landingSpot = target.getLocation().add(behind);

        // Calcule le vecteur du saut
        Vector jumpVector = landingSpot.toVector().subtract(entity.getLocation().toVector());
        jumpVector.setY(jumpVector.getY() + 5.0).normalize().multiply(1.6); // Ajuste la hauteur et la puissance
        if(jumpVector.getY() < 0.6) jumpVector.setY(0.6); // Assure une hauteur minimale

        entity.setVelocity(jumpVector);

        new BukkitRunnable() {
            @Override
            public void run() {
                isPerformingAbility = false;
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * Crache un projectile qui crée une nappe de toiles d'araignée.
     */
    private void webSpit(Player target) {
        isPerformingAbility = true;
        lastWebSpit = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.0f, 1.0f);

        // Simule un projectile de toile
        new BukkitRunnable() {
            Location current = entity.getEyeLocation();
            Vector direction = target.getEyeLocation().subtract(current).toVector().normalize();
            double distanceTraveled = 0;

            @Override
            public void run() {
                if (distanceTraveled > 20 || entity.isDead()) {
                    isPerformingAbility = false;
                    cancel();
                    return;
                }

                current.add(direction);
                current.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, current, 1, 0, 0, 0, 0);

                // Si le projectile touche un joueur ou est proche d'un bloc
                if (!current.getBlock().isPassable() || current.distanceSquared(target.getLocation()) < 4) {
                    createWebPatch(current);
                    isPerformingAbility = false;
                    cancel();
                    return;
                }
                distanceTraveled++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Crée une zone de toiles d'araignée temporaires.
     * @param center Le centre de la zone.
     */
    private void createWebPatch(Location center) {
        center.getWorld().playSound(center, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 1.2f);
        List<Block> webBlocks = new ArrayList<>();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = center.clone().add(x, 0, z).getBlock();
                if (block.getType().isAir()) {
                    block.setType(Material.COBWEB);
                    webBlocks.add(block);
                }
            }
        }

        // Fait disparaître les toiles après 7 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                for(Block b : webBlocks) {
                    if (b.getType() == Material.COBWEB) {
                        b.setType(Material.AIR);
                    }
                }
            }
        }.runTaskLater(plugin, 140L); // 7 secondes
    }

    @Override
    public void specialAbility(Player target) {
        // La logique est gérée par onPlayerNear pour plus de tactique,
        // mais on peut forcer une capacité ici si on veut.
        if (System.currentTimeMillis() - lastLeap > LEAP_COOLDOWN) {
            ambushLeap(target);
        }
    }
}