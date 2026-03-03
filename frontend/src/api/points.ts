import { request } from './request';

// ========== 类型定义 ==========

/**
 * 积分记录类型
 */
export type PointsRecordType = 'SIGN_IN' | 'COMPLETE_INTERVIEW' | 'SHARE_KB' | 'EXCHANGE';

/**
 * 积分记录 DTO
 */
export interface PointsRecordDTO {
  id: number;
  userId: number;
  points: number;
  type: PointsRecordType;
  description: string;
  createdAt: string;
}

/**
 * 积分历史响应
 */
export interface PointsHistoryResponse {
  content: PointsRecordDTO[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/**
 * 签到响应
 */
export interface SignInResponse {
  signedIn: boolean;
  consecutiveDays: number;
  pointsCanEarn: number;
}

/**
 * 签到状态响应（与签到响应相同）
 */
export type SignInStatusResponse = SignInResponse;

// ========== API 方法 ==========

export const pointsApi = {
  /**
   * 获取当前积分余额
   */
  async getPoints(): Promise<number> {
    return request.get<number>('/api/points');
  },

  /**
   * 获取积分记录列表（分页）
   */
  async getPointsHistory(page: number = 0, size: number = 20): Promise<PointsHistoryResponse> {
    const data = await request.get<PointsRecordDTO[]>(
      `/api/points/history?page=${page}&size=${size}`
    );
    // 转换为分页格式
    return {
      content: data,
      totalElements: data.length,
      totalPages: Math.ceil(data.length / size),
      size: size,
      number: page
    };
  },

  /**
   * 签到
   */
  async signIn(): Promise<SignInResponse> {
    return request.post<SignInResponse>('/api/points/signin');
  },

  /**
   * 获取签到状态
   */
  async getSignInStatus(): Promise<SignInResponse> {
    return request.get<SignInResponse>('/api/points/signin/status');
  },
};
