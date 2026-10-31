package net.minestom.demo.mobs;

import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ai.activity.ActivityHandler;
import net.minestom.server.entity.ai.sensing.Sensor;
import net.minestom.server.entity.ai.sensing.SensorHandler;
import net.minestom.server.entity.ai.sensing.types.NearestLivingEntitySensor;

import java.util.List;
import java.util.function.Function;

public final class SheepMob extends EntityMob {

    private static final Function<EntityMob, List<Sensor>> DEFAULT_SENSORS = entityMob -> List.of(
            new NearestLivingEntitySensor(entityMob, 20, 20.0D)
    );

    public SheepMob() {
        super(EntityType.SHEEP);
    }

    @Override
    protected void initializeBrain() {
        final SensorHandler sensorHandler = brain.getSensorHandler();
        for (Sensor sensor : DEFAULT_SENSORS.apply(this)) {
            sensorHandler.addSensor(sensor);
        }

        // setup activities
        initializeCoreActivity();
    }

    private void initializeCoreActivity() {
        final ActivityHandler activityHandler = brain.getActivityHandler();
    }
}