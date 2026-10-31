package net.minestom.server.entity;

import net.minestom.server.entity.ai.Brain;

import java.util.UUID;

public class EntityMob extends LivingEntity {

    protected final Brain brain;

    public EntityMob(EntityType entityType) {
        super(entityType);

        this.brain = new Brain();
        initializeBrain();
    }

    public EntityMob(EntityType entityType,
                     UUID uuid) {
        super(entityType, uuid);

        this.brain = new Brain();
        initializeBrain();
    }

    protected void initializeBrain() {
    }

    @Override
    public void update(long time) {
        // mob-related updates
        brain.tick(time);

        // living entity updates
        super.update(time);
    }

//    public void disableAI() {
//        if (ai != null) ai.reset(TerminationReason.REMOVED);
//        ai = null;
//        brain.pathNavigator().clearPath();
//    }
//
//    @Override
//    public boolean damage(Damage damage) {
//        boolean accepted = super.damage(damage);
//        if (accepted && ai != null && !isDead()) {
//            Damage applied = getLastDamageSource();
//            if (applied != null) ai.onDamage(applied);
//        }
//        return accepted;
//    }
//
//    @Override
//    public void kill() {
//        if (ai != null) ai.reset(TerminationReason.REMOVED);
//        super.kill();
//    }
//
//    @Override
//    protected void remove(boolean permanent) {
//        if (ai != null) ai.reset(TerminationReason.REMOVED);
//        super.remove(permanent);
//    }
//
//    public CompletableFuture<Path> findPath(Point target,
//                                            int completionRange,
//                                            Runnable completionCallback,
//                                            Runnable bestEffortCompletionCallback) {
//        if (ai != null) throw new IllegalStateException("AI goals own navigation while AI is enabled");
//        return brain.pathNavigator().findPath(target, completionRange, completionCallback, bestEffortCompletionCallback);
//    }
//
//    public void setPath(NavigationDataSnapshot dataSnapshot) {
//        if (ai != null) throw new IllegalStateException("AI goals own navigation while AI is enabled");
//        brain.pathNavigator().setPath(dataSnapshot);
//    }
//
//    public NavigationDataSnapshot getNavigationDataSnapshot() {
//        return brain.pathNavigator().getNavigationDataSnapshot();
//    }
//
//    public void clearPath() {
//        if (ai != null) {
//            ai.scheduler().interrupt(Control.MOVE);
//            ai.navigation().clear();
//        }
//        brain.pathNavigator().clearPath();
//    }
}