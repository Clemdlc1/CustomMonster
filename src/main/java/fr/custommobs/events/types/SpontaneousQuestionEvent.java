package fr.custommobs.events.types;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.events.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom; /**
 * Question Spontanée
 */
public class SpontaneousQuestionEvent extends ServerEvent {
    private String question;
    private String correctAnswer;
    private boolean answered = false;
    private UUID winner = null;

    public SpontaneousQuestionEvent(CustomMobsPlugin plugin, PrisonTycoonHook prisonHook, EventListener.EventRewardsManager rewardsManager) {
        super(plugin, prisonHook, rewardsManager, "spontaneous_question", "Question Spontanée",
                EventType.SPONTANEOUS, 2 * 60);
    }

    @Override
    protected void onStart() {
        generateQuestion();

        Bukkit.broadcastMessage("§d§l⚡ QUESTION SPONTANÉE ! ⚡");
        Bukkit.broadcastMessage("§e§lPremier à répondre correctement gagne une clé légendaire !");
        Bukkit.broadcastMessage("§6§lQuestion: §f" + question);
        Bukkit.broadcastMessage("§7§lRépondez dans le chat !");
    }

    private void generateQuestion() {
        String[][] questions = {
                {"Quel est le bloc le plus résistant dans Minecraft ?", "bedrock"},
                {"Combien de hearts a un joueur en mode survie ?", "10"},
                {"Quel mob drop des perles d'ender ?", "enderman"},
                {"Quel est le niveau minimum pour trouver des diamants ?", "16"},
                {"Combien de durabilité a une pioche en diamant ?", "1561"},
                {"Quel biome spawn les slimes ?", "swamp"},
                {"Combien de lingots de fer faut-il pour un golem ?", "4"},
                {"Quel est le nom du boss du Nether ?", "wither"},
                {"Quelle potion donne de la régénération ?", "regeneration"},
                {"Combien de blocs peut porter un piston ?", "12"}
        };

        String[] selectedQuestion = questions[ThreadLocalRandom.current().nextInt(questions.length)];
        this.question = selectedQuestion[0];
        this.correctAnswer = selectedQuestion[1].toLowerCase();
    }

    public void onPlayerAnswer(Player player, String answer) {
        if (answered) return;

        if (answer.toLowerCase().contains(correctAnswer)) {
            answered = true;
            winner = player.getUniqueId();

            Bukkit.broadcastMessage("§a§l[QUESTION] §2" + player.getName() + " a trouvé la bonne réponse !");
            Bukkit.broadcastMessage("§6§lRéponse: §f" + correctAnswer);

            // Récompense immédiate
            PrisonTycoonHook.EventReward reward = new PrisonTycoonHook.EventReward()
                    .addItem(prisonHook.createKey("legendary"))
                     ;

            prisonHook.giveEventReward(player, reward);

            // Terminer l'événement
            new BukkitRunnable() {
                @Override
                public void run() {
                    forceEnd();
                }
            }.runTaskLater(plugin, 60L);
        }
    }

    @Override
    protected void onEnd() {
        if (!answered) {
            Bukkit.broadcastMessage("§c§l[QUESTION] §7Temps écoulé ! Réponse: §f" + correctAnswer);
        }
    }

    @Override
    protected void onCleanup() {
        // Rien à nettoyer
    }
}
