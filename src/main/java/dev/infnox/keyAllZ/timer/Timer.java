package dev.infnox.keyAllZ.timer;

import dev.infnox.keyAllZ.rewards.RewardExecutor;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Timer {

    private final JavaPlugin plugin;
    private final String name;
    private final int totalSeconds;

    private int remainingSeconds;
    private boolean running;
    private boolean looping;
    private ScheduledTask task;

    private final List<Runnable> endActions = new ArrayList<>();
    private final List<Runnable> tickActions = new ArrayList<>();
    private final RewardExecutor rewardExecutor;

    public Timer(JavaPlugin plugin, String name, int totalSeconds, RewardExecutor rewardExecutor) {
        this.plugin = plugin;
        this.name = name;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = totalSeconds;
        this.rewardExecutor = rewardExecutor;
    }


    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    public String getName() {
        return name;
    }

    public void addEndAction(Runnable action) {
        endActions.add(action);
    }

    public void addTickAction(Runnable action) {
        tickActions.add(action);
    }

    /**
     * Starts or restarts the timer.
     */
    public void start() {
        stop();
        remainingSeconds = totalSeconds;
        running = true;

        // reset reward execution state for this timer
        rewardExecutor.clearExecuted(name);

        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (remainingSeconds <= 0) {
                runEndActions();
                if (looping) {
                    remainingSeconds = totalSeconds;
                    rewardExecutor.clearExecuted(name);
                } else {
                    stop();
                }
                return;
            }

            runTickActions();
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

    /**
     * Resets the timer without starting it.
     */
    public void reset() {
        stop();
        remainingSeconds = totalSeconds;
    }


    private void runEndActions() {
        for (Runnable action : endActions) {
            try {
                action.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[" + name + "] End action error", e);
            }
        }
    }

    private void runTickActions() {
        for (Runnable action : tickActions) {
            try {
                action.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[" + name + "] Tick action error", e);
            }
        }
    }


    public int getTimeRemaining() {
        return remainingSeconds;
    }

    public int getTotalTime() {
        return totalSeconds;
    }

    public int getElapsedTime() {
        return totalSeconds - remainingSeconds;
    }

    public double getProgress() {
        return 1.0 - ((double) remainingSeconds / (double) totalSeconds);
    }

    public boolean isRunning() {
        return running;
    }
}
