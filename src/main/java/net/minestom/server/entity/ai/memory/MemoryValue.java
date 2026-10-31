package net.minestom.server.entity.ai.memory;

import net.minestom.server.Tickable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a memory value that can optionally expire after a specified number of ticks.
 * <p>
 * A memory value stores an associated value and maintains a remaining
 * time-to-live, which decreases by one on each tick.
 * Once the time-to-live reaches zero, the memory is considered expired.
 * <p>
 * Memories created without a time-to-live never expire.
 *
 * @param <T> the type of value stored in this memory
 */
public final class MemoryValue<T> implements Tickable {

    private final T value;

    private long timeToLiveTicks;

    /**
     * Creates a memory value that never expires.
     *
     * @param value the value to store
     */
    public MemoryValue(@NotNull T value) {
        this(value, Long.MAX_VALUE);
    }

    /**
     * Creates a memory value with the specified time-to-live.
     * <p>
     * A time-to-live of {@link Long#MAX_VALUE} indicates that
     * the memory never expires.
     *
     * @param value           the value to store
     * @param timeToLiveTicks the number of ticks before the memory expires
     */
    public MemoryValue(@NotNull T value,
                       long timeToLiveTicks) {
        this.value = value;
        this.timeToLiveTicks = timeToLiveTicks;
    }

    /**
     * Updates the remaining time-to-live of this memory.
     * <p>
     * Decreases the remaining lifetime by one tick if the memory
     * is configured to expire.
     *
     * @param time the current tick time
     */
    @Override
    public void tick(long time) {
        if (canExpire() && !hasExpired()) {
            timeToLiveTicks--;
        }
    }

    /**
     * Determines whether this memory has expired.
     * <p>
     * A memory is considered expired when its remaining
     * time-to-live is zero or negative.
     *
     * @return {@code true} if the memory has expired,
     *         {@code false} otherwise
     */
    public boolean hasExpired() {
        return timeToLiveTicks <= 0L;
    }

    /**
     * Determines whether this memory has a finite lifetime.
     *
     * @return {@code true} if the memory can expire,
     *         {@code false} if it is permanent
     */
    public boolean canExpire() {
        return timeToLiveTicks != Long.MAX_VALUE;
    }

    /**
     * Returns the value stored in this memory.
     * <p>
     * The value remains accessible even after the memory has expired.
     *
     * @return the stored value
     */
    @NotNull
    public T getValue() {
        return value;
    }
}