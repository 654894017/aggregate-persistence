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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MybatisRepositorySupport extends DbRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(MybatisRepositorySupport.class);


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

        if (CollectionUtils.isEmpty(items)) {
            return true;
        }

        if (items.size() > batchSize()) {
            throw new AggregatePersistenceException(
                    "插入数量超过批量限制. 最大批量: " + batchSize() + ", 实际数量: " + items.size()
            );
        }

        Class<A> clazz = (Class<A>) items.iterator().next().getClass();
        BaseMapper<A> baseMapper = getMapper(clazz);
        baseMapper.insert(items, batchSize());
        return true;
    }

    @Override
    protected <A extends ID> boolean deleteBatch(Collection<A> items) {
        if (CollectionUtils.isEmpty(items)) {
            return true;
        }
        Class<A> entityClass = (Class<A>) items.iterator().next().getClass();
        SqlSession sqlSession = getSqlSession();
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(1);
        Set<Object> ids = items.stream().filter(Objects::nonNull).map(ID::getId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            log.warn("批量删除失败，所有实体ID均为null，实体类型: {}", entityClass.getSimpleName());
            return false;
        }

        map.put(Constants.COLL, ids);
        try {
            return SqlHelper.retBool(sqlSession.delete(sqlStatement(SqlMethod.DELETE_BY_IDS.getMethod(), entityClass), map));
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
        boolean result = update(entity, changedFields, newEntity);
        entity.setVersion(newEntity.getVersion());
        return result;
    }

    private <A extends ID> boolean update(A entity, Set<String> changedFields, A newEntity) {
        SqlSession sqlSession = getSqlSession();

        // 创建 UpdateWrapper 对象以指定更新条件
        String primaryKey = getPrimaryKey(newEntity.getClass());
        if (StrUtil.isEmpty(primaryKey)) {
            throw new AggregatePersistenceException(
                    "实体未找到主键注解 @TableId，实体类: " + entity.getClass().getName()
            );
        }
        UpdateWrapper<A> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq(primaryKey, entity.getId());
        // 设置更新字段（转换为下划线命名）
        changedFields.forEach(field -> {
            Object fieldValue = ReflectUtil.getFieldValue(entity, field);
            String fieldName = StrUtil.toUnderlineCase(field);
            updateWrapper.set(fieldName, fieldValue);
            log.debug("设置更新字段: {}={}", fieldName, fieldValue);
        });

        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(2);
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