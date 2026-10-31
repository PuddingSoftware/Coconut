package net.minestom.server.entity.old.navigation.data;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.pathfinding.data.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Useful for storing state that might need to be restored for a removed {@link EntityMob}.
 */
public final class NavigationDataSnapshot {

    private final Path path;
    private final List<Point> points;
    private final int index;

    private final int completionRange;
    private final Runnable completionCallback;
    private final Runnable bestEffortCompletionCallback;

    public NavigationDataSnapshot(@Nullable Path path,
                                  @NotNull List<Point> points,
                                  int index,
                                  int completionRange,
                                  @NotNull Runnable completionCallback,
                                  @NotNull Runnable bestEffortCompletionCallback) {
        this.path = path;
        this.points = points;
        this.index = index;
        this.completionRange = completionRange;
        this.completionCallback = completionCallback;
        this.bestEffortCompletionCallback = bestEffortCompletionCallback;
    }

    @Nullable
    public Path getPath() {
        return path;
    }

    @NotNull
    public List<Point> getPoints() {
        return points;
    }

    public int getIndex() {
        return index;
    }

    public int getCompletionRange() {
        return completionRange;
    }

    @NotNull
    public Runnable getCompletionCallback() {
        return completionCallback;
    }

    @NotNull
    public Runnable getBestEffortCompletionCallback() {
        return bestEffortCompletionCallback;
    }
}