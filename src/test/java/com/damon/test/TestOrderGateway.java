package com.damon.test;

import cn.hutool.core.util.IdUtil;
import com.damon.aggregate.persistence.Aggregate;
import com.damon.aggregate.persistence.AggregateFactory;
import com.damon.test.domain.order.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = OrderApplication.class)
public class TestOrderGateway {
    @Autowired
    private IOrderGateway orderGateway;

    @Test
    public void saveTest() {
        Aggregate<Order> orderAggregate = orderGateway.get(new OrderId(2L));
        Order order = orderAggregate.getRoot();
        order.setStatus(order.getStatus() + 1);
        List<OrderItem> item = order.getOrderItems();
        item.add(new OrderItem(order.getId(), 1l, "1", 1, 1l));
        item.add(new OrderItem(order.getId(), 1l, "1", 1, 1l));

        item.get(1).setGoodsId(IdUtil.getSnowflakeNextId());
        item.remove(0);
        item.remove(1);
        orderGateway.save(orderAggregate);
        System.out.println(1);
    }

    @Test
    public void createTest() {
        Order order = new Order();
        order.setVersion(0);
        order.setConsignee(new Consignee("1", "1", "18050194863"));
        order.setCouponId(1L);
        order.setActualPayMoney(100L);
        order.setDeductionPoints(100L);
        order.setOrderSubmitUserId(181987L);
        order.setSellerId(11L);
        order.setTotalMoney(100L);
        order.setStatus(1);
        List<OrderItem> item = new ArrayList<>();
        item.add(new OrderItem(1l, "1", 1, 1l));
        item.add(new OrderItem(2l, "1", 1, 1l));
        order.setOrderItems(item);
        Aggregate<Order> orderAggregate = AggregateFactory.createAggregate(order);
        orderGateway.save(orderAggregate);
    }

}
