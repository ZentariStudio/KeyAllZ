package dev.infnox.keyAllZ;

import dev.infnox.keyAllZ.commands.KeyAllZCommands;
import dev.infnox.keyAllZ.config.ConfigManager;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.placeholder.KeyAllZPlaceholderExpansion;
import dev.infnox.keyAllZ.rewards.RewardExecutor;
import dev.infnox.keyAllZ.timer.Timer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class KeyAllZ extends JavaPlugin {

    private RewardExecutor rewardExecutor;
    private ConfigManager configManager;

    private final Map<String, KeyAllDefinition> definitions = new HashMap<>();
    private final Map<String, Timer> timers = new HashMap<>();

    @Override
    public void onEnable() {

        saveDefaultConfig();

        configManager = new ConfigManager(this);

        definitions.clear();
        for (KeyAllDefinition def : configManager.getAllKeyAlls()) {
            definitions.put(def.getName().toLowerCase(), def);
            getLogger().info("Loaded KeyAll definition: " + def.getName());
        }

        rewardExecutor = new RewardExecutor(this);

        new KeyAllZCommands(this, rewardExecutor, configManager);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new KeyAllZPlaceholderExpansion(timers).register();
            getLogger().info("Registered KeyAllZ PlaceholderAPI expansion.");
        }

        new Metrics(this, 21830);

        getLogger().info("KeyAllZ enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("KeyAllZ disabled!");
    }


    public RewardExecutor getRewardExecutor() {
        return rewardExecutor;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Map<String, KeyAllDefinition> getDefinitions() {
        return definitions;
    }

    public Map<String, Timer> getTimers() {
        return timers;
    }


}
