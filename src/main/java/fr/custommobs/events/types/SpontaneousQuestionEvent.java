package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Question Spontanée
 */
public class SpontaneousQuestionEvent extends ServerEvent {
    private String question;
    private String correctAnswer;
    private volatile boolean answered = false;
    private UUID winner = null;

    public SpontaneousQuestionEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventListener.EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "spontaneous_question", "Question Spontanée",
                EventType.SPONTANEOUS, 2 * 60);
    }

    @Override
    protected void onStart() {
        generateQuestion();

        // Les messages sont regroupés pour un affichage plus propre et atomique.
        String broadcastMessage = "\n" +
                "§d§l⚡ QUESTION SPONTANÉE ! ⚡\n" +
                " \n" +
                "§e§lLe premier à répondre correctement gagne une clé légendaire !\n" +
                "§6§lQuestion: §f" + question + "\n" +
                "§7§lRépondez dans le chat !\n" +
                " ";
        Bukkit.broadcastMessage(broadcastMessage);
    }

    private void generateQuestion() {
        String[][] questions = {
                {"Quel est le bloc le plus résistant dans Minecraft ?", "bedrock"},
                {"Combien de cœurs a un joueur en mode survie ?", "10"},
                {"Quel mob drop des perles de l'end ?", "enderman"},
                // CORRECTION : Le niveau des diamants a changé dans les versions récentes.
                // Si votre serveur est en 1.18+, -58 serait plus correct. J'utilise 11 comme valeur classique.
                {"Quel est le niveau le plus bas pour trouver du diamant (avant la 1.18) ?", "11"},
                {"Combien de durabilité a une pioche en diamant ?", "1561"},
                {"Dans quel biome les slimes apparaissent-ils naturellement ?", "swamp"},
                // CORRECTION : Il faut 36 lingots (4 blocs) pour un Golem de Fer.
                {"Combien de lingots de fer faut-il pour fabriquer un Golem de Fer ?", "36"},
                {"Quel est le nom du boss du Nether ?", "wither"},
                {"Quelle potion donne un effet de Régénération ?", "regeneration"},
                {"Combien de blocs un piston collant peut-il tirer ?", "12"}
        };

        String[] selectedQuestion = questions[ThreadLocalRandom.current().nextInt(questions.length)];
        this.question = selectedQuestion[0];
        this.correctAnswer = normalize(selectedQuestion[1]);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String s = ChatColor.stripColor(input);
        s = s.replaceAll("(?i)&[0-9A-FK-ORX]", "");
        s = s.replaceAll("§[0-9A-FK-ORX]", "");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        return s;
    }

    /**
     * Gère la réponse d'un joueur.
     * La logique est exécutée sur le thread principal pour éviter les problèmes de concurrence.
     */
    public void onPlayerAnswer(Player player, String answer) {
        if (answered) return;
        String normalized = normalize(answer);
        boolean match = Pattern.compile("\\b" + Pattern.quote(correctAnswer) + "\\b").matcher(normalized).find();
        if (!match) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                // On revérifie la condition au cas où un autre joueur aurait répondu
                // dans le même "tick" de serveur. C'est une double sécurité.
                if (answered) {
                    return;
                }

                answered = true;
                winner = player.getUniqueId();

                String winMessage = "\n" +
                        "§a§l[QUESTION] §2" + player.getName() + " a trouvé la bonne réponse !\n" +
                        "§6§lRéponse: §f" + correctAnswer + "\n" +
                        " ";
                Bukkit.broadcastMessage(winMessage);

                // Récompense immédiate
                PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                        .addItem(prisonHook.createKey("legendary"));
                prisonHook.giveEventReward(player, reward);

                try {
                    fr.prisontycoon.PrisonTycoon pt = fr.prisontycoon.PrisonTycoon.getInstance();
                    if (pt != null) {
                        pt.getQuestManager().addProgress(player, fr.prisontycoon.quests.QuestType.WIN_SPONTANEOUS_EVENT, 1);
                    }
                } catch (Throwable ignored) {}

                // Terminer l'événement après un court délai
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        forceEnd();
                    }
                }.runTaskLater(plugin, 60L); // 3 secondes
            }
        }.runTask(plugin);
    }

    @Override
    protected void onEnd() {
        if (!answered) {
            Bukkit.broadcastMessage("§c§l[QUESTION] §7Temps écoulé ! Personne n'a trouvé la bonne réponse. C'était : §f" + correctAnswer);
        }
    }

    @Override
    protected void onCleanup() {
        // Rien à nettoyer
    }
}