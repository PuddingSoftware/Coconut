package net.minestom.server.entity.ai.memory;

import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.ai.Brain;
import net.minestom.server.utils.Unit;

/**
 * Defines the available {@link MemoryType}s that can be used by a {@link Brain} to store and retrieve memories.
 */
public final class Memories {

    private Memories() {
    }

    /**
     * The nearest living entity known to the {@link Brain}.
     */
    public static final MemoryType<LivingEntity> NEAREST_LIVING_ENTITY = new MemoryType<>("nearest_living_entity", LivingEntity.class);

    /**
     * Indicates that the entity is panicking when this memory is present.
     */
    public static final MemoryType<Unit> PANICKING = new MemoryType<>("panicking", Unit.class);
}