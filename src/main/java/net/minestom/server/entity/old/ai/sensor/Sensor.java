package net.minestom.server.entity.old.ai.sensor;

import net.minestom.server.entity.old.ai.MobBrain;

/**
 * Sensors run on the entity tick thread and should publish bounded, immutable observations.
 */
public interface Sensor {
    int interval(MobBrain brain);

    default boolean essential() {
        return false;
    }

    void sense(MobBrain brain);
}
