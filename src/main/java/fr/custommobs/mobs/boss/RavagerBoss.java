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

public class RavagerBoss extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> summonedMinions = new ArrayList<>();
    private final Map<Player, Double> playerThreat = new HashMap<>(); // Système d'aggro
    private final Map<Player, Long> lastAttackedTime = new HashMap<>();

    // --- PHASES DE COMBAT ---
    private BossPhase currentPhase = BossPhase.PHASE_1;
    private boolean isEnraged = false;
    private boolean isPerformingSpecialAttack = false;
    private int playerCount = 0;
    private long lastTargetSwitch = 0;

    // --- COOLDOWNS ADAPTATIFS ---
    private long lastDevastatingCharge = 0;
    private long lastEarthquake = 0;
    private long lastSummonRaiders = 0;
    private long lastRage = 0;
    private long lastGroundPound = 0;
    private long lastAreaSlam = 0;
    private final long AREA_SLAM_COOLDOWN = 10000; // 10s

    private enum BossPhase {
        PHASE_1, // 100-70% HP - Attaques basiques, adaptation au groupe
        PHASE_2, // 70-40% HP - Plus agressif, summon minions, focus multijoueur
        PHASE_3  // 40-0% HP - Rage mode, attaques dévastatrices de zone
    }

    public RavagerBoss(CustomMobsPlugin plugin) {
        super(plugin, "ravager_boss");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 800.0; // Boss très résistant
        this.damage = 25.0;
        this.speed = 0.35;
    }

    @Override
    public LivingEntity spawn(Location location) {
        Ravager ravager = location.getWorld().spawn(location, Ravager.class);

        ravager.setCustomName("§4§l§kx§r §c§lDÉVASTATEUR PRIMORDIAL §4§l§kx§r");
        ravager.setCustomNameVisible(true);

        // Immunité au knockback et aux projectiles en phase 1
        ravager.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));

        setupEntity(ravager);
        startBossEffects();
        announceSpawn();

        return ravager;
    }

    @Override
    protected void onPlayerNear(Player target) {
        if (entity.isDead() || isPerformingSpecialAttack) return;

        List<Player> nearbyPlayers = getNearbyPlayers(25);
        playerCount = nearbyPlayers.size();

        // Met à jour l'aggro de tous les joueurs proches
        updateThreatLevels(nearbyPlayers);
        updatePhase();

        long currentTime = System.currentTimeMillis();

        // === SYSTÈME DE CIBLAGE INTELLIGENT ===
        Player primaryTarget = selectOptimalTarget(nearbyPlayers, currentTime);
        if (primaryTarget == null) primaryTarget = target;

        ((Ravager) entity).setTarget(primaryTarget);

        double distance = entity.getLocation().distance(primaryTarget.getLocation());

        // === IA ADAPTATIVE MULTIJOUEUR ===
        switch (currentPhase) {
            case PHASE_1:
                handlePhase1Multiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_2:
                handlePhase2Multiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_3:
                handlePhase3Multiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
        }

        // Attaque secondaire sur un autre joueur (20% de chance)
        if (nearbyPlayers.size() > 1 && Math.random() < 0.2) {
            Player finalPrimaryTarget = primaryTarget;
            Player secondaryTarget = nearbyPlayers.stream()
                    .filter(p -> !p.equals(finalPrimaryTarget))
                    .findFirst().orElse(null);
            if (secondaryTarget != null) {
                performSecondaryAttack(secondaryTarget);
            }
        }
    }

    private void handlePhase1Multiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Ajuste les cooldowns selon le nombre de joueurs
        long groundPoundCooldown = Math.max(6000, 12000 - (players.size() * 1000)); // Plus rapide avec plus de joueurs
        long chargeCooldown = Math.max(8000, 15000 - (players.size() * 1000));

        if (players.size() >= 3 && currentTime - lastAreaSlam > AREA_SLAM_COOLDOWN) {
            // Attaque spéciale multijoueur : frappe de zone rotative
            rotatingAreaSlam(players);
        } else if (distance < 4 && currentTime - lastGroundPound > groundPoundCooldown) {
            groundPound();
        } else if (distance > 6 && distance < 20 && currentTime - lastDevastatingCharge > chargeCooldown) {
            // Charge vers le joueur avec le plus d'aggro
            Player highThreatTarget = getHighestThreatPlayer(players);
            devastatingCharge(highThreatTarget != null ? highThreatTarget : target);
        } else if (distance <= 5) {
            attack(target);
        }
    }

    private void handlePhase2Multiplayer(Player target, double distance, long currentTime, List<Player> players) {
        cleanUpMinions();

        // Cooldowns réduits en multijoueur
        long earthquakeCooldown = Math.max(15000, 25000 - (players.size() * 2000));
        long summonCooldown = Math.max(18000, 30000 - (players.size() * 2000));

        // Priorité aux attaques de groupe
        if (players.size() > 1 && currentTime - lastEarthquake > earthquakeCooldown) {
            earthquakeSlamMultiplayer(players);
        } else if (summonedMinions.size() < Math.min(players.size() + 1, 5) && currentTime - lastSummonRaiders > summonCooldown) {
            summonRaidersAdaptive(players.size());
        } else if (players.size() >= 2 && currentTime - lastAreaSlam > (AREA_SLAM_COOLDOWN * 0.7)) {
            chainedSlam(players);
        } else if (distance > 8 && currentTime - lastDevastatingCharge > (12000 * 0.7)) {
            devastatingCharge(target);
        } else if (distance <= 6) {
            attack(target);
        }
    }

    private void handlePhase3Multiplayer(Player target, double distance, long currentTime, List<Player> players) {
        if (!isEnraged && currentTime - lastRage > (45000 - players.size() * 5000L)) {
            enterRageMode();
        }

        // Phase finale : attaques dévastatrices constantes
        long baseCooldown = Math.max(8000, 15000 - (players.size() * 1500));

        if (players.size() > 1 && currentTime - lastEarthquake > (baseCooldown * 0.6)) {
            apocalypticSlam(players);
        } else if (distance > 5 && currentTime - lastDevastatingCharge > (baseCooldown * 0.5)) {
            berserkerCharge(players); // Charge multiple
        } else if (distance <= 8 && currentTime - lastGroundPound > (baseCooldown * 0.6)) {
            groundPound();
        } else {
            attack(target);
        }
    }

    // === SYSTÈME D'AGGRO AMÉLIORÉ ===

    private void updateThreatLevels(List<Player> players) {
        for (Player player : players) {
            // L'aggro diminue avec le temps
            double currentThreat = playerThreat.getOrDefault(player, 0.0);
            playerThreat.put(player, Math.max(0, currentThreat * 0.99));
        }
    }

    private Player selectOptimalTarget(List<Player> players, long currentTime) {
        if (players.isEmpty()) return null;

        // Change de cible toutes les 8 secondes ou selon les conditions
        boolean shouldSwitchTarget = currentTime - lastTargetSwitch > 8000 ||
                Math.random() < 0.15; // 15% de chance de changer

        if (!shouldSwitchTarget && lastTargetSwitch > 0) {
            // Garde la cible actuelle si elle est encore valide
            return players.stream()
                    .filter(p -> ((Ravager) entity).getTarget() != null &&
                            ((Ravager) entity).getTarget().equals(p))
                    .findFirst()
                    .orElse(null);
        }

        lastTargetSwitch = currentTime;

        // Sélection intelligente basée sur plusieurs critères
        return players.stream()
                .max((p1, p2) -> {
                    double score1 = calculateTargetScore(p1);
                    double score2 = calculateTargetScore(p2);
                    return Double.compare(score1, score2);
                })
                .orElse(players.getFirst());
    }

    private double calculateTargetScore(Player player) {
        double score = 0;

        // Facteur distance (plus proche = plus de score)
        double distance = entity.getLocation().distance(player.getLocation());
        score += Math.max(0, 30 - distance);

        // Facteur aggro/threat
        score += playerThreat.getOrDefault(player, 0.0) * 2;

        // Priorité aux joueurs avec moins de vie
        score += (1.0 - (player.getHealth() / player.getMaxHealth())) * 25;

        // Bonus si le joueur a attaqué récemment
        Long lastAttacked = lastAttackedTime.get(player);
        if (lastAttacked != null && System.currentTimeMillis() - lastAttacked < 10000) {
            score += 15;
        }

        return score;
    }

    private Player getHighestThreatPlayer(List<Player> players) {
        return players.stream()
                .max(Comparator.comparingDouble(p -> playerThreat.getOrDefault(p, 0.0)))
                .orElse(null);
    }

    @Override
    public void attack(Player target) {
        // Attaque de base améliorée selon la phase et le nombre de joueurs
        double damageMultiplier = (isEnraged ? 1.5 : 1.0) * (1.0 + playerCount * 0.1);

        target.damage(damage * damageMultiplier, entity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

        if (currentPhase == BossPhase.PHASE_3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
        }

        // Augmente l'aggro du joueur attaqué
        increaseThreat(target, 10);
        lastAttackedTime.put(target, System.currentTimeMillis());

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 2.0f, 0.8f);
        entity.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 20, 1, 1, 1, 0.5);
    }

    private void performSecondaryAttack(Player target) {
        if (Math.random() < 0.6) { // 60% chance d'attaque à distance
            // Projectile de debris
            FallingBlock debris = entity.getWorld().spawnFallingBlock(
                    entity.getEyeLocation().add(0, 3, 0),
                    Material.COBBLESTONE.createBlockData()
            );
            Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
            debris.setVelocity(direction.multiply(1.2).setY(0.8));
            debris.setDropItem(false);
        } else { // 40% chance d'onde de choc
            Vector shockDirection = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
            createDirectionalShockwave(entity.getLocation(), shockDirection, 12);
        }
    }

    // === NOUVELLES ATTAQUES MULTIJOUEUR ===

    private void rotatingAreaSlam(List<Player> players) {
        isPerformingSpecialAttack = true;
        lastAreaSlam = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3.0f, 0.8f);
        Bukkit.broadcastMessage("§c§l[BOSS] §4Le Dévastateur prépare une attaque rotative !");

        new BukkitRunnable() {
            int angle = 0;
            int rotations = 0;
            @Override
            public void run() {
                if (rotations >= 2 || entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    cancel();
                    return;
                }

                // Crée une ligne de destruction rotative
                Location center = entity.getLocation();
                for (int r = 3; r <= 15; r += 2) {
                    double radians = Math.toRadians(angle);
                    Location impactLoc = center.clone().add(
                            Math.cos(radians) * r,
                            0,
                            Math.sin(radians) * r
                    );

                    createShockwaveRing(impactLoc, 2);
                }

                angle += 30; // Rotation de 30 degrés par tick
                if (angle >= 360) {
                    angle = 0;
                    rotations++;
                }
            }
        }.runTaskTimer(plugin, 40L, 2L); // Délai de 2s puis très rapide
    }

    private void chainedSlam(List<Player> players) {
        isPerformingSpecialAttack = true;
        lastAreaSlam = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 2.0f, 0.7f);

        new BukkitRunnable() {
            int playerIndex = 0;
            @Override
            public void run() {
                if (playerIndex >= players.size() || entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    cancel();
                    return;
                }

                Player target = players.get(playerIndex);
                if (!target.isDead()) {
                    Location slamLoc = target.getLocation();

                    // Avertissement visuel
                    drawTargetingCircle(slamLoc, 4);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            createMassiveExplosion(slamLoc);
                        }
                    }.runTaskLater(plugin, 15L); // 0.75s de délai
                }

                playerIndex++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Une frappe par seconde
    }

    private void berserkerCharge(List<Player> players) {
        isPerformingSpecialAttack = true;
        lastDevastatingCharge = System.currentTimeMillis();

        Bukkit.broadcastMessage("§4§l[BOSS] §cCHARGE BERSERKER ! Dispersez-vous !");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3.0f, 0.5f);

        new BukkitRunnable() {
            int chargeCount = 0;
            @Override
            public void run() {
                if (chargeCount >= Math.min(players.size(), 3) || entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    entity.setVelocity(new Vector(0, 0, 0));
                    cancel();
                    return;
                }

                Player target = players.get(chargeCount);
                if (!target.isDead()) {
                    Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
                    entity.setVelocity(direction.multiply(3.0));

                    // Traînée de destruction
                    createChargeTrail(entity.getLocation(), direction, 8);
                }

                chargeCount++;
            }
        }.runTaskTimer(plugin, 20L, 30L); // Une charge toutes les 1.5s
    }

    private void apocalypticSlam(List<Player> players) {
        isPerformingSpecialAttack = true;
        lastEarthquake = System.currentTimeMillis();

        Bukkit.broadcastMessage("§4§l§k⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡");
        Bukkit.broadcastMessage("§c§l        APOCALYPSE TELLURIQUE !");
        Bukkit.broadcastMessage("§4§l§k⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.3f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 6 || entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    cancel();
                    return;
                }

                // Frappe chaque joueur individuellement
                for (Player p : players) {
                    if (p.isDead()) continue;

                    // Multiples points d'impact autour du joueur
                    for (int i = 0; i < 3; i++) {
                        Location impactLoc = p.getLocation().add(
                                (random.nextDouble() - 0.5) * 8,
                                0,
                                (random.nextDouble() - 0.5) * 8
                        );

                        createMassiveExplosion(impactLoc);
                    }
                }

                // Impact central massif
                if (wave % 2 == 0) {
                    createMassiveExplosion(entity.getLocation());
                }

                wave++;
            }
        }.runTaskTimer(plugin, 30L, 25L); // Délai initial puis toutes les 1.25s
    }

    private void earthquakeSlamMultiplayer(List<Player> players) {
        isPerformingSpecialAttack = true;
        lastEarthquake = System.currentTimeMillis();

        // Calcule le centre du groupe avec pondération
        Vector centerPoint = calculateWeightedCenter(players);
        Location epicenter = centerPoint.toLocation(entity.getWorld());

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2.5f, 0.6f);
        Bukkit.broadcastMessage("§c§l[BOSS] §4Séisme dévastateur imminent !");

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 4 || entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    cancel();
                    return;
                }

                double radius = 4 + (wave * 3);
                createEarthquakeFissure(epicenter, radius);

                // Dégâts et effets adaptatifs
                for (Player p : getNearbyPlayersAt(epicenter, radius + 2)) {
                    double distance = p.getLocation().distance(epicenter);
                    if (distance <= radius + 2) {
                        double damageReduction = Math.max(0.4, 1.0 - (distance / (radius + 2)));
                        p.damage(damage * 0.9 * damageReduction, entity);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));

                        Vector knockup = p.getLocation().toVector().subtract(epicenter.toVector()).normalize();
                        knockup.setY(0.7);
                        p.setVelocity(knockup.multiply(1.8));

                        increaseThreat(p, 5);
                    }
                }

                wave++;
            }
        }.runTaskTimer(plugin, 40L, 20L); // Délai de 2s puis toutes les secondes
    }

    private void summonRaidersAdaptive(int playerCount) {
        isPerformingSpecialAttack = true;
        lastSummonRaiders = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_CELEBRATE, 2.0f, 1.0f);

        // Nombre de sbires adaptatif
        int minionsToSummon = Math.min(playerCount + 1, 5);

        for (int i = 0; i < minionsToSummon; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location spawnLoc = entity.getLocation().add(
                            (random.nextDouble() - 0.5) * 10,
                            0,
                            (random.nextDouble() - 0.5) * 10
                    );
                    spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

                    // Types variés selon la phase
                    String minionType = switch (currentPhase) {
                        case PHASE_1 -> random.nextBoolean() ? "zombie_warrior" : "skeleton_archer";
                        case PHASE_2 -> random.nextBoolean() ? "golem_stone" : "witch_cursed";
                        case PHASE_3 -> random.nextBoolean() ? "enderman_shadow" : "spider_venomous";
                    };

                    LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);

                    if (minion != null) {
                        summonedMinions.add(minion);

                        // Bonus selon le nombre de joueurs
                        if (playerCount >= 3) {
                            minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                            minion.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
                        }

                        spawnLoc.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 30, 1, 2, 1, 0.1);
                        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);
                    }
                }
            }.runTaskLater(plugin, i * 15L);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                isPerformingSpecialAttack = false;
            }
        }.runTaskLater(plugin, minionsToSummon * 15L + 20L);
    }

    // === MÉTHODES UTILITAIRES AMÉLIORÉES ===

    private void increaseThreat(Player player, double amount) {
        double currentThreat = playerThreat.getOrDefault(player, 0.0);
        playerThreat.put(player, currentThreat + amount);
    }

    private Vector calculateWeightedCenter(List<Player> players) {
        Vector center = new Vector();
        double totalWeight = 0;

        for (Player player : players) {
            double weight = 1.0 + playerThreat.getOrDefault(player, 0.0) * 0.1; // L'aggro influence la position
            center.add(player.getLocation().toVector().multiply(weight));
            totalWeight += weight;
        }

        return center.multiply(1.0 / totalWeight);
    }

    private void drawTargetingCircle(Location center, double radius) {
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            Location particleLoc = center.clone().add(
                    Math.cos(angle) * radius,
                    0.1,
                    Math.sin(angle) * radius
            );
            center.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    private void createChargeTrail(Location start, Vector direction, int length) {
        for (int i = 1; i <= length; i++) {
            Location trailLoc = start.clone().add(direction.clone().multiply(i));
            start.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, trailLoc, 10, 1, 0.5, 1, 0.1, Material.STONE.createBlockData());

            // Dégâts aux joueurs touchés
            for (Player p : getNearbyPlayersAt(trailLoc, 2)) {
                p.damage(damage * 0.8, entity);
                Vector knockback = p.getLocation().subtract(trailLoc).toVector().normalize().multiply(1.5).setY(0.6);
                p.setVelocity(knockback);
                increaseThreat(p, 8);
            }
        }
    }

    private void createDirectionalShockwave(Location start, Vector direction, int length) {
        new BukkitRunnable() {
            int distance = 0;
            @Override
            public void run() {
                if (distance >= length) {
                    cancel();
                    return;
                }

                Location waveLoc = start.clone().add(direction.clone().multiply(distance));
                createShockwaveRing(waveLoc, 3);
                distance += 2;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void createMassiveExplosion(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        location.getWorld().createExplosion(location, 0, false, false);

        for (int i = 0; i < 50; i++) {
            location.getWorld().spawnParticle(Particle.EXPLOSION, location.clone().add(
                    (random.nextDouble() - 0.5) * 6,
                    random.nextDouble() * 3,
                    (random.nextDouble() - 0.5) * 6
            ), 1, 0, 0, 0, 0);
        }

        for (Player p : getNearbyPlayersAt(location, 5)) {
            p.damage(damage * 1.2, entity);
            Vector knockback = p.getLocation().subtract(location).toVector().normalize().multiply(2.5).setY(0.8);
            p.setVelocity(knockback);
            increaseThreat(p, 10);
        }
    }

    // === MÉTHODES INCHANGÉES (conservées du code original) ===

    private void updatePhase() {
        double healthPercent = entity.getHealth() / maxHealth;

        BossPhase newPhase;
        if (healthPercent > 0.7) {
            newPhase = BossPhase.PHASE_1;
        } else if (healthPercent > 0.4) {
            newPhase = BossPhase.PHASE_2;
        } else {
            newPhase = BossPhase.PHASE_3;
        }

        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            announcePhaseChange();
        }
    }

    private void announcePhaseChange() {
        String message = switch (currentPhase) {
            case PHASE_1 -> "§6Le Dévastateur vous observe...";
            case PHASE_2 -> "§e§lLe Dévastateur devient plus agressif !";
            case PHASE_3 -> "§c§l⚠ ATTENTION ! Le Dévastateur entre dans sa phase finale ! ⚠";
        };

        Bukkit.broadcastMessage("§4§l[BOSS] §r" + message);
    }

    private void enterRageMode() {
        isEnraged = true;
        lastRage = System.currentTimeMillis();

        Bukkit.broadcastMessage("§4§l[BOSS] §c" + entity.getCustomName() + " §4ENTRE EN RAGE !");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.5f);

        Location loc = entity.getLocation();
        for (int i = 0; i < 50; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 2, 0), 20, 2, 2, 2, 0.5);
                    loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 1, 0), 10, 1, 1, 1, 0);
                }
            }.runTaskLater(plugin, i * 2L);
        }

        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        entity.setCustomName("§4§l§k|||§r §c§lDÉVASTATEUR ENRAGÉ §4§l§k|||§r");
    }

    private void groundPound() {
        isPerformingSpecialAttack = true;
        lastGroundPound = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 2.0f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    return;
                }

                Location center = entity.getLocation();
                center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 2.0f, 0.3f);
                center.getWorld().createExplosion(center, 0, false, false);

                for (int radius = 1; radius <= 8; radius++) {
                    final int r = radius;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            createShockwaveRing(center, r);
                        }
                    }.runTaskLater(plugin, radius * 3L);
                }

                isPerformingSpecialAttack = false;
            }
        }.runTaskLater(plugin, 15L);
    }

    private void devastatingCharge(Player target) {
        isPerformingSpecialAttack = true;
        lastDevastatingCharge = System.currentTimeMillis();

        Location start = entity.getLocation();
        Vector direction = target.getLocation().subtract(start).toVector().normalize();
        double maxDistance = Math.min(20, start.distance(target.getLocation()) + 5);

        entity.getWorld().playSound(start, Sound.ENTITY_RAVAGER_ROAR, 3.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            double traveledDistance = 0;
            double currentSpeed = 1.5;

            @Override
            public void run() {
                if (ticks > 80 || entity.isDead() || traveledDistance >= maxDistance) {
                    isPerformingSpecialAttack = false;
                    entity.setVelocity(new Vector(0, 0, 0));
                    cancel();
                    return;
                }

                if (ticks > 40) {
                    currentSpeed = Math.max(0.3, currentSpeed * 0.95);
                }

                Vector velocity = direction.clone().multiply(currentSpeed);
                entity.setVelocity(velocity);
                traveledDistance += currentSpeed;

                Location loc = entity.getLocation();
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 15, 1, 0.5, 1, 0.1);
                loc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 20, 1, 0.1, 1, 0.5, Material.STONE.createBlockData());

                if (ticks % 8 == 0) {
                    destroyBlocksInPath(loc, 1);
                }

                for (Player p : getNearbyPlayersAt(loc, 3)) {
                    p.damage(damage * 1.3, entity);
                    Vector knockback = p.getLocation().subtract(loc).toVector().normalize().multiply(1.8).setY(0.6);
                    p.setVelocity(knockback);
                    increaseThreat(p, 12);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    private void createShockwaveRing(Location center, int radius) {
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            Location loc = center.clone().add(
                    Math.cos(angle) * radius,
                    0.5,
                    Math.sin(angle) * radius
            );

            center.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 5, 0.5, 0.5, 0.5, 0.1, Material.STONE.createBlockData());

            for (Player p : getNearbyPlayersAt(loc, 2)) {
                p.damage(damage * 0.6, entity);
                Vector knockback = p.getLocation().subtract(center).toVector().normalize().multiply(1.5).setY(0.4);
                p.setVelocity(knockback);
                increaseThreat(p, 3);
            }
        }
    }

    private void createEarthquakeFissure(Location center, double radius) {
        center.getWorld().playSound(center, Sound.BLOCK_STONE_BREAK, 1.5f, 0.8f);

        for (int i = 0; i < 30; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double r = Math.random() * radius;
            Location loc = center.clone().add(
                    Math.cos(angle) * r,
                    0,
                    Math.sin(angle) * r
            );

            center.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 8, 0.5, 0.1, 0.5, 0.1, Material.DIRT.createBlockData());
        }
    }

    private void destroyBlocksInPath(Location center, int radius) {
        List<Block> blocksToDestroy = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    Material type = block.getType();

                    if (type.isBlock() && !type.name().contains("BEDROCK") &&
                            !type.name().contains("OBSIDIAN") &&
                            !type.name().contains("BARRIER") &&
                            type.getHardness() > 0 && type.getHardness() < 5.0f &&
                            Math.random() < 0.6) {

                        blocksToDestroy.add(block);
                    }
                }
            }
        }

        for (Block block : blocksToDestroy) {
            Material originalType = block.getType();
            block.breakNaturally();

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType().isAir()) {
                        block.setType(originalType);
                    }
                }
            }.runTaskLater(plugin, 600L);
        }
    }

    private void cleanUpMinions() {
        summonedMinions.removeIf(LivingEntity::isDead);
    }

    private void startBossEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                Location loc = entity.getLocation();
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1.5, 0), 8, 1, 1, 1, 0.1);

                if (isEnraged) {
                    loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 2, 0), 5, 1.5, 1.5, 1.5, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void announceSpawn() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§4§l§k=========================================");
        Bukkit.broadcastMessage("§4§l           ⚡ BOSS APPARU ! ⚡");
        Bukkit.broadcastMessage("§c§l      DÉVASTATEUR PRIMORDIAL");
        Bukkit.broadcastMessage("§4§l§k=========================================");
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
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
        // Géré par l'IA de phases
    }
}