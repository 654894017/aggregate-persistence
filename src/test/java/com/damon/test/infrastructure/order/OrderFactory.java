package com.damon.test.infrastructure.order;


import com.damon.aggregate.persistence.Aggregate;
import com.damon.aggregate.persistence.AggregateFactory;
import com.damon.test.domain.order.Consignee;
import com.damon.test.domain.order.Order;
import com.damon.test.domain.order.OrderItem;
import com.damon.test.infrastructure.order.mapper.OrderItemPO;
import com.damon.test.infrastructure.order.mapper.OrderPO;
import lombok.NonNull;

import java.util.List;
import java.util.stream.Collectors;

public class OrderFactory {

    public static OrderPO convert(@NonNull Order order) {
        OrderPO orderPO = new OrderPO();
        orderPO.setId(order.getId());
        orderPO.setConsigneeMobile(order.getConsignee().getMobile());
        orderPO.setConsigneeName(order.getConsignee().getName());
        orderPO.setConsigneeShippingAddress(order.getConsignee().getShippingAddress());
        orderPO.setDeductionPoints(order.getDeductionPoints());
        orderPO.setSellerId(order.getSellerId());
        orderPO.setTotalMoney(order.getTotalMoney());
        orderPO.setOrderSubmitUserId(order.getOrderSubmitUserId());
        orderPO.setStatus(order.getStatus());
        orderPO.setVersion(order.getVersion());
        orderPO.setCouponId(order.getCouponId());
        return orderPO;
    }

    public static OrderItemPO convert(@NonNull OrderItem item) {
        OrderItemPO itemPO = new OrderItemPO();
        itemPO.setId(item.getId());
        itemPO.setGoodsName(item.getGoodsName());
        itemPO.setOrderId(item.getOrderId());
        itemPO.setGoodsId(item.getGoodsId());
        itemPO.setAmount(item.getAmount());
        itemPO.setPrice(item.getPrice());
        return itemPO;
    }

    public static Aggregate<Order> convert(@NonNull OrderPO orderPO, @NonNull List<OrderItemPO> orderItemPOS) {
        Order order = new Order();
        List<OrderItem> orderItems = orderItemPOS.stream().map(item ->
                new OrderItem(item.getId(), item.getOrderId(), item.getGoodsId(), item.getGoodsName(), item.getAmount(), item.getPrice())
        ).collect(Collectors.toList());
        order.setVersion(orderPO.getVersion());
        order.setDeleted(orderPO.getIsDelete());
        order.setConsignee(new Consignee(orderPO.getConsigneeName(), orderPO.getConsigneeShippingAddress(), orderPO.getConsigneeMobile()));
        order.setId(orderPO.getId());
        order.setConsignee(new Consignee(orderPO.getConsigneeName(), orderPO.getConsigneeShippingAddress(), orderPO.getConsigneeMobile()));
        order.setVersion(orderPO.getVersion());
        order.setStatus(orderPO.getStatus());
        order.setDeductionPoints(orderPO.getDeductionPoints());
        order.setCouponId(orderPO.getCouponId());
        order.setActualPayMoney(orderPO.getActualPayMoney());
        order.setTotalMoney(orderPO.getTotalMoney());
        order.setOrderSubmitUserId(orderPO.getOrderSubmitUserId());
        order.setOrderItems(orderItems);
        order.setSellerId(orderPO.getSellerId());
        return AggregateFactory.createAggregate(order);
    }
}
