package net.minestom.server.entity.old.ai.sensor;

import net.minestom.server.entity.old.ai.MobBrain;
import net.minestom.server.entity.old.ai.memory.Memories;
import net.minestom.server.instance.block.Block;

/**
 * Cheap local observations keep hazards responsive independently of routine sensing.
 */
public final class EnvironmentSensor implements Sensor {
    @Override
    public int interval(MobBrain brain) {
        return 1;
    }

    @Override
    public boolean essential() {
        return true;
    }

    @Override
    public void sense(MobBrain brain) {
        var mob = brain.mob();
        var feet = brain.terrain().block(mob, mob.getPosition());
        var head = brain.terrain().block(mob, mob.getPosition().add(0, mob.getEyeHeight(), 0));
        boolean inWater = feet != null && feet.compare(Block.WATER);
        boolean lava = feet != null && feet.compare(Block.LAVA) || head != null && head.compare(Block.LAVA);
        boolean fire = mob.isOnFire() || feet != null
                && (feet.compare(Block.FIRE) || feet.compare(Block.SOUL_FIRE));
        boolean drowning = head != null && head.compare(Block.WATER) && mob.getEntityMeta().getAirTicks() < 60;
        int lifetime = brain.settings().scheduling().observationLifetime();
        brain.memory().remember(Memories.IN_WATER, inWater, lifetime);
        brain.memory().remember(Memories.DANGER, new Memories.Danger(fire, lava, drowning), lifetime);
    }
}
