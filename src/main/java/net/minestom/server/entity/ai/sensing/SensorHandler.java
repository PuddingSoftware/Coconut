package net.minestom.server.entity.ai.sensing;

import net.minestom.server.Tickable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class SensorHandler implements Tickable {

    private final List<Sensor> sensors;

    public SensorHandler() {
        this.sensors = new ArrayList<>();
    }

    // add a sensor
    public void addSensor(@NotNull Sensor sensor) {
        sensors.add(sensor);
    }

    // TODO: remove a sensor

    // handles the ticking of our brains sensors
    @Override
    public void tick(long time) {
        for (Sensor sensor : sensors) {
            sensor.tick(time);
        }
    }
}