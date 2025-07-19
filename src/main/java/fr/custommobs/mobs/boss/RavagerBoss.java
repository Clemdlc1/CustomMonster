package fr.custommobs.mobs.boss;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class RavagerBoss extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> summonedMinions = new ArrayList<>();

    // --- PHASES DE COMBAT ---
    private BossPhase currentPhase = BossPhase.PHASE_1;
    private boolean isEnraged = false;
    private boolean isPerformingSpecialAttack = false;

    // --- COOLDOWNS ---
    private long lastDevastatingCharge = 0;
    private final long DEVASTATING_CHARGE_COOLDOWN = 12000; // 12s

    private long lastEarthquake = 0;
    private final long EARTHQUAKE_COOLDOWN = 18000; // 18s

    private long lastSummonRaiders = 0;
    private final long SUMMON_RAIDERS_COOLDOWN = 25000; // 25s

    private long lastRage = 0;
    private final long RAGE_COOLDOWN = 45000; // 45s

    private long lastGroundPound = 0;
    private final long GROUND_POUND_COOLDOWN = 8000; // 8s

    private enum BossPhase {
        PHASE_1, // 100-70% HP - Attaques basiques
        PHASE_2, // 70-40% HP - Plus agressif, summon minions
        PHASE_3  // 40-0% HP - Rage mode, attaques dévastatrices
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

        updatePhase();

        long currentTime = System.currentTimeMillis();
        double distance = entity.getLocation().distance(target.getLocation());
        List<Player> nearbyPlayers = getNearbyPlayers(20);

        // === IA MULTIJOUEUR AMÉLIORÉE ===
        // Sélection intelligente de cible
        if (Math.random() < 0.2 && nearbyPlayers.size() > 1) { // 20% de chance de changer
            target = selectPriorityTarget(nearbyPlayers);
        }

        ((Ravager) entity).setTarget(target);

        // === IA BASÉE SUR LES PHASES ===
        switch (currentPhase) {
            case PHASE_1:
                handlePhase1(target, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_2:
                handlePhase2(target, distance, currentTime, nearbyPlayers);
                break;
            case PHASE_3:
                handlePhase3(target, distance, currentTime, nearbyPlayers);
                break;
        }
    }

    private void handlePhase1(Player target, double distance, long currentTime, List<Player> players) {
        // Phase 1 : Combat basique mais puissant
        if (distance < 4 && currentTime - lastGroundPound > GROUND_POUND_COOLDOWN) {
            groundPound();
        } else if (distance > 6 && distance < 20 && currentTime - lastDevastatingCharge > DEVASTATING_CHARGE_COOLDOWN) {
            devastatingCharge(target);
        } else if (distance <= 5) {
            attack(target);
        }
    }

    private void handlePhase2(Player target, double distance, long currentTime, List<Player> players) {
        // Phase 2 : Plus agressif, summon des renforts
        cleanUpMinions();

        if (summonedMinions.size() < 3 && currentTime - lastSummonRaiders > SUMMON_RAIDERS_COOLDOWN) {
            summonRaiders();
        } else if (players.size() > 1 && currentTime - lastEarthquake > EARTHQUAKE_COOLDOWN) {
            earthquakeSlam(players);
        } else if (distance > 8 && currentTime - lastDevastatingCharge > (DEVASTATING_CHARGE_COOLDOWN * 0.7)) {
            devastatingCharge(target);
        } else if (distance <= 6) {
            attack(target);
        }
    }

    private void handlePhase3(Player target, double distance, long currentTime, List<Player> players) {
        // Phase 3 : Rage mode - Attaques dévastatrices
        if (!isEnraged && currentTime - lastRage > RAGE_COOLDOWN) {
            enterRageMode();
        }

        if (players.size() > 1 && currentTime - lastEarthquake > (EARTHQUAKE_COOLDOWN * 0.6)) {
            earthquakeSlam(players);
        } else if (distance > 5 && currentTime - lastDevastatingCharge > (DEVASTATING_CHARGE_COOLDOWN * 0.5)) {
            devastatingCharge(target);
        } else if (distance <= 8 && currentTime - lastGroundPound > (GROUND_POUND_COOLDOWN * 0.6)) {
            groundPound();
        } else {
            attack(target);
        }
    }

    @Override
    public void attack(Player target) {
        // Attaque de base améliorée selon la phase
        double damageMultiplier = isEnraged ? 1.5 : 1.0;

        target.damage(damage * damageMultiplier, entity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

        if (currentPhase == BossPhase.PHASE_3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
        }

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 2.0f, 0.8f);
        entity.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 20, 1, 1, 1, 0.5);
    }

    /**
     * Charge dévastatrice qui détruit les blocs sur son passage
     */
    private void devastatingCharge(Player target) {
        isPerformingSpecialAttack = true;
        lastDevastatingCharge = System.currentTimeMillis();

        Location start = entity.getLocation();
        Vector direction = target.getLocation().subtract(start).toVector().normalize();
        double maxDistance = Math.min(20, start.distance(target.getLocation()) + 5); // Distance limitée

        // Son d'avertissement
        entity.getWorld().playSound(start, Sound.ENTITY_RAVAGER_ROAR, 3.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            double traveledDistance = 0;
            double currentSpeed = 1.5; // Vitesse initiale réduite

            @Override
            public void run() {
                if (ticks > 80 || entity.isDead() || traveledDistance >= maxDistance) { // 4 secondes max
                    isPerformingSpecialAttack = false;
                    entity.setVelocity(new Vector(0, 0, 0)); // Arrête le mouvement
                    cancel();
                    return;
                }

                // Décélération progressive après 2 secondes
                if (ticks > 40) {
                    currentSpeed = Math.max(0.3, currentSpeed * 0.95);
                }

                Vector velocity = direction.clone().multiply(currentSpeed);
                entity.setVelocity(velocity);
                traveledDistance += currentSpeed;

                // Effets visuels
                Location loc = entity.getLocation();
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 15, 1, 0.5, 1, 0.1);
                loc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 20, 1, 0.1, 1, 0.5, Material.STONE.createBlockData());

                // Détruit les blocs faibles sur le passage (modérément)
                if (ticks % 8 == 0) {
                    destroyBlocksInPath(loc, 1); // Rayon réduit
                }

                // Dégâts aux joueurs touchés
                for (Player p : getNearbyPlayersAt(loc, 3)) {
                    p.damage(damage * 1.3, entity); // Dégâts réduits
                    Vector knockback = p.getLocation().subtract(loc).toVector().normalize().multiply(1.8).setY(0.6);
                    p.setVelocity(knockback);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 1L); // Délai de 1s puis toutes les ticks
    }

    /**
     * Frappe le sol créant une onde de choc
     */
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

                // Onde de choc en cercles concentriques
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
        }.runTaskLater(plugin, 15L); // 0.75s de préparation
    }

    /**
     * Séisme dévastateur pour les groupes
     */
    private void earthquakeSlam(List<Player> targets) {
        isPerformingSpecialAttack = true;
        lastEarthquake = System.currentTimeMillis();

        // Calcule le centre du groupe
        Vector center = new Vector();
        for (Player p : targets) {
            center.add(p.getLocation().toVector());
        }
        Location epicenter = center.multiply(1.0 / targets.size()).toLocation(entity.getWorld());

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3.0f, 0.5f);

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 5 || entity.isDead()) {
                    isPerformingSpecialAttack = false;
                    cancel();
                    return;
                }

                // Crée des fissures qui s'étendent
                double radius = 3 + (wave * 2);
                createEarthquakeFissure(epicenter, radius);

                // Dégâts et effets aux joueurs dans la zone
                for (Player p : getNearbyPlayersAt(epicenter, radius)) {
                    p.damage(damage * 0.8, entity);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));

                    Vector knockup = new Vector(0, 0.6, 0);
                    p.setVelocity(p.getVelocity().add(knockup));
                }

                wave++;
            }
        }.runTaskTimer(plugin, 30L, 15L); // Délai initial de 1.5s puis toutes les 0.75s
    }

    /**
     * Invoque des raiders pour l'assister
     */
    private void summonRaiders() {
        isPerformingSpecialAttack = true;
        lastSummonRaiders = System.currentTimeMillis();

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_CELEBRATE, 2.0f, 1.0f);

        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location spawnLoc = entity.getLocation().add(
                            (random.nextDouble() - 0.5) * 8,
                            0,
                            (random.nextDouble() - 0.5) * 8
                    );
                    spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);

                    // Alterne entre différents types de minions
                    String minionType = random.nextBoolean() ? "zombie_warrior" : "skeleton_archer";
                    LivingEntity minion = plugin.getMobManager().spawnCustomMob(minionType, spawnLoc);

                    if (minion != null) {
                        summonedMinions.add(minion);
                        spawnLoc.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 30, 1, 2, 1, 0.1);
                        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);
                    }
                }
            }.runTaskLater(plugin, i * 10L);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                isPerformingSpecialAttack = false;
            }
        }.runTaskLater(plugin, 40L);
    }

    /**
     * Entre en mode rage (Phase 3)
     */
    private void enterRageMode() {
        isEnraged = true;
        lastRage = System.currentTimeMillis();

        // Annonce dramatique
        Bukkit.broadcastMessage("§4§l[BOSS] §c" + entity.getCustomName() + " §4ENTRE EN RAGE !");

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.5f);

        // Effets visuels dramatiques
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

        // Boost permanent
        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        // Met à jour le nom
        entity.setCustomName("§4§l§k|||§r §c§lDÉVASTATEUR ENRAGÉ §4§l§k|||§r");
    }

    // === MÉTHODES UTILITAIRES ===

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

    private void createShockwaveRing(Location center, int radius) {
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            Location loc = center.clone().add(
                    Math.cos(angle) * radius,
                    0.5,
                    Math.sin(angle) * radius
            );

            center.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 5, 0.5, 0.5, 0.5, 0.1, Material.STONE.createBlockData());

            // Dégâts aux joueurs touchés
            for (Player p : getNearbyPlayersAt(loc, 2)) {
                p.damage(damage * 0.6, entity);
                Vector knockback = p.getLocation().subtract(center).toVector().normalize().multiply(1.5).setY(0.4);
                p.setVelocity(knockback);
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

                    // Détruit seulement certains blocs fragiles
                    if (type.isBlock() && !type.name().contains("BEDROCK") &&
                            !type.name().contains("OBSIDIAN") &&
                            !type.name().contains("BARRIER") &&
                            type.getHardness() > 0 && type.getHardness() < 5.0f &&
                            Math.random() < 0.6) { // 60% de chance seulement

                        blocksToDestroy.add(block);
                    }
                }
            }
        }

        // Détruit progressivement et restaure après 30 secondes
        for (Block block : blocksToDestroy) {
            Material originalType = block.getType();
            block.breakNaturally();

            // Restaure le bloc après 30 secondes
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType().isAir()) {
                        block.setType(originalType);
                    }
                }
            }.runTaskLater(plugin, 600L); // 30 secondes
        }
    }

    /**
     * Sélectionne la cible prioritaire selon différents critères
     */
    private Player selectPriorityTarget(List<Player> players) {
        if (players.isEmpty()) return null;
        if (players.size() == 1) return players.get(0);

        // Priorité : joueur le plus proche, ou avec le moins de vie, ou qui attaque le boss
        return players.stream()
                .min((p1, p2) -> {
                    double dist1 = entity.getLocation().distanceSquared(p1.getLocation());
                    double dist2 = entity.getLocation().distanceSquared(p2.getLocation());

                    // Priorité aux joueurs proches
                    if (Math.abs(dist1 - dist2) > 100) { // Différence significative
                        return Double.compare(dist1, dist2);
                    }

                    // Sinon, priorité au joueur avec moins de vie
                    return Double.compare(p1.getHealth(), p2.getHealth());
                })
                .orElse(players.get(random.nextInt(players.size())));
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

                // Aura constante
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

        // Son d'apparition dramatique
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