package com.damon.test.infrastructure.order.mapper;

import com.baomidou.mybatisplus.annotation.*;
import com.damon.aggregate.persistence.ID;
import lombok.Data;

@Data
@TableName("demo_order_item")
public class OrderItemPO implements ID<Long> {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long goodsId;
    private String goodsName;
    private Integer amount;
    private Long price;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}
