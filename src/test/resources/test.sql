CREATE TABLE `demo_order` (
  `version` int(11) DEFAULT NULL,
  `id` bigint(20) NOT NULL,
  `status` int(11) DEFAULT NULL,
  `create_time` bigint(20) DEFAULT NULL,
  `update_time` bigint(20) DEFAULT NULL,
  `consignee_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `consignee_shipping_address` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `consignee_mobile` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL,
  `total_money` bigint(20) DEFAULT NULL,
  `actual_pay_money` bigint(20) DEFAULT NULL,
  `coupon_id` bigint(20) DEFAULT NULL,
  `deduction_points` bigint(20) DEFAULT NULL,
  `order_submit_user_id` bigint(20) DEFAULT NULL,
  `is_delete` int(1) DEFAULT NULL,
  `seller_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;



CREATE TABLE `demo_order_item` (
  `id` bigint(20) NOT NULL,
  `order_id` bigint(20) DEFAULT NULL,
  `goods_id` bigint(20) DEFAULT NULL,
  `goods_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `amount` int(11) DEFAULT NULL,
  `price` bigint(20) DEFAULT NULL,
  `update_time` bigint(20) DEFAULT NULL,
  `is_delete` bigint(20) DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;


INSERT INTO `demo_order`(`version`, `id`, `status`, `create_time`, `update_time`, `consignee_name`, `consignee_shipping_address`, `consignee_mobile`, `total_money`, `actual_pay_money`, `coupon_id`, `deduction_points`, `order_submit_user_id`, `is_delete`, `seller_id`) VALUES (39, 2, 9, 1703658608810, 1746793887426, '111', '1', '18050194863', 1, 1, 1, 1, 1, 0, 1);


INSERT INTO `demo_order_item`(`id`, `order_id`, `goods_id`, `goods_name`, `amount`, `price`, `update_time`, `is_delete`) VALUES (1770333256328372224, 2, 1920818899503788032, '1', 1, 1, 1746793887453, 0);
INSERT INTO `demo_order_item`(`id`, `order_id`, `goods_id`, `goods_name`, `amount`, `price`, `update_time`, `is_delete`) VALUES (1920818899734474753, 2, 1, '1', 1, 1, 1746793887445, 0);

