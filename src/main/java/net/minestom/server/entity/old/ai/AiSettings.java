package net.minestom.server.entity.old.ai;

import java.util.Objects;

/**
 * Timing is in entity ticks, distances are in blocks. Settings belong to a brain, not to a mob species.
 */
public record AiSettings(Scheduling scheduling, Navigation navigation, Simulation simulation) {
    public AiSettings {
        Objects.requireNonNull(scheduling);
        Objects.requireNonNull(navigation);
        Objects.requireNonNull(simulation);
    }

    public AiSettings() {
        this(new Scheduling(), new Navigation(), new Simulation());
    }

    public record Scheduling(int selectionInterval, int sensorInterval, int essentialsInterval,
                             int observationLifetime, int memoryLifetime, int failureCooldown) {
        public Scheduling() {
            this(10, 20, 4, 40, 200, 100);
        }

        public Scheduling {
            if (selectionInterval < 1 || sensorInterval < 1 || essentialsInterval < 1
                    || observationLifetime < sensorInterval || memoryLifetime < observationLifetime
                    || failureCooldown < 1) throw new IllegalArgumentException("Invalid AI scheduling settings");
        }
    }

    public record Navigation(int repathInterval, double targetMovement, int stuckTicks, int requestTimeout,
                             int maxRepaths, double waypointRange, double progressDistance, int failureHistorySize) {
        public Navigation() {
            this(20, 2, 40, 200, 2, 0.35, 0.2, 32);
        }

        public Navigation {
            if (repathInterval < 1 || stuckTicks < 1 || requestTimeout < 1 || maxRepaths < 0
                    || !Double.isFinite(targetMovement) || targetMovement <= 0
                    || !Double.isFinite(waypointRange) || waypointRange <= 0
                    || !Double.isFinite(progressDistance) || progressDistance <= 0 || failureHistorySize < 1)
                throw new IllegalArgumentException("Invalid AI navigation settings");
        }
    }

    /**
     * Enter distances must be smaller than exit distances to provide ring hysteresis.
     */
    public record Simulation(boolean automatic, double fullEnter, double fullExit,
                             double essentialsEnter, double essentialsExit, int checkInterval) {
        public Simulation() {
            this(true, 32, 40, 96, 112, 20);
        }

        public Simulation {
            if (!(0 <= fullEnter && fullEnter < fullExit && fullExit < essentialsEnter
                    && essentialsEnter < essentialsExit && Double.isFinite(essentialsExit)) || checkInterval < 1)
                throw new IllegalArgumentException("Invalid AI simulation rings");
        }

        public SimulationLevel level(SimulationLevel previous, double distance) {
            if (previous == SimulationLevel.FULL && distance <= fullExit) return SimulationLevel.FULL;
            if (distance <= fullEnter) return SimulationLevel.FULL;
            double outer = previous == SimulationLevel.INACTIVE ? essentialsEnter : essentialsExit;
            return distance <= outer ? SimulationLevel.ESSENTIALS_ONLY : SimulationLevel.INACTIVE;
        }
    }
}
