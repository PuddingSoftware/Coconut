package net.minestom.server.entity.ai.sensing.types;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.ai.sensing.Sensor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

// keep track of the nearest entity
public final class NearestLivingEntitySensor extends Sensor {

    private final double range;

    public NearestLivingEntitySensor(@NotNull EntityMob entityMob,
                                     int ticksBetweenSense,
                                     double range) {
        super(entityMob, ticksBetweenSense);
        this.range = range;
    }

    @Override
    public void sense() {
        final Pos position = entityMob.getPosition();
        final Collection<Entity> nearbyEntities = entityMob.getInstance().getNearbyEntities(position, range);

        LivingEntity closest = null;
        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof LivingEntity))
                continue;

            if (closest == null)
                closest = (LivingEntity) entity;

            if (closest.getDistanceSquared(position) > entity.getDistanceSquared(position))
                closest = (LivingEntity) entity;
        }
    }
}