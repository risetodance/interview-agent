import { request } from './request';

// ========== 类型定义 ==========

/**
 * 会员类型
 */
export type MembershipType = 'FREE' | 'PREMIUM';

/**
 * 会员信息 DTO
 */
export interface MembershipDTO {
  membership: MembershipType;
  points: number;
  resumeQuota: number;
  interviewQuota: number;
  aiCallQuota: number;
  vipExpiryDate?: string;
}

// ========== API 方法 ==========

export const membershipApi = {
  /**
   * 获取当前用户的会员信息
   */
  async getMembership(): Promise<MembershipDTO> {
    return request.get<MembershipDTO>('/api/membership');
  },

  /**
   * 升级为 VIP 会员
   */
  async upgradeToPremium(): Promise<MembershipDTO> {
    return request.post<MembershipDTO>('/api/membership/upgrade');
  },

  /**
   * 使用简历额度
   */
  async useResumeQuota(): Promise<MembershipDTO> {
    return request.post<MembershipDTO>('/api/membership/use-resume-quota');
  },

  /**
   * 使用面试额度
   */
  async useInterviewQuota(): Promise<MembershipDTO> {
    return request.post<MembershipDTO>('/api/membership/use-interview-quota');
  },

  /**
   * 使用 AI 调用额度
   */
  async useAiQuota(): Promise<MembershipDTO> {
    return request.post<MembershipDTO>('/api/membership/use-ai-quota');
  },
};
