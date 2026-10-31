package net.minestom.server.pathfinding.data;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minestom.server.coordinate.Point;
import net.minestom.server.pathfinding.options.PathfinderOptions;
import org.jetbrains.annotations.NotNull;

/**
 * The SpatialData class represents the data associated with a grid region. This data includes a
 * Bloom filter used to quickly check if a position is within the region and a set of positions that
 * have been examined by the pathfinder.
 */
public final class SpatialData {

    /**
     * The Bloom filter used to store the positions of the region. This filter is used to quickly
     * check if a position is within the region without having to iterate over all the positions in
     * the region.
     */
    private final BloomFilter<Point> bloomFilter;

    /**
     * The set of positions that have been examined by the pathfinder. This set is used to track the
     * positions that have been examined by the pathfinder to avoid examining the same position
     * multiple times.
     */
    private final LongSet regionalExaminedPositions;

    /**
     * Creates a new GridRegionData with Bloom filter settings from the provided configuration.
     *
     * @param options The pathfinder options containing Bloom filter settings
     */
    public SpatialData(@NotNull PathfinderOptions options) {
        final Funnel<Point> pointFunnel = (point, into) ->
                into.putInt(point.blockX())
                        .putInt(point.blockY())
                        .putInt(point.blockZ());

        this.bloomFilter = BloomFilter.create(pointFunnel, options.bloomFilterSize(), options.bloomFilterFpp());
        this.regionalExaminedPositions = new LongOpenHashSet();
    }

    /**
     * Registers a given path position by adding it to the Bloom filter and marking it as examined
     * within the regional positions set.
     *
     * @param point The point in the path to be registered. It represents a specific
     *              location within the grid region.
     */
    public void insert(@NotNull Point point,
                       long packedPoint) {
        bloomFilter.put(point);
        regionalExaminedPositions.add(packedPoint);
    }

    /**
     * First Line of Defence. This method first checks the bloom filter if it might contain the
     * provided {@code point}. If true, it performs an expensive containment check on the
     * examined positions.
     */
    public boolean contains(@NotNull Point point,
                            long packedPoint) {
        return bloomFilter.mightContain(point)
                && regionalExaminedPositions.contains(packedPoint);
    }
}