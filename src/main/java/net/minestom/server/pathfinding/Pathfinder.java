package net.minestom.server.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.pathfinding.collections.BinaryMinimumHeap;
import net.minestom.server.pathfinding.context.MobContext;
import net.minestom.server.pathfinding.context.PathfindingContext;
import net.minestom.server.pathfinding.data.Node;
import net.minestom.server.pathfinding.data.Path;
import net.minestom.server.pathfinding.data.RegionKey;
import net.minestom.server.pathfinding.data.SpatialData;
import net.minestom.server.pathfinding.evaluator.result.NodeEvaluationResult;
import net.minestom.server.pathfinding.options.PathfinderOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO: maybe support dynamic pathfinding in the future so we can adjust weights on the fly based on environmental factors, not needed right now though
public final class Pathfinder {

    public static final Pathfinder DEFAULT_PATHFINDER = new Pathfinder(
            new PathfinderOptions.Builder()
                    .autoLoadChunks(true)
                    .build()
    );

    private static final double TIE_BREAKER_WEIGHT = 1e-6;

    private final PathfinderOptions options;

    public Pathfinder(@NotNull PathfinderOptions options) {
        this.options = options;
    }

    public CompletableFuture<Path> findPath(@NotNull Point start,
                                            @NotNull Point target,
                                            @NotNull MobContext mobContext,
                                            int completionRange,
                                            @NotNull AtomicBoolean cancelFlag) {
        if (options.async()) {
            return CompletableFuture.supplyAsync(() ->
                    evaluatePath(start, target, mobContext, completionRange, cancelFlag), PathfinderScheduler.PATHING_EXECUTOR_SERVICE);
        } else {
            return CompletableFuture.completedFuture(evaluatePath(start, target, mobContext, completionRange, cancelFlag));
        }
    }

    public PathfinderOptions getOptions() {
        return options;
    }

    @NotNull
    private Path evaluatePath(@NotNull Point start,
                              @NotNull Point target,
                              @NotNull MobContext mobContext,
                              int completionRange,
                              @NotNull AtomicBoolean cancelFlag) {
        // attempt to generate a valid starting node
        final Node startNode = options.nodeEvaluator().getValidStart(mobContext, target);

        if (startNode == null) {
            // a valid starting position could not be found
            return new Path(Path.State.FAILED, Collections.emptyList(), start, target);
        }

        // make a minimum heap priority queue and sort by the lowest F value
        final BinaryMinimumHeap openSet = new BinaryMinimumHeap(2048); // TODO: test for optimal initial capacity
        final Long2ObjectMap<SpatialData> visitedRegions = new Long2ObjectOpenHashMap<>();
        final Long2ObjectMap<Node> openSetNodes = new Long2ObjectOpenHashMap<>();

        final PathfindingContext pathfindingContext = new PathfindingContext(openSet, visitedRegions, openSetNodes);

        // insert the starting node
        insertStartNode(startNode, pathfindingContext);

        final int maxIterations = options.maxIterations();
        final boolean hasMaxIterations = maxIterations > 0;
        final int maxLength = options.getMaxLength();
        final boolean hasMaxLength = maxLength > 0;

        int iteration = 0;
        Node bestFallbackNode = startNode;

        while (!openSet.isEmpty() && (!hasMaxIterations || iteration < options.maxIterations())) {
            // check if the pathfinding request was canceled
            if (cancelFlag.get()) {
                if (options.isBestEffortOnCancel()) {
//                    System.out.println("best effort on cancel");
                    return reconstructPath(start, target, bestFallbackNode, true);
                }

//                System.out.println("canceled");
                return new Path(Path.State.FAILED, Collections.emptyList(), start, target);
            }

            iteration++;

            Node currentNode = extractBestNode(pathfindingContext);
            markNodeAsExpanded(currentNode, pathfindingContext);

            if (currentNode.getH() < bestFallbackNode.getH()) {
                bestFallbackNode = currentNode;
            }

            // check if the path has reached the max length
            if (hasMaxLength && currentNode.depth() >= options.getMaxLength()) {
//                System.out.println("max length exceeded");
//                System.out.println(currentNode.depth());
                return reconstructPath(start, target, bestFallbackNode, true);
            }

            // check if we have finished pathfinding (i.e. are we close enough to the target point)
            if (currentNode.point().octileDistance(target) <= completionRange) {
                return reconstructPath(start, target, currentNode, false);
            }

            // process the neighbors around the current node, this is where the magic happens
            processNeighbors(start, target, currentNode, pathfindingContext, mobContext);
        }

        // check if we should resort to the best effort path if we couldn't find a path
        if (options.isBestEffortOnFailure()) {
//            System.out.println("best effort on failure");
//            System.out.println("iterations: " + iteration);
            return reconstructPath(start, target, bestFallbackNode, true);
        }

//        System.out.println("failed x2");
        return new Path(Path.State.FAILED, Collections.emptyList(), start, target);
    }

    @NotNull
    @SuppressWarnings("MethodCanBeStatic")
    private Path reconstructPath(@NotNull Point start,
                                 @NotNull Point target,
                                 @NotNull Node endNode,
                                 boolean bestEffort) {
        if (endNode.getParentNode() == null && endNode.depth() == 0) {
            // the end node was the start node, so no movement really happens but the path is "found"
            return new Path(Path.State.FOUND, Collections.singletonList(endNode.point()), start, target);
        }

        // reconstruct the path by tracing through the nodes that were taken
        final List<Node> debugNodes = new ArrayList<>();
        final List<Point> points = new ArrayList<>();
        Node currentNode = endNode;
        while (currentNode != null) {
            debugNodes.add(currentNode);
            points.add(currentNode.point());
            currentNode = currentNode.getParentNode();
        }

        Collections.reverse(debugNodes);
//        for (Node node : debugNodes) {
//            System.out.println(node);
//        }

        Collections.reverse(points);
        return new Path(bestEffort ? Path.State.BEST_EFFORT : Path.State.FOUND, points, start, target);
    }

    private void processNeighbors(@NotNull Point start,
                                  @NotNull Point target,
                                  @NotNull Node currentNode,
                                  @NotNull PathfindingContext pathfindingContext,
                                  @NotNull MobContext mobContext) {
        for (Vec offset : options.movementStrategy()) {
            final double centeredX = currentNode.point().centerBlockX();
            final double centeredZ = currentNode.point().centerBlockZ();
            final Point initialNeighborPoint = new Pos(centeredX, currentNode.point().y(), centeredZ).add(offset);
            final Node initialNeighborNode = new Node(initialNeighborPoint, start, target, currentNode.depth() + 1);

            // check if the step from the current node to the neighbor node is valid
            // and if it is valid, check to see if a new node was found (due to the initial neighbor node being blocked)
            final NodeEvaluationResult evaluationResult = options.nodeEvaluator().isValidMove(currentNode, initialNeighborNode, mobContext, options);

            // if the node isn't a valid move, we'll just skip it and continue to the next one
            if (evaluationResult.status() == NodeEvaluationResult.Status.INVALID_MOVE)
                continue;

            // if the node is a valid move, then we have 2 possibilities:
            // the initial neighbor node was accurate, we will use this node (this means they could just walk to the next point)
            // the initial neighbor node is blocked but a new node was found, this means that there was a fall, step, jump, etc...
            final Node validNeighborNode = evaluationResult.newNode() == null ? initialNeighborNode : evaluationResult.newNode();
            final Point neighborPoint = validNeighborNode.point();
            final long packedPoint = RegionKey.pack(validNeighborNode.point());

            // check if the valid neighbor node is in the closed set
            final SpatialData spatialData = getOrCreateSpatialData(neighborPoint, pathfindingContext);
            if (spatialData.contains(neighborPoint, packedPoint)) {
                continue;
            }

            // check if the open set already contains our valid neighbor node
            // if it is, then check if we can update the existing node with a better node
            final Node existingNode = pathfindingContext.openSetNodes().get(packedPoint);
            if (existingNode != null) {
                updateExistingNode(existingNode, validNeighborNode, packedPoint, currentNode, pathfindingContext);
                continue;
            }

            validNeighborNode.setParentNode(currentNode);
            insertNode(currentNode, validNeighborNode, pathfindingContext);
        }
    }

    @SuppressWarnings("MethodCanBeStatic")
    private double calculateHeapKey(Node neighbor, double f) {
        double heuristic = neighbor.getH();
        double tieBreaker = TIE_BREAKER_WEIGHT * (heuristic / (Math.abs(f) + 1));
        double heapKey = f - tieBreaker;

        if (Double.isNaN(heapKey) || Double.isInfinite(heapKey)) {
            heapKey = f;
        }

        return heapKey;
    }

    private void insertStartNode(@NotNull Node startNode,
                                 @NotNull PathfindingContext pathfindingContext) {
        insertNode(null, startNode, pathfindingContext);
    }

    private void insertNode(@Nullable Node parentNode,
                            @NotNull Node newNode,
                            @NotNull PathfindingContext pathfindingContext) {
        if (parentNode != null) {
            // this will not be a start node, so we'll need to do some cost processing for the new node
            newNode.setG(calculateGCost(parentNode, newNode));
        }

        final double heapKey = calculateHeapKey(newNode, newNode.getF());
        final long packedPoint = RegionKey.pack(newNode.point());
        pathfindingContext.openSet().insertOrUpdate(packedPoint, heapKey);
        pathfindingContext.openSetNodes().put(packedPoint, newNode);
    }

    @SuppressWarnings("MethodCanBeStatic")
    private Node extractBestNode(@NotNull PathfindingContext pathfindingContext) {
        final long packedPoint = pathfindingContext.openSet().extractMin();
        final Node node = pathfindingContext.openSetNodes().get(packedPoint);
        pathfindingContext.openSetNodes().remove(packedPoint);

        return node;
    }

    private void markNodeAsExpanded(@NotNull Node node,
                                    @NotNull PathfindingContext pathfindingContext) {
        final Point point = node.point();

        final long packedPoint = RegionKey.pack(point);
        pathfindingContext.openSetNodes().remove(packedPoint);

        final SpatialData spatialData = getOrCreateSpatialData(point, pathfindingContext);
        spatialData.insert(point, packedPoint);
    }

    // handles cases where we find an existing node that is not as optimal as a new node when pathing
    private void updateExistingNode(@NotNull Node existingNode,
                                    @NotNull Node candidateNode,
                                    long packedPoint,
                                    @NotNull Node currentNode,
                                    @NotNull PathfindingContext pathfindingContext) {
        // check to see if the candidate node has a lower cost than the existing node
        final double newG = calculateGCost(currentNode, candidateNode);
        final double oldG = existingNode.getG();
        final double tol = Math.ulp(Math.max(Math.abs(newG), Math.abs(oldG)));
        if (newG + tol >= oldG) {
            return;
        }

        // the candidate node has a lower cost than the existing node
        // use the candidate in place of the existing node
        candidateNode.setParentNode(currentNode);
        candidateNode.setG(newG);

        final double newKey = calculateHeapKey(candidateNode, candidateNode.getF());
        final double oldKey = pathfindingContext.openSet().getCost(packedPoint);

        // replace the node stored for this packed point with the better candidate node
        pathfindingContext.openSetNodes().put(packedPoint, candidateNode);

        // we only call the heap once the key actually decreased
        if (newKey + Math.ulp(newKey) < oldKey) {
            pathfindingContext.openSet().insertOrUpdate(packedPoint, newKey);
        } else if (Math.abs(newKey - oldKey) <= Math.ulp(newKey)) {
            /*
             * Sometimes a tiny nudging helps to maintain consistency,
             * but usually insertOrUpdate catches that.
             *
             * Since our heap strictly checks <, we can force it here
             */
            pathfindingContext.openSet().insertOrUpdate(packedPoint, oldKey - Math.ulp(oldKey));
        }
    }

    @NotNull
    private SpatialData getOrCreateSpatialData(@NotNull Point point,
                                               @NotNull PathfindingContext pathfindingContext) {
        final int cellSize = 12;

        final int rX = Math.floorDiv(point.blockX(), cellSize);
        final int rY = Math.floorDiv(point.blockY(), cellSize);
        final int rZ = Math.floorDiv(point.blockZ(), cellSize);

        final long regionKey = RegionKey.pack(rX, rY, rZ);

        return pathfindingContext.visitedRegions().computeIfAbsent(regionKey,
                (long _) -> new SpatialData(options));
    }

    @SuppressWarnings("MethodCanBeStatic")
    private double calculateGCost(@NotNull Node parentNode,
                                  @NotNull Node node) {
        // penalize diagonal moves according to the Octile distance of sqrt(2) = 1.41421356237
        final Point direction = node.point().sub(parentNode.point());
        final double movementCost = (Math.abs(direction.blockX()) == Math.abs(direction.blockZ())) ? 1.4142135623730951D : 1.0D;

        // TODO: node type penalty
        return parentNode.getG() + movementCost;
    }
}