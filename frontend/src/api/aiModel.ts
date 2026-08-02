import { request } from './request';
import type {
  AiModelConfig,
  AiModelConfigListResponse,
  AiModelConfigRequest,
  AiModelConfigType,
  ProbeRequest,
  ProbeResult,
  TestRequest,
  TestResult,
} from '../types/aiModel';

/**
 * AI 模型配置 API（角色指派模型）
 */
export const aiModelApi = {
  /** 列表：全部凭证 + 当前角色指派映射 */
  async list(): Promise<AiModelConfigListResponse> {
    return request.get<AiModelConfigListResponse>('/api/admin/ai-models');
  },

  /** 详情 */
  async get(id: number): Promise<AiModelConfig> {
    return request.get<AiModelConfig>(`/api/admin/ai-models/${id}`);
  },

  /** 新建（纯凭证，不绑角色） */
  async create(data: AiModelConfigRequest): Promise<AiModelConfig> {
    return request.post<AiModelConfig>('/api/admin/ai-models', data);
  },

  /** 更新（apiKey 空/占位表示不修改；被引用时后端触发热生效） */
  async update(id: number, data: AiModelConfigRequest): Promise<AiModelConfig> {
    return request.put<AiModelConfig>(`/api/admin/ai-models/${id}`, data);
  },

  /** 删除（被角色引用时后端拒绝） */
  async remove(id: number): Promise<void> {
    return request.delete<void>(`/api/admin/ai-models/${id}`);
  },

  /** 指派角色（CHAT 不允许置空；SMALL_CHAT 置空请走 disableRole） */
  async assignRole(role: AiModelConfigType, configId: number): Promise<void> {
    return request.put<void>(`/api/admin/ai-models/role/${role}/assign/${configId}`);
  },

  /** 禁用角色（仅 SMALL_CHAT；CHAT 会被后端拒绝） */
  async disableRole(role: AiModelConfigType): Promise<void> {
    return request.put<void>(`/api/admin/ai-models/role/${role}/disable`);
  },

  /** 拉取可用模型列表 */
  async probe(data: ProbeRequest): Promise<ProbeResult> {
    return request.post<ProbeResult>('/api/admin/ai-models/probe', data);
  },

  /** 测试连接 */
  async test(data: TestRequest): Promise<TestResult> {
    return request.post<TestResult>('/api/admin/ai-models/test', data);
  },
};
