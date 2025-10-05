package dev.infnox.keyAllZ.commands;

import dev.infnox.keyAllZ.KeyAllZ;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.config.ConfigManager;
import dev.infnox.keyAllZ.rewards.RewardExecutor;
import dev.infnox.keyAllZ.timer.Timer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class KeyAllZCommands implements CommandExecutor, TabCompleter {

    private final KeyAllZ plugin;
    private final RewardExecutor rewardExecutor;
    private final ConfigManager configManager;
    private final Map<String, Timer> timers;

    private final Map<String, Integer> reminders = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final List<String> subCommands = List.of("start", "stop", "loop", "remind", "list", "reload");

    public KeyAllZCommands(KeyAllZ plugin, RewardExecutor rewardExecutor, ConfigManager configManager) {
        this.plugin = plugin;
        this.rewardExecutor = rewardExecutor;
        this.configManager = configManager;
        this.timers = plugin.getTimers();

        PluginCommand command = plugin.getCommand("keyallz");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "loop" -> handleLoop(sender, args);
            case "remind" -> handleRemind(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList());

        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("start", "stop", "loop", "remind").contains(sub)) {
                completions.addAll(configManager.getAllKeyAlls().stream()
                        .map(KeyAllDefinition::getName)
                        .filter(d -> d.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .toList());
            }

        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("loop".equals(sub)) {
                completions.addAll(List.of("true", "false").stream()
                        .filter(b -> b.startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .toList());
            }

        } else if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("start".equals(sub)) {
                completions.addAll(List.of("true", "false").stream()
                        .filter(b -> b.startsWith(args[3].toLowerCase(Locale.ROOT)))
                        .toList());
            }
        }

        return completions.isEmpty() ? Collections.emptyList() : completions;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /keyallz start <definition> <seconds> [loop]"));
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        KeyAllDefinition def = configManager.getKeyAll(defName);
        if (def == null) {
            sender.sendMessage(mm.deserialize("<red>No KeyAll found: <white>" + defName));
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<red>Invalid number: <white>" + args[2]));
            return;
        }

        boolean loop = args.length >= 4 && Boolean.parseBoolean(args[3]);

        if (timers.containsKey(defName)) {
            timers.get(defName).stop();
            timers.remove(defName);
            sender.sendMessage(mm.deserialize("<yellow>Stopped existing timer for <white>" + defName));
        }

        Timer timer = new Timer(plugin, defName, seconds, rewardExecutor);
        timer.setLooping(loop);

        timer.addTickAction(() -> {
            int remaining = timer.getTimeRemaining();
            int interval = reminders.getOrDefault(defName, 10);
            if (remaining % interval == 0 || remaining <= 5) {
                Bukkit.getOnlinePlayers().forEach(p -> rewardExecutor.sendReminder(def, p, remaining));
            }
        });

        timer.addEndAction(() -> {
            // run per-player rewards
            Bukkit.getOnlinePlayers().forEach(p -> rewardExecutor.execute(def, p));
            // run global/console commands once
            rewardExecutor.executeGlobalCommands(def);
        });

        timer.start();
        timers.put(defName, timer);

        sender.sendMessage(mm.deserialize("<green>Started KeyAll timer for <white>" + defName +
                " <green>(" + seconds + "s)" + (loop ? " looping." : ".")));
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Usage: /keyallz stop <definition>"));
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        Timer timer = timers.remove(defName);
        if (timer != null && timer.isRunning()) {
            timer.stop();
            sender.sendMessage(mm.deserialize("<green>Stopped timer for <white>" + defName));
        } else {
            sender.sendMessage(mm.deserialize("<red>No running timer for: <white>" + defName));
        }
    }

    private void handleLoop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /keyallz loop <definition> <true|false>"));
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        Timer timer = timers.get(defName);
        if (timer == null) {
            sender.sendMessage(mm.deserialize("<red>No running timer for: <white>" + defName));
            return;
        }

        boolean loop = Boolean.parseBoolean(args[2]);
        timer.setLooping(loop);
        sender.sendMessage(mm.deserialize("<green>Set looping for <white>" + defName + " <green>to <white>" + loop));
    }

    private void handleRemind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /keyallz remind <definition> <intervalSeconds>"));
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        int interval;
        try {
            interval = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<red>Invalid number: <white>" + args[2]));
            return;
        }

        reminders.put(defName, interval);
        sender.sendMessage(mm.deserialize("<green>Reminder interval for <white>" + defName + " <green>set to every <white>" + interval + "s."));
    }

    private void handleList(CommandSender sender) {
        if (timers.isEmpty()) {
            sender.sendMessage(mm.deserialize("<yellow>No active timers."));
            return;
        }

        sender.sendMessage(mm.deserialize("<green>Active KeyAll timers:"));
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            Timer t = entry.getValue();
            sender.sendMessage(mm.deserialize("<white>- " + entry.getKey() + ": <yellow>" +
                    t.getTimeRemaining() + "s left (looping=" + t.isLooping() + ")"));
        }
    }

    private void handleReload(CommandSender sender) {
        configManager.reload();
        timers.values().forEach(Timer::stop);
        timers.clear();
        sender.sendMessage(mm.deserialize("<green>KeyAllZ configuration reloaded and all timers stopped."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<gold>KeyAllZ Commands:"));
        sender.sendMessage(mm.deserialize("<yellow>/keyallz start <def> <seconds> [loop] <gray>- Start a timer"));
        sender.sendMessage(mm.deserialize("<yellow>/keyallz stop <def> <gray>- Stop a timer"));
        sender.sendMessage(mm.deserialize("<yellow>/keyallz loop <def> <true|false> <gray>- Toggle looping"));
        sender.sendMessage(mm.deserialize("<yellow>/keyallz remind <def> <seconds> <gray>- Set reminder interval"));
        sender.sendMessage(mm.deserialize("<yellow>/keyallz list <gray>- List running timers"));
        sender.sendMessage(mm.deserialize("<yellow>/keyallz reload <gray>- Reload config and stop all timers"));
    }
}
