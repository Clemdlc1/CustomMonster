package fr.custommobs;

import fr.custommobs.api.PrisonTycoonHook;
import fr.custommobs.commands.LootConfigCommand;
import fr.custommobs.commands.SpawnMobCommand;
import fr.custommobs.events.EventScheduler;
import fr.custommobs.listeners.BossStatsListener;
import fr.custommobs.listeners.MobControlListener;
import fr.custommobs.listeners.MobSpawnListener;
import fr.custommobs.listeners.MonsterDamageListener;
import fr.custommobs.managers.*;
import fr.custommobs.mobs.advanced.*;
import fr.custommobs.mobs.simple.*;
import fr.custommobs.mobs.boss.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomMobsPlugin extends JavaPlugin {

    private static CustomMobsPlugin instance;
    private CustomMobManager mobManager;
    private LootManager lootManager;
    private SpawnManager spawnManager;
    private BossBarManager bossBarManager;
    private BossStatsManager bossStatsManager;
    private PrisonTycoonHook prisonTycoonHook;
    private EventScheduler eventScheduler;

    @Override
    public void onEnable() {
        instance = this;

        // Sauvegarde la config par défaut
        saveDefaultConfig();

        // Initialise les managers
        initializeManagers();

        // Enregistre les monstres
        registerMobs();

        // Enregistre les commandes
        registerCommands();

        // Enregistre les listeners
        registerListeners();

        this.prisonTycoonHook = new PrisonTycoonHook(this);

        this.eventScheduler = new EventScheduler(this);

        getLogger().info("CustomMobs plugin activé avec succès!");
        getLogger().info("Nombre de monstres enregistrés: " + mobManager.getRegisteredMobIds().size());
        getLogger().info("Zones de spawn configurées: " + spawnManager.getSpawnZones().size());
        getLogger().info("Système de statistiques de boss activé !");
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) {
            spawnManager.stopAllSpawning();
        }
        if (lootManager != null) {
            lootManager.saveLootConfig();
        }
        if (bossBarManager != null) {
            bossBarManager.cleanup();
        }

        getLogger().info("CustomMobs plugin désactivé!");
    }

    private void initializeManagers() {
        mobManager = new CustomMobManager(this);
        lootManager = new LootManager(this);
        spawnManager = new SpawnManager(this);
        bossBarManager = new BossBarManager(this);
        bossStatsManager = new BossStatsManager(this);
    }

    private void registerMobs() {
        // Monstres simples
        mobManager.registerMob("zombie_warrior", ZombieWarrior.class);
        mobManager.registerMob("skeleton_archer", SkeletonArcher.class);
        mobManager.registerMob("spider_venomous", SpiderVenomous.class);
        mobManager.registerMob("creeper_explosive", CreeperExplosive.class);
        mobManager.registerMob("enderman_shadow", EndermanShadow.class);
        mobManager.registerMob("witch_cursed", WitchCursed.class);
        mobManager.registerMob("golem_stone", GolemStone.class);
        mobManager.registerMob("lutin_treasure", LutinTreasure.class);

        // Monstres avancés
        mobManager.registerMob("dragon_fire", DragonFire.class);
        mobManager.registerMob("necromancer_dark", NecromancerDark.class);
        mobManager.registerMob("geode_aberration", GeodeAberration.class);

        // Boss
        mobManager.registerMob("wither_boss", WitherBoss.class);
        mobManager.registerMob("warden_boss", WardenBoss.class);
        mobManager.registerMob("ravager_boss", RavagerBoss.class);
    }

    private void registerCommands() {
        getCommand("lootconfig").setExecutor(new LootConfigCommand(this));
        getCommand("spawnmob").setExecutor(new SpawnMobCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MobSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new MobControlListener(this), this);
        getServer().getPluginManager().registerEvents(new MonsterDamageListener(), this);
        getServer().getPluginManager().registerEvents(new BossStatsListener(this), this);
    }

    // Getters
    public static CustomMobsPlugin getInstance() {
        return instance;
    }

    public CustomMobManager getMobManager() {
        return mobManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public BossStatsManager getBossStatsManager() {
        return bossStatsManager;
    }

    public PrisonTycoonHook getPrisonTycoonHook() {
        return prisonTycoonHook;
    }

}