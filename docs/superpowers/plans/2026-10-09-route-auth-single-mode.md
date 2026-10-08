# 路由请求认证单选化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 路由/AI 路由表单的请求认证从三列表并列输入改为「授权方式」单选（消费者 / 消费者组 / 凭证值），提交时总是显式三列表（未选=空数组），编辑存量混配配置时按优先级回显并警告。

**Architecture:** 纯前端改动。两个表单（route、ai route）各自把三个 `Form.Item` 替换为一个 Radio + 条件渲染的单输入项；新增 `authMode` 表单字段驱动切换；提交逻辑不变（仍显式提交三列表）。共用回显/提交辅助逻辑放在一个新文件 `routeAuthConfig.ts`，两个表单复用。

**Tech Stack:** React + antd Form + i18next，仓库 D:\OpenSource\higress-console，分支 feat/key-auth-consumer-group。

## Global Constraints

- 前端无单元测试设施：验证用 `npm run eslint`、`npm run check-i18n`、`npx tsc --noEmit`（在 frontend/ 目录执行）。
- i18n：新增 key 必须同时加到 `frontend/src/locales/zh-CN/translation.json` 和 `frontend/src/locales/en-US/translation.json`。
- `AuthConfig` 接口（`frontend/src/interfaces/route.ts:101-108`）与后端 sdk 不改。
- 提交语义不变：编辑表单总是显式提交三个列表，空数组=清空（与后端 REPLACE null=保留 区分）。
- 授权方式 Radio 值域：`'consumers' | 'groups' | 'credentials'`。
- 回显优先级：消费者 > 消费者组 > 凭证值，取第一个非空列表；检测到其他非空列表时显示警告条，不阻断保存。

---

### Task 1: 共用辅助模块 routeAuthConfig.ts

**Files:**
- Create: `frontend/src/pages/route/components/RouteForm/routeAuthConfig.ts`

**Interfaces:**
- Produces:
  - `type AuthMode = 'consumers' | 'groups' | 'credentials'`
  - `interface AuthConfigLists { allowedConsumers?: string[]; allowedConsumerGroups?: string[]; allowedCredentials?: string[] }`
  - `resolveAuthMode(auth?: AuthConfigLists | null): AuthMode` — 按优先级取第一个非空列表，全空返回 `'consumers'`
  - `hasIgnoredLists(auth: AuthConfigLists | undefined, mode: AuthMode): boolean` — mode 之外存在非空列表时为 true
  - `getListsForMode(values: Record<string, any>, mode: AuthMode): [string[], string[], string[]]` — 返回按 consumers/groups/credentials 顺序的三列表，选中项取表单值，其余为空数组

- [x] **Step 1: 写模块**

```ts
/**
 * 路由请求认证授权方式的共用逻辑：UI 上单选一种授权方式，
 * 插件侧仍是三列表 OR 语义（任一命中即放行），提交时总是显式提交
 * 三个列表（未选的为空数组=清空），与后端 REPLACE 的 null=保留现值
 * 语义区分开。
 */

export type AuthMode = 'consumers' | 'groups' | 'credentials';

export interface AuthConfigLists {
  allowedConsumers?: string[];
  allowedConsumerGroups?: string[];
  allowedCredentials?: string[];
}

// 回显优先级：消费者 > 消费者组 > 凭证值。存量混配配置只回显第一个非空列表。
export function resolveAuthMode(auth?: AuthConfigLists | null): AuthMode {
  if (auth?.allowedConsumers?.length) {
    return 'consumers';
  }
  if (auth?.allowedConsumerGroups?.length) {
    return 'groups';
  }
  if (auth?.allowedCredentials?.length) {
    return 'credentials';
  }
  return 'consumers';
}

// mode 之外还有非空列表 → 存量混配，需要警告"保存后将仅保留所选方式"。
export function hasIgnoredLists(auth: AuthConfigLists | undefined, mode: AuthMode): boolean {
  if (!auth) {
    return false;
  }
  const has = (list?: string[]) => !!list?.length;
  if (mode === 'consumers') {
    return has(auth.allowedConsumerGroups) || has(auth.allowedCredentials);
  }
  if (mode === 'groups') {
    return has(auth.allowedConsumers) || has(auth.allowedCredentials);
  }
  return has(auth.allowedConsumers) || has(auth.allowedConsumerGroups);
}

// 按选中方式生成提交用的三列表（顺序：consumers, groups, credentials）。
export function getListsForMode(values: Record<string, any>, mode: AuthMode): [string[], string[], string[]] {
  const normalize = (v: any): string[] => (v && !Array.isArray(v) ? [v] : v || []);
  switch (mode) {
    case 'groups':
      return [[], normalize(values.authConfig_allowedConsumerGroups), []];
    case 'credentials':
      return [[], [], normalize(values.authConfig_allowedCredentials)];
    default:
      return [normalize(values.authConfig_allowedConsumers), [], []];
  }
}
```

- [x] **Step 2: 类型检查**

Run: `cd /d/OpenSource/higress-console/frontend && npx tsc --noEmit 2>&1 | grep routeAuthConfig || echo OK`
Expected: `OK`（模块无类型错误；仓库其他既有报错不在本任务范围）

- [x] **Step 3: Commit**

```bash
cd /d/OpenSource/higress-console
git add frontend/src/pages/route/components/RouteForm/routeAuthConfig.ts
git commit -m "feat(route-auth): add shared single-mode auth config helpers"
```

---

### Task 2: i18n 文案

**Files:**
- Modify: `frontend/src/locales/zh-CN/translation.json:66-71`（aiRoute.routeForm.label 节）
- Modify: `frontend/src/locales/en-US/translation.json:64-71`（同节）

**Interfaces:**
- Produces（新增 key，两语言同名）：
  - `aiRoute.routeForm.label.authMode` — 授权方式
  - `aiRoute.routeForm.label.authModeConsumers` — 按消费者
  - `aiRoute.routeForm.label.authModeGroups` — 按消费者组
  - `aiRoute.routeForm.label.authModeCredentials` — 按凭证值
  - `aiRoute.routeForm.label.authConfigMixedWarning` — 混配警告条文案

- [x] **Step 1: zh-CN**

在 `zh-CN/translation.json` 的 `aiRoute.routeForm.label` 节（`authConfigExtra` 之后、`authConfigList` 之前）插入：

```json
"authMode": "授权方式",
"authModeConsumers": "按消费者",
"authModeGroups": "按消费者组",
"authModeCredentials": "按凭证值",
"authConfigMixedWarning": "当前配置包含多种授权方式，保存后将仅保留所选方式",
```

- [x] **Step 2: en-US**

在 `en-US/translation.json` 同节同位置插入：

```json
"authMode": "Authorization Type",
"authModeConsumers": "By Consumer",
"authModeGroups": "By Consumer Group",
"authModeCredentials": "By API Key",
"authConfigMixedWarning": "This configuration contains multiple authorization types. Only the selected type will be kept after saving",
```

- [x] **Step 3: 校验**

Run: `cd /d/OpenSource/higress-console/frontend && npm run check-i18n`
Expected: exit 0，无 missing key 报告

- [x] **Step 4: Commit**

```bash
cd /d/OpenSource/higress-console
git add frontend/src/locales/zh-CN/translation.json frontend/src/locales/en-US/translation.json
git commit -m "feat(route-auth): add i18n strings for single-mode authorization type"
```

---

### Task 3: route 表单单选化

**Files:**
- Modify: `frontend/src/pages/route/components/RouteForm/index.tsx`

**Interfaces:**
- Consumes: Task 1 的 `resolveAuthMode / hasIgnoredLists / getListsForMode / AuthMode`，Task 2 的 i18n key
- Produces: 表单字段 `authMode`（值域 AuthMode）；提交的三列表语义不变

- [x] **Step 1: 引入辅助模块与组件**

文件头部 import 区加：

```tsx
import { Alert, Radio } from 'antd';
import { AuthConfigLists, AuthMode, getListsForMode, hasIgnoredLists, resolveAuthMode } from './routeAuthConfig';
```

组件内（`const [authConfig_enabled, setAuthConfigEnabled] = useState(false);` 附近）加：

```tsx
const [authMode, setAuthMode] = useState<AuthMode>('consumers');
const [mixedAuthWarning, setMixedAuthWarning] = useState(false);
```

（若 `Alert` 已在 import 中则不重复。）

- [x] **Step 2: 编辑回显（useEffect 内）**

将 `useEffect` 中（约 122-140 行）：

```tsx
const _authConfig_allowedConsumers = authConfig?.allowedConsumers || [];
const _authConfig_allowedConsumerGroups = authConfig?.allowedConsumerGroups || [];
const _authConfig_allowedCredentials = authConfig?.allowedCredentials || [];
form.setFieldsValue({
  ...
  authConfig_allowedConsumers: _authConfig_allowedConsumers,
  authConfig_allowedConsumerGroups: _authConfig_allowedConsumerGroups,
  authConfig_allowedCredentials: _authConfig_allowedCredentials,
```

改为：

```tsx
const _authMode = resolveAuthMode(authConfig as AuthConfigLists);
form.setFieldsValue({
  ...
  authMode: _authMode,
  authConfig_allowedConsumers: _authMode === 'consumers' ? (authConfig?.allowedConsumers || []) : [],
  authConfig_allowedConsumerGroups: _authMode === 'groups' ? (authConfig?.allowedConsumerGroups || []) : [],
  authConfig_allowedCredentials: _authMode === 'credentials' ? (authConfig?.allowedCredentials || []) : [],
```

并在 `setAuthConfigEnabled(_authConfig_enabled);` 之后加：

```tsx
setAuthMode(_authMode);
setMixedAuthWarning(hasIgnoredLists(authConfig as AuthConfigLists, _authMode));
```

（`authConfig` 变量取自该 effect 已有的 `value.authConfig` 解构，保持原样。）

- [x] **Step 3: 提交逻辑（handleSubmit 内）**

将（约 212-217 行）：

```tsx
const authConfig = { enabled: authConfig_enabled, allowedConsumers: null, allowedConsumerGroups: null, allowedCredentials: null };
const allowdConsumers = values.authConfig_allowedConsumers;
authConfig.allowedConsumers = allowdConsumers && !Array.isArray(allowdConsumers) ? [allowdConsumers] : allowdConsumers;
// 三列表 OR：编辑表单总是显式提交三个列表（空数组=清空），与后端 REPLACE 的 null=保留现值 语义区分开。
authConfig.allowedConsumerGroups = values.authConfig_allowedConsumerGroups || [];
authConfig.allowedCredentials = values.authConfig_allowedCredentials || [];
```

改为：

```tsx
// UI 单选一种授权方式；插件仍是三列表 OR 语义。总是显式提交三个列表
// （空数组=清空），与后端 REPLACE 的 null=保留现值 语义区分开。
const [allowedConsumers, allowedConsumerGroups, allowedCredentials] =
  getListsForMode(values, values.authMode as AuthMode);
const authConfig = { enabled: authConfig_enabled, allowedConsumers, allowedConsumerGroups, allowedCredentials };
```

- [x] **Step 4: 表单 JSX 替换**

将「允许请求本路由的消费者名称列表 / 消费者分组列表 / API Key 列表」三个 `Form.Item`（约 385-469 行）整体替换为：

```tsx
{authConfig_enabled && (
  <>
    {mixedAuthWarning && (
      <Form.Item>
        <Alert type="warning" showIcon message={t('aiRoute.routeForm.label.authConfigMixedWarning')} />
      </Form.Item>
    )}
    <Form.Item label={t('aiRoute.routeForm.label.authMode')} name="authMode" initialValue="consumers">
      <Radio.Group onChange={e => setAuthMode(e.target.value)}>
        <Radio value="consumers">{t('aiRoute.routeForm.label.authModeConsumers')}</Radio>
        <Radio value="groups">{t('aiRoute.routeForm.label.authModeGroups')}</Radio>
        <Radio value="credentials">{t('aiRoute.routeForm.label.authModeCredentials')}</Radio>
      </Radio.Group>
    </Form.Item>
    {authMode === 'consumers' && (
      <Form.Item
        label={t('aiRoute.routeForm.label.authConfigList')}
        extra={(<HistoryButton text={t('consumer.create')} path={"/consumer"} />)}
      >
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Form.Item name="authConfig_allowedConsumers" noStyle>
            <Select
              allowClear
              mode="multiple"
              placeholder={t('aiRoute.routeForm.label.authConfigList')}
              style={{ flex: 1 }}
            >
              {consumerList.map((item) => (
                <Select.Option key={String(item.name)} value={item.name}>{item.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Button style={{ marginLeft: 8 }} onClick={() => consumerResult.run()} icon={<RedoOutlined />} />
        </div>
      </Form.Item>
    )}
    {authMode === 'groups' && (
      <Form.Item label={t('aiRoute.routeForm.label.authConfigGroups')}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Form.Item name="authConfig_allowedConsumerGroups" noStyle>
            <Select
              allowClear
              mode="multiple"
              placeholder={t('aiRoute.routeForm.label.authConfigGroups')}
              style={{ flex: 1 }}
            >
              {Array.from(new Set(consumerList.filter(item => item.group).map(item => item.group))).map(group => (
                <Select.Option key={group} value={group}>{group}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Button style={{ marginLeft: 8 }} onClick={() => consumerResult.run()} icon={<RedoOutlined />} />
        </div>
      </Form.Item>
    )}
    {authMode === 'credentials' && (
      <Form.Item label={t('aiRoute.routeForm.label.authConfigCredentials')}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Form.Item name="authConfig_allowedCredentials" noStyle>
            <Select
              allowClear
              mode="tags"
              placeholder={t('aiRoute.routeForm.label.authConfigCredentials')}
              style={{ flex: 1 }}
            >
              {Array.from(new Set(consumerList.flatMap(item => (item.credentials || [])
                .filter(c => c.type === CredentialType.KEY_AUTH.key)
                .flatMap(c => {
                  // 插件匹配的是请求中携带的原始凭证值：BEARER 来源的凭证在请求里带
                  // "Bearer " 前缀，allow_credentials 必须包含该前缀才能命中。
                  const values = (c as any).values || [];
                  return c.source === KeyAuthCredentialSource.BEARER
                    ? values.map((v: string) => `Bearer ${v}`)
                    : values;
                }))
                .filter(v => !!v))).map(credential => (
                <Select.Option key={credential} value={credential}>{credential}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Button style={{ marginLeft: 8 }} onClick={() => consumerResult.run()} icon={<RedoOutlined />} />
        </div>
      </Form.Item>
    )}
  </>
)}
```

- [x] **Step 5: 验证**

Run: `cd /d/OpenSource/higress-console/frontend && npx tsc --noEmit 2>&1 | grep "RouteForm/index" || echo OK; npm run check-i18n; npm run eslint 2>&1 | grep "pages/route" || echo LINT-OK`
Expected: `OK` + `LINT-OK` + i18n 校验通过

- [x] **Step 6: Commit**

```bash
cd /d/OpenSource/higress-console
git add frontend/src/pages/route/components/RouteForm/index.tsx
git commit -m "feat(route-auth): single authorization type selector in route form"
```

---

### Task 4: AI 路由表单单选化

**Files:**
- Modify: `frontend/src/pages/ai/components/RouteForm/index.tsx`

**Interfaces:**
- Consumes: Task 1 辅助模块、Task 2 i18n key（与 Task 3 相同的 key）

- [x] **Step 1: import 与 state**

文件头部 import 区加：

```tsx
import { Alert, Radio } from 'antd';
import { AuthConfigLists, AuthMode, getListsForMode, hasIgnoredLists, resolveAuthMode } from '@/pages/route/components/RouteForm/routeAuthConfig';
```

（`Alert`/`Radio` 若已在既有 antd import 里，合并进该行。）组件内加：

```tsx
const [authMode, setAuthMode] = useState<AuthMode>('consumers');
const [mixedAuthWarning, setMixedAuthWarning] = useState(false);
```

- [x] **Step 2: 回显（initForm 内）**

将（约 124-126 行）：

```tsx
authConfig_allowedConsumers: value?.authConfig?.allowedConsumers || [],
authConfig_allowedConsumerGroups: value?.authConfig?.allowedConsumerGroups || [],
authConfig_allowedCredentials: value?.authConfig?.allowedCredentials || [],
```

改为：

```tsx
authMode: _authMode,
authConfig_allowedConsumers: _authMode === 'consumers' ? (value?.authConfig?.allowedConsumers || []) : [],
authConfig_allowedConsumerGroups: _authMode === 'groups' ? (value?.authConfig?.allowedConsumerGroups || []) : [],
authConfig_allowedCredentials: _authMode === 'credentials' ? (value?.authConfig?.allowedCredentials || []) : [],
```

并在 `initValues` 构造前加：

```tsx
const _authMode = resolveAuthMode(value?.authConfig as AuthConfigLists);
```

在 `form.setFieldsValue(initValues);` 之后加：

```tsx
setAuthMode(_authMode);
setMixedAuthWarning(hasIgnoredLists(value?.authConfig as AuthConfigLists, _authMode));
```

- [x] **Step 3: 提交（handleSubmit 内）**

将（约 218-223 行）：

```tsx
payload['authConfig']['allowedConsumers'] = authConfig_allowedConsumers && !Array.isArray(authConfig_allowedConsumers)
  ? [authConfig_allowedConsumers] : authConfig_allowedConsumers;
// 三列表 OR：任一命中即可访问。编辑表单总是显式提交三个列表（空数组=清空），
// 与后端 REPLACE 的 null=保留现值 语义区分开。
payload['authConfig']['allowedConsumerGroups'] = authConfig_allowedConsumerGroups || [];
payload['authConfig']['allowedCredentials'] = authConfig_allowedCredentials || [];
```

改为：

```tsx
// UI 单选一种授权方式；插件仍是三列表 OR 语义。总是显式提交三个列表
// （空数组=清空），与后端 REPLACE 的 null=保留现值 语义区分开。
const [allowedConsumers, allowedConsumerGroups, allowedCredentials] =
  getListsForMode(values, values.authMode as AuthMode);
payload['authConfig']['allowedConsumers'] = allowedConsumers;
payload['authConfig']['allowedConsumerGroups'] = allowedConsumerGroups;
payload['authConfig']['allowedCredentials'] = allowedCredentials;
```

并从 `values` 解构中删除 `authConfig_allowedConsumers`、`authConfig_allowedConsumerGroups`、`authConfig_allowedCredentials` 三项（约 180-182 行）。

- [x] **Step 4: 表单 JSX 替换**

将三个授权列表 `Form.Item`（约 606-690 行）替换为与 Task 3 Step 4 相同结构的块（选项渲染代码与该文件原有的保持一致——消费者列表、组去重、凭证值 BEARER 前缀处理逐字沿用本文件原代码），外层同样用 `{authConfig_enabled && (<>...</>)}` 包裹，含 `mixedAuthWarning` 警告条与 `authMode` Radio。

- [x] **Step 5: 验证**

Run: `cd /d/OpenSource/higress-console/frontend && npx tsc --noEmit 2>&1 | grep "ai/components/RouteForm" || echo OK; npm run check-i18n; npm run eslint 2>&1 | grep "pages/ai" || echo LINT-OK`
Expected: `OK` + `LINT-OK` + i18n 校验通过

- [x] **Step 6: Commit**

```bash
cd /d/OpenSource/higress-console
git add frontend/src/pages/ai/components/RouteForm/index.tsx
git commit -m "feat(route-auth): single authorization type selector in AI route form"
```

---

### Task 5: 构建验证与收尾

**Files:** 无新增改动；仅验证

- [x] **Step 1: 全量构建**

Run: `cd /d/OpenSource/higress-console/frontend && npm run build`
Expected: 构建成功退出

- [x] **Step 2: 手工冒烟（可选，本地起 dev 时）**

`npm start` 后检查：新建路由启用认证 → Radio 默认「按消费者」；切换方式只显示对应输入项；编辑三列表混配存量路由 → 出现警告条且回显消费者列表；保存后重新打开 → 只剩所选列表。

- [x] **Step 3: 最终提交（如有 lint 自动修复产物）**

```bash
cd /d/OpenSource/higress-console
git status --short
# 仅当存在 lint-staged 自动修复时：
git add -A frontend/src && git commit -m "style(route-auth): apply lint fixes"
```
