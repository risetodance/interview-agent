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
  // 对齐后端 UserStatus（user 模块）：仅 ACTIVE/INACTIVE/BANNED
  status: 'ACTIVE' | 'INACTIVE' | 'BANNED';
  membership: string;
  points: number;
  createdAt: string;
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
 * 系统配置（对齐后端 SystemConfigEntity）
 */
export interface SystemConfig {
  id?: number;
  configKey: string;
  configValue: string;
  description: string;
  configType: string; // STRING / INTEGER / BOOLEAN / JSON
  editable?: boolean;
}

/**
 * 审计日志（对齐后端 AuditLogEntity）
 */
export interface AuditLog {
  id: number;
  operationType: string;
  operatorId: number;
  operatorUsername: string;
  operatorRole?: string;
  targetType?: string;
  targetId?: number;
  details?: string;
  ipAddress?: string;
  method?: string;
  requestUrl?: string;
  userAgent?: string;
  result?: string;
  errorMessage?: string;
  duration?: number;
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
 * 后端 /dashboard/activities 返回 List<AuditLogEntity>，故字段与 AuditLog 同源
 */
export interface RecentActivity {
  id: number;
  operationType: string;
  operatorId: number;
  operatorUsername?: string;
  operatorRole?: string;
  targetType?: string;
  targetId?: number;
  details?: string;
  ipAddress?: string;
  method?: string;
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
    startDate?: string; // ISO datetime，如 2026-07-20T00:00:00
    endDate?: string; // ISO datetime，如 2026-07-20T23:59:59
    operationType?: string;
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
