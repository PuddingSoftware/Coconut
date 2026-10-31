package net.minestom.server.entity.ai.sensing;

import net.minestom.server.Tickable;
import net.minestom.server.entity.EntityMob;
import org.jetbrains.annotations.NotNull;

// observes the world and keeps it stored in memory
public abstract class Sensor implements Tickable {

    protected final EntityMob entityMob;

    private final long ticksBetweenSense;
    private long ticksSinceLastSense;

    public Sensor(@NotNull EntityMob entityMob,
                  long ticksBetweenSense) {
        this.entityMob = entityMob;
        this.ticksBetweenSense = ticksBetweenSense;
    }

    // logic to run when a sensor activates
    public abstract void sense();

    // check if the sensor can activate
    @Override
    public void tick(long time) {
        if (--ticksSinceLastSense <= 0L) {
            ticksSinceLastSense = ticksBetweenSense;
            sense();
        }
    }
}