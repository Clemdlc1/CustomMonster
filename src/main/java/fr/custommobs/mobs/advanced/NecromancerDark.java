package fr.custommobs.mobs.advanced;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class NecromancerDark extends CustomMob {

    private final List<Vex> summonedVexes = new ArrayList<>();
    private boolean isCasting = false;

    // --- Cooldowns ---
    private long lastSummon = 0;
    private final long SUMMON_COOLDOWN = 20000; // 20s

    private long lastSoulHarvest = 0;
    private final long SOUL_HARVEST_COOLDOWN = 15000; // 15s

    private long lastShadowPact = 0;
    private final long SHADOW_PACT_COOLDOWN = 12000; // 12s

    public NecromancerDark(CustomMobsPlugin plugin) {
        super(plugin, "necromancer_dark");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 180.0;
        this.damage = 10.0;
        this.speed = 0.22;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Evoker evoker = location.getWorld().spawn(location, Evoker.class);

        evoker.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        evoker.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
        evoker.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        evoker.getEquipment().setItemInMainHandDropChance(0.0f);
        evoker.getEquipment().setHelmetDropChance(0.0f);
        evoker.getEquipment().setChestplateDropChance(0.0f);

        evoker.setCustomName("§5§lArchiliche");
        evoker.setCustomNameVisible(true);
        evoker.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        setupEntity(evoker);
        startDarkAura();
        return evoker;
    }

    @Override
    protected void onPlayerNear(Player target) {
        ((Evoker) entity).setTarget(target);

        if (isCasting || entity.isDead()) return;

        long currentTime = System.currentTimeMillis();
        double distance = entity.getLocation().distance(target.getLocation());

        // --- IA TACTIQUE ---
        // 1. Survie : se téléporter si un joueur est trop proche
        if (distance < 5 && currentTime - lastShadowPact > SHADOW_PACT_COOLDOWN) {
            shadowPact();
            return;
        }

        // 2. Contrôle du terrain : Invoquer des sbires
        summonedVexes.removeIf(LivingEntity::isDead); // Nettoyage
        if (summonedVexes.size() < 3 && currentTime - lastSummon > SUMMON_COOLDOWN) {
            summonSouls(target);
            return;
        }

        // 3. Punition : Canaliser Moisson d'Âmes sur une cible à distance
        if (distance > 8 && currentTime - lastSoulHarvest > SOUL_HARVEST_COOLDOWN) {
            soulHarvest(target);
            return;
        }

        // 4. Attaque de base
        attack(target);
    }

    @Override
    public void attack(Player target) {
        // Attaque de base : Éclair d'Entropie
        entropyBolt(target);
    }

    /**
     * Invoque des Vexes pour l'assister.
     */
    private void summonSouls(Player target) {
        isCasting = true;
        lastSummon = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.7f);

        new BukkitRunnable() {
            int summonedCount = 0;
            @Override
            public void run() {
                if (summonedCount >= 2 || entity.isDead()) {
                    isCasting = false;
                    cancel();
                    return;
                }

                Location spawnLoc = entity.getLocation().add((Math.random() - 0.5) * 4, 1, (Math.random() - 0.5) * 4);
                spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc, 20, 0.5, 1, 0.5, 0.1);

                Vex vex = entity.getWorld().spawn(spawnLoc, Vex.class);
                vex.setCustomName("§8Âme Tourmentée");
                vex.setTarget(target);

                // IMPORTANT: Marque le sbire pour les statistiques
                vex.setMetadata("boss_minion", new FixedMetadataValue(plugin, true));
                vex.setMetadata("summoned_by_boss", new FixedMetadataValue(plugin, entity.getUniqueId().toString()));
                vex.setMetadata("boss_type", new FixedMetadataValue(plugin, "necromancer_dark"));

                summonedVexes.add(vex);

                // Les Vexes ont une durée de vie limitée
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!vex.isDead()) {
                            vex.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, vex.getLocation(), 10);
                            vex.damage(500);
                        }
                    }
                }.runTaskLater(plugin, 400L); // 20 secondes de vie

                summonedCount++;
            }
        }.runTaskTimer(plugin, 10L, 20L);
    }

    /**
     * Canalise un rayon qui draine la vie.
     */
    private void soulHarvest(Player target) {
        isCasting = true;
        lastSoulHarvest = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 2.0f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 100 || entity.isDead() || target.isDead() || !entity.hasLineOfSight(target) || entity.getLocation().distance(target.getLocation()) > 20) {
                    isCasting = false;
                    cancel();
                    return;
                }

                // Dessine le rayon
                Location start = entity.getEyeLocation();
                Vector dir = target.getEyeLocation().subtract(start).toVector().normalize();
                for (double i = 0; i < start.distance(target.getEyeLocation()); i += 0.5) {
                    start.getWorld().spawnParticle(Particle.SOUL, start.clone().add(dir.clone().multiply(i)), 1, 0, 0, 0, 0);
                }

                // Applique les effets
                target.damage(0.5, entity);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Se sacrifie un Vex pour se téléporter.
     */
    private void shadowPact() {
        summonedVexes.removeIf(LivingEntity::isDead);
        if (summonedVexes.isEmpty()) return;

        isCasting = true;
        lastShadowPact = System.currentTimeMillis();

        Vex sacrifice = summonedVexes.get(0);
        Location destination = sacrifice.getLocation();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
        entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation(), 30, 0.5, 1, 0.5, 0.5);

        entity.teleport(destination);
        sacrifice.damage(500); // Tue le Vex

        destination.getWorld().createExplosion(destination, 2.0F, false, false, entity);
        destination.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, destination, 50, 1, 1, 1, 0.1);
        destination.getWorld().playSound(destination, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        new BukkitRunnable() { @Override public void run() { isCasting = false; } }.runTaskLater(plugin, 10L);
    }

    /**
     * Lance un projectile nécrotique.
     */
    private void entropyBolt(Player target) {
        isCasting = true;
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.5f, 1.0f);

        WitherSkull skull = entity.launchProjectile(WitherSkull.class);
        skull.setDirection(target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize().multiply(1.2));
        skull.setShooter(entity);

        new BukkitRunnable() { @Override public void run() { isCasting = false; } }.runTaskLater(plugin, 30L);
    }

    private void startDarkAura() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                entity.getWorld().spawnParticle(Particle.SOUL, entity.getLocation().add(0, 1, 0), 2, 0.5, 1, 0.5, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    @Override
    public void specialAbility(Player target) {
        // Géré par la logique avancée de onPlayerNear
    }
}