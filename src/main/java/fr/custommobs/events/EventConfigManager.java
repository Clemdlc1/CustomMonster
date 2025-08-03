package fr.custommobs.events;

import fr.custommobs.CustomMobsPlugin;
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
 * Gestionnaire pour la configuration des événements
 */
public class EventConfigManager {

    private final CustomMobsPlugin plugin;
    private final File eventsConfigFile;
    private FileConfiguration eventsConfig;

    // Caches pour les données de configuration
    private final Map<String, EventScheduleConfig> eventSchedules = new HashMap<>();
    private final Map<String, List<EventLocationConfig>> eventLocations = new HashMap<>();
    private final Map<String, EventMobConfig> eventMobs = new HashMap<>();
    private final Map<String, EventRewardConfig> eventRewards = new HashMap<>();

    public EventConfigManager(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.eventsConfigFile = new File(plugin.getDataFolder(), "events.yml");
        loadEventsConfig();
    }

    /**
     * Charge la configuration des événements
     */
    public void loadEventsConfig() {
        if (!eventsConfigFile.exists()) {
            plugin.saveResource("events.yml", false);
        }

        eventsConfig = YamlConfiguration.loadConfiguration(eventsConfigFile);

        loadEventSchedules();
        loadEventLocations();
        loadEventMobs();
        loadEventRewards();

        plugin.getLogger().info("§aConfiguration des événements chargée!");
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

            // Charger les points spécifiques
            loadSpecificLocations(locationsSection);
        }
    }

    private void loadLocationCategory(String category, ConfigurationSection locationsSection) {
        ConfigurationSection categorySection = locationsSection.getConfigurationSection(category);
        if (categorySection != null) {
            List<EventLocationConfig> locations = new ArrayList<>();

            for (String locationId : categorySection.getKeys(false)) {
                ConfigurationSection locationSection = categorySection.getConfigurationSection(locationId);
                if (locationSection != null) {
                    try {
                        EventLocationConfig config = EventLocationConfig.fromConfig(locationId, locationSection);
                        locations.add(config);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement de la localisation " + locationId + ": " + e.getMessage());
                    }
                }
            }

            if (!locations.isEmpty()) {
                eventLocations.put(category, locations);
                plugin.getLogger().info("Chargé " + locations.size() + " localisations pour: " + category);
            }
        }
    }

    private void loadSpecificLocations(ConfigurationSection locationsSection) {
        ConfigurationSection specificSection = locationsSection.getConfigurationSection("specific-locations");
        if (specificSection != null) {
            for (String category : specificSection.getKeys(false)) {
                if (specificSection.isList(category)) {
                    List<Map<?, ?>> locationsList = specificSection.getMapList(category);
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
                        plugin.getLogger().warning("Erreur lors du chargement des récompenses pour " + eventId + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    // ===============================
    // MÉTHODES D'ACCÈS PUBLIC
    // ===============================

    public EventScheduleConfig getEventSchedule(String eventId) {
        return eventSchedules.get(eventId);
    }

    public Collection<EventScheduleConfig> getAllEventSchedules() {
        return eventSchedules.values();
    }

    public Location getRandomLocation(String category) {
        List<EventLocationConfig> locations = eventLocations.get(category);
        if (locations == null || locations.isEmpty()) {
            plugin.getLogger().warning("Aucune localisation trouvée pour la catégorie: " + category);
            return null;
        }

        // Sélection pondérée
        int totalWeight = locations.stream().mapToInt(EventLocationConfig::getWeight).sum();
        if (totalWeight <= 0) { // Avoid division by zero or negative numbers
            return locations.get(0).getRandomLocation(plugin);
        }
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

        int currentWeight = 0;
        for (EventLocationConfig location : locations) {
            currentWeight += location.getWeight();
            if (randomWeight < currentWeight) {
                return location.getRandomLocation(plugin);
            }
        }

        // Fallback
        return locations.get(0).getRandomLocation(plugin);
    }

    public List<Location> getMultipleLocations(String category, int count) {
        List<Location> result = new ArrayList<>();
        List<EventLocationConfig> locations = eventLocations.get(category);

        if (locations == null || locations.isEmpty()) {
            return result;
        }

        for (int i = 0; i < count; i++) {
            Location loc = getRandomLocation(category);
            if (loc != null) {
                result.add(loc);
            }
        }

        return result;
    }

    public EventMobConfig getEventMob(String category, String mobId) {
        return eventMobs.get(category + "." + mobId);
    }

    public List<EventMobConfig> getEventMobsInCategory(String category) {
        return eventMobs.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(category + "."))
                .map(Map.Entry::getValue)
                .toList();
    }

    public EventRewardConfig getEventRewards(String eventId) {
        return eventRewards.get(eventId);
    }

    public FileConfiguration getEventsConfig() {
        return eventsConfig;
    }

    public boolean isEventEnabled(String eventId) {
        EventScheduleConfig schedule = getEventSchedule(eventId);
        return schedule != null && schedule.isEnabled();
    }

    // ===============================
    // CLASSES DE CONFIGURATION
    // ===============================

    public static class EventScheduleConfig {
        private final String id;
        private final String name;
        private final boolean enabled;
        private final List<DayOfWeek> days;
        private final LocalTime time;
        private final int duration;
        private final String description;

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

            List<String> dayStrings = section.getStringList("days");
            List<DayOfWeek> days = dayStrings.stream()
                    .map(s -> {
                        try {
                            return DayOfWeek.valueOf(s.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            String timeString = section.getString("time", "12:00");
            LocalTime time = LocalTime.parse(timeString);

            int duration = section.getInt("duration", 1800);
            String description = section.getString("description", "");

            return new EventScheduleConfig(id, name, enabled, days, time, duration, description);
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public List<DayOfWeek> getDays() { return new ArrayList<>(days); }
        public LocalTime getTime() { return time; }
        public int getDuration() { return duration; }
        public String getDescription() { return description; }
    }

    public static class EventLocationConfig {
        private final String id;
        private final String worldName;
        private final double centerX, centerY, centerZ;
        private final double radius;
        private final double minY, maxY;
        private final int weight;
        private final String displayName;
        private final Map<String, Object> extraData;

        public EventLocationConfig(String id, String worldName, double centerX, double centerY, double centerZ,
                                   double radius, double minY, double maxY, int weight, String displayName,
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
            this.displayName = displayName;
            this.extraData = extraData != null ? new HashMap<>(extraData) : new HashMap<>();
        }

        public static EventLocationConfig fromConfig(String id, ConfigurationSection section) {
            String worldName = section.getString("world", "world");

            ConfigurationSection centerSection = section.getConfigurationSection("center");
            double centerX = centerSection != null ? centerSection.getDouble("x", 0) : section.getDouble("x", 0);
            double centerY = centerSection != null ? centerSection.getDouble("y", 70) : section.getDouble("y", 70);
            double centerZ = centerSection != null ? centerSection.getDouble("z", 0) : section.getDouble("z", 0);

            double radius = section.getDouble("radius", 50);
            double minY = section.getDouble("min-y", centerY - 10);
            double maxY = section.getDouble("max-y", centerY + 10);
            int weight = section.getInt("weight", 1);
            String displayName = section.getString("display-name", id);

            Map<String, Object> extraData = new HashMap<>();
            if (section.contains("max-chests")) extraData.put("max-chests", section.getInt("max-chests"));
            if (section.contains("max-drops")) extraData.put("max-drops", section.getInt("max-drops"));

            return new EventLocationConfig(id, worldName, centerX, centerY, centerZ,
                    radius, minY, maxY, weight, displayName, extraData);
        }

        public static EventLocationConfig fromMap(String id, Map<?, ?> map) {
            Object worldObj = map.get("world");
            String worldName = worldObj instanceof String ? (String) worldObj : "world";

            Object xObj = map.get("x");
            double centerX = xObj instanceof Number ? ((Number) xObj).doubleValue() : 0.0;

            Object yObj = map.get("y");
            double centerY = yObj instanceof Number ? ((Number) yObj).doubleValue() : 70.0;

            Object zObj = map.get("z");
            double centerZ = zObj instanceof Number ? ((Number) zObj).doubleValue() : 0.0;

            Object radiusObj = map.get("radius");
            double radius = radiusObj instanceof Number ? ((Number) radiusObj).doubleValue() : 10.0;

            Object weightObj = map.get("weight");
            int weight = weightObj instanceof Number ? ((Number) weightObj).intValue() : 1;

            return new EventLocationConfig(id, worldName, centerX, centerY, centerZ,
                    radius, centerY - 5, centerY + 5, weight, id, null);
        }

        public Location getRandomLocation(CustomMobsPlugin plugin) {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Monde '" + worldName + "' non trouvé pour la localisation " + id);
                return null;
            }

            // Génère une position aléatoire dans le rayon
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = ThreadLocalRandom.current().nextDouble() * radius;

            double x = centerX + Math.cos(angle) * distance;
            double z = centerZ + Math.sin(angle) * distance;
            double y = minY + ThreadLocalRandom.current().nextDouble() * (maxY - minY);

            return new Location(world, x, y, z);
        }

        public Location getCenterLocation(CustomMobsPlugin plugin) {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Monde '" + worldName + "' non trouvé pour la localisation " + id);
                return null;
            }
            return new Location(world, centerX, centerY, centerZ);
        }

        // Getters
        public String getId() { return id; }
        public String getWorldName() { return worldName; }
        public double getCenterX() { return centerX; }
        public double getCenterY() { return centerY; }
        public double getCenterZ() { return centerZ; }
        public double getRadius() { return radius; }
        public double getMinY() { return minY; }
        public double getMaxY() { return maxY; }
        public int getWeight() { return weight; }
        public String getDisplayName() { return displayName; }
        public Map<String, Object> getExtraData() { return new HashMap<>(extraData); }

        public int getMaxChests() { return (int) extraData.getOrDefault("max-chests", 5); }
        public int getMaxDrops() { return (int) extraData.getOrDefault("max-drops", 8); }
    }

    public static class EventMobConfig {
        private final String id;
        private final int weight;
        private final int groupSize;
        private final Map<String, Object> extraData;

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
            if (section.contains("spawn-weight")) extraData.put("spawn-weight", section.getInt("spawn-weight"));

            return new EventMobConfig(id, weight, groupSize, extraData);
        }

        // Getters
        public String getId() { return id; }
        public int getWeight() { return weight; }
        public int getGroupSize() { return groupSize; }
        public Map<String, Object> getExtraData() { return new HashMap<>(extraData); }
        public int getSpawnWeight() { return (int) extraData.getOrDefault("spawn-weight", weight); }
    }

    public static class EventRewardConfig {
        private final String eventId;
        private final Map<String, Integer> rewards;
        private final Map<String, Map<String, Integer>> tieredRewards;

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
                            tierMap.put(tierKey, tierSection.getInt(tierKey));
                        }
                    }
                    tieredRewards.put(key, tierMap);
                } else {
                    rewards.put(key, section.getInt(key));
                }
            }

            return new EventRewardConfig(eventId, rewards, tieredRewards);
        }

        // Getters
        public String getEventId() { return eventId; }
        public Map<String, Integer> getRewards() { return new HashMap<>(rewards); }
        public Map<String, Map<String, Integer>> getTieredRewards() { return new HashMap<>(tieredRewards); }

        public int getReward(String key) { return rewards.getOrDefault(key, 0); }
        public int getTieredReward(String category, String tier) {
            return tieredRewards.getOrDefault(category, new HashMap<>()).getOrDefault(tier, 0);
        }
    }

    // Add this method inside your EventConfigManager.java class
    public EventLocationConfig getRandomLocationConfig(String category) {
        List<EventLocationConfig> locations = eventLocations.get(category);
        if (locations == null || locations.isEmpty()) {
            plugin.getLogger().warning("Aucune localisation trouvée pour la catégorie: " + category);
            return null;
        }

        // Weighted selection
        int totalWeight = locations.stream().mapToInt(EventLocationConfig::getWeight).sum();
        if (totalWeight <= 0) { // Fallback for no/invalid weights
            return locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
        }
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

        int currentWeight = 0;
        for (EventLocationConfig location : locations) {
            currentWeight += location.getWeight();
            if (randomWeight < currentWeight) {
                return location;
            }
        }

        // Fallback
        return locations.getFirst();
    }

    public List<EventLocationConfig> getEventLocationConfigs(String category) {
        return this.eventLocations.getOrDefault(category, new ArrayList<>());
    }
}