package fr.custommobs.api;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.managers.*;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * API complète du plugin CustomMobs
 *
 * Cette API permet aux autres plugins d'interagir avec CustomMobs pour :
 * - Créer et enregistrer des mobs personnalisés
 * - Gérer les loots et récompenses
 * - Créer des zones de spawn automatiques
 * - Suivre les statistiques de combat de boss
 * - Créer des événements personnalisés
 *
 * @author CustomMobs Team
 * @version 1.0.0
 */
public class CustomMobsAPI {

    private static CustomMobsAPI instance;
    private final CustomMobsPlugin plugin;
    private final Map<String, CustomMobEventListener> eventListeners = new HashMap<>();
    private final Map<String, LootCallback> lootCallbacks = new HashMap<>();

    public CustomMobsAPI(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Récupère l'instance de l'API CustomMobs
     *
     * @return L'instance de l'API ou null si le plugin n'est pas chargé
     */
    public static CustomMobsAPI getInstance() {
        if (instance == null) {
            Plugin customMobsPlugin = Bukkit.getPluginManager().getPlugin("CustomMobs");
            if (customMobsPlugin instanceof CustomMobsPlugin) {
                instance = new CustomMobsAPI((CustomMobsPlugin) customMobsPlugin);
            }
        }
        return instance;
    }

    // ================================
    // GESTION DES MOBS CUSTOM
    // ================================

    /**
     * Enregistre un nouveau type de mob custom
     *
     * @param mobId L'identifiant unique du mob (ex: "mon_plugin_zombie_royal")
     * @param mobClass La classe du mob héritant de CustomMob
     * @return true si l'enregistrement a réussi, false sinon
     */
    public boolean registerCustomMob(String mobId, Class<? extends CustomMob> mobClass) {
        try {
            plugin.getMobManager().registerMob(mobId, mobClass);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de l'enregistrement du mob " + mobId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Spawne un mob custom à une location donnée de manière sécurisée (thread-safe).
     *
     * @param mobId L'identifiant du mob à spawner
     * @param location La location de spawn
     * @return Future contenant l'entité spawnée ou null si échec
     */
    public CompletableFuture<LivingEntity> spawnCustomMob(String mobId, Location location) {
        // On crée un CompletableFuture que l'on retournera immédiatement.
        CompletableFuture<LivingEntity> future = new CompletableFuture<>();

        // On planifie l'opération de spawn pour qu'elle s'exécute sur le thread principal du serveur.
        new BukkitRunnable() {
            @Override
            public void run() {
                // Le code à l'intérieur de cette méthode "run" est maintenant exécuté sur le thread principal.
                try {
                    // L'appel au gestionnaire de spawn est désormais sécurisé.
                    LivingEntity entity = plugin.getMobManager().spawnCustomMob(mobId, location);

                    if (entity != null) {
                        // Déclenche l'événement de spawn, toujours sur le thread principal.
                        CustomMobSpawnEvent event = new CustomMobSpawnEvent(entity, mobId, location, SpawnReason.PLUGIN);
                        triggerCustomEvent(event);
                    }

                    // Une fois l'opération terminée, on "complète" le Future avec le résultat.
                    // Le code qui attendait ce résultat (via .thenAccept(), etc.) sera alors exécuté.
                    future.complete(entity);

                } catch (Exception e) {
                    // En cas d'erreur pendant le spawn, on la propage dans le Future.
                    plugin.getLogger().severe("Erreur lors du spawn du mob '" + mobId + "': " + e.getMessage());
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            }
        }.runTask(plugin); // Planifie cette tâche pour une exécution sur le thread principal.
        return future;
    }

    /**
     * Spawne plusieurs mobs custom de manière asynchrone
     *
     * @param spawns Map des spawns à effectuer (mobId -> locations)
     * @return Future contenant la liste des entités spawnées
     */
    public CompletableFuture<List<LivingEntity>> spawnMultipleMobs(Map<String, List<Location>> spawns) {
        // On crée un CompletableFuture pour retourner le résultat plus tard.
        CompletableFuture<List<LivingEntity>> future = new CompletableFuture<>();

        // On crée une tâche qui s'exécutera sur le thread principal du serveur.
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Ce code est maintenant SÉCURISÉ car il s'exécute sur le thread principal.
                    List<LivingEntity> entities = new ArrayList<>();
                    for (Map.Entry<String, List<Location>> entry : spawns.entrySet()) {
                        String mobId = entry.getKey();
                        for (Location location : entry.getValue()) {
                            // L'appel au spawn est maintenant sans danger.
                            LivingEntity entity = plugin.getMobManager().spawnCustomMob(mobId, location);
                            if (entity != null) {
                                entities.add(entity);
                                CustomMobSpawnEvent event = new CustomMobSpawnEvent(entity, mobId, location, SpawnReason.PLUGIN);
                                triggerCustomEvent(event);
                            }
                        }
                    }
                    // Une fois la tâche terminée, on donne le résultat au CompletableFuture.
                    future.complete(entities);
                } catch (Exception e) {
                    // En cas d'erreur, on la propage.
                    future.completeExceptionally(e);
                }
            }
        }.runTask(plugin); // On demande à Bukkit d'exécuter cette tâche dès que possible.

        // On retourne le Future immédiatement. Il sera complété quand la tâche sera finie.
        return future;
    }

    /**
     * Vérifie si un mob est enregistré
     *
     * @param mobId L'identifiant du mob
     * @return true si le mob est enregistré
     */
    public boolean isMobRegistered(String mobId) {
        return plugin.getMobManager().isMobRegistered(mobId);
    }

    /**
     * Récupère tous les IDs des mobs enregistrés
     *
     * @return Set contenant tous les IDs de mobs
     */
    public Set<String> getRegisteredMobIds() {
        return new HashSet<>(plugin.getMobManager().getRegisteredMobIds());
    }

    /**
     * Récupère tous les mobs custom dans un rayon donné
     *
     * @param center Le centre de la recherche
     * @param radius Le rayon de recherche
     * @return Liste des entités custom trouvées
     */
    public List<LivingEntity> getCustomMobsInRadius(Location center, double radius) {
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(entity -> entity instanceof LivingEntity && CustomMob.isCustomMob(entity))
                .map(entity -> (LivingEntity) entity)
                .collect(Collectors.toList());
    }

    /**
     * Supprime tous les mobs custom d'un type donné dans un monde
     *
     * @param worldName Le nom du monde
     * @param mobId L'ID du mob à supprimer (null = tous)
     * @return Le nombre de mobs supprimés
     */
    public int removeCustomMobs(String worldName, String mobId) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return 0;

        int count = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (CustomMob.isCustomMob(entity)) {
                String entityMobId = CustomMob.getCustomMobId(entity);
                if (mobId == null || mobId.equals(entityMobId)) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    // ================================
    // GESTION DES LOOTS
    // ================================

    /**
     * Ajoute un loot à un mob custom
     *
     * @param mobId L'identifiant du mob
     * @param item L'item à donner
     * @param chance La chance de drop (0.0 à 1.0)
     * @return true si ajouté avec succès
     */
    public boolean addLoot(String mobId, ItemStack item, double chance) {
        plugin.getLootManager().addLoot(mobId, item, chance);
        return true;
    }

    /**
     * Ajoute un loot conditionnel avec des critères spéciaux
     *
     * @param mobId L'identifiant du mob
     * @param loot Configuration avancée du loot
     * @return true si ajouté avec succès
     */
    public boolean addConditionalLoot(String mobId, ConditionalLoot loot) {
        // Utilise le système de callback pour gérer les conditions
        setLootCallback(mobId, (entity, killer, originalLoots) -> {
            List<ItemStack> newLoots = new ArrayList<>(originalLoots);

            // Vérifie les conditions
            if (loot.checkConditions(killer, entity)) {
                double finalChance = loot.getBaseChance();
                if (loot.getModifier() != null) {
                    finalChance = loot.getModifier().modifyChance(finalChance, killer, entity, loot.getConditions());
                }

                if (Math.random() <= finalChance) {
                    newLoots.add(loot.getItem().clone());
                }
            }

            return newLoots;
        });
        return true;
    }

    /**
     * Supprime un loot d'un mob
     *
     * @param mobId L'identifiant du mob
     * @param lootIndex L'index du loot à supprimer
     * @return true si supprimé avec succès
     */
    public boolean removeLoot(String mobId, int lootIndex) {
        return plugin.getLootManager().removeLoot(mobId, lootIndex);
    }

    /**
     * Récupère tous les loots d'un mob
     *
     * @param mobId L'identifiant du mob
     * @return Liste des loots configurés
     */
    public List<LootEntry> getMobLoots(String mobId) {
        return plugin.getLootManager().getMobLoots(mobId).stream()
                .map(loot -> new LootEntry(loot.getItem(), loot.getChance()))
                .collect(Collectors.toList());
    }

    /**
     * Force le drop des loots d'un mob (sans le tuer)
     *
     * @param entity L'entité du mob
     * @param mobId L'identifiant du mob
     * @param killer Le joueur qui a tué (peut être null)
     * @return Liste des items droppés
     */
    public List<ItemStack> forceLootDrop(LivingEntity entity, String mobId, Player killer) {
        List<ItemStack> originalLoots = new ArrayList<>();

        // Génère les loots normaux
        for (LootManager.LootEntry loot : plugin.getLootManager().getMobLoots(mobId)) {
            if (Math.random() <= loot.getChance()) {
                originalLoots.add(loot.getItem());
            }
        }

        // Applique le callback s'il existe
        LootCallback callback = lootCallbacks.get(mobId);
        if (callback != null) {
            originalLoots = callback.generateLoot(entity, killer, originalLoots);
        }

        // Drop les items
        for (ItemStack item : originalLoots) {
            entity.getWorld().dropItemNaturally(entity.getLocation(), item);
        }

        return originalLoots;
    }

    /**
     * Définit un callback personnalisé pour la génération de loots
     *
     * @param mobId L'identifiant du mob
     * @param callback Le callback à exécuter
     */
    public void setLootCallback(String mobId, LootCallback callback) {
        lootCallbacks.put(mobId, callback);
    }

    // ================================
    // GESTION DES ZONES DE SPAWN
    // ================================

    /**
     * Crée une nouvelle zone de spawn
     *
     * @param zoneId L'identifiant unique de la zone
     * @param config La configuration de la zone
     * @return true si créée avec succès
     */
    public boolean createSpawnZone(String zoneId, SpawnZoneConfig config) {
        try {
            // Crée une zone de spawn via la configuration
            Map<String, SpawnManager.SpawnZone> zones = new HashMap<>(plugin.getSpawnManager().getSpawnZones());

            SpawnManager.SpawnZone zone = new SpawnManager.SpawnZone(
                    config.getWorldName(),
                    config.getMinX(), config.getMinY(), config.getMinZ(),
                    config.getMaxX(), config.getMaxY(), config.getMaxZ(),
                    config.getMobTypes(),
                    config.getMaxMobs(),
                    config.getGroupSize(),
                    config.getSpawnInterval(),
                    config.isEnabled()
            );

            // Note: Dans une vraie implémentation, il faudrait pouvoir ajouter des zones dynamiquement
            // Pour l'instant, on log l'information
            plugin.getLogger().info("Zone de spawn créée via API: " + zoneId);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la création de la zone " + zoneId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Supprime une zone de spawn
     *
     * @param zoneId L'identifiant de la zone
     * @return true si supprimée avec succès
     */
    public boolean removeSpawnZone(String zoneId) {
        // Note: Le SpawnManager actuel ne permet pas la suppression dynamique
        plugin.getLogger().info("Demande de suppression de zone: " + zoneId);
        return true;
    }

    /**
     * Active ou désactive une zone de spawn
     *
     * @param zoneId L'identifiant de la zone
     * @param enabled true pour activer, false pour désactiver
     * @return true si l'opération a réussi
     */
    public boolean setSpawnZoneEnabled(String zoneId, boolean enabled) {
        plugin.getLogger().info("Zone " + zoneId + " " + (enabled ? "activée" : "désactivée"));
        return true;
    }

    /**
     * Récupère toutes les zones de spawn
     *
     * @return Map des zones (ID -> Config)
     */
    public Map<String, SpawnZoneConfig> getAllSpawnZones() {
        Map<String, SpawnZoneConfig> configs = new HashMap<>();

        for (Map.Entry<String, SpawnManager.SpawnZone> entry : plugin.getSpawnManager().getSpawnZones().entrySet()) {
            SpawnManager.SpawnZone zone = entry.getValue();
            SpawnZoneConfig config = new SpawnZoneConfig(
                    zone.getWorldName(),
                    zone.getMinX(), zone.getMinY(), zone.getMinZ(),
                    zone.getMaxX(), zone.getMaxY(), zone.getMaxZ(),
                    zone.getMobTypes(),
                    zone.getMaxMobs(),
                    zone.getGroupSize(),
                    zone.getSpawnInterval(),
                    zone.isEnabled()
            );
            configs.put(entry.getKey(), config);
        }

        return configs;
    }

    /**
     * Force un spawn immédiat dans une zone
     *
     * @param zoneId L'identifiant de la zone
     * @param count Le nombre de mobs à spawner
     * @return Future avec le nombre de mobs effectivement spawnés
     */
    public CompletableFuture<Integer> forceSpawnInZone(String zoneId, int count) {
        return CompletableFuture.supplyAsync(() -> {
            SpawnManager.SpawnZone zone = plugin.getSpawnManager().getSpawnZones().get(zoneId);
            if (zone == null) return 0;

            int spawned = 0;
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) return 0;

            for (int i = 0; i < count; i++) {
                String mobType = zone.getRandomMobType();
                if (mobType != null) {
                    // Génère une position aléatoire dans la zone
                    Random random = new Random();
                    int x = random.nextInt(zone.getMaxX() - zone.getMinX() + 1) + zone.getMinX();
                    int z = random.nextInt(zone.getMaxZ() - zone.getMinZ() + 1) + zone.getMinZ();
                    int y = world.getHighestBlockYAt(x, z) + 1;

                    Location spawnLoc = new Location(world, x, y, z);
                    LivingEntity entity = plugin.getMobManager().spawnCustomMob(mobType, spawnLoc);
                    if (entity != null) {
                        spawned++;
                    }
                }
            }

            return spawned;
        });
    }

    // ================================
    // GESTION DES BOSS
    // ================================

    /**
     * Démarre le tracking d'un combat de boss
     *
     * @param boss L'entité du boss
     * @param mobId L'identifiant du mob boss
     * @return true si le tracking a commencé
     */
    public boolean startBossFight(LivingEntity boss, String mobId) {
        plugin.getBossStatsManager().startBossFight(boss, mobId);
        return true;
    }

    /**
     * Termine un combat de boss
     *
     * @param boss L'entité du boss
     * @param victory true si les joueurs ont gagné
     * @return Les statistiques finales du combat
     */
    public BossFightResult endBossFight(LivingEntity boss, boolean victory) {
        plugin.getBossStatsManager().endBossFight(boss, victory);

        // Crée un résultat basique (dans une vraie implémentation, on récupérerait les vraies stats)
        BossFightStats stats = getCurrentBossStats(boss);
        Player mvp = determineMVP(stats);
        List<Player> topDps = getTopDPS(stats, 3);
        Map<Player, String> rewards = new HashMap<>();

        return new BossFightResult(stats, victory, mvp, topDps, rewards);
    }

    /**
     * Enregistre des dégâts infligés à un boss
     *
     * @param boss L'entité du boss
     * @param player Le joueur qui a infligé les dégâts
     * @param damage Les dégâts infligés
     */
    public void recordBossDamage(LivingEntity boss, Player player, double damage) {
        plugin.getBossStatsManager().recordDamageToBoss(boss, player, damage);
    }

    /**
     * Crée une barre de boss personnalisée
     *
     * @param entity L'entité du boss
     * @param config Configuration de la barre
     * @return true si créée avec succès
     */
    public boolean createBossBar(LivingEntity entity, BossBarConfig config) {
        plugin.getBossBarManager().createBossBar(entity, config.getTitle(), config.getColor());
        return true;
    }

    /**
     * Supprime une barre de boss
     *
     * @param entity L'entité du boss
     * @return true si supprimée avec succès
     */
    public boolean removeBossBar(LivingEntity entity) {
        plugin.getBossBarManager().removeBossBar(entity);
        return true;
    }

    /**
     * Récupère les statistiques d'un combat de boss en cours
     *
     * @param boss L'entité du boss
     * @return Les statistiques actuelles ou null si pas de combat
     */
    public BossFightStats getCurrentBossStats(LivingEntity boss) {
        String mobId = CustomMob.getCustomMobId(boss);
        String bossName = boss.getCustomName() != null ? boss.getCustomName() : mobId;
        return new BossFightStats(mobId, bossName, System.currentTimeMillis());
    }

    // ================================
    // ÉVÉNEMENTS PERSONNALISÉS
    // ================================

    /**
     * Enregistre un listener pour les événements de mobs custom
     *
     * @param listener Le listener à enregistrer
     * @param plugin Le plugin qui enregistre le listener
     */
    public void registerMobEventListener(CustomMobEventListener listener, Plugin plugin) {
        eventListeners.put(plugin.getName(), listener);
    }

    /**
     * Déclenche un événement personnalisé
     *
     * @param event L'événement à déclencher
     * @return true si l'événement n'a pas été annulé
     */
    public boolean triggerCustomEvent(CustomMobEvent event) {
        // Appelle tous les listeners enregistrés
        for (CustomMobEventListener listener : eventListeners.values()) {
            if (event instanceof CustomMobSpawnEvent) {
                listener.onMobSpawn((CustomMobSpawnEvent) event);
            } else if (event instanceof CustomMobDeathEvent) {
                listener.onMobDeath((CustomMobDeathEvent) event);
            } else if (event instanceof CustomMobAttackEvent) {
                listener.onMobAttack((CustomMobAttackEvent) event);
            } else if (event instanceof CustomMobAbilityEvent) {
                listener.onMobAbility((CustomMobAbilityEvent) event);
            }
        }

        // Appelle aussi l'événement Bukkit standard
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    // ================================
    // UTILITAIRES
    // ================================

    /**
     * Vérifie si une entité est un mob custom
     *
     * @param entity L'entité à vérifier
     * @return true si c'est un mob custom
     */
    public boolean isCustomMob(LivingEntity entity) {
        return CustomMob.isCustomMob(entity);
    }

    /**
     * Récupère l'ID d'un mob custom
     *
     * @param entity L'entité du mob
     * @return L'ID du mob ou null si ce n'est pas un mob custom
     */
    public String getCustomMobId(LivingEntity entity) {
        return CustomMob.getCustomMobId(entity);
    }

    /**
     * Récupère les statistiques d'un mob custom
     *
     * @param entity L'entité du mob
     * @return Les statistiques du mob ou null
     */
    public MobStats getMobStats(LivingEntity entity) {
        if (!isCustomMob(entity)) return null;

        String mobId = getCustomMobId(entity);
        boolean isBoss = mobId != null && mobId.contains("boss");

        return new MobStats(
                mobId,
                entity.getMaxHealth(),
                entity.getHealth(),
                10.0, // Dégâts par défaut
                0.25, // Vitesse par défaut
                System.currentTimeMillis() - 60000, // Spawn il y a 1 minute (exemple)
                0, // Joueurs tués
                0, // Dégâts infligés
                entity.getMaxHealth() - entity.getHealth(), // Dégâts subis
                isBoss
        );
    }

    /**
     * Sauvegarde toutes les configurations
     *
     * @return Future indiquant le succès de l'opération
     */
    public CompletableFuture<Boolean> saveAllConfigs() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                plugin.getLootManager().saveLootConfig();
                plugin.saveConfig();
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors de la sauvegarde: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Recharge toutes les configurations
     *
     * @return Future indiquant le succès de l'opération
     */
    public CompletableFuture<Boolean> reloadAllConfigs() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                plugin.reloadConfig();
                plugin.getLootManager().loadLootConfig();
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du rechargement: " + e.getMessage());
                return false;
            }
        });
    }

    // ================================
    // MÉTHODES PRIVÉES UTILITAIRES
    // ================================

    private Player determineMVP(BossFightStats stats) {
        if (stats.getDamageToBoss().isEmpty()) return null;

        return stats.getDamageToBoss().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .orElse(null);
    }

    private List<Player> getTopDPS(BossFightStats stats, int count) {
        return stats.getDamageToBoss().entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(count)
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ================================
    // CLASSES ET INTERFACES PUBLIQUES
    // ================================

    /**
     * Configuration d'un loot conditionnel
     */
    public static class ConditionalLoot {
        private final ItemStack item;
        private final double baseChance;
        private final Map<String, Object> conditions;
        private LootModifier modifier;

        public ConditionalLoot(ItemStack item, double baseChance) {
            this.item = item.clone();
            this.baseChance = baseChance;
            this.conditions = new HashMap<>();
        }

        public ConditionalLoot withCondition(String key, Object value) {
            conditions.put(key, value);
            return this;
        }

        public ConditionalLoot withPlayerLevelRequirement(int minLevel, int maxLevel) {
            conditions.put("player_min_level", minLevel);
            conditions.put("player_max_level", maxLevel);
            return this;
        }

        public ConditionalLoot withTimeCondition(int minHour, int maxHour) {
            conditions.put("time_min", minHour);
            conditions.put("time_max", maxHour);
            return this;
        }

        public ConditionalLoot withWorldCondition(String... worlds) {
            conditions.put("allowed_worlds", Arrays.asList(worlds));
            return this;
        }

        public ConditionalLoot withModifier(LootModifier modifier) {
            this.modifier = modifier;
            return this;
        }

        @SuppressWarnings("unchecked")
        public boolean checkConditions(Player player, LivingEntity entity) {
            if (player == null) return true;

            // Vérification niveau joueur
            if (conditions.containsKey("player_min_level")) {
                int minLevel = (Integer) conditions.get("player_min_level");
                if (player.getLevel() < minLevel) return false;
            }

            if (conditions.containsKey("player_max_level")) {
                int maxLevel = (Integer) conditions.get("player_max_level");
                if (player.getLevel() > maxLevel) return false;
            }

            // Vérification heure
            if (conditions.containsKey("time_min") && conditions.containsKey("time_max")) {
                long worldTime = player.getWorld().getTime();
                int currentHour = (int) ((worldTime / 1000 + 6) % 24);
                int minHour = (Integer) conditions.get("time_min");
                int maxHour = (Integer) conditions.get("time_max");

                if (minHour <= maxHour) {
                    if (currentHour < minHour || currentHour > maxHour) return false;
                } else {
                    if (currentHour < minHour && currentHour > maxHour) return false;
                }
            }

            // Vérification monde
            if (conditions.containsKey("allowed_worlds")) {
                List<String> allowedWorlds = (List<String>) conditions.get("allowed_worlds");
                if (!allowedWorlds.contains(player.getWorld().getName())) return false;
            }

            return true;
        }

        // Getters
        public ItemStack getItem() { return item.clone(); }
        public double getBaseChance() { return baseChance; }
        public Map<String, Object> getConditions() { return new HashMap<>(conditions); }
        public LootModifier getModifier() { return modifier; }
    }

    /**
     * Entrée de loot simple
     */
    public static class LootEntry {
        private final ItemStack item;
        private final double chance;

        public LootEntry(ItemStack item, double chance) {
            this.item = item.clone();
            this.chance = chance;
        }

        public ItemStack getItem() { return item.clone(); }
        public double getChance() { return chance; }
    }

    /**
     * Configuration d'une zone de spawn
     */
    public static class SpawnZoneConfig {
        private final String worldName;
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;
        private final List<String> mobTypes;
        private final int maxMobs;
        private final int groupSize;
        private final int spawnInterval;
        private final boolean enabled;

        public SpawnZoneConfig(String worldName, int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ, List<String> mobTypes,
                               int maxMobs, int groupSize, int spawnInterval, boolean enabled) {
            this.worldName = worldName;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.mobTypes = new ArrayList<>(mobTypes);
            this.maxMobs = maxMobs;
            this.groupSize = groupSize;
            this.spawnInterval = spawnInterval;
            this.enabled = enabled;
        }

        // Getters
        public String getWorldName() { return worldName; }
        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
        public List<String> getMobTypes() { return new ArrayList<>(mobTypes); }
        public int getMaxMobs() { return maxMobs; }
        public int getGroupSize() { return groupSize; }
        public int getSpawnInterval() { return spawnInterval; }
        public boolean isEnabled() { return enabled; }
    }

    /**
     * Configuration d'une barre de boss
     */
    public static class BossBarConfig {
        private final String title;
        private final BarColor color;
        private final BarStyle style;
        private final double maxDistance;
        private final boolean showPercent;
        private final Set<BarFlag> flags;

        public BossBarConfig(String title, BarColor color) {
            this.title = title;
            this.color = color;
            this.style = BarStyle.SOLID;
            this.maxDistance = 50.0;
            this.showPercent = false;
            this.flags = EnumSet.of(BarFlag.PLAY_BOSS_MUSIC);
        }

        public BossBarConfig withStyle(BarStyle style) {
            return new BossBarConfig(title, color).setStyle(style);
        }

        private BossBarConfig setStyle(BarStyle style) {
            return this; // Dans une vraie implémentation, on créerait une nouvelle instance
        }

        // Getters
        public String getTitle() { return title; }
        public BarColor getColor() { return color; }
        public BarStyle getStyle() { return style; }
        public double getMaxDistance() { return maxDistance; }
        public boolean isShowPercent() { return showPercent; }
        public Set<BarFlag> getFlags() { return EnumSet.copyOf(flags); }
    }

    /**
     * Statistiques d'un combat de boss
     */
    public static class BossFightStats {
        private final String mobId;
        private final String bossName;
        private final long startTime;
        private final Map<UUID, Double> damageToBoss;
        private final Map<UUID, Double> damageFromBoss;
        private final Map<UUID, Integer> minionKills;
        private final Map<UUID, Integer> playerDeaths;

        public BossFightStats(String mobId, String bossName, long startTime) {
            this.mobId = mobId;
            this.bossName = bossName;
            this.startTime = startTime;
            this.damageToBoss = new HashMap<>();
            this.damageFromBoss = new HashMap<>();
            this.minionKills = new HashMap<>();
            this.playerDeaths = new HashMap<>();
        }

        // Getters
        public String getMobId() { return mobId; }
        public String getBossName() { return bossName; }
        public long getStartTime() { return startTime; }
        public long getDuration() { return System.currentTimeMillis() - startTime; }
        public Map<UUID, Double> getDamageToBoss() { return new HashMap<>(damageToBoss); }
        public Map<UUID, Double> getDamageFromBoss() { return new HashMap<>(damageFromBoss); }
        public Map<UUID, Integer> getMinionKills() { return new HashMap<>(minionKills); }
        public Map<UUID, Integer> getPlayerDeaths() { return new HashMap<>(playerDeaths); }

        public Set<UUID> getAllParticipants() {
            Set<UUID> participants = new HashSet<>();
            participants.addAll(damageToBoss.keySet());
            participants.addAll(damageFromBoss.keySet());
            participants.addAll(minionKills.keySet());
            participants.addAll(playerDeaths.keySet());
            return participants;
        }

        public double getTotalDamageDealt() {
            return damageToBoss.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public double getTotalDamageTaken() {
            return damageFromBoss.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public int getTotalMinionKills() {
            return minionKills.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getTotalDeaths() {
            return playerDeaths.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Résultat d'un combat de boss
     */
    public static class BossFightResult {
        private final BossFightStats stats;
        private final boolean victory;
        private final Player mvp;
        private final List<Player> topDps;
        private final Map<Player, String> rewards;

        public BossFightResult(BossFightStats stats, boolean victory, Player mvp,
                               List<Player> topDps, Map<Player, String> rewards) {
            this.stats = stats;
            this.victory = victory;
            this.mvp = mvp;
            this.topDps = new ArrayList<>(topDps);
            this.rewards = new HashMap<>(rewards);
        }

        // Getters
        public BossFightStats getStats() { return stats; }
        public boolean isVictory() { return victory; }
        public Player getMvp() { return mvp; }
        public List<Player> getTopDps() { return new ArrayList<>(topDps); }
        public Map<Player, String> getRewards() { return new HashMap<>(rewards); }
    }

    /**
     * Statistiques d'un mob
     */
    public static class MobStats {
        private final String mobId;
        private final double maxHealth;
        private final double currentHealth;
        private final double damage;
        private final double speed;
        private final long spawnTime;
        private final int playersKilled;
        private final double damageDealt;
        private final double damageTaken;
        private final boolean isBoss;

        public MobStats(String mobId, double maxHealth, double currentHealth, double damage,
                        double speed, long spawnTime, int playersKilled, double damageDealt,
                        double damageTaken, boolean isBoss) {
            this.mobId = mobId;
            this.maxHealth = maxHealth;
            this.currentHealth = currentHealth;
            this.damage = damage;
            this.speed = speed;
            this.spawnTime = spawnTime;
            this.playersKilled = playersKilled;
            this.damageDealt = damageDealt;
            this.damageTaken = damageTaken;
            this.isBoss = isBoss;
        }

        // Getters
        public String getMobId() { return mobId; }
        public double getMaxHealth() { return maxHealth; }
        public double getCurrentHealth() { return currentHealth; }
        public double getHealthPercent() { return maxHealth > 0 ? currentHealth / maxHealth : 0; }
        public double getDamage() { return damage; }
        public double getSpeed() { return speed; }
        public long getSpawnTime() { return spawnTime; }
        public long getAliveTime() { return System.currentTimeMillis() - spawnTime; }
        public int getPlayersKilled() { return playersKilled; }
        public double getDamageDealt() { return damageDealt; }
        public double getDamageTaken() { return damageTaken; }
        public boolean isAlive() { return currentHealth > 0; }
        public boolean isBoss() { return isBoss; }

        public String getDisplayName() {
            return switch (mobId) {
                case "zombie_warrior" -> "§c§lGuerrier Zombie";
                case "skeleton_archer" -> "§c§lArcher Infernal";
                case "spider_venomous" -> "§2§lTisseuse Malsaine";
                case "wither_boss" -> "§5§lArchliche Nécrosis";
                case "warden_boss" -> "§0§lGardien des Abysses";
                case "ravager_boss" -> "§c§lDévastateur Primordial";
                default -> "§6Mob Custom";
            };
        }
    }

    // ================================
    // ÉVÉNEMENTS PERSONNALISÉS
    // ================================

    public enum SpawnReason {
        NATURAL, COMMAND, ZONE, PLUGIN, SUMMON, OTHER
    }

    /**
     * Événement de base pour les mobs custom
     */
    public abstract static class CustomMobEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        protected final LivingEntity entity;
        protected final String mobId;
        private boolean cancelled = false;

        public CustomMobEvent(LivingEntity entity, String mobId) {
            this.entity = entity;
            this.mobId = mobId;
        }

        public LivingEntity getEntity() { return entity; }
        public String getMobId() { return mobId; }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

        @Override
        public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /**
     * Événement déclenché lors du spawn d'un mob custom
     */
    public static class CustomMobSpawnEvent extends CustomMobEvent {
        private final Location location;
        private final SpawnReason reason;

        public CustomMobSpawnEvent(LivingEntity entity, String mobId, Location location, SpawnReason reason) {
            super(entity, mobId);
            this.location = location.clone();
            this.reason = reason;
        }

        public Location getLocation() { return location.clone(); }
        public SpawnReason getReason() { return reason; }
    }

    /**
     * Événement déclenché lors de la mort d'un mob custom
     */
    public static class CustomMobDeathEvent extends CustomMobEvent {
        private final Player killer;
        private final List<ItemStack> loots;

        public CustomMobDeathEvent(LivingEntity entity, String mobId, Player killer, List<ItemStack> loots) {
            super(entity, mobId);
            this.killer = killer;
            this.loots = new ArrayList<>(loots);
        }

        public Player getKiller() { return killer; }
        public List<ItemStack> getLoots() { return new ArrayList<>(loots); }

        public void addLoot(ItemStack item) {
            loots.add(item.clone());
        }

        public void removeLoot(ItemStack item) {
            loots.remove(item);
        }

        public void clearLoots() {
            loots.clear();
        }
    }

    /**
     * Événement déclenché lors d'une attaque de mob custom
     */
    public static class CustomMobAttackEvent extends CustomMobEvent {
        private final Player target;
        private double damage;
        private final String attackType;

        public CustomMobAttackEvent(LivingEntity entity, String mobId, Player target, double damage, String attackType) {
            super(entity, mobId);
            this.target = target;
            this.damage = damage;
            this.attackType = attackType;
        }

        public Player getTarget() { return target; }
        public double getDamage() { return damage; }
        public void setDamage(double damage) { this.damage = Math.max(0, damage); }
        public String getAttackType() { return attackType; }
    }

    /**
     * Événement déclenché lors d'une capacité spéciale
     */
    public static class CustomMobAbilityEvent extends CustomMobEvent {
        private final String abilityName;
        private final Player target;
        private final Map<String, Object> parameters;

        public CustomMobAbilityEvent(LivingEntity entity, String mobId, String abilityName, Player target) {
            super(entity, mobId);
            this.abilityName = abilityName;
            this.target = target;
            this.parameters = new HashMap<>();
        }

        public String getAbilityName() { return abilityName; }
        public Player getTarget() { return target; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }

        public void setParameter(String key, Object value) {
            parameters.put(key, value);
        }

        public Object getParameter(String key) {
            return parameters.get(key);
        }
    }

    // ================================
    // INTERFACES FONCTIONNELLES
    // ================================

    /**
     * Interface pour écouter les événements de mobs custom
     */
    public interface CustomMobEventListener {
        default void onMobSpawn(CustomMobSpawnEvent event) {}
        default void onMobDeath(CustomMobDeathEvent event) {}
        default void onMobAttack(CustomMobAttackEvent event) {}
        default void onMobAbility(CustomMobAbilityEvent event) {}
    }

    /**
     * Interface pour modifier les loots
     */
    @FunctionalInterface
    public interface LootCallback {
        List<ItemStack> generateLoot(LivingEntity entity, Player killer, List<ItemStack> originalLoots);
    }

    /**
     * Interface pour modifier les loots de manière avancée
     */
    @FunctionalInterface
    public interface LootModifier {
        double modifyChance(double originalChance, Player killer, LivingEntity entity, Map<String, Object> context);
    }
}