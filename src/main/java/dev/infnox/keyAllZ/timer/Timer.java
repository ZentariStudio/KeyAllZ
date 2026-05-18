package dev.infnox.keyAllZ.timer;

import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.redis.TimerSyncListener;
import dev.infnox.keyAllZ.rewards.RewardExecutor;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class Timer {

    private final JavaPlugin plugin;
    private KeyAllDefinition definition;
    private final RewardExecutor rewardExecutor;

    private int remainingSeconds;
    private int totalSeconds;
    private boolean running;
    private boolean looping;
    private int reminderInterval = 10;

    private String cycleId;
    private int syncTickCount = 0;
    private TimerSyncListener syncListener;

    private ScheduledTask task;

    public Timer(JavaPlugin plugin, KeyAllDefinition definition, int totalSeconds, int remainingSeconds, RewardExecutor rewardExecutor) {
        this.plugin = plugin;
        this.definition = definition;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = remainingSeconds;
        this.rewardExecutor = rewardExecutor;
        this.cycleId = UUID.randomUUID().toString();
    }

    public Timer(JavaPlugin plugin, KeyAllDefinition definition, int totalSeconds, RewardExecutor rewardExecutor) {
        this(plugin, definition, totalSeconds, totalSeconds, rewardExecutor);
    }

    public void setSyncListener(TimerSyncListener listener) {
        this.syncListener = listener;
    }

    public void setDefinition(KeyAllDefinition definition) {
        this.definition = definition;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    public void setReminderInterval(int reminderInterval) {
        this.reminderInterval = Math.max(0, reminderInterval);
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

    public String getCycleId() {
        return cycleId;
    }

    public void start(boolean resume) {
        cancelTask();

        if (!resume) {
            remainingSeconds = totalSeconds;
            cycleId = UUID.randomUUID().toString();
            rewardExecutor.clearExecuted(definition.getName());
        }

        running = true;
        syncTickCount = 0;
        startSchedulerTask();

        if (syncListener != null) {
            syncListener.onTimerStarted(definition.getName(), totalSeconds, remainingSeconds, looping, reminderInterval, cycleId);
        }
    }

    public void startReplicated(int remaining, String cycleId) {
        cancelTask();
        remainingSeconds = remaining;
        this.cycleId = cycleId;
        running = true;
        syncTickCount = 0;
        startSchedulerTask();
    }

    public void stop() {
        boolean wasRunning = running;
        cancelTask();
        if (wasRunning && syncListener != null) {
            syncListener.onTimerStopped(definition.getName());
        }
    }

    public void stopReplicated() {
        cancelTask();
    }

    public void syncRemainingTime(int remaining, String eventCycleId) {
        if (cycleId.equals(eventCycleId)) {
            remainingSeconds = remaining;
        }
    }

    public void resetCycleReplicated(int total, String newCycleId) {
        cycleId = newCycleId;
        remainingSeconds = total;
        syncTickCount = 0;
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = false;
    }

    private void startSchedulerTask() {
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
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

    private void handleTick() {
        syncTickCount++;

        final KeyAllDefinition.ReminderDefinition reminder = definition.getReminder();
        final int rem = remainingSeconds;
        
        if (reminder != null && reminder.isEnabled()) {
            if (rem <= 5 || (reminderInterval > 0 && rem % reminderInterval == 0)) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                    Collection<? extends Player> online = Bukkit.getOnlinePlayers();
                    if (!online.isEmpty()) {
                        for (Player player : online) {
                            rewardExecutor.sendReminder(definition, player, rem);
                        }
                    }
                });
            }
        }

        if (syncListener != null && syncTickCount % 5 == 0) {
            final String name  = definition.getName();
            final String cid   = cycleId;
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    t -> syncListener.onTimerTick(name, rem, totalSeconds, cid));
        }
    }

    private void handleEnd() {
        triggerRewards();

        if (looping) {
            String newCycleId = UUID.randomUUID().toString();
            cycleId = newCycleId;
            remainingSeconds = totalSeconds;
            syncTickCount = 0;
            if (syncListener != null) {
                syncListener.onTimerCycleReset(definition.getName(), totalSeconds, newCycleId);
            }
        } else {
            cancelTask();
            if (syncListener != null) {
                syncListener.onTimerEnded(definition.getName());
            }
        }
    }

    public void triggerRewards() {
        final List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        final String name = definition.getName();
        final String cid = cycleId;

        final TimerSyncListener listener = syncListener;

        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            List<Player> eligible;
            if (listener != null && !online.isEmpty()) {
                eligible = listener.filterClaimedPlayers(name, cid, online);
            } else {
                eligible = online;
            }

            for (Player player : eligible) {
                if (!player.isOnline()) continue;
                try {
                    rewardExecutor.execute(definition, player, cid);
                } catch (Exception e) {
                    plugin.getLogger().warning("[KeyAllZ] Error executing reward for " + player.getName() + ": " + e.getMessage());
                }
            }

            boolean runGlobal = listener == null || listener.claimGlobalCommands(name, cid);
            if (runGlobal) {
                rewardExecutor.executeGlobalOnlyCommands(definition);
            }
        });
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
