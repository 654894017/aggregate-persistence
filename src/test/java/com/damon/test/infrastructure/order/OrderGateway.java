package com.damon.test.infrastructure.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damon.aggregate.persistence.Aggregate;
import com.damon.aggregate.persistence.exception.EntityNotFoundException;
import com.damon.aggregate.persistence.exception.OptimisticLockException;
import com.damon.aggregate.persistence.mybatis.MybatisRepositorySupport;
import com.damon.test.domain.order.IOrderGateway;
import com.damon.test.domain.order.Order;
import com.damon.test.domain.order.OrderId;
import com.damon.test.infrastructure.order.mapper.OrderItemMapper;
import com.damon.test.infrastructure.order.mapper.OrderItemPO;
import com.damon.test.infrastructure.order.mapper.OrderMapper;
import com.damon.test.infrastructure.order.mapper.OrderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderGateway extends MybatisRepositorySupport implements IOrderGateway {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

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
        boolean orderResult = super.executeSafeUpdate(order, snapshot, OrderFactory::convert);
        boolean orderItemResult = super.executeListUpdate(order.getOrderItems(), snapshot.getOrderItems(), item -> {
            item.setOrderId(order.getId());
            return OrderFactory.convert(item);
        });
        if (!orderResult && !orderItemResult) {
            String errorMessage = String.format("Update order (%s) error, it's not found or changed by another user", orderAggregate.getRoot().getId());
            throw new OptimisticLockException(errorMessage);
        }
        return order.getId();
    }

}
