package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom; /**
 * 11.6 Course au Butin - Événement compétitif
 */
public class TreasureHuntEvent extends ServerEvent {
    private LivingEntity treasureLutin;
    private Location mineLocation;
    private final Map<UUID, Integer> damageScore = new ConcurrentHashMap<>();
    private boolean lutinCaptured = false;
    private UUID capturedBy = null;

    public TreasureHuntEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventListener.EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "treasure_hunt", "Course au Butin",
                EventType.COMPETITIVE, 15 * 60);
    }

    @Override
    protected void onStart() {
        // Choisir une mine pour le lutin
        mineLocation = selectRandomMineLocation();

        Bukkit.broadcastMessage("§6§l💰 COURSE AU BUTIN COMMENCÉE ! 💰");
        Bukkit.broadcastMessage("§e§lLutin trésorier détecté en mine §a" + getMineName(mineLocation));
        Bukkit.broadcastMessage("§c§lCompétition: Infligez des dégâts pour gagner !");

        // Spawner le lutin après 10 secondes
        BukkitTask spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnTreasureLutin();
                applyReputationModifiers();
            }
        }.runTaskLater(plugin, 200L);

        tasks.add(spawnTask);
    }

    private void spawnTreasureLutin() {
        treasureLutin = plugin.getMobManager().spawnCustomMob("lutin_treasure", mineLocation);

        if (treasureLutin != null) {
            treasureLutin.setMetadata("treasure_lutin", new FixedMetadataValue(plugin, true));
            treasureLutin.setCustomName("§6§l✦ Lutin Trésorier ✦");

            Bukkit.broadcastMessage("§6§l[COURSE] §eLe lutin est apparu ! Trouvez-le vite !");

            // Effets visuels
            mineLocation.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mineLocation, 100, 5, 5, 5, 0.5);
            mineLocation.getWorld().playSound(mineLocation, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.5f);
        }
    }

    private void applyReputationModifiers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PrisonTycoonHook.ReputationLevel repLevel = prisonHook.getReputationLevel(player);

            // Retirer anciens effets
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);

            switch (repLevel) {
                case NEGATIVE:
                case VERY_NEGATIVE:
                    // Réputation négative: Avantages
                    player.sendMessage("§c§l[COURSE] §7Votre mauvaise réputation vous aide à traquer !");
                    break;

                case NEUTRAL:
                    // Neutre: Légère pénalité vision
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, durationSeconds * 20, 0));
                    player.sendMessage("§7§l[COURSE] §7Réputation neutre: vision légèrement réduite.");
                    break;

                case POSITIVE:
                    // Réputation positive: Pénalités vision moyennes
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationSeconds * 20, 0));
                    player.sendMessage("§a§l[COURSE] §7Bonne réputation: vision réduite pour équilibrer.");
                    break;

                case VERY_POSITIVE:
                    // Très positive: Pénalités sévères
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationSeconds * 20, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, durationSeconds * 20, 1));
                    player.sendMessage("§2§l[COURSE] §7Très bonne réputation: vision très réduite !");
                    break;
            }
        }
    }

    public void onLutinDamaged(Player attacker, double damage) {
        if (treasureLutin == null || treasureLutin.isDead()) return;

        addParticipant(attacker);

        double healthPercent = treasureLutin.getHealth() / treasureLutin.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        int points = (int) (20 * (1.0 - healthPercent)); // Plus de points si plus de vie retirée

        damageScore.merge(attacker.getUniqueId(), points, Integer::sum);

        attacker.sendMessage("§6§l[COURSE] §7+" + points + " points de dégâts !");

        // Vérifier si le lutin est capturé (très peu de vie)
        if (healthPercent <= 0.1 && !lutinCaptured) {
            captureLutin(attacker);
        }
    }

    private void captureLutin(Player capturer) {
        lutinCaptured = true;
        capturedBy = capturer.getUniqueId();

        Bukkit.broadcastMessage("§6§l[COURSE] §a" + capturer.getName() + " §ea capturé le lutin !");

        // Récompense spéciale pour la capture
        treasureLutin.getWorld().spawnParticle(Particle.FIREWORK, treasureLutin.getLocation(), 50, 3, 3, 3, 0.3);
        treasureLutin.getWorld().playSound(treasureLutin.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);

        // Terminer l'événement plus tôt
        new BukkitRunnable() {
            @Override
            public void run() {
                forceEnd();
            }
        }.runTaskLater(plugin, 100L); // 5 secondes pour célébrer
    }

    @Override
    protected void onEnd() {
        // Nettoyer le lutin
        if (treasureLutin != null && !treasureLutin.isDead()) {
            treasureLutin.getWorld().spawnParticle(Particle.CLOUD, treasureLutin.getLocation(), 30);
            treasureLutin.remove();
        }

        // Calculer les résultats
        calculateResults();

        // Distribuer les récompenses
        distributeRewards();

        if (lutinCaptured) {
            Bukkit.broadcastMessage("§6§l[COURSE] §aLutin capturé ! Course terminée !");
        } else {
            Bukkit.broadcastMessage("§c§l[COURSE] §7Le lutin s'est échappé...");
        }
    }

    private void calculateResults() {
        List<Map.Entry<UUID, Integer>> ranking = damageScore.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();

        Bukkit.broadcastMessage("§6§l=== CLASSEMENT COURSE AU BUTIN ===");
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            UUID playerId = ranking.get(i).getKey();
            int score = ranking.get(i).getValue();
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Joueur déconnecté";

            String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
            Bukkit.broadcastMessage(medal + " §e" + name + " §7- §c" + score + " points");
        }
    }

    private void distributeRewards() {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            // Récompenses de base
            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .beacons(50 + ThreadLocalRandom.current().nextInt(200))
                    .tokens(2500 + ThreadLocalRandom.current().nextInt(7500))
                    .reputation(-5, "Participation Course au Butin")
                    .addItem(prisonHook.createKey("rare"));

            // Bonus capture
            if (playerId.equals(capturedBy)) {
                reward.multiply(2.0)
                        .addItem(prisonHook.createKey("legendary"))
                        .beacons(reward.getBeacons() + 50)
                        .reputation(reward.getReputation() - 5, "Capture Lutin");
            }

            // Bonus performance
            int score = damageScore.getOrDefault(playerId, 0);
            if (score >= 250) {
                reward.reputation(reward.getReputation() - 5, "Performance Course");
            }

            prisonHook.giveEventReward(player, reward);
        }
    }

    private Location selectRandomMineLocation() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private String getMineName(Location location) {
        return "Test";
    }

    @Override
    protected void onCleanup() {
        damageScore.clear();
    }
}
