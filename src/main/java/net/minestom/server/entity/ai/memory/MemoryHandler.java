package net.minestom.server.entity.ai.memory;

import net.minestom.server.Tickable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class MemoryHandler implements Tickable {

    private final Map<MemoryType<?>, MemoryValue<?>> memories;

    public MemoryHandler() {
        this.memories = new HashMap<>();
    }

    // checks if we have a certain type of memory
    public boolean hasMemory(@NotNull MemoryType<?> memoryType) {
        return memories.containsKey(memoryType);
    }

    // gets a memory value
    @Nullable
    public <T> T getMemory(@NotNull MemoryType<T> memoryType) {
        final MemoryValue<?> value = memories.get(memoryType);
        if (value == null) return null;
        return memoryType.type().cast(value);
    }

    // sets a memory with no expiration time
    public <T> void setMemory(@NotNull MemoryType<T> memoryType,
                              T value) {
        setMemoryWithExpiry(memoryType, value, Long.MAX_VALUE);
    }

    // sets a memory with an expiration time
    public <T> void setMemoryWithExpiry(@NotNull MemoryType<T> memoryType,
                              T value,
                              long timeToLive) {
        final MemoryValue<T> memoryValue = new MemoryValue<>(value, timeToLive);
        memories.put(memoryType, memoryValue);
    }

    // removes a memory
    public <T> void removeMemory(@NotNull MemoryType<T> memoryType) {
        memories.remove(memoryType);
    }

    // clear memories
    public void clearMemories() {
        memories.clear();
    }

    // tick memories time-to-live and handles expirations
    @Override
    public void tick(long time) {
        for (Iterator<Map.Entry<MemoryType<?>, MemoryValue<?>>> memoryIterator = memories.entrySet().iterator(); memoryIterator.hasNext(); ) {
            final Map.Entry<MemoryType<?>, MemoryValue<?>> memoryEntry = memoryIterator.next();

            // first check if a memory value has expired and if so, remove it from the memories
            // then tick the memory value to decrement the time-to-live (if applicable)
            final MemoryValue<?> memoryValue = memoryEntry.getValue();
            if (memoryValue.hasExpired()) {
                memoryIterator.remove();
            } else {
                memoryValue.tick(time);
            }
        }
    }
}