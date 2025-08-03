package fr.custommobs.events.types;

import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID; /**
 * Données d'un participant à un événement
 */
public class EventParticipant {
    private final UUID playerId;
    private final String playerName;
    private final LocalDateTime joinTime;
    private int score = 0;
    private final Map<String, Object> data = new HashMap<>();

    public EventParticipant(Player player) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.joinTime = LocalDateTime.now();
    }

    // Getters et setters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public LocalDateTime getJoinTime() { return joinTime; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public void addScore(int points) { this.score += points; }

    public Object getData(String key) { return data.get(key); }
    public void setData(String key, Object value) { data.put(key, value); }
    public Map<String, Object> getAllData() { return new HashMap<>(data); }
}
