package com.damon.order.infra.order.mapper;


import com.baomidou.mybatisplus.annotation.*;
import com.damon.object_trace.Versionable;
import lombok.Data;


@Data
@TableName("demo_order")
public class OrderPO implements Versionable<Long> {
    private static final long serialVersionUID = 1L;
    @Version
    private Integer version;
    @TableId
    private Long id;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Long createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
    private String consigneeName;
    private String consigneeShippingAddress;
    private String consigneeMobile;
    private Long totalMoney;
    private Long actualPayMoney;
    private Long couponId;
    private Long deductionPoints;
    private Long orderSubmitUserId;
    private Long deleted;
    private Long sellerId;
}
