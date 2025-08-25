package com.damon.aggregate.persistence.comparator;

import cn.hutool.core.builder.EqualsBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.damon.aggregate.persistence.ID;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ObjectComparator {
    private static final Map<Class<?>, Field[]> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();

    private static Field[] getCachedFields(Class<?> clazz) {
        return CLASS_FIELD_CACHE.computeIfAbsent(clazz, ReflectUtil::getFields);
    }

    public static Set<String> findChangedFields(Object newObject, Object oldObject) {
        return findChangedFields(newObject, oldObject, false);
    }

    public static Set<String> findChangedFields(Object newObject, Object oldObject, boolean toUnderlineCase) {
        Objects.requireNonNull(newObject, "New object cannot be null");
        Objects.requireNonNull(oldObject, "Old object cannot be null");
        if (ObjectUtil.notEqual(newObject.getClass().getName(), oldObject.getClass().getName())) {
            return Collections.emptySet();
        }

        Set<String> differentFields = new HashSet<>();
        Class<?> clazz = newObject.getClass();
        Field[] fields = getCachedFields(clazz);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);

            Object newValue = ReflectUtil.getFieldValue(newObject, field);
            Object oldValue = ReflectUtil.getFieldValue(oldObject, field);

            if (ObjectUtil.notEqual(newValue, oldValue)) {
                String name = toUnderlineCase ? StrUtil.toUnderlineCase(field.getName()) : field.getName();
                differentFields.add(name);
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