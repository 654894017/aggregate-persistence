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
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MybatisRepositorySupport extends DbRepositorySupport {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @SuppressWarnings("unchecked")
    protected <T extends ID> BaseMapper<T> getMapper(Class<T> entityClass) {
        // 获取当前SqlSession（自动处理多数据源）
        SqlSession sqlSession = getSqlSession();
        return SqlHelper.getMapper(entityClass, sqlSession);
    }

    @Override
    protected <A extends ID> boolean insertBatch(Collection<A> items) {
        if (items.size() > batchSize()) {
            throw new AggregatePersistenceException("The number of items to be inserted cannot exceed the batch size. " +
                    "batchSize : " + batchSize());
        }
        Class<A> clazz = (Class<A>) items.iterator().next().getClass();
        BaseMapper<A> baseMapper = getMapper(clazz);
        baseMapper.insert(items, batchSize());
        return true;
    }

    @Override
    protected <A extends ID> boolean deleteBatch(Collection<A> items) {
        Class<A> clazz = (Class<A>) items.iterator().next().getClass();
        SqlSession sqlSession = getSqlSession();
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(1);
        Set<Object> ids = items.stream().map(ID::getId).collect(Collectors.toSet());
        map.put(Constants.COLL, ids);
        try {
            return SqlHelper.retBool(sqlSession.delete(sqlStatement(SqlMethod.DELETE_BY_IDS.getMethod(), clazz), map));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    @Override
    protected <A extends ID> boolean insert(A entity) {
        if (entity instanceof Versionable) {
            Versionable versionable = (Versionable) entity;
            versionable.setVersion(1);
        }
        SqlSession sqlSession = getSqlSession();
        try {
            return SqlHelper.retBool(sqlSession.insert(sqlStatement(SqlMethod.INSERT_ONE.getMethod(), entity.getClass()), entity));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    @Override
    protected <A extends ID, B extends ID> boolean insert(A entity, Function<A, B> function) {
        B newEntity = function.apply(entity);
        boolean result = insert(newEntity);
        entity.setId(newEntity.getId());
        return result;
    }

    private String sqlStatement(String sqlMethod, Class<?> entityClass) {
        return SqlHelper.table(entityClass).getSqlStatement(sqlMethod);
    }

    private void closeSqlSession(SqlSession sqlSession) {
        SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionTemplate.getSqlSessionFactory());
    }

    private SqlSession getSqlSession() {
        return SqlSessionUtils.getSqlSession(sqlSessionTemplate.getSqlSessionFactory());
    }

    /**
     * 未带版本号的更新
     *
     * @param entity
     * @param changedFields
     * @param <A>
     * @return
     */
    @Override
    protected <A extends ID> boolean update(A entity, Set<String> changedFields) {
        A newEntity = (A) ReflectUtil.newInstance(entity.getClass());
        newEntity.setId(entity.getId());
        return update(entity, changedFields, newEntity);
    }

    /**
     * 带版本version的安全更新
     *
     * @param entity
     * @param changedFields
     * @param <A>
     * @return
     */
    @Override
    protected <A extends Versionable> boolean update(A entity, Set<String> changedFields) {
        A newEntity = (A) ReflectUtil.newInstance(entity.getClass());
        newEntity.setId(entity.getId());
        newEntity.setVersion(entity.getVersion());
        return update(entity, changedFields, newEntity);
    }

    private <A extends ID> boolean update(A entity, Set<String> changedFields, A newEntity) {
        SqlSession sqlSession = getSqlSession();
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(2);
        // 创建 UpdateWrapper 对象以指定更新条件
        String primaryKey = getPrimaryKey(newEntity.getClass());
        if (primaryKey == null) {
            throw new AggregatePersistenceException("Entity not found with the primary key annotation. entity : " + entity.getClass().getName());
        }
        UpdateWrapper<A> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq(primaryKey, entity.getId());
        changedFields.forEach(field ->
                updateWrapper.set(StrUtil.toUnderlineCase(field), ReflectUtil.getFieldValue(entity, field))
        );
        map.put(Constants.ENTITY, newEntity);
        map.put(Constants.WRAPPER, updateWrapper);
        try {
            return SqlHelper.retBool(sqlSession.update(sqlStatement(SqlMethod.UPDATE.getMethod(), entity.getClass()), map));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    private String getPrimaryKey(Class<?> entityClass) {
        return ReflectUtils.getFieldNameByAnnotation(entityClass, TableId.class);
    }

    /**
     * 批量保存最大大小
     *
     * @return
     */
    protected int batchSize() {
        return 1024;
    }


}