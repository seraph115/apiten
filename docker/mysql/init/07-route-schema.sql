-- apiten 路由域建表（apiten_route 运行库）
CREATE DATABASE IF NOT EXISTS `apiten_route` DEFAULT CHARACTER SET utf8mb4;
USE `apiten_route`;

CREATE TABLE `route_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `route_code` varchar(32) NOT NULL COMMENT '路由编码 R+序号',
  `name` varchar(128) NOT NULL COMMENT '路由名称',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码(松耦合快照)',
  `org_id` bigint NULL DEFAULT NULL COMMENT '机构ID(空=产品默认级,非空=机构产品级)',
  `target_type` varchar(16) NOT NULL DEFAULT 'SINGLE' COMMENT '目标类型：SINGLE(本期)/SPLIT/CHAIN/DYNAMIC(预留)',
  `target_ds_interface_id` bigint NULL DEFAULT NULL COMMENT 'SINGLE 目标数据源接口ID(松耦合)',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级(越小越优先)',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_product_code` (`product_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路由配置表';
