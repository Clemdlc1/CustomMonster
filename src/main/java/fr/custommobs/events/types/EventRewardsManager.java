package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;

// Classe pour gérer les récompenses (simplifiée pour l'exemple)
public class EventRewardsManager {
    private final CustomMobsPlugin plugin;
    private final PrisonTycoonHook prisonHook;

    public EventRewardsManager(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook) {
        this.plugin = plugin;
        this.prisonHook = prisonHook;
    }

    // Méthodes pour créer et distribuer les récompenses
}
