package net.minestom.server.entity.ai.behavior;

import net.minestom.server.Tickable;
import net.minestom.server.entity.ai.memory.MemoryStatus;
import net.minestom.server.entity.ai.memory.MemoryType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

// behavior represents how a mob will respond to environmental changes through sensors and memory
public abstract class Behavior implements Tickable {

    // check if this behavior can start
    // start the behavior
    // tick the behavior
    // check if the behavior can continue
    // stop the behavior

    private final int priority;

    // the memory requirements needed to start this behavior (absent/present)
    private final Map<MemoryType<?>, MemoryStatus> memoryRequirements;

    public Behavior(int priority,
                    @NotNull Map<MemoryType<?>, MemoryStatus> memoryRequirements) {
        this.priority = priority;
        this.memoryRequirements = Map.copyOf(memoryRequirements);
    }

    public boolean canStart() {
        return false;
    }

    // checks if this behavior can start
//    public abstract boolean canStart();

    // checks if this behavior can still be used
    public abstract boolean canStillUse();

    public abstract void start();

    public abstract void stop();

    public int getPriority() {
        return priority;
    }
}