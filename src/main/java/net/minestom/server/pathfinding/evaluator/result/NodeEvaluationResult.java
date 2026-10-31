package net.minestom.server.pathfinding.evaluator.result;

import net.minestom.server.pathfinding.data.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NodeEvaluationResult {

    private final Status status;

    @Nullable
    private final Node newNode;

    public NodeEvaluationResult(@NotNull Status status) {
        this.status = status;
        this.newNode = null;
    }

    public NodeEvaluationResult(@NotNull Status status,
                                @NotNull Node newNode) {
        this.status = status;
        this.newNode = newNode;
    }

    @NotNull
    public Status status() {
        return status;
    }

    @Nullable
    public Node newNode() {
        return newNode;
    }

    public enum Status {

        VALID_MOVE,

        INVALID_MOVE
    }
}