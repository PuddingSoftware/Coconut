package net.minestom.server.entity.ai.memory;

import net.minestom.server.entity.ai.Brain;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a typed key for accessing a memory value stored in a {@link Brain}.
 *
 * @param name the unique identifier of the memory key
 * @param type type of class representing the type of value associated with this key
 * @param <T>  the type of value stored under this key
 */
// TODO: memory data status
public record MemoryType<T>(@NotNull String name, @NotNull Class<T> type) {

}