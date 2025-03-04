package com.damon.test.domain.order;


import com.damon.aggregate.persistence.Versionable;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class Order implements Versionable<Long> {

    private Long id;
    private Integer status;
    private Consignee consignee;
    private List<OrderItem> orderItems;
    private Long totalMoney;
    private Long actualPayMoney;
    private Integer version;
    private Long couponId;
    private Long deductionPoints;
    private Long orderSubmitUserId;
    private Long deleted;
    private Long sellerId;
}
