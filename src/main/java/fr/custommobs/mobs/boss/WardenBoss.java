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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WardenBoss extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> sculkMinions = new ArrayList<>();
    private final Map<Player, Integer> playerAnger = new HashMap<>();
    private final Map<Player, Long> lastPlayerAction = new HashMap<>();
    private final Map<Player, Double> playerThreat = new HashMap<>();

    // === GESTION DES BLOCS SCULK TEMPORAIRES ===
    private final Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    private final Set<Location> sculkInfectedBlocks = ConcurrentHashMap.newKeySet();

    // --- ÉTAT DU BOSS MULTIJOUEUR ---
    private BossPhase currentPhase = BossPhase.DORMANT;
    private int angerLevel = 0; // 0-100
    private boolean isBurrowed = false;
    private boolean isDetectingVibrations = true;
    private Location burrowLocation;
    private Player primaryTarget = null;
    private int activePlayerCount = 0;
    private long lastTargetSwitch = 0;
    private boolean isPerformingGroupAttack = false;

    // --- COOLDOWNS ADAPTATIFS ---
    private long lastSonicBoom = 0;
    private long lastSeismicJump = 0;
    private long lastSculkSpread = 0;
    private long lastBurrow = 0;
    private long lastEarthquake = 0;
    private long lastSwarmCall = 0;
    private final long SWARM_CALL_COOLDOWN = 22000; // 22s

    private enum BossPhase {
        DORMANT,    // Initial - Réveillé par les vibrations
        HUNTING,    // 100-60% HP - Traque coordonnée
        ENRAGED,    // 60-30% HP - Colère maximale, attaques de groupe
        CATACLYSM   // 30-0% HP - Destruction totale multijoueur
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

        warden.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        warden.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 2, false, false));

        this.burrowLocation = location.clone();

        setupEntity(warden);
        startDormantPhase();
        createSculkArena();
        startSculkCleanupTask();

        return warden;
    }

    @Override
    protected void onPlayerNear(Player target) {
        if (entity.isDead()) return;

        List<Player> nearbyPlayers = getNearbyPlayers(30);
        activePlayerCount = nearbyPlayers.size();

        // Système de détection par vibrations multijoueur
        handleMultiPlayerVibrationDetection(nearbyPlayers);
        updateThreatLevels(nearbyPlayers);
        updatePhase();
        cleanupMinions();

        long currentTime = System.currentTimeMillis();

        // === SYSTÈME DE CIBLAGE MULTIJOUEUR INTELLIGENT ===
        Player optimalTarget = selectOptimalTarget(nearbyPlayers, currentTime);
        if (optimalTarget == null) optimalTarget = target;
        primaryTarget = optimalTarget;

        double distance = entity.getLocation().distance(primaryTarget.getLocation());

        // === IA MULTIJOUEUR ADAPTATIVE ===
        switch (currentPhase) {
            case DORMANT:
                handleDormantPhaseMultiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case HUNTING:
                handleHuntingPhaseMultiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case ENRAGED:
                handleEnragedPhaseMultiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case CATACLYSM:
                handleCataclysmPhaseMultiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
        }

        // Attaques secondaires sur d'autres joueurs (30% de chance)
        if (nearbyPlayers.size() > 1 && Math.random() < 0.3 && !isPerformingGroupAttack) {
            executeSecondaryAbyssalAttacks(nearbyPlayers, primaryTarget);
        }
    }

    private void handleDormantPhaseMultiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Réveil adaptatif selon le nombre de joueurs
        int awakeningThreshold = Math.max(15, 25 - (players.size() * 3));

        if (angerLevel >= awakeningThreshold && currentPhase == BossPhase.DORMANT) {
            awakenMultiplayer(players);
        } else if (distance < 12) {
            // Avertissement sonique adaptatif
            double powerMultiplier = 0.3 + (players.size() * 0.1);
            sonicBoom(target, powerMultiplier);
        }
    }

    private void handleHuntingPhaseMultiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Cooldowns adaptatifs
        long burrowCD = Math.max(25000, 35000 - (players.size() * 2000));
        long sonicCD = Math.max(6000, 10000 - (players.size() * 800));
        long sculkCD = Math.max(15000, 25000 - (players.size() * 1500));

        if (isBurrowed) {
            burrowAttackMultiplayer(players);
            return;
        }

        if (players.size() >= 3 && currentTime - lastSwarmCall > SWARM_CALL_COOLDOWN) {
            sculkSwarmCall(players);
        } else if (currentTime - lastBurrow > burrowCD && distance > 18) {
            burrowAndStalkMultiplayer(players);
        } else if (currentTime - lastSonicBoom > sonicCD && distance < 25) {
            if (players.size() > 2) {
                sonicBoomOmnidirectional(players);
            } else {
                sonicBoom(target, 1.0);
            }
        } else if (currentTime - lastSculkSpread > sculkCD) {
            expandSculkDomainMultiplayer(players);
        } else if (distance <= 8) {
            attack(target);
        }
    }

    private void handleEnragedPhaseMultiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Phase enragée - attaques de groupe prioritaires
        long jumpCD = Math.max(10000, 18000 - (players.size() * 1200));
        long sonicCD = Math.max(5000, 8000 - (players.size() * 600));

        if (players.size() >= 2 && currentTime - lastSeismicJump > jumpCD) {
            seismicJumpMultiTarget(players);
        } else if (currentTime - lastSonicBoom > sonicCD && distance < 30) {
            sonicBoomChain(players);
        } else if (currentTime - lastSculkSpread > 12000) {
            sculkInfestationAggressive(players);
        } else if (distance <= 10) {
            attack(target);
        }
    }

    private void handleCataclysmPhaseMultiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Phase finale - chaos total
        long earthquakeCD = Math.max(20000, 30000 - (players.size() * 3000));

        if (currentTime - lastEarthquake > earthquakeCD) {
            abyssalCataclysmMultiplayer(players);
        } else if (currentTime - lastSeismicJump > 8000) {
            seismicJumpDevastating(players);
        } else if (currentTime - lastSonicBoom > 6000) {
            sonicBoomApocalyptic(players);
        } else {
            attack(target);
        }
    }

    // === SYSTÈME D'AGGRO ET CIBLAGE ===

    private void updateThreatLevels(List<Player> players) {
        for (Player player : players) {
            // Diminue l'aggro avec le temps
            double currentThreat = playerThreat.getOrDefault(player, 0.0);
            playerThreat.put(player, Math.max(0, currentThreat * 0.97));

            // Diminue la colère avec le temps
            int currentAnger = playerAnger.getOrDefault(player, 0);
            playerAnger.put(player, Math.max(0, currentAnger - 1));
        }
    }

    private Player selectOptimalTarget(List<Player> players, long currentTime) {
        if (players.isEmpty()) return null;

        // Change de cible selon la stratégie multijoueur
        boolean shouldSwitch = currentTime - lastTargetSwitch > (players.size() > 3 ? 5000 : 8000) ||
                Math.random() < (players.size() * 0.04);

        if (!shouldSwitch && primaryTarget != null && !primaryTarget.isDead() && players.contains(primaryTarget)) {
            return primaryTarget;
        }

        lastTargetSwitch = currentTime;

        return players.stream()
                .max((p1, p2) -> {
                    double priority1 = calculateTargetPriority(p1);
                    double priority2 = calculateTargetPriority(p2);
                    return Double.compare(priority1, priority2);
                })
                .orElse(players.get(random.nextInt(players.size())));
    }

    private double calculateTargetPriority(Player player) {
        double priority = 0;

        // Distance (plus proche = plus de priorité)
        double distance = entity.getLocation().distance(player.getLocation());
        priority += Math.max(0, 35 - distance);

        // Niveau de colère individuel
        priority += playerAnger.getOrDefault(player, 0) * 2;

        // Niveau d'aggro
        priority += playerThreat.getOrDefault(player, 0.0) * 3;

        // Priorité aux joueurs actifs (bougeant récemment)
        Long lastAction = lastPlayerAction.get(player);
        if (lastAction != null && System.currentTimeMillis() - lastAction < 5000) {
            priority += 20;
        }

        // Priorité aux joueurs avec moins de vie
        priority += (1.0 - (player.getHealth() / player.getMaxHealth())) * 25;

        // Facteur aléatoire pour imprévisibilité
        priority += random.nextDouble() * 12;

        return priority;
    }

    private void increaseAnger(Player player, int amount) {
        int currentAnger = playerAnger.getOrDefault(player, 0);
        playerAnger.put(player, Math.min(currentAnger + amount, 100));

        double currentThreat = playerThreat.getOrDefault(player, 0.0);
        playerThreat.put(player, currentThreat + amount * 1.5);

        angerLevel = Math.min(angerLevel + amount, 100);
        lastPlayerAction.put(player, System.currentTimeMillis());

        if (angerLevel % 15 == 0) {
            entity.getWorld().spawnParticle(Particle.SCULK_SOUL, entity.getLocation().add(0, 2, 0), angerLevel / 8, 1, 1, 1, 0.1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 1.0f + (angerLevel / 100.0f), 0.8f);
        }
    }

    @Override
    public void attack(Player target) {
        // Attaque de base adaptée au multijoueur
        double damageMultiplier = 1.0 + (activePlayerCount * 0.08) + (angerLevel / 100.0);

        target.damage(damage * damageMultiplier, entity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 250, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2));

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
        entity.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation(), 1);

        increaseAnger(target, 8);

        // Chance d'effet de zone en multijoueur
        if (activePlayerCount > 2 && Math.random() < 0.3) {
            createAbyssalShockwave(target.getLocation(), 4);
        }
    }

    private void executeSecondaryAbyssalAttacks(List<Player> players, Player primaryTarget) {
        List<Player> secondaryTargets = players.stream()
                .filter(p -> !p.equals(primaryTarget))
                .collect(Collectors.toList());

        if (!secondaryTargets.isEmpty()) {
            Player secondary = secondaryTargets.get(random.nextInt(secondaryTargets.size()));

            if (Math.random() < 0.5) {
                // Tentacule sculk
                createSculkTentacle(secondary.getLocation());
            } else {
                // Piège de ténèbres
                createDarknessTrap(secondary.getLocation());
            }
        }
    }

    // === NOUVELLES ATTAQUES MULTIJOUEUR ===

    private void sculkSwarmCall(List<Player> players) {
        isPerformingGroupAttack = true;
        lastSwarmCall = System.currentTimeMillis();

        Bukkit.broadcastMessage("§0§l[BOSS] §8Le Gardien appelle l'essaim des abysses !");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.4f);

        // Nombre de créatures adaptatif
        int swarmSize = Math.min(players.size() + 2, 8);

        new BukkitRunnable() {
            int spawned = 0;
            @Override
            public void run() {
                if (spawned >= swarmSize || entity.isDead()) {
                    isPerformingGroupAttack = false;
                    cancel();
                    return;
                }

                // Spawn near each player
                for (Player player : players) {
                    if (spawned >= swarmSize) break;

                    Location spawnLoc = player.getLocation().add(
                            (random.nextDouble() - 0.5) * 8,
                            0,
                            (random.nextDouble() - 0.5) * 8
                    );
                    spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

                    // Types de minions adaptatifs
                    String minionType = switch (spawned % 3) {
                        case 0 -> "enderman_shadow";
                        case 1 -> "spider_venomous";
                        default -> "witch_cursed";
                    };

                    LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);
                    if (minion != null) {
                        sculkMinions.add(minion);

                        // Bonus multijoueur
                        minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                        if (players.size() >= 4) {
                            minion.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
                        }

                        spawnLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, spawnLoc, 25, 1, 2, 1, 0.1);
                        spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 1.4f);
                    }
                    spawned++;
                }
            }
        }.runTaskTimer(plugin, 0L, 15L); // Spawn rapide
    }

    private void sonicBoomOmnidirectional(List<Player> players) {
        lastSonicBoom = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3.0f, 0.6f);

        Bukkit.broadcastMessage("§8§l[BOSS] §0Boom sonique omnidirectionnel !");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead()) return;

                Location center = entity.getLocation();

                // Boom central
                for (int angle = 0; angle < 360; angle += 30) {
                    Vector direction = new Vector(
                            Math.cos(Math.toRadians(angle)),
                            0,
                            Math.sin(Math.toRadians(angle))
                    ).normalize();

                    createSonicBeamLine(center, direction, 20);
                }

                // Boom ciblé sur chaque joueur
                for (Player player : players) {
                    if (!player.isDead()) {
                        drawSonicBeam(entity.getEyeLocation(), player.getEyeLocation());
                        player.damage(damage * 1.3, entity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));

                        Vector knockback = player.getLocation().subtract(center).toVector().normalize().multiply(2.2).setY(0.7);
                        player.setVelocity(knockback);

                        increaseAnger(player, 6);
                    }
                }

                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.8f);
            }
        }.runTaskLater(plugin, 50L); // Délai de charge
    }

    private void sonicBoomChain(List<Player> players) {
        lastSonicBoom = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.5f, 0.8f);

        new BukkitRunnable() {
            int playerIndex = 0;
            @Override
            public void run() {
                if (playerIndex >= players.size() || entity.isDead()) {
                    cancel();
                    return;
                }

                Player target = players.get(playerIndex);
                if (!target.isDead()) {
                    // Boom avec effet de chaîne
                    drawSonicBeam(entity.getEyeLocation(), target.getEyeLocation());
                    target.damage(damage * 1.1, entity);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));

                    // Effet de chaîne vers les joueurs proches
                    for (Player nearby : getNearbyPlayersAt(target.getLocation(), 6)) {
                        if (!nearby.equals(target)) {
                            drawSonicBeam(target.getEyeLocation(), nearby.getEyeLocation());
                            nearby.damage(damage * 0.6, entity);
                            nearby.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 150, 0));
                            increaseAnger(nearby, 4);
                        }
                    }

                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.2f);
                    increaseAnger(target, 8);
                }

                playerIndex++;
            }
        }.runTaskTimer(plugin, 40L, 10L); // Délai initial puis chaîne rapide
    }

    private void seismicJumpMultiTarget(List<Player> players) {
        lastSeismicJump = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_DIG, 2.5f, 0.6f);

        // Calcule le centre du groupe
        Vector centerPoint = calculatePlayerGroupCenter(players);
        Location targetLoc = centerPoint.toLocation(entity.getWorld());

        Bukkit.broadcastMessage("§8§l[BOSS] §0Saut sismique multi-cible !");

        Vector jumpVector = targetLoc.subtract(entity.getLocation()).toVector();
        jumpVector.setY(Math.max(jumpVector.getY(), 0) + 10 + players.size());
        jumpVector.normalize().multiply(3.5);

        entity.setVelocity(jumpVector);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isOnGround() || entity.isDead()) {
                    seismicImpactMultiplayer(entity.getLocation(), players);
                    cancel();
                    return;
                }
                entity.getWorld().spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, entity.getLocation(), 20, 2, 2, 2, 0);
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }

    private void seismicJumpDevastating(List<Player> players) {
        lastSeismicJump = System.currentTimeMillis();

        Bukkit.broadcastMessage("§0§l⚠ SAUT SISMIQUE DÉVASTATEUR ! ⚠");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_DIG, 3.0f, 0.3f);

        // Saute vers chaque joueur en séquence
        new BukkitRunnable() {
            int jumpCount = 0;
            @Override
            public void run() {
                if (jumpCount >= Math.min(players.size(), 4) || entity.isDead()) {
                    cancel();
                    return;
                }

                Player target = players.get(jumpCount);
                if (!target.isDead()) {
                    Vector jumpVector = target.getLocation().subtract(entity.getLocation()).toVector();
                    jumpVector.setY(Math.max(jumpVector.getY(), 0) + 8);
                    jumpVector.normalize().multiply(4.0);

                    entity.setVelocity(jumpVector);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (entity.isOnGround() || entity.isDead()) {
                                seismicImpactMassive(entity.getLocation());
                                cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 5L, 2L);
                }

                jumpCount++;
            }
        }.runTaskTimer(plugin, 0L, 40L); // Un saut toutes les 2 secondes
    }

    private void burrowAndStalkMultiplayer(List<Player> players) {
        isBurrowed = true;
        lastBurrow = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_DIG, 2.5f, 0.8f);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 0));
        entity.setInvulnerable(true);

        Bukkit.broadcastMessage("§8§l[BOSS] §0Le Gardien disparaît dans les abysses...");

        Location loc = entity.getLocation();
        for (int i = 0; i < 40; i++) {
            loc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc.clone().add(
                    (random.nextDouble() - 0.5) * 6,
                    random.nextDouble() * 3,
                    (random.nextDouble() - 0.5) * 6
            ), 8, 0.5, 0.5, 0.5, 0.1, Material.DEEPSLATE.createBlockData());
        }

        new BukkitRunnable() {
            int huntTime = 0;
            int targetIndex = 0;
            @Override
            public void run() {
                if (huntTime >= 250 || entity.isDead()) {
                    emergenceAttackMultiplayer(players);
                    cancel();
                    return;
                }

                // Cible les joueurs en rotation
                if (huntTime % 30 == 0 && !players.isEmpty()) {
                    Player currentTarget = players.get(targetIndex % players.size());
                    if (!currentTarget.isDead()) {
                        Location underground = currentTarget.getLocation().subtract(0, 4, 0);
                        entity.teleport(underground);

                        // Effets de traque
                        currentTarget.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, currentTarget.getLocation(), 15, 3, 0.1, 3, 0.1, Material.SCULK.createBlockData());
                        currentTarget.getWorld().playSound(currentTarget.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.6f);

                        // Crée des capteurs sculk temporaires
                        createTrackingSculkSensors(currentTarget.getLocation());
                    }
                    targetIndex++;
                }

                huntTime++;
            }
        }.runTaskTimer(plugin, 30L, 1L);
    }

    private void burrowAttackMultiplayer(List<Player> players) {
        // Méthode appelée depuis burrowAndStalkMultiplayer
    }

    private void emergenceAttackMultiplayer(List<Player> players) {
        isBurrowed = false;
        entity.setInvulnerable(false);

        // Émerge au centre du groupe avec explosion massive
        Vector centerPoint = calculatePlayerGroupCenter(players);
        Location emergeLoc = centerPoint.toLocation(entity.getWorld());
        emergeLoc = emergeLoc.getWorld().getHighestBlockAt(emergeLoc).getLocation().add(0, 1, 0);

        entity.teleport(emergeLoc);
        entity.removePotionEffect(PotionEffectType.INVISIBILITY);

        Bukkit.broadcastMessage("§0§l[BOSS] §8LE GARDIEN ÉMERGE DES PROFONDEURS !");
        emergeLoc.getWorld().playSound(emergeLoc, Sound.ENTITY_WARDEN_EMERGE, 3.0f, 0.8f);
        emergeLoc.getWorld().createExplosion(emergeLoc, 2.0f, false, false);

        // Effets d'émergence massifs
        for (int i = 0; i < 80; i++) {
            emergeLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, emergeLoc.clone().add(0, 2, 0), 30, 4, 3, 4, 0.6, Material.DEEPSLATE.createBlockData());
        }

        // Dégâts de zone adaptatifs
        for (Player p : getNearbyPlayersAt(emergeLoc, 8)) {
            double distance = p.getLocation().distance(emergeLoc);
            double damageMultiplier = Math.max(0.5, 1.0 - (distance / 8.0));

            p.damage(damage * 2.0 * damageMultiplier, entity);
            Vector knockback = p.getLocation().subtract(emergeLoc).toVector().normalize().multiply(3.5).setY(1.4);
            p.setVelocity(knockback);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0));

            increaseAnger(p, 15);
        }

        // Crée des fissures radiantes
        for (int angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(angle);
            createSeismicFissure(emergeLoc, radians, 12);
        }
    }

    private void abyssalCataclysmMultiplayer(List<Player> players) {
        lastEarthquake = System.currentTimeMillis();

        Bukkit.broadcastMessage("§0§l§k██████████████████████████████");
        Bukkit.broadcastMessage("§8§l    CATACLYSME ABYSSAL MULTIDIMENSIONNEL !");
        Bukkit.broadcastMessage("§0§l§k██████████████████████████████");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.1f);

        new BukkitRunnable() {
            int cataclysmWave = 0;
            @Override
            public void run() {
                if (cataclysmWave >= 12 || entity.isDead()) {
                    cancel();
                    return;
                }

                // Phase 1-4 : Attaques ciblées individuelles
                if (cataclysmWave < 4) {
                    for (Player p : players) {
                        if (p.isDead()) continue;

                        for (int i = 0; i < 2; i++) {
                            Location epicenter = p.getLocation().add(
                                    (random.nextDouble() - 0.5) * 12,
                                    0,
                                    (random.nextDouble() - 0.5) * 12
                            );
                            createAbyssalFissureMultiplayer(epicenter, 3 + cataclysmWave);
                        }
                    }
                }
                // Phase 5-8 : Attaques de zone
                else if (cataclysmWave < 8) {
                    Vector groupCenter = calculatePlayerGroupCenter(players);
                    Location centerLoc = groupCenter.toLocation(entity.getWorld());

                    for (int ring = 1; ring <= 4; ring++) {
                        createAbyssalRing(centerLoc, ring * 5, cataclysmWave - 4);
                    }
                }
                // Phase 9-12 : Chaos total
                else {
                    for (int i = 0; i < 8; i++) {
                        Location randomLoc = entity.getLocation().add(
                                (random.nextDouble() - 0.5) * 40,
                                0,
                                (random.nextDouble() - 0.5) * 40
                        );
                        createAbyssalFissureMultiplayer(randomLoc, 8);
                    }

                    // Boom sonique global
                    sonicBoomApocalyptic(players);
                }

                cataclysmWave++;
            }
        }.runTaskTimer(plugin, 0L, 25L); // Toutes les 1.25s
    }

    private void sonicBoomApocalyptic(List<Player> players) {
        lastSonicBoom = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3.0f, 0.2f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead()) return;

                Location center = entity.getLocation();

                // Onde de choc globale
                for (int radius = 5; radius <= 35; radius += 5) {
                    final int r = radius;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            createApocalypticSonicWave(center, r);
                        }
                    }.runTaskLater(plugin, radius / 5 * 3L);
                }

                // Attaque ciblée sur chaque joueur
                for (Player player : players) {
                    if (!player.isDead()) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                drawSonicBeam(entity.getEyeLocation(), player.getEyeLocation());
                                player.damage(damage * 1.8, entity);
                                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 500, 0));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 4));

                                Vector massiveKnockback = player.getLocation().subtract(center).toVector().normalize().multiply(4.0).setY(1.5);
                                player.setVelocity(massiveKnockback);

                                increaseAnger(player, 20);
                            }
                        }.runTaskLater(plugin, 20L);
                    }
                }
            }
        }.runTaskLater(plugin, 60L); // Délai de charge long
    }

    // === MÉTHODES UTILITAIRES MULTIJOUEUR ===

    private Vector calculatePlayerGroupCenter(List<Player> players) {
        if (players.isEmpty()) return entity.getLocation().toVector();

        Vector center = new Vector();
        double totalWeight = 0;

        for (Player player : players) {
            double weight = 1.0 + playerThreat.getOrDefault(player, 0.0) * 0.05;
            center.add(player.getLocation().toVector().multiply(weight));
            totalWeight += weight;
        }

        return center.multiply(1.0 / totalWeight);
    }

    private void createSculkTentacle(Location location) {
        // Tentacule sculk qui émerge du sol
        new BukkitRunnable() {
            int height = 0;
            @Override
            public void run() {
                if (height >= 5) {
                    // Tentacule retombe après 3 secondes
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (int y = 0; y < 5; y++) {
                                Block block = location.clone().add(0, y, 0).getBlock();
                                if (block.getType() == Material.SCULK_VEIN) {
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }.runTaskLater(plugin, 60L);
                    cancel();
                    return;
                }

                Block block = location.clone().add(0, height, 0).getBlock();
                if (block.getType().isAir()) {
                    Material originalType = block.getType();
                    block.setType(Material.SCULK_VEIN);
                    registerSculkBlock(block.getLocation(), originalType, 100L);
                }

                // Dégâts aux joueurs touchés
                for (Player p : getNearbyPlayersAt(location.clone().add(0, height, 0), 2)) {
                    p.damage(damage * 0.4, entity);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    increaseAnger(p, 3);
                }

                location.getWorld().spawnParticle(Particle.SCULK_CHARGE, location.clone().add(0, height, 0), 5, 0.5, 0.5, 0.5, 0.1);
                height++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void createDarknessTrap(Location location) {
        // Piège de ténèbres persistant
        location.getWorld().playSound(location, Sound.ENTITY_WARDEN_LISTENING, 1.0f, 0.6f);

        new BukkitRunnable() {
            int duration = 100;
            @Override
            public void run() {
                if (duration <= 0) {
                    cancel();
                    return;
                }

                // Effets visuels du piège
                for (int i = 0; i < 8; i++) {
                    double angle = (duration * 10 + i * 45) % 360;
                    double radius = 3 + Math.sin(duration * 0.1) * 0.5;

                    Location particleLoc = location.clone().add(
                            Math.cos(Math.toRadians(angle)) * radius,
                            0.5,
                            Math.sin(Math.toRadians(angle)) * radius
                    );

                    location.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 1, 0, 0, 0, 0);
                }

                // Effets sur les joueurs dans le piège
                for (Player p : getNearbyPlayersAt(location, 4)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));

                    if (duration % 20 == 0) {
                        p.damage(1.5, entity);
                        increaseAnger(p, 2);
                    }
                }

                duration--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createTrackingSculkSensors(Location center) {
        for (int i = 0; i < 4; i++) {
            double angle = i * 90;
            Location sensorLoc = center.clone().add(
                    Math.cos(Math.toRadians(angle)) * 5,
                    0,
                    Math.sin(Math.toRadians(angle)) * 5
            );

            summonSculkSensor(sensorLoc);
        }
    }

    private void createAbyssalShockwave(Location center, double radius) {
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_STEP, 1.5f, 0.7f);

        for (int angle = 0; angle < 360; angle += 20) {
            double radians = Math.toRadians(angle);
            Location waveLoc = center.clone().add(
                    Math.cos(radians) * radius,
                    0.2,
                    Math.sin(radians) * radius
            );

            center.getWorld().spawnParticle(Particle.SCULK_SOUL, waveLoc, 3, 0.3, 0.3, 0.3, 0.1);
        }

        for (Player p : getNearbyPlayersAt(center, radius + 1)) {
            double distance = p.getLocation().distance(center);
            if (distance <= radius + 1 && distance >= radius - 1) {
                p.damage(damage * 0.5, entity);
                Vector knockback = p.getLocation().subtract(center).toVector().normalize().multiply(1.2).setY(0.3);
                p.setVelocity(knockback);
                increaseAnger(p, 3);
            }
        }
    }

    private void createSonicBeamLine(Location start, Vector direction, double length) {
        new BukkitRunnable() {
            double distance = 0;
            @Override
            public void run() {
                if (distance >= length) {
                    cancel();
                    return;
                }

                Location beamLoc = start.clone().add(direction.clone().multiply(distance));
                start.getWorld().spawnParticle(Particle.SONIC_BOOM, beamLoc, 1, 0, 0, 0, 0);

                // Dégâts aux joueurs touchés
                for (Player p : getNearbyPlayersAt(beamLoc, 2)) {
                    p.damage(damage * 0.7, entity);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
                    increaseAnger(p, 4);
                }

                distance += 2;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void seismicImpactMultiplayer(Location impact, List<Player> players) {
        impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_STEP, 3.0f, 0.4f);
        impact.getWorld().createExplosion(impact, 1.5f, false, false);

        // Ondes de choc adaptatives
        int maxRings = 6 + players.size();
        for (int radius = 1; radius <= maxRings; radius++) {
            final int r = radius;
            new BukkitRunnable() {
                @Override
                public void run() {
                    createAbyssalShockwave(impact, r * 2);
                }
            }.runTaskLater(plugin, radius * 3L);
        }

        // Fissures directionnelles vers chaque joueur
        for (Player player : players) {
            if (!player.isDead()) {
                Vector directionToPlayer = player.getLocation().subtract(impact).toVector().normalize();
                createSeismicFissure(impact, Math.atan2(directionToPlayer.getZ(), directionToPlayer.getX()), 10);
            }
        }
    }

    private void createAbyssalFissureMultiplayer(Location epicenter, int intensity) {
        epicenter.getWorld().playSound(epicenter, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.4f);
        epicenter.getWorld().createExplosion(epicenter, intensity * 0.5f, false, false);

        List<Block> destroyedBlocks = new ArrayList<>();
        Map<Block, Material> originalTypes = new HashMap<>();

        // Fissure plus large et profonde selon l'intensité
        int width = Math.min(intensity, 4);
        int depth = Math.min(intensity * 2, 12);

        for (int y = 0; y >= -depth; y--) {
            for (int x = -width; x <= width; x++) {
                for (int z = -width; z <= width; z++) {
                    if (Math.abs(x) + Math.abs(z) <= width) {
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

        // Sculk au fond de la fissure
        for (int x = -width; x <= width; x++) {
            for (int z = -width; z <= width; z++) {
                Block block = epicenter.clone().add(x, -depth, z).getBlock();
                if (block.getType().isAir()) {
                    block.setType(Material.SCULK);
                    registerSculkBlock(block.getLocation(), Material.AIR, 2400L);
                }
            }
        }

        // Dégâts adaptatifs selon l'intensité
        for (Player p : getNearbyPlayersAt(epicenter, intensity + 3)) {
            double distance = p.getLocation().distance(epicenter);
            double damageMultiplier = Math.max(0.3, 1.0 - (distance / (intensity + 3)));

            p.damage(damage * 1.5 * damageMultiplier, entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));

            increaseAnger(p, intensity * 2);
        }

        // Restauration retardée
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block block : destroyedBlocks) {
                    Material originalType = originalTypes.get(block);
                    if (originalType != null && block.getType().isAir()) {
                        block.setType(originalType);
                    }
                }
            }
        }.runTaskLater(plugin, 2400L); // 2 minutes
    }

    private void createAbyssalRing(Location center, double radius, int intensity) {
        center.getWorld().playSound(center, Sound.BLOCK_STONE_BREAK, 2.0f, 0.6f);

        for (int angle = 0; angle < 360; angle += 15) {
            double radians = Math.toRadians(angle);
            Location ringLoc = center.clone().add(
                    Math.cos(radians) * radius,
                    0,
                    Math.sin(radians) * radius
            );

            // Explosion mineure à chaque point de l'anneau
            ringLoc.getWorld().createExplosion(ringLoc, 1.0f, false, false);

            for (int i = 0; i < intensity * 5; i++) {
                ringLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, ringLoc, 8, 1, 0.5, 1, 0.1, Material.DEEPSLATE.createBlockData());
            }
        }

        // Dégâts aux joueurs dans l'anneau
        for (Player p : getNearbyPlayersAt(center, radius + 3)) {
            double distance = p.getLocation().distance(center);
            if (distance >= radius - 3 && distance <= radius + 3) {
                p.damage(damage * (0.8 + intensity * 0.2), entity);
                Vector knockback = p.getLocation().subtract(center).toVector().normalize().multiply(2.0).setY(0.6);
                p.setVelocity(knockback);
                increaseAnger(p, intensity * 3);
            }
        }
    }

    private void createApocalypticSonicWave(Location center, double radius) {
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.5f);

        for (int i = 0; i < 360; i += 8) {
            double angle = Math.toRadians(i);
            Location particleLoc = center.clone().add(
                    Math.cos(angle) * radius,
                    1.0,
                    Math.sin(angle) * radius
            );

            center.getWorld().spawnParticle(Particle.SONIC_BOOM, particleLoc, 2, 0.2, 0.2, 0.2, 0);
            center.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 5, 0.5, 0.5, 0.5, 0.1);
        }

        for (Player p : getNearbyPlayersAt(center, radius + 3)) {
            double distance = p.getLocation().distance(center);
            if (distance <= radius + 3 && distance >= radius - 3) {
                p.damage(damage * 1.2, entity);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 150, 2));

                Vector massiveKnockback = p.getLocation().subtract(center).toVector().normalize().multiply(3.0).setY(0.8);
                p.setVelocity(massiveKnockback);

                increaseAnger(p, 15);
            }
        }
    }

    // === MÉTHODES DE GESTION SCULK (inchangées du code original) ===

    private void startSculkCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cleanupAllSculkBlocks(true);
                    cancel();
                    return;
                }
                cleanupOldSculkBlocks();
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    private void registerSculkBlock(Location location, Material originalType, long durationTicks) {
        originalBlocks.put(location, originalType);
        sculkInfectedBlocks.add(location);

        new BukkitRunnable() {
            @Override
            public void run() {
                restoreSculkBlock(location, true);
            }
        }.runTaskLater(plugin, durationTicks);
    }

    private boolean restoreSculkBlock(Location location, boolean withEffects) {
        Material originalType = originalBlocks.get(location);
        if (originalType == null) return false;

        Block block = location.getBlock();
        if (block.getType() == Material.SCULK ||
                block.getType() == Material.SCULK_VEIN ||
                block.getType() == Material.SCULK_SENSOR ||
                block.getType() == Material.SCULK_CATALYST) {

            block.setType(originalType);

            if (withEffects && Math.random() < 0.1) {
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        location.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0);
            }
        }

        originalBlocks.remove(location);
        sculkInfectedBlocks.remove(location);
        return true;
    }

    private void cleanupOldSculkBlocks() {
        int cleaned = 0;
        Iterator<Location> iterator = sculkInfectedBlocks.iterator();

        while (iterator.hasNext()) {
            Location location = iterator.next();

            if (Math.random() < 0.1) {
                if (restoreSculkBlock(location, false)) {
                    cleaned++;
                }
                iterator.remove();
            }
        }

        if (cleaned > 0) {
            plugin.getLogger().fine("Nettoyage sculk périodique: " + cleaned + " blocs restaurés");
        }
    }

    private void cleanupAllSculkBlocks(boolean withEffects) {
        plugin.getLogger().info("Nettoyage complet des blocs sculk du Warden...");

        new BukkitRunnable() {
            int wave = 0;
            final int maxWaves = 8;

            @Override
            public void run() {
                if (wave >= maxWaves || sculkInfectedBlocks.isEmpty()) {
                    plugin.getLogger().info("Nettoyage sculk terminé après " + wave + " vagues");
                    cancel();
                    return;
                }

                Location center = burrowLocation != null ? burrowLocation : entity.getLocation();
                int radius = 5 + (wave * 6);
                int cleaned = cleanupSculkInRadius(center, radius, withEffects);

                if (cleaned > 0) {
                    plugin.getLogger().fine("Vague " + (wave + 1) + ": " + cleaned + " blocs restaurés (rayon " + radius + ")");
                }

                wave++;
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }

    private int cleanupSculkInRadius(Location center, int radius, boolean withEffects) {
        int cleaned = 0;
        Iterator<Location> iterator = sculkInfectedBlocks.iterator();

        while (iterator.hasNext()) {
            Location location = iterator.next();

            if (location.getWorld().equals(center.getWorld()) &&
                    location.distance(center) <= radius) {

                if (restoreSculkBlock(location, withEffects)) {
                    cleaned++;
                }
                iterator.remove();
            }
        }

        return cleaned;
    }

    // === MÉTHODES EXISTANTES CONSERVÉES ===

    private void handleMultiPlayerVibrationDetection(List<Player> players) {
        if (!isDetectingVibrations || currentPhase == BossPhase.CATACLYSM) return;

        for (Player player : players) {
            if (player.getVelocity().lengthSquared() > 0.01) {
                increaseAnger(player, 1);
            }

            if (random.nextDouble() < 0.08) { // Réduit la fréquence
                increaseAnger(player, 2);

                Location loc = player.getLocation();
                loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 12, 1, 1, 1, 0.1);
                loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 1.2f);
            }
        }
    }

    private void awakenMultiplayer(List<Player> players) {
        currentPhase = BossPhase.HUNTING;

        Bukkit.broadcastMessage("§4§l⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
        Bukkit.broadcastMessage("§0§l          LE GARDIEN S'ÉVEILLE !");
        Bukkit.broadcastMessage("§8§l     " + players.size() + " INTRUS DÉTECTÉS !");
        Bukkit.broadcastMessage("§4§l⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 3.0f, 0.5f);

        Location loc = entity.getLocation();
        for (int i = 0; i < 25; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 2, 0), 25, 4, 4, 4, 0.5);
                    loc.getWorld().createExplosion(loc, 0, false, false);
                }
            }.runTaskLater(plugin, i * 4L);
        }

        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, Math.min(players.size() - 1, 2)));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, Math.min(players.size() / 2, 2)));
    }

    // === MÉTHODES CONSERVÉES (pour maintenir la compatibilité) ===

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

    private void createSculkArena() {
        Location center = entity.getLocation();

        new BukkitRunnable() {
            int radius = 0;
            @Override
            public void run() {
                if (radius > 18) { // Arena plus grande pour le multijoueur
                    cancel();
                    return;
                }

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x*x + z*z <= radius*radius && random.nextDouble() < 0.25) {
                            Location blockLoc = center.clone().add(x, -1, z);
                            Block block = blockLoc.getBlock();

                            if (block.getType().isSolid() && !block.getType().name().contains("BEDROCK")) {
                                Material originalType = block.getType();
                                block.setType(Material.SCULK);
                                registerSculkBlock(blockLoc, originalType, 3000L);
                            }
                        }
                    }
                }

                radius++;
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    private void sonicBoom(Player target, double powerMultiplier) {
        lastSonicBoom = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 0.8f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || target.isDead()) return;

                Location from = entity.getEyeLocation();
                Location to = target.getEyeLocation();

                drawSonicBeam(from, to);

                double boomDamage = damage * 1.5 * powerMultiplier;
                target.damage(boomDamage, entity);
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));

                Vector knockback = target.getLocation().subtract(entity.getLocation()).toVector().normalize().multiply(2.5).setY(0.8);
                target.setVelocity(knockback);

                entity.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);
                target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation(), 5, 1, 1, 1);

                increaseAnger(target, 6);
            }
        }.runTaskLater(plugin, 40L);
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

    private void createSeismicFissure(Location start, double angleRadians, int length) {
        Vector direction = new Vector(Math.cos(angleRadians), 0, Math.sin(angleRadians));

        for (int i = 1; i <= length; i++) {
            Location fissureLoc = start.clone().add(direction.clone().multiply(i));
            Block block = fissureLoc.getBlock();

            if (block.getType().isSolid() && block.getType().getHardness() < 50) {
                block.setType(Material.AIR);
                fissureLoc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, fissureLoc.add(0.5, 0.5, 0.5), 10, 0.5, 0.5, 0.5, 0.1, block.getType().createBlockData());
            }
        }
    }

    private void summonSculkSensor(Location location) {
        ArmorStand sensor = location.getWorld().spawn(location, ArmorStand.class);
        sensor.setVisible(false);
        sensor.setGravity(false);
        sensor.setCustomName("§8Capteur Sculk");
        sensor.setCustomNameVisible(false);

        sculkMinions.add(sensor);

        Block block = location.getBlock();
        if (block.getType().isAir() || !block.getType().isSolid()) {
            Material originalType = block.getType();
            block.setType(Material.SCULK_SENSOR);
            registerSculkBlock(location, originalType, 600L);
        }

        new BukkitRunnable() {
            int lifetime = 600;
            @Override
            public void run() {
                if (lifetime <= 0 || sensor.isDead()) {
                    sculkMinions.remove(sensor);
                    if (!sensor.isDead()) sensor.remove();
                    cancel();
                    return;
                }

                for (Player p : getNearbyPlayersAt(location, 10)) {
                    if (p.getVelocity().lengthSquared() > 0.01) {
                        increaseAnger(p, 3);

                        location.getWorld().playSound(location, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 1.5f);
                        location.getWorld().spawnParticle(Particle.SCULK_CHARGE, location, 12, 5, 1, 5, 0.1);

                        if (entity.getLocation().distance(location) < 25) {
                            sonicBoom(p, 0.8);
                        }
                        break;
                    }
                }

                lifetime--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupMinions() {
        sculkMinions.removeIf(LivingEntity::isDead);
    }

    private void expandSculkDomainMultiplayer(List<Player> players) {
        lastSculkSpread = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SCULK_SPREAD, 2.0f, 0.8f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 4 || entity.isDead()) {
                    cancel();
                    return;
                }

                spreadSculkWave(entity.getLocation(), 10 + wave * 5);

                if (wave == 1 && sculkMinions.size() < players.size() + 1) {
                    summonSculkMinions();
                }

                wave++;
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    private void sculkInfestationAggressive(List<Player> players) {
        lastSculkSpread = System.currentTimeMillis();
        Bukkit.broadcastMessage("§8§l[BOSS] §0Le Gardien corrompt agressivement l'environnement !");
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SCULK_SPREAD, 3.0f, 0.5f);

        for (Player player : players) {
            if (player.isDead()) continue;
            createSculkTrap(player.getLocation());
            createTrackingSculkSensors(player.getLocation());
        }
    }

    private void spreadSculkWave(Location center, int radius) {
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
                        registerSculkBlock(blockLoc, originalType, 1800L);

                        if (random.nextDouble() < 0.2) {
                            Block above = block.getRelative(0, 1, 0);
                            if (above.getType().isAir()) {
                                above.setType(Material.SCULK_VEIN);
                                registerSculkBlock(above.getLocation(), Material.AIR, 1200L);
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
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Location trapLoc = location.clone().add(x, -1, z);
                Block block = trapLoc.getBlock();

                if (block.getType().isSolid()) {
                    Material originalType = block.getType();
                    block.setType(Material.SCULK);
                    registerSculkBlock(trapLoc, originalType, 800L);
                }
            }
        }

        new BukkitRunnable() {
            int lifetime = 800;
            @Override
            public void run() {
                if (lifetime <= 0) {
                    cancel();
                    return;
                }

                for (Player p : getNearbyPlayersAt(location, 4)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2));

                    if (lifetime % 25 == 0) {
                        p.damage(2.5, entity);
                        increaseAnger(p, 2);
                    }
                }

                if (lifetime % 15 == 0) {
                    location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 8, 3, 1, 3, 0.1);
                }

                lifetime--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void summonSculkMinions() {
        for (int i = 0; i < Math.min(activePlayerCount + 1, 4); i++) {
            Location spawnLoc = entity.getLocation().add(
                    (random.nextDouble() - 0.5) * 12,
                    0,
                    (random.nextDouble() - 0.5) * 12
            );
            spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

            String minionType = i % 2 == 0 ? "enderman_shadow" : "spider_venomous";
            LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);

            if (minion != null) {
                sculkMinions.add(minion);
                minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                minion.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));

                spawnLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, spawnLoc, 30, 1, 2, 1, 0.1);
                spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 1.2f);
            }
        }
    }

    private void seismicImpactMassive(Location impact) {
        impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_STEP, 3.0f, 0.3f);
        impact.getWorld().createExplosion(impact, 3.0f, false, false);

        for (int i = 0; i < 10; i++) {
            double angle = i * 36;
            createSeismicFissure(impact, Math.toRadians(angle), 18);
        }

        for (Player p : getNearbyPlayersAt(impact, 15)) {
            p.damage(damage * 1.8, entity);
            Vector knockback = p.getLocation().subtract(impact).toVector().normalize().multiply(4).setY(1.2);
            p.setVelocity(knockback);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 500, 0));
            increaseAnger(p, 20);
        }
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