/**
 * 路由请求认证授权方式的共用逻辑：UI 上单选一种授权方式，
 * 插件侧仍是三列表 OR 语义（任一命中即放行），提交时总是显式提交
 * 三个列表（未选的为空数组=清空），与后端 REPLACE 的 null=保留现值
 * 语义区分开。
 */
import { CredentialType } from '@/interfaces/consumer';

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

// 已启用认证的实例是否存在授权范围（三个列表任一非空）。
export function hasAnyAuthScope(auth?: AuthConfigLists | null): boolean {
  return !!auth?.allowedConsumers?.length
    || !!auth?.allowedConsumerGroups?.length
    || !!auth?.allowedCredentials?.length;
}

// 列表页展示凭证值时脱敏：仅保留首 3 个字符，避免在列表页泄露完整密钥。
// 存储值即逻辑凭证值（不带传输前缀），统一按原值脱敏。
export function maskAllowedCredential(value: string): string {
  return value.length > 3 ? value.slice(0, 3) + '***' : '***';
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

// allow_credentials 匹配的是插件提取后的逻辑凭证值（header 来源已剥离
// scheme 前缀，query 来源为字面值），与凭证库中存储的值一致。
// 供凭证值输入框生成候选选项。
interface CredentialCarrier {
  credentials?: { type: string;[propName: string]: any }[];
}

export function getCredentialOptions(consumers: CredentialCarrier[]): string[] {
  return Array.from(new Set(consumers.flatMap(item => (item.credentials || [])
    .filter(c => c.type === CredentialType.KEY_AUTH.key)
    .flatMap(c => c.values || [])
    .filter(v => !!v))));
}
