package dev.infnox.keyAllZ.rewards;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RewardExecutor {

    private final JavaPlugin plugin;
    private final FoliaLib foliaLib;
    private final Set<String> executed = ConcurrentHashMap.newKeySet(); // thread-safe
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Constants for tuning
    private static final Duration TITLE_FADE_IN = Duration.ofMillis(500);
    private static final Duration TITLE_STAY = Duration.ofSeconds(2);
    private static final Duration TITLE_FADE_OUT = Duration.ofMillis(500);
    private static final int CONSOLE_BATCH_SIZE = 50;

    public RewardExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.foliaLib = new FoliaLib(plugin);
    }

    public void execute(KeyAllDefinition def, Player player) {
        if (!hasPermission(def, player)) return;

        String key = def.getName() + ":" + player.getUniqueId();
        if (!executed.add(key)) {
            logSkipped(player, def.getName(), key);
            return;
        }

        // Player-specific rewards
        foliaLib.getScheduler().runAtEntity(player, task -> runPlayerRewards(def, player));
    }
    public void executeGlobalCommands(KeyAllDefinition def) {
        // Global console-only commands
        foliaLib.getScheduler().runNextTick(task -> runConsoleCommands(def));

        // Player batch console commands (PLAYER:)
        runPlayerBatchConsoleCommands(def, plugin);
    }

    public void sendReminder(KeyAllDefinition def, Player player, int secondsRemaining) {
        var reminder = def.getReminder();
        if (reminder == null) return;

        foliaLib.getScheduler().runAtEntity(player, task -> {
            sendComponent(player, parse(reminder.getTitle(), player, def, secondsRemaining), ComponentType.TITLE);
            sendComponent(player, parse(reminder.getActionbar(), player, def, secondsRemaining), ComponentType.ACTIONBAR);
            sendComponent(player, parse(reminder.getChat(), player, def, secondsRemaining), ComponentType.CHAT);

            if (notEmpty(reminder.getSound())) {
                playSound(player, reminder.getSound(), reminder.getSoundVolume(), reminder.getSoundPitch());
            }
        });
    }

    public void clearExecuted(String keyAllName) {
        int before = executed.size();
        executed.removeIf(k -> k.startsWith(keyAllName + ":"));
        int after = executed.size();
        plugin.getLogger().info("[KeyAllZ] Cleared executed keys for '" + keyAllName + "'. Before: " + before + ", After: " + after);
        logExecutedSet(keyAllName);
    }

    public void stopSound(Player player, String soundKey) {
        foliaLib.getScheduler().runAtEntity(player, task -> player.stopSound(soundKey));
    }

    public void stopAllSounds(Player player) {
        foliaLib.getScheduler().runAtEntity(player, task -> player.stopAllSounds());
    }


    private boolean hasPermission(KeyAllDefinition def, Player player) {
        String perm = def.getPermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
            return false;
        }
        return true;
    }

    private void runPlayerRewards(KeyAllDefinition def, Player player) {
        sendComponent(player, parse(def.getTitle(), player, def, 0), ComponentType.TITLE);
        sendComponent(player, parse(def.getActionbar(), player, def, 0), ComponentType.ACTIONBAR);
        sendComponent(player, parse(def.getChatMessage(), player, def, 0), ComponentType.CHAT);

        if (notEmpty(def.getSound())) {
            playSound(player, def.getSound(), def.getSoundVolume(), def.getSoundPitch());
        }

        if (def.getPlayerCommands() != null && !def.getPlayerCommands().isEmpty()) {
            def.getPlayerCommands().stream()
                    .map(cmd -> parse(cmd, player, def, 0))
                    .filter(this::notEmpty)
                    .distinct()
                    .forEach(player::performCommand);
        }
    }

    private void runConsoleCommands(KeyAllDefinition def) {
        if (def.getConsoleCommands() == null || def.getConsoleCommands().isEmpty()) return;
        Set<String> consoleCommands = new LinkedHashSet<>(def.getConsoleCommands());

        for (String cmd : consoleCommands) {
            if (cmd.startsWith("PLAYER:")) {
                // handled separately in runPlayerBatchConsoleCommands
                continue;
            }
            if (notEmpty(cmd)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    public static void runPlayerBatchConsoleCommands(KeyAllDefinition def, JavaPlugin plugin) {
        if (def.getConsoleCommands() == null || def.getConsoleCommands().isEmpty()) return;
        FoliaLib foliaLib = new FoliaLib(plugin);
        Set<String> consoleCommands = new LinkedHashSet<>(def.getConsoleCommands());

        for (String cmd : consoleCommands) {
            if (cmd.startsWith("PLAYER:")) {
                String actualCmd = cmd.substring("PLAYER:".length()).trim();

                List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> new RewardExecutor(plugin).hasPermission(def, p))
                        .collect(Collectors.toList());

                if (eligiblePlayers.isEmpty()) continue;

                final int total = eligiblePlayers.size();
                final int[] index = {0};

                Consumer<WrappedTask> batchConsumer = new Consumer<>() {
                    @Override
                    public void accept(WrappedTask task) {
                        int end = Math.min(index[0] + CONSOLE_BATCH_SIZE, total);
                        for (int i = index[0]; i < end; i++) {
                            Player target = eligiblePlayers.get(i);
                            String replaced = actualCmd.replace("%player%", target.getName());
                            String parsed = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                                    ? PlaceholderAPI.setPlaceholders(target, replaced)
                                    : replaced;
                            if (parsed != null && !parsed.isEmpty()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                            }
                        }
                        index[0] = end;
                        if (index[0] < total) {
                            foliaLib.getScheduler().runNextTick(this);
                        }
                    }
                };

                foliaLib.getScheduler().runNextTick(batchConsumer);
            }
        }
    }

    private void sendComponent(Player player, String text, ComponentType type) {
        if (!notEmpty(text)) return;

        switch (type) {
            case TITLE -> {
                Title title = Title.title(
                        mm.deserialize(text),
                        Component.empty(),
                        Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)
                );
                player.showTitle(title);
            }
            case ACTIONBAR -> player.sendActionBar(mm.deserialize(text));
            case CHAT -> player.sendMessage(mm.deserialize(text));
        }
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(),
                    soundName,
                    volume > 0 ? volume : 1f,
                    pitch > 0 ? pitch : 1f);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound for KeyAll for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void logSkipped(Player player, String defName, String key) {
        logExecutedSet(defName);
    }

    private void logExecutedSet(String keyAllName) {
        long count = executed.stream().filter(k -> k.startsWith(keyAllName + ":")).count();
    }

    private String formatRemainingTime(int seconds) {
        if (seconds <= 0) return "0s";
        int mins = seconds / 60;
        int secs = seconds % 60;
        return mins > 0 ? mins + "m " + secs + "s" : secs + "s";
    }

    private String parse(String text, Player player, KeyAllDefinition def, int secondsRemaining) {
        if (text == null || text.isEmpty()) return "";

        text = text.replace("%player%", player.getName())
                .replace("%keyall%", def.getName())
                .replace("%time%", String.valueOf(secondsRemaining))
                .replace("%remaining-time%", formatRemainingTime(secondsRemaining));

        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? PlaceholderAPI.setPlaceholders(player, text)
                : text;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private enum ComponentType { TITLE, ACTIONBAR, CHAT }
}
