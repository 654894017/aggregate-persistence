package com.damon.aggregate.persistence;

import com.damon.aggregate.persistence.comparator.ChangedEntity;
import com.damon.aggregate.persistence.comparator.ObjectComparator;
import com.damon.aggregate.persistence.exception.AggregatePersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Database repository support class providing safe update and list incremental update operations
 */
public abstract class DbRepositorySupport {
    private static final Logger log = LoggerFactory.getLogger(DbRepositorySupport.class);

    /**
     * Perform safe update using database optimistic locking
     *
     * @param newObj   New object
     * @param oldObj   Old object
     * @param function Conversion function
     * @param <A>      Entity type extending Versionable
     * @param <B>      DTO type extending Versionable
     * @return Whether the update was successful
     */
    public <A extends Versionable, B extends Versionable> boolean executeSafeUpdate(B newObj, B oldObj, Function<B, A> function) {
        // Null pointer checks
        Objects.requireNonNull(newObj, "New object cannot be null");
        Objects.requireNonNull(oldObj, "Old object cannot be null");
        Objects.requireNonNull(function, "Conversion function cannot be null");

        A newEntity = function.apply(newObj);
        A oldEntity = function.apply(oldObj);
        String entityType = newEntity.getClass().getSimpleName();
        Object entityId = newEntity.getId();

        // Find changed fields
        Set<String> changedFields = ObjectComparator.findChangedFields(newEntity, oldEntity, false);
        if (changedFields.isEmpty()) {
            log.debug("[Entity: {}] No changes detected, no update needed. Entity ID: {}", entityType, entityId);
            return true;
        }

        // Execute update and sync version number
        boolean result = update(newEntity, changedFields);
        if (result) {
            newObj.setVersion(newEntity.getVersion());
            log.debug("[Entity: {}] Safe update successful. Entity ID: {}, Changed fields: {}",
                    entityType, entityId, changedFields);
        } else {
            log.warn("[Entity: {}] Safe update failed. Entity ID: {}", entityType, entityId);
        }
        return result;
    }

    /**
     * Incremental update for list (automatically handles new, modified, and deleted entities)
     * Note: Entities with non-existent IDs will be inserted
     *
     * @param newItems  New entity list
     * @param oldItems  Old entity list
     * @param converter Conversion function
     * @param <A>       Entity type extending ID
     * @param <B>       DTO type extending ID
     * @return Whether the update was successful
     */
    public <A extends ID, B extends ID> boolean executeListUpdate(Collection<B> newItems, Collection<B> oldItems, Function<B, A> converter) {
        return executeListUpdate(newItems, oldItems, converter, null);
    }

    /**
     * Incremental update for list (automatically handles new, modified, and deleted entities)
     *
     * @param newItems  New entity list
     * @param oldItems  Old entity list
     * @param converter Conversion function
     * @param isNew     Predicate to determine if an entity is new
     * @param <T>       DTO type extending ID
     * @param <B>       Entity type extending ID
     * @return Whether the update was successful
     */
    public <T extends ID, B extends ID> boolean executeListUpdate(Collection<T> newItems, Collection<T> oldItems,
                                                                  Function<T, B> converter, Predicate<T> isNew) {
        // Handle null collections to avoid NPE
        Collection<T> safeNewItems = Optional.ofNullable(newItems).orElse(Collections.emptyList());
        Collection<T> safeOldItems = Optional.ofNullable(oldItems).orElse(Collections.emptyList());
        Objects.requireNonNull(converter, "Conversion function cannot be null");

        // Determine entity type
        String entityType = getEntityType(safeNewItems, converter);

        // 1. Handle new entities
        Collection<T> newAddItems = findNewEntities(safeNewItems, safeOldItems, isNew);
        handleNewEntities(newAddItems, converter, entityType);

        // 2. Handle updated entities
        handleUpdatedEntities(safeNewItems, safeOldItems, newAddItems, converter, entityType);

        // 3. Handle deleted entities
        boolean deleteResult = handleDeletedEntities(safeNewItems, safeOldItems, converter, entityType);

        return deleteResult;
    }

    /**
     * Find new entities in the collection
     */
    private <T extends ID> Collection<T> findNewEntities(Collection<T> newItems, Collection<T> oldItems, Predicate<T> isNew) {
        if (isNew == null) {
            return ObjectComparator.findNewEntities(newItems, oldItems);
        } else {
            return ObjectComparator.findNewEntities(newItems, isNew::test);
        }
    }

    /**
     * Handle new entities insertion
     */
    private <T extends ID, B extends ID> void handleNewEntities(Collection<T> newAddItems, Function<T, B> converter, String entityType) {
        if (newAddItems.isEmpty()) {
            log.debug("[Entity: {}] No new entities to add", entityType);
            return;
        }

        log.debug("[Entity: {}] Starting to process new entities. Count: {}", entityType, newAddItems.size());

        // Convert and batch insert
        Map<B, T> convertedMap = new IdentityHashMap<>();
        newAddItems.forEach(item -> convertedMap.put(converter.apply(item), item));

        boolean insertResult = insertBatch(convertedMap.keySet());

        if (insertResult) {
            // Sync auto-generated IDs back to original objects
            convertedMap.forEach((converted, original) -> original.setId(converted.getId()));
            log.debug("[Entity: {}] Completed processing new entities. Count: {}", entityType, newAddItems.size());
        } else {
            log.error("[Entity: {}] Failed to process new entities. Count: {}", entityType, newAddItems.size());
            throw new AggregatePersistenceException(String.format("[Entity: %s] Failed to batch insert new entities", entityType));
        }
    }

    /**
     * Handle entity updates
     */
    private <T extends ID, B extends ID> void handleUpdatedEntities(Collection<T> newItems, Collection<T> oldItems,
                                                                    Collection<T> newAddItems, Function<T, B> converter, String entityType) {
        // Exclude new items, remaining may need updates
        Collection<T> potentialUpdates = new ArrayList<>(newItems);
        potentialUpdates.removeAll(newAddItems);

        if (potentialUpdates.isEmpty()) {
            log.debug("[Entity: {}] No entities to update", entityType);
            return;
        }

        // Convert to entity objects
        Collection<B> newEntities = potentialUpdates.stream()
                .map(converter)
                .collect(Collectors.toList());

        Collection<B> oldEntities = oldItems.stream()
                .map(converter)
                .collect(Collectors.toList());

        // Find changed entities and update
        Collection<ChangedEntity<B>> changedEntities = ObjectComparator.findChangedEntities(newEntities, oldEntities);

        if (changedEntities.isEmpty()) {
            log.debug("[Entity: {}] No changed entities found, no updates needed", entityType);
            return;
        }

        log.debug("[Entity: {}] Starting to process updated entities. Count: {}", entityType, changedEntities.size());

        changedEntities.forEach(changedEntity -> {
            B newEntity = changedEntity.getNewEntity();
            B oldEntity = changedEntity.getOldEntity();

            Set<String> changedFields = ObjectComparator.findChangedFields(newEntity, oldEntity);
            if (!changedFields.isEmpty()) {
                boolean updateResult = update(newEntity, changedFields);
                if (updateResult) {
                    log.info("[Entity: {}] Entity update successful. ID: {}, Changed fields: {}",
                            entityType, newEntity.getId(), changedFields);
                } else {
                    log.error("[Entity: {}] Entity update failed. ID: {}", entityType, newEntity.getId());
                }
            }
        });
    }

    /**
     * Handle entity deletions
     */
    private <T extends ID, B extends ID> boolean handleDeletedEntities(Collection<T> newItems, Collection<T> oldItems,
                                                                       Function<T, B> converter, String entityType) {
        Collection<B> newEntities = newItems.stream()
                .map(converter)
                .collect(Collectors.toList());

        Collection<B> oldEntities = oldItems.stream()
                .map(converter)
                .collect(Collectors.toList());

        Collection<B> removedItems = ObjectComparator.findRemovedEntities(newEntities, oldEntities);

        if (removedItems.isEmpty()) {
            log.debug("[Entity: {}] No entities to delete", entityType);
            return true;
        }

        log.debug("[Entity: {}] Starting to process deleted entities. Count: {}", entityType, removedItems.size());
        boolean deleteResult = deleteBatch(removedItems);

        if (deleteResult) {
            log.debug("[Entity: {}] Completed processing deleted entities. Count: {}", entityType, removedItems.size());
        } else {
            log.error("[Entity: {}] Failed to process deleted entities. Count: {}", entityType, removedItems.size());
        }

        return deleteResult;
    }

    /**
     * Get entity type name
     */
    private <T extends ID, B extends ID> String getEntityType(Collection<T> items, Function<T, B> converter) {
        if (items.isEmpty()) {
            return "UnknownType";
        }
        return items.iterator().next().getClass().getSimpleName();
    }

    /**
     * Batch insert entities
     *
     * @param items Entities to insert
     * @param <A>   Entity type
     * @return Whether insertion was successful
     */
    protected abstract <A extends ID> boolean insertBatch(Collection<A> items);

    /**
     * Batch delete entities
     *
     * @param items Entities to delete
     * @param <A>   Entity type
     * @return Whether deletion was successful
     */
    protected abstract <A extends ID> boolean deleteBatch(Collection<A> items);

    /**
     * Insert single entity
     *
     * @param entity Entity to insert
     * @param <A>    Entity type
     * @return Whether insertion was successful
     */
    protected abstract <A extends ID> boolean insert(A entity);

    /**
     * Insert single entity with conversion
     *
     * @param entity    Entity to insert
     * @param converter Conversion function
     * @param <A>       Source entity type
     * @param <B>       Target entity type
     * @return Whether insertion was successful
     */
    protected abstract <A extends ID, B extends ID> boolean insert(A entity, Function<A, B> converter);

    /**
     * Update non-versioned entity
     *
     * @param entity        Entity to update
     * @param changedFields Changed fields
     * @param <A>           Entity type
     * @return Whether update was successful
     */
    protected abstract <A extends ID> boolean update(A entity, Set<String> changedFields);

    /**
     * Update versioned entity with optimistic locking
     *
     * @param entity        Entity to update
     * @param changedFields Changed fields
     * @param <A>           Entity type extending Versionable
     * @return Whether update was successful
     */
    protected abstract <A extends Versionable> boolean update(A entity, Set<String> changedFields);
}
