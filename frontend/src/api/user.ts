import { request } from './request';

/**
 * 用户类型
 */
export interface UserProfile {
  id: number;
  username: string;
  email: string;
  nickname: string;
  avatar: string;
  role: string;
  points: number;
  membership: string;
}

/**
 * 个人资料更新请求
 */
export interface ProfileUpdateRequest {
  nickname?: string;
  avatar?: string;
}

/**
 * 密码修改请求
 */
export interface PasswordChangeRequest {
  oldPassword: string;
  newPassword: string;
}

export const userApi = {
  /**
   * 获取当前用户信息
   */
  async getUserProfile(): Promise<UserProfile> {
    return request.get<UserProfile>('/api/users/me');
  },

  /**
   * 更新个人资料
   */
  async updateProfile(data: ProfileUpdateRequest): Promise<UserProfile> {
    return request.put<UserProfile>('/api/users/me/profile', data);
  },

  /**
   * 修改密码
   */
  async changePassword(data: PasswordChangeRequest): Promise<void> {
    return request.put<void>('/api/users/me/password', data);
  },
};
