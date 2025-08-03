package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID; /**
 * Guerre des Gangs (implémentation de base)
 */
public class GangWarEvent extends ServerEvent {
    private final Map<String, Integer> gangScores = new HashMap<>();
    private final Map<UUID, String> playerGangs = new HashMap<>();

    public GangWarEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "gang_war", "Guerre des Gangs",
                EventType.COMPETITIVE, 2 * 60 * 60); // 2 heures
    }

    @Override
    protected void onStart() {
        Bukkit.broadcastMessage("§4§l⚔ GUERRE DES GANGS ! ⚔");
        Bukkit.broadcastMessage("§c§lLes gangs s'affrontent pour la domination !");
        Bukkit.broadcastMessage("§7§lObjectifs: PvP, contrôle d'avant-postes, collecte...");

        setupGangWar();
    }

    private void setupGangWar() {
        // Logique pour configurer la guerre des gangs
        // À développer selon les besoins spécifiques
    }

    @Override
    protected void onEnd() {
        // Calculer les résultats et distribuer les récompenses
        Bukkit.broadcastMessage("§4§l[GUERRE] §cGuerre des Gangs terminée !");
    }

    @Override
    protected void onCleanup() {
        gangScores.clear();
        playerGangs.clear();
    }
}
