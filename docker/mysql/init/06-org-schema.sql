-- apiten 机构域建表（apiten_org 运行库）
CREATE DATABASE IF NOT EXISTS `apiten_org` DEFAULT CHARACTER SET utf8mb4;
USE `apiten_org`;

CREATE TABLE `org_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_code` varchar(32) NOT NULL COMMENT '机构编码 ORG+序号',
  `name` varchar(128) NOT NULL COMMENT '机构名称',
  `unified_social_credit_code` varchar(32) NULL DEFAULT '' COMMENT '统一社会信用代码',
  `contact_person` varchar(64) NULL DEFAULT '' COMMENT '联系人',
  `contact_phone` varchar(32) NULL DEFAULT '' COMMENT '联系方式',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `business_owner` varchar(64) NULL DEFAULT '' COMMENT '业务归属',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_org_code` (`org_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构信息表';

CREATE TABLE `org_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '所属机构ID',
  `account_name` varchar(128) NULL DEFAULT '' COMMENT '账号名称',
  `app_key` varchar(64) NOT NULL COMMENT '访问密钥 AK',
  `secret_key_cipher` varchar(512) NOT NULL COMMENT 'SK 密文(AES)',
  `key_status` tinyint NOT NULL DEFAULT 0 COMMENT '密钥状态：0有效 1作废',
  `ip_whitelist` varchar(512) NULL DEFAULT '' COMMENT 'IP白名单(逗号分隔,支持CIDR段)',
  `callback_url` varchar(256) NULL DEFAULT '' COMMENT '回调地址',
  `sign_algorithm` varchar(32) NOT NULL DEFAULT 'HMAC-SHA256' COMMENT '签名算法',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '账号有效期(空为长期)',
  `concurrency_limit` int NULL DEFAULT NULL COMMENT '账号级最大并发',
  `daily_limit` int NULL DEFAULT NULL COMMENT '账号级日调用量上限',
  `monthly_limit` int NULL DEFAULT NULL COMMENT '账号级月调用量上限',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_app_key` (`app_key`),
  KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构账号表';
