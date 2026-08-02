// AI 模型配置相关类型定义（与后端 DTO 精确对应）

/** 角色槽位：主对话模型 / 小模型 */
export type AiModelConfigType = 'CHAT' | 'SMALL_CHAT';

/**
 * AI 模型配置 DTO（纯凭证，对应后端 AiModelConfigDTO）
 * 注意：DTO 不含 api key 字段，前端固定显示 ******
 * 角色指派重构后：不再有 configType / isDefault / enabled，
 * 当前被哪些角色引用由列表响应里的 activeRoles 映射给出。
 */
export interface AiModelConfig {
  id: number;
  provider: string;
  displayName: string;
  baseUrl: string;
  modelName: string;
  temperature: number;
  lastTestAt?: string | null;
  lastTestOk?: boolean | null;
  createdAt: string;
  updatedAt: string;
}

/** 当前角色指派映射（CHAT / SMALL_CHAT → configId 或 null） */
export interface ActiveRoles {
  CHAT: number | null;
  SMALL_CHAT: number | null;
}

/** 列表响应：全部凭证 + 当前角色指派 */
export interface AiModelConfigListResponse {
  configs: AiModelConfig[];
  activeRoles: ActiveRoles;
}

/** 创建/更新请求（纯凭证，对应后端 AiModelConfigRequest） */
export interface AiModelConfigRequest {
  provider: string;
  displayName: string;
  baseUrl: string;
  /** baseUrl 输入模式：true=用户填完整 URL（原样入库）；false=域名根（后端补 /v1）。默认 false */
  useFullUrl?: boolean;
  /** 明文；更新时空/占位表示不修改 */
  apiKey: string;
  modelName: string;
  temperature: number;
}

/** 拉取的单个模型信息 */
export interface ProbeModelInfo {
  id: string;
  name?: string;
}

/** 拉取模型列表结果 */
export interface ProbeResult {
  models: ProbeModelInfo[];
  ok: boolean;
  message?: string;
}

/** 连接测试结果 */
export interface TestResult {
  ok: boolean;
  latencyMs?: number;
  message?: string;
}

/** 拉取请求体 */
export interface ProbeRequest {
  /** 已存配置 id（编辑态优先用此字段，后端用已存的 baseUrl+apiKey 拉取） */
  id?: number;
  /** 新建态必填；编辑态传 id 时可省略 */
  baseUrl?: string;
  /** 新建态必填；编辑态传 id 时可省略 */
  apiKey?: string;
}

/** 测试请求体 */
export interface TestRequest {
  id?: number;
  baseUrl?: string;
  apiKey?: string;
  modelName?: string;
}
