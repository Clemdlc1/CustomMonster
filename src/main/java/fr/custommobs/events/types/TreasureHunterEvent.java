package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventConfigManager;
import fr.custommobs.events.EventListener;
import fr.custommobs.managers.BossStatsManager;
import fr.custommobs.mobs.simple.LutinTreasure;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Événement Chasseur de Trésor - Version compétitive favorisant la réputation négative
 * Narratif: Lutin avec trésor à traquer dans la mine
 * Organisation: Hebdomadaire (Vendredi 18h), Durée 15 minutes
 * Mécaniques: Utilise LutinTreasure avec système de score basé sur les dégâts
 */
public class TreasureHunterEvent extends ServerEvent {

    private final EventConfigManager configManager;
    private final BossStatsManager bossStatsManager;

    // Système de score - basé sur les dégâts infligés au lutin
    private final Map<UUID, Double> playerDamageScores = new HashMap<>();
    private final Map<UUID, Integer> playerHitCount = new HashMap<>();
    private final Map<UUID, Boolean> playerCapturedLutin = new HashMap<>();

    // Gestion du lutin trésorier
    private LutinTreasure lutinTreasure;
    private LivingEntity lutinEntity;
    private boolean lutinCaptured = false;
    private UUID lutinKillerId = null;

    // Gestion de la zone - Mine
    private EventConfigManager.EventLocationConfig selectedMineZone;
    private Location mineCenter;
    private Location lutinSpawnPoint;

    // Système de monitoring des joueurs dans la zone
    private final Set<UUID> playersInZone = new HashSet<>();
    private BukkitTask zoneMonitoringTask;
    private final double eventRadius = 30.0; // Rayon plus large pour la mine

    // Système de téléportation automatique après inactivité
    private long lastLutinDamageTime;
    private BukkitTask inactivityTask;
    private static final long INACTIVITY_TIMEOUT = 60000; // 1 minute

    // Gestion des effets atmosphériques de la mine
    private long originalTimeWorld = -1;
    private World eventWorld;

    public TreasureHunterEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook,
                               EventListener.EventRewardsManager rewardsManager, EventConfigManager configManager,
                               BossStatsManager bossStatsManager) {
        super(plugin, prisonHook, rewardsManager, "treasure_hunter", "Chasseur de Trésor",
                EventType.COMPETITIVE, 15 * 60); // 15 minutes

        this.configManager = configManager;
        this.bossStatsManager = bossStatsManager;
        this.lastLutinDamageTime = System.currentTimeMillis();
    }

    @Override
    protected void onStart() {
        // Sélectionner une zone de mine aléatoire
        List<EventConfigManager.EventLocationConfig> mineZones = configManager.getEventLocationConfigs("mine-areas");
        if (mineZones.isEmpty()) {
            plugin.getLogger().severe("§c§l[CHASSEUR] Aucune zone de mine configurée! Événement annulé.");
            forceEnd();
            return;
        }

        selectedMineZone = mineZones.get(ThreadLocalRandom.current().nextInt(mineZones.size()));

        // Créer le centre à partir des coordonnées
        World world = Bukkit.getWorld(selectedMineZone.getWorldName());
        if (world == null) {
            plugin.getLogger().severe("§c§l[CHASSEUR] Monde " + selectedMineZone.getWorldName() + " introuvable! Événement annulé.");
            forceEnd();
            return;
        }

        mineCenter = new Location(world, selectedMineZone.getCenterX(), selectedMineZone.getCenterY(), selectedMineZone.getCenterZ());
        eventWorld = world;

        if (eventWorld == null) {
            plugin.getLogger().severe("§c§l[CHASSEUR] Monde de la mine indisponible! Événement annulé.");
            forceEnd();
            return;
        }

        // Déterminer le point de spawn du lutin (aléatoire dans la zone)
        lutinSpawnPoint = generateRandomLocationInMine();

        // Créer l'atmosphère de la mine
        setupMineAtmosphere();

        // Spawn du Lutin Trésorier
        spawnLutinTreasure();

        // Démarrer les systèmes de monitoring
        startZoneMonitoring();
        startInactivityMonitoring();

        // Annonce spectaculaire
        announceTreasureHunt();

        plugin.getLogger().info("§6§l[CHASSEUR] Événement démarré dans la zone: " + selectedMineZone.getDisplayName());
        plugin.getLogger().info("§6§l[CHASSEUR] Lutin spawné à: " + lutinSpawnPoint);
    }

    /**
     * Configure l'atmosphère sombre et mystérieuse de la mine
     */
    private void setupMineAtmosphere() {
        // Sauvegarder le temps original
        originalTimeWorld = eventWorld.getTime();

        // Mettre la nuit pour l'ambiance
        eventWorld.setTime(18000); // Minuit
        eventWorld.setStorm(false);
        eventWorld.setThundering(false);

        // Effets atmosphériques dans la zone
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive()) {
                    cancel();
                    return;
                }

                // Particules mystérieuses dans la mine
                for (int i = 0; i < 10; i++) {
                    Location randomLoc = generateRandomLocationInMine();
                    if (randomLoc != null) {
                        eventWorld.spawnParticle(Particle.PORTAL, randomLoc, 3, 1, 1, 1, 0.1);
                        eventWorld.spawnParticle(Particle.ENCHANT, randomLoc, 5, 2, 2, 2, 0.2);
                    }
                }

                // Sons d'ambiance occasionnels
                if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                    eventWorld.playSound(mineCenter, Sound.AMBIENT_CAVE, 0.5f, 0.8f);
                }
            }
        }.runTaskTimer(plugin, 20L, 100L); // Toutes les 5 secondes
    }

    /**
     * Spawn du Lutin Trésorier avec annonce
     */
    private void spawnLutinTreasure() {
        try {
            lutinTreasure = new LutinTreasure(plugin);
            lutinEntity = lutinTreasure.spawn(lutinSpawnPoint);

            if (lutinEntity == null) {
                plugin.getLogger().severe("§c§l[CHASSEUR] Impossible de spawner le Lutin Trésorier!");
                forceEnd();
                return;
            }

            // Métadonnées pour identification
            lutinEntity.setMetadata("treasure_hunter_lutin", new FixedMetadataValue(plugin, true));
            lutinEntity.setMetadata("event_mob", new FixedMetadataValue(plugin, true));

            // Nom spécial pour l'événement
            lutinEntity.setCustomName("§6§l✦ §e§lLutin Trésorier §6§l✦");
            lutinEntity.setCustomNameVisible(true);

            // Effets visuels de spawn
            eventWorld.spawnParticle(Particle.TOTEM_OF_UNDYING, lutinSpawnPoint, 100, 3, 3, 3, 0.3);
            eventWorld.spawnParticle(Particle.FIREWORK, lutinSpawnPoint, 50, 2, 2, 2, 0.2);

            plugin.getLogger().info("§6§l[CHASSEUR] Lutin Trésorier spawné avec succès!");

        } catch (Exception e) {
            plugin.getLogger().severe("§c§l[CHASSEUR] Erreur lors du spawn du Lutin: " + e.getMessage());
            e.printStackTrace();
            forceEnd();
        }
    }

    /**
     * Génère une location aléatoire dans la zone de mine
     */
    private Location generateRandomLocationInMine() {
        if (selectedMineZone == null || eventWorld == null) return null;

        for (int attempt = 0; attempt < 15; attempt++) {
            // Utiliser le système de coordonnées centrées avec rayon
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = ThreadLocalRandom.current().nextDouble() * selectedMineZone.getRadius();

            double x = selectedMineZone.getCenterX() + Math.cos(angle) * distance;
            double z = selectedMineZone.getCenterZ() + Math.sin(angle) * distance;
            double y = selectedMineZone.getMinY() + ThreadLocalRandom.current().nextDouble() *
                    (selectedMineZone.getMaxY() - selectedMineZone.getMinY());

            Location testLoc = new Location(eventWorld, x, y, z);

            // Vérifier que la location est sûre
            if (testLoc.getBlock().getType().isAir() &&
                    testLoc.add(0, 1, 0).getBlock().getType().isAir()) {
                return testLoc;
            }
        }

        return mineCenter.clone().add(0, 5, 0); // Fallback
    }

    /**
     * Annonce spectaculaire du début de la chasse
     */
    private void announceTreasureHunt() {
        String[] announcements = {
                "",
                "§6§l╔══════════════════════════════════════╗",
                "§6§l║          §e§lCHASSEUR DE TRÉSOR          §6§l║",
                "§6§l╠══════════════════════════════════════╣",
                "§6§l║                                      §6§l║",
                "§6§l║ §f Un §e§lLutin Trésorier §f§ls'est échappé   §6§l║",
                "§6§l║ §f de la mine avec un trésor précieux ! §6§l║",
                "§6§l║                                      §6§l║",
                "§6§l║ §a§l➤ §f§lObjectif: §c§lTraquez et capturez-le §6§l║",
                "§6§l║ §a§l➤ §f§lZone: §e" + selectedMineZone.getDisplayName() + "            §6§l║",
                "§6§l║ §a§l➤ §f§lDurée: §c§l15 minutes              §6§l║",
                "§6§l║                                      §6§l║",
                "§6§l║ §c§l⚡ Plus votre réputation est négative, §6§l║",
                "§6§l║ §c§l   plus vous êtes avantagé !          §6§l║",
                "§6§l║                                      §6§l║",
                "§6§l╚══════════════════════════════════════╝",
                ""
        };

        for (String line : announcements) {
            Bukkit.broadcastMessage(line);
        }

        // Son d'annonce pour tous les joueurs
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }

    /**
     * Démarre le monitoring de la zone
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
     * Met à jour la liste des joueurs dans la zone et applique les effets de réputation
     */
    private void updatePlayersInZone() {
        if (eventWorld == null || mineCenter == null) return;

        Set<UUID> currentPlayersInZone = new HashSet<>();

        for (Player player : eventWorld.getPlayers()) {
            double distance = player.getLocation().distance(mineCenter);
            if (distance <= eventRadius) {
                currentPlayersInZone.add(player.getUniqueId());

                // Nouveau joueur entrant dans la zone
                if (!playersInZone.contains(player.getUniqueId())) {
                    onPlayerEnterMine(player);
                }
            }
        }

        // Joueurs qui ont quitté la zone
        for (UUID playerId : playersInZone) {
            if (!currentPlayersInZone.contains(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    onPlayerLeaveMine(player);
                }
            }
        }

        playersInZone.clear();
        playersInZone.addAll(currentPlayersInZone);
    }

    /**
     * Gère l'entrée d'un joueur dans la mine
     */
    private void onPlayerEnterMine(Player player) {
        addParticipant(player);

        // Appliquer les effets de réputation (favorise la réputation négative)
        applyReputationEffects(player);

        player.sendMessage("§6§l[CHASSEUR] §7Vous entrez dans la mine maudite !");
        player.sendMessage("§6§l[CHASSEUR] §e⚡ Traquez le Lutin Trésorier et infligez-lui des dégâts !");
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 0.8f);

        // Effets visuels d'entrée
        player.spawnParticle(Particle.ENCHANT, player.getLocation(), 30, 1, 2, 1, 0.3);
    }

    /**
     * Gère la sortie d'un joueur de la mine
     */
    private void onPlayerLeaveMine(Player player) {
        clearPlayerEffects(player);

        player.sendMessage("§7§l[CHASSEUR] §8Vous quittez la mine...");
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_STEP, 0.5f, 1.2f);
    }

    /**
     * Applique les effets de réputation - FAVORISE LA RÉPUTATION NÉGATIVE
     */
    private void applyReputationEffects(Player player) {
        ReputationTier reputation = prisonHook.getReputationLevel(player);

        // Nettoyer les anciens effets
        clearPlayerEffects(player);

        switch (reputation) {
            case INFAME:
            case CRIMINEL:
            case SUSPECT:
                // Réputation NÉGATIVE : Avantages complets (vitesse, perception, vision normale)
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 0, false, false));
                player.sendMessage("§c§l[CHASSEUR] §a⚡ Votre réputation criminelle vous donne tous les avantages !");
                break;

            case ORDINAIRE:
                // Réputation NEUTRE : Malus léger (-10% vision)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, true));
                player.sendMessage("§7§l[CHASSEUR] §e⚡ Réputation neutre : léger désavantage de vision");
                break;

            case RESPECTE:
                // Réputation POSITIVE faible : Malus modéré (-20% vision)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, true));
                player.sendMessage("§a§l[CHASSEUR] §c⚡ Votre bonne réputation vous handicape modérément");
                break;

            case HONORABLE:
            case EXEMPLAIRE:
                // Réputation POSITIVE élevée : Malus sévères (-30% à -50% vision, lenteur)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 0, false, true));
                player.sendMessage("§2§l[CHASSEUR] §4⚡ Votre excellente réputation vous handicape sévèrement !");
                break;
        }
    }

    /**
     * Retire tous les effets de l'événement d'un joueur
     */
    private void clearPlayerEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    /**
     * Démarre le monitoring d'inactivité
     */
    private void startInactivityMonitoring() {
        inactivityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive() || lutinEntity == null || lutinEntity.isDead()) {
                    cancel();
                    return;
                }

                long timeSinceLastDamage = System.currentTimeMillis() - lastLutinDamageTime;

                if (timeSinceLastDamage > INACTIVITY_TIMEOUT) {
                    teleportLutinToActiveArea();
                    lastLutinDamageTime = System.currentTimeMillis();
                }
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // Vérifier toutes les 30 secondes

        tasks.add(inactivityTask);
    }

    /**
     * Téléporte le lutin vers une zone avec des joueurs actifs
     */
    private void teleportLutinToActiveArea() {
        if (lutinEntity == null || lutinEntity.isDead()) return;

        List<Player> playersInMine = new ArrayList<>();
        for (UUID playerId : playersInZone) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                playersInMine.add(player);
            }
        }

        if (playersInMine.isEmpty()) {
            // Aucun joueur dans la mine, téléporter aléatoirement
            Location newLoc = generateRandomLocationInMine();
            if (newLoc != null) {
                lutinEntity.teleport(newLoc);
            }
            return;
        }

        // Choisir un joueur aléatoire et téléporter le lutin près de lui
        Player targetPlayer = playersInMine.get(ThreadLocalRandom.current().nextInt(playersInMine.size()));
        Location playerLoc = targetPlayer.getLocation();

        // Téléporter à une distance sûre (15-25 blocs)
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = 15 + ThreadLocalRandom.current().nextDouble() * 10;

        Location teleportLoc = playerLoc.clone().add(
                Math.cos(angle) * distance,
                ThreadLocalRandom.current().nextInt(5) + 2,
                Math.sin(angle) * distance
        );

        lutinEntity.teleport(teleportLoc);

        // Annonce avec effets
        Bukkit.broadcastMessage("§6§l[CHASSEUR] §c⚡ Le Lutin Trésorier se déplace vers une nouvelle cachette !");
        eventWorld.spawnParticle(Particle.PORTAL, teleportLoc, 50, 2, 2, 2, 0.3);
        eventWorld.playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.8f);
    }

    /**
     * Méthode appelée quand le lutin reçoit des dégâts
     */
    public void onLutinDamaged(Player damager, double damage) {
        if (damager == null || !isActive()) return;

        UUID playerId = damager.getUniqueId();

        // Mettre à jour le score (basé sur les dégâts)
        playerDamageScores.merge(playerId, damage, Double::sum);
        playerHitCount.merge(playerId, 1, Integer::sum);

        // Mettre à jour le temps de dernière activité
        lastLutinDamageTime = System.currentTimeMillis();

        // Message de feedback au joueur
        damager.sendMessage("§6§l[CHASSEUR] §a+" + String.format("%.1f", damage) + " dégâts ! " +
                "§7(Total: §e" + String.format("%.1f", playerDamageScores.get(playerId)) + "§7)");

        damager.playSound(damager.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);

        plugin.getLogger().fine("§6[CHASSEUR] " + damager.getName() + " a infligé " + damage + " dégâts au lutin");
    }

    /**
     * Méthode appelée quand le lutin est tué/capturé
     */
    public void onLutinCaptured(Player captor) {
        if (captor == null || !isActive() || lutinCaptured) return;

        lutinCaptured = true;
        lutinKillerId = captor.getUniqueId();

        // Marquer le captureur
        playerCapturedLutin.put(captor.getUniqueId(), true);

        // Annonce dramatique
        String[] captureAnnouncement = {
                "",
                "§6§l╔══════════════════════════════════════╗",
                "§6§l║       §c§l🏆 LUTIN CAPTURÉ ! 🏆         §6§l║",
                "§6§l╠══════════════════════════════════════╣",
                "§6§l║                                      §6§l║",
                "§6§l║ §a" + captor.getName() + " §fa capturé le §e§lLutin Trésorier §6§l║",
                "§6§l║                                      §6§l║",
                "§6§l╚══════════════════════════════════════╝",
                ""
        };

        for (String line : captureAnnouncement) {
            Bukkit.broadcastMessage(line);
        }

        // Effets spectaculaires de capture
        Location lutinLoc = lutinEntity.getLocation();
        eventWorld.spawnParticle(Particle.TOTEM_OF_UNDYING, lutinLoc, 200, 5, 5, 5, 0.5);
        eventWorld.spawnParticle(Particle.FIREWORK, lutinLoc, 100, 3, 3, 3, 0.3);

        for (Player player : eventWorld.getPlayers()) {
            player.playSound(lutinLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
            player.playSound(lutinLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.2f);
        }
        forceEnd();

        plugin.getLogger().info("§6§l[CHASSEUR] Lutin capturé par " + captor.getName() + " !");
    }

    @Override
    protected void onEnd() {
        // Nettoyer les effets atmosphériques
        if (originalTimeWorld != -1 && eventWorld != null) {
            eventWorld.setTime(originalTimeWorld);
        }

        // Nettoyer les effets des joueurs
        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null) {
                clearPlayerEffects(participant);
            }
        }

        // Supprimer le lutin s'il existe encore
        if (lutinEntity != null && !lutinEntity.isDead()) {
            lutinEntity.remove();
        }

        // Calculer et distribuer les récompenses
        distributeRewards();

        // Afficher le classement final
        displayFinalRanking();

        // Annonce de fin
        Bukkit.broadcastMessage("§6§l[CHASSEUR] §7La chasse au trésor se termine !");
        Bukkit.broadcastMessage("§6§l[CHASSEUR] §eMerci à tous les participants !");

        plugin.getLogger().info("§6§l[CHASSEUR] Événement terminé avec " + getParticipantCount() + " participants");
    }

    /**
     * Affiche le classement final basé sur les dégâts
     */
    private void displayFinalRanking() {
        if (playerDamageScores.isEmpty()) {
            Bukkit.broadcastMessage("§6§l[CHASSEUR] §7Aucun participant n'a infligé de dégâts.");
            return;
        }

        List<Map.Entry<UUID, Double>> sortedScores = playerDamageScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(5)
                .toList();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l════════ CLASSEMENT FINAL ════════");

        for (int i = 0; i < sortedScores.size(); i++) {
            Map.Entry<UUID, Double> entry = sortedScores.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            String name = player != null ? player.getName() : "Joueur déconnecté";
            double damage = entry.getValue();

            String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§e" + (i + 1) + ".";
            String captureBonus = playerCapturedLutin.getOrDefault(entry.getKey(), false) ? " §a[CAPTURÉ]" : "";

            Bukkit.broadcastMessage(medal + " §e" + name + " §7- §c" + String.format("%.1f", damage) + " dégâts" + captureBonus);
        }

        Bukkit.broadcastMessage("§6§l════════════════════════════════");
        Bukkit.broadcastMessage("");
    }

    /**
     * Distribue les récompenses selon les performances
     */
    private void distributeRewards() {
        if (getParticipants().isEmpty()) return;

        // Calculer le top 3 pour les récompenses spéciales
        List<Map.Entry<UUID, Double>> sortedScores = playerDamageScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();

        for (UUID participantId : getParticipants()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null) continue;

            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward();

            // Récompenses universelles de participation
            int baseBeacons = 50 + ThreadLocalRandom.current().nextInt(201); // 50-250
            long baseTokens = 2500 + ThreadLocalRandom.current().nextLong(7501); // 2.5k-10k

            reward.withBeacons(baseBeacons)
                    .withTokens(baseTokens)
                    .addItem(prisonHook.createKey("rare"));

            double playerDamage = playerDamageScores.getOrDefault(participantId, 0.0);
            boolean capturedLutin = playerCapturedLutin.getOrDefault(participantId, false);

            // Bonus de capture (+100% récompenses de base, clé légendaire possible, +50 beacons)
            if (capturedLutin) {
                reward.multiply(2.0) // Double les récompenses de base
                        .withBeacons(reward.getBeacons() + 50);

                // Chance de clé légendaire
                if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                    reward.addItem(prisonHook.createKey("legendary"));
                }
            }

            // Calculer la position du joueur
            int position = -1;
            for (int i = 0; i < sortedScores.size(); i++) {
                if (sortedScores.get(i).getKey().equals(participantId)) {
                    position = i;
                    break;
                }
            }

            // Récompenses TOP 3
            if (position >= 0 && position < 3) {
                long bonusBeacons = position == 0 ? 750 : position == 1 ? 500 : 250;
                String keyType = position == 0 ? "crystal" : "legendary";

                reward.withBeacons(reward.getBeacons() + bonusBeacons)
                        .addItem(prisonHook.createKey(keyType));
            }

            // Impact sur la réputation (système compétitif négatif)
            int reputationChange = calculateReputationChange(participantId, playerDamage, position, capturedLutin);
            if (reputationChange != 0) {
                reward.withReputation(reputationChange, "Chasseur de Trésor");
            }

            // Donner les récompenses
            prisonHook.giveEventReward(participant, reward);

            // Message personnalisé
            participant.sendMessage("§6§l[CHASSEUR] §aVous avez infligé §e" + String.format("%.1f", playerDamage) + " dégâts §aau lutin !");
            if (capturedLutin) {
                participant.sendMessage("§6§l[CHASSEUR] §a🏆 Bonus de capture obtenu !");
            }
        }
    }

    /**
     * Calcule le changement de réputation pour un joueur
     */
    private int calculateReputationChange(UUID playerId, double damage, int position, boolean captured) {
        int reputationChange = 0;

        // Participation de base : -5 réputation (encourage la participation)
        reputationChange -= 5;

        // Performance basée sur les dégâts : -5 par tranche de 250 points de dégâts
        reputationChange -= (int) (damage / 250) * 5;

        // Bonus pour celui qui capture : -5 réputation supplémentaire
        if (captured) {
            reputationChange -= 5;
        }

        // Bonus TOP 3 : -20 réputation supplémentaire
        if (position >= 0 && position < 3) {
            reputationChange -= 20;
        }

        // Limite maximale : -25 réputation par événement
        return Math.max(reputationChange, -25);
    }

    @Override
    protected void onCleanup() {
        // Nettoyer les données de l'événement
        playerDamageScores.clear();
        playerHitCount.clear();
        playerCapturedLutin.clear();
        playersInZone.clear();

        // Réinitialiser les variables
        lutinTreasure = null;
        lutinEntity = null;
        lutinCaptured = false;
        lutinKillerId = null;
        selectedMineZone = null;
        mineCenter = null;
        lutinSpawnPoint = null;
        eventWorld = null;
        originalTimeWorld = -1;
    }

    // Getters pour les statistiques
    public Map<UUID, Double> getPlayerDamageScores() {
        return new HashMap<>(playerDamageScores);
    }

    public boolean isLutinCaptured() {
        return lutinCaptured;
    }

    public UUID getLutinKillerId() {
        return lutinKillerId;
    }
}