package dev.infnox.keyAllZ.timer;

import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.rewards.RewardExecutor;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class Timer {

    private final JavaPlugin plugin;
    private final KeyAllDefinition definition;
    private final RewardExecutor rewardExecutor;

    private int remainingSeconds;
    private final int totalSeconds;
    private boolean running;
    private boolean looping;
    private int reminderInterval = 10;

    private ScheduledTask task;

    public Timer(JavaPlugin plugin, KeyAllDefinition definition, int totalSeconds, int remainingSeconds, RewardExecutor rewardExecutor) {
        this.plugin = plugin;
        this.definition = definition;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = remainingSeconds;
        this.rewardExecutor = rewardExecutor;
    }

    public Timer(JavaPlugin plugin, KeyAllDefinition definition, int totalSeconds, RewardExecutor rewardExecutor) {
        this(plugin, definition, totalSeconds, totalSeconds, rewardExecutor);
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    public void setReminderInterval(int reminderInterval) {
        this.reminderInterval = reminderInterval;
    }

    public int getReminderInterval() {
        return reminderInterval;
    }

    public String getName() {
        return definition.getName();
    }

    public KeyAllDefinition getDefinition() {
        return definition;
    }

    /**
     * Starts the timer.
     * @param resume If true, continues from current remainingSeconds. If false, resets to totalSeconds.
     */
    public void start(boolean resume) {
        stop();

        if (!resume) {
            this.remainingSeconds = totalSeconds;
            // Clear executed cache for this new run
            rewardExecutor.clearExecuted(definition.getName());
        }

        this.running = true;

        this.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!running) {
                scheduledTask.cancel();
                return;
            }

            if (remainingSeconds <= 0) {
                handleEnd();
                return;
            }

            handleTick();
            remainingSeconds--;

        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = false;
    }

    private void handleTick() {
        KeyAllDefinition.ReminderDefinition reminder = definition.getReminder();

        // No reminders configured or reminders disabled
        if (reminder == null || !reminder.isEnabled()) {
            return;
        }

        int interval = reminder.getInterval();

        if (remainingSeconds <= 5 || (interval > 0 && remainingSeconds % interval == 0)) {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                rewardExecutor.sendReminder(definition, player, remainingSeconds);
            }
        }
    }


    private void handleEnd() {
        // Execute Rewards
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                rewardExecutor.execute(definition, player);
            }
        }
        rewardExecutor.executeGlobalCommands(definition);

        // Loop Logic
        if (looping) {
            remainingSeconds = totalSeconds;
            rewardExecutor.clearExecuted(definition.getName());
            // Timer keeps running after remainingSeconds reset
        } else {
            stop();
        }
    }

    public int getTimeRemaining() {
        return remainingSeconds;
    }

    public int getTotalTime() {
        return totalSeconds;
    }

    public boolean isRunning() {
        return running;
    }
}