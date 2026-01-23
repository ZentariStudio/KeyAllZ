package dev.infnox.keyAllZ;

import dev.infnox.keyAllZ.commands.KeyAllZCommands;
import dev.infnox.keyAllZ.config.ConfigManager;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.placeholder.KeyAllZPlaceholderExpansion;
import dev.infnox.keyAllZ.rewards.RewardExecutor;
import dev.infnox.keyAllZ.timer.Timer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class KeyAllZ extends JavaPlugin {

    private RewardExecutor rewardExecutor;
    private ConfigManager configManager;

    private final Map<String, Timer> timers = new HashMap<>();
    private File timersFile;

    @Override
    public void onEnable() {

        printStartupBanner();


        if (!isPaperOrFolia()) {
            getLogger().severe("====================================================");
            getLogger().severe(" [!] SPIGOT DETECTED [!]");
            getLogger().severe(" There are currently issues with Spigot support.");
            getLogger().severe(" KeyAllZ works best on Paper or Folia.");
            getLogger().severe(" Please consider joining the discord for a");
            getLogger().severe(" Spigot-specific version until the next update.");
            getLogger().severe(" Discord: https://discord.gg/fdngRyKjUA");
            getLogger().severe("====================================================");
        }

        saveDefaultConfig();

        configManager = new ConfigManager(this);

        // Load definitions logic
        for (KeyAllDefinition def : configManager.getAllKeyAlls()) {
            getLogger().info("Loaded KeyAll definition: " + def.getName());
        }

        rewardExecutor = new RewardExecutor(this);

        new KeyAllZCommands(this, rewardExecutor, configManager);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new KeyAllZPlaceholderExpansion(this).register();
            getLogger().info("Registered KeyAllZ PlaceholderAPI expansion.");
        }

        // Load saved timers
        loadTimers();

         new Metrics(this, 21830);

        getLogger().info("KeyAllZ enabled successfully!");
    }

    @Override
    public void onDisable() {
        saveTimers();
        getLogger().info("KeyAllZ disabled!");
    }

    public RewardExecutor getRewardExecutor() {
        return rewardExecutor;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Map<String, Timer> getTimers() {
        return timers;
    }


    private void printStartupBanner() {
        String version = getDescription().getVersion();
        Bukkit.getConsoleSender().sendMessage(MiniMessage.miniMessage().deserialize(
                "<br>" +
                        "<gradient:#FFD700:#FFA500><bold>KeyAllZ</bold></gradient> <gray>v" + version + "</gray><br>" +
                        "<gray>Running on:</gray> <white>" + Bukkit.getName() + " " + Bukkit.getVersion() + "</white><br>" +
                        "<gray>Developed by:</gray> <white>luvtoxic</white><br>"
        ));
    }

    private boolean isPaperOrFolia() {
        try {
            Class.forName("io.papermc.paper.util.Tick");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    private void loadTimers() {
        timersFile = new File(getDataFolder(), "saved_timers.yml");
        if (!timersFile.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(timersFile);
        ConfigurationSection section = data.getConfigurationSection("timers");
        if (section == null) return;

        int restored = 0;
        for (String key : section.getKeys(false)) {
            String defName = section.getString(key + ".definition");
            if (defName == null) continue;

            KeyAllDefinition def = configManager.getKeyAll(defName);

            if (def == null) {
                getLogger().warning("Skipping saved timer '" + key + "': Definition '" + defName + "' not found.");
                continue;
            }

            int totalSeconds = section.getInt(key + ".totalSeconds");
            int remainingSeconds = section.getInt(key + ".remainingSeconds");
            boolean looping = section.getBoolean(key + ".looping");
            int reminderInterval = section.getInt(key + ".reminderInterval", 10);

            // Create and start timer
            Timer timer = new Timer(this, def, totalSeconds, remainingSeconds, rewardExecutor);
            timer.setLooping(looping);
            timer.setReminderInterval(reminderInterval);

            // Resume!
            timer.start(true);
            timers.put(def.getName().toLowerCase(), timer);
            restored++;
        }

        if (restored > 0) {
            getLogger().info("Restored " + restored + " active timers from session.");
        }

        // Delete file after load to prevent stale data if server crashes later
        timersFile.delete();
    }

    private void saveTimers() {
        if (timers.isEmpty()) return;

        if (timersFile == null) timersFile = new File(getDataFolder(), "saved_timers.yml");
        YamlConfiguration data = new YamlConfiguration();
        ConfigurationSection section = data.createSection("timers");

        int saved = 0;
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            Timer timer = entry.getValue();
            if (!timer.isRunning()) continue;

            String path = entry.getKey();
            section.set(path + ".definition", timer.getDefinition().getName());
            section.set(path + ".totalSeconds", timer.getTotalTime());
            section.set(path + ".remainingSeconds", timer.getTimeRemaining());
            section.set(path + ".looping", timer.isLooping());
            section.set(path + ".reminderInterval", timer.getReminderInterval());
            saved++;
        }

        if (saved > 0) {
            try {
                data.save(timersFile);
                getLogger().info("Saved " + saved + " active timers.");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not save active timers!", e);
            }
        }
    }
}