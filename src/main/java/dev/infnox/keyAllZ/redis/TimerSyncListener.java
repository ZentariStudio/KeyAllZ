package dev.infnox.keyAllZ.redis;

import org.bukkit.entity.Player;

import java.util.List;

public interface TimerSyncListener {
    void onTimerStarted(String defName, int total, int remaining, boolean looping, int reminderInterval, String cycleId);
    void onTimerStopped(String defName);
    void onTimerEnded(String defName);
    void onTimerCycleReset(String defName, int total, String cycleId);
    void onTimerTick(String defName, int remaining, int total, String cycleId);
    boolean claimGlobalCommands(String defName, String cycleId);

    List<Player> filterClaimedPlayers(String defName, String cycleId, List<Player> players);
}
