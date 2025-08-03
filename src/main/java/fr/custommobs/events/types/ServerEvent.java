package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Classe de base pour tous les événements serveur
 */
public abstract class ServerEvent {
    protected final CustomMobsPlugin plugin;
    protected final PrisonTycoonHook prisonHook;
    protected final EventRewardsManager rewardsManager;

    protected final String id;
    protected final String name;
    protected final EventType type;
    protected final int durationSeconds;

    protected LocalDateTime startTime;
    protected LocalDateTime endTime;
    protected boolean active = false;
    protected boolean finished = false;
    protected boolean cancelled = false;

    protected final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    protected final Map<UUID, EventParticipant> participantData = new ConcurrentHashMap<>();
    protected final List<BukkitTask> tasks = new ArrayList<>();

    public ServerEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook,
                       EventRewardsManager rewardsManager, String id, String name,
                       EventType type, int durationSeconds) {
        this.plugin = plugin;
        this.prisonHook = prisonHook;
        this.rewardsManager = rewardsManager;
        this.id = id;
        this.name = name;
        this.type = type;
        this.durationSeconds = durationSeconds;
    }

    public void start() {
        if (active) return;

        active = true;
        startTime = LocalDateTime.now();
        endTime = startTime.plusSeconds(durationSeconds);

        onStart();
        startTimer();

        plugin.getLogger().info("Événement démarré: " + name);
    }

    public void forceEnd() {
        if (!active) return;

        active = false;
        finished = true;

        onEnd();
        cleanup();

        plugin.getLogger().info("Événement terminé: " + name);
    }

    private void startTimer() {
        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (LocalDateTime.now().isAfter(endTime) || !active) {
                    forceEnd();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        tasks.add(timerTask);
    }

    public void cleanup() {
        for (BukkitTask task : tasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();

        onCleanup();
    }

    // Méthodes abstraites à implémenter
    protected abstract void onStart();
    protected abstract void onEnd();
    protected abstract void onCleanup();

    // Gestion des participants
    public void addParticipant(Player player) {
        UUID playerId = player.getUniqueId();
        if (participants.add(playerId)) {
            participantData.put(playerId, new EventParticipant(player));
            onPlayerJoin(player);
        }
    }

    public void removeParticipant(Player player) {
        UUID playerId = player.getUniqueId();
        if (participants.remove(playerId)) {
            participantData.remove(playerId);
            onPlayerLeave(player);
        }
    }

    protected void onPlayerJoin(Player player) {
        player.sendMessage("§a§l[ÉVÉNEMENT] §7Vous participez maintenant à: §e" + name);
    }

    protected void onPlayerLeave(Player player) {
        player.sendMessage("§c§l[ÉVÉNEMENT] §7Vous ne participez plus à: §e" + name);
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public EventType getType() { return type; }
    public boolean isActive() { return active; }
    public boolean isFinished() { return finished; }
    public boolean isCancelled() { return cancelled; }
    public Set<UUID> getParticipants() { return new HashSet<>(participants); }
    public int getParticipantCount() { return participants.size(); }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    public int getRemainingSeconds() {
        if (!active) return 0;
        return (int) java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
    }
}
