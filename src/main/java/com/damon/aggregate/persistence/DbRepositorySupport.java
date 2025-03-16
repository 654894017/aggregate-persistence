package com.damon.aggregate.persistence;


import com.damon.aggregate.persistence.comparator.ChangedEntity;
import com.damon.aggregate.persistence.comparator.ObjectComparator;
import com.damon.aggregate.persistence.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public abstract class DbRepositorySupport {
    private final Logger log = LoggerFactory.getLogger(DbRepositorySupport.class);

    /**
     * 通过数据库乐观锁实现安全更新
     *
     * @param newObj
     * @param oldObj
     * @param function
     * @param <A>
     * @param <B>
     * @return
     */
    public <A extends Versionable, B extends ID> Boolean executeSafeUpdate(B newObj, B oldObj, Function<B, A> function) {
        A newObject = function.apply(newObj);
        A oldObject = function.apply(oldObj);
        Set<String> changedFields = ObjectComparator.findChangedFields(newObject, oldObject, false);
        return update(newObject, changedFields);
    }

    /**
     * 不带数据库乐观锁的更新，适用非主表数据更新
     *
     * @param newObj
     * @param oldObj
     * @param convert
     * @param <A>
     * @param <B>
     * @return
     */
    public <A extends ID, B extends ID> Boolean executeUpdate(B newObj, B oldObj, Function<B, A> convert) {
        A newObject = convert.apply(newObj);
        A oldObject = convert.apply(oldObj);
        Set<String> changedFields = ObjectComparator.findChangedFields(newObject, oldObject);
        return update(newObject, changedFields);
    }

    /**
     * 列表模式的增量更新(自动处理新增、修改、删除的实体)
     * <br>
     * 注意: 如果id在数据库里不存在则进行新增操作
     *
     * @param newItem
     * @param oldItem
     * @param convert
     * @param <A>
     * @param <B>
     * @return
     */
    public <A extends ID, B extends ID> Boolean executeListUpdate(Collection<B> newItem, Collection<B> oldItem, Function<B, A> convert) {
        return executeListUpdate(newItem, oldItem, convert, null);
    }


    /**
     * 列表模式的增量更新(自动处理新增、修改、删除的实体)
     *
     * @param newItem
     * @param oldItem
     * @param convertor
     * @param isNew
     * @param <T>
     * @param <B>
     * @return
     */
    public <T extends ID, B extends ID> Boolean executeListUpdate(Collection<T> newItem, Collection<T> oldItem,
                                                                  Function<T, B> convertor, Predicate<T> isNew) {
        Collection<T> newAddItems;
        if (isNew == null) {
            newAddItems = ObjectComparator.findNewEntities(newItem, oldItem);
        } else {
            newAddItems = ObjectComparator.findNewEntities(newItem, isNew::test);
        }
        for (T item : newAddItems) {
            B itemPO = convertor.apply(item);
            boolean result = insert(itemPO);
            item.setId(itemPO.getId());
            if (!result) {
                log.warn("Create item failed, type: {} , info : {}", item.getClass().getTypeName(), JsonUtils.jsonToString(item));
                return false;
            }
        }
        newItem.removeAll(newAddItems);
        Collection<B> newItems = newItem.stream().map(convertor::apply).collect(Collectors.toList());
        Collection<B> oldItems = oldItem.stream().map(convertor::apply).collect(Collectors.toList());
        Collection<ChangedEntity<B>> changedEntityList = ObjectComparator.findChangedEntities(newItems, oldItems);
        for (ChangedEntity<B> changedEntity : changedEntityList) {
            Set<String> changedFields = ObjectComparator.findChangedFields(changedEntity.getNewEntity(), changedEntity.getOldEntity());
            if (changedFields.isEmpty()) {
                continue;
            }
            boolean result = update(changedEntity.getNewEntity(), changedFields);
            if (!result) {
                log.warn("Update item failed, type: {} , info : {} ,change fields : {}",
                        changedEntity.getNewEntity().getClass().getTypeName(),
                        JsonUtils.jsonToString(changedEntity.getNewEntity()), changedFields
                );
                return false;
            }
        }

        Collection<B> removedItems = ObjectComparator.findRemovedEntities(newItems, oldItems);
        if (removedItems.isEmpty()) {
            return true;
        }

        for (B item : removedItems) {
            boolean result = this.delete(item);
            if (!result) {
                Set<Object> removedItemIds = removedItems.stream().map(B::getId).collect(Collectors.toSet());
                log.warn("Delete item failed, type: {} , ids : {}", item.getClass().getTypeName(), removedItemIds);
                return false;
            }
        }

        return true;
    }

    protected abstract <A extends ID> Boolean delete(A item);

    protected abstract <A extends ID> Boolean insert(A entity);

    protected abstract <A extends ID, B extends ID> Boolean insert(A entity, Function<A, B> convertor);

    protected abstract <A extends ID> Boolean update(A entity, Set<String> changedFields);

    protected abstract <A extends Versionable> Boolean update(A entity, Set<String> changedFields);


}
