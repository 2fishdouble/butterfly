CREATE TABLE if not exists `gpu` (
                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                       `name` varchar(128) DEFAULT NULL COMMENT '显卡名称',
                       `cuda_cores` int DEFAULT NULL COMMENT 'CUDA 核心数',
                       `process_node` decimal(5,2) DEFAULT NULL COMMENT '制程工艺(nm)',
                       `vulkan_support` tinyint(1) DEFAULT NULL COMMENT '是否支持 Vulkan:1 支持,0 不支持',
                       `release_date` date DEFAULT NULL COMMENT '发布/上市日期',
                       `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                       `release_time` time DEFAULT NULL COMMENT '每日发布时间',
                       `week_type` varchar(16) DEFAULT NULL COMMENT '周类型(枚举名):MONDAY/TUESDAY/WEDNESDAY/THURSDAY/FRIDAY/SATURDAY/SUNDAY',
                       `week_types` varchar(255) DEFAULT NULL,
                       PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='GPU 型号(canal 沙箱示例表)';