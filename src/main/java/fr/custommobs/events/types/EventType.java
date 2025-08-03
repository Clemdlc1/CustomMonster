package fr.custommobs.events.types;

/**
 * Enum pour les types d'événements
 */
public enum EventType {
    COOPERATIVE("§a§lCoopératif", "Événement d'entraide"),
    COMPETITIVE("§c§lCompétitif", "Événement de compétition"),
    NEUTRAL("§7§lNeutre", "Événement neutre"),
    COMMUNITY("§b§lCommunautaire", "Défi communautaire"),
    SPONTANEOUS("§d§lSpontané", "Événement impromptu");

    private final String displayName;
    private final String description;

    EventType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
