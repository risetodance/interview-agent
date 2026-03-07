import { request } from './request';

/**
 * 通知类型
 */
export type NotificationType =
  | 'SYSTEM'       // 系统通知
  | 'INTERVIEW'     // 面试通知
  | 'RESUME'        // 简历通知
  | 'KNOWLEDGEBASE' // 知识库通知
  | 'MEMBERSHIP';   // 会员通知

/**
 * 通知状态
 */
export type NotificationStatus = 'UNREAD' | 'READ';

/**
 * 通知项
 */
export interface Notification {
  id: number;
  type: NotificationType;
  title: string;
  content: string;
  status: NotificationStatus;
  relatedId?: number;
  relatedType?: string;
  createdAt: string;
  readAt?: string;
}

/**
 * 通知列表查询参数
 */
export interface NotificationQueryParams {
  page: number;
  pageSize: number;
  type?: NotificationType;
  status?: NotificationStatus;
}

/**
 * 通知列表响应
 */
export interface NotificationListResponse {
  items: Notification[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

/**
 * 通知设置
 */
export interface NotificationSettings {
  inAppEnabled: boolean;    // 站内通知
  emailEnabled: boolean;    // 邮件通知
  smsEnabled: boolean;      // 短信通知
  wechatEnabled: boolean;   // 微信通知
}

/**
 * 通知设置更新请求
 */
export interface NotificationSettingsUpdateRequest {
  inAppEnabled?: boolean;
  emailEnabled?: boolean;
  smsEnabled?: boolean;
  wechatEnabled?: boolean;
}

/**
 * 未读计数响应
 */
export interface UnreadCountResponse {
  unreadCount: number;
}

export const notificationApi = {
  /**
   * 获取通知列表
   */
  async getNotifications(params: NotificationQueryParams): Promise<NotificationListResponse> {
    return request.get<NotificationListResponse>('/api/notifications', { params });
  },

  /**
   * 获取未读通知数量
   */
  async getUnreadCount(): Promise<UnreadCountResponse> {
    return request.get<UnreadCountResponse>('/api/notifications/unread-count');
  },

  /**
   * 标记单条通知为已读
   */
  async markAsRead(notificationId: number): Promise<void> {
    return request.put<void>(`/api/notifications/${notificationId}/read`);
  },

  /**
   * 标记全部通知为已读
   */
  async markAllAsRead(): Promise<void> {
    return request.put<void>('/api/notifications/read-all');
  },

  /**
   * 删除通知
   */
  async deleteNotification(notificationId: number): Promise<void> {
    return request.delete<void>(`/api/notifications/${notificationId}`);
  },

  /**
   * 获取通知设置
   */
  async getSettings(): Promise<NotificationSettings> {
    return request.get<NotificationSettings>('/api/notifications/settings');
  },

  /**
   * 更新通知设置
   */
  async updateSettings(data: NotificationSettingsUpdateRequest): Promise<NotificationSettings> {
    return request.put<NotificationSettings>('/api/notifications/settings', data);
  },
};
