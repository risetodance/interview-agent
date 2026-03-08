import { request } from './request';

/**
 * 管理员用户信息
 */
export interface AdminUser {
  id: number;
  username: string;
  email: string;
  nickname: string;
  avatar: string;
  role: string;
  status: 'ACTIVE' | 'BANNED' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISABLED';
  membership: string;
  points: number;
  createdAt: string;
  approvedAt?: string;
  rejectedAt?: string;
  disabledAt?: string;
}

/**
 * 用户列表查询参数
 */
export interface UserQueryParams {
  page?: number;
  size?: number;
  status?: string;
  keyword?: string;
}

/**
 * 分页响应
 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/**
 * 系统配置
 */
export interface SystemConfig {
  key: string;
  value: string;
  description: string;
  type: string;
}

/**
 * 审计日志
 */
export interface AuditLog {
  id: number;
  userId: number;
  username: string;
  action: string;
  resource: string;
  method: string;
  ip: string;
  userAgent: string;
  details: string;
  createdAt: string;
}

/**
 * 仪表盘统计数据
 */
export interface DashboardStats {
  totalUsers: number;
  activeUsers: number;
  pendingUsers: number;
  totalInterviews: number;
  totalResumes: number;
  totalKnowledgeBases: number;
}

/**
 * 仪表盘最近活动
 */
export interface RecentActivity {
  id: number;
  type: 'USER' | 'INTERVIEW' | 'RESUME' | 'KNOWLEDGEBASE';
  action: string;
  description: string;
  createdAt: string;
}

export const adminApi = {
  /**
   * 获取用户列表
   */
  async getUsers(params?: UserQueryParams): Promise<PageResponse<AdminUser>> {
    return request.get<PageResponse<AdminUser>>('/api/admin/users', { params });
  },

  /**
   * 审核通过用户
   */
  async approveUser(userId: number): Promise<void> {
    return request.put<void>(`/api/admin/users/${userId}/approve`);
  },

  /**
   * 审核拒绝用户
   */
  async rejectUser(userId: number): Promise<void> {
    return request.put<void>(`/api/admin/users/${userId}/reject`);
  },

  /**
   * 禁用用户
   */
  async disableUser(userId: number): Promise<void> {
    return request.put<void>(`/api/admin/users/${userId}/disable`);
  },

  /**
   * 启用用户
   */
  async enableUser(userId: number): Promise<void> {
    return request.put<void>(`/api/admin/users/${userId}/enable`);
  },

  /**
   * 获取系统配置
   */
  async getSystemConfig(): Promise<SystemConfig[]> {
    return request.get<SystemConfig[]>('/api/admin/config');
  },

  /**
   * 更新系统配置
   */
  async updateSystemConfig(config: Record<string, string>): Promise<void> {
    return request.put<void>('/api/admin/config', config);
  },

  /**
   * 获取审计日志
   */
  async getAuditLogs(params?: {
    page?: number;
    size?: number;
    startDate?: string;
    endDate?: string;
    action?: string;
  }): Promise<PageResponse<AuditLog>> {
    return request.get<PageResponse<AuditLog>>('/api/admin/audit-logs', { params });
  },

  /**
   * 获取仪表盘统计数据
   */
  async getDashboardStats(): Promise<DashboardStats> {
    return request.get<DashboardStats>('/api/admin/dashboard/stats');
  },

  /**
   * 获取最近活动
   */
  async getRecentActivities(limit?: number): Promise<RecentActivity[]> {
    return request.get<RecentActivity[]>('/api/admin/dashboard/activities', {
      params: { limit },
    });
  },
};
