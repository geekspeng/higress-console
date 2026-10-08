# 路由请求认证授权方式单选化设计

日期：2026-10-09
仓库：higress-console（D:\OpenSource\higress-console）

## 背景

路由级 key-auth 请求认证（插件 `key-auth`）的授权范围支持三种路由级允许列表，插件侧为 OR 语义（任一命中即放行）：

| 列表 | 插件字段 | 含义 |
|---|---|---|
| 消费者名称 | `allow` | 个体授权 |
| 消费者组 | `allow_groups` | 批量授权 |
| 凭证值 | `allow_credentials` | 绕过消费者模型直接授权某个 key |

控制台此前在路由表单中同时暴露三个输入项，配置体验复杂。本次改为**默认模式单选**：用户一次只选一种授权方式。插件能力不变（仍支持三列表 OR），仅收敛 UI；后端提交格式不变。

## 决策记录

- 插件（higress 仓库 C++ key-auth）三列表 OR 语义**维持不动**。
- 后端 `AllowList` / `KeyAuthCredentialHandler.updateAllowList` **不动**：REPLACE 语义已支持显式提交三个列表（非 null 即替换，含空数组），前端单选总是显式提交三列表即可。
- 编辑回显策略选 **方案 a**：按优先级取第一个非空列表回显，其余不展示但给出警告，允许直接保存（保存即丢弃未选列表）。
- 消费者列表页列顺序已由 commit `0b8fb1c` 完成（名称 → 分组 → 认证方式 → 操作），本次**无改动**。

## 交互设计

### 表单（两处，结构一致）

- `frontend/src/pages/route/components/RouteForm/index.tsx`
- `frontend/src/pages/ai/components/RouteForm/index.tsx`

启用「请求认证」开关后：

1. 新增「授权方式」Radio（三选一，默认「指定消费者」）：
   - 按消费者（`allowedConsumers`）
   - 按消费者组（`allowedConsumerGroups`）
   - 按凭证值（`allowedCredentials`）
2. Radio 下方仅渲染所选方式对应的一个输入项：
   - 消费者：消费者名称多选（选项来自 consumerList）
   - 消费者组：组多选（选项来自 consumerList 中非空 group 去重）
   - 凭证值：API Key tags 输入（BEARER 来源自动带 `Bearer ` 前缀，逻辑沿用现有实现）

### 提交

选中的列表提交现值；另外两个列表提交 `[]` 显式清空。沿用现有 REPLACE 语义（表单总是显式提交三个列表，与后端 null=保留现值 区分）。

### 编辑回显（存量混配数据）

1. 按 消费者 > 消费者组 > 凭证值 优先级，取第一个非空列表回显为所选授权方式及选项值；Radio 无选中列表时默认「按消费者」。
2. 若存在被忽略的其他非空列表，表单认证区域顶部显示警告条：
   「当前配置包含多种授权方式，保存后将仅保留所选方式」。
3. 用户可直接保存（未选列表将被清空）——不阻断。

## 改动清单

| 文件 | 改动 |
|---|---|
| `frontend/src/interfaces/route.ts` | `AuthConfig` 三字段保留不变；无接口变更 |
| `pages/route/components/RouteForm/index.tsx` | 三输入项改为 授权方式 Radio + 单输入项；回显优先级逻辑；混配警告条 |
| `pages/ai/components/RouteForm/index.tsx` | 同上 |
| 后端（sdk / handler） | 无改动 |
| `pages/consumer/index.tsx` | 无改动（列顺序已完成） |

## 不变式 / 风险

- 「任意已认证消费者」场景 = 三个列表均为空，插件语义为放行所有已认证消费者，保持不变。
- 列表页（route/index.tsx、ai/route.tsx）按 `allowedConsumers` 的展示/筛选不改：存量混配数据在列表页仍如实显示。
- 风险：用户编辑存量混配配置且忽略警告时，未选列表被静默清空——已用警告条缓解，产品上接受。

## 测试要点

1. 新建路由：单选消费者/组/凭证值，保存后插件配置只含对应一个列表。
2. 切换授权方式后提交：旧列表被清空。
3. 编辑存量单列表配置：正确回显。
4. 编辑存量三列表混配配置：回显优先级列表 + 警告条出现；保存后仅剩所选列表。
5. AI 路由表单同 1-4。
6. i18n：新增文案的 zh/en 键补齐。
