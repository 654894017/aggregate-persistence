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
 * 数据库仓库支持类，提供安全更新、列表增量更新等基础操作
 *
 * @param
 */
public abstract class DbRepositorySupport {
    private static final Logger log = LoggerFactory.getLogger(DbRepositorySupport.class);

    /**
     * 通过数据库乐观锁实现安全更新
     *
     * @param newObj   新对象
     * @param oldObj   旧对象
     * @param function 转换函数
     * @param <A>      继承自Versionable的实体类型
     * @param <B>      继承自Versionable的DTO类型
     * @return 是否更新成功
     */
    public <A extends Versionable, B extends Versionable> boolean executeSafeUpdate(B newObj, B oldObj, Function<B, A> function) {
        // 空指针检查
        Objects.requireNonNull(newObj, "新对象不能为null");
        Objects.requireNonNull(oldObj, "旧对象不能为null");
        Objects.requireNonNull(function, "转换函数不能为null");

        A newEntity = function.apply(newObj);
        A oldEntity = function.apply(oldObj);

        // 查找变更字段
        Set<String> changedFields = ObjectComparator.findChangedFields(newEntity, oldEntity, false);
        if (changedFields.isEmpty()) {
            log.debug("对象没有变更，无需更新。实体ID: {}", newEntity.getId());
            return true;
        }

        // 执行更新并同步版本号
        boolean result = update(newEntity, changedFields);
        if (result) {
            newObj.setVersion(newEntity.getVersion());
            log.debug("安全更新成功。实体ID: {}, 变更字段: {}", newEntity.getId(), changedFields);
        } else {
            log.warn("安全更新失败。实体ID: {}", newEntity.getId());
        }
        return result;
    }

    /**
     * 列表模式的增量更新(自动处理新增、修改、删除的实体)
     * 注意: 如果id在数据库里不存在则进行新增操作
     *
     * @param newItems  新的实体列表
     * @param oldItems  旧的实体列表
     * @param converter 转换函数
     * @param <A>       继承自ID的实体类型
     * @param <B>       继承自ID的DTO类型
     * @return 是否更新成功
     */
    public <A extends ID, B extends ID> boolean executeListUpdate(Collection<B> newItems, Collection<B> oldItems, Function<B, A> converter) {
        return executeListUpdate(newItems, oldItems, converter, null);
    }

    /**
     * 列表模式的增量更新(自动处理新增、修改、删除的实体)
     *
     * @param newItems  新的实体列表
     * @param oldItems  旧的实体列表
     * @param converter 转换函数
     * @param isNew     判断是否为新实体的 predicate
     * @param <T>       继承自ID的DTO类型
     * @param <B>       继承自ID的实体类型
     * @return 是否更新成功
     */
    public <T extends ID, B extends ID> boolean executeListUpdate(Collection<T> newItems, Collection<T> oldItems,
                                                                  Function<T, B> converter, Predicate<T> isNew) {
        // 空指针处理，避免后续操作出现NPE
        Collection<T> safeNewItems = Optional.ofNullable(newItems).orElse(Collections.emptyList());
        Collection<T> safeOldItems = Optional.ofNullable(oldItems).orElse(Collections.emptyList());
        Objects.requireNonNull(converter, "转换函数不能为null");

        log.debug("开始执行列表增量更新。新列表大小: {}, 旧列表大小: {}", safeNewItems.size(), safeOldItems.size());

        // 1. 处理新增的实体
        Collection<T> newAddItems = findNewEntities(safeNewItems, safeOldItems, isNew);
        handleNewEntities(newAddItems, converter);

        // 2. 处理更新的实体
        handleUpdatedEntities(safeNewItems, safeOldItems, newAddItems, converter);

        // 3. 处理删除的实体
        boolean deleteResult = handleDeletedEntities(safeNewItems, safeOldItems, converter);

        log.debug("列表增量更新完成。新增: {}, 更新: {}, 删除: {}",
                newAddItems.size(),
                (safeNewItems.size() - newAddItems.size()),
                (safeOldItems.size() - (safeNewItems.size() - newAddItems.size())));

        return deleteResult;
    }

    /**
     * 查找新实体
     */
    private <T extends ID> Collection<T> findNewEntities(Collection<T> newItems, Collection<T> oldItems, Predicate<T> isNew) {
        if (isNew == null) {
            return ObjectComparator.findNewEntities(newItems, oldItems);
        } else {
            return ObjectComparator.findNewEntities(newItems, isNew::test);
        }
    }

    /**
     * 处理新增实体
     */
    private <T extends ID, B extends ID> void handleNewEntities(Collection<T> newAddItems, Function<T, B> converter) {
        if (newAddItems.isEmpty()) {
            log.debug("没有需要新增的实体");
            return;
        }

        log.debug("开始处理新增实体，数量: {}", newAddItems.size());

        // 转换并批量插入
        Map<B, T> convertedMap = new IdentityHashMap<>();
        newAddItems.forEach(item ->
                convertedMap.put(converter.apply(item), item)
        );

        boolean insertResult = insertBatch(convertedMap.keySet());

        if (insertResult) {
            // 同步自增ID到原始对象
            convertedMap.forEach((converted, original) -> original.setId(converted.getId()));
            log.debug("新增实体处理完成，数量: {}", newAddItems.size());
        } else {
            log.error("新增实体处理失败，数量: {}", newAddItems.size());
            throw new AggregatePersistenceException("批量插入新实体失败");
        }
    }

    /**
     * 处理更新实体
     */
    private <T extends ID, B extends ID> void handleUpdatedEntities(Collection<T> newItems, Collection<T> oldItems,
                                                                    Collection<T> newAddItems, Function<T, B> converter) {
        // 排除新增项，剩下的是可能需要更新的项
        Collection<T> potentialUpdates = new ArrayList<>(newItems);
        potentialUpdates.removeAll(newAddItems);

        if (potentialUpdates.isEmpty()) {
            log.debug("没有需要更新的实体");
            return;
        }

        // 转换为实体对象
        Collection<B> newEntities = potentialUpdates.stream()
                .map(converter)
                .collect(Collectors.toList());

        Collection<B> oldEntities = oldItems.stream()
                .map(converter)
                .collect(Collectors.toList());

        // 查找变更的实体并更新
        Collection<ChangedEntity<B>> changedEntities = ObjectComparator.findChangedEntities(newEntities, oldEntities);

        if (changedEntities.isEmpty()) {
            log.debug("没有发现变更的实体，无需更新");
            return;
        }

        log.debug("开始处理更新实体，数量: {}", changedEntities.size());

        changedEntities.forEach(changedEntity -> {
            B newEntity = changedEntity.getNewEntity();
            B oldEntity = changedEntity.getOldEntity();

            Set<String> changedFields = ObjectComparator.findChangedFields(newEntity, oldEntity);
            if (!changedFields.isEmpty()) {
                boolean updateResult = update(newEntity, changedFields);
                if (updateResult) {
                    log.info("实体更新成功。ID: {}, 变更字段: {}", newEntity.getId(), changedFields);
                } else {
                    log.error("实体更新失败。ID: {}", newEntity.getId());
                }
            }
        });
    }

    /**
     * 处理删除实体
     */
    private <T extends ID, B extends ID> boolean handleDeletedEntities(Collection<T> newItems, Collection<T> oldItems,
                                                                       Function<T, B> converter) {
        Collection<B> newEntities = newItems.stream()
                .map(converter)
                .collect(Collectors.toList());

        Collection<B> oldEntities = oldItems.stream()
                .map(converter)
                .collect(Collectors.toList());

        Collection<B> removedItems = ObjectComparator.findRemovedEntities(newEntities, oldEntities);

        if (removedItems.isEmpty()) {
            log.debug("没有需要删除的实体");
            return true;
        }

        log.debug("开始处理删除实体，数量: {}", removedItems.size());
        boolean deleteResult = deleteBatch(removedItems);

        if (deleteResult) {
            log.debug("删除实体处理完成，数量: {}", removedItems.size());
        } else {
            log.error("删除实体处理失败，数量: {}", removedItems.size());
        }

        return deleteResult;
    }

    /**
     * 批量插入实体
     *
     * @param items 待插入的实体集合
     * @param <A>   实体类型
     * @return 是否插入成功
     */
    protected abstract <A extends ID> boolean insertBatch(Collection<A> items);

    /**
     * 批量删除实体
     *
     * @param items 待删除的实体集合
     * @param <A>   实体类型
     * @return 是否删除成功
     */
    protected abstract <A extends ID> boolean deleteBatch(Collection<A> items);

    /**
     * 插入单个实体
     *
     * @param entity 待插入的实体
     * @param <A>    实体类型
     * @return 是否插入成功
     */
    protected abstract <A extends ID> boolean insert(A entity);

    /**
     * 插入单个实体并进行转换
     *
     * @param entity    待插入的实体
     * @param converter 转换函数
     * @param <A>       实体类型
     * @param <B>       转换后的类型
     * @return 是否插入成功
     */
    protected abstract <A extends ID, B extends ID> boolean insert(A entity, Function<A, B> converter);

    /**
     * 更新实体（非版本化）
     *
     * @param entity        待更新的实体
     * @param changedFields 变更的字段集合
     * @param <A>           实体类型
     * @return 是否更新成功
     */
    protected abstract <A extends ID> boolean update(A entity, Set<String> changedFields);

    /**
     * 更新实体（版本化，支持乐观锁）
     *
     * @param entity        待更新的实体
     * @param changedFields 变更的字段集合
     * @param <A>           继承自Versionable的实体类型
     * @return 是否更新成功
     */
    protected abstract <A extends Versionable> boolean update(A entity, Set<String> changedFields);
}
