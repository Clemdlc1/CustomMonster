package fr.custommobs.events;

import fr.custommobs.CustomMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire de configuration pour les événements
 * Version mise à jour pour supporter BreachContainmentEvent
 */
public class EventConfigManager {

    private final CustomMobsPlugin plugin;
    private File eventsConfigFile;
    private FileConfiguration eventsConfig;

    // Caches pour les configurations
    private final Map<String, EventScheduleConfig> eventSchedules = new HashMap<>();
    private final Map<String, List<EventLocationConfig>> eventLocations = new HashMap<>();
    private final Map<String, EventMobConfig> eventMobs = new HashMap<>();
    private final Map<String, EventRewardConfig> eventRewards = new HashMap<>();

    public EventConfigManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        loadEventsConfig();
    }

    /**
     * Charge le fichier de configuration des événements
     */
    void loadEventsConfig() {
        eventsConfigFile = new File(plugin.getDataFolder(), "events.yml");

        if (!eventsConfigFile.exists()) {
            plugin.saveResource("events.yml", false);
            plugin.getLogger().info("Fichier events.yml créé par défaut!");
        }

        eventsConfig = YamlConfiguration.loadConfiguration(eventsConfigFile);

        // Charger toutes les configurations
        loadEventSchedules();
        loadEventLocations();
        loadEventMobs();
        loadEventRewards();

        plugin.getLogger().info("Configuration des événements chargée!");
        plugin.getLogger().info("- " + eventSchedules.size() + " plannings d'événements");
        plugin.getLogger().info("- " + eventLocations.size() + " catégories de zones");
        plugin.getLogger().info("- " + eventMobs.size() + " monstres configurés");
        plugin.getLogger().info("- " + eventRewards.size() + " systèmes de récompenses");
    }

    /**
     * Recharge la configuration
     */
    public void reloadConfig() {
        eventsConfig = YamlConfiguration.loadConfiguration(eventsConfigFile);

        // Vider les caches
        eventSchedules.clear();
        eventLocations.clear();
        eventMobs.clear();
        eventRewards.clear();

        // Recharger
        loadEventsConfig();
        plugin.getLogger().info("Configuration des événements rechargée!");
    }

    /**
     * Sauvegarde la configuration des événements
     */
    public void saveEventsConfig() {
        try {
            eventsConfig.save(eventsConfigFile);
            plugin.getLogger().info("Configuration des événements sauvegardée!");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde de la config événements: " + e.getMessage());
        }
    }

    /**
     * Charge les plannings des événements
     */
    public void loadEventSchedules() {
        eventSchedules.clear();
        ConfigurationSection schedulesSection = eventsConfig.getConfigurationSection("event-schedules");

        if (schedulesSection != null) {
            for (String eventId : schedulesSection.getKeys(false)) {
                ConfigurationSection eventSection = schedulesSection.getConfigurationSection(eventId);
                if (eventSection != null) {
                    try {
                        EventScheduleConfig config = EventScheduleConfig.fromConfig(eventId, eventSection);
                        eventSchedules.put(eventId, config);
                        plugin.getLogger().info("Planning chargé pour l'événement: " + eventId);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement du planning pour " + eventId + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Charge les localisations des événements
     */
    private void loadEventLocations() {
        eventLocations.clear();
        ConfigurationSection locationsSection = eventsConfig.getConfigurationSection("event-locations");

        if (locationsSection != null) {
            // Charger les zones de brèche
            loadLocationCategory("breach-areas", locationsSection);

            // Charger les arènes de boss
            loadLocationCategory("boss-arenas", locationsSection);

            // Charger les zones de trésor
            loadLocationCategory("treasure-zones", locationsSection);

            // Charger les zones de largage
            loadLocationCategory("supply-drop-zones", locationsSection);

            // Charger les zones de mine
            loadLocationCategory("mine-areas", locationsSection);

            // Charger les localisations spécifiques
            loadSpecificLocations(locationsSection);
        }
    }

    /**
     * Charge une catégorie de localisation
     */
    private void loadLocationCategory(String category, ConfigurationSection parent) {
        ConfigurationSection categorySection = parent.getConfigurationSection(category);
        if (categorySection != null) {
            List<EventLocationConfig> locations = new ArrayList<>();

            for (String locationId : categorySection.getKeys(false)) {
                ConfigurationSection locationSection = categorySection.getConfigurationSection(locationId);
                if (locationSection != null) {
                    try {
                        EventLocationConfig config = EventLocationConfig.fromConfig(locationId, locationSection);
                        locations.add(config);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement de la localisation " + locationId + " dans " + category + ": " + e.getMessage());
                    }
                }
            }

            if (!locations.isEmpty()) {
                eventLocations.put(category, locations);
                plugin.getLogger().info("Chargé " + locations.size() + " localisations pour: " + category);
            }
        }
    }

    /**
     * Charge les localisations spécifiques
     */
    private void loadSpecificLocations(ConfigurationSection parent) {
        ConfigurationSection specificSection = parent.getConfigurationSection("specific-locations");
        if (specificSection != null) {
            for (String category : specificSection.getKeys(false)) {
                if (specificSection.isList(category)) {
                    List<Map<?, ?>> locationsList = (List<Map<?, ?>>) specificSection.getMapList(category);
                    List<EventLocationConfig> locations = new ArrayList<>();

                    for (Map<?, ?> locationMap : locationsList) {
                        try {
                            EventLocationConfig config = EventLocationConfig.fromMap(category, locationMap);
                            locations.add(config);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur lors du chargement de la localisation spécifique " + category + ": " + e.getMessage());
                        }
                    }

                    if (!locations.isEmpty()) {
                        eventLocations.put(category, locations);
                        plugin.getLogger().info("Chargé " + locations.size() + " localisations spécifiques pour: " + category);
                    }
                }
            }
        }
    }

    /**
     * Charge les configurations de mobs
     */
    private void loadEventMobs() {
        eventMobs.clear();
        ConfigurationSection mobsSection = eventsConfig.getConfigurationSection("event-mobs");

        if (mobsSection != null) {
            for (String category : mobsSection.getKeys(false)) {
                ConfigurationSection categorySection = mobsSection.getConfigurationSection(category);
                if (categorySection != null) {
                    for (String mobId : categorySection.getKeys(false)) {
                        ConfigurationSection mobSection = categorySection.getConfigurationSection(mobId);
                        if (mobSection != null) {
                            try {
                                EventMobConfig config = EventMobConfig.fromConfig(mobId, mobSection);
                                eventMobs.put(category + "." + mobId, config);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Erreur lors du chargement du mob " + mobId + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Charge les configurations de récompenses
     */
    private void loadEventRewards() {
        eventRewards.clear();
        ConfigurationSection rewardsSection = eventsConfig.getConfigurationSection("event-rewards");

        if (rewardsSection != null) {
            for (String eventId : rewardsSection.getKeys(false)) {
                ConfigurationSection eventSection = rewardsSection.getConfigurationSection(eventId);
                if (eventSection != null) {
                    try {
                        EventRewardConfig config = EventRewardConfig.fromConfig(eventId, eventSection);
                        eventRewards.put(eventId, config);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement des récompenses " + eventId + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    // =================================
    // GETTERS PUBLICS
    // =================================

    public FileConfiguration getEventsConfig() {
        return eventsConfig;
    }

    public EventScheduleConfig getEventSchedule(String eventId) {
        return eventSchedules.get(eventId);
    }

    public List<EventLocationConfig> getEventLocationConfigs(String category) {
        return eventLocations.getOrDefault(category, new ArrayList<>());
    }

    public List<EventMobConfig> getEventMobsInCategory(String category) {
        return eventMobs.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(category + "."))
                .map(Map.Entry::getValue)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public EventRewardConfig getEventRewards(String eventId) {
        return eventRewards.get(eventId);
    }

    public Map<String, EventScheduleConfig> getAllEventSchedules() {
        return new HashMap<>(eventSchedules);
    }

    public Map<String, List<EventLocationConfig>> getAllEventLocations() {
        return new HashMap<>(eventLocations);
    }

    // =================================
    // MÉTHODES UTILITAIRES SPÉCIFIQUES
    // =================================

    /**
     * Récupère une zone de brèche aléatoire pondérée
     */
    public EventLocationConfig getRandomBreachArea() {
        return getWeightedRandomLocation("breach-areas");
    }

    /**
     * Récupère une localisation aléatoire pondérée d'une catégorie
     */
    public EventLocationConfig getWeightedRandomLocation(String category) {
        List<EventLocationConfig> locations = getEventLocationConfigs(category);
        if (locations.isEmpty()) return null;

        int totalWeight = locations.stream().mapToInt(EventLocationConfig::weight).sum();
        if (totalWeight <= 0) {
            return locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
        }

        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (EventLocationConfig location : locations) {
            currentWeight += location.weight();
            if (randomWeight < currentWeight) {
                return location;
            }
        }

        return locations.getFirst(); // Fallback
    }

    /**
     * Valide si toutes les zones de brèche sont disponibles
     */
    public boolean validateBreachAreas() {
        List<EventLocationConfig> breachAreas = getEventLocationConfigs("breach-areas");
        if (breachAreas.isEmpty()) {
            plugin.getLogger().severe("Aucune zone de brèche configurée!");
            return false;
        }

        int validAreas = 0;
        for (EventLocationConfig area : breachAreas) {
            if (area.isWorldAvailable()) {
                validAreas++;
            } else {
                plugin.getLogger().warning("Monde non disponible pour la zone: " + area.displayName());
            }
        }

        if (validAreas == 0) {
            plugin.getLogger().severe("Aucune zone de brèche utilisable!");
            return false;
        }

        plugin.getLogger().info("Validation réussie: " + validAreas + "/" + breachAreas.size() + " zones de brèche disponibles");
        return true;
    }

    /**
     * Récupère la configuration avancée d'un événement
     */
    public ConfigurationSection getAdvancedSettings(String eventId) {
        return eventsConfig.getConfigurationSection("advanced-settings." + eventId);
    }

    /**
     * Récupère les paramètres de debug
     */
    public boolean isDebugEnabled() {
        return eventsConfig.getBoolean("debug.enabled", false);
    }

    public boolean shouldLogMobSpawns() {
        return eventsConfig.getBoolean("debug.log_mob_spawns", false);
    }

    public boolean shouldLogWaveProgression() {
        return eventsConfig.getBoolean("debug.log_wave_progression", true);
    }

    public EventLocationConfig getRandomLocationConfig(String s) {
        return null;
    }

    // =================================
    // CLASSES DE CONFIGURATION
    // =================================

    public record EventScheduleConfig(String id, String name, boolean enabled, List<DayOfWeek> days, LocalTime time,
                                      int duration, String description) {
            public EventScheduleConfig(String id, String name, boolean enabled, List<DayOfWeek> days,
                                       LocalTime time, int duration, String description) {
                this.id = id;
                this.name = name;
                this.enabled = enabled;
                this.days = new ArrayList<>(days);
                this.time = time;
                this.duration = duration;
                this.description = description;
            }

            public static EventScheduleConfig fromConfig(String id, ConfigurationSection section) {
                String name = section.getString("name", id);
                boolean enabled = section.getBoolean("enabled", true);
                String timeStr = section.getString("time", "12:00");
                int duration = section.getInt("duration", 1800);
                String description = section.getString("description", "");

                List<DayOfWeek> days = new ArrayList<>();
                for (String dayStr : section.getStringList("days")) {
                    try {
                        days.add(DayOfWeek.valueOf(dayStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        System.err.println("Jour invalide: " + dayStr);
                    }
                }

                LocalTime time = LocalTime.parse(timeStr);
                return new EventScheduleConfig(id, name, enabled, days, time, duration, description);
            }

        @Override
        public List<DayOfWeek> days() {
            return new ArrayList<>(days);
        }
        }

    public record EventLocationConfig(String id, String worldName, int centerX, int centerY, int centerZ, int radius,
                                      int minY, int maxY, int weight, String displayName,
                                      Map<String, Object> extraData) {
            public EventLocationConfig(String id, String worldName, int centerX, int centerY, int centerZ,
                                       int radius, int minY, int maxY, int weight, String displayName,
                                       Map<String, Object> extraData) {
                this.id = id;
                this.worldName = worldName;
                this.centerX = centerX;
                this.centerY = centerY;
                this.centerZ = centerZ;
                this.radius = radius;
                this.minY = minY;
                this.maxY = maxY;
                this.weight = weight;
                this.displayName = displayName != null ? displayName : id;
                this.extraData = extraData != null ? new HashMap<>(extraData) : new HashMap<>();
            }

            public static EventLocationConfig fromConfig(String id, ConfigurationSection section) {
                String worldName = section.getString("world", "world");

                // Support pour center ou coordonnées directes
                ConfigurationSection centerSection = section.getConfigurationSection("center");
                int centerX, centerY, centerZ;

                if (centerSection != null) {
                    centerX = centerSection.getInt("x", 0);
                    centerY = centerSection.getInt("y", 64);
                    centerZ = centerSection.getInt("z", 0);
                } else {
                    centerX = section.getInt("x", 0);
                    centerY = section.getInt("y", 64);
                    centerZ = section.getInt("z", 0);
                }

                int radius = section.getInt("radius", 10);
                int minY = section.getInt("min-y", centerY - 5);
                int maxY = section.getInt("max-y", centerY + 5);
                int weight = section.getInt("weight", 1);
                String displayName = section.getString("display-name", id);

                Map<String, Object> extraData = new HashMap<>();
                for (String key : section.getKeys(false)) {
                    if (!Arrays.asList("world", "center", "x", "y", "z", "radius", "min-y", "max-y",
                            "weight", "display-name").contains(key)) {
                        extraData.put(key, section.get(key));
                    }
                }

                return new EventLocationConfig(id, worldName, centerX, centerY, centerZ, radius,
                        minY, maxY, weight, displayName, extraData);
            }

            public static EventLocationConfig fromMap(String id, Map<?, ?> map) {
                Object worldObj = map.get("world");
                String worldName = worldObj instanceof String ? (String) worldObj : "world";

                Object xObj = map.get("x");
                int centerX = xObj instanceof Number ? ((Number) xObj).intValue() : 0;

                Object yObj = map.get("y");
                int centerY = yObj instanceof Number ? ((Number) yObj).intValue() : 70;

                Object zObj = map.get("z");
                int centerZ = zObj instanceof Number ? ((Number) zObj).intValue() : 0;

                Object radiusObj = map.get("radius");
                int radius = radiusObj instanceof Number ? ((Number) radiusObj).intValue() : 10;

                Object weightObj = map.get("weight");
                int weight = weightObj instanceof Number ? ((Number) weightObj).intValue() : 1;

                return new EventLocationConfig(id, worldName, centerX, centerY, centerZ,
                        radius, centerY - 5, centerY + 5, weight, id, null);
            }

            public Location getRandomLocation(CustomMobsPlugin plugin) {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Monde '" + worldName + "' non trouvé pour la zone " + id);
                    return null;
                }

                if (radius <= 0) {
                    return new Location(world, centerX, centerY, centerZ);
                }

                double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                double distance = ThreadLocalRandom.current().nextDouble() * radius;

                int x = centerX + (int) (Math.cos(angle) * distance);
                int z = centerZ + (int) (Math.sin(angle) * distance);
                int y = minY + ThreadLocalRandom.current().nextInt(maxY - minY + 1);

                return new Location(world, x, y, z);
            }

            public boolean isWorldAvailable() {
                return Bukkit.getWorld(worldName) != null;
            }

        @Override
        public Map<String, Object> extraData() {
            return new HashMap<>(extraData);
        }

            public Location getCenterLocation(CustomMobsPlugin plugin) {
                return null;
            }
        }

    public record EventMobConfig(String id, int weight, int groupSize, Map<String, Object> extraData) {
            public EventMobConfig(String id, int weight, int groupSize, Map<String, Object> extraData) {
                this.id = id;
                this.weight = weight;
                this.groupSize = groupSize;
                this.extraData = extraData != null ? new HashMap<>(extraData) : new HashMap<>();
            }

            public static EventMobConfig fromConfig(String id, ConfigurationSection section) {
                String name = section.getString("name", id);
                int weight = section.getInt("weight", 1);
                int groupSize = section.getInt("group-size", 1);

                Map<String, Object> extraData = new HashMap<>();
                extraData.put("name", name);

                // Données spécifiques aux brèches
                if (section.contains("spawn-weight")) {
                    extraData.put("spawn-weight", section.getInt("spawn-weight"));
                }
                if (section.contains("health-multiplier")) {
                    extraData.put("health-multiplier", section.getDouble("health-multiplier"));
                }
                if (section.contains("damage-multiplier")) {
                    extraData.put("damage-multiplier", section.getDouble("damage-multiplier"));
                }

                // Autres données
                for (String key : section.getKeys(false)) {
                    if (!Arrays.asList("name", "weight", "group-size", "spawn-weight",
                            "health-multiplier", "damage-multiplier").contains(key)) {
                        extraData.put(key, section.get(key));
                    }
                }

                return new EventMobConfig(id, weight, groupSize, extraData);
            }

        @Override
        public Map<String, Object> extraData() {
            return new HashMap<>(extraData);
        }

        public int getSpawnWeight() {
            return (int) extraData.getOrDefault("spawn-weight", weight);
        }

        public String getName() {
            return (String) extraData.getOrDefault("name", id);
        }

        public double getHealthMultiplier() {
            return (double) extraData.getOrDefault("health-multiplier", 1.0);
        }

        public double getDamageMultiplier() {
            return (double) extraData.getOrDefault("damage-multiplier", 1.0);
        }
        }

    public record EventRewardConfig(String eventId, Map<String, Integer> rewards,
                                    Map<String, Map<String, Integer>> tieredRewards) {
            public EventRewardConfig(String eventId, Map<String, Integer> rewards,
                                     Map<String, Map<String, Integer>> tieredRewards) {
                this.eventId = eventId;
                this.rewards = new HashMap<>(rewards);
                this.tieredRewards = new HashMap<>(tieredRewards);
            }

            public static EventRewardConfig fromConfig(String eventId, ConfigurationSection section) {
                Map<String, Integer> rewards = new HashMap<>();
                Map<String, Map<String, Integer>> tieredRewards = new HashMap<>();

                for (String key : section.getKeys(false)) {
                    if (section.isConfigurationSection(key)) {
                        ConfigurationSection tierSection = section.getConfigurationSection(key);
                        Map<String, Integer> tierMap = new HashMap<>();
                        if (tierSection != null) {
                            for (String tierKey : tierSection.getKeys(false)) {
                                if (tierSection.isInt(tierKey)) {
                                    tierMap.put(tierKey, tierSection.getInt(tierKey));
                                }
                            }
                        }
                        tieredRewards.put(key, tierMap);
                    } else if (section.isInt(key)) {
                        rewards.put(key, section.getInt(key));
                    }
                }

                return new EventRewardConfig(eventId, rewards, tieredRewards);
            }

        @Override
        public Map<String, Integer> rewards() {
            return new HashMap<>(rewards);
        }

        @Override
        public Map<String, Map<String, Integer>> tieredRewards() {
            return new HashMap<>(tieredRewards);
        }

            public int getReward(String key) {
                return rewards.getOrDefault(key, 0);
            }

            public Map<String, Integer> getTierRewards(String tier) {
                return tieredRewards.getOrDefault(tier, new HashMap<>());
            }

            public int getTierReward(String tier, String key) {
                Map<String, Integer> tierMap = tieredRewards.get(tier);
                return tierMap != null ? tierMap.getOrDefault(key, 0) : 0;
            }

            public int getTieredReward(String s, String s1) {
                return 0;
            }
        }
}