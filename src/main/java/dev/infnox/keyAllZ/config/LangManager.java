package dev.infnox.keyAllZ.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LangManager {

    private final JavaPlugin plugin;
    private final File langFile;
    private FileConfiguration langConfig;
    private final Map<String, String> cache = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.langFile = new File(plugin.getDataFolder(), "lang.yml");
        reload();
    }

    public void reload() {
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Add defaults from JAR
        InputStream defConfigStream = plugin.getResource("lang.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defConfig);
        }

        cache.clear();
    }

    public String getString(String path) {
        return cache.computeIfAbsent(path, p -> langConfig.getString(p, p));
    }

    public Component getComponent(String path) {
        return mm.deserialize(getString(path));
    }

    public Component getComponent(String path, Map<String, String> placeholders) {
        String msg = getString(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return mm.deserialize(msg);
    }

    public Component getComponent(String path, String... placeholders) {
        String msg = getString(path);
        if (placeholders != null && placeholders.length >= 2) {
            for (int i = 0; i < placeholders.length; i += 2) {
                msg = msg.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return mm.deserialize(msg);
    }
}
