-- apiten 产品域建表（apiten_product 运行库）
CREATE DATABASE IF NOT EXISTS `apiten_product` DEFAULT CHARACTER SET utf8mb4;
USE `apiten_product`;

CREATE TABLE `product_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码 P+序号',
  `name` varchar(128) NOT NULL COMMENT '产品名称',
  `product_type` tinyint NOT NULL DEFAULT 1 COMMENT '产品类型：1企业 2个人 3核验 4司法 5经营风险 6知识产权 7报告 8组合',
  `auth_type` tinyint NOT NULL DEFAULT 0 COMMENT '认证类型',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `version` varchar(16) NOT NULL DEFAULT 'v1' COMMENT '版本',
  `description` varchar(512) NULL DEFAULT '' COMMENT '说明',
  `cache_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否缓存',
  `async_support` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否支持异步',
  `need_auth_no` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要授权书编号',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_product_code` (`product_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品信息表';

CREATE TABLE `product_function` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `func_code` varchar(32) NOT NULL COMMENT '功能编码 F+序号',
  `name` varchar(128) NOT NULL COMMENT '功能名称',
  `product_id` bigint NOT NULL COMMENT '所属产品ID',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必选',
  `charge` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否计费',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_func_code` (`func_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品功能表';

CREATE TABLE `product_func_interface` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `product_function_id` bigint NOT NULL COMMENT '产品功能ID',
  `ds_interface_id` bigint NOT NULL COMMENT '数据源接口ID（松耦合引用 adapter）',
  `ds_interface_code` varchar(32) NULL DEFAULT '' COMMENT '数据源接口编码快照',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级（越小越优先）',
  `is_default` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认数据源',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_func_id` (`product_function_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品功能-数据源接口绑定表';

CREATE TABLE `product_param` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `product_id` bigint NOT NULL COMMENT '所属产品ID',
  `param_direction` tinyint NOT NULL COMMENT '方向：1入参 2出参',
  `field_name` varchar(128) NOT NULL COMMENT '字段名',
  `data_type` tinyint NOT NULL DEFAULT 1 COMMENT '数据类型：1字符串 2数字 3布尔 4日期 5对象 6数组',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必填',
  `validation_rule` varchar(256) NULL DEFAULT '' COMMENT '入参校验规则',
  `desensitize_rule` varchar(128) NULL DEFAULT '' COMMENT '出参脱敏规则',
  `description` varchar(256) NULL DEFAULT '' COMMENT '说明',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_product_direction` (`product_id`, `param_direction`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品参数定义表';
