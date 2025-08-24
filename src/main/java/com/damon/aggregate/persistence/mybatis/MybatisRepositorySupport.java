package com.damon.aggregate.persistence.mybatis;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.damon.aggregate.persistence.DbRepositorySupport;
import com.damon.aggregate.persistence.ID;
import com.damon.aggregate.persistence.Versionable;
import com.damon.aggregate.persistence.exception.AggregatePersistenceException;
import com.damon.aggregate.persistence.utils.ReflectUtils;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MybatisRepositorySupport extends DbRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(MybatisRepositorySupport.class);

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @SuppressWarnings("unchecked")
    protected <T extends ID> BaseMapper<T> getMapper(Class<T> entityClass) {
        // Get current SqlSession (automatically handles multi-data sources)
        SqlSession sqlSession = getSqlSession();
        return SqlHelper.getMapper(entityClass, sqlSession);
    }

    @Override
    protected <A extends ID> boolean insertBatch(Collection<A> items) {
        if (CollectionUtils.isEmpty(items)) {
            log.debug("[Batch Insert] No items to insert");
            return true;
        }

        Class<A> entityClass = (Class<A>) items.iterator().next().getClass();
        String entityType = entityClass.getSimpleName();

        if (items.size() > batchSize()) {
            throw new AggregatePersistenceException(
                    String.format("[Entity: %s] Insert count exceeds batch limit. Max batch size: %d, Actual count: %d",
                            entityType, batchSize(), items.size())
            );
        }

        BaseMapper<A> baseMapper = getMapper(entityClass);
        baseMapper.insert(items, batchSize());
        log.debug("[Entity: {}] Batch insert successful. Inserted {} records", entityType, items.size());

        return true;
    }

    @Override
    protected <A extends ID> boolean deleteBatch(Collection<A> items) {
        if (CollectionUtils.isEmpty(items)) {
            log.debug("[Batch Delete] No items to delete");
            return true;
        }

        Class<A> entityClass = (Class<A>) items.iterator().next().getClass();
        String entityType = entityClass.getSimpleName();

        Set<Object> ids = items.stream()
                .filter(Objects::nonNull)
                .map(ID::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            log.warn("[Entity: {}] Batch delete failed - all entity IDs are null", entityType);
            return false;
        }

        SqlSession sqlSession = getSqlSession();
        try {
            Map<String, Object> params = CollectionUtils.newHashMapWithExpectedSize(1);
            params.put(Constants.COLL, ids);

            int deleted = sqlSession.delete(sqlStatement(SqlMethod.DELETE_BY_IDS.getMethod(), entityClass), params);
            boolean success = deleted > 0;

            log.debug("[Entity: {}] Batch delete completed. Target IDs: {}, Deleted records: {}",
                    entityType, ids.size(), deleted);
            return success;
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    @Override
    protected <A extends ID> boolean insert(A entity) {
        Objects.requireNonNull(entity, "[Insert] Entity cannot be null");
        String entityType = entity.getClass().getSimpleName();

        // Initialize version for versionable entities
        if (entity instanceof Versionable) {
            Versionable versionable = (Versionable) entity;
            if (versionable.getVersion() == null) {
                versionable.setVersion(1);
                log.trace("[Entity: {}] Initialized version to 1 for new entity", entityType);
            }
        }

        SqlSession sqlSession = getSqlSession();
        try {
            int inserted = sqlSession.insert(sqlStatement(SqlMethod.INSERT_ONE.getMethod(), entity.getClass()), entity);
            boolean success = SqlHelper.retBool(inserted);

            if (success) {
                log.debug("[Entity: {}] Insert successful. ID: {}", entityType, entity.getId());
            } else {
                log.error("[Entity: {}] Insert failed", entityType);
            }
            return success;
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    @Override
    protected <A extends ID, B extends ID> boolean insert(A entity, Function<A, B> function) {
        Objects.requireNonNull(entity, "[Insert with conversion] Source entity cannot be null");
        Objects.requireNonNull(function, "[Insert with conversion] Conversion function cannot be null");

        String sourceType = entity.getClass().getSimpleName();
        B targetEntity = function.apply(entity);
        String targetType = targetEntity.getClass().getSimpleName();

        boolean result = insert(targetEntity);

        if (result) {
            entity.setId(targetEntity.getId());
            log.trace("[Source: {}, Target: {}] Synced generated ID to source entity",
                    sourceType, targetType);
        }

        return result;
    }

    private String sqlStatement(String sqlMethod, Class<?> entityClass) {
        return SqlHelper.table(entityClass).getSqlStatement(sqlMethod);
    }

    private void closeSqlSession(SqlSession sqlSession) {
        if (sqlSession != null) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionTemplate.getSqlSessionFactory());
        }
    }

    private SqlSession getSqlSession() {
        return SqlSessionUtils.getSqlSession(sqlSessionTemplate.getSqlSessionFactory());
    }

    /**
     * Update without version
     */
    @Override
    protected <A extends ID> boolean update(A entity, Set<String> changedFields) {
        Objects.requireNonNull(entity, "[Update] Entity cannot be null");
        Objects.requireNonNull(changedFields, "[Update] Changed fields cannot be null");

        String entityType = entity.getClass().getSimpleName();

        if (changedFields.isEmpty()) {
            log.debug("[Entity: {}] No fields to update. Entity ID: {}", entityType, entity.getId());
            return true;
        }

        A conditionEntity = (A) ReflectUtil.newInstance(entity.getClass());
        conditionEntity.setId(entity.getId());

        return update(entity, changedFields, conditionEntity);
    }

    /**
     * Safe update with version (optimistic locking)
     */
    @Override
    protected <A extends Versionable> boolean update(A entity, Set<String> changedFields) {
        Objects.requireNonNull(entity, "[Versioned Update] Entity cannot be null");
        Objects.requireNonNull(changedFields, "[Versioned Update] Changed fields cannot be null");

        String entityType = entity.getClass().getSimpleName();

        if (changedFields.isEmpty()) {
            log.debug("[Entity: {}] No fields to update. Entity ID: {}", entityType, entity.getId());
            return true;
        }

        A conditionEntity = (A) ReflectUtil.newInstance(entity.getClass());
        conditionEntity.setId(entity.getId());
        conditionEntity.setVersion(entity.getVersion());

        boolean result = update(entity, changedFields, conditionEntity);

        // Increment version if update successful
        if (result) {
            entity.setVersion(conditionEntity.getVersion() + 1);
            log.trace("[Entity: {}] Incremented version. New version: {}", entityType, entity.getVersion());
        }

        return result;
    }

    private <A extends ID> boolean update(A entity, Set<String> changedFields, A newEntity) {
        String entityType = entity.getClass().getSimpleName();
        Object entityId = entity.getId();

        // Get primary key
        String primaryKey = getPrimaryKey(newEntity.getClass());
        if (StrUtil.isEmpty(primaryKey)) {
            throw new AggregatePersistenceException(
                    String.format("[Entity: %s] Primary key annotation @TableId not found", entityType)
            );
        }

        // Create update conditions
        UpdateWrapper<A> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq(primaryKey, entityId);

        // Set update fields (convert to underline case)
        changedFields.forEach(field -> {
            Object fieldValue = ReflectUtil.getFieldValue(entity, field);
            String dbFieldName = StrUtil.toUnderlineCase(field);
            updateWrapper.set(dbFieldName, fieldValue);
            log.trace("[Entity: {}] Setting update field: {} = {}", entityType, dbFieldName, fieldValue);
        });

        // Add version condition for versionable entities
        if (newEntity instanceof Versionable) {
            Versionable versionable = (Versionable) newEntity;
            updateWrapper.eq("version", versionable.getVersion());
            log.trace("[Entity: {}] Adding version condition: version = {}", entityType, versionable.getVersion());
        }

        SqlSession sqlSession = getSqlSession();
        try {
            Map<String, Object> params = CollectionUtils.newHashMapWithExpectedSize(2);
            params.put(Constants.ENTITY, newEntity);
            params.put(Constants.WRAPPER, updateWrapper);

            int updated = sqlSession.update(sqlStatement(SqlMethod.UPDATE.getMethod(), entity.getClass()), params);
            boolean success = SqlHelper.retBool(updated);

            if (success) {
                log.debug("[Entity: {}] Update successful. ID: {}, Changed fields: {}",
                        entityType, entityId, changedFields);
            } else {
                log.warn("[Entity: {}] Update failed (record may not exist or has been modified). ID: {}",
                        entityType, entityId);
            }
            return success;
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    private String getPrimaryKey(Class<?> entityClass) {
        return ReflectUtils.getFieldNameByAnnotation(entityClass, TableId.class);
    }

    /**
     * Maximum batch size for batch operations
     *
     * @return Maximum batch size
     */
    protected int batchSize() {
        return 1024;
    }
}
