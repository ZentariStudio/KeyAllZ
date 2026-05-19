package dev.infnox.keyAllZ.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<String, KeyAllDefinition> keyAlls = new HashMap<>();
    private final LangManager langManager;

    public ConfigManager(JavaPlugin plugin, LangManager langManager) {
        this.plugin = plugin;
        this.langManager = langManager;
        reload();
    }

    /**
     * Reloads config and parses all KeyAll definitions
     */
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        langManager.reload();
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
            keyAlls.put(name.toLowerCase(Locale.ROOT), def);
        }
    }

    public KeyAllDefinition getKeyAll(String name) {
        return keyAlls.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<KeyAllDefinition> getAllKeyAlls() {
        return keyAlls.values();
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
