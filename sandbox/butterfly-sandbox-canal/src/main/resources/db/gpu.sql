CREATE TABLE IF NOT EXISTS `gpu`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(128)  DEFAULT NULL COMMENT '显卡名称',
    `cuda_cores`     INT           DEFAULT NULL COMMENT 'CUDA 核心数',
    `process_node`   DECIMAL(5, 2) DEFAULT NULL COMMENT '制程工艺(nm)',
    `vulkan_support` TINYINT(1)    DEFAULT NULL COMMENT '是否支持 Vulkan:1 支持,0 不支持',
    `release_date`   DATE          DEFAULT NULL COMMENT '发布/上市日期',
    `create_time`    DATETIME      DEFAULT NULL COMMENT '创建时间',
    `release_time`   TIME          DEFAULT NULL COMMENT '每日发布时间',
    `week_type`      VARCHAR(16)   DEFAULT NULL COMMENT '周类型(枚举名):MONDAY/TUESDAY/WEDNESDAY/THURSDAY/FRIDAY/SATURDAY/SUNDAY',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'GPU 型号(canal 沙箱示例表)';
