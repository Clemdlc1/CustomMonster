package fr.custommobs.events;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.events.types.*;
import fr.custommobs.mobs.CustomMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Listener principal pour gérer les interactions des événements
 */
public class EventListener implements Listener {

    private final CustomMobsPlugin plugin;
    private final EventScheduler scheduler;

    public EventListener(CustomMobsPlugin plugin, EventScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /**
     * Gère les dégâts aux entités pour les événements
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (!(event.getDamager() instanceof Player)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();
        Player player = (Player) event.getDamager();

        // Brèche - Monstres de brèche
        if (entity.hasMetadata("breach_mob")) {
            ServerEvent breachEvent = scheduler.getActiveEvent("breach_containment");
            if (breachEvent instanceof BreachContainmentEvent) {
                // Les dégâts sont gérés dans l'événement de mort
            }
        }

        // Course au Butin - Lutin trésorier
        if (entity.hasMetadata("treasure_lutin")) {
            ServerEvent huntEvent = scheduler.getActiveEvent("treasure_hunt");
            if (huntEvent instanceof TreasureHuntEvent) {
                ((TreasureHuntEvent) huntEvent).onLutinDamaged(player, event.getFinalDamage());
            }
        }

        // Boss Quotidien
        if (entity.hasMetadata("daily_boss")) {
            ServerEvent bossEvent = scheduler.getActiveEvent("daily_boss");
            if (bossEvent instanceof DailyBossEvent) {
                ((DailyBossEvent) bossEvent).onBossDamaged(player, event.getFinalDamage());
            }
        }
    }

    /**
     * Gère la mort des entités pour les événements
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // Brèche - Mort d'un monstre de brèche
        if (entity.hasMetadata("breach_mob")) {
            ServerEvent breachEvent = scheduler.getActiveEvent("breach_containment");
            if (breachEvent instanceof BreachContainmentEvent) {
                ((BreachContainmentEvent) breachEvent).onMobKilled(entity, killer);
            }
        }

        // Boss Quotidien - Mort du boss
        if (entity.hasMetadata("daily_boss")) {
            ServerEvent bossEvent = scheduler.getActiveEvent("daily_boss");
            if (bossEvent instanceof DailyBossEvent) {
                ((DailyBossEvent) bossEvent).onBossKilled(killer);
            }
        }

        // Mise à jour du compteur global de monstres pour les défis communautaires
        if (CustomMob.isCustomMob(entity)) {
            // Ajouter à un compteur global si nécessaire
        }
    }

    /**
     * Gère les interactions avec les blocs (coffres de trésor)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();

        // Chasse au Trésor - Coffres cachés
        if (event.getClickedBlock().hasMetadata("treasure_chest")) {
            ServerEvent treasureEvent = scheduler.getActiveEvent("treasure_search");
            if (treasureEvent instanceof TreasureSearchEvent) {
                ((TreasureSearchEvent) treasureEvent).onTreasureFound(player, event.getClickedBlock().getLocation());
            }
        }

        // Largage - Coffres de largage
        if (event.getClickedBlock().hasMetadata("supply_drop")) {
            // Le joueur récupère automatiquement le contenu
            player.sendMessage("§c§l[LARGAGE] §aVous avez récupéré un coffre de largage !");
        }
    }

    /**
     * Gère le chat pour les questions spontanées
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Vérifier s'il y a une question spontanée active
        for (ServerEvent activeEvent : scheduler.getActiveEvents()) {
            if (activeEvent instanceof SpontaneousQuestionEvent) {
                ((SpontaneousQuestionEvent) activeEvent).onPlayerAnswer(player, message);
                break;
            }
        }
    }
}