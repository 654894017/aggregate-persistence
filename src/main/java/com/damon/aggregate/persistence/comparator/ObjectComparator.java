package com.damon.aggregate.persistence.comparator;

import cn.hutool.core.builder.EqualsBuilder;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.damon.aggregate.persistence.ID;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ObjectComparator {
    public static Set<String> findChangedFields(Object newObject, Object oldObject) {
        return findChangedFields(newObject, oldObject, false);
    }

    /**
     * 比对两个数据库实体对象的字段差异。
     * <p>
     * 基于 MyBatis-Plus 的 {@link TableInfo} 元数据进行字段遍历，仅比对与数据库列映射的字段，
     * 通过 {@link MetaObject} 进行字段读取，相比通用反射性能更优（TableInfo 已被 MyBatis 缓存）。
     *
     * @param newObject       新对象（必须为 MyBatis 实体类型）
     * @param oldObject       旧对象（必须与 newObject 同类型）
     * @param toUnderlineCase 是否输出下划线列名（true: 返回数据库列名；false: 返回 Java 属性名）
     * @return 发生变化的字段集合
     */
    public static Set<String> findChangedFields(Object newObject, Object oldObject, boolean toUnderlineCase) {
        Objects.requireNonNull(newObject, "New object cannot be null");
        Objects.requireNonNull(oldObject, "Old object cannot be null");
        if (ObjectUtil.notEqual(newObject.getClass().getName(), oldObject.getClass().getName())) {
            return Collections.emptySet();
        }

        TableInfo tableInfo = TableInfoHelper.getTableInfo(newObject.getClass());
        if (tableInfo == null) {
            throw new IllegalArgumentException(
                    String.format("Class [%s] is not a MyBatis-Plus entity (no TableInfo found). "
                            + "findChangedFields requires a database entity type.", newObject.getClass().getName()));
        }

        Set<String> differentFields = new HashSet<>();
        MetaObject newMeta = SystemMetaObject.forObject(newObject);
        MetaObject oldMeta = SystemMetaObject.forObject(oldObject);

        for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
            String property = fieldInfo.getProperty();
            Object newValue = newMeta.getValue(property);
            Object oldValue = oldMeta.getValue(property);

            if (ObjectUtil.notEqual(newValue, oldValue)) {
                differentFields.add(toUnderlineCase ? fieldInfo.getColumn() : property);
            }
        }
        return differentFields;
    }

    /**
     * 查询列表新增的实体(默认ID为空或新列表中的ID在旧的列表中不存在都当新的实体处理)
     *
     * @param newEntities
     * @param oldEntities
     * @param <T>
     * @return
     */
    public static <T extends ID> Collection<T> findNewEntities(Collection<T> newEntities, Collection<T> oldEntities) {
        Set<Object> newIds = newEntities.stream().map(T::getId).collect(Collectors.toSet());
        Set<Object> oldIds = oldEntities.stream().map(T::getId).collect(Collectors.toSet());
        newIds.removeAll(oldIds);
        return newEntities.stream().filter((item) -> newIds.contains(item.getId()) || item.getId() == null).collect(Collectors.toList());
    }

    public static <T extends ID> Collection<T> findNewEntities(Collection<T> newEntities, Predicate<T> predicate) {
        return newEntities.stream().filter(predicate).collect(Collectors.toList());
    }

    public static <T extends ID> Collection<T> findRemovedEntities(Collection<T> newEntities, Collection<T> oldEntities) {
        Set<Object> newIds = newEntities.stream().map(T::getId).collect(Collectors.toSet());
        Set<Object> oldIds = oldEntities.stream().map(T::getId).collect(Collectors.toSet());
        oldIds.removeAll(newIds);
        return oldEntities.stream().filter((item) -> oldIds.contains(item.getId())).collect(Collectors.toList());
    }

    public static <T extends ID> Collection<ChangedEntity<T>> findChangedEntities(Collection<T> newEntities, Collection<T> oldEntities) {
        Map<Object, T> newEntityMap = newEntities.stream().collect(Collectors.toMap(ID::getId, Function.identity()));
        Map<Object, T> oldEntityMap = oldEntities.stream().collect(Collectors.toMap(ID::getId, Function.identity()));
        //交集
        oldEntityMap.keySet().retainAll(newEntityMap.keySet());
        List<ChangedEntity<T>> results = new ArrayList();
        Iterator iterator = oldEntityMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ID, T> entry = (Map.Entry) iterator.next();
            T oldEntity = entry.getValue();
            T newEntity = newEntityMap.get(entry.getKey());
            if (!EqualsBuilder.reflectionEquals(oldEntity, newEntity, false)) {
                results.add(new ChangedEntity(oldEntity, newEntity));
            }
        }
        return results;
    }


}