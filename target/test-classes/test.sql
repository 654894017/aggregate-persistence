CREATE TABLE `demo_order` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `status` int DEFAULT NULL,
                              `create_time` bigint DEFAULT NULL,
                              `update_time` bigint DEFAULT NULL,
                              `consignee_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
                              `consignee_shipping_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
                              `consignee_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
                              `total_money` bigint DEFAULT NULL,
                              `actual_pay_money` bigint DEFAULT NULL,
                              `coupon_id` bigint DEFAULT NULL,
                              `deduction_points` bigint DEFAULT NULL,
                              `order_submit_user_id` bigint DEFAULT NULL,
                              `is_delete` int DEFAULT NULL,
                              `seller_id` bigint DEFAULT NULL,
                              `version` int DEFAULT NULL,
                              PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;



CREATE TABLE `demo_order_item` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `order_id` bigint DEFAULT NULL,
                                   `goods_id` bigint DEFAULT NULL,
                                   `goods_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
                                   `amount` int DEFAULT NULL,
                                   `price` bigint DEFAULT NULL,
                                   `update_time` bigint DEFAULT NULL,
                                   `is_delete` bigint DEFAULT '0',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO `demo_order` (`id`, `status`, `create_time`, `update_time`, `consignee_name`, `consignee_shipping_address`, `consignee_mobile`, `total_money`, `actual_pay_money`, `coupon_id`, `deduction_points`, `order_submit_user_id`, `is_delete`, `seller_id`, `version`) VALUES (2, 41, 1703658608810, 1750002402505, '111', '1', '18050194863', 1, 1, 1, 1, 1, 0, 1, 71);

INSERT INTO `demo_order_item` (`id`, `order_id`, `goods_id`, `goods_name`, `amount`, `price`, `update_time`, `is_delete`) VALUES (1934257655212171307, 2, 1934276387250753536, '1', 1, 1, 1750002402519, 0);
INSERT INTO `demo_order_item` (`id`, `order_id`, `goods_id`, `goods_name`, `amount`, `price`, `update_time`, `is_delete`) VALUES (1934257655212171309, 2, 1, '1', 1, 1, 1750002402513, 0);
INSERT INTO `demo_order_item` (`id`, `order_id`, `goods_id`, `goods_name`, `amount`, `price`, `update_time`, `is_delete`) VALUES (1934257655212171310, 2, 1, '1', 1, 1, 1750002402514, 0);