package net.minestom.server.entity.ai.activity;

import it.unimi.dsi.fastutil.Pair;
import net.minestom.server.Tickable;
import net.minestom.server.entity.ai.behavior.Behavior;
import net.minestom.server.entity.ai.memory.MemoryType;
import net.minestom.server.entity.ai.memory.MemoryValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class ActivityHandler implements Tickable {

    // TODO: have a core activity, which runs alongside the active activity
    // TODO: default activity in-case an activity is not selected
    // TODO: other activites

    // the activity that will always be running
    private Activity coreActivity;
    // the activity that will run if no other activities are running
    private Activity defaultActivity;

    private final EnumSet<Activity> currentActivities;

    // the memory requirements needed for certain activities to activate
    private final Map<Activity, Map<MemoryType<?>, MemoryValue<?>>> activityMemoryRequirements;

    // stores the prioritized behaviors based on the activity
    // the list is sorted on insertion and the list value stores the behaviors by index 0 being the highest priority behavior
    // this gets rid of the unnecessary TreeMap that is present in the NMS implementation
    private final Map<Activity, List<Behavior>> prioritizedBehaviors;

    public ActivityHandler() {
        this.coreActivity = Activity.CORE;
        this.defaultActivity = Activity.IDLE;
        this.currentActivities = EnumSet.of(this.coreActivity, this.defaultActivity);
        this.activityMemoryRequirements = new EnumMap<>(Activity.class);
        this.prioritizedBehaviors = new EnumMap<>(Activity.class);
    }

    /*
    Add an activity that has a list of behaviors that will execute with a certain priority and potentially some memory conditions needed to activate the activity
    Update the activity with the first valid activity detected in a list
     */

    // TODO: add activity and remove memories when stopped

    // adds an activity for this mob
    // behaviors that this activity should include
    // memory requirements that need to be fulfilled in order to activate this activity
    public void addActivity(@NotNull Activity activity,
                            @NotNull List<Behavior> behaviors,
                            @NotNull Map<MemoryType<?>, MemoryValue<?>> memoryRequirements) {
        if (activityMemoryRequirements.containsKey(activity)
                || prioritizedBehaviors.containsKey(activity)) {
            throw new IllegalArgumentException(activity.name() + " is already assigned to this brain.");
        }

        // store the memory requirements needed for this activity to activate
        activityMemoryRequirements.put(activity, memoryRequirements);

        // sort the behaviors based on priority (0 being the highest priority)
        final List<Behavior> sortedBehaviors = new ArrayList<>(behaviors);
        sortedBehaviors.sort(Comparator.comparingInt(Behavior::getPriority));

        // store the sorted behaviors with the activity
        prioritizedBehaviors.put(activity, sortedBehaviors);
    }

    public void setCoreActivity(@NotNull Activity coreActivity) {
        this.coreActivity = coreActivity;
    }

    public void setDefaultActivity(@NotNull Activity defaultActivity) {
        this.defaultActivity = defaultActivity;
    }

    public void setCurrentActivityToFirstValid(@NotNull List<Activity> activities) {

    }

    public void setCurrentActivity(@NotNull Activity activity) {
        // check if the mob is already doing this activity
        if (currentActivities.contains(activity))
            return;

        // TODO: erase memories for other activites
        currentActivities.clear();
        currentActivities.add(coreActivity);
        currentActivities.add(activity);
    }

    public void useDefaultActivity() {
        setCurrentActivity(defaultActivity);
    }

    @NotNull
    @UnmodifiableView
    public EnumSet<Activity> getCurrentActivities() {
        return EnumSet.copyOf(currentActivities);
    }

    // try to update the current activities
    @Override
    public void tick(long time) {

    }
}