package dev.infnox.keyAllZ.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<String, KeyAllDefinition> keyAlls = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads config and parses all KeyAll definitions
     */
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadKeyAlls();
    }

    /**
     * Loads KeyAll definitions from "keyalls" section
     */
    private void loadKeyAlls() {
        keyAlls.clear();

        ConfigurationSection section = config.getConfigurationSection("keyalls");
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            ConfigurationSection keyAllSec = section.getConfigurationSection(name);
            if (keyAllSec == null) continue;

            KeyAllDefinition def = KeyAllDefinition.fromConfig(name, keyAllSec);
            keyAlls.put(name.toLowerCase(), def);
        }
    }

    public KeyAllDefinition getKeyAll(String name) {
        return keyAlls.get(name.toLowerCase());
    }

    public Collection<KeyAllDefinition> getAllKeyAlls() {
        return keyAlls.values();
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
