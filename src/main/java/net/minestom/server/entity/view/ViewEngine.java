package net.minestom.server.entity.view;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.instance.Instance;
import net.minestom.server.property.ServerProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ViewEngine {

    // gets the entity that this view engine represents
    private final Entity entity;

    // all entities (which includes players) will have an entity view by default
    private final EntityView entityView;
    // only players will have a player view (it will be null for all other types of entities)
    @Nullable
    private PlayerView playerView;

    // gets the last known location of this entity, or null if there is none
    @Nullable
    private volatile TrackedLocation trackedLocation;

    private final Set<Player> viewers = new ViewerSet();

    // useful to prevent collection resizing when processing viewer/viewable rule updates
    private int previousNearbyPlayersCount;
    private int previousNearbyEntitiesCount;

    private final Object lock = this;

    public ViewEngine(Entity entity) {
        this.entity = entity;

        this.entityView = new EntityView();
        if (entity instanceof Player player) {
            this.playerView = new PlayerView(player);
        }
    }

    /**
     * Adds a viewer to this entity that will not need to meet the viewable/viewer rules.
     *
     * @param player the player to add as a viewer
     * @return true if the player was added as a viewer, false if not
     */
    public boolean addManualViewer(Player player) {
        if (player == entity)
            return false;

        final boolean added;
        synchronized (lock) {
            added = entityView.getManualViewers().add(player);
        }

        if (added) {
            ViewEngineUtils.showEntityToPlayer(entity, player);
        }

        return added;
    }

    /**
     * Removes a viewer from this entity. This does not hide the entity automatically, it only makes the {@link Player}
     * adhere to the viewable/viewer rules.
     *
     * @param player the player to remove as a viewer
     * @return true if the player was removed as a viewer, false if not
     */
    public boolean removeManualViewer(Player player) {
        if (player == entity)
            return false;

        final boolean removed;
        synchronized (lock) {
            removed = entityView.getManualViewers().remove(player);
        }

        if (removed) {
            ViewEngineUtils.hideEntityFromPlayer(entity, player, true);
        }

        return removed;
    }

    /**
     * Hides the {@code entity} from all {@link Player}s besides manual viewers.
     *
     * @return true if the entity could be hidden, false if not
     */
    public synchronized boolean hide() {
        final Predicate<Player> newViewableRule = _ -> false;

        entityView.setViewableRule(newViewableRule);
        entityView.setAutoViewable(false);

        handleViewableRuleUpdate(newViewableRule);
        return true;
    }

    /**
     * Shows the {@code entity} to all {@link Player}s unless the players viewer rule does not permit them to see this entity.
     *
     * @return true if the entity could be shown, false if not
     */
    public synchronized boolean show() {
        if (entityView.isAutoViewable())
            return false;

        final Predicate<Player> newViewableRule = _ -> true;

        entityView.setViewableRule(newViewableRule);
        entityView.setAutoViewable(true);

        handleViewableRuleUpdate(newViewableRule);
        return true;
    }

    /**
     * Assuming that this {@link ViewEngine} represents a {@link Player}, hides all entities from the player
     *
     * @return true if this is called on a player view, otherwise false
     */
    public boolean hideEntities() {
        if (playerView == null)
            return false;

        synchronized (lock) {
            final Predicate<Entity> newViewerRule = _ -> false;

            playerView.setViewerRule(newViewerRule);
            playerView.setAutoViewEntities(false);

            handleViewerRuleUpdate(newViewerRule);
            return true;
        }
    }

    // player only
    public boolean showEntities() {
        if (playerView == null)
            return false;

        synchronized (lock) {
            final Predicate<Entity> newViewerRule = _ -> true;

            playerView.setViewerRule(newViewerRule);
            playerView.setAutoViewEntities(true);

            handleViewerRuleUpdate(newViewerRule);
            return true;
        }
    }

    public synchronized void synchronizePassengerVisibility(Entity entity) {
        for (Player player : entity.getViewers()) {
            if (ViewEngineUtils.canViewEntity(entity, player))
                continue;

            ViewEngineUtils.hideEntityFromPlayer(entity, player);
        }

        for (Entity passenger : entity.getPassengers()) {
            synchronizePassengerVisibility(passenger);
        }
    }

    public synchronized void setViewableRule(Predicate<Player> viewableRule) {
        entityView.setViewableRule(viewableRule);
        entityView.setAutoViewable(false);

        handleViewableRuleUpdate(viewableRule);
    }

    // player only
    public boolean setViewerRule(Predicate<Entity> viewerRule) {
        if (playerView == null)
            return false;

        synchronized (lock) {
            playerView.setViewerRule(viewerRule);
            playerView.setAutoViewEntities(false);

            handleViewerRuleUpdate(viewerRule);
            return true;
        }
    }

    // internal method
    private void handleViewableRuleUpdate(Predicate<Player> viewableRule) {
        for (Player nearbyPlayer : getNearbyPlayers()) {
            // the entity is now auto-viewable, attempt to show to all nearby players (we still have to worry about player viewer rules)
            if (entityView.isAutoViewable()) {
                ViewEngineUtils.showEntityToPlayer(this.entity, nearbyPlayer);
                continue;
            }

            // check if the player meets the new viewable rule
            if (viewableRule.test(nearbyPlayer)) {
                ViewEngineUtils.showEntityToPlayer(this.entity, nearbyPlayer);
            } else {
                ViewEngineUtils.hideEntityFromPlayer(this.entity, nearbyPlayer);
            }
        }
    }

    // internal method
    private void handleViewerRuleUpdate(Predicate<Entity> viewerRule) {
        if (playerView == null)
            return;

        final Player player = playerView.getPlayer();
        for (Entity nearbyEntity : getNearbyEntities()) {
            // the player can now auto-view entities, attempt to show the entity (we still have to worry about entity viewable rules)
            if (playerView.isAutoViewEntities()) {
                ViewEngineUtils.showEntityToPlayer(nearbyEntity, player);
            }

            // check if the entity meets the new viewer rule
            if (viewerRule.test(nearbyEntity)) {
                ViewEngineUtils.showEntityToPlayer(nearbyEntity, player);
            } else {
                ViewEngineUtils.hideEntityFromPlayer(nearbyEntity, player);
            }
        }
    }

    public synchronized boolean hasPredictableViewers() {
        return entityView.isAutoViewable() && entityView.getManualViewers().isEmpty();
    }

    public EntityView getEntityView() {
        return entityView;
    }

    @Nullable
    public PlayerView getPlayerView() {
        return playerView;
    }

    public synchronized void handleTrackerAddition(Entity entity) {
        // this ViewEngine is responsible for a player
        if (playerView != null) {
            ViewEngineUtils.showEntityToPlayer(entity, playerView.getPlayer());
        }

        // this ViewEngine is responsible for an entity
        // if the passed in entity is a player, then show this ViewEngine's entity to the passed in player
        if (entity instanceof Player player) {
            ViewEngineUtils.showEntityToPlayer(this.entity, player);
        }
    }

    public synchronized void handleTrackerRemoval(Entity entity) {
        if (playerView != null) {
            ViewEngineUtils.hideEntityFromPlayer(entity, playerView.getPlayer());
        }

        if (entity instanceof Player player) {
            ViewEngineUtils.hideEntityFromPlayer(this.entity, player);
        }
    }

    /**
     * Updates the current location (instance and point) of this entity.
     * This is useful for viewing purposes because we want to keep track of nearby players and entities.
     *
     * @param instance the instance that this entity is in, or null if none
     * @param point    the point that this entity is at
     */
    public void handleTrackerUpdate(@Nullable Instance instance, Point point) {
        this.trackedLocation = instance != null ? new TrackedLocation(instance, point) : null;
    }

    record TrackedLocation(Instance instance, Point point) {
    }

    private Collection<Player> getNearbyPlayers() {
        final TrackedLocation lastTrackedLocation = trackedLocation;
        if (lastTrackedLocation == null)
            return List.of();

        final Instance instance = lastTrackedLocation.instance();
        final Point point = lastTrackedLocation.point();

        final Int2ObjectMap<Player> nearbyPlayers = new Int2ObjectOpenHashMap<>(previousNearbyPlayersCount);
        instance.getEntityTracker().nearbyEntitiesByChunkRange(
                point,
                ServerProperties.ENTITY_VIEW_DISTANCE.get(),
                EntityTracker.Target.PLAYERS,
                player -> nearbyPlayers.putIfAbsent(player.getEntityId(), player)
        );

        previousNearbyPlayersCount = nearbyPlayers.size();
        return nearbyPlayers.values();
    }

    private Collection<Entity> getNearbyEntities() {
        final TrackedLocation lastTrackedLocation = trackedLocation;
        if (lastTrackedLocation == null)
            return List.of();

        final Instance instance = lastTrackedLocation.instance();
        final Point point = lastTrackedLocation.point();

        final Int2ObjectMap<Entity> nearbyEntities = new Int2ObjectOpenHashMap<>(previousNearbyEntitiesCount);
        instance.getEntityTracker().nearbyEntitiesByChunkRange(
                point,
                ServerProperties.ENTITY_VIEW_DISTANCE.get(),
                EntityTracker.Target.ENTITIES,
                entity -> nearbyEntities.putIfAbsent(entity.getEntityId(), entity)
        );

        previousNearbyEntitiesCount = nearbyEntities.size();
        return nearbyEntities.values();
    }

    @Unmodifiable
    public Set<Player> getViewers() {
        return viewers;
    }

    /**
     * An unmodifiable set that holds the current viewers of the {@code entity}.
     */
    final class ViewerSet extends AbstractSet<Player> {

        @Override
        public @NotNull Iterator<Player> iterator() {
            final Set<Player> players;
            synchronized (lock) {
                // consists of all viewers (including manual viewers)
                final IntSet viewers = entityView.getViewers();
                if (viewers.isEmpty())
                    return Collections.emptyIterator();

                final Instance instance = entity.getInstance();
                if (instance == null)
                    return Collections.emptyIterator();

                players = new HashSet<>(viewers.size());
                for (IntIterator iterator = viewers.iterator(); iterator.hasNext(); ) {
                    final int playerId = iterator.nextInt();
                    final Player player = (Player) instance.getEntityById(playerId);
                    if (player != null)
                        players.add(player);
                }
            }

            return players.iterator();
        }

        @Override
        public int size() {
            synchronized (lock) {
                final Instance instance = entity.getInstance();
                if (instance == null)
                    return 0;

                int count = 0;
                for (IntIterator iterator = entityView.getViewers().iterator(); iterator.hasNext(); ) {
                    final int viewerId = iterator.nextInt();
                    if (instance.getEntityById(viewerId) != null)
                        count++;
                }

                return count;
            }
        }

        @Override
        public boolean isEmpty() {
            synchronized (lock) {
                return entityView.getViewers().isEmpty();
            }
        }

        @Override
        public boolean contains(Object object) {
            if (!(object instanceof Player player))
                return false;

            synchronized (lock) {
                return entityView.getViewers().contains(player.getEntityId());
            }
        }
    }
}