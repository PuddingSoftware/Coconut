package net.minestom.server.pathfinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class PathfinderScheduler {

    static final ExecutorService PATHING_EXECUTOR_SERVICE =
            Executors.newWorkStealingPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PATHING_EXECUTOR_SERVICE.shutdown();
            try {
                if (!PATHING_EXECUTOR_SERVICE.awaitTermination(5, TimeUnit.SECONDS)) {
                    PATHING_EXECUTOR_SERVICE.shutdownNow();
                }
            } catch (InterruptedException _) {
                PATHING_EXECUTOR_SERVICE.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }
}