package net.minestom.server.pathfinding.movement;

import net.minestom.server.coordinate.Vec;

import java.util.Arrays;
import java.util.Collection;

public final class MovementStrategies {

    private MovementStrategies() {
    }

    /**
     * Left, right, forward, and backwards movement.
     */
    @SuppressWarnings("PreferredInterfaceType")
    public static final Collection<Vec> BASIC = Arrays.asList(
            new Vec(-1, 0, 0),
            new Vec(0, 0, -1),
            new Vec(0, 0, 1),
            new Vec(1, 0, 0)
    );

    /**
     * Left, right, forward, backwards, and diagonal movement.
     */
    @SuppressWarnings("PreferredInterfaceType")
    public static final Collection<Vec> BASIC_AND_DIAGONAL = Arrays.asList(
            new Vec(-1, 0, -1),
            new Vec(-1, 0, 0),
            new Vec(-1, 0, 1),
            new Vec(0, 0, -1),
            new Vec(0, 0, 1),
            new Vec(1, 0, -1),
            new Vec(1, 0, 0),
            new Vec(1, 0, 1)
    );
}