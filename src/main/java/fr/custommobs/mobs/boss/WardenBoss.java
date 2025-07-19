package fr.custommobs.mobs.boss;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class WardenBoss extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> sculkMinions = new ArrayList<>();
    private final Map<Player, Integer> playerAnger = new HashMap<>();
    private final Set<Location> sculkInfectedBlocks = new HashSet<>();

    // --- ÉTAT DU BOSS ---
    private BossPhase currentPhase = BossPhase.DORMANT;
    private int angerLevel = 0; // 0-100
    private boolean isBurrowed = false;
    private boolean isDetectingVibrations = true;
    private Location burrowLocation;
    private Player primaryTarget = null;

    // --- COOLDOWNS ---
    private long lastSonicBoom = 0;
    private final long SONIC_BOOM_COOLDOWN = 8000; // 8s

    private long lastSeismicJump = 0;
    private final long SEISMIC_JUMP_COOLDOWN = 15000; // 15s

    private long lastSculkSpread = 0;
    private final long SCULK_SPREAD_COOLDOWN = 20000; // 20s

    private long lastBurrow = 0;
    private final long BURROW_COOLDOWN = 30000; // 30s

    private long lastEarthquake = 0;
    private final long EARTHQUAKE_COOLDOWN = 25000; // 25s

    private enum BossPhase {
        DORMANT,    // Initial - Réveillé par les vibrations
        HUNTING,    // 100-60% HP - Traque les joueurs
        ENRAGED,    // 60-30% HP - Colère maximale
        CATACLYSM   // 30-0% HP - Destruction totale
    }

    public WardenBoss(CustomMobsPlugin plugin) {
        super(plugin, "warden_boss");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 1000.0; // Très résistant
        this.damage = 35.0; // Dégâts les plus élevés
        this.speed = 0.4; // Relativement lent mais implacable
    }

    @Override
    public LivingEntity spawn(Location location) {
        Warden warden = location.getWorld().spawn(location, Warden.class);

        warden.setCustomName("§0§l§k⬛§r §8§lGARDIEN DES ABYSSES §0§l§k⬛§r");
        warden.setCustomNameVisible(true);

        // Immunités de boss des profondeurs
        warden.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        warden.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 2, false, false));

        this.burrowLocation = location.clone();

        setupEntity(warden);
        startDormantPhase();
        createSculkArena();

        return warden;
    }

    @Override
    protected void onPlayerNear(Player target) {
        if (entity.isDead()) return;

        // Système de détection par vibrations
        handleVibrationDetection(target);
        updatePhase();
        cleanupMinions();

        long currentTime = System.currentTimeMillis();
        double distance = entity.getLocation().distance(target.getLocation());
        List<Player> nearbyPlayers = getNearbyPlayers(25);

        // === IA MULTIJOUEUR AMÉLIORÉE ===
        // Sélection de cible basée sur la colère et rotation
        if (Math.random() < 0.25 && nearbyPlayers.size() > 1) { // 25% de chance de changer
            selectPrimaryTarget(nearbyPlayers);
            if (primaryTarget != null && !primaryTarget.isDead()) {
                target = primaryTarget;
            }
        }

        // === IA BASÉE SUR LES PHASES ===
        switch (currentPhase) {
            case DORMANT:
                handleDormantPhase(target, distance, currentTime);
                break;
            case HUNTING:
                handleHuntingPhase(target, distance, currentTime, nearbyPlayers);
                break;
            case ENRAGED:
                handleEnragedPhase(target, distance, currentTime, nearbyPlayers);
                break;
            case CATACLYSM:
                handleCataclysmPhase(target, distance, currentTime, nearbyPlayers);
                break;
        }
    }

    private void handleDormantPhase(Player target, double distance, long currentTime) {
        // Phase dormante - Se réveille avec les vibrations
        if (angerLevel >= 20 && currentPhase == BossPhase.DORMANT) {
            awaken();
        } else if (distance < 10) {
            // Attaque de réveil
            sonicBoom(target, 0.5); // Boom réduit
        }
    }

    private void handleHuntingPhase(Player target, double distance, long currentTime, List<Player> players) {
        // Phase de chasse - Traque implacable
        if (isBurrowed) {
            burrowAttack(target);
            return;
        }

        if (currentTime - lastBurrow > BURROW_COOLDOWN && distance > 15) {
            burrowAndStalk(target);
        } else if (currentTime - lastSonicBoom > SONIC_BOOM_COOLDOWN && distance < 20 && distance > 5) {
            sonicBoom(target, 1.0);
        } else if (currentTime - lastSculkSpread > SCULK_SPREAD_COOLDOWN) {
            expandSculkDomain(players);
        } else if (distance <= 5) {
            attack(target);
        }
    }

    private void handleEnragedPhase(Player target, double distance, long currentTime, List<Player> players) {
        // Phase de colère - Attaques dévastatrices
        if (currentTime - lastSeismicJump > SEISMIC_JUMP_COOLDOWN && distance > 8) {
            seismicJump(target);
        } else if (currentTime - lastSonicBoom > (SONIC_BOOM_COOLDOWN * 0.6) && distance < 25) {
            sonicBoomMulti(players);
        } else if (currentTime - lastSculkSpread > (SCULK_SPREAD_COOLDOWN * 0.7)) {
            sculkInfestation(players);
        } else if (distance <= 6) {
            attack(target);
        }
    }

    private void handleCataclysmPhase(Player target, double distance, long currentTime, List<Player> players) {
        // Phase cataclysme - Destruction totale
        if (currentTime - lastEarthquake > EARTHQUAKE_COOLDOWN) {
            abyssalCataclysm(players);
        } else if (currentTime - lastSeismicJump > (SEISMIC_JUMP_COOLDOWN * 0.4)) {
            seismicJumpArea(players);
        } else if (currentTime - lastSonicBoom > (SONIC_BOOM_COOLDOWN * 0.3)) {
            sonicBoomWave(target);
        } else {
            attack(target);
        }
    }

    @Override
    public void attack(Player target) {
        // Attaque de mêlée dévastatrice
        double damageMultiplier = 1.0 + (angerLevel / 100.0); // Plus de colère = plus de dégâts

        target.damage(damage * damageMultiplier, entity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0)); // 10s d'obscurité
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
        entity.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation(), 1);

        // Augmente la colère
        increaseAnger(target, 5);
    }

    /**
     * Attaque sonique dévastatrice
     */
    private void sonicBoom(Player target, double powerMultiplier) {
        lastSonicBoom = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 0.8f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || target.isDead()) return;

                Location from = entity.getEyeLocation();
                Location to = target.getEyeLocation();

                // Effets visuels du rayon sonique
                drawSonicBeam(from, to);

                // Dégâts et effets
                double boomDamage = damage * 1.5 * powerMultiplier;
                target.damage(boomDamage, entity);
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));

                // Projette le joueur en arrière
                Vector knockback = target.getLocation().subtract(entity.getLocation()).toVector().normalize().multiply(2.5).setY(0.8);
                target.setVelocity(knockback);

                entity.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);
                target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation(), 5, 1, 1, 1);

                increaseAnger(target, 3);
            }
        }.runTaskLater(plugin, 40L); // 2s de charge
    }

    /**
     * Boom sonique multi-cibles
     */
    private void sonicBoomMulti(List<Player> targets) {
        lastSonicBoom = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3.0f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead()) return;

                for (Player target : targets) {
                    if (target.isDead()) continue;

                    drawSonicBeam(entity.getEyeLocation(), target.getEyeLocation());

                    target.damage(damage * 1.2, entity);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));

                    Vector knockback = target.getLocation().subtract(entity.getLocation()).toVector().normalize().multiply(1.8).setY(0.6);
                    target.setVelocity(knockback);
                }

                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.8f);
            }
        }.runTaskLater(plugin, 30L);
    }

    /**
     * Vague sonique en zone
     */
    private void sonicBoomWave(Player target) {
        lastSonicBoom = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.3f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 5 || entity.isDead()) {
                    cancel();
                    return;
                }

                double radius = 5 + (wave * 4);
                createSonicWave(entity.getLocation(), radius);
                wave++;
            }
        }.runTaskTimer(plugin, 40L, 8L); // Délai initial puis toutes les 0.4s
    }

    /**
     * Saut sismique vers la cible
     */
    private void seismicJump(Player target) {
        lastSeismicJump = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_DIG, 2.0f, 0.8f);

        // Calcule la trajectoire
        Vector jumpVector = target.getLocation().subtract(entity.getLocation()).toVector();
        jumpVector.setY(Math.max(jumpVector.getY(), 0) + 8); // Hauteur minimum
        jumpVector.normalize().multiply(3);

        entity.setVelocity(jumpVector);

        // Effets pendant le saut
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isOnGround() || entity.isDead()) {
                    // Impact au sol
                    seismicImpact(entity.getLocation());
                    cancel();
                    return;
                }

                // Particules pendant le vol
                entity.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, entity.getLocation(), 20, 1, 1, 1, 0.5, Material.DEEPSLATE.createBlockData());
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }

    /**
     * Saut sismique de zone
     */
    private void seismicJumpArea(List<Player> targets) {
        lastSeismicJump = System.currentTimeMillis();

        // Calcule le centre du groupe
        Vector center = new Vector();
        for (Player p : targets) {
            center.add(p.getLocation().toVector());
        }
        Location targetLoc = center.multiply(1.0 / Math.max(targets.size(), 1)).toLocation(entity.getWorld());

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_DIG, 3.0f, 0.5f);

        Vector jumpVector = targetLoc.subtract(entity.getLocation()).toVector();
        jumpVector.setY(Math.max(jumpVector.getY(), 0) + 12);
        jumpVector.normalize().multiply(3.5);

        entity.setVelocity(jumpVector);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isOnGround() || entity.isDead()) {
                    seismicImpactMassive(entity.getLocation());
                    cancel();
                    return;
                }

                entity.getWorld().spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, entity.getLocation(), 15, 1.5, 1.5, 1.5, 0);
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }

    /**
     * S'enterre et attaque par surprise
     */
    private void burrowAndStalk(Player target) {
        isBurrowed = true;
        lastBurrow = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_DIG, 2.0f, 1.0f);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0)); // 10s d'invisibilité
        entity.setInvulnerable(true);

        // Particules de terre qui s'effondre
        Location loc = entity.getLocation();
        for (int i = 0; i < 30; i++) {
            loc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc.clone().add(
                    (random.nextDouble() - 0.5) * 4,
                    random.nextDouble() * 2,
                    (random.nextDouble() - 0.5) * 4
            ), 5, 0.5, 0.5, 0.5, 0.1, Material.DIRT.createBlockData());
        }

        // Traque souterraine
        new BukkitRunnable() {
            int huntTime = 0;
            @Override
            public void run() {
                if (huntTime >= 200 || entity.isDead() || target.isDead()) { // 10s max
                    surfaceAttack(target);
                    cancel();
                    return;
                }

                // Se déplace vers la cible sous terre
                Location targetLoc = target.getLocation();
                Location underground = targetLoc.clone().subtract(0, 3, 0);

                if (huntTime % 20 == 0) { // Toutes les secondes
                    entity.teleport(underground);

                    // Indice de présence souterraine
                    targetLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, targetLoc, 10, 2, 0.1, 2, 0.1, Material.STONE.createBlockData());
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                }

                huntTime++;
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    /**
     * Attaque d'émergence
     */
    private void burrowAttack(Player target) {
        // Cette méthode est appelée depuis la runnable de burrowAndStalk
    }

    private void surfaceAttack(Player target) {
        isBurrowed = false;
        entity.setInvulnerable(false);

        // Émerge violemment près du joueur
        Location emergeLoc = target.getLocation().add(
                (random.nextDouble() - 0.5) * 6,
                0,
                (random.nextDouble() - 0.5) * 6
        );
        emergeLoc = emergeLoc.getWorld().getHighestBlockAt(emergeLoc).getLocation().add(0, 1, 0);

        entity.teleport(emergeLoc);
        entity.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Explosion d'émergence
        emergeLoc.getWorld().playSound(emergeLoc, Sound.ENTITY_WARDEN_EMERGE, 3.0f, 1.0f);
        emergeLoc.getWorld().createExplosion(emergeLoc, 0, false, false);

        for (int i = 0; i < 50; i++) {
            emergeLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, emergeLoc.clone().add(0, 1, 0), 20, 3, 2, 3, 0.5, Material.DEEPSLATE.createBlockData());
        }

        // Attaque surprise sur les joueurs proches
        for (Player p : getNearbyPlayersAt(emergeLoc, 5)) {
            p.damage(damage * 1.8, entity);
            Vector knockback = p.getLocation().subtract(emergeLoc).toVector().normalize().multiply(3).setY(1.2);
            p.setVelocity(knockback);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0));
        }
    }

    /**
     * Étend le domaine de Sculk
     */
    private void expandSculkDomain(List<Player> players) {
        lastSculkSpread = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SCULK_SPREAD, 2.0f, 0.8f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 3 || entity.isDead()) {
                    cancel();
                    return;
                }

                // Propage le sculk depuis le boss
                spreadSculkWave(entity.getLocation(), 8 + wave * 4);

                // Invoque des minions sculk
                if (wave == 1 && sculkMinions.size() < 3) {
                    summonSculkMinions();
                }

                wave++;
            }
        }.runTaskTimer(plugin, 0L, 40L); // Toutes les 2s
    }

    /**
     * Infestation sculk agressive
     */
    private void sculkInfestation(List<Player> players) {
        lastSculkSpread = System.currentTimeMillis();

        Bukkit.broadcastMessage("§8§l[BOSS] §0Le Gardien corrompt l'environnement !");
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SCULK_SPREAD, 3.0f, 0.5f);

        for (Player player : players) {
            if (player.isDead()) continue;

            // Crée des zones de sculk sous chaque joueur
            createSculkTrap(player.getLocation());

            // Invoque des sensors sculk
            summonSculkSensor(player.getLocation());
        }
    }

    /**
     * Cataclysme abyssal final
     */
    private void abyssalCataclysm(List<Player> players) {
        lastEarthquake = System.currentTimeMillis();

        Bukkit.broadcastMessage("§0§l§k██████████████████████████████");
        Bukkit.broadcastMessage("§8§l    CATACLYSME ABYSSAL !");
        Bukkit.broadcastMessage("§0§l§k██████████████████████████████");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.1f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 8 || entity.isDead()) {
                    cancel();
                    return;
                }

                // Séismes multiples
                for (Player p : players) {
                    if (p.isDead()) continue;

                    Location epicenter = p.getLocation().add(
                            (random.nextDouble() - 0.5) * 20,
                            0,
                            (random.nextDouble() - 0.5) * 20
                    );

                    createAbyssalFissure(epicenter);
                }

                // Corruption sculk massive
                if (wave % 2 == 0) {
                    spreadSculkWave(entity.getLocation(), 15 + wave * 2);
                }

                wave++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Toutes les secondes
    }

    // === MÉCANIQUES SPÉCIALES ===

    private void handleVibrationDetection(Player player) {
        if (!isDetectingVibrations || currentPhase == BossPhase.CATACLYSM) return;

        // Détecte les mouvements (marche, course, saut)
        if (player.getVelocity().lengthSquared() > 0.01) {
            increaseAnger(player, 1);
        }

        // Détecte les actions bruyantes
        // Note: Dans un vrai plugin, on écouterait les events spécifiques
        if (random.nextDouble() < 0.1) { // Simule la détection d'actions
            increaseAnger(player, 2);

            // Effet visuel de détection
            Location loc = player.getLocation();
            loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 10, 1, 1, 1, 0.1);
            loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 1.2f);
        }
    }

    private void increaseAnger(Player player, int amount) {
        playerAnger.merge(player, amount, Integer::sum);
        angerLevel = Math.min(angerLevel + amount, 100);

        // Effets visuels de colère
        if (angerLevel % 20 == 0) {
            entity.getWorld().spawnParticle(Particle.SCULK_SOUL, entity.getLocation().add(0, 2, 0), angerLevel / 5, 1, 1, 1, 0.1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 1.0f + (angerLevel / 100.0f), 0.8f);
        }
    }

    private void selectPrimaryTarget(List<Player> players) {
        if (players.isEmpty()) {
            primaryTarget = null;
            return;
        }

        // Cible le joueur avec le plus de "colère"
        primaryTarget = players.stream()
                .max(Comparator.comparingInt(p -> playerAnger.getOrDefault(p, 0)))
                .orElse(players.get(0));
    }

    private void updatePhase() {
        double healthPercent = entity.getHealth() / maxHealth;

        BossPhase newPhase = currentPhase;
        if (currentPhase == BossPhase.DORMANT && angerLevel >= 20) {
            newPhase = BossPhase.HUNTING;
        } else if (healthPercent <= 0.60 && currentPhase != BossPhase.CATACLYSM) {
            newPhase = BossPhase.ENRAGED;
        } else if (healthPercent <= 0.30) {
            newPhase = BossPhase.CATACLYSM;
        }

        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            announcePhaseChange();
        }
    }

    // === MÉTHODES D'EFFETS ===

    private void createSculkArena() {
        Location center = entity.getLocation();

        // Crée un domaine de sculk initial
        new BukkitRunnable() {
            int radius = 0;
            @Override
            public void run() {
                if (radius > 15) {
                    cancel();
                    return;
                }

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x*x + z*z <= radius*radius && random.nextDouble() < 0.3) {
                            Location blockLoc = center.clone().add(x, -1, z);
                            Block block = blockLoc.getBlock();

                            if (block.getType().isSolid() && !block.getType().name().contains("BEDROCK")) {
                                block.setType(Material.SCULK);
                                sculkInfectedBlocks.add(blockLoc);
                            }
                        }
                    }
                }

                radius++;
            }
        }.runTaskTimer(plugin, 20L, 5L); // Expansion progressive
    }

    private void drawSonicBeam(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);

        for (double i = 0; i < distance; i += 0.5) {
            Location particleLoc = from.clone().add(direction.clone().multiply(i));
            from.getWorld().spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0.1, 0.1, 0.1, 0);
            from.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 2, 0.2, 0.2, 0.2, 0);
        }
    }

    private void createSonicWave(Location center, double radius) {
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);

        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            Location particleLoc = center.clone().add(
                    Math.cos(angle) * radius,
                    0.5,
                    Math.sin(angle) * radius
            );

            center.getWorld().spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0, 0, 0, 0);
        }

        // Dégâts aux joueurs dans la vague
        for (Player p : getNearbyPlayersAt(center, radius + 2)) {
            double distance = p.getLocation().distance(center);
            if (distance <= radius + 2 && distance >= radius - 2) {
                p.damage(damage * 0.8, entity);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));

                Vector knockback = p.getLocation().subtract(center).toVector().normalize().multiply(2).setY(0.5);
                p.setVelocity(knockback);
            }
        }
    }

    private void seismicImpact(Location impact) {
        impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_STEP, 3.0f, 0.5f);
        impact.getWorld().createExplosion(impact, 0, false, false);

        // Onde de choc
        for (int radius = 1; radius <= 8; radius++) {
            final int r = radius;
            new BukkitRunnable() {
                @Override
                public void run() {
                    createShockwaveRing(impact, r);
                }
            }.runTaskLater(plugin, radius * 2L);
        }
    }

    private void seismicImpactMassive(Location impact) {
        impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_STEP, 3.0f, 0.3f);
        impact.getWorld().createExplosion(impact, 2.0f, false, false);

        // Crée des fissures permanentes
        for (int i = 0; i < 8; i++) {
            double angle = i * 45;
            createSeismicFissure(impact, angle, 15);
        }

        // Dégâts massifs
        for (Player p : getNearbyPlayersAt(impact, 12)) {
            p.damage(damage * 1.5, entity);
            Vector knockback = p.getLocation().subtract(impact).toVector().normalize().multiply(3).setY(1);
            p.setVelocity(knockback);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0));
        }
    }

    private void createShockwaveRing(Location center, int radius) {
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            Location loc = center.clone().add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            );

            center.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 5, 0.5, 0.5, 0.5, 0.1, Material.DEEPSLATE.createBlockData());
        }

        // Dégâts aux joueurs touchés
        for (Player p : getNearbyPlayersAt(center, radius + 1)) {
            double distance = p.getLocation().distance(center);
            if (distance <= radius + 1 && distance >= radius - 1) {
                p.damage(damage * 0.6, entity);
                Vector knockback = p.getLocation().subtract(center).toVector().normalize().multiply(1.5).setY(0.4);
                p.setVelocity(knockback);
            }
        }
    }

    private void createSeismicFissure(Location start, double angle, int length) {
        Vector direction = new Vector(Math.cos(Math.toRadians(angle)), 0, Math.sin(Math.toRadians(angle)));

        for (int i = 1; i <= length; i++) {
            Location fissureLoc = start.clone().add(direction.clone().multiply(i));
            Block block = fissureLoc.getBlock();

            if (block.getType().isSolid() && block.getType().getHardness() < 50) {
                block.setType(Material.AIR);
                fissureLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, fissureLoc.add(0.5, 0.5, 0.5), 10, 0.5, 0.5, 0.5, 0.1, block.getType().createBlockData());
            }
        }
    }

    private void spreadSculkWave(Location center, int radius) {
        List<Block> sculkBlocks = new ArrayList<>(); // Garde une trace des blocs changés

        new BukkitRunnable() {
            int currentRadius = 0;
            @Override
            public void run() {
                if (currentRadius > radius) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 360; i += 20) {
                    double angle = Math.toRadians(i);
                    Location blockLoc = center.clone().add(
                            Math.cos(angle) * currentRadius,
                            -1,
                            Math.sin(angle) * currentRadius
                    );

                    Block block = blockLoc.getBlock();
                    if (block.getType().isSolid() && !block.getType().name().contains("BEDROCK") && random.nextDouble() < 0.4) {
                        Material originalType = block.getType();
                        block.setType(Material.SCULK);
                        sculkInfectedBlocks.add(blockLoc);
                        sculkBlocks.add(block);

                        // Restaure le bloc après 60 secondes
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (block.getType() == Material.SCULK) {
                                    block.setType(originalType);
                                    sculkInfectedBlocks.remove(blockLoc);
                                }
                            }
                        }.runTaskLater(plugin, 1200L); // 60 secondes

                        // Chance de créer des véines sculk temporaires
                        if (random.nextDouble() < 0.2) {
                            Block above = block.getRelative(0, 1, 0);
                            if (above.getType().isAir()) {
                                above.setType(Material.SCULK_VEIN);

                                // Retire la veine après 45 secondes
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (above.getType() == Material.SCULK_VEIN) {
                                            above.setType(Material.AIR);
                                        }
                                    }
                                }.runTaskLater(plugin, 900L);
                            }
                        }
                    }
                }

                center.getWorld().playSound(center, Sound.BLOCK_SCULK_SPREAD, 1.0f, 0.8f + (currentRadius * 0.1f));
                currentRadius++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void createSculkTrap(Location location) {
        // Crée une zone de piège sculk
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location trapLoc = location.clone().add(x, -1, z);
                Block block = trapLoc.getBlock();

                if (block.getType().isSolid()) {
                    block.setType(Material.SCULK);
                    sculkInfectedBlocks.add(trapLoc);
                }
            }
        }

        // Effet de ralentissement permanent dans la zone
        new BukkitRunnable() {
            int lifetime = 600; // 30s
            @Override
            public void run() {
                if (lifetime <= 0) {
                    cancel();
                    return;
                }

                for (Player p : getNearbyPlayersAt(location, 3)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1));

                    if (lifetime % 20 == 0) { // Toutes les secondes
                        p.damage(2, entity);
                        increaseAnger(p, 1);
                    }
                }

                if (lifetime % 10 == 0) {
                    location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 5, 2, 1, 2, 0.1);
                }

                lifetime--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void summonSculkSensor(Location location) {
        // Simule un capteur sculk avec un ArmorStand invisible
        ArmorStand sensor = location.getWorld().spawn(location, ArmorStand.class);
        sensor.setVisible(false);
        sensor.setGravity(false);
        sensor.setCustomName("§8Capteur Sculk");
        sensor.setCustomNameVisible(false);

        sculkMinions.add(sensor);

        // Place un vrai bloc capteur sculk
        Block block = location.getBlock();
        if (block.getType().isAir() || !block.getType().isSolid()) {
            block.setType(Material.SCULK_SENSOR);
        }

        // Détection active
        new BukkitRunnable() {
            int lifetime = 400; // 20s
            @Override
            public void run() {
                if (lifetime <= 0 || sensor.isDead()) {
                    sculkMinions.remove(sensor);
                    if (!sensor.isDead()) sensor.remove();
                    cancel();
                    return;
                }

                // Détecte les joueurs proches
                for (Player p : getNearbyPlayersAt(location, 8)) {
                    if (p.getVelocity().lengthSquared() > 0.01) { // Si le joueur bouge
                        increaseAnger(p, 2);

                        location.getWorld().playSound(location, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 1.5f);
                        location.getWorld().spawnParticle(Particle.SCULK_CHARGE, location, 10, 4, 1, 4, 0.1);

                        // Lance une attaque du boss si il est proche
                        if (entity.getLocation().distance(location) < 20) {
                            sonicBoom(p, 0.7);
                        }
                        break;
                    }
                }

                lifetime--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void summonSculkMinions() {
        for (int i = 0; i < 2; i++) {
            Location spawnLoc = entity.getLocation().add(
                    (random.nextDouble() - 0.5) * 10,
                    0,
                    (random.nextDouble() - 0.5) * 10
            );
            spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

            // Alterne entre différents types
            String minionType = i == 0 ? "enderman_shadow" : "spider_venomous";
            LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);

            if (minion != null) {
                sculkMinions.add(minion);

                // Applique des effets sculk
                minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                minion.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));

                spawnLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, spawnLoc, 30, 1, 2, 1, 0.1);
                spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 1.2f);
            }
        }
    }

    private void createAbyssalFissure(Location epicenter) {
        epicenter.getWorld().playSound(epicenter, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        epicenter.getWorld().createExplosion(epicenter, 3.0f, false, false);

        List<Block> destroyedBlocks = new ArrayList<>();
        Map<Block, Material> originalTypes = new HashMap<>();

        // Crée une fissure profonde
        for (int y = 0; y >= -8; y--) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(z) <= 2) {
                        Block block = epicenter.clone().add(x, y, z).getBlock();
                        if (!block.getType().name().contains("BEDROCK") && !block.getType().isAir()) {
                            originalTypes.put(block, block.getType());
                            block.setType(Material.AIR);
                            destroyedBlocks.add(block);
                        }
                    }
                }
            }
        }

        // Remplit temporairement le fond de sculk
        List<Block> sculkBlocks = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = epicenter.clone().add(x, -8, z).getBlock();
                if (block.getType().isAir()) {
                    block.setType(Material.SCULK);
                    sculkBlocks.add(block);
                }
            }
        }

        // Restaure tout après 90 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                // Restaure les blocs détruits
                for (Block block : destroyedBlocks) {
                    Material originalType = originalTypes.get(block);
                    if (originalType != null && block.getType().isAir()) {
                        block.setType(originalType);
                    }
                }

                // Retire le sculk
                for (Block block : sculkBlocks) {
                    if (block.getType() == Material.SCULK) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }.runTaskLater(plugin, 1800L); // 90 secondes

        // Dégâts aux joueurs proches
        for (Player p : getNearbyPlayersAt(epicenter, 6)) {
            p.damage(damage * 1.3, entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));
        }
    }

    // === MÉTHODES DE PHASE ===

    private void startDormantPhase() {
        currentPhase = BossPhase.DORMANT;
        isDetectingVibrations = true;

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§0§l§k===========================================");
        Bukkit.broadcastMessage("§8§l        ⚫ ENTITÉ ANCIENNE DÉTECTÉE ⚫");
        Bukkit.broadcastMessage("§0§l         GARDIEN DES ABYSSES");
        Bukkit.broadcastMessage("§8§l      Le gardien dort... pour l'instant.");
        Bukkit.broadcastMessage("§7§l    §oBougez silencieusement ou réveillez-le...");
        Bukkit.broadcastMessage("§0§l§k===========================================");
        Bukkit.broadcastMessage("");

        // Effets d'ambiance
        Location loc = entity.getLocation();
        for (int i = 0; i < 100; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (currentPhase != BossPhase.DORMANT || entity.isDead()) {
                        cancel();
                        return;
                    }

                    loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 2, 2, 2, 2, 0.01);

                    if (random.nextDouble() < 0.1) {
                        loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.8f);
                    }
                }
            }.runTaskLater(plugin, i * 20L);
        }
    }

    private void awaken() {
        currentPhase = BossPhase.HUNTING;

        Bukkit.broadcastMessage("§4§l⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
        Bukkit.broadcastMessage("§0§l          LE GARDIEN S'ÉVEILLE !");
        Bukkit.broadcastMessage("§4§l⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 3.0f, 0.5f);

        // Effets dramatiques d'éveil
        Location loc = entity.getLocation();
        for (int i = 0; i < 20; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 2, 0), 20, 3, 3, 3, 0.5);
                    loc.getWorld().createExplosion(loc, 0, false, false);
                }
            }.runTaskLater(plugin, i * 5L);
        }

        // Boost de réveil
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));
    }

    private void announcePhaseChange() {
        String message = switch (currentPhase) {
            case HUNTING -> "§8Le Gardien commence sa traque...";
            case ENRAGED -> "§4§lLE GARDIEN ENTRE EN COLÈRE !";
            case CATACLYSM -> "§0§l💀 LE GARDIEN DÉCHAÎNE SA FUREUR FINALE ! 💀";
            default -> "";
        };

        if (!message.isEmpty()) {
            Bukkit.broadcastMessage("§0§l[BOSS] §r" + message);
        }
    }

    private void cleanupMinions() {
        sculkMinions.removeIf(LivingEntity::isDead);
    }

    protected List<Player> getNearbyPlayers(double radius) {
        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && (((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE))
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    protected List<Player> getNearbyPlayersAt(Location location, double radius) {
        return location.getWorld().getNearbyEntities(location, radius, radius, radius).stream()
                .filter(e -> e instanceof Player && (((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE))
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    @Override
    public void specialAbility(Player target) {
        // Géré par l'IA de phases complexe
    }
}