package fr.custommobs.mobs.boss;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class WitherBoss extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> summonedUndead = new ArrayList<>();
    private final List<LivingEntity> soulOrbs = new ArrayList<>();
    private final Map<Player, Double> playerThreat = new HashMap<>();
    private final Map<Player, Integer> playerCurses = new HashMap<>();

    // --- ÉTAT DU BOSS ---
    private BossPhase currentPhase = BossPhase.AWAKENING;
    private boolean isShielded = false;
    private boolean isChanneling = false;
    private int necroticStacks = 0;
    private Location altarLocation;
    private int activePlayerCount = 0;
    private long lastTargetSwitch = 0;

    // --- COOLDOWNS ADAPTATIFS ---
    private long lastSkullBarrage = 0;
    private long lastSoulDrain = 0;
    private long lastNecromancy = 0;
    private long lastWitherShield = 0;
    private long lastApocalypse = 0;
    private long lastCurseStorm = 0;
    private final long CURSE_STORM_COOLDOWN = 18000; // 18s

    private enum BossPhase {
        AWAKENING,  // 0-15s : Invulnérable, setup arena
        PHASE_1,    // 100-75% HP : Attaques aériennes multiples
        PHASE_2,    // 75-40% HP : Invocations + Shield + Malédictions
        PHASE_3,    // 40-15% HP : Attaques dévastatrices coordonnées
        DEATH_THROES // 15-0% HP : Explosions finales multiples
    }

    public WitherBoss(CustomMobsPlugin plugin) {
        super(plugin, "wither_boss");
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 1024.0; // Le plus résistant des boss
        this.damage = 30.0;
        this.speed = 0.6; // Vol rapide
    }

    @Override
    public LivingEntity spawn(Location location) {
        Wither wither = location.getWorld().spawn(location, Wither.class);

        wither.setCustomName("§5§l§k⚡§r §d§lARCHLICHE NÉCROSIS §5§l§k⚡§r");
        wither.setCustomNameVisible(true);

        wither.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        wither.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));

        this.altarLocation = location.clone();

        setupEntity(wither);
        startAwakeningSequence();

        return wither;
    }

    @Override
    protected void onPlayerNear(Player target) {
        if (entity.isDead()) return;

        List<Player> nearbyPlayers = getNearbyPlayers(35);
        activePlayerCount = nearbyPlayers.size();

        updateThreatLevels(nearbyPlayers);
        updatePhase();
        cleanupMinions();

        long currentTime = System.currentTimeMillis();

        // === SYSTÈME DE CIBLAGE MULTIJOUEUR INTELLIGENT ===
        Player primaryTarget = selectOptimalTarget(nearbyPlayers, currentTime);
        if (primaryTarget == null) primaryTarget = target;

        ((Wither) entity).setTarget(primaryTarget);

        double distance = entity.getLocation().distance(primaryTarget.getLocation());

        // === IA MULTIJOUEUR ADAPTATIVE ===
        switch (currentPhase) {
            case AWAKENING:
                // Invulnérable pendant l'éveil
                break;
            case PHASE_1:
                handlePhase1Multiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_2:
                handlePhase2Multiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_3:
                handlePhase3Multiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
            case DEATH_THROES:
                handleDeathThroesMultiplayer(primaryTarget, distance, currentTime, nearbyPlayers);
                break;
        }

        // Attaques secondaires sur d'autres joueurs (25% de chance)
        if (nearbyPlayers.size() > 1 && Math.random() < 0.25) {
            executeSecondaryAttacks(nearbyPlayers, primaryTarget);
        }
    }

    private void handlePhase1Multiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Cooldowns adaptatifs selon le nombre de joueurs
        long barrageCD = Math.max(10000, 15000 - (players.size() * 1000));
        long drainCD = Math.max(15000, 20000 - (players.size() * 1000));

        if (players.size() >= 3 && currentTime - lastCurseStorm > CURSE_STORM_COOLDOWN) {
            curseStormMultiplayer(players);
        } else if (currentTime - lastSkullBarrage > barrageCD) {
            if (players.size() > 2) {
                skullBarrageCoordinated(players);
            } else if (players.size() > 1) {
                skullBarrageMulti(players);
            } else {
                skullBarrageSingle(target);
            }
        } else if (distance > 15 && currentTime - lastSoulDrain > drainCD) {
            if (players.size() > 1) {
                soulDrainArea(players);
            } else {
                soulDrain(target);
            }
        } else {
            attack(target);
        }

        maintainFlightAltitude(15 + players.size() * 2); // Plus haut avec plus de joueurs
    }

    private void handlePhase2Multiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Cooldowns réduits et plus agressif
        long shieldCD = Math.max(35000, 45000 - (players.size() * 2000));
        long necroCD = Math.max(20000, 30000 - (players.size() * 2000));

        if (!isShielded && currentTime - lastWitherShield > shieldCD) {
            activateWitherShield();
        }

        cleanupMinions();
        int maxMinions = Math.min(players.size() + 2, 8);
        if (summonedUndead.size() < maxMinions && currentTime - lastNecromancy > necroCD) {
            massNecromancyAdaptive(players);
        } else if (currentTime - lastSkullBarrage > 10000) {
            skullBarrageOmnidirectional(players);
        } else if (distance > 20) {
            teleportToArena();
        } else {
            attack(target);
        }

        maintainFlightAltitude(20 + players.size() * 2);
    }

    private void handlePhase3Multiplayer(Player target, double distance, long currentTime, List<Player> players) {
        // Phase dévastatrice - attaques coordonnées
        long apocalypseCD = Math.max(45000, 60000 - (players.size() * 3000));

        if (currentTime - lastApocalypse > apocalypseCD && !players.isEmpty()) {
            necroticApocalypseMultiplayer(players);
        } else if (currentTime - lastSoulDrain > 12000) {
            soulDrainOmnipresent(players);
        } else if (currentTime - lastSkullBarrage > 8000) {
            skullBarrageDevastating(players);
        } else {
            attack(target);
        }

        maintainFlightAltitude(25 + players.size() * 3);
    }

    private void handleDeathThroesMultiplayer(Player target, double distance, long currentTime, List<Player> players) {
        if (!isChanneling) {
            finalExplosionSequenceMultiplayer(players);
        }
    }

    // === SYSTÈME D'AGGRO ET CIBLAGE ===

    private void updateThreatLevels(List<Player> players) {
        for (Player player : players) {
            double currentThreat = playerThreat.getOrDefault(player, 0.0);
            playerThreat.put(player, Math.max(0, currentThreat * 0.98)); // Déclin plus lent
        }
    }

    private Player selectOptimalTarget(List<Player> players, long currentTime) {
        if (players.isEmpty()) return null;

        // Change de cible plus fréquemment en multijoueur
        boolean shouldSwitch = currentTime - lastTargetSwitch > (players.size() > 2 ? 6000 : 10000) ||
                Math.random() < (players.size() * 0.05); // Plus de chance avec plus de joueurs

        if (!shouldSwitch && lastTargetSwitch > 0) {
            return players.stream()
                    .filter(p -> ((Wither) entity).getTarget() != null &&
                            ((Wither) entity).getTarget().equals(p))
                    .findFirst()
                    .orElse(null);
        }

        lastTargetSwitch = currentTime;

        return players.stream()
                .max((p1, p2) -> {
                    double score1 = calculateTargetPriority(p1);
                    double score2 = calculateTargetPriority(p2);
                    return Double.compare(score1, score2);
                })
                .orElse(players.get(random.nextInt(players.size())));
    }

    private double calculateTargetPriority(Player player) {
        double priority = 0;

        // Distance inversée (plus proche = plus de priorité)
        double distance = entity.getLocation().distance(player.getLocation());
        priority += Math.max(0, 40 - distance);

        // Niveau d'aggro
        priority += playerThreat.getOrDefault(player, 0.0) * 3;

        // Priorité aux joueurs avec moins de vie (prédateur)
        priority += (1.0 - (player.getHealth() / player.getMaxHealth())) * 30;

        // Bonus si le joueur a beaucoup de malédictions
        priority += playerCurses.getOrDefault(player, 0) * 8;

        // Facteur aléatoire pour imprévisibilité
        priority += random.nextDouble() * 15;

        return priority;
    }

    private void increaseThreat(Player player, double amount) {
        double currentThreat = playerThreat.getOrDefault(player, 0.0);
        playerThreat.put(player, currentThreat + amount);
    }

    private void addCurse(Player player) {
        int currentCurses = playerCurses.getOrDefault(player, 0);
        playerCurses.put(player, currentCurses + 1);
    }

    @Override
    public void attack(Player target) {
        if (entity instanceof Wither wither) {
            // Attaque adaptée au nombre de joueurs
            if (activePlayerCount > 2 && Math.random() < 0.4) {
                // Attaque de zone
                createNecroticExplosion(target.getLocation());
            } else {
                // Attaque ciblée
                WitherSkull skull = wither.launchProjectile(WitherSkull.class);
                skull.setDirection(target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize());
                skull.setCharged(currentPhase == BossPhase.PHASE_3 || currentPhase == BossPhase.DEATH_THROES);
                skull.setShooter(entity);
            }

            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.8f);

            increaseThreat(target, 5);
            necroticStacks++;
            if (necroticStacks >= 5) {
                createNecroticExplosion(target.getLocation());
                necroticStacks = 0;
            }
        }
    }

    private void executeSecondaryAttacks(List<Player> players, Player primaryTarget) {
        List<Player> secondaryTargets = players.stream()
                .filter(p -> !p.equals(primaryTarget))
                .collect(Collectors.toList());

        if (!secondaryTargets.isEmpty()) {
            Player secondary = secondaryTargets.get(random.nextInt(secondaryTargets.size()));

            if (Math.random() < 0.6) {
                // Projectile nécrotique
                WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
                skull.setDirection(secondary.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize());
                skull.setShooter(entity);
                skull.setCharged(false);
            } else {
                // Malédiction à distance
                applyCurseToPlayer(secondary);
            }
        }
    }

    // === NOUVELLES ATTAQUES MULTIJOUEUR ===

    private void curseStormMultiplayer(List<Player> players) {
        isChanneling = true;
        lastCurseStorm = System.currentTimeMillis();

        Bukkit.broadcastMessage("§5§l[BOSS] §dL'Archliche déchaîne une tempête de malédictions !");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 3.0f, 0.5f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 5 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Frappe chaque joueur avec des malédictions rotatives
                for (int i = 0; i < players.size(); i++) {
                    Player player = players.get(i);
                    if (player.isDead()) continue;

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            createCurseVortex(player.getLocation());
                            applyCurseToPlayer(player);
                        }
                    }.runTaskLater(plugin, i * 5L); // Décalage temporel
                }

                wave++;
            }
        }.runTaskTimer(plugin, 0L, 25L); // Toutes les 1.25s
    }

    private void skullBarrageCoordinated(List<Player> players) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.3f);
        Bukkit.broadcastMessage("§5§l[BOSS] §dBarrage coordonné imminent !");

        // Phase 1 : Marquage des cibles
        for (Player player : players) {
            drawTargetingBeam(entity.getEyeLocation(), player.getEyeLocation());
        }

        new BukkitRunnable() {
            int salvo = 0;
            @Override
            public void run() {
                if (salvo >= 6 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Alterne entre les joueurs
                for (int i = 0; i < players.size(); i++) {
                    Player target = players.get(i);
                    if (target.isDead()) continue;

                    // Délai progressif pour chaque joueur
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            fireTrackingSkull(target, salvo % 2 == 0); // Alterne têtes bleues/noires
                        }
                    }.runTaskLater(plugin, i * 3L);
                }

                salvo++;
            }
        }.runTaskTimer(plugin, 40L, 15L); // Délai initial puis toutes les 0.75s
    }

    private void skullBarrageOmnidirectional(List<Player> players) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.4f);

        new BukkitRunnable() {
            int burst = 0;
            @Override
            public void run() {
                if (burst >= 8 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Barrage omnidirectionnel avec focus sur les joueurs
                for (int angle = 0; angle < 360; angle += 45) {
                    Vector direction = new Vector(
                            Math.cos(Math.toRadians(angle)),
                            -0.2,
                            Math.sin(Math.toRadians(angle))
                    ).normalize();

                    WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
                    skull.setDirection(direction);
                    skull.setShooter(entity);
                    skull.setCharged(false);
                }

                // Focus spécial sur chaque joueur
                for (Player player : players) {
                    if (!player.isDead()) {
                        fireTrackingSkull(player, false);
                    }
                }

                burst++;
            }
        }.runTaskTimer(plugin, 0L, 12L); // Toutes les 0.6s
    }

    private void skullBarrageDevastating(List<Player> players) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        Bukkit.broadcastMessage("§5§l⚠ BARRAGE FINAL IMMINENT ! ⚠");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_HURT, 3.0f, 0.2f);

        new BukkitRunnable() {
            int intensity = 0;
            @Override
            public void run() {
                if (intensity >= 12 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Intensité croissante
                int skullsPerPlayer = 1 + (intensity / 3);

                for (Player player : players) {
                    if (player.isDead()) continue;

                    for (int i = 0; i < skullsPerPlayer; i++) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                fireTrackingSkull(player, true); // Toutes les têtes sont chargées
                            }
                        }.runTaskLater(plugin, i * 2L);
                    }
                }

                intensity++;
            }
        }.runTaskTimer(plugin, 0L, 8L); // Toutes les 0.4s
    }

    private void soulDrainOmnipresent(List<Player> players) {
        isChanneling = true;
        lastSoulDrain = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 3.0f, 0.3f);
        Bukkit.broadcastMessage("§5§l[BOSS] §dDrain d'âmes omnipresent !");

        new BukkitRunnable() {
            int duration = 0;
            @Override
            public void run() {
                if (duration > 100 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Drain simultané de tous les joueurs
                for (Player player : players) {
                    if (player.isDead()) continue;

                    drawSoulLink(entity.getEyeLocation(), player.getEyeLocation());
                    player.damage(1.8, entity);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));

                    // Crée des orbes d'âme flottantes
                    if (Math.random() < 0.15) {
                        createSoulOrb(player.getLocation().add(0, 2, 0));
                    }
                }

                // Régénération du boss basée sur le nombre de joueurs
                entity.setHealth(Math.min(entity.getHealth() + players.size() * 0.8, maxHealth));
                duration += 3;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void massNecromancyAdaptive(List<Player> players) {
        isChanneling = true;
        lastNecromancy = System.currentTimeMillis();

        Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche invoque une armée adaptée...");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.7f);

        // Nombre de sbires adaptatif
        int minionsToSummon = Math.min(players.size() + 3, 10);

        new BukkitRunnable() {
            int summoned = 0;
            @Override
            public void run() {
                if (summoned >= minionsToSummon || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                Location spawnLoc = altarLocation.clone().add(
                        (random.nextDouble() - 0.5) * 25,
                        0,
                        (random.nextDouble() - 0.5) * 25
                );
                spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

                // Types de sbires adaptatifs
                String minionType;
                if (players.size() >= 4) {
                    // Plus de joueurs = sbires plus forts
                    minionType = switch (summoned % 4) {
                        case 0 -> "necromancer_dark";
                        case 1 -> "dragon_fire";
                        case 2 -> "enderman_shadow";
                        default -> "witch_cursed";
                    };
                } else {
                    minionType = switch (summoned % 3) {
                        case 0 -> "enderman_shadow";
                        case 1 -> "witch_cursed";
                        default -> "golem_stone";
                    };
                }

                LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);
                if (minion != null) {
                    summonedUndead.add(minion);

                    // Bonus selon le nombre de joueurs
                    if (players.size() >= 3) {
                        minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                        minion.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
                    }

                    spawnLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc, 50, 1, 2, 1, 0.1);
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
                    spawnLoc.getWorld().strikeLightningEffect(spawnLoc);
                }

                summoned++;
            }
        }.runTaskTimer(plugin, 20L, 25L); // Délai initial puis toutes les 1.25s
    }

    private void necroticApocalypseMultiplayer(List<Player> players) {
        isChanneling = true;
        lastApocalypse = System.currentTimeMillis();

        Bukkit.broadcastMessage("§5§l§k⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡");
        Bukkit.broadcastMessage("§d§l     APOCALYPSE NÉCROTIQUE MULTIDIMENSIONNELLE !");
        Bukkit.broadcastMessage("§5§l§k⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 3.0f, 0.3f);

        new BukkitRunnable() {
            int apocalypseWave = 0;
            @Override
            public void run() {
                if (apocalypseWave >= 10 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Phase de destruction progressive
                for (Player player : players) {
                    if (player.isDead()) continue;

                    // Explosions multiples autour de chaque joueur
                    int explosionCount = 2 + (apocalypseWave / 2);
                    for (int i = 0; i < explosionCount; i++) {
                        Location explosionLoc = player.getLocation().add(
                                (random.nextDouble() - 0.5) * 15,
                                random.nextDouble() * 8,
                                (random.nextDouble() - 0.5) * 15
                        );

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                createApocalypticExplosion(explosionLoc);
                            }
                        }.runTaskLater(plugin, i * 5L);
                    }
                }

                // Impact central rotatif
                if (apocalypseWave % 2 == 0) {
                    createRotatingApocalypse(entity.getLocation(), apocalypseWave);
                }

                apocalypseWave++;
            }
        }.runTaskTimer(plugin, 0L, 18L); // Toutes les 0.9s
    }

    private void finalExplosionSequenceMultiplayer(List<Player> players) {
        isChanneling = true;

        Bukkit.broadcastMessage("§4§l⚠⚠⚠ L'ARCHLICHE ENTRE EN PHASE FINALE ! ⚠⚠⚠");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_HURT, 3.0f, 0.1f);

        // Countdown adaptatif selon le nombre de joueurs
        int countdownTime = Math.max(5, 12 - players.size());

        new BukkitRunnable() {
            int countdown = countdownTime;
            @Override
            public void run() {
                if (entity.isDead() || countdown <= 0) {
                    // EXPLOSIONS FINALES MULTIPLES
                    executeMultiPlayerFinale(players);
                    cancel();
                    return;
                }

                Bukkit.broadcastMessage("§c§l" + countdown + "... §5FUYEZ !");
                entity.getWorld().playSound(entity.getLocation(), Sound.UI_BUTTON_CLICK, 3.0f, 2.0f);

                // Explosions préludes autour des joueurs
                for (Player player : players) {
                    if (!player.isDead()) {
                        Location preLoc = player.getLocation().add(
                                (random.nextDouble() - 0.5) * 8,
                                random.nextDouble() * 4,
                                (random.nextDouble() - 0.5) * 8
                        );
                        createNecroticExplosion(preLoc);
                    }
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void executeMultiPlayerFinale(List<Player> players) {
        Location bossLoc = entity.getLocation();

        // Explosion centrale massive
        bossLoc.getWorld().createExplosion(bossLoc, 10.0f, false, false);

        // Explosions individuelles pour chaque joueur
        for (Player player : players) {
            if (player.isDead()) continue;

            for (int i = 0; i < 5; i++) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Location explosionLoc = player.getLocation().add(
                                (random.nextDouble() - 0.5) * 12,
                                random.nextDouble() * 6,
                                (random.nextDouble() - 0.5) * 12
                        );
                        createApocalypticExplosion(explosionLoc);
                    }
                }.runTaskLater(plugin, i * 10L);
            }
        }

        // Explosion finale retardée
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche Nécrosis a été vaincu !");
                for (int i = 0; i < 20; i++) {
                    Location randomLoc = bossLoc.clone().add(
                            (random.nextDouble() - 0.5) * 40,
                            random.nextDouble() * 15,
                            (random.nextDouble() - 0.5) * 40
                    );
                    createApocalypticExplosion(randomLoc);
                }
            }
        }.runTaskLater(plugin, 100L);
    }

    // === MÉTHODES UTILITAIRES AMÉLIORÉES ===

    private void fireTrackingSkull(Player target, boolean charged) {
        WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
        skull.setDirection(target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize());
        skull.setShooter(entity);
        skull.setCharged(charged);

        if (activePlayerCount > 2) {
            // Têtes à tête chercheuse pour plus de joueurs
            new BukkitRunnable() {
                int lifetime = 0;
                @Override
                public void run() {
                    if (skull.isDead() || lifetime > 80 || target.isDead()) {
                        cancel();
                        return;
                    }

                    Vector toTarget = target.getEyeLocation().subtract(skull.getLocation()).toVector().normalize();
                    skull.setVelocity(toTarget.multiply(charged ? 2.0 : 1.8));

                    skull.getWorld().spawnParticle(charged ? Particle.SOUL_FIRE_FLAME : Particle.SOUL,
                            skull.getLocation(), 3, 0.1, 0.1, 0.1, 0);
                    lifetime++;
                }
            }.runTaskTimer(plugin, 10L, 2L);
        }
    }

    private void applyCurseToPlayer(Player player) {
        // Malédiction adaptative selon le nombre de malédictions existantes
        int curseLevel = playerCurses.getOrDefault(player, 0);

        PotionEffectType[] curses = {
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.SLOWNESS,
                PotionEffectType.WEAKNESS,
                PotionEffectType.BLINDNESS
        };

        PotionEffectType curse = curses[Math.min(curseLevel, curses.length - 1)];
        int amplifier = Math.min(curseLevel / 2, 3);

        player.addPotionEffect(new PotionEffect(curse, 200 + curseLevel * 20, amplifier));
        addCurse(player);
        increaseThreat(player, 8);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VEX_CHARGE, 1.0f, 0.6f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getEyeLocation(), 15, 0.5, 0.5, 0.5);
    }

    private void createCurseVortex(Location location) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 8; i++) {
                    double angle = (ticks * 15 + i * 45) % 360;
                    double radius = 2 + Math.sin(ticks * 0.2) * 0.5;

                    Location particleLoc = location.clone().add(
                            Math.cos(Math.toRadians(angle)) * radius,
                            Math.sin(ticks * 0.1) * 1.5 + 1,
                            Math.sin(Math.toRadians(angle)) * radius
                    );

                    location.getWorld().spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawTargetingBeam(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);

        for (double i = 0; i < distance; i += 1.0) {
            Location beamLoc = from.clone().add(direction.clone().multiply(i));
            from.getWorld().spawnParticle(Particle.END_ROD, beamLoc, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    private void createApocalypticExplosion(Location location) {
        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 40, 3, 3, 3, 0.2);
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 10, 2, 2, 2, 0);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);

        for (Player p : getNearbyPlayersAt(location, 6)) {
            p.damage(damage * 0.8, entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 2));
            increaseThreat(p, 12);
        }
    }

    private void createRotatingApocalypse(Location center, int wave) {
        new BukkitRunnable() {
            int angle = wave * 45;
            int rotations = 0;
            @Override
            public void run() {
                if (rotations >= 3) {
                    cancel();
                    return;
                }

                for (int r = 5; r <= 20; r += 5) {
                    double radians = Math.toRadians(angle);
                    Location explosionLoc = center.clone().add(
                            Math.cos(radians) * r,
                            0,
                            Math.sin(radians) * r
                    );

                    createApocalypticExplosion(explosionLoc);
                }

                angle += 72; // Rotation de 72 degrés
                if (angle >= 360) {
                    angle = 0;
                    rotations++;
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // === MÉTHODES INCHANGÉES (conservées du code original) ===

    private void startAwakeningSequence() {
        currentPhase = BossPhase.AWAKENING;
        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 10));

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§5§l§k=====================================");
        Bukkit.broadcastMessage("§d§l        ☠ BOSS ULTIME ! ☠");
        Bukkit.broadcastMessage("§5§l       ARCHLICHE NÉCROSIS");
        Bukkit.broadcastMessage("§d§l      s'éveille des ténèbres...");
        Bukkit.broadcastMessage("§5§l§k=====================================");
        Bukkit.broadcastMessage("");

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 300 || entity.isDead()) {
                    currentPhase = BossPhase.PHASE_1;
                    Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche est maintenant éveillé ! Le combat commence !");
                    cancel();
                    return;
                }

                Location loc = entity.getLocation();

                if (ticks % 10 == 0) {
                    loc.getWorld().spawnParticle(Particle.PORTAL, loc, 100, 5, 5, 5, 1);
                    loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.5f);
                }

                if (ticks % 60 == 0) {
                    Bukkit.broadcastMessage("§5§l[ÉVEIL] §d" + (15 - ticks/20) + " secondes...");
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updatePhase() {
        if (currentPhase == BossPhase.AWAKENING) return;

        double healthPercent = entity.getHealth() / maxHealth;

        BossPhase newPhase;
        if (healthPercent > 0.75) {
            newPhase = BossPhase.PHASE_1;
        } else if (healthPercent > 0.40) {
            newPhase = BossPhase.PHASE_2;
        } else if (healthPercent > 0.15) {
            newPhase = BossPhase.PHASE_3;
        } else {
            newPhase = BossPhase.DEATH_THROES;
        }

        if (newPhase != currentPhase && newPhase != BossPhase.AWAKENING) {
            currentPhase = newPhase;
            announcePhaseChange();
        }
    }

    private void announcePhaseChange() {
        String message = switch (currentPhase) {
            case PHASE_1 -> "§5L'Archliche commence à utiliser sa vraie puissance...";
            case PHASE_2 -> "§d§lL'Archliche invoque ses servants !";
            case PHASE_3 -> "§4§l⚠ L'ARCHLICHE ENTRE EN FUREUR ! ⚠";
            case DEATH_THROES -> "§c§l💀 L'ARCHLICHE AGONISE ET DEVIENT DÉSESPÉRÉ ! 💀";
            default -> "";
        };

        Bukkit.broadcastMessage("§5§l[BOSS] §r" + message);
    }

    private void maintainFlightAltitude(int targetHeight) {
        Location current = entity.getLocation();
        Location ground = current.getWorld().getHighestBlockAt(current).getLocation();

        if (current.getY() - ground.getY() < targetHeight) {
            Vector upward = new Vector(0, 0.5, 0);
            entity.setVelocity(entity.getVelocity().add(upward));
        }
    }

    private void teleportToArena() {
        if (altarLocation != null) {
            Location tpLoc = altarLocation.clone().add(0, 25, 0);
            entity.teleport(tpLoc);

            entity.getWorld().spawnParticle(Particle.PORTAL, tpLoc, 50, 2, 2, 2, 1);
            entity.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);
        }
    }

    private void createNecroticExplosion(Location location) {
        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 30, 2, 2, 2, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        for (Player p : getNearbyPlayersAt(location, 4)) {
            p.damage(damage * 0.7, entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            increaseThreat(p, 6);
        }
    }

    private void drawSoulLink(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);

        for (double i = 0; i < distance; i += 0.5) {
            Location particleLoc = from.clone().add(direction.clone().multiply(i));
            from.getWorld().spawnParticle(Particle.SOUL, particleLoc, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    private void createSoulOrb(Location location) {
        ArmorStand orb = location.getWorld().spawn(location, ArmorStand.class);
        orb.setVisible(false);
        orb.setGravity(false);
        orb.setCustomName("§5Orbe d'Âme");
        orb.setCustomNameVisible(true);

        soulOrbs.add(orb);

        new BukkitRunnable() {
            int lifetime = 200;
            @Override
            public void run() {
                if (lifetime <= 0 || orb.isDead()) {
                    soulOrbs.remove(orb);
                    if (!orb.isDead()) orb.remove();
                    cancel();
                    return;
                }

                orb.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, orb.getLocation(), 3, 0.2, 0.2, 0.2, 0.01);

                Vector float_movement = new Vector(
                        Math.sin(lifetime * 0.1) * 0.1,
                        Math.cos(lifetime * 0.05) * 0.05,
                        Math.cos(lifetime * 0.1) * 0.1
                );
                orb.setVelocity(float_movement);

                lifetime--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Player findRandomTarget(List<Player> players) {
        if (players.isEmpty()) return null;
        return players.get(random.nextInt(players.size()));
    }

    private void cleanupMinions() {
        summonedUndead.removeIf(LivingEntity::isDead);
        soulOrbs.removeIf(LivingEntity::isDead);
    }

    private void activateWitherShield() {
        isShielded = true;
        lastWitherShield = System.currentTimeMillis();

        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 3));
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 2.0f, 0.5f);

        Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche s'entoure d'un bouclier nécrotique !");

        new BukkitRunnable() {
            int duration = 200;
            @Override
            public void run() {
                if (duration <= 0 || entity.isDead()) {
                    isShielded = false;
                    cancel();
                    return;
                }

                Location loc = entity.getLocation();
                for (int i = 0; i < 8; i++) {
                    double angle = (duration * 5 + i * 45) % 360;
                    double radius = 3 + Math.sin(duration * 0.1) * 0.5;

                    Location particleLoc = loc.clone().add(
                            Math.cos(Math.toRadians(angle)) * radius,
                            Math.sin(duration * 0.05) * 2 + 2,
                            Math.sin(Math.toRadians(angle)) * radius
                    );

                    loc.getWorld().spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                }

                duration -= 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // Méthodes simplifiées pour le single player (conservées pour compatibilité)
    private void skullBarrageSingle(Player target) {
        fireTrackingSkull(target, false);
    }

    private void skullBarrageMulti(List<Player> targets) {
        for (Player target : targets) {
            if (!target.isDead()) {
                fireTrackingSkull(target, false);
            }
        }
    }

    private void soulDrain(Player target) {
        drawSoulLink(entity.getEyeLocation(), target.getEyeLocation());
        target.damage(2.0, entity);
        entity.setHealth(Math.min(entity.getHealth() + 1.0, maxHealth));
        increaseThreat(target, 5);
    }

    private void soulDrainArea(List<Player> targets) {
        for (Player target : targets) {
            if (!target.isDead()) {
                soulDrain(target);
            }
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