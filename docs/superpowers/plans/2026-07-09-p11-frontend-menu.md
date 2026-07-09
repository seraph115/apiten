# 征信 API 平台 P11：前端管理页面 + 菜单权限 + 基础监控看板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（前端为主，逐页/逐任务）；UI 实现可配合 frontend-design 技能. Steps use checkbox (`- [ ]`).

**Goal:** 补齐一期管理面前端：各业务模块 CRUD 页面（数据源/产品/机构/账户/路由/计费/流水/报表）、`sys_menu`/`sys_perm` 菜单与按钮权限落库（对齐 P1–P10 全程用的 `@ss.hasPermission('...')` 权限串）、重交互页面（联调测试台/路由模拟器/流水全链路视图/机构开通向导）、基础监控看板与告警（§6.10 一期）。

**Architecture:** 复用 `yudao-ui-admin-vue3`（Vue3 + TS + Vite + Element Plus + Pinia）：RBAC 动态菜单/按钮权限、字典组件、CRUD 脚手架（配合 yudao 代码生成器生成基础页面）。高度动态配置（适配映射/阶梯区间/路由条件）用 **JSON Schema 驱动动态表单引擎**（§12.4）。ECharts 看板。菜单/权限经 `sys_menu`/`sys_role_menu` 落库，前端动态渲染。

**决策记录:**
- **CRUD 页面**：优先用 yudao 代码生成器基于各 DO 生成 列表/表单/API 再微调——不手写重复 CRUD。
- **权限串来源**：扫描 P1–P10 各 Controller `@ss.hasPermission('X')`，据此生成 `sys_menu`（目录/菜单/按钮）+ 权限标识 SQL 种子，导入 `apiten_system`。
- **重交互页面**（手写）：联调测试台（调 `/adapter/ds-interface/{id}/test`）、路由模拟器（route resolve dry-run）、流水全链路视图（`/flow/trace/{flowNo}`）、机构开通向导 7 步（§6.4.3）。
- **基础监控看板**：调用量/成功率/失败率/P95（ECharts）、数据源可用率/超时率/错误码分布；告警对接邮件（mail_flow）——一期基础，SkyWalking/Prometheus 深度接入留后续。

**Tech Stack:** Vue 3 + TS + Vite + Element Plus + Pinia + ECharts；yudao 代码生成器；JSON Schema 动态表单；yudao RBAC。

**Spec:** v1.3 §6.1–§6.10、§8.3、§12.4。

## Global Constraints
- 前端在 `yudao-ui`（`yudao-ui-admin-vue3`）按 yudao 约定（`src/views/apiten/<domain>/`、`src/api/apiten/<domain>/`）。
- 菜单/权限 SQL 落 `docker/mysql/init/12-apiten-menu.sql`（导入 `apiten_system` 的 `sys_menu`/`sys_role_menu`）。
- 每模块页面组一 commit；前端「验证」= `npm run dev` 起前端 + 后端联调（无 H2 单测，用 `/browse` 或 `/qa` 做页面 QA）。
- 权限串与后端 Controller 完全一致（逐一核对）。

## Tasks（骨架——前端为主）

### Task 1: sys_menu/权限种子 SQL（对齐 P1–P10 权限串）
- 扫描 `@ss.hasPermission`：`datasource:*`、`product:{info,function,func-interface,param}:*`、`org:{info,account,product}:*`、`route:config:*`、`billing:{template,tier,charge-code}:*`、`flow:trace:*`、`report:*` 等。
- 生成 `12-apiten-menu.sql`：目录→模块菜单→按钮权限；`sys_role_menu` 赋超管。
- 验证：导入后管理员登录见菜单树、按钮权限生效。Commit `feat(fe): sys_menu 菜单与按钮权限种子(对齐P1-P10)`。

### Task 2: 数据源 + 产品 页面（代码生成 + 微调）
- 生成 data_source/ds_interface/ds_response_code/ds_interface_param、product/product_function/product_func_interface/product_param 列表/表单/API；JSON Schema 动态表单（入/出参映射、应答码映射）。
- Commit `feat(fe): 数据源/产品 管理页面`。

### Task 3: 机构 + 账户 页面 + 开通向导
- org/org_account（AK/SK 一次显示/重置）/org_product；billing_account/充值/财务流水/账务调整；**开通向导 7 步**（§6.4.3）。
- Commit `feat(fe): 机构/账户 管理页面 + 开通向导`。

### Task 4: 路由 + 计费 页面
- route_config 三级视图 + **路由模拟器**；billing_template/template_tier（阶梯动态表单）/charge_code_rule。
- Commit `feat(fe): 路由(含模拟器)/计费 管理页面`。

### Task 5: 流水 + 报表 页面
- org_flow/ds_flow 查询 + **全链路视图**；日/月账单/成本/毛利/质量/对账 查询+导出+邮件；**联调测试台**。
- Commit `feat(fe): 流水(全链路视图)/报表/联调测试台 页面`。

### Task 6: 基础监控看板 + 告警
- ECharts：API 监控（调用量/成功率/失败率/P95/P99）、数据源监控（可用率/超时率/错误码分布/切换/配额）；告警列表（调用异常/余额不足/限额/数据源异常/未映射码）对接邮件。
- Commit `feat(fe): 基础监控看板 + 告警`。

## DoD
1. 前端 `npm run build` 成功；`npm run dev` 起后页面可访问、菜单/按钮按角色渲染。
2. 端到端 QA：走通「新增数据源→配接口→建产品→开通机构→配路由/计费→AK/SK 调用→查全链路/账单」。
3. 权限串与后端逐一一致，未授权按钮不显示。

## 后续（二期）
开放门户 + 在线调试 + 多语言 SDK + API 文档站；异步/批量任务页面；动态选源调参 + 决策留痕可视化；SkyWalking/Prometheus/Grafana 深度监控；图谱/股权穿透/报告生成。

---

> **一期收官**：P0 骨架 → P1 数据源 → P2 HTTP 引擎 → P3 产品 → P4 机构+网关鉴权 → P5 路由 → P6/P6b 计费 → P7 流水 → P8 限流 → P9 配置发布 → P10 报表 → P11 前端。至此一期「数据源可接入、产品可配置、机构可开通、路由可切换、调用可追踪、费用可计算、账单可统计、问题可排查」运营闭环达成；二期依赖一期积累的真实指标数据后启动（§10.2）。
