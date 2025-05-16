# aggregate-persistence

可参考：

* [DDD之聚合持久化应该怎么做？](https://zhuanlan.zhihu.com/p/334344752)
* [聊一聊聚合的持久化](https://zhuanlan.zhihu.com/p/87074950)

## 1. 简介

# aggregate-persistence

## 1. 简介

领域驱动设计(DDD)已经被业界认为是行之有效的复杂问题解决之道。随着微服务的流行，DDD也被更多的团队采纳。然而在DDD落地时，聚合(
Aggregate)的持久化一直缺少一种优雅的方式解决。

在DDD实践中，聚合应该作为一个完整的单元进行读取和持久化，以确保业务的不变性或者说业务规则不变破坏。例如，订单总金额应该与订单明细金额之和一致。

由于领域模型和数据库的数据模型可能不一致，并且聚合可能涉及多个实体，因此Hibernate, MyBatis和Spring
Data等框架直接用于聚合持久化时，总是面临一些困难，而且代码也不够优雅。有人认为NoSQL是最适合聚合持久化的方案。确实如此，每个聚合实例就是一个文档，NoSQL天然为聚合持久化提供了很好的支持。然而并不是所有系统都适合用NoSQL。当遇到关系型数据库时，一种方式是将领域事件引入持久化过程。也就是在处理业务过程中，聚合抛出领域事件，Repository根据领域事件的不同，执行不同的SQL，完成数据库的修改。但这样的话，Repository层就要引入一些逻辑判断，代码冗余增加了维护成本。

本项目旨在提供一种轻量级聚合持久化方案，帮助开发者真正从业务出发设计领域模型，不需要考虑持久化的事情。在实现Repository持久化时，不需要考虑业务逻辑，只负责聚合的持久化，从而真正做到关注点分离。
**也就是说，不论有多少个业务场景对聚合进行了修改，对聚合的持久化只需要一个方法。**

方案的核心是`Aggregate<T>`容器，T是聚合根的类型。Repository以`Aggregate<T>`为核心，当Repository查询或保存聚合时，返回的不是聚合本身，而是聚合容器
`Aggregate<T>`。以订单创建为例，OrderGateway的代码如下：

```java

@Repository
public class OrderGateway extends MybatisRepositorySupport implements IOrderGateway {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public OrderGateway(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public Aggregate<Order> get(OrderId orderId) {
        OrderPO orderPO = orderMapper.selectById(orderId.getId());
        if (orderPO == null) {
            throw new EntityNotFoundException(String.format("Order (%s) is not found", orderId.getId()));
        }
        List<OrderItemPO> orderItemPOList = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemPO>().eq(OrderItemPO::getOrderId, orderId.getId()));
        return OrderFactory.convert(orderPO, orderItemPOList);
    }

    private Long create(Aggregate<Order> orderAggregate) {
        Order order = orderAggregate.getRoot();
        super.insert(order, OrderFactory::convert);
        order.getOrderItems().forEach(orderItem -> {
            orderItem.setOrderId(order.getId());
            super.insert(orderItem, OrderFactory::convert);
        });
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(Aggregate<Order> aggregate) {
        if (aggregate.isNew()) {
            return create(aggregate);
        }
        if (!aggregate.isChanged()) {
            return aggregate.getRoot().getId();
        }
        return update(aggregate);
    }

    private Long update(Aggregate<Order> orderAggregate) {
        Order order = orderAggregate.getRoot();
        Order snapshot = orderAggregate.getSnapshot();
        Boolean result = super.executeSafeUpdate(order, snapshot, OrderFactory::convert);
        Boolean result2 = super.executeListUpdate(order.getOrderItems(), snapshot.getOrderItems(), item -> {
            item.setOrderId(order.getId());
            return OrderFactory.convert(item);
        });
        if (!result2 && !result) {
            throw new OptimisticLockException(String.format("Update order (%s) error, it's not found or changed by another user", orderAggregate.getRoot().getId()));
        }
        return order.getId();
    }
}
```

`Aggregate<T>`保留了聚合的历史快照，因此在Repository保存聚合时，就可以与快照进行对比，找到需要修改的实体和字段，然后完成持久化工作。它提供以下功能：

* `public R getRoot()`：获取聚合根
* `public R getRootSnapshot()`: 获取聚合根的历史快照
* `public boolean isChanged()`: 聚合是否发生了变化
* `public boolean isNew()`：是否为新的聚合

`ObjectComparator`用于比较对象之间的差异，用户处理新增、修改、删除的实体，包括实体修改了，可以获取变动的属性。它提供以下功能：

* `public <T> Collection<T> findNewEntitiesById(Function<R, Collection<T>> getCollection, Function<T, ID> getId)`
  ：在实体集合（例如订单的所有订单明细行中）找到新的实体
* `public <T, ID> Collection<T> findChangedEntities(Function<R, Collection<T>> getCollection, Function<T, ID> getId)`
  ：在实体集合（例如所有订单明细行中）找到发生变更的实体
* `public <T, ID> Collection<T> findRemovedEntities(Function<R, Collection<T>> getCollection, Function<T, ID> getId)`
  ：在实体集合（例如所有订单明细行中）找到已经删除的实体

工具类`ObjectComparator`
提供了对象的对比功能。它可以帮助你修改数据库时只update那些变化了的字段。以Person为例，
`ObjectComparator.getChangedFields(personSnapshot, personCurrent)`
将返回哪些Field发生了变化。你可以据此按需修改数据库（请参考示例工程）。

与Hibernate的`@Version`类似，聚合根需要实现Versionable接口，以便Repository基于Version实现乐观锁。Repository对聚合的所有持久化操作，都要判断Version。示意SQL如下：

```sql
insert into person (id, name, age, address, version )
values (#{id}, #{name}, #{age}, #{address}, 1)

update person set age = #{age}, address = #{address}, version = version + 1
where id = #{id} and version = #{version}

delete person
where id = #{id} and version = #{version}
``` 

## 2. 使用aggregate-persistence

在项目中加入以下依赖，就可以使用aggregate-persistence的功能了

```xml

<dependency>
    <groupId>com.damon</groupId>
    <artifactId>aggregate-persistence</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 3. 使用示例

aggregate-persistence 本身并不负责持久化工作，它是一个工具，用于识别聚合的变更，例如发现有新增、修改和删除的实体，真正的持久化工作由你的Repository实现。
完整的示例代码见[订单聚合持久化项目](https://github.com/654894017/aggregate-persistence/blob/master/src/test/java/com/damon/test/TestOrderGateway.java)
，该示例演示了如何运用Mybatis实现聚合的持久化，并且只持久化那些修改的数据。
例如一个表有20个字段，只有1个字段修改了，采用此方案时，只会修改数据库的一个字段，而非所有字段。

## 4. 总结

总的来说，本项目提供了一种轻量级聚合持久化方案，能够帮助开发者设计干净的领域模型的同时，很好地支持Repository做持久化工作。通过持有聚合根的快照，
`Aggregate<T>`
可以识别聚合发生了哪些变化，然后Repository使用基于Version的乐观锁和ObjectComparator在字段属性级别的比较功能，实现按需更新数据库。
