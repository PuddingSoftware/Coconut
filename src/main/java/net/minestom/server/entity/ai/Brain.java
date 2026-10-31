package net.minestom.server.entity.ai;

import net.minestom.server.Tickable;
import net.minestom.server.entity.ai.activity.ActivityHandler;
import net.minestom.server.entity.ai.memory.MemoryHandler;
import net.minestom.server.entity.ai.sensing.SensorHandler;
import org.jetbrains.annotations.NotNull;

// handles the mob AI for an EntityMob
// maintains a memory which gets updated through sensors
// activities are set based on memories
// schedules behavior based on the current activity
public final class Brain implements Tickable {

    private final ActivityHandler activityHandler;
    private final SensorHandler sensorHandler;
    private final MemoryHandler memoryHandler;

    public Brain() {
        this.activityHandler = new ActivityHandler();
        this.sensorHandler = new SensorHandler();
        this.memoryHandler = new MemoryHandler();
    }

    @NotNull
    public ActivityHandler getActivityHandler() {
        return activityHandler;
    }

    @NotNull
    public SensorHandler getSensorHandler() {
        return sensorHandler;
    }

    @NotNull
    public MemoryHandler getMemoryHandler() {
        return memoryHandler;
    }

    @Override
    public void tick(long time) {
        memoryHandler.tick(time);
        sensorHandler.tick(time);
    }
}