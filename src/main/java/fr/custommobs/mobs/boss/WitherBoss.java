package fr.custommobs.mobs.boss;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class WitherBoss extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> summonedUndead = new ArrayList<>();
    private final List<LivingEntity> soulOrbs = new ArrayList<>();

    // --- ÉTAT DU BOSS ---
    private BossPhase currentPhase = BossPhase.AWAKENING;
    private boolean isShielded = false;
    private boolean isChanneling = false;
    private int necroticStacks = 0;
    private Location altarLocation;

    // --- COOLDOWNS ---
    private long lastSkullBarrage = 0;
    private final long SKULL_BARRAGE_COOLDOWN = 15000; // 15s

    private long lastSoulDrain = 0;
    private final long SOUL_DRAIN_COOLDOWN = 20000; // 20s

    private long lastNecromancy = 0;
    private final long NECROMANCY_COOLDOWN = 30000; // 30s

    private long lastWitherShield = 0;
    private final long WITHER_SHIELD_COOLDOWN = 45000; // 45s

    private long lastApocalypse = 0;
    private final long APOCALYPSE_COOLDOWN = 60000; // 60s

    private enum BossPhase {
        AWAKENING,  // 0-15s : Invulnérable, setup arena
        PHASE_1,    // 100-75% HP : Attaques aériennes
        PHASE_2,    // 75-40% HP : Invocations + Shield
        PHASE_3,    // 40-15% HP : Attaques dévastatrices
        DEATH_THROES // 15-0% HP : Explosions finales
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

        // Immunités de boss
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

        updatePhase();
        cleanupMinions();

        long currentTime = System.currentTimeMillis();
        double distance = entity.getLocation().distance(target.getLocation());
        List<Player> nearbyPlayers = getNearbyPlayers(30);

        // Ciblage intelligent - vise le joueur le plus proche mais change parfois
        if (Math.random() < 0.1) { // 10% de chance de changer de cible
            Player newTarget = findRandomTarget(nearbyPlayers);
            if (newTarget != null) target = newTarget;
        }

        ((Wither) entity).setTarget(target);

        // === IA BASÉE SUR LES PHASES ===
        switch (currentPhase) {
            case AWAKENING:
                // Invulnérable pendant l'éveil
                break;
            case PHASE_1:
                handlePhase1(target, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_2:
                handlePhase2(target, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_3:
                handlePhase3(target, distance, currentTime, nearbyPlayers);
                break;
            case DEATH_THROES:
                handleDeathThroes(target, distance, currentTime, nearbyPlayers);
                break;
        }
    }

    private void handlePhase1(Player target, double distance, long currentTime, List<Player> players) {
        // Phase 1 : Attaques aériennes classiques
        if (currentTime - lastSkullBarrage > SKULL_BARRAGE_COOLDOWN) {
            if (players.size() > 1) {
                skullBarrageMulti(players);
            } else {
                skullBarrageSingle(target);
            }
        } else if (distance > 15 && currentTime - lastSoulDrain > SOUL_DRAIN_COOLDOWN) {
            soulDrain(target);
        } else {
            // Attaque de base : têtes explosives
            attack(target);
        }

        // Maintient une altitude élevée
        maintainFlightAltitude(15);
    }

    private void handlePhase2(Player target, double distance, long currentTime, List<Player> players) {
        // Phase 2 : Invocations et protection
        if (!isShielded && currentTime - lastWitherShield > WITHER_SHIELD_COOLDOWN) {
            activateWitherShield();
        }

        cleanupMinions();
        if (summonedUndead.size() < 4 && currentTime - lastNecromancy > NECROMANCY_COOLDOWN) {
            massNecromancy();
        } else if (currentTime - lastSkullBarrage > (SKULL_BARRAGE_COOLDOWN * 0.7)) {
            skullBarrageArea(players);
        } else if (distance > 20) {
            teleportToArena();
        } else {
            attack(target);
        }

        maintainFlightAltitude(20);
    }

    private void handlePhase3(Player target, double distance, long currentTime, List<Player> players) {
        // Phase 3 : Attaques dévastatrices
        if (currentTime - lastApocalypse > APOCALYPSE_COOLDOWN && players.size() > 0) {
            necroticApocalypse(players);
        } else if (currentTime - lastSoulDrain > (SOUL_DRAIN_COOLDOWN * 0.6)) {
            soulDrainArea(players);
        } else if (currentTime - lastSkullBarrage > (SKULL_BARRAGE_COOLDOWN * 0.5)) {
            skullBarrageHoming(target);
        } else {
            // Attaque renforcée
            attack(target);
        }

        maintainFlightAltitude(25);
    }

    private void handleDeathThroes(Player target, double distance, long currentTime, List<Player> players) {
        // Phase finale : Explosions désespérées
        if (!isChanneling) {
            finalExplosionSequence();
        }
    }

    @Override
    public void attack(Player target) {
        // Attaque de base : Tête explosive avec effet Wither
        if (entity instanceof Wither wither) {
            // Simule le tir de tête explosive
            WitherSkull skull = wither.launchProjectile(WitherSkull.class);
            skull.setDirection(target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize());
            skull.setCharged(currentPhase == BossPhase.PHASE_3 || currentPhase == BossPhase.DEATH_THROES);
            skull.setShooter(entity);

            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.8f);

            // Ajoute un effet nécrotique
            necroticStacks++;
            if (necroticStacks >= 5) {
                createNecroticExplosion(target.getLocation());
                necroticStacks = 0;
            }
        }
    }

    /**
     * Barrage de têtes sur un seul joueur
     */
    private void skullBarrageSingle(Player target) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);

        new BukkitRunnable() {
            int skulls = 0;
            @Override
            public void run() {
                if (skulls >= 8 || entity.isDead() || target.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Prédiction de mouvement
                Vector prediction = target.getVelocity().multiply(20);
                Location targetLoc = target.getLocation().add(prediction);

                WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
                skull.setDirection(targetLoc.subtract(entity.getEyeLocation()).toVector().normalize());
                skull.setShooter(entity);
                skull.setCharged(currentPhase == BossPhase.PHASE_3);

                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);
                skulls++;
            }
        }.runTaskTimer(plugin, 0L, 8L); // Toutes les 0.4s
    }

    /**
     * Barrage de têtes sur plusieurs joueurs
     */
    private void skullBarrageMulti(List<Player> targets) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.3f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 4 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                for (Player target : targets) {
                    if (target.isDead()) continue;

                    WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
                    Vector direction = target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize();
                    skull.setDirection(direction);
                    skull.setShooter(entity);
                    skull.setCharged(false);
                }

                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
                wave++;
            }
        }.runTaskTimer(plugin, 0L, 15L); // Toutes les 0.75s
    }

    /**
     * Barrage de zone sur une area
     */
    private void skullBarrageArea(List<Player> targets) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        // Calcule le centre du groupe
        Vector center = new Vector();
        for (Player p : targets) {
            center.add(p.getLocation().toVector());
        }
        Location targetArea = center.multiply(1.0 / targets.size()).toLocation(entity.getWorld());

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);

        new BukkitRunnable() {
            int skulls = 0;
            @Override
            public void run() {
                if (skulls >= 15 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Cible aléatoirement autour de la zone
                Location randomTarget = targetArea.clone().add(
                        (random.nextDouble() - 0.5) * 16,
                        random.nextDouble() * 5,
                        (random.nextDouble() - 0.5) * 16
                );

                WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
                skull.setDirection(randomTarget.subtract(entity.getEyeLocation()).toVector().normalize());
                skull.setShooter(entity);
                skull.setCharged(Math.random() < 0.3); // 30% de têtes bleues

                skulls++;
            }
        }.runTaskTimer(plugin, 0L, 4L); // Toutes les 0.2s
    }

    /**
     * Têtes à tête chercheuse (Phase 3)
     */
    private void skullBarrageHoming(Player target) {
        isChanneling = true;
        lastSkullBarrage = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_HURT, 2.0f, 0.3f);

        new BukkitRunnable() {
            int skulls = 0;
            @Override
            public void run() {
                if (skulls >= 6 || entity.isDead() || target.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Crée des têtes qui suivent le joueur
                WitherSkull skull = entity.getWorld().spawn(entity.getEyeLocation(), WitherSkull.class);
                skull.setDirection(target.getEyeLocation().subtract(entity.getEyeLocation()).toVector().normalize());
                skull.setShooter(entity);
                skull.setCharged(true);

                // Tâche pour faire suivre la tête
                new BukkitRunnable() {
                    int lifetime = 0;
                    @Override
                    public void run() {
                        if (skull.isDead() || lifetime > 100 || target.isDead()) {
                            cancel();
                            return;
                        }

                        Vector toTarget = target.getEyeLocation().subtract(skull.getLocation()).toVector().normalize();
                        skull.setVelocity(toTarget.multiply(1.5));

                        skull.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, skull.getLocation(), 2, 0.1, 0.1, 0.1, 0);
                        lifetime++;
                    }
                }.runTaskTimer(plugin, 20L, 2L);

                skulls++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Toutes les secondes
    }

    /**
     * Draine les âmes des joueurs proches
     */
    private void soulDrain(Player target) {
        isChanneling = true;
        lastSoulDrain = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 2.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 80 || entity.isDead() || target.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                // Dessine les liens d'âme
                drawSoulLink(entity.getEyeLocation(), target.getEyeLocation());

                // Draine la vie et régénère le boss
                target.damage(2.0, entity);
                entity.setHealth(Math.min(entity.getHealth() + 1.0, maxHealth));

                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));

                // Chance de créer une orbe d'âme
                if (Math.random() < 0.1) {
                    createSoulOrb(target.getLocation());
                }

                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Drain d'âme de zone (Phase 3)
     */
    private void soulDrainArea(List<Player> targets) {
        isChanneling = true;
        lastSoulDrain = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 3.0f, 0.3f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 60 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                for (Player target : targets) {
                    if (target.isDead()) continue;

                    drawSoulLink(entity.getEyeLocation(), target.getEyeLocation());
                    target.damage(1.5, entity);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1));
                }

                entity.setHealth(Math.min(entity.getHealth() + targets.size(), maxHealth));
                ticks += 3;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * Invoque une armée de morts-vivants
     */
    private void massNecromancy() {
        isChanneling = true;
        lastNecromancy = System.currentTimeMillis();

        Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche invoque ses servants...");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.7f);

        new BukkitRunnable() {
            int summoned = 0;
            @Override
            public void run() {
                if (summoned >= 6 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                Location spawnLoc = altarLocation.clone().add(
                        (random.nextDouble() - 0.5) * 20,
                        0,
                        (random.nextDouble() - 0.5) * 20
                );
                spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

                // Varie les types de servants
                String minionType;
                if (summoned < 2) {
                    minionType = "necromancer_dark"; // Mini-boss
                } else if (summoned < 4) {
                    minionType = "enderman_shadow";
                } else {
                    minionType = "witch_cursed";
                }

                LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);
                if (minion != null) {
                    summonedUndead.add(minion);

                    // Effets d'invocation dramatiques
                    spawnLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc, 50, 1, 2, 1, 0.1);
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
                    spawnLoc.getWorld().strikeLightningEffect(spawnLoc);
                }

                summoned++;
            }
        }.runTaskTimer(plugin, 20L, 30L); // Délai initial puis toutes les 1.5s
    }

    /**
     * Active un bouclier de Wither
     */
    private void activateWitherShield() {
        isShielded = true;
        lastWitherShield = System.currentTimeMillis();

        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 3)); // 10s de protection
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 2.0f, 0.5f);

        Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche s'entoure d'un bouclier nécrotique !");

        // Effets visuels du bouclier
        new BukkitRunnable() {
            int duration = 200; // 10s
            @Override
            public void run() {
                if (duration <= 0 || entity.isDead()) {
                    isShielded = false;
                    cancel();
                    return;
                }

                // Particules de bouclier
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

    /**
     * Apocalypse nécrotique finale
     */
    private void necroticApocalypse(List<Player> targets) {
        isChanneling = true;
        lastApocalypse = System.currentTimeMillis();

        Bukkit.broadcastMessage("§5§l§k⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡");
        Bukkit.broadcastMessage("§d§l        APOCALYPSE NÉCROTIQUE !");
        Bukkit.broadcastMessage("§5§l§k⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡⚡");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 3.0f, 0.3f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 8 || entity.isDead()) {
                    isChanneling = false;
                    cancel();
                    return;
                }

                for (Player target : targets) {
                    if (target.isDead()) continue;

                    // Explosions aléatoires autour des joueurs
                    for (int i = 0; i < 3; i++) {
                        Location explosionLoc = target.getLocation().add(
                                (random.nextDouble() - 0.5) * 12,
                                random.nextDouble() * 5,
                                (random.nextDouble() - 0.5) * 12
                        );

                        createNecroticExplosion(explosionLoc);
                    }
                }

                wave++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Toutes les secondes
    }

    /**
     * Séquence d'explosion finale
     */
    private void finalExplosionSequence() {
        isChanneling = true;

        Bukkit.broadcastMessage("§4§l⚠ L'ARCHLICHE SE PRÉPARE À EXPLOSER ! ⚠");
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_HURT, 3.0f, 0.1f);

        new BukkitRunnable() {
            int countdown = 10;
            @Override
            public void run() {
                if (entity.isDead() || countdown <= 0) {
                    // EXPLOSION FINALE MASSIVE
                    Location loc = entity.getLocation();
                    loc.getWorld().createExplosion(loc, 8.0f, false, false);

                    for (int i = 0; i < 5; i++) {
                        Location randomLoc = loc.clone().add(
                                (random.nextDouble() - 0.5) * 30,
                                random.nextDouble() * 10,
                                (random.nextDouble() - 0.5) * 30
                        );
                        createNecroticExplosion(randomLoc);
                    }

                    Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche Nécrosis a été vaincu !");
                    cancel();
                    return;
                }

                Bukkit.broadcastMessage("§c§l" + countdown + "...");
                entity.getWorld().playSound(entity.getLocation(), Sound.UI_BUTTON_CLICK, 2.0f, 2.0f);

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // === MÉTHODES UTILITAIRES ===

    private void startAwakeningSequence() {
        currentPhase = BossPhase.AWAKENING;
        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 10)); // 15s d'invulnérabilité

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§5§l§k=====================================");
        Bukkit.broadcastMessage("§d§l        ☠ BOSS ULTIME ! ☠");
        Bukkit.broadcastMessage("§5§l       ARCHLICHE NÉCROSIS");
        Bukkit.broadcastMessage("§d§l      s'éveille des ténèbres...");
        Bukkit.broadcastMessage("§5§l§k=====================================");
        Bukkit.broadcastMessage("");

        // Séquence d'éveil dramatique
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 300 || entity.isDead()) { // 15 secondes
                    currentPhase = BossPhase.PHASE_1;
                    Bukkit.broadcastMessage("§d§l[BOSS] §5L'Archliche est maintenant éveillé ! Le combat commence !");
                    cancel();
                    return;
                }

                Location loc = entity.getLocation();

                // Effets visuels d'éveil
                if (ticks % 10 == 0) {
                    loc.getWorld().spawnParticle(Particle.PORTAL, loc, 100, 5, 5, 5, 1);
                    loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.5f);
                }

                if (ticks % 60 == 0) { // Toutes les 3 secondes
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
            Location tpLoc = altarLocation.clone().add(0, 20, 0);
            entity.teleport(tpLoc);

            entity.getWorld().spawnParticle(Particle.PORTAL, tpLoc, 50, 2, 2, 2, 1);
            entity.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);
        }
    }

    private void createNecroticExplosion(Location location) {
        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 30, 2, 2, 2, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        // Dégâts aux joueurs proches
        for (Player p : getNearbyPlayersAt(location, 4)) {
            p.damage(damage * 0.7, entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
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
        ArmorStand orb = location.getWorld().spawn(location.add(0, 2, 0), ArmorStand.class);
        orb.setVisible(false);
        orb.setGravity(false);
        orb.setCustomName("§5Orbe d'Âme");
        orb.setCustomNameVisible(true);

        soulOrbs.add(orb);

        // L'orbe flotte et disparaît après 10s
        new BukkitRunnable() {
            int lifetime = 200; // 10s
            @Override
            public void run() {
                if (lifetime <= 0 || orb.isDead()) {
                    soulOrbs.remove(orb);
                    if (!orb.isDead()) orb.remove();
                    cancel();
                    return;
                }

                // Effets visuels
                orb.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, orb.getLocation(), 3, 0.2, 0.2, 0.2, 0.01);

                // Mouvement flottant
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