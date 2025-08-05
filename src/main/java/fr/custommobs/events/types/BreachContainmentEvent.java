package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventConfigManager;
import fr.custommobs.events.EventListener;
import fr.custommobs.managers.BossStatsManager;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Événement Contenir la Brèche - Version entièrement recrée
 * Coopératif avec système de vagues et influence de la réputation
 */
public class BreachContainmentEvent extends ServerEvent {

    private final EventConfigManager configManager;
    private final BossStatsManager bossStatsManager;

    // Système de score
    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Boolean> playerSurvived = new HashMap<>();

    // Gestion des vagues
    private int currentWave = 0;
    private final int maxWaves = 5;
    private final Set<LivingEntity> currentWaveMobs = new HashSet<>();
    private final Set<LivingEntity> allSpawnedMobs = new HashSet<>();

    // Gestion de la zone
    private EventConfigManager.EventLocationConfig selectedZone;
    private Location breachCenter;
    private Location spawnPoint1;
    private Location spawnPoint2;

    // Boss final
    private Ravager finalBoss;

    // Système de téléportation inactivité
    private long lastKillTime;
    private BukkitTask inactivityTask;

    // Gestion des effets atmosphériques
    private long originalTimeWorld = -1;
    private World eventWorld;

    // Monitoring des joueurs dans la zone
    private final Set<UUID> playersInZone = new HashSet<>();
    private BukkitTask zoneMonitoringTask;
    private final double eventRadius = 25.0; // Rayon pour considérer qu'un joueur est dans la zone

    public BreachContainmentEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook,
                                  EventListener.EventRewardsManager rewardsManager, EventConfigManager configManager,
                                  BossStatsManager bossStatsManager) {
        super(plugin, prisonHook, rewardsManager, "breach_containment", "Contenir la Brèche",
                EventType.COOPERATIVE, 20 * 60); // 20 minutes

        this.configManager = configManager;
        this.bossStatsManager = bossStatsManager;
        this.lastKillTime = System.currentTimeMillis();
    }

    @Override
    protected void onStart() {
        // Sélectionner une zone aléatoire
        List<EventConfigManager.EventLocationConfig> zones = configManager.getEventLocationConfigs("breach-areas");
        if (zones.isEmpty()) {
            plugin.getLogger().severe("§cAucune zone de brèche configurée! Événement annulé.");
            forceEnd();
            return;
        }

        selectedZone = zones.get(ThreadLocalRandom.current().nextInt(zones.size()));
        setupBreachLocations();

        if (breachCenter == null) {
            plugin.getLogger().severe("§cImpossible de configurer la zone de brèche! Événement annulé.");
            forceEnd();
            return;
        }

        eventWorld = breachCenter.getWorld();

        // Appliquer les effets atmosphériques
        applyAtmosphericEffects();

        // Annonces serveur
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§5§l🌀 ALERTE CRITIQUE - BRÈCHE DIMENSIONNELLE ! 🌀");
        Bukkit.broadcastMessage("§d§lRupture dimensionnelle détectée en §f" + selectedZone.displayName());
        Bukkit.broadcastMessage("§7§lDes créatures tentent de traverser la barrière !");
        Bukkit.broadcastMessage("§c§lÉvénement coopératif - Unissez-vous pour survivre !");
        Bukkit.broadcastMessage("§7§l5 vagues à affronter - 20 minutes pour tout contenir");
        Bukkit.broadcastMessage("");

        // Démarrer le monitoring des joueurs dans la zone
        startZoneMonitoring();

        // Démarrer la première vague après 10 secondes
        BukkitTask firstWaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                startNextWave();
            }
        }.runTaskLater(plugin, 200L);
        tasks.add(firstWaveTask);

        // Démarrer le système de vérification d'inactivité
        startInactivityCheck();

        plugin.getLogger().info("§5[BRÈCHE] Événement démarré dans le monde " + eventWorld.getName() +
                " à " + breachCenter.getBlockX() + "," + breachCenter.getBlockY() + "," + breachCenter.getBlockZ());
    }

    /**
     * Configure les points de spawn pour la brèche
     */
    private void setupBreachLocations() {
        breachCenter = selectedZone.getRandomLocation(plugin);
        if (breachCenter == null) {
            plugin.getLogger().severe("§cImpossible de générer une localisation pour la brèche!");
            forceEnd();
            return;
        }

        // Deux points de spawn distants de 10-15 blocs
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = 12 + ThreadLocalRandom.current().nextDouble() * 3;

        spawnPoint1 = breachCenter.clone().add(
                Math.cos(angle) * distance,
                2,
                Math.sin(angle) * distance
        );

        spawnPoint2 = breachCenter.clone().add(
                Math.cos(angle + Math.PI) * distance,
                2,
                Math.sin(angle + Math.PI) * distance
        );

        // Effets visuels de la brèche
        createBreachEffects(breachCenter);
    }

    /**
     * Crée les effets visuels de la brèche
     */
    private void createBreachEffects(Location location) {
        if (location.getWorld() == null) return;

        World world = location.getWorld();
        world.spawnParticle(Particle.PORTAL, location, 150, 4, 4, 4, 1.5);
        world.spawnParticle(Particle.DRAGON_BREATH, location, 80, 3, 3, 3, 0.2);
        world.spawnParticle(Particle.REVERSE_PORTAL, location, 50, 2, 2, 2, 0.5);

        // Son dramatique pour tous les joueurs du monde
        for (Player player : world.getPlayers()) {
            player.playSound(location, Sound.ENTITY_ENDERMAN_SCREAM, 2.0f, 0.3f);
            player.playSound(location, Sound.AMBIENT_CRIMSON_FOREST_MOOD, 1.5f, 0.8f);
        }
    }

    /**
     * Applique les effets atmosphériques pendant l'événement
     */
    private void applyAtmosphericEffects() {
        if (eventWorld == null) return;

        // Sauvegarder l'heure actuelle
        originalTimeWorld = eventWorld.getTime();

        // Mettre la nuit (18000 = minuit)
        eventWorld.setTime(18000);

        // Empêcher le cycle jour/nuit
        eventWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

        // Effets météo dramatiques (optionnel)
        if (ThreadLocalRandom.current().nextBoolean()) {
            eventWorld.setStorm(true);
            eventWorld.setThundering(true);
        }

        plugin.getLogger().info("§5[BRÈCHE] Effets atmosphériques appliqués - Nuit éternelle activée");
    }

    /**
     * Restaure les effets atmosphériques normaux
     */
    private void restoreAtmosphericEffects() {
        if (eventWorld == null) return;

        // Restaurer l'heure
        if (originalTimeWorld >= 0) {
            eventWorld.setTime(originalTimeWorld);
        }

        // Réactiver le cycle jour/nuit
        eventWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);

        // Arrêter la météo
        eventWorld.setStorm(false);
        eventWorld.setThundering(false);

        plugin.getLogger().info("§5[BRÈCHE] Effets atmosphériques restaurés");
    }

    /**
     * Démarre le monitoring des joueurs dans la zone
     */
    private void startZoneMonitoring() {
        zoneMonitoringTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive()) {
                    cancel();
                    return;
                }
                updatePlayersInZone();
            }
        }.runTaskTimer(plugin, 20L, 40L); // Vérifier toutes les 2 secondes

        tasks.add(zoneMonitoringTask);
    }

    /**
     * Met à jour la liste des joueurs dans la zone et applique/retire les effets
     */
    private void updatePlayersInZone() {
        if (eventWorld == null || breachCenter == null) return;

        Set<UUID> currentPlayersInZone = new HashSet<>();

        for (Player player : eventWorld.getPlayers()) {
            double distance = player.getLocation().distance(breachCenter);
            if (distance <= eventRadius) {
                currentPlayersInZone.add(player.getUniqueId());

                // Nouveau joueur entrant dans la zone
                if (!playersInZone.contains(player.getUniqueId())) {
                    onPlayerEnterZone(player);
                }
            }
        }

        // Joueurs qui ont quitté la zone
        for (UUID playerId : playersInZone) {
            if (!currentPlayersInZone.contains(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    onPlayerLeaveZone(player);
                }
            }
        }

        playersInZone.clear();
        playersInZone.addAll(currentPlayersInZone);
    }

    /**
     * Gère l'entrée d'un joueur dans la zone
     */
    private void onPlayerEnterZone(Player player) {
        addParticipant(player);
        playerSurvived.put(player.getUniqueId(), true);

        applyReputationEffects(player);

        player.sendMessage("§5§l[BRÈCHE] §7Vous entrez dans la zone de rupture dimensionnelle !");
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.8f);

        // Effets visuels d'entrée
        player.spawnParticle(Particle.PORTAL, player.getLocation(), 20, 0.5, 1, 0.5, 0.2);
    }

    /**
     * Gère la sortie d'un joueur de la zone
     */
    private void onPlayerLeaveZone(Player player) {
        clearPlayerEffects(player);

        player.sendMessage("§7§l[BRÈCHE] §8Vous quittez la zone dimensionnelle...");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.2f);

        // Effets visuels de sortie
        player.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 15, 0.5, 1, 0.5, 0.1);
    }

    /**
     * Applique les effets de réputation à un joueur spécifique
     */
    private void applyReputationEffects(Player player) {
        ReputationTier reputation = prisonHook.getReputationLevel(player);

        // Nettoyer les anciens effets
        clearPlayerEffects(player);

        switch (reputation) {
            case EXEMPLAIRE:
            case HONORABLE:
                // Réputation positive : stats normales (aucun modificateur)
                player.sendMessage("§a§l[BRÈCHE] §7Votre bonne réputation vous maintient en forme !");
                break;

            case RESPECTE:
                // Légèrement positive : stats normales
                player.sendMessage("§7§l[BRÈCHE] §7Vous êtes prêt pour le combat !");
                break;

            case ORDINAIRE:
                // Neutre : -5% vitesse et dégâts, vision légèrement réduite
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
                player.sendMessage("§7§l[BRÈCHE] §8Réputation neutre : vous ressentez une légère fatigue...");
                break;

            case SUSPECT:
                // Négative : -10% vitesse
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, false));
                player.sendMessage("§c§l[BRÈCHE] §7Votre réputation douteuse vous ralentit...");
                break;

            case CRIMINEL:
                // Très négative : -20% vitesse
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 2, false, false));
                player.sendMessage("§4§l[BRÈCHE] §7Votre passé criminel pèse lourd sur vos épaules...");
                break;

            case INFAME:
                // Extrêmement négative : -30% vitesse
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 3, false, false));
                player.sendMessage("§4§l[BRÈCHE] §4Votre infamie vous paralyse presque...");
                break;
        }
    }

    /**
     * Nettoie les effets de potion du joueur
     */
    private void clearPlayerEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }

    /**
     * Vérifie si le joueur est dans la zone de l'événement
     */
    private boolean isInEventZone(Player player) {
        return playersInZone.contains(player.getUniqueId());
    }

    /**
     * Vérifie si le joueur est dans le monde de l'événement
     */
    private boolean isInEventWorld(Player player) {
        return eventWorld != null && player.getWorld().equals(eventWorld);
    }

    /**
     * Démarre la vague suivante
     */
    private void startNextWave() {
        if (!isActive() || currentWave >= maxWaves) return;

        currentWave++;
        currentWaveMobs.clear();

        // Calcul du nombre de monstres selon le nombre de joueurs
        int playerCount = (int) Bukkit.getOnlinePlayers().stream()
                .filter(this::isInEventWorld)
                .count();
        int mobCount = Math.min(50, Math.max(10, playerCount * (2 + currentWave)));

        // Annonces
        if (currentWave < maxWaves) {
            announceWaveStart(currentWave, mobCount);
        } else {
            announceFinalWave(mobCount);
        }

        // Spawner les monstres après 3 secondes
        BukkitTask spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentWave < maxWaves) {
                    spawnWaveMobs(mobCount);
                } else {
                    spawnFinalBoss();
                }
                lastKillTime = System.currentTimeMillis();
            }
        }.runTaskLater(plugin, 60L);
        tasks.add(spawnTask);
    }

    /**
     * Annonce le début d'une vague normale
     */
    private void announceWaveStart(int wave, int mobCount) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInEventWorld(player)) {
                player.sendTitle("§5§lVAGUE " + wave, "§d" + mobCount + " créatures approchent...", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
            }
        }

        Bukkit.broadcastMessage("§5§l[BRÈCHE] §d§lVAGUE " + wave + "/" + maxWaves + " COMMENCE !");
        Bukkit.broadcastMessage("§7§l" + mobCount + " créatures tentent de traverser !");

        plugin.getLogger().info("§5[BRÈCHE] Vague " + wave + " démarrée - " + mobCount + " monstres à spawner");
    }

    /**
     * Annonce la vague finale avec le boss
     */
    private void announceFinalWave(int mobCount) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInEventWorld(player)) {
                player.sendTitle("§4§lVAGUE FINALE", "§c§lRAVAGER DIMENSIONNEL !", 10, 60, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
            }
        }

        Bukkit.broadcastMessage("§4§l[BRÈCHE] §c§lVAGUE FINALE - BOSS DIMENSIONNEL !");
        Bukkit.broadcastMessage("§7§lUn Ravager géant traverse la brèche !");

        plugin.getLogger().info("§5[BRÈCHE] Vague finale démarrée - Boss Ravager");
    }

    /**
     * Spawn les monstres d'une vague normale
     */
    private void spawnWaveMobs(int mobCount) {
        List<EventConfigManager.EventMobConfig> possibleMobs = configManager.getEventMobsInCategory("breach-mobs");
        if (possibleMobs.isEmpty()) {
            plugin.getLogger().warning("Aucun monstre configuré pour breach-mobs!");
            return;
        }

        // Répartir les monstres entre les deux points de spawn
        int mobsPoint1 = mobCount / 2;
        int mobsPoint2 = mobCount - mobsPoint1;

        spawnMobsAtLocation(spawnPoint1, mobsPoint1, possibleMobs);
        spawnMobsAtLocation(spawnPoint2, mobsPoint2, possibleMobs);

        // Effets visuels d'apparition
        createWaveSpawnEffects();
    }

    /**
     * Spawn des monstres à un point donné avec validation des surfaces
     */
    private void spawnMobsAtLocation(Location spawnPoint, int mobCount, List<EventConfigManager.EventMobConfig> possibleMobs) {
        int successfulSpawns = 0;
        int totalAttempts = 0;
        final int maxAttemptsPerMob = 10;

        plugin.getLogger().info("§5[BRÈCHE] Tentative de spawn de " + mobCount + " monstres près de " +
                spawnPoint.getBlockX() + "," + spawnPoint.getBlockY() + "," + spawnPoint.getBlockZ());

        // Vérifier que les monstres sont bien enregistrés dans le plugin
        plugin.getLogger().info("§5[BRÈCHE] Monstres disponibles: " + possibleMobs.size());
        for (EventConfigManager.EventMobConfig mobConfig : possibleMobs) {
            boolean isRegistered = plugin.getMobManager().isMobRegistered(mobConfig.id());
            plugin.getLogger().info("§5[BRÈCHE] - " + mobConfig.id() +
                    " (enregistré: " + isRegistered +
                    ", poids: " + mobConfig.getSpawnWeight() +
                    ", nom: " + mobConfig.getName() + ")");
        }

        for (int i = 0; i < mobCount; i++) {
            EventConfigManager.EventMobConfig selectedMob = selectRandomMob(possibleMobs);
            if (selectedMob == null) {
                plugin.getLogger().warning("§5[BRÈCHE] Aucun monstre sélectionné (liste vide?)");
                continue;
            }

            // Vérifier que le monstre est bien enregistré dans le plugin
            if (!plugin.getMobManager().isMobRegistered(selectedMob.id())) {
                plugin.getLogger().warning("§5[BRÈCHE] ATTENTION: Monstre '" + selectedMob.id() +
                        "' non enregistré dans CustomMobManager!");
                continue;
            }

            Location validSpawnLocation = findValidSpawnLocation(spawnPoint, maxAttemptsPerMob);
            totalAttempts += maxAttemptsPerMob;

            if (validSpawnLocation != null) {
                try {
                    // Utiliser le CustomMobManager pour spawner les monstres custom
                    LivingEntity mob = plugin.getMobManager().spawnCustomMob(selectedMob.id(), validSpawnLocation);

                    if (mob != null) {
                        setupMob(mob);
                        currentWaveMobs.add(mob);
                        allSpawnedMobs.add(mob);
                        successfulSpawns++;

                        plugin.getLogger().info("§5[BRÈCHE] ✅ Monstre CUSTOM " + selectedMob.id() +
                                " (" + mob.getClass().getSimpleName() + ") spawné à " +
                                validSpawnLocation.getBlockX() + "," +
                                validSpawnLocation.getBlockY() + "," +
                                validSpawnLocation.getBlockZ());
                    } else {
                        plugin.getLogger().warning("§5[BRÈCHE] ❌ Échec spawn CustomMob: " + selectedMob.id() +
                                " (getMobManager().spawnCustomMob retourné null)");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§5[BRÈCHE] ❌ Erreur spawn monstre custom " + selectedMob.id() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                plugin.getLogger().warning("§5[BRÈCHE] ❌ Impossible de trouver un emplacement valide pour " + selectedMob.id());
            }
        }

        plugin.getLogger().info("§5[BRÈCHE] Spawn vague " + currentWave + " terminé: " +
                successfulSpawns + "/" + mobCount + " monstres custom spawnés avec succès");

        // Vérifier que tous les monstres spawnés sont bien custom
        int customMobCount = 0;
        for (LivingEntity mob : currentWaveMobs) {
            if (fr.custommobs.mobs.CustomMob.isCustomMob(mob)) {
                customMobCount++;
            }
        }

        plugin.getLogger().info("§5[BRÈCHE] Vérification: " + customMobCount + "/" + currentWaveMobs.size() +
                " monstres de la vague sont des CustomMobs");
    }

    /**
     * Trouve un emplacement de spawn valide près d'un point donné
     */
    private Location findValidSpawnLocation(Location center, int maxAttempts) {
        if (center.getWorld() == null) return null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Position aléatoire dans un rayon de 8 blocs
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = ThreadLocalRandom.current().nextDouble() * 8;

            int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);

            // Chercher une surface valide
            Location testLocation = findSafeLocationAt(center.getWorld(), x, z, center.getBlockY() - 5, center.getBlockY() + 10);

            if (testLocation != null && isValidSpawnLocation(testLocation)) {
                return testLocation;
            }
        }

        return null;
    }

    /**
     * Trouve un emplacement sûr à des coordonnées données
     */
    private Location findSafeLocationAt(World world, int x, int z, int minY, int maxY) {
        // Commence par le haut et descend pour trouver un bloc solide
        for (int y = maxY; y >= minY; y--) {
            Location testLoc = new Location(world, x + 0.5, y + 1, z + 0.5); // +1 pour être au-dessus du bloc

            if (isValidSpawnLocation(testLoc)) {
                return testLoc;
            }
        }
        return null;
    }

    /**
     * Vérifie si un emplacement est valide pour le spawn (basé sur SpawnManager)
     */
    private boolean isValidSpawnLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;

        World world = location.getWorld();
        Block groundBlock = location.getBlock().getRelative(0, -1, 0);
        Block bodyBlock = location.getBlock();
        Block headBlock = location.getBlock().getRelative(0, 1, 0);

        // Le sol doit être solide et pas dangereux
        if (!isValidGroundBlock(groundBlock)) {
            return false;
        }

        // Le corps et la tête doivent être libres
        if (bodyBlock.getType().isSolid() || headBlock.getType().isSolid()) {
            return false;
        }

        // Éviter les spawns dans l'eau, lave, ou trop haut
        if (isInDangerousLocation(location)) {
            return false;
        }

        // Éviter les spawns trop proches des joueurs (minimum 3 blocs)
        return !isTooCloseToPlayers(location, 3.0);
    }

    /**
     * Vérifie si un bloc est un bon sol pour le spawn
     */
    private boolean isValidGroundBlock(Block block) {
        Material type = block.getType();

        // Le bloc doit être solide
        if (!type.isSolid()) return false;

        // Éviter les blocs dangereux
        return type != Material.LAVA &&
                type != Material.MAGMA_BLOCK &&
                type != Material.CACTUS &&
                type != Material.CAMPFIRE &&
                type != Material.SOUL_CAMPFIRE &&
                !type.name().contains("PRESSURE_PLATE") &&
                type != Material.TRIPWIRE_HOOK;
    }

    /**
     * Vérifie si l'emplacement est dangereux
     */
    private boolean isInDangerousLocation(Location location) {
        Block block = location.getBlock();

        // Dans l'eau ou la lave
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            return true;
        }

        // Trop haut (risque de chute mortelle)
        Location below = location.clone();
        for (int i = 1; i <= 10; i++) {
            below.subtract(0, 1, 0);
            if (below.getBlock().getType().isSolid()) {
                return false; // Il y a un sol en dessous
            }
        }
        return true; // Pas de sol trouvé dans les 10 blocs
    }

    /**
     * Vérifie si l'emplacement est trop proche des joueurs
     */
    private boolean isTooCloseToPlayers(Location location, double minDistance) {
        return location.getWorld().getPlayers().stream()
                .anyMatch(player -> player.getLocation().distance(location) < minDistance);
    }

    /**
     * Configure un monstre spawné
     */
    private void setupMob(LivingEntity mob) {
        // Métadonnées principales
        mob.setMetadata("breach_mob", new FixedMetadataValue(plugin, true));
        mob.setMetadata("breach_event_id", new FixedMetadataValue(plugin, getId()));
        mob.setMetadata("breach_wave", new FixedMetadataValue(plugin, currentWave));

        // Boss final
        if (currentWave >= maxWaves && mob instanceof Ravager) {
            mob.setMetadata("breach_boss", new FixedMetadataValue(plugin, true));
            plugin.getLogger().info("§5[BRÈCHE] Boss final configuré avec métadonnées breach_boss");
        }

        // Effet glowing
        mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));

        // Nom visible
        mob.setCustomNameVisible(true);

        // Debug : vérifier les métadonnées appliquées
        plugin.getLogger().info("§5[BRÈCHE] Monstre configuré: " + mob.getType() +
                " | Métadonnées: " + mob.getName() +
                " | ID: " + mob.getUniqueId());

        // Effets visuels d'apparition
        if (mob.getLocation().getWorld() != null) {
            mob.getLocation().getWorld().spawnParticle(Particle.PORTAL, mob.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 1.0);
        }
    }

    /**
     * Spawn le boss final (RavagerBoss custom)
     */
    private void spawnFinalBoss() {
        try {
            // Utiliser le RavagerBoss custom du plugin au lieu d'un Ravager vanilla
            LivingEntity bossEntity = plugin.getMobManager().spawnCustomMob("ravager_boss", breachCenter);

            if (bossEntity == null) {
                plugin.getLogger().severe("§5[BRÈCHE] Erreur: Impossible de spawner le RavagerBoss custom!");
                return;
            }

            // Vérifier que c'est bien un Ravager
            if (!(bossEntity instanceof Ravager)) {
                plugin.getLogger().warning("§5[BRÈCHE] Attention: Le boss spawné n'est pas un Ravager!");
            }

            finalBoss = (Ravager) bossEntity;

            // Le nom est déjà configuré par RavagerBoss, mais on peut l'override pour l'événement
            finalBoss.setCustomName("§4§l⚔ RAVAGER DIMENSIONNEL ⚔");
            finalBoss.setCustomNameVisible(true);

            // Scaling de santé selon le nombre de joueurs
            double healthMultiplier = Math.max(1.0, getParticipantCount() / 4.0);
            double newMaxHealth = finalBoss.getMaxHealth() * healthMultiplier;
            finalBoss.setMaxHealth(Math.min(1024.0, newMaxHealth));

            finalBoss.setHealth(finalBoss.getMaxHealth());

            // Métadonnées spécifiques au boss de brèche
            finalBoss.setMetadata("breach_boss", new FixedMetadataValue(plugin, true));
            finalBoss.setMetadata("breach_mob", new FixedMetadataValue(plugin, true));
            finalBoss.setMetadata("breach_event_id", new FixedMetadataValue(plugin, getId()));
            finalBoss.setMetadata("breach_wave", new FixedMetadataValue(plugin, currentWave));

            // Effets spéciaux pour l'événement (en plus de ceux du RavagerBoss)
            finalBoss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            finalBoss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            finalBoss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));

            currentWaveMobs.add(finalBoss);
            allSpawnedMobs.add(finalBoss);

            // Debug log pour le boss
            plugin.getLogger().info("§5[BRÈCHE] RavagerBoss custom spawné avec succès !");
            plugin.getLogger().info("§5[BRÈCHE] Boss stats: Santé=" + finalBoss.getHealth() + "/" + finalBoss.getMaxHealth() +
                    " | Métadonnées=" + finalBoss.getName() +
                    " | ID=" + finalBoss.getUniqueId());

            // Effets visuels dramatiques spéciaux pour la brèche
            World world = finalBoss.getWorld();
            world.spawnParticle(Particle.EXPLOSION, finalBoss.getLocation(), 30, 4, 4, 4, 0);
            world.spawnParticle(Particle.LAVA, finalBoss.getLocation(), 80, 3, 3, 3, 0);
            world.spawnParticle(Particle.PORTAL, finalBoss.getLocation(), 100, 5, 5, 5, 2.0);
            world.spawnParticle(Particle.DRAGON_BREATH, finalBoss.getLocation(), 60, 3, 3, 3, 0.3);

            // Sons multiples pour plus d'impact
            for (Player player : world.getPlayers()) {
                if (isInEventWorld(player)) {
                    player.playSound(finalBoss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.3f);
                    player.playSound(finalBoss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
                    player.playSound(finalBoss.getLocation(), Sound.AMBIENT_CRIMSON_FOREST_MOOD, 2.0f, 0.5f);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du spawn du RavagerBoss custom: " + e.getMessage());
            e.printStackTrace();

            // Fallback: essayer de spawn un Ravager vanilla si le custom échoue
            try {
                plugin.getLogger().warning("§5[BRÈCHE] Tentative de fallback avec Ravager vanilla...");
                finalBoss = (Ravager) breachCenter.getWorld().spawnEntity(breachCenter, EntityType.RAVAGER);
                finalBoss.setCustomName("§4§l⚔ Ravager Dimensionnel (Fallback) ⚔");
                finalBoss.setCustomNameVisible(true);

                // Configuration minimale pour le fallback
                finalBoss.setMaxHealth(400.0);
                finalBoss.setHealth(400.0);
                finalBoss.setMetadata("breach_boss", new FixedMetadataValue(plugin, true));
                finalBoss.setMetadata("breach_mob", new FixedMetadataValue(plugin, true));

                currentWaveMobs.add(finalBoss);
                allSpawnedMobs.add(finalBoss);

                plugin.getLogger().info("§5[BRÈCHE] Fallback Ravager vanilla spawné avec succès");

            } catch (Exception fallbackError) {
                plugin.getLogger().severe("§5[BRÈCHE] Erreur critique: Impossible de spawner le boss final!");
                fallbackError.printStackTrace();
            }
        }
    }

    /**
     * Sélectionne un monstre aléatoire selon les poids
     */
    private EventConfigManager.EventMobConfig selectRandomMob(List<EventConfigManager.EventMobConfig> mobs) {
        if (mobs.isEmpty()) return null;

        int totalWeight = mobs.stream().mapToInt(EventConfigManager.EventMobConfig::getSpawnWeight).sum();
        if (totalWeight <= 0) {
            return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size()));
        }

        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (EventConfigManager.EventMobConfig mob : mobs) {
            currentWeight += mob.getSpawnWeight();
            if (randomWeight < currentWeight) {
                return mob;
            }
        }

        return mobs.getFirst(); // Fallback
    }

    /**
     * Crée les effets visuels d'apparition d'une vague
     */
    private void createWaveSpawnEffects() {
        if (breachCenter.getWorld() == null) return;

        World world = breachCenter.getWorld();

        // Effets à la brèche centrale
        world.spawnParticle(Particle.PORTAL, breachCenter, 100, 5, 5, 5, 2.0);
        world.spawnParticle(Particle.DRAGON_BREATH, breachCenter, 60, 3, 3, 3, 0.3);

        // Effets aux points de spawn
        world.spawnParticle(Particle.REVERSE_PORTAL, spawnPoint1, 50, 2, 2, 2, 1.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, spawnPoint2, 50, 2, 2, 2, 1.0);
    }

    /**
     * Démarre le système de vérification d'inactivité
     */
    private void startInactivityCheck() {
        inactivityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive()) {
                    cancel();
                    return;
                }

                long timeSinceLastKill = System.currentTimeMillis() - lastKillTime;
                if (timeSinceLastKill >= 60000) { // 1 minute
                    teleportMobsToSpawn();
                    lastKillTime = System.currentTimeMillis();
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Vérifier toutes les minutes

        tasks.add(inactivityTask);
    }

    /**
     * Retéléporte tous les monstres vivants aux points de spawn
     */
    private void teleportMobsToSpawn() {
        int teleported = 0;
        for (LivingEntity mob : new HashSet<>(currentWaveMobs)) {
            if (mob != null && !mob.isDead()) {
                Location teleportLocation = findValidSpawnLocation(
                        ThreadLocalRandom.current().nextBoolean() ? spawnPoint1 : spawnPoint2,
                        10
                );

                if (teleportLocation != null) {
                    mob.teleport(teleportLocation);

                    // Effets visuels
                    if (mob.getLocation().getWorld() != null) {
                        mob.getLocation().getWorld().spawnParticle(Particle.PORTAL, mob.getLocation(), 20, 1, 1, 1, 0.5);
                    }
                    teleported++;
                } else {
                    // Si on ne trouve pas de position valide, utiliser le point de base
                    Location fallbackLocation = ThreadLocalRandom.current().nextBoolean() ? spawnPoint1 : spawnPoint2;
                    mob.teleport(fallbackLocation);
                    teleported++;
                }
            }
        }

        if (teleported > 0) {
            plugin.getLogger().info("§5[BRÈCHE] " + teleported + " monstres retéléportés pour inactivité");

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isInEventWorld(player)) {
                    player.sendMessage("§5§l[BRÈCHE] §7Les créatures ont été retéléportées ! (" + teleported + " retéléportées)");
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                }
            }
        }
    }

    /**
     * Gère la mort d'un monstre
     */
    public void onMobKilled(LivingEntity mob, Player killer) {
        if (!isActive()) {
            plugin.getLogger().warning("§5[BRÈCHE] Mort de monstre reçue mais événement inactif");
            return;
        }

        // Vérifier que le monstre fait partie de la vague actuelle
        boolean wasInCurrentWave = currentWaveMobs.remove(mob);
        boolean wasInAllSpawned = allSpawnedMobs.remove(mob);

        if (!wasInCurrentWave && !wasInAllSpawned) {
            plugin.getLogger().warning("§5[BRÈCHE] Monstre tué non reconnu (pas dans les listes)");
            return;
        }

        addParticipant(killer);
        lastKillTime = System.currentTimeMillis();

        // Attribution des points
        int points = calculatePoints(mob);
        playerScores.merge(killer.getUniqueId(), points, Integer::sum);
        playerKills.merge(killer.getUniqueId(), 1, Integer::sum);

        // Message au joueur
        killer.sendMessage("§5§l[BRÈCHE] §a+" + points + " points ! §7(Total: " + playerScores.get(killer.getUniqueId()) + ")");
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Effets visuels
        if (mob.getLocation().getWorld() != null) {
            mob.getLocation().getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        }

        // Debug de progression
        plugin.getLogger().info("§5[BRÈCHE] Monstre tué par " + killer.getName() +
                " - Monstres restants vague " + currentWave + ": " + currentWaveMobs.size());

        // Vérifier si la vague est terminée
        if (currentWaveMobs.isEmpty()) {
            plugin.getLogger().info("§5[BRÈCHE] Vague " + currentWave + " terminée - Tous les monstres éliminés");

            if (currentWave >= maxWaves) {
                plugin.getLogger().info("§5[BRÈCHE] Toutes les vagues terminées - Événement réussi !");
                onEventCompleted();
            } else {
                plugin.getLogger().info("§5[BRÈCHE] Passage à la vague suivante");
                onWaveCompleted();
            }
        } else {
            // Log périodique pour le suivi
            int remainingMobs = currentWaveMobs.size();
            if (remainingMobs % 5 == 0 || remainingMobs <= 3) {
                plugin.getLogger().info("§5[BRÈCHE] Progression vague " + currentWave + ": " + remainingMobs + " monstres restants");
            }
        }
    }

    /**
     * Calcule les points pour un kill
     */
    private int calculatePoints(LivingEntity mob) {
        if (mob.hasMetadata("breach_boss")) {
            return 50; // Boss final
        }
        return 5 + ThreadLocalRandom.current().nextInt(16); // 5-20 points pour monstres normaux
    }

    /**
     * Gère la fin d'une vague
     */
    private void onWaveCompleted() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInEventWorld(player)) {
                player.sendTitle("§a§lVAGUE TERMINÉE", "§7Préparez-vous pour la suite...", 10, 60, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        }

        Bukkit.broadcastMessage("§a§l[BRÈCHE] §2VAGUE " + currentWave + " TERMINÉE !");
        Bukkit.broadcastMessage("§7§lVague suivante dans 15 secondes...");

        // Démarrer la vague suivante après 15 secondes
        BukkitTask nextWaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                startNextWave();
            }
        }.runTaskLater(plugin, 300L);
        tasks.add(nextWaveTask);
    }

    /**
     * Gère la réussite de l'événement
     */
    private void onEventCompleted() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInEventWorld(player)) {
                player.sendTitle("§a§lBRÈCHE CONTENUE !", "§2Félicitations héros !", 20, 80, 20);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
            }
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§a§l🎉 BRÈCHE DIMENSIONNELLE CONTENUE ! 🎉");
        Bukkit.broadcastMessage("§2§lLes héros ont triomphé des créatures interdimensionnelles !");
        Bukkit.broadcastMessage("§7§lParticipants: §f" + getParticipantCount());
        showTopScores();
        Bukkit.broadcastMessage("");

        // Fermer la brèche avec effets
        closeBreachEffects();

        // Distribuer les récompenses
        distributeRewards();

        // Terminer l'événement après 30 secondes
        BukkitTask endTask = new BukkitRunnable() {
            @Override
            public void run() {
                forceEnd();
            }
        }.runTaskLater(plugin, 600L);
        tasks.add(endTask);
    }

    /**
     * Affiche le classement des meilleurs scores
     */
    private void showTopScores() {
        List<Map.Entry<UUID, Integer>> topScores = playerScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();

        Bukkit.broadcastMessage("§6§l🏆 TOP 3 DES SCORES 🏆");
        for (int i = 0; i < topScores.size(); i++) {
            UUID playerId = topScores.get(i).getKey();
            Integer score = topScores.get(i).getValue();
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Joueur déconnecté";

            String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
            Bukkit.broadcastMessage(medal + " §e" + name + " §7- §a" + score + " points");
        }
    }

    /**
     * Crée les effets de fermeture de la brèche
     */
    private void closeBreachEffects() {
        if (breachCenter.getWorld() == null) return;

        World world = breachCenter.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, breachCenter, 200, 5, 5, 5, 0.5);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, breachCenter, 100, 3, 3, 3, 0.3);

        for (Player player : world.getPlayers()) {
            player.playSound(breachCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);
        }
    }

    /**
     * Distribue les récompenses selon les performances
     */
    private void distributeRewards() {
        // Calculer les top 3 pour les bonus spéciaux
        List<Map.Entry<UUID, Integer>> sortedScores = playerScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();

        int participantCount = getParticipantCount();
        int top50PercentThreshold = participantCount / 2;

        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null) continue;

            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward();

            // Récompenses de base (participation)
            reward.withBeacons(100 + ThreadLocalRandom.current().nextInt(151)) // 100-250
                    .withTokens(5000 + ThreadLocalRandom.current().nextInt(20001)) // 5k-25k
                    .addItem(prisonHook.createKey("rare"));

            // Ajouter une clé rare supplémentaire
            reward.addItem(prisonHook.createKey("rare"));

            int score = playerScores.getOrDefault(participantId, 0);
            boolean survived = playerSurvived.getOrDefault(participantId, false);

            // Bonus victoire (+100% base + 1 clé lég + 50-100 beacons)
            reward.multiply(2.0)
                    .addItem(prisonHook.createKey("legendary"))
                    .withBeacons(reward.getBeacons() + 50 + ThreadLocalRandom.current().nextInt(51));

            // Calcul position dans le classement
            int position = -1;
            for (int i = 0; i < sortedScores.size(); i++) {
                if (sortedScores.get(i).getKey().equals(participantId)) {
                    position = i;
                    break;
                }
            }

            // Bonus TOP 3
            if (position >= 0 && position < 3) {
                long bonusBeacons = position == 0 ? 1000 : position == 1 ? 750 : 500;
                reward.withBeacons(reward.getBeacons() + bonusBeacons)
                        .addItem(prisonHook.createKey(position == 0 ? "crystal" : "legendary"));

                // Armure P4 U3 pour le top 3
                // Note: Ici on ajouterait la logique pour créer l'armure enchantée
                participant.sendMessage("§6§l[RÉCOMPENSE] §aTOP " + (position + 1) + " ! Bonus exceptionnel !");
            }

            // Calcul réputation
            int reputationGain = 5; // Participation de base

            // Bonus performance (+5 par 500 points)
            reputationGain += (score / 500) * 5;

            // Bonus survie
            if (survived) {
                reputationGain += 5;
            }

            // Bonus top 50%
            if (position >= 0 && position < top50PercentThreshold) {
                reputationGain += 5;
            }

            // Bonus top 3
            if (position >= 0 && position < 3) {
                reputationGain += 20;
            }

            // Limiter à +25 max
            reputationGain = Math.min(25, reputationGain);

            reward.withReputation(reputationGain, "Événement Brèche Dimensionnelle");

            // Donner les récompenses
            prisonHook.giveEventReward(participant, reward);

            // Message détaillé
            participant.sendMessage("§5§l[BRÈCHE] §7Score: §a" + score + " §7| Réputation: §a+" + reputationGain);
        }
    }

    /**
     * Gère la mort d'un joueur (marque comme non-survivant)
     */
    public void onPlayerDeath(Player player) {
        if (participants.contains(player.getUniqueId())) {
            playerSurvived.put(player.getUniqueId(), false);
            player.sendMessage("§c§l[BRÈCHE] §7Vous avez succombé... Bonus de survie perdu !");
        }
    }

    @Override
    protected void onEnd() {
        // Restaurer les effets atmosphériques
        restoreAtmosphericEffects();

        // Nettoyage des monstres restants
        int removedMobs = 0;
        for (LivingEntity mob : allSpawnedMobs) {
            if (mob != null && !mob.isDead()) {
                mob.remove();
                removedMobs++;
            }
        }

        if (removedMobs > 0) {
            plugin.getLogger().info("§5[BRÈCHE] " + removedMobs + " monstres restants supprimés");
        }

        // Nettoyer les effets de tous les joueurs qui étaient dans la zone
        for (UUID playerId : playersInZone) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                clearPlayerEffects(player);
            }
        }

        if (currentWave < maxWaves) {
            Bukkit.broadcastMessage("§c§l[BRÈCHE] §7Temps écoulé ! La brèche se stabilise naturellement...");
            Bukkit.broadcastMessage("§7§lVagues complétées: §c" + currentWave + "§7/§e" + maxWaves);

            // Récompenses réduites pour échec
            distributeFailureRewards();
        }
    }

    /**
     * Distribue des récompenses réduites en cas d'échec
     */
    private void distributeFailureRewards() {
        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null) continue;

            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .withBeacons(50 + ThreadLocalRandom.current().nextInt(76)) // 50-125 (moitié)
                    .withTokens(2500 + ThreadLocalRandom.current().nextInt(10001)) // 2.5k-12.5k (moitié)
                    .withReputation(3, "Tentative Brèche Dimensionnelle"); // Réputation réduite

            int score = playerScores.getOrDefault(participantId, 0);
            if (score > 0) {
                reward.addItem(prisonHook.createKey("common"));
            }

            prisonHook.giveEventReward(participant, reward);
            participant.sendMessage("§7§l[BRÈCHE] §cÉchec - Récompense partielle reçue.");
        }
    }

    @Override
    protected void onCleanup() {
        // Nettoyer toutes les collections
        playerScores.clear();
        playerKills.clear();
        playerSurvived.clear();
        currentWaveMobs.clear();
        allSpawnedMobs.clear();
        playersInZone.clear();

        // Annuler les tâches spécialisées
        if (inactivityTask != null && !inactivityTask.isCancelled()) {
            inactivityTask.cancel();
        }

        if (zoneMonitoringTask != null && !zoneMonitoringTask.isCancelled()) {
            zoneMonitoringTask.cancel();
        }

        // Reset des variables
        currentWave = 0;
        finalBoss = null;
        selectedZone = null;
        breachCenter = null;
        spawnPoint1 = null;
        spawnPoint2 = null;
        eventWorld = null;
        originalTimeWorld = -1;

        plugin.getLogger().info("§5[BRÈCHE] Nettoyage complet terminé");
    }

    // Getters pour monitoring
    public int getCurrentWave() { return currentWave; }
    public int getMaxWaves() { return maxWaves; }
    public int getTotalScore(UUID playerId) { return playerScores.getOrDefault(playerId, 0); }
    public int getPlayerKills(UUID playerId) { return playerKills.getOrDefault(playerId, 0); }
    public Map<UUID, Integer> getAllScores() { return new HashMap<>(playerScores); }
}