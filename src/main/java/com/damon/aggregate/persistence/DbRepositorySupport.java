package com.damon.aggregate.persistence;


import com.damon.aggregate.persistence.comparator.ChangedEntity;
import com.damon.aggregate.persistence.comparator.ObjectComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    public <A extends Versionable, B extends ID> boolean executeSafeUpdate(B newObj, B oldObj, Function<B, A> function) {
        A newObject = function.apply(newObj);
        A oldObject = function.apply(oldObj);
        Set<String> changedFields = ObjectComparator.findChangedFields(newObject, oldObject, false);
        return update(newObject, changedFields);
    }

//    /**
//     * 不带数据库乐观锁的更新，适用非主表数据更新
//     *
//     * @param newObj
//     * @param oldObj
//     * @param convert
//     * @param <A>
//     * @param <B>
//     * @return
//     */
//    public <A extends ID, B extends ID> boolean executeUpdate(B newObj, B oldObj, Function<B, A> convert) {
//        A newObject = convert.apply(newObj);
//        A oldObject = convert.apply(oldObj);
//        Set<String> changedFields = ObjectComparator.findChangedFields(newObject, oldObject);
//        return update(newObject, changedFields);
//    }

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
    public <A extends ID, B extends ID> boolean executeListUpdate(Collection<B> newItem, Collection<B> oldItem, Function<B, A> convert) {
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
    public <T extends ID, B extends ID> boolean executeListUpdate(Collection<T> newItem, Collection<T> oldItem,
                                                                  Function<T, B> convertor, Predicate<T> isNew) {
        Collection<T> newAddItems;
        if (isNew == null) {
            newAddItems = ObjectComparator.findNewEntities(newItem, oldItem);
        } else {
            newAddItems = ObjectComparator.findNewEntities(newItem, isNew::test);
        }
        //1.处理新增的实体
        Map<B, T> map = new IdentityHashMap<>();
        newAddItems.forEach(item ->
                map.put(convertor.apply(item), item)
        );
        this.insertBatch(map.keySet());
        map.forEach((converted, original) ->
                //把数据库自增id设置回原来的实体
                original.setId(converted.getId())
        );


        //2.处理更新的实体
        Collection<T> newestItems = new ArrayList<>(newItem);
        newestItems.removeAll(newAddItems);
        Collection<B> newItems = newestItems.stream().map(convertor::apply).collect(Collectors.toList());
        Collection<B> oldItems = oldItem.stream().map(convertor::apply).collect(Collectors.toList());
        Collection<ChangedEntity<B>> changedEntities = ObjectComparator.findChangedEntities(newItems, oldItems);
        changedEntities.forEach(changedEntity -> {
            Set<String> changedFields = ObjectComparator.findChangedFields(
                    changedEntity.getNewEntity(), changedEntity.getOldEntity()
            );
            if (!changedFields.isEmpty()) {
                update(changedEntity.getNewEntity(), changedFields);
            }
        });

        //3.处理删除的实体
        Collection<B> removedItems = ObjectComparator.findRemovedEntities(newItems, oldItems);
        if (removedItems.isEmpty()) {
            return true;
        }

        return deleteBatch(removedItems);
    }

    protected abstract <A extends ID> boolean insertBatch(Collection<A> items);

    protected abstract <A extends ID> boolean deleteBatch(Collection<A> items);

    protected abstract <A extends ID> boolean insert(A entity);

    protected abstract <A extends ID, B extends ID> boolean insert(A entity, Function<A, B> convertor);

    protected abstract <A extends ID> boolean update(A entity, Set<String> changedFields);

    protected abstract <A extends Versionable> boolean update(A entity, Set<String> changedFields);


}
