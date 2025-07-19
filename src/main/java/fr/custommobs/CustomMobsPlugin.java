package fr.custommobs;

import fr.custommobs.commands.LootConfigCommand;
import fr.custommobs.commands.SpawnMobCommand;
import fr.custommobs.listeners.MobControlListener;
import fr.custommobs.listeners.MobSpawnListener;
import fr.custommobs.listeners.MonsterDamageListener;
import fr.custommobs.managers.CustomMobManager;
import fr.custommobs.managers.LootManager;
import fr.custommobs.managers.SpawnManager;
import fr.custommobs.mobs.advanced.*;
import fr.custommobs.mobs.simple.*;
import fr.custommobs.mobs.boss.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomMobsPlugin extends JavaPlugin {

    private static CustomMobsPlugin instance;
    private CustomMobManager mobManager;
    private LootManager lootManager;
    private SpawnManager spawnManager;

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

        getLogger().info("CustomMobs plugin activé avec succès!");
        getLogger().info("Nombre de monstres enregistrés: " + mobManager.getRegisteredMobIds().size());
        getLogger().info("Zones de spawn configurées: " + spawnManager.getSpawnZones().size());
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) {
            spawnManager.stopAllSpawning();
        }
        if (lootManager != null) {
            lootManager.saveLootConfig();
        }

        getLogger().info("CustomMobs plugin désactivé!");
    }

    private void initializeManagers() {
        mobManager = new CustomMobManager(this);
        lootManager = new LootManager(this);
        spawnManager = new SpawnManager(this);
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

        // Monstres avancés
        mobManager.registerMob("dragon_fire", DragonFire.class);
        mobManager.registerMob("necromancer_dark", NecromancerDark.class);
        mobManager.registerMob("geode_aberration", GeodeAberration.class);

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
}