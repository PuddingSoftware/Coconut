package net.minestom.server.entity.old.ai.sensor;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.old.ai.MobBrain;
import net.minestom.server.entity.old.ai.SimulationLevel;
import net.minestom.server.entity.old.ai.memory.Memories;

import java.util.ArrayList;

public final class TerrainSensor implements Sensor {
    @Override
    public int interval(MobBrain brain) {
        return brain.settings().scheduling().sensorInterval();
    }

    @Override
    public boolean essential() {
        return true;
    }

    @Override
    public void sense(MobBrain brain) {
        if (brain.simulationLevel() != SimulationLevel.FULL && !brain.inDanger()
                && brain.memory().get(Memories.THREAT) == null) return;
        var origin = brain.mob().getPosition();
        var positions = new ArrayList<Vec>();
        if (brain.terrain().safe(brain.mob(), origin)) positions.add(origin.asVec());
        int samples = Math.max(1, brain.policy().terrainSamples());
        double radius = Math.max(1, brain.policy().explorationRange());
        for (int i = 0; i < samples; i++) {
            double angle = brain.random().nextDouble() * Math.PI * 2;
            double distance = 2 + brain.random().nextDouble() * radius;
            Vec candidate = brain.terrain().ground(brain.mob(), origin.x() + Math.cos(angle) * distance,
                    origin.y(), origin.z() + Math.sin(angle) * distance);
            if (candidate != null && !positions.contains(candidate)) positions.add(candidate);
        }
        brain.memory().remember(Memories.SAFE_POSITIONS, new Memories.Locations(positions),
                brain.settings().scheduling().observationLifetime());
    }
}
