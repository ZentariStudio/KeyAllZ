package dev.infnox.keyAllZ.rewards;

import com.tcoded.folialib.FoliaLib;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RewardExecutor {

    private final JavaPlugin plugin;
    private final FoliaLib foliaLib;
    private final Set<String> executed = ConcurrentHashMap.newKeySet();
    private final MiniMessage mm = MiniMessage.miniMessage();

    private static final Duration TITLE_FADE_IN  = Duration.ofMillis(500);
    private static final Duration TITLE_STAY     = Duration.ofSeconds(2);
    private static final Duration TITLE_FADE_OUT = Duration.ofMillis(500);

    public RewardExecutor(JavaPlugin plugin) {
        this.plugin    = plugin;
        this.foliaLib  = new FoliaLib(plugin);
    }

    public void execute(KeyAllDefinition def, Player player, String cycleId) {
        if (!hasPermission(def, player)) return;

        String localKey = def.getName() + ":" + player.getUniqueId() + ":" + cycleId;
        if (!executed.add(localKey)) return;

        foliaLib.getScheduler().runAtEntity(player, task -> runPlayerRewards(def, player));
    }

    public void executeGlobalOnlyCommands(KeyAllDefinition def) {
        foliaLib.getScheduler().runNextTick(task -> runConsoleCommands(def));
    }

    public void sendReminder(KeyAllDefinition def, Player player, int secondsRemaining) {
        var reminder = def.getReminder();
        if (reminder == null) return;

        foliaLib.getScheduler().runAtEntity(player, task -> {
            String title = parse(reminder.getTitle(), player, def, secondsRemaining);
            String ab    = parse(reminder.getActionbar(), player, def, secondsRemaining);
            String chat  = parse(reminder.getMessage(), player, def, secondsRemaining);

            if (notEmpty(title)) sendComponent(player, title, ComponentType.TITLE);
            if (notEmpty(ab))    sendComponent(player, ab,    ComponentType.ACTIONBAR);
            if (notEmpty(chat))  sendComponent(player, chat,  ComponentType.CHAT);

            if (notEmpty(reminder.getSound())) {
                playSound(player, reminder.getSound(), reminder.getSoundVolume(), reminder.getSoundPitch());
            }
        });
    }

    public void clearExecuted(String keyAllName) {
        String prefix      = keyAllName + ":";
        String prefixLower = prefix.toLowerCase(Locale.ROOT);
        executed.removeIf(k -> k.startsWith(prefix) || k.toLowerCase(Locale.ROOT).startsWith(prefixLower));
    }

    public void stopSound(Player player, String soundKey) {
        foliaLib.getScheduler().runAtEntity(player, task -> player.stopSound(soundKey));
    }

    public void stopAllSounds(Player player) {
        foliaLib.getScheduler().runAtEntity(player, task -> player.stopAllSounds());
    }

    private boolean hasPermission(KeyAllDefinition def, Player player) {
        String perm = def.getPermission();
        return perm == null || perm.isEmpty() || player.hasPermission(perm);
    }

    private void runPlayerRewards(KeyAllDefinition def, Player player) {
        // Visuals and Sounds
        if (notEmpty(def.getTitle()))      sendComponent(player, parse(def.getTitle(),      player, def, 0), ComponentType.TITLE);
        if (notEmpty(def.getActionbar()))  sendComponent(player, parse(def.getActionbar(),  player, def, 0), ComponentType.ACTIONBAR);
        if (notEmpty(def.getChatMessage()))sendComponent(player, parse(def.getChatMessage(),player, def, 0), ComponentType.CHAT);

        if (notEmpty(def.getSound())) {
            playSound(player, def.getSound(), def.getSoundVolume(), def.getSoundPitch());
        }

        // Player-run commands
        if (def.getPlayerCommands() != null && !def.getPlayerCommands().isEmpty()) {
            for (String cmd : def.getPlayerCommands()) {
                String parsed = parse(cmd, player, def, 0);
                if (notEmpty(parsed)) {
                    try {
                        player.performCommand(parsed);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[KeyAllZ] Player command '" + parsed + "' failed for " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Console-run commands (PLAYER: prefixed)
        // These are now run here to take advantage of the per-player region thread (runAtEntity)
        // and to avoid the "delayed" look of the old batcher.
        if (def.getConsoleCommands() != null && !def.getConsoleCommands().isEmpty()) {
            for (String cmd : def.getConsoleCommands()) {
                if (cmd.startsWith("PLAYER:")) {
                    String actualCmd = cmd.substring(7).trim(); // Skip "PLAYER:"
                    String parsed = parse(actualCmd, player, def, 0);
                    if (notEmpty(parsed)) {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                        } catch (Exception e) {
                            plugin.getLogger().warning("[KeyAllZ] Console command '" + parsed + "' failed for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void runConsoleCommands(KeyAllDefinition def) {
        if (def.getConsoleCommands() == null || def.getConsoleCommands().isEmpty()) return;
        for (String cmd : def.getConsoleCommands()) {
            if (!cmd.startsWith("PLAYER:") && notEmpty(cmd)) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } catch (Exception e) {
                    plugin.getLogger().warning("[KeyAllZ] Global console command '" + cmd + "' failed: " + e.getMessage());
                }
            }
        }
    }

    private void sendComponent(Player player, String text, ComponentType type) {
        if (!notEmpty(text)) return;
        switch (type) {
            case TITLE -> {
                player.showTitle(Title.title(
                        mm.deserialize(text),
                        Component.empty(),
                        Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)
                ));
            }
            case ACTIONBAR -> player.sendActionBar(mm.deserialize(text));
            case CHAT      -> player.sendMessage(mm.deserialize(text));
        }
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), soundName, volume > 0 ? volume : 1f, pitch > 0 ? pitch : 1f);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound '" + soundName + "' for " + player.getName() + ": " + e.getMessage());
        }
    }

    private String formatRemainingTime(int seconds) {
        if (seconds <= 0) return "0s";
        int mins = seconds / 60;
        int secs = seconds % 60;
        return mins > 0 ? mins + "m " + secs + "s" : secs + "s";
    }

    private String parse(String text, Player player, KeyAllDefinition def, int secondsRemaining) {
        if (text == null || text.isEmpty()) return "";
        
        if (!text.contains("%")) return text;

        text = text.replace("%player%", player.getName())
                   .replace("%keyall%", def.getName());
        
        if (secondsRemaining > 0) {
            text = text.replace("%time%", String.valueOf(secondsRemaining))
                       .replace("%remaining-time%", formatRemainingTime(secondsRemaining));
        }

        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? PlaceholderAPI.setPlaceholders(player, text)
                : text;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private enum ComponentType { TITLE, ACTIONBAR, CHAT }
}
