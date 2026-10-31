package net.minestom.server.entity.old.ai.navigation;

import net.minestom.server.instance.Instance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Shared per-instance admission and in-flight limits, with FIFO admission across mobs.
 */
public final class PathRequestBudget {
    private static final PathRequestBudget SHARED = new PathRequestBudget(8, 32);

    private static final class Window {
        long tick = Long.MIN_VALUE;
        int started;
        int running;
        final LinkedHashMap<UUID, Long> waiting = new LinkedHashMap<>();
    }

    private final int startsPerTick;
    private final int maxRunning;
    private final int queueLeaseTicks;
    private final Map<Instance, Window> windows = new WeakHashMap<>();

    public PathRequestBudget(int startsPerTick, int maxRunning) {
        this(startsPerTick, maxRunning, 40);
    }

    public PathRequestBudget(int startsPerTick, int maxRunning, int queueLeaseTicks) {
        if (startsPerTick < 1 || maxRunning < 1 || queueLeaseTicks < 1)
            throw new IllegalArgumentException("Invalid path request budget");
        this.startsPerTick = startsPerTick;
        this.maxRunning = maxRunning;
        this.queueLeaseTicks = queueLeaseTicks;
    }

    public static PathRequestBudget shared() {
        return SHARED;
    }

    public synchronized boolean acquire(Instance instance, UUID mob) {
        Window window = windows.computeIfAbsent(instance, ignored -> new Window());
        long tick = instance.getWorldAge();
        if (window.tick != tick) {
            window.tick = tick;
            window.started = 0;
        }
        window.waiting.values().removeIf(lastRequest -> tick - lastRequest >= queueLeaseTicks);
        window.waiting.put(mob, tick);
        if (window.started >= startsPerTick || window.running >= maxRunning
                || !window.waiting.firstEntry().getKey().equals(mob)) return false;
        window.waiting.remove(mob);
        window.started++;
        window.running++;
        return true;
    }

    public synchronized void complete(Instance instance) {
        Window window = windows.get(instance);
        if (window != null) window.running--;
    }

    public synchronized void cancel(Instance instance, UUID mob) {
        Window window = windows.get(instance);
        if (window != null) window.waiting.remove(mob);
    }
}
