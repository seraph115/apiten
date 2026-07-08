-- apiten adapter 数据源域建表（从 P1 追加内容提取，供 apiten_adapter 运行库）
USE `apiten_adapter`;

CREATE TABLE `adapter_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ds_code` varchar(32) NOT NULL COMMENT '数据源编码 DS+序号',
  `name` varchar(128) NOT NULL COMMENT '数据源名称',
  `supplier_name` varchar(128) NULL DEFAULT '' COMMENT '供应商名称',
  `source_type` tinyint NOT NULL DEFAULT 1 COMMENT '类型：1供应商 2内部',
  `contact_person` varchar(64) NULL DEFAULT '' COMMENT '联系人',
  `contact_phone` varchar(32) NULL DEFAULT '' COMMENT '联系方式',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `env_type` tinyint NOT NULL DEFAULT 1 COMMENT '环境：1生产 2测试',
  `service_addr` varchar(512) NULL DEFAULT '' COMMENT '服务地址',
  `auth_type` tinyint NOT NULL DEFAULT 0 COMMENT '认证方式',
  `timeout_ms` int NOT NULL DEFAULT 3000 COMMENT '超时毫秒',
  `max_concurrency` int NOT NULL DEFAULT 50 COMMENT '最大并发',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `routable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否参与路由',
  `protocol_type` tinyint NOT NULL DEFAULT 1 COMMENT '接入协议：1HTTP 2RPC 3DB 4FILE',
  `protocol_ext_config` varchar(2048) NULL DEFAULT NULL COMMENT '协议扩展参数JSON',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_ds_code` (`ds_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源信息表';

-- ----------------------------
-- Table structure for adapter_ds_interface
-- ----------------------------
CREATE TABLE `adapter_ds_interface` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `if_code` varchar(32) NOT NULL COMMENT '接口编码 IF+序号',
  `name` varchar(128) NOT NULL COMMENT '接口名称',
  `data_source_id` bigint NOT NULL COMMENT '所属数据源ID',
  `uri` varchar(512) NULL DEFAULT '' COMMENT '接口URI',
  `method` varchar(8) NOT NULL DEFAULT 'POST' COMMENT '请求方式',
  `msg_format` tinyint NOT NULL DEFAULT 1 COMMENT '报文格式：1JSON 2XML 3FORM',
  `sign_type` tinyint NOT NULL DEFAULT 0 COMMENT '签名方式',
  `encrypt_type` tinyint NOT NULL DEFAULT 0 COMMENT '加密方式',
  `auth_params` varchar(2048) NULL DEFAULT NULL COMMENT '认证参数JSON',
  `version` varchar(16) NOT NULL DEFAULT 'v1' COMMENT '接口版本',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `timeout_ms` int NOT NULL DEFAULT 3000 COMMENT '超时毫秒',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `cache_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否缓存',
  `cache_ttl` int NOT NULL DEFAULT 0 COMMENT '缓存TTL秒',
  `cache_key` varchar(256) NULL DEFAULT '' COMMENT '缓存键模板',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_if_code` (`if_code`),
  KEY `idx_data_source_id` (`data_source_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源接口表';

-- ----------------------------
-- Table structure for adapter_ds_response_code
-- ----------------------------
CREATE TABLE `adapter_ds_response_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `data_source_id` bigint NOT NULL COMMENT '所属数据源ID',
  `ds_interface_id` bigint NOT NULL DEFAULT 0 COMMENT '所属接口ID，0为数据源级通用',
  `raw_code` varchar(64) NOT NULL COMMENT '原始应答码',
  `raw_desc` varchar(256) NULL DEFAULT '' COMMENT '应答描述',
  `success` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否成功',
  `charge` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否计费',
  `retryable` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否可重试',
  `trigger_switch` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否触发切换',
  `platform_code` varchar(8) NULL DEFAULT '' COMMENT '映射平台统一码',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ds_if` (`data_source_id`, `ds_interface_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源应答码映射表';

-- ----------------------------
-- Table structure for adapter_ds_interface_param
-- ----------------------------
CREATE TABLE `adapter_ds_interface_param` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ds_interface_id` bigint NOT NULL COMMENT '所属接口ID',
  `param_direction` tinyint NOT NULL COMMENT '方向：1入参 2出参',
  `platform_field` varchar(128) NOT NULL COMMENT '平台标准字段',
  `provider_field` varchar(128) NULL DEFAULT '' COMMENT '供应商字段',
  `data_type` tinyint NOT NULL DEFAULT 1 COMMENT '数据类型：1字符串 2数字 3布尔 4日期 5对象 6数组',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必填',
  `transform_fn` varchar(64) NULL DEFAULT '' COMMENT '转换函数名',
  `default_value` varchar(256) NULL DEFAULT '' COMMENT '默认值',
  `json_path` varchar(256) NULL DEFAULT '' COMMENT '出参JSONPath抽取路径',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_if_direction` (`ds_interface_id`, `param_direction`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源接口参数映射表';
