package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom; /**
 * 11.5 Contenir la Brèche - Événement coopératif
 */
public class BreachContainmentEvent extends ServerEvent {
    private static final int WAVES_COUNT = 4;
    private static final int WAVE_DURATION = 5 * 60; // 5 minutes par vague

    private int currentWave = 0;
    private Location breachLocation;
    private final List<LivingEntity> spawnedMobs = new ArrayList<>();
    private final Map<UUID, Integer> eliminationScore = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> protectionScore = new ConcurrentHashMap<>();

    public BreachContainmentEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "breach_containment", "Contenir la Brèche",
                EventType.COOPERATIVE, 20 * 60);
    }

    @Override
    protected void onStart() {
        // Choisir une mine aléatoire pour la brèche
        breachLocation = selectRandomMineLocation();

        Bukkit.broadcastMessage("§4§l⚠ BRÈCHE DIMENSIONNELLE DÉTECTÉE ! ⚠");
        Bukkit.broadcastMessage("§c§lUne rupture s'est ouverte en mine §e" + getMineName(breachLocation));
        Bukkit.broadcastMessage("§6§lCoopérez pour contenir l'invasion ! (4 vagues - 5min chacune)");

        // Créer des effets visuels à la brèche
        createBreachEffects();

        // Démarrer la première vague après 30 secondes
        BukkitTask waveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (active && currentWave < WAVES_COUNT) {
                    startNextWave();
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 600L, WAVE_DURATION * 20L); // 30s puis toutes les 5min

        tasks.add(waveTask);
    }

    private void startNextWave() {
        currentWave++;

        Bukkit.broadcastMessage("§c§l[BRÈCHE] §4Vague " + currentWave + "/" + WAVES_COUNT + " commence !");

        // Jouer des sons d'alarme
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.5f);
        }

        // Spawner les monstres de la vague
        spawnWaveMonsters();

        // Appliquer les modificateurs de réputation
        applyReputationModifiers();
    }

    private void spawnWaveMonsters() {
        int mobCount = 5 + (currentWave * 3); // Plus de mobs par vague
        String[] mobTypes = {"zombie_warrior", "skeleton_archer", "spider_venomous", "enderman_shadow", "witch_cursed"};

        for (int i = 0; i < mobCount; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    String mobType = mobTypes[ThreadLocalRandom.current().nextInt(mobTypes.length)];
                    Location spawnLoc = breachLocation.clone().add(
                            ThreadLocalRandom.current().nextDouble(-10, 10),
                            1,
                            ThreadLocalRandom.current().nextDouble(-10, 10)
                    );

                    LivingEntity mob = plugin.getMobManager().spawnCustomMob(mobType, spawnLoc);
                    if (mob != null) {
                        mob.setMetadata("breach_mob", new FixedMetadataValue(plugin, true));
                        mob.setMetadata("wave", new FixedMetadataValue(plugin, currentWave));
                        spawnedMobs.add(mob);

                        // Effets visuels de spawn
                        spawnLoc.getWorld().spawnParticle(Particle.PORTAL, spawnLoc, 30, 1, 1, 1, 0.5);
                        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                    }
                }
            }.runTaskLater(plugin, i * 20L); // 1 mob par seconde
        }
    }

    private void applyReputationModifiers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PrisonTycoonHook.ReputationLevel repLevel = prisonHook.getReputationLevel(player);

            // Retirer les anciens effets
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.WEAKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);

            switch (repLevel) {
                case POSITIVE:
                case VERY_POSITIVE:
                    // Réputation positive: Bonus normaux
                    player.sendMessage("§a§l[BRÈCHE] §7Votre bonne réputation vous donne des bonus !");
                    break;

                case NEUTRAL:
                    // Neutre: Légères pénalités
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, WAVE_DURATION * 20, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, WAVE_DURATION * 20, 0));
                    player.sendMessage("§7§l[BRÈCHE] §7Réputation neutre: légères pénalités appliquées.");
                    break;

                case NEGATIVE:
                    // Réputation négative: Pénalités moyennes
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, WAVE_DURATION * 20, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, WAVE_DURATION * 20, 1));
                    player.sendMessage("§c§l[BRÈCHE] §7Mauvaise réputation: pénalités moyennes appliquées.");
                    break;

                case VERY_NEGATIVE:
                    // Très négative: Pénalités sévères
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, WAVE_DURATION * 20, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, WAVE_DURATION * 20, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, WAVE_DURATION * 20, 0));
                    player.sendMessage("§4§l[BRÈCHE] §7Très mauvaise réputation: pénalités sévères appliquées !");
                    break;
            }
        }
    }

    public void onMobKilled(LivingEntity mob, Player killer) {
        if (!mob.hasMetadata("breach_mob")) return;

        int wave = mob.getMetadata("wave").getFirst().asInt();
        int points = 5 + (wave * 5); // Plus de points pour les vagues tardives

        eliminationScore.merge(killer.getUniqueId(), points, Integer::sum);
        addParticipant(killer);

        killer.sendMessage("§a§l[BRÈCHE] §7+" + points + " points (Élimination vague " + wave + ")");

        // Retirer de la liste
        spawnedMobs.remove(mob);
    }

    public void onPlayerTakeDamage(Player player, double damage) {
        if (!participants.contains(player.getUniqueId())) return;

        int protectionPoints = (int) damage;
        protectionScore.merge(player.getUniqueId(), protectionPoints, Integer::sum);

        if (protectionPoints >= 10) {
            player.sendMessage("§b§l[BRÈCHE] §7+" + protectionPoints + " points (Protection)");
        }
    }

    @Override
    protected void onEnd() {
        // Nettoyer les mobs restants
        for (LivingEntity mob : spawnedMobs) {
            if (!mob.isDead()) {
                mob.getWorld().spawnParticle(Particle.SMOKE, mob.getLocation(), 20);
                mob.remove();
            }
        }

        // Calculer les résultats
        calculateResults();

        // Distribuer les récompenses
        distributeRewards();

        Bukkit.broadcastMessage("§a§l[BRÈCHE] §2Brèche dimensionnelle contenue avec succès !");
    }

    private void calculateResults() {
        // Créer le classement combiné
        Map<UUID, Integer> totalScores = new HashMap<>();

        for (UUID playerId : participants) {
            int eliminations = eliminationScore.getOrDefault(playerId, 0);
            int protection = protectionScore.getOrDefault(playerId, 0);
            totalScores.put(playerId, eliminations + protection);
        }

        // Afficher le classement
        List<Map.Entry<UUID, Integer>> ranking = totalScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();

        Bukkit.broadcastMessage("§6§l=== RÉSULTATS BRÈCHE ===");
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            UUID playerId = ranking.get(i).getKey();
            int score = ranking.get(i).getValue();
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Joueur déconnecté";

            String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : "§c🥉";
            Bukkit.broadcastMessage(medal + " §e" + name + " §7- §a" + score + " points");
        }
    }

    private void distributeRewards() {
        boolean victory = currentWave >= WAVES_COUNT;

        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            // Récompenses de base
            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .beacons(100 + ThreadLocalRandom.current().nextInt(150))
                    .tokens(5000 + ThreadLocalRandom.current().nextInt(20000))
                    .reputation(5, "Participation Brèche")
                    .addItem(prisonHook.createKey("rare"));

            // Bonus victoire
            if (victory) {
                reward.multiply(2.0)
                        .addItem(prisonHook.createKey("legendary"))
                        .beacons(reward.getBeacons() + 100);
            }

            // Bonus performance
            int totalScore = eliminationScore.getOrDefault(playerId, 0) + protectionScore.getOrDefault(playerId, 0);
            if (totalScore >= 500) {
                reward.reputation(reward.getReputation() + 5, "Performance Brèche");
            }

            prisonHook.giveEventReward(player, reward);
        }
    }

    private Location selectRandomMineLocation() {
        // Logique pour sélectionner une mine aléatoire
        // Pour l'exemple, on utilise le spawn du monde
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private String getMineName(Location location) {
        return "Test"; // À implémenter selon la logique des mines
    }

    private void createBreachEffects() {
        BukkitTask effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }

                breachLocation.getWorld().spawnParticle(Particle.PORTAL, breachLocation, 50, 5, 5, 5, 1);
                breachLocation.getWorld().playSound(breachLocation, Sound.BLOCK_PORTAL_AMBIENT, 2.0f, 0.5f);
            }
        }.runTaskTimer(plugin, 0L, 60L);

        tasks.add(effectTask);
    }

    @Override
    protected void onCleanup() {
        spawnedMobs.clear();
        eliminationScore.clear();
        protectionScore.clear();
    }
}
