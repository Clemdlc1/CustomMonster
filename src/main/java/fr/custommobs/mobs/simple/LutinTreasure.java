package fr.custommobs.mobs.simple;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class LutinTreasure extends CustomMob {

    private final Random random = new Random();
    private final List<LivingEntity> illusions = new ArrayList<>();
    private final List<LivingEntity> guardians = new ArrayList<>();
    private final Set<UUID> threateningPlayers = new HashSet<>();

    // --- Système de phases ---
    private LutinPhase currentPhase = LutinPhase.PHASE_1;
    private long phaseStartTime;

    // --- État et timing ---
    private long spawnTime;
    private final long LIFETIME = 400000; // 6 minutes 40 secondes
    private long lastMovement = 0;
    private long lastRPMessage = 0;
    private boolean isConstantlyMoving = true;
    private Vector currentDirection;
    private int movementTicks = 0;
    private boolean isInCombat = false;

    // --- Cooldowns adaptatifs ---
    private long lastTeleport = 0;
    private long lastIllusion = 0;
    private long lastSummonGuardians = 0;
    private long lastRepel = 0;
    private long lastTreasureDrop = 0;
    private long lastPanicAbility = 0;

    // --- Messages RP par phase ---
    private final Map<LutinPhase, List<String>> phaseMessages = new HashMap<>();
    private final List<String> combatMessages = new ArrayList<>();
    private final List<String> fleeMessages = new ArrayList<>();

    // --- Phases du lutin ---
    private enum LutinPhase {
        PHASE_1(1.0, 0.75, "§6§l✦ Lutin Trésorier ✦", 8000, 15000, 20000, 10000),
        PHASE_2(0.75, 0.50, "§e§l✦ Lutin Nerveux ✦", 6000, 12000, 15000, 8000),
        PHASE_3(0.50, 0.25, "§c§l✦ Lutin Paniqué ✦", 4000, 8000, 10000, 6000),
        PHASE_4(0.25, 0.0, "§4§l✦ Lutin Désespéré ✦", 3000, 5000, 8000, 4000);

        private final double healthStart, healthEnd;
        private final String displayName;
        private final long teleportCD, illusionCD, guardianCD, repelCD;

        LutinPhase(double healthStart, double healthEnd, String displayName,
                   long teleportCD, long illusionCD, long guardianCD, long repelCD) {
            this.healthStart = healthStart;
            this.healthEnd = healthEnd;
            this.displayName = displayName;
            this.teleportCD = teleportCD;
            this.illusionCD = illusionCD;
            this.guardianCD = guardianCD;
            this.repelCD = repelCD;
        }

        public boolean isInPhase(double healthPercent) {
            return healthPercent <= healthStart && healthPercent > healthEnd;
        }

        // Getters
        public String getDisplayName() { return displayName; }
        public long getTeleportCD() { return teleportCD; }
        public long getIllusionCD() { return illusionCD; }
        public long getGuardianCD() { return guardianCD; }
        public long getRepelCD() { return repelCD; }
    }

    public LutinTreasure(CustomMobsPlugin plugin) {
        super(plugin, "lutin_treasure");
        initializeMessages();
    }

    @Override
    protected void setDefaultStats() {
        this.maxHealth = 500.0; // Plus de vie pour les phases
        this.damage = 0;       // Ne fait aucun dégât
        this.speed = 0.45;     // Très rapide
    }

    @Override
    public LivingEntity spawn(Location location) {
        // Utilise un Zombie Villager pour l'apparence
        ZombieVillager lutin = location.getWorld().spawn(location, ZombieVillager.class);

        // Équipement riche et scintillant
        lutin.getEquipment().setItemInMainHand(createTreasureItem(Material.EMERALD, "§a§lÉmeraudes Précieuses", 8));
        lutin.getEquipment().setItemInOffHand(createTreasureItem(Material.GOLD_INGOT, "§6§lLingots Dorés", 5));
        lutin.getEquipment().setHelmet(createTreasureItem(Material.GOLDEN_HELMET, "§6§lCouronne de Fortune", 1));
        lutin.getEquipment().setChestplate(createTreasureItem(Material.LEATHER_CHESTPLATE, "§2§lTunique d'Aventurier", 1));
        lutin.getEquipment().setLeggings(createTreasureItem(Material.CHAINMAIL_LEGGINGS, "§7§lJambières Renforcées", 1));
        lutin.getEquipment().setBoots(createTreasureItem(Material.GOLDEN_BOOTS, "§6§lBottes de Vitesse", 1));

        // Configuration des drops d'équipement
        lutin.getEquipment().setItemInMainHandDropChance(0.0f);
        lutin.getEquipment().setItemInOffHandDropChance(0.0f);
        lutin.getEquipment().setHelmetDropChance(0.0f);
        lutin.getEquipment().setChestplateDropChance(0.0f);
        lutin.getEquipment().setLeggingsDropChance(0.0f);
        lutin.getEquipment().setBootsDropChance(0.0f);

        lutin.setCustomName(currentPhase.getDisplayName());
        lutin.setCustomNameVisible(true);
        lutin.setShouldBurnInDay(false);
        lutin.setCanBreakDoors(false);

        // Effets permanents adaptatifs
        updatePhaseEffects(lutin);

        setupEntity(lutin);

        // Initialisation
        this.spawnTime = System.currentTimeMillis();
        this.phaseStartTime = System.currentTimeMillis();
        this.currentDirection = generateRandomDirection();

        // Démarrage des systèmes
        startConstantMovement();
        startThreatDetection();
        startLifetimeManagement();
        startTreasureAura();
        startPhaseUpdater();

        // Annonce spectaculaire
        announceSpawn(location);

        return lutin;
    }

    /**
     * Comportement principal amélioré avec phases
     */
    @Override
    protected void onPlayerNear(Player target) {
        if (entity.isDead()) return;

        List<Player> nearbyPlayers = getNearbyPlayers(20);
        updateThreatLevels(nearbyPlayers);

        double distance = entity.getLocation().distance(target.getLocation());
        long currentTime = System.currentTimeMillis();

        // Messages RP contextuels
        if (currentTime - lastRPMessage > 5000) {
            sendContextualMessage(nearbyPlayers, distance);
            lastRPMessage = currentTime;
        }

        // Logique de phase
        switch (currentPhase) {
            case PHASE_1:
                handlePhase1(target, nearbyPlayers, distance, currentTime);
                break;
            case PHASE_2:
                handlePhase2(target, nearbyPlayers, distance, currentTime);
                break;
            case PHASE_3:
                handlePhase3(target, nearbyPlayers, distance, currentTime);
                break;
            case PHASE_4:
                handlePhase4(target, nearbyPlayers, distance, currentTime);
                break;
        }
    }

    /**
     * Phase 1 : Lutin confiant mais méfiant
     */
    private void handlePhase1(Player target, List<Player> players, double distance, long currentTime) {
        if (distance < 8) {
            if (currentTime - lastTeleport > currentPhase.getTeleportCD()) {
                performTeleportEscape(players);
            } else if (distance < 4) {
                performPanicFlee(target);
            }
        }
    }

    /**
     * Phase 2 : Lutin nerveux, invoque des gardiens
     */
    private void handlePhase2(Player target, List<Player> players, double distance, long currentTime) {
        cleanupGuardians();

        if (guardians.size() < 3 && currentTime - lastSummonGuardians > currentPhase.getGuardianCD()) {
            summonTreasureGuardians(players);
        } else if (distance < 6 && currentTime - lastRepel > currentPhase.getRepelCD()) {
            repelIntruders(players);
        } else if (currentTime - lastTeleport > currentPhase.getTeleportCD() && distance < 10) {
            performTeleportEscape(players);
        }
    }

    /**
     * Phase 3 : Lutin paniqué, illusions multiples
     */
    private void handlePhase3(Player target, List<Player> players, double distance, long currentTime) {
        if (currentTime - lastIllusion > currentPhase.getIllusionCD()) {
            createAdvancedIllusions(players);
        } else if (distance < 8 && currentTime - lastTeleport > currentPhase.getTeleportCD()) {
            performChainTeleports(players);
        } else if (currentTime - lastPanicAbility > 8000) {
            performPanicAbility(players);
        }
    }

    /**
     * Phase 4 : Lutin désespéré, toutes les capacités
     */
    private void handlePhase4(Player target, List<Player> players, double distance, long currentTime) {
        // Utilise toutes ses capacités en désespoir
        if (currentTime - lastIllusion > currentPhase.getIllusionCD()) {
            createMassiveIllusionStorm(players);
        } else if (currentTime - lastSummonGuardians > currentPhase.getGuardianCD()) {
            summonEliteGuardians(players);
        } else if (currentTime - lastTeleport > currentPhase.getTeleportCD()) {
            performDesperationTeleport(players);
        } else if (distance < 12) {
            performUltimateRepel(players);
        }
    }

    /**
     * Animation quand le lutin prend des dégâts
     */
    @Override
    public void attack(Player target) {
        isInCombat = true;

        // Animation de pièces qui tombent
        createCoinDropAnimation();

        // Message de combat
        sendCombatMessage(Collections.singletonList(target));

        // Force une fuite immédiate
        performPanicFlee(target);

        // Redevient non-combattant après 5 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                isInCombat = false;
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Crée l'animation des pièces qui tombent
     */
    private void createCoinDropAnimation() {
        if (System.currentTimeMillis() - lastTreasureDrop < 1000) return;
        lastTreasureDrop = System.currentTimeMillis();

        Location loc = entity.getLocation();

        // Effets sonores et visuels
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.5f, 0.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_COPPER_BREAK, 1.0f, 1.5f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.add(0, 1, 0), 15, 1, 1, 1, 0.1);

        // Créer 3-6 pièces temporaires
        int coinCount = 3 + random.nextInt(4);
        for (int i = 0; i < coinCount; i++) {
            createTemporaryCoin(loc, i * 2L);
        }
    }

    /**
     * Crée une pièce temporaire qui disparaît
     */
    private void createTemporaryCoin(Location center, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Position aléatoire autour du lutin
                Location coinLoc = center.clone().add(
                        (random.nextDouble() - 0.5) * 3,
                        0.5,
                        (random.nextDouble() - 0.5) * 3
                );

                // Type de pièce aléatoire
                Material coinType = random.nextBoolean() ? Material.GOLD_NUGGET : Material.EMERALD;
                ItemStack coin = createTreasureItem(coinType, "§6§l✦ Pièce Perdue ✦", 1);

                // Spawn l'item avec des effets
                Item droppedCoin = coinLoc.getWorld().dropItem(coinLoc, coin);
                droppedCoin.setPickupDelay(Integer.MAX_VALUE); // Ne peut pas être ramassé
                droppedCoin.setVelocity(new Vector(
                        (random.nextDouble() - 0.5) * 0.3,
                        0.2 + random.nextDouble() * 0.3,
                        (random.nextDouble() - 0.5) * 0.3
                ));

                // Effets visuels sur la pièce
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (droppedCoin.isDead() || ticks >= 40) { // 2 secondes
                            if (!droppedCoin.isDead()) {
                                // Animation de disparition
                                droppedCoin.getWorld().spawnParticle(Particle.SMOKE, droppedCoin.getLocation(), 10, 0.3, 0.3, 0.3, 0.1);
                                droppedCoin.getWorld().playSound(droppedCoin.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.5f);
                                droppedCoin.remove();
                            }
                            cancel();
                            return;
                        }

                        // Particules scintillantes
                        droppedCoin.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, droppedCoin.getLocation(), 2, 0.1, 0.1, 0.1, 0);
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, delay);
    }

    /**
     * Mouvement constant amélioré
     */
    private void startConstantMovement() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                if (isConstantlyMoving) {
                    performConstantMovement();
                }
            }
        }.runTaskTimer(plugin, 0L, 8L); // Toutes les 0.4 secondes
    }

    /**
     * Effectue le mouvement constant adaptatif
     */
    private void performConstantMovement() {
        movementTicks++;

        // Change de direction toutes les 1-3 secondes
        if (movementTicks % (25 + random.nextInt(50)) == 0) {
            currentDirection = generateRandomDirection();
        }

        // Évite les murs et obstacles
        if (isBlockingAhead()) {
            currentDirection = generateRandomDirection();
        }

        // Vitesse adaptative selon la phase et la situation
        double speedMultiplier = getSpeedMultiplier();
        Vector movement = currentDirection.clone().multiply(speedMultiplier);

        // Ajout de petits sauts occasionnels
        if (random.nextDouble() < 0.1) {
            movement.setY(0.3);
        }

        entity.setVelocity(movement);

        // Particules de mouvement
        if (movementTicks % 10 == 0) {
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation(), 1, 0.2, 0.2, 0.2, 0);
        }
    }

    /**
     * Génère une direction aléatoire intelligente
     */
    private Vector generateRandomDirection() {
        // Évite les joueurs proches
        List<Player> nearbyPlayers = getNearbyPlayers(10);
        if (!nearbyPlayers.isEmpty()) {
            Vector fleeDirection = new Vector(0, 0, 0);
            for (Player p : nearbyPlayers) {
                Vector away = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
                fleeDirection.add(away);
            }
            fleeDirection.normalize();
            fleeDirection.setY(0);
            return fleeDirection.multiply(0.3);
        }

        // Direction aléatoire normale
        double angle = random.nextDouble() * 2 * Math.PI;
        return new Vector(Math.cos(angle) * 0.25, 0, Math.sin(angle) * 0.25);
    }

    /**
     * Vérifie s'il y a un obstacle devant
     */
    private boolean isBlockingAhead() {
        Location ahead = entity.getLocation().add(currentDirection.clone().multiply(2));
        return !ahead.getBlock().isPassable() || !ahead.clone().add(0, 1, 0).getBlock().isPassable();
    }

    /**
     * Calcule le multiplicateur de vitesse selon le contexte
     */
    private double getSpeedMultiplier() {
        double baseSpeed = 0.25;

        // Bonus de phase
        baseSpeed *= switch (currentPhase) {
            case PHASE_1 -> 1.0;
            case PHASE_2 -> 1.2;
            case PHASE_3 -> 1.4;
            case PHASE_4 -> 1.6;
        };

        // Bonus si en combat
        if (isInCombat) {
            baseSpeed *= 1.5;
        }

        // Bonus si des joueurs sont très proches
        List<Player> veryClose = getNearbyPlayers(5);
        if (!veryClose.isEmpty()) {
            baseSpeed *= 1.3;
        }

        return baseSpeed;
    }

    /**
     * Téléportation améliorée
     */
    private void performTeleportEscape(List<Player> players) {
        lastTeleport = System.currentTimeMillis();

        Location teleportLoc = findOptimalTeleportLocation(players);
        if (teleportLoc == null) return;

        // Effets avant téléportation
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.3f);
        entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation(), 40, 1, 1, 1, 1);

        // Message RP
        sendMessageToNearby("§6§l« Vous ne m'attraperez jamais ! »", players);

        entity.teleport(teleportLoc);

        // Effets après téléportation
        teleportLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, teleportLoc, 25, 1, 1, 1, 0.1);
        teleportLoc.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.8f);
    }

    /**
     * Illusions avancées corrigées
     */
    private void createAdvancedIllusions(List<Player> players) {
        lastIllusion = System.currentTimeMillis();

        cleanupIllusions();
        sendMessageToNearby("§6§l« Essayez de me trouver maintenant ! »", players);

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.0f, 1.0f);
        entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation(), 50, 2, 2, 2, 0.3);

        // Crée 4-6 illusions dans un rayon plus large
        int illusionCount = 4 + random.nextInt(3);
        for (int i = 0; i < illusionCount; i++) {
            Location illusionLoc = findValidIllusionLocation();
            if (illusionLoc != null) {
                createAdvancedIllusion(illusionLoc, i);
            }
        }

        // Le vrai lutin devient invisible plus longtemps
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0));
    }

    /**
     * Crée une illusion avancée avec comportement
     */
    private void createAdvancedIllusion(Location location, int index) {
        ZombieVillager illusion = location.getWorld().spawn(location, ZombieVillager.class);

        // Copie l'apparence exacte
        copyAppearanceToIllusion(illusion);

        illusion.setCustomName("§7§l✦ " + currentPhase.getDisplayName().replace("§6§l", "").replace("§e§l", "").replace("§c§l", "").replace("§4§l", "") + " ✦");
        illusion.setCustomNameVisible(true);
        illusion.setShouldBurnInDay(false);

        // Configuration spéciale des illusions
        illusion.setMetadata("lutin_illusion", new FixedMetadataValue(plugin, true));
        illusion.setMetadata("illusion_index", new FixedMetadataValue(plugin, index));
        illusion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));

        // Stats d'illusion
        illusion.getAttribute(Attribute.MAX_HEALTH).setBaseValue(5.0);
        illusion.setHealth(5.0);

        illusions.add(illusion);

        // Comportement d'illusion
        startIllusionBehavior(illusion);

        // Auto-destruction après 20 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!illusion.isDead()) {
                    illusion.getWorld().spawnParticle(Particle.CLOUD, illusion.getLocation(), 15, 1, 1, 1, 0.1);
                    illusion.getWorld().playSound(illusion.getLocation(), Sound.ENTITY_ILLUSIONER_HURT, 1.0f, 1.5f);
                    illusion.remove();
                }
                illusions.remove(illusion);
            }
        }.runTaskLater(plugin, 400L);
    }

    /**
     * Donne un comportement réaliste aux illusions
     */
    private void startIllusionBehavior(ZombieVillager illusion) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (illusion.isDead()) {
                    cancel();
                    return;
                }

                // Mouvement erratique
                if (ticks % 20 == 0) {
                    Vector randomMove = new Vector(
                            (random.nextDouble() - 0.5) * 0.3,
                            0,
                            (random.nextDouble() - 0.5) * 0.3
                    );
                    illusion.setVelocity(randomMove);
                }

                // Particules occasionnelles
                if (ticks % 40 == 0) {
                    illusion.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, illusion.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /**
     * Invoque des gardiens du trésor
     */
    private void summonTreasureGuardians(List<Player> players) {
        lastSummonGuardians = System.currentTimeMillis();
        cleanupGuardians();

        sendMessageToNearby("§6§l« Mes gardiens vont vous arrêter ! »", players);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 2.0f, 0.8f);

        int guardianCount = Math.min(2 + currentPhase.ordinal(), 5);
        for (int i = 0; i < guardianCount; i++) {
            summonSingleGuardian(i);
        }
    }

    /**
     * Invoque un gardien individuel
     */
    private void summonSingleGuardian(int index) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Location guardianLoc = entity.getLocation().add(
                        (random.nextDouble() - 0.5) * 6,
                        0,
                        (random.nextDouble() - 0.5) * 6
                );
                guardianLoc = guardianLoc.getWorld().getHighestBlockAt(guardianLoc).getLocation().add(0, 1, 0);

                // Types de gardiens selon la phase
                LivingEntity guardian;
                switch (currentPhase) {
                    case PHASE_1, PHASE_2:
                        guardian = guardianLoc.getWorld().spawn(guardianLoc, Vex.class);
                        guardian.setCustomName("§7§lGardien Éthéré");
                        break;
                    case PHASE_3:
                        guardian = guardianLoc.getWorld().spawn(guardianLoc, Silverfish.class);
                        guardian.setCustomName("§8§lProtecteur Mineur");
                        break;
                    case PHASE_4:
                    default:
                        guardian = guardianLoc.getWorld().spawn(guardianLoc, Endermite.class);
                        guardian.setCustomName("§5§lGardien du Vide");
                        break;
                }

                guardian.setCustomNameVisible(true);
                guardian.setMetadata("lutin_guardian", new FixedMetadataValue(plugin, true));
                guardian.setMetadata("guardian_master", new FixedMetadataValue(plugin, entity.getUniqueId().toString()));

                // Stats de gardien
                guardian.getAttribute(Attribute.MAX_HEALTH).setBaseValue(15.0);
                guardian.setHealth(15.0);
                guardian.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));

                guardians.add(guardian);

                // Effets de spawn
                guardianLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, guardianLoc, 20, 1, 1, 1, 0.1);
                guardianLoc.getWorld().playSound(guardianLoc, Sound.ENTITY_VEX_AMBIENT, 1.0f, 1.2f);

                // Auto-destruction après 30 secondes
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!guardian.isDead()) {
                            guardian.getWorld().spawnParticle(Particle.SMOKE, guardian.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                            guardian.remove();
                        }
                        guardians.remove(guardian);
                    }
                }.runTaskLater(plugin, 600L);
            }
        }.runTaskLater(plugin, index * 10L);
    }

    /**
     * Messages RP contextuels
     */
    private void initializeMessages() {
        // Messages par phase
        phaseMessages.put(LutinPhase.PHASE_1, Arrays.asList(
                "§6§l« Mon trésor est à moi ! »",
                "§6§l« Vous ne pouvez pas m'attraper ! »",
                "§6§l« Ces richesses sont miennes ! »",
                "§6§l« Allez-vous en ! »",
                "§6§l« Mes pièces brillent trop pour vous ! »"
        ));

        phaseMessages.put(LutinPhase.PHASE_2, Arrays.asList(
                "§e§l« Restez loin de mon trésor ! »",
                "§e§l« Mes gardiens vont vous chasser ! »",
                "§e§l« Vous commencez à m'énerver... »",
                "§e§l« Arrêtez de me poursuivre ! »",
                "§e§l« J'ai travaillé dur pour ces richesses ! »"
        ));

        phaseMessages.put(LutinPhase.PHASE_3, Arrays.asList(
                "§c§l« LAISSEZ-MOI TRANQUILLE ! »",
                "§c§l« Mes illusions vont vous perdre ! »",
                "§c§l« Vous n'aurez JAMAIS mon trésor ! »",
                "§c§l« Je commence à paniquer ! »",
                "§c§l« Mes précieuses pièces ! »"
        ));

        phaseMessages.put(LutinPhase.PHASE_4, Arrays.asList(
                "§4§l« NON ! PAS MON TRÉSOR ! »",
                "§4§l« JE VAIS TOUT UTILISER ! »",
                "§4§l« VOUS N'AUREZ RIEN ! »",
                "§4§l« C'EST MA DERNIÈRE CHANCE ! »",
                "§4§l« JE PRÉFÈRE MOURIR ! »"
        ));

        // Messages de combat
        combatMessages.addAll(Arrays.asList(
                "§c§l« AÏE ! Mes pièces ! »",
                "§c§l« Mes trésors s'échappent ! »",
                "§c§l« Non ! Pas mes précieuses ! »",
                "§c§l« Vous m'avez touché ! »",
                "§c§l« Mes richesses tombent ! »"
        ));

        // Messages de fuite
        fleeMessages.addAll(Arrays.asList(
                "§6§l« Attrapez-moi si vous le pouvez ! »",
                "§6§l« Je suis trop rapide ! »",
                "§6§l« Mes petites jambes sont rapides ! »",
                "§6§l« Vous ne me rattraperez jamais ! »",
                "§6§l« Bye bye ! »"
        ));
    }

    /**
     * Envoie un message contextuel
     */
    private void sendContextualMessage(List<Player> players, double distance) {
        if (players.isEmpty()) return;

        String message;
        if (isInCombat) {
            message = combatMessages.get(random.nextInt(combatMessages.size()));
        } else if (distance < 8) {
            message = fleeMessages.get(random.nextInt(fleeMessages.size()));
        } else {
            List<String> phaseMsg = phaseMessages.get(currentPhase);
            message = phaseMsg.get(random.nextInt(phaseMsg.size()));
        }

        sendMessageToNearby(message, players);
    }

    /**
     * Envoie un message de combat
     */
    private void sendCombatMessage(List<Player> players) {
        String message = combatMessages.get(random.nextInt(combatMessages.size()));
        sendMessageToNearby(message, players);
    }

    /**
     * Envoie un message aux joueurs proches
     */
    private void sendMessageToNearby(String message, List<Player> players) {
        for (Player p : players) {
            if (p.getLocation().distance(entity.getLocation()) <= 15) {
                p.sendMessage(message);
            }
        }
    }

    // === MÉTHODES UTILITAIRES ET SYSTÈMES ===

    /**
     * Met à jour les effets de phase
     */
    private void updatePhaseEffects(ZombieVillager lutin) {
        // Retire les anciens effets
        lutin.removePotionEffect(PotionEffectType.SPEED);
        lutin.removePotionEffect(PotionEffectType.JUMP_BOOST);
        lutin.removePotionEffect(PotionEffectType.RESISTANCE);

        // Applique les nouveaux effets selon la phase
        switch (currentPhase) {
            case PHASE_1:
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
                break;
            case PHASE_2:
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 3));
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
                break;
            case PHASE_3:
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 4));
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2));
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));
                break;
            case PHASE_4:
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 5));
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2));
                lutin.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 2));
                break;
        }
    }

    /**
     * Démarre le système de mise à jour des phases
     */
    private void startPhaseUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                updatePhase();
            }
        }.runTaskTimer(plugin, 40L, 40L); // Toutes les 2 secondes
    }

    /**
     * Met à jour la phase selon la vie
     */
    private void updatePhase() {
        double healthPercent = entity.getHealth() / maxHealth;
        LutinPhase newPhase = currentPhase;

        for (LutinPhase phase : LutinPhase.values()) {
            if (phase.isInPhase(healthPercent)) {
                newPhase = phase;
                break;
            }
        }

        if (newPhase != currentPhase) {
            LutinPhase oldPhase = currentPhase;
            currentPhase = newPhase;
            onPhaseChange(oldPhase, newPhase);
        }
    }

    /**
     * Gère le changement de phase
     */
    private void onPhaseChange(LutinPhase oldPhase, LutinPhase newPhase) {
        phaseStartTime = System.currentTimeMillis();

        // Met à jour le nom
        entity.setCustomName(newPhase.getDisplayName());

        // Met à jour les effets
        if (entity instanceof ZombieVillager) {
            updatePhaseEffects((ZombieVillager) entity);
        }

        // Annonce le changement
        List<Player> nearbyPlayers = getNearbyPlayers(25);
        switch (newPhase) {
            case PHASE_2:
                sendMessageToNearby("§e§l« Vous commencez à m'énerver ! »", nearbyPlayers);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_HURT, 2.0f, 1.0f);
                break;
            case PHASE_3:
                sendMessageToNearby("§c§l« ÇA SUFFIT ! JE VAIS ÊTRE SÉRIEUX ! »", nearbyPlayers);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 2.0f, 1.2f);
                entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, entity.getLocation().add(0, 2, 0), 10, 1, 1, 1, 0);
                break;
            case PHASE_4:
                sendMessageToNearby("§4§l« NON ! PAS MON PRÉCIEUX TRÉSOR ! »", nearbyPlayers);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.5f);
                entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation(), 20, 2, 2, 2, 0);
                break;
        }

        // Effets visuels de transition
        entity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, entity.getLocation(), 30, 1, 2, 1, 0.2);
    }

    // === CAPACITÉS SPÉCIALES AVANCÉES ===

    private void performChainTeleports(List<Player> players) {
        lastTeleport = System.currentTimeMillis();
        sendMessageToNearby("§c§l« Téléportations en chaîne ! »", players);

        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location teleportLoc = findOptimalTeleportLocation(players);
                    if (teleportLoc != null && !entity.isDead()) {
                        entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation(), 20, 1, 1, 1, 0.5);
                        entity.teleport(teleportLoc);
                        entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, teleportLoc, 20, 1, 1, 1, 0.5);
                    }
                }
            }.runTaskLater(plugin, i * 15L);
        }
    }

    private void createMassiveIllusionStorm(List<Player> players) {
        lastIllusion = System.currentTimeMillis();
        cleanupIllusions();

        sendMessageToNearby("§c§l« TEMPÊTE D'ILLUSIONS ! »", players);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 3.0f, 0.5f);

        // Crée 8-12 illusions en cercle
        int illusionCount = 8 + random.nextInt(5);
        for (int i = 0; i < illusionCount; i++) {
            double angle = (2 * Math.PI * i) / illusionCount;
            Location illusionLoc = entity.getLocation().add(
                    Math.cos(angle) * (5 + random.nextDouble() * 5),
                    0,
                    Math.sin(angle) * (5 + random.nextDouble() * 5)
            );
            illusionLoc = illusionLoc.getWorld().getHighestBlockAt(illusionLoc).getLocation().add(0, 1, 0);

            createAdvancedIllusion(illusionLoc, i);
        }

        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0));
    }

    private void summonEliteGuardians(List<Player> players) {
        lastSummonGuardians = System.currentTimeMillis();
        cleanupGuardians();

        sendMessageToNearby("§4§l« MES GARDIENS ULTIMES ! »", players);

        // Invoque des gardiens plus puissants
        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location guardianLoc = entity.getLocation().add(
                            (random.nextDouble() - 0.5) * 8,
                            0,
                            (random.nextDouble() - 0.5) * 8
                    );
                    guardianLoc = guardianLoc.getWorld().getHighestBlockAt(guardianLoc).getLocation().add(0, 1, 0);

                    IronGolem eliteGuardian = guardianLoc.getWorld().spawn(guardianLoc, IronGolem.class);
                    eliteGuardian.setCustomName("§4§lGardien Ultime");
                    eliteGuardian.setCustomNameVisible(true);
                    eliteGuardian.setMetadata("lutin_guardian", new FixedMetadataValue(plugin, true));

                    // Stats élites
                    eliteGuardian.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
                    eliteGuardian.setHealth(40.0);
                    eliteGuardian.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                    eliteGuardian.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));

                    guardians.add(eliteGuardian);

                    guardianLoc.getWorld().spawnParticle(Particle.FLAME, guardianLoc, 30, 2, 2, 2, 0.1);
                    guardianLoc.getWorld().playSound(guardianLoc, Sound.ENTITY_IRON_GOLEM_HURT, 2.0f, 0.8f);
                }
            }.runTaskLater(plugin, i * 20L);
        }
    }

    private void performDesperationTeleport(List<Player> players) {
        lastTeleport = System.currentTimeMillis();

        sendMessageToNearby("§4§l« TÉLÉPORTATION DÉSESPÉRÉE ! »", players);

        // Téléportation très loin avec beaucoup d'effets
        Location desperationLoc = findDesperationTeleportLocation(players);
        if (desperationLoc != null) {
            entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 20, 2, 2, 2, 0);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

            entity.teleport(desperationLoc);

            desperationLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, desperationLoc, 50, 3, 3, 3, 0.5);
            desperationLoc.getWorld().playSound(desperationLoc, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 0.5f);
        }
    }

    private void performUltimateRepel(List<Player> players) {
        if (System.currentTimeMillis() - lastRepel < currentPhase.getRepelCD()) return;
        lastRepel = System.currentTimeMillis();

        sendMessageToNearby("§4§l« EXPLOSION FINALE ! »", players);

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.3f);
        entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 30, 3, 3, 3, 0);

        for (Player player : players) {
            if (player.getLocation().distance(entity.getLocation()) <= 15) {
                Vector repelDirection = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
                repelDirection.setY(0.8);
                player.setVelocity(repelDirection.multiply(3.0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            }
        }
    }

    private void performPanicAbility(List<Player> players) {
        lastPanicAbility = System.currentTimeMillis();

        // Combine plusieurs capacités en panique
        if (random.nextBoolean()) {
            createAdvancedIllusions(players);
        } else {
            repelIntruders(players);
        }

        // Puis téléportation
        new BukkitRunnable() {
            @Override
            public void run() {
                performTeleportEscape(players);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void performPanicFlee(Player target) {
        Vector fleeDirection = entity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        fleeDirection.setY(0.3);
        entity.setVelocity(fleeDirection.multiply(1.5));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 6));

        entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, entity.getLocation().add(0, 2, 0), 8, 0.5, 0.5, 0.5, 0);
    }

    private void repelIntruders(List<Player> players) {
        if (System.currentTimeMillis() - lastRepel < currentPhase.getRepelCD()) return;
        lastRepel = System.currentTimeMillis();

        sendMessageToNearby("§c§l« REPOUSSEZ-VOUS ! »", players);

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.2f);
        entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 15, 2, 2, 2, 0);

        for (Player player : players) {
            if (player.getLocation().distance(entity.getLocation()) <= 10) {
                Vector repelDirection = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
                repelDirection.setY(0.6);
                player.setVelocity(repelDirection.multiply(2.0));
            }
        }
    }

    // === MÉTHODES DE LOCALISATION ===

    private Location findOptimalTeleportLocation(List<Player> players) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 15 + random.nextDouble() * 20; // 15-35 blocs

            Location testLoc = entity.getLocation().add(
                    Math.cos(angle) * distance,
                    0,
                    Math.sin(angle) * distance
            );
            testLoc = testLoc.getWorld().getHighestBlockAt(testLoc).getLocation().add(0, 1, 0);

            if (isLocationSafeFromPlayers(testLoc, players, 12)) {
                return testLoc;
            }
        }
        return null;
    }

    private Location findDesperationTeleportLocation(List<Player> players) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 30 + random.nextDouble() * 30; // 30-60 blocs

            Location testLoc = entity.getLocation().add(
                    Math.cos(angle) * distance,
                    0,
                    Math.sin(angle) * distance
            );
            testLoc = testLoc.getWorld().getHighestBlockAt(testLoc).getLocation().add(0, 1, 0);

            if (isLocationSafeFromPlayers(testLoc, players, 20)) {
                return testLoc;
            }
        }
        return null;
    }

    private Location findValidIllusionLocation() {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 8 + random.nextDouble() * 12;

            Location testLoc = entity.getLocation().add(
                    Math.cos(angle) * distance,
                    0,
                    Math.sin(angle) * distance
            );
            testLoc = testLoc.getWorld().getHighestBlockAt(testLoc).getLocation().add(0, 1, 0);

            if (isLocationSafe(testLoc)) {
                return testLoc;
            }
        }
        return entity.getLocation().add((random.nextDouble() - 0.5) * 10, 0, (random.nextDouble() - 0.5) * 10);
    }

    private boolean isLocationSafeFromPlayers(Location loc, List<Player> players, double minDistance) {
        if (!isLocationSafe(loc)) return false;

        for (Player p : players) {
            if (loc.distance(p.getLocation()) < minDistance) {
                return false;
            }
        }
        return true;
    }

    private boolean isLocationSafe(Location loc) {
        if (loc.getBlockY() < 1 || loc.getBlockY() > loc.getWorld().getMaxHeight() - 3) return false;

        return loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid() &&
                loc.getBlock().getType().isAir() &&
                loc.clone().add(0, 1, 0).getBlock().getType().isAir();
    }

    // === MÉTHODES DE GESTION ET UTILITAIRES ===

    private void updateThreatLevels(List<Player> players) {
        threateningPlayers.clear();
        for (Player p : players) {
            if (p.getLocation().distance(entity.getLocation()) <= 15) {
                threateningPlayers.add(p.getUniqueId());
            }
        }
    }

    private void startThreatDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                List<Player> nearbyPlayers = getNearbyPlayers(20);
                updateThreatLevels(nearbyPlayers);
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    private void startLifetimeManagement() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                long aliveTime = System.currentTimeMillis() - spawnTime;

                if (aliveTime > LIFETIME - 90000 && aliveTime < LIFETIME - 80000) {
                    sendMessageToNearby("§6§l« Plus qu'une minute et demie ! »", getNearbyPlayers(30));
                } else if (aliveTime > LIFETIME - 30000 && aliveTime < LIFETIME - 20000) {
                    sendMessageToNearby("§c§l« 30 secondes avant que je disparaisse ! »", getNearbyPlayers(30));
                } else if (aliveTime > LIFETIME) {
                    performNaturalEscape();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void startTreasureAura() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                // Aura adaptative selon la phase
                Particle particle = switch (currentPhase) {
                    case PHASE_1 -> Particle.HAPPY_VILLAGER;
                    case PHASE_2 -> Particle.CRIT;
                    case PHASE_3 -> Particle.ENCHANT;
                    case PHASE_4 -> Particle.TOTEM_OF_UNDYING;
                };

                entity.getWorld().spawnParticle(particle, entity.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3, 0);

                if (random.nextDouble() < 0.3) {
                    entity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, entity.getLocation().add(0, 1, 0), 1, 0.1, 0.1, 0.1, 0);
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f, 2.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 12L);
    }

    private void performNaturalEscape() {
        sendMessageToNearby("§6§l« Ha ! Je me suis échappé avec tout mon trésor ! Au revoir ! »", getNearbyPlayers(40));

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 3.0f, 1.5f);
        entity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, entity.getLocation(), 50, 3, 3, 3, 0.5);

        cleanupAll();
        entity.remove();
    }

    private void announceSpawn(Location location) {
        for (Player p : location.getWorld().getPlayers()) {
            if (p.getLocation().distance(location) <= 60) {
                p.sendMessage("");
                p.sendMessage("§6§l✦ ═══════════════════════════════════ ✦");
                p.sendMessage("§6§l       UN LUTIN TRÉSORIER APPARAÎT !");
                p.sendMessage("§e§l     Capturez-le avant qu'il s'échappe !");
                p.sendMessage("§e§l    Il cache des richesses inestimables !");
                p.sendMessage("§6§l✦ ═══════════════════════════════════ ✦");
                p.sendMessage("");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            }
        }

        location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 50, 3, 3, 3, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
    }

    private void cleanupIllusions() {
        for (LivingEntity illusion : new ArrayList<>(illusions)) {
            if (!illusion.isDead()) {
                illusion.getWorld().spawnParticle(Particle.CLOUD, illusion.getLocation(), 8, 0.5, 0.5, 0.5, 0.1);
                illusion.remove();
            }
        }
        illusions.clear();
    }

    private void cleanupGuardians() {
        guardians.removeIf(guardian -> {
            if (guardian.isDead()) {
                return true;
            }
            // Garde seulement les gardiens récents
            return false;
        });
    }

    private void cleanupAll() {
        cleanupIllusions();
        cleanupGuardians();
    }

    private void copyAppearanceToIllusion(ZombieVillager illusion) {
        if (entity instanceof ZombieVillager original) {
            illusion.getEquipment().setItemInMainHand(original.getEquipment().getItemInMainHand());
            illusion.getEquipment().setItemInOffHand(original.getEquipment().getItemInOffHand());
            illusion.getEquipment().setHelmet(original.getEquipment().getHelmet());
            illusion.getEquipment().setChestplate(original.getEquipment().getChestplate());
            illusion.getEquipment().setLeggings(original.getEquipment().getLeggings());
            illusion.getEquipment().setBoots(original.getEquipment().getBoots());
        }
    }

    private ItemStack createTreasureItem(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList("§7Trésor du Lutin", "§8Précieux et scintillant"));
            item.setItemMeta(meta);
        }
        return item;
    }

    protected List<Player> getNearbyPlayers(double radius) {
        return entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && (((Player)e).getGameMode() == GameMode.SURVIVAL || ((Player)e).getGameMode() == GameMode.ADVENTURE))
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    @Override
    public void specialAbility(Player target) {
        switch (currentPhase) {
            case PHASE_1, PHASE_2:
                if (System.currentTimeMillis() - lastIllusion > currentPhase.getIllusionCD()) {
                    createAdvancedIllusions(Collections.singletonList(target));
                }
                break;
            case PHASE_3, PHASE_4:
                if (System.currentTimeMillis() - lastTeleport > currentPhase.getTeleportCD()) {
                    performTeleportEscape(Collections.singletonList(target));
                }
                break;
        }
    }
}