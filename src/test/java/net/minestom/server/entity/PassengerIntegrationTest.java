package net.minestom.server.entity;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnvTest
public class PassengerIntegrationTest {

    @Test
    public void addPassengerTest(Env env) {
        final Instance instance = env.createFlatInstance();

        final Entity entity = new Entity(EntityType.ZOMBIE);
        final Entity passenger = new Entity(EntityType.SPIDER);

        entity.setInstance(instance, new Pos(0, 42, 0)).join();
        passenger.setInstance(instance, new Pos(0, 42, 0)).join();

        assertEquals(0, entity.getPassengers().size());
        assertNull(passenger.getVehicle());

        entity.addPassenger(passenger);

        assertEquals(1, entity.getPassengers().size());
        assertEquals(entity, passenger.getVehicle());
    }

    @Test
    public void addPassengerNonBlockingTest(Env env) {
        final Instance instance = env.createFlatInstance();

        final Entity entity = new Entity(EntityType.ZOMBIE);
        final Entity passenger = new Entity(EntityType.SPIDER);

        entity.setInstance(instance, new Pos(0, 42, 0));
        passenger.setInstance(instance, new Pos(0, 42, 0));

        assertEquals(0, entity.getPassengers().size());
        assertNull(passenger.getVehicle());

        entity.addPassenger(passenger);

        assertEquals(1, entity.getPassengers().size());
        assertEquals(entity, passenger.getVehicle());
    }

    @Test
    public void addUnspawnedPassengerTest(Env env) {
        final Instance instance = env.createFlatInstance();

        final Entity entity = new Entity(EntityType.ZOMBIE);
        final Entity passenger = new Entity(EntityType.SPIDER);

        entity.setInstance(instance, new Pos(0, 42, 0)).join();

        entity.addPassenger(passenger);

        assertEquals(1, entity.getPassengers().size());
    }

    @Test
    public void addPassengerFromAnotherInstanceTest(Env env) {
        final Instance instance1 = env.createFlatInstance();
        final Instance instance2 = env.createFlatInstance();

        final Entity entity = new Entity(EntityTypes.ZOMBIE);
        final Entity passenger = new Entity(EntityTypes.SPIDER);

        entity.setInstance(instance1, new Pos(0, 42, 0)).join();
        passenger.setInstance(instance2, new Pos(0, 42, 0)).join();

        assertEquals(0, entity.getPassengers().size());
        assertNull(passenger.getVehicle());

        entity.addPassenger(passenger);

        assertEquals(1, entity.getPassengers().size());
        assertEquals(entity, passenger.getVehicle());
        assertEquals(0, instance2.getEntities().size());
    }

    @Test
    public void swapPassengerTest(Env env) {
        final Instance instance = env.createFlatInstance();

        final Entity entity1 = new Entity(EntityTypes.ZOMBIE);
        final Entity entity2 = new Entity(EntityTypes.ZOMBIFIED_PIGLIN);
        final Entity passenger = new Entity(EntityTypes.SPIDER);

        entity1.setInstance(instance, new Pos(0, 42, 0)).join();
        entity2.setInstance(instance, new Pos(0, 42, 0)).join();
        passenger.setInstance(instance, new Pos(0, 42, 0)).join();

        entity2.addPassenger(passenger);

        assertEquals(0, entity1.getPassengers().size());
        assertEquals(1, entity2.getPassengers().size());

        entity1.addPassenger(passenger);

        assertEquals(1, entity1.getPassengers().size());
        assertEquals(0, entity2.getPassengers().size());
    }

    @Test
    public void passengersOnCamelsAtDifferentHeights(Env env) {
        var instance = env.createFlatInstance();
        var camel1 = new Entity(EntityType.CAMEL);
        var camel2 = new Entity(EntityType.CAMEL);
        var rider1 = new Entity(EntityType.ZOMBIE);
        var rider2 = new Entity(EntityType.ZOMBIE);

        camel1.setInstance(instance, new Pos(0, 42, 0)).join();
        camel2.setInstance(instance, new Pos(0, 42, 0)).join();

        camel2.setPose(EntityPose.SITTING);
        camel1.addPassenger(rider1);
        camel2.addPassenger(rider2);

        assertNotEquals(rider2.getPosition().y(), rider1.getPosition().y());
    }

    @Test
    public void passengerOffsetProcessedCorrectly(Env env) {
        var instance = env.createFlatInstance();
        var vehicle = new Entity(EntityType.ZOMBIE);
        var passenger = new Entity(EntityType.SKELETON);

        vehicle.setInstance(instance, new Pos(0, 42, 0, 0, 0)).join();
        vehicle.addPassenger(passenger);

        // Default case

        List<Double> passengerAttachment = vehicle.getEntityType().registry().entityAttachment("PASSENGER");
        List<Double> vehicleAttachment = passenger.getEntityType().registry().entityAttachment("VEHICLE");
        assertNotNull(passengerAttachment);
        assertNotNull(vehicleAttachment);

        Pos passengerOffset = new Pos(passengerAttachment.get(0), passengerAttachment.get(1), passengerAttachment.get(2));
        Pos vehicleOffset = new Pos(vehicleAttachment.get(0), vehicleAttachment.get(1), vehicleAttachment.get(2));
        Pos assumedPosition = vehicle.getPosition().add(passengerOffset).sub(vehicleOffset);
        assertEquals(passenger.getPosition(), assumedPosition);
    }

    @Test
    public void removePassengerTest(Env env) {
        final Instance instance = env.createFlatInstance();
    }

    @Test
    public void teleportPassengerTest(Env env) {
        final Instance instance = env.createFlatInstance();

        final Entity entity = new Entity(EntityType.ZOMBIE);
        final Entity passenger = new Entity(EntityType.SPIDER);

        entity.setInstance(instance, new Pos(0, 42, 0)).join();
        passenger.setInstance(instance, new Pos(0, 42, 5000)).join();

        entity.addPassenger(passenger);

        assertEquals(1, entity.getPassengers().size());
        assertEquals(entity, passenger.getVehicle());

        passenger.teleport(new Pos(10, 42, 10)).join();

        // TODO: what should happen when a passenger is teleported?
    }
}