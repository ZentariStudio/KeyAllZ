package dev.infnox.keyAllZ.commands;

import dev.infnox.keyAllZ.KeyAllZ;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.config.ConfigManager;
import dev.infnox.keyAllZ.rewards.RewardExecutor;
import dev.infnox.keyAllZ.timer.Timer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class KeyAllZCommands implements CommandExecutor, TabCompleter {

    private final KeyAllZ plugin;
    private final RewardExecutor rewardExecutor;
    private final ConfigManager configManager;
    private final Map<String, Timer> timers;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private static final String PREFIX = "<gradient:#FFD700:#FFA500><bold>KeyAllZ</bold></gradient> <dark_gray>»</dark_gray> ";
    private static final String ERROR_PREFIX = "<dark_gray>[<red>✖</red>]</dark_gray> <red>";
    private static final String SUCCESS_PREFIX = "<dark_gray>[<green>✔</green>]</dark_gray> <gray>";

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
            default -> sendError(sender, "Unknown subcommand. Type <yellow>/keyallz</yellow> for help.");
        }
        return true;
    }

    /* SUBCOMMANDS */

    private void handleStart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/keyallz start <definition> <seconds> [loop]", "Starts a new KeyAll event timer.");
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        KeyAllDefinition def = configManager.getKeyAll(defName);

        if (def == null) {
            sendError(sender, "The definition <white>'" + defName + "'</white> does not exist in config.");
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid time format: <white>'" + args[2] + "'</white> is not a number.");
            return;
        }
        if (seconds <= 0) {
            sendError(sender, "Timer duration must be greater than 0 seconds.");
            return;
        }

        boolean loop = args.length >= 4 && Boolean.parseBoolean(args[3]);

        if (timers.containsKey(defName)) {
            Timer oldTimer = timers.get(defName);
            oldTimer.stop();
            timers.remove(defName);
            sendInfo(sender, "<yellow>Overwriting</yellow> existing timer for <white>" + defName + "</white>.");
        }

        // Timer handles reminders/end
        Timer timer = new Timer(plugin, def, seconds, rewardExecutor);
        timer.setLooping(loop);

        // Apply reminder interval from config
        if (def.getReminder() != null) {
            timer.setReminderInterval(def.getReminder().getInterval());
        }

        // Start fresh (no resume)
        timer.start(false);
        timers.put(defName, timer);
        plugin.persistTimersNow();

        sendSuccess(sender, "Started KeyAll <gold>" + defName + "</gold> for <green>" + seconds + "s</green>" +
                (loop ? " <dark_gray>(Looping)</dark_gray>" : "") + ".");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/keyallz stop <definition>", "Forces a running timer to stop.");
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        Timer timer = timers.remove(defName);

        if (timer != null && timer.isRunning()) {
            timer.stop();
            plugin.persistTimersNow();
            sendSuccess(sender, "Stopped timer for <gold>" + defName + "</gold>.");
        } else if (timer != null) {
            plugin.persistTimersNow();
            sendError(sender, "There is no running timer for <white>" + defName + "</white>.");
        } else {
            sendError(sender, "There is no running timer for <white>" + defName + "</white>.");
        }
    }

    private void handleLoop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/keyallz loop <definition> <true|false>", "Toggles if a timer should repeat automatically.");
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        Timer timer = timers.get(defName);

        if (timer == null) {
            sendError(sender, "No active timer found for <white>" + defName + "</white>. Start one first!");
            return;
        }

        boolean loop = Boolean.parseBoolean(args[2]);
        timer.setLooping(loop);
        plugin.persistTimersNow();
        sendSuccess(sender, "Looping for <gold>" + defName + "</gold> set to: " + (loop ? "<green>TRUE</green>" : "<red>FALSE</red>"));
    }

    private void handleRemind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/keyallz remind <definition> <seconds>", "Sets how often chat reminders appear.");
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        Timer timer = timers.get(defName);

        if (timer == null) {
            sendError(sender, "No active timer found for <white>" + defName + "</white>.");
            return;
        }

        int interval;
        try {
            interval = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(sender, "<white>'" + args[2] + "'</white> is not a valid number.");
            return;
        }
        if (interval < 0) {
            sendError(sender, "Reminder interval cannot be negative.");
            return;
        }

        timer.setReminderInterval(interval);
        plugin.persistTimersNow();
        sendSuccess(sender, "Reminder interval for <gold>" + defName + "</gold> updated to every <green>" + interval + "s</green>.");
    }

    private void handleList(CommandSender sender) {
        if (timers.isEmpty()) {
            sendInfo(sender, "There are currently <red>no active timers</red>.");
            return;
        }

        sender.sendMessage(mm.deserialize("<st><dark_gray>             </dark_gray></st> <gradient:#FFD700:#FFA500>Active Timers</gradient> <st><dark_gray>             </dark_gray></st>"));
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            Timer t = entry.getValue();
            String status = t.isLooping() ? "<green>Looping</green>" : "<yellow>Once</yellow>";

            sender.sendMessage(mm.deserialize(
                    " <dark_gray>»</dark_gray> <gold>" + entry.getKey() + "</gold> <dark_gray>|</dark_gray> " +
                            "<gray>Time:</gray> <white>" + t.getTimeRemaining() + "s</white> " +
                            "<dark_gray>|</dark_gray> " + status + " " +
                            "<dark_gray>|</dark_gray> <gray>Remind:</gray> <white>" + t.getReminderInterval() + "s</white>"
            ));
        }
        sender.sendMessage(mm.deserialize("<dark_gray><st>                                     </st></dark_gray>"));
    }

    private void handleReload(CommandSender sender) {
        long start = System.currentTimeMillis();
        configManager.reload();

        // Cleanup old timers
        int stopped = timers.size();
        timers.values().forEach(Timer::stop);
        timers.clear();
        plugin.persistTimersNow();

        long time = System.currentTimeMillis() - start;
        sendSuccess(sender, "Configuration reloaded in <white>" + time + "ms</white>. Stopped <red>" + stopped + "</red> active timers.");
    }

    /* UTILS */

    private void sendHelp(CommandSender sender) {
        Component header = mm.deserialize("<br><st><dark_gray>-------------</dark_gray></st> <gradient:#FFD700:#FFA500><bold>KeyAllZ Help</bold></gradient> <st><dark_gray>-------------</dark_gray></st><br>");
        sender.sendMessage(header);

        sendHelpLine(sender, "/keyallz start <def> <sec>", "Start a KeyAll event");
        sendHelpLine(sender, "/keyallz stop <def>", "Stop an active event");
        sendHelpLine(sender, "/keyallz loop <def> <bool>", "Toggle looping state");
        sendHelpLine(sender, "/keyallz remind <def> <sec>", "Set chat reminder interval");
        sendHelpLine(sender, "/keyallz list", "View active timers");
        sendHelpLine(sender, "/keyallz reload", "Reload plugin configuration");

        sender.sendMessage(mm.deserialize("<br><gray><i>Hover over commands for details.</i></gray>"));
    }

    private void sendHelpLine(CommandSender sender, String syntax, String desc) {
        String baseCmd = syntax.split(" ")[0] + " " + syntax.split(" ")[1];

        Component message = mm.deserialize(" <gold>»</gold> <gray>" + syntax + "</gray>")
                .hoverEvent(HoverEvent.showText(mm.deserialize("<yellow>Action:</yellow> <gray>" + desc + "</gray>\n<gray><i>Click to auto-complete</i></gray>")))
                .clickEvent(ClickEvent.suggestCommand(baseCmd));

        sender.sendMessage(message);
    }

    private void sendUsage(CommandSender sender, String usage, String description) {
        sender.sendMessage(mm.deserialize(ERROR_PREFIX + "Invalid usage."));
        sender.sendMessage(mm.deserialize(" <gold>»</gold> <gray>Try:</gray> <yellow>" + usage + "</yellow>"));
        sender.sendMessage(mm.deserialize(" <gold>»</gold> <gray>Info:</gray> " + description));
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(mm.deserialize(ERROR_PREFIX + message));
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(mm.deserialize(PREFIX + message));
    }

    private void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(mm.deserialize(SUCCESS_PREFIX + message));
    }

    /* TAB COMPLETION */

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
}
