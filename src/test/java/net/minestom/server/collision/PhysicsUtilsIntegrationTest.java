package net.minestom.server.collision;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public class PhysicsUtilsIntegrationTest {
    private static final BoundingBox BOX = new BoundingBox(0.6, 1.8, 0.6);
    private static final Aerodynamics AERODYNAMICS = new Aerodynamics(0, 1, 1);
    private static final Pos START = new Pos(8.5, 40, 8.5);
    private static final double PRECISION = 0.0001;

    @ParameterizedTest
    @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1", "1, 1", "-1, -1"})
    public void stepOntoSlab(int x, int z, Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(8 + x, 40, 8 + z, Block.STONE_SLAB);
        var velocity = new Vec(x, -0.08, z);

        var result = simulate(instance, START, velocity, 0.6);

        assertEquals(START.x() + x, result.newPosition().x(), PRECISION);
        assertEquals(40.5, result.newPosition().y(), PRECISION);
        assertEquals(START.z() + z, result.newPosition().z(), PRECISION);
        assertTrue(result.isOnGround());
        assertTrue(result.collisionY());
        assertFalse(result.collisionX());
        assertFalse(result.collisionZ());
        assertEquals(0, result.newVelocity().y());
        assertEquals(velocity, result.originalDelta());
        assertEquals(new BlockVec(8 + x, 40, 8 + z), result.collisionShapePositions()[1]);
        assertNotNull(result.collisionPoints()[1]);
        assertNotNull(result.collisionShapes()[1]);
        assertTrue(CollisionUtils.canFit(BOX, instance, result.newPosition()));
    }

    @Test
    public void climbBothStairRisers(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_STAIRS.withProperties(Map.of(
                "facing", "east", "half", "bottom", "shape", "straight")));

        var first = simulate(instance, START, new Vec(0.4, -0.08, 0), 0.6);
        assertEquals(8.9, first.newPosition().x(), PRECISION);
        assertEquals(40.5, first.newPosition().y(), PRECISION);
        assertTrue(first.isOnGround());
        assertTrue(CollisionUtils.canFit(BOX, instance, first.newPosition()));

        var second = simulate(instance, first.newPosition(), new Vec(0.6, -0.08, 0), 0.6);
        assertEquals(9.5, second.newPosition().x(), PRECISION);
        assertEquals(41, second.newPosition().y(), PRECISION);
        assertTrue(second.isOnGround());
        assertTrue(CollisionUtils.canFit(BOX, instance, second.newPosition()));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.49})
    public void respectMaximumStepHeight(double stepHeight, Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);

        var result = simulate(instance, START, new Vec(1, 0, 0), stepHeight);

        assertEquals(8.7, result.newPosition().x(), PRECISION);
        assertEquals(40, result.newPosition().y(), PRECISION);
        assertTrue(result.collisionX());
    }

    @Test
    public void stepOntoSlabAtExactMaximumHeight(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);

        var result = simulate(instance, START, new Vec(1, 0, 0), 0.5);

        assertEquals(9.5, result.newPosition().x(), PRECISION);
        assertEquals(40.5, result.newPosition().y(), PRECISION);
        assertTrue(result.isOnGround());
    }

    @Test
    public void stepWithLessThanMaximumHeadroom(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);
        // The ceiling leaves 0.55 blocks of upward movement for this box.
        instance.setBlock(8, 42, 8, Block.STONE);
        var box = new BoundingBox(0.6, 1.45, 0.6);

        var result = PhysicsUtils.simulateMovement(START, new Vec(1, 0, 0), box,
                instance.getWorldBorder(), instance, AERODYNAMICS, true, true, true, false, 0.6, null);

        assertEquals(9.5, result.newPosition().x(), PRECISION);
        assertEquals(40.5, result.newPosition().y(), PRECISION);
        assertTrue(result.isOnGround());
        assertTrue(CollisionUtils.canFit(box, instance, result.newPosition()));
    }

    @Test
    public void rejectStepWithoutHeadroom(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);
        instance.setBlock(8, 42, 8, Block.STONE);
        instance.setBlock(9, 42, 8, Block.STONE);

        var result = simulate(instance, START, new Vec(1, 0, 0), 0.6);

        assertEquals(8.7, result.newPosition().x(), PRECISION);
        assertEquals(40, result.newPosition().y(), PRECISION);
        assertTrue(result.collisionX());
        assertTrue(CollisionUtils.canFit(BOX, instance, result.newPosition()));
    }

    @Test
    public void heightGainDoesNotReplaceGreaterHorizontalProgress(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);
        instance.setBlock(9, 40, 9, Block.STONE);

        var normal = simulate(instance, START, new Vec(1, 0, 1), 0);
        var stepped = simulate(instance, START, new Vec(1, 0, 1), 0.6);

        assertEquals(normal.newPosition(), stepped.newPosition());
        assertEquals(normal.newVelocity(), stepped.newVelocity());
        assertEquals(40, stepped.newPosition().y(), PRECISION);
    }

    @Test
    public void preserveWallCollisionWhileStepping(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);
        instance.setBlock(9, 40, 9, Block.STONE);
        instance.setBlock(9, 41, 9, Block.STONE);

        var result = simulate(instance, START, new Vec(1, 0, 0.4), 0.6);

        assertEquals(9.5, result.newPosition().x(), PRECISION);
        assertEquals(40.5, result.newPosition().y(), PRECISION);
        assertEquals(8.7, result.newPosition().z(), PRECISION);
        assertTrue(result.collisionY());
        assertTrue(result.collisionZ());
        assertTrue(result.isOnGround());
        assertEquals(0, result.newVelocity().z());
        assertNotNull(result.collisionPoints()[2]);
        assertNotNull(result.collisionShapes()[2]);
        assertEquals(new BlockVec(9, 40, 9), result.collisionShapePositions()[2]);
        assertEquals(new BlockVec(9, 40, 8), result.collisionShapePositions()[1]);
    }

    @Test
    public void stepRemainsInsideWorldBorder(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);
        var border = new WorldBorder(4, 8, 8, 0, 0);

        var result = PhysicsUtils.simulateMovement(START, new Vec(2, 0, 0), BOX,
                border, instance, AERODYNAMICS, true, true, true, false, 0.6, null);

        assertEquals(9.7, result.newPosition().x(), PRECISION);
        assertEquals(40.5, result.newPosition().y(), PRECISION);
        assertTrue(border.inBounds(result.newPosition(), BOX));
        assertTrue(result.collisionX());
        assertTrue(result.collisionY());
        assertNotNull(result.collisionPoints()[0]);
        assertNull(result.collisionShapes()[0]);
        assertNull(result.collisionShapePositions()[0]);
    }

    @Test
    public void doNotStepWhileAirborne(Env env) {
        var instance = env.createFlatInstance();
        instance.setBlock(9, 40, 8, Block.STONE_SLAB);

        var result = PhysicsUtils.simulateMovement(START, new Vec(1, 0, 0), BOX,
                instance.getWorldBorder(), instance, AERODYNAMICS, true, true, false, false, 0.6, null);

        assertEquals(8.7, result.newPosition().x(), PRECISION);
        assertEquals(40, result.newPosition().y(), PRECISION);
        assertTrue(result.collisionX());
    }

    private static PhysicsResult simulate(Instance instance, Pos position, Vec velocity, double stepHeight) {
        return PhysicsUtils.simulateMovement(position, velocity, BOX, instance.getWorldBorder(), instance,
                AERODYNAMICS, true, true, true, false, stepHeight, null);
    }
}
