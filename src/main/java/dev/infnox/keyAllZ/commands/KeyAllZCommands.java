package dev.infnox.keyAllZ.commands;

import dev.infnox.keyAllZ.KeyAllZ;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.config.ConfigManager;
import dev.infnox.keyAllZ.config.LangManager;
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
    private final LangManager lang;
    private final Map<String, Timer> timers;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final List<String> subCommands = List.of("start", "stop", "loop", "remind", "list", "reload");

    public KeyAllZCommands(KeyAllZ plugin, RewardExecutor rewardExecutor, ConfigManager configManager) {
        this.plugin = plugin;
        this.rewardExecutor = rewardExecutor;
        this.configManager = configManager;
        this.lang = plugin.getLangManager();
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
            default -> sendError(sender, lang.getString("commands.unknown-subcommand"));
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
            sendError(sender, lang.getString("commands.start.def-not-found").replace("%definition%", defName));
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(sender, lang.getString("commands.start.invalid-time").replace("%input%", args[2]));
            return;
        }
        if (seconds <= 0) {
            sendError(sender, lang.getString("commands.start.time-too-low"));
            return;
        }

        boolean loop = args.length >= 4 && Boolean.parseBoolean(args[3]);

        if (timers.containsKey(defName)) {
            Timer oldTimer = timers.get(defName);
            oldTimer.stop();
            timers.remove(defName);
            sendInfo(sender, lang.getString("commands.start.overwriting").replace("%definition%", defName));
        }

        Timer timer = new Timer(plugin, def, seconds, rewardExecutor);
        timer.setLooping(loop);

        if (def.getReminder() != null) {
            timer.setReminderInterval(def.getReminder().getInterval());
        }

        if (plugin.getRedisSync() != null) {
            timer.setSyncListener(plugin.getRedisSync());
        }

        timer.start(false);
        timers.put(defName, timer);
        plugin.persistTimersNow();

        String loopingTag = loop ? lang.getString("commands.start.looping-tag") : "";
        sendSuccess(sender, lang.getString("commands.start.success")
                .replace("%definition%", defName)
                .replace("%seconds%", String.valueOf(seconds))
                .replace("%looping%", loopingTag));
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
            sendSuccess(sender, lang.getString("commands.stop.success").replace("%definition%", defName));
        } else {
            plugin.persistTimersNow();
            sendError(sender, lang.getString("commands.stop.not-running").replace("%definition%", defName));
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
            sendError(sender, lang.getString("commands.loop.no-timer").replace("%definition%", defName));
            return;
        }

        boolean loop = Boolean.parseBoolean(args[2]);
        timer.setLooping(loop);
        if (plugin.getRedisSync() != null) plugin.getRedisSync().publishLoopSet(defName, loop);
        plugin.persistTimersNow();
        
        String state = loop ? lang.getString("commands.loop.state-true") : lang.getString("commands.loop.state-false");
        sendSuccess(sender, lang.getString("commands.loop.success")
                .replace("%definition%", defName)
                .replace("%state%", state));
    }

    private void handleRemind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/keyallz remind <definition> <seconds>", "Sets how often chat reminders appear.");
            return;
        }

        String defName = args[1].toLowerCase(Locale.ROOT);
        Timer timer = timers.get(defName);

        if (timer == null) {
            sendError(sender, lang.getString("commands.remind.no-timer").replace("%definition%", defName));
            return;
        }

        int interval;
        try {
            interval = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(sender, lang.getString("commands.remind.invalid-number").replace("%input%", args[2]));
            return;
        }
        if (interval < 0) {
            sendError(sender, lang.getString("commands.remind.negative-interval"));
            return;
        }

        timer.setReminderInterval(interval);
        if (plugin.getRedisSync() != null) plugin.getRedisSync().publishRemindSet(defName, interval);
        plugin.persistTimersNow();
        sendSuccess(sender, lang.getString("commands.remind.success")
                .replace("%definition%", defName)
                .replace("%interval%", String.valueOf(interval)));
    }

    private void handleList(CommandSender sender) {
        if (timers.isEmpty()) {
            sendInfo(sender, lang.getString("commands.list.no-timers"));
            return;
        }

        sender.sendMessage(lang.getComponent("commands.list.header"));
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            Timer t = entry.getValue();
            String status = t.isLooping() ? lang.getString("commands.list.status-looping") : lang.getString("commands.list.status-once");

            sender.sendMessage(lang.getComponent("commands.list.line",
                    "definition", entry.getKey(),
                    "time", String.valueOf(t.getTimeRemaining()),
                    "status", status,
                    "remind", String.valueOf(t.getReminderInterval())));
        }
        sender.sendMessage(lang.getComponent("commands.list.footer"));
    }

    private void handleReload(CommandSender sender) {
        long start = System.currentTimeMillis();

        // Broadcast reload to other servers before reloading locally
        if (plugin.getRedisSync() != null) plugin.getRedisSync().publishReload();

        configManager.reload();

        // Update existing timers with new definitions
        List<String> toRemove = new ArrayList<>();
        int updated = 0;
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            KeyAllDefinition newDef = configManager.getKeyAll(entry.getKey());
            if (newDef == null) {
                entry.getValue().stop();
                toRemove.add(entry.getKey());
            } else {
                entry.getValue().setDefinition(newDef);
                updated++;
            }
        }
        toRemove.forEach(timers::remove);
        plugin.persistTimersNow();

        long time = System.currentTimeMillis() - start;
        String stoppedTag = toRemove.isEmpty() ? "." : lang.getString("commands.reload.stopped-count").replace("%count%", String.valueOf(toRemove.size()));
        
        sendSuccess(sender, lang.getString("commands.reload.success")
                .replace("%time%", String.valueOf(time))
                .replace("%updated%", String.valueOf(updated))
                .replace("%stopped%", stoppedTag));
    }

    /* UTILS */

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(lang.getComponent("commands.help.header"));

        sendHelpLine(sender, "/keyallz start <def> <sec>", "Start a KeyAll event");
        sendHelpLine(sender, "/keyallz stop <def>", "Stop an active event");
        sendHelpLine(sender, "/keyallz loop <def> <bool>", "Toggle looping state");
        sendHelpLine(sender, "/keyallz remind <def> <sec>", "Set chat reminder interval");
        sendHelpLine(sender, "/keyallz list", "View active timers");
        sendHelpLine(sender, "/keyallz reload", "Reload plugin configuration");

        sender.sendMessage(lang.getComponent("commands.help.footer"));
    }

    private void sendHelpLine(CommandSender sender, String syntax, String desc) {
        String baseCmd = syntax.split(" ")[0] + " " + syntax.split(" ")[1];

        Component message = lang.getComponent("commands.help.line", "syntax", syntax)
                .hoverEvent(HoverEvent.showText(lang.getComponent("commands.help.line-hover", "description", desc)))
                .clickEvent(ClickEvent.suggestCommand(baseCmd));

        sender.sendMessage(message);
    }

    private void sendUsage(CommandSender sender, String usage, String description) {
        sender.sendMessage(lang.getComponent("error-prefix").append(lang.getComponent("commands.invalid-usage")));
        sender.sendMessage(lang.getComponent("commands.usage-try", "usage", usage));
        sender.sendMessage(lang.getComponent("commands.usage-info", "description", description));
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(lang.getComponent("error-prefix").append(mm.deserialize(message)));
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(lang.getComponent("prefix").append(mm.deserialize(message)));
    }

    private void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(lang.getComponent("success-prefix").append(mm.deserialize(message)));
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
