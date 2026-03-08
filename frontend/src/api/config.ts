import { request } from './request';

/**
 * 会话配置
 */
export interface SessionConfig {
  idleTimeoutMinutes: number;
  tokenExpirationHours: number;
}

export const configApi = {
  /**
   * 获取会话配置
   */
  async getSessionConfig(): Promise<SessionConfig> {
    return request.get<SessionConfig>('/api/config/session');
  },
};
