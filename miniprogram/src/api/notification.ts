import { get, put, del, post } from '../utils/request'

// 检测是否为H5环境
const isH5 = typeof window !== 'undefined' && typeof uni !== 'undefined' && !uni.getSystemInfoSync

// 通知类型
export interface Notification {
  id: string
  type: 'SYSTEM' | 'INTERVIEW' | 'RESUME' | 'MEMBERSHIP' | 'POINTS' | 'KNOWLEDGE'
  title: string
  content: string
  isRead: boolean
  createdAt: string
  relatedId?: string
  relatedType?: string
}

// 通知设置
export interface NotificationSettings {
  interviewNotification: boolean
  resumeNotification: boolean
  membershipNotification: boolean
  pointsNotification: boolean
  knowledgeNotification: boolean
  systemNotification: boolean
}

// 通知列表查询参数
export interface NotificationQueryParams {
  page?: number
  pageSize?: number
  type?: string
  isRead?: boolean
}

/**
 * 获取通知列表
 */
export const getNotificationList = (params?: NotificationQueryParams) => {
  return get<any>('/api/notifications', params).then(data => {
    // 如果是mock（包含list属性），直接返回
    if (data && data.list) {
      return data
    }
    // 后端返回分页对象的情况
    return {
      list: data?.content || [],
      total: data?.totalElements || 0,
      page: (data?.number || 0) + 1,
      pageSize: data?.size || 20
    }
  })
}

/**
 * 获取未读通知数量
 */
export const getUnreadCount = () => {
  return get<number>('/api/notifications/unread-count')
}

/**
 * 标记通知为已读
 */
export const markNotificationAsRead = (id: string) => {
  return put<Notification>(`/api/notifications/${id}/read`)
}

/**
 * 标记所有通知为已读
 */
export const markAllNotificationsAsRead = () => {
  return put<void>('/api/notifications/read-all')
}

/**
 * 删除通知
 */
export const deleteNotification = (id: string) => {
  return del(`/api/notifications/${id}`)
}

/**
 * 获取通知设置
 */
export const getNotificationSettings = () => {
  return get<NotificationSettings>('/api/notifications/settings')
}

/**
 * 更新通知设置
 */
export const updateNotificationSettings = (settings: Partial<NotificationSettings>) => {
  return put<NotificationSettings>('/api/notifications/settings', settings)
}

/**
 * 订阅微信消息
 * H5模式下mock
 */
export const subscribeWechatMessage = (templateIds: string[]) => {
  if (isH5 || (typeof uni !== 'undefined' && !uni.getSystemInfoSync?.())) {
    return Promise.resolve({ success: true, message: 'H5模式模拟订阅成功' })
  }
  return post<{ success: boolean }>('/api/notifications/wechat/subscribe', { templateIds })
}

/**
 * 发送微信通知消息
 * H5模式下mock
 */
export const sendWechatNotification = (data: { openid: string; templateId: string; data: any }) => {
  if (isH5 || (typeof uni !== 'undefined' && !uni.getSystemInfoSync?.())) {
    return Promise.resolve({ success: true, message: 'H5模式模拟发送成功' })
  }
  return post<{ success: boolean }>('/api/notifications/wechat/send', data)
}
