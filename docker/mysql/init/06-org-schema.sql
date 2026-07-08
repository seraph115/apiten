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
