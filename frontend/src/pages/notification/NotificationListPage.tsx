import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  Check,
  CheckCheck,
  Trash2,
  FileText,
  Users,
  Database,
  Crown,
  Settings,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import {
  notificationApi,
  Notification,
  NotificationType,
  NotificationStatus,
} from '../../api/notification';
import { getErrorMessage } from '../../api/request';

// 通知类型映射
const notificationTypeConfig: Record<NotificationType, { icon: React.ComponentType<{ className?: string }>; label: string; color: string }> = {
  SYSTEM: { icon: Bell, label: '系统通知', color: 'bg-slate-100 text-slate-600' },
  INTERVIEW: { icon: Users, label: '面试通知', color: 'bg-blue-100 text-blue-600' },
  RESUME: { icon: FileText, label: '简历通知', color: 'bg-green-100 text-green-600' },
  KNOWLEDGEBASE: { icon: Database, label: '知识库通知', color: 'bg-purple-100 text-purple-600' },
  MEMBERSHIP: { icon: Crown, label: '会员通知', color: 'bg-amber-100 text-amber-600' },
};

// 格式化时间
function formatTime(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;
  return date.toLocaleDateString('zh-CN');
}

export default function NotificationListPage() {
  const navigate = useNavigate();

  // 状态
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [filterType, setFilterType] = useState<NotificationType | ''>('');
  const [filterStatus, setFilterStatus] = useState<NotificationStatus | ''>('');
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 加载通知列表
  const loadNotifications = async () => {
    setLoading(true);
    try {
      const params: { page: number; pageSize: number; type?: NotificationType; status?: NotificationStatus } = {
        page,
        pageSize,
      };
      if (filterType) params.type = filterType;
      if (filterStatus) params.status = filterStatus;

      const response = await notificationApi.getNotifications(params);
      // 后端返回格式
      setNotifications(response.items || []);
      setTotal(response.total || 0);
      setTotalPages(response.totalPages || 0);
    } catch (error) {
      setMessage({ type: 'error', text: getErrorMessage(error) });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadNotifications();
  }, [page, filterType, filterStatus]);

  // 标记单条已读
  const handleMarkAsRead = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setActionLoading(id);
    try {
      await notificationApi.markAsRead(id);
      setNotifications(prev =>
        prev.map(n => (n.id === id ? { ...n, status: 'READ' as NotificationStatus } : n))
      );
      setMessage({ type: 'success', text: '已标记为已读' });
    } catch (error) {
      setMessage({ type: 'error', text: getErrorMessage(error) });
    } finally {
      setActionLoading(null);
    }
  };

  // 标记全部已读
  const handleMarkAllAsRead = async () => {
    setActionLoading(-1);
    try {
      await notificationApi.markAllAsRead();
      setNotifications(prev =>
        prev.map(n => ({ ...n, status: 'READ' as NotificationStatus }))
      );
      setMessage({ type: 'success', text: '已全部标记为已读' });
    } catch (error) {
      setMessage({ type: 'error', text: getErrorMessage(error) });
    } finally {
      setActionLoading(null);
    }
  };

  // 删除通知
  const handleDelete = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('确定要删除这条通知吗？')) return;

    setActionLoading(id);
    try {
      await notificationApi.deleteNotification(id);
      setNotifications(prev => prev.filter(n => n.id !== id));
      setTotal(prev => prev - 1);
      setMessage({ type: 'success', text: '删除成功' });
    } catch (error) {
      setMessage({ type: 'error', text: getErrorMessage(error) });
    } finally {
      setActionLoading(null);
    }
  };

  // 跳转设置页面
  const handleGoToSettings = () => {
    navigate('/notifications/settings');
  };

  // 分页
  const handlePageChange = (newPage: number) => {
    if (newPage >= 1 && newPage <= totalPages) {
      setPage(newPage);
    }
  };

  // 清除消息
  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [message]);

  return (
    <motion.div
      className="max-w-4xl mx-auto"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 页面头部 */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <motion.h1
            className="text-3xl font-bold text-slate-900 mb-2"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            通知中心
          </motion.h1>
          <motion.p
            className="text-slate-500"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            共 {total} 条通知
          </motion.p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={handleMarkAllAsRead}
            disabled={actionLoading !== null}
            className="flex items-center gap-2 px-4 py-2 bg-primary-50 hover:bg-primary-100 text-primary-600 font-medium rounded-xl transition-colors disabled:opacity-50"
          >
            <CheckCheck className="w-4 h-4" />
            全部已读
          </button>
          <button
            onClick={handleGoToSettings}
            className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-xl transition-colors"
          >
            <Settings className="w-4 h-4" />
            通知设置
          </button>
        </div>
      </div>

      {/* 消息提示 */}
      {message && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className={`mb-4 px-4 py-2 rounded-lg text-sm ${
            message.type === 'success' ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'
          }`}
        >
          {message.text}
        </motion.div>
      )}

      {/* 筛选器 */}
      <motion.div
        className="bg-white rounded-2xl shadow-sm p-4 mb-4"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="flex gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-sm text-slate-500">类型:</span>
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as NotificationType | '')}
              className="px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500"
            >
              <option value="">全部</option>
              {Object.entries(notificationTypeConfig).map(([type, config]) => (
                <option key={type} value={type}>
                  {config.label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm text-slate-500">状态:</span>
            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as NotificationStatus | '')}
              className="px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500"
            >
              <option value="">全部</option>
              <option value="UNREAD">未读</option>
              <option value="READ">已读</option>
            </select>
          </div>
        </div>
      </motion.div>

      {/* 通知列表 */}
      <motion.div
        className="bg-white rounded-2xl shadow-sm overflow-hidden"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
          </div>
        ) : notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mb-4">
              <Bell className="w-8 h-8 text-slate-400" />
            </div>
            <p className="text-slate-500">暂无通知</p>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {notifications.map((notification) => {
              const typeConfig = notificationTypeConfig[notification.type];
              const IconComponent = typeConfig.icon;
              const isUnread = notification.status === 'UNREAD';

              return (
                <motion.div
                  key={notification.id}
                  className={`p-4 hover:bg-slate-50 transition-colors cursor-pointer ${
                    isUnread ? 'bg-primary-50/50' : ''
                  }`}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                >
                  <div className="flex gap-4">
                    {/* 图标 */}
                    <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${typeConfig.color}`}>
                      <IconComponent className="w-5 h-5" />
                    </div>

                    {/* 内容 */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            {isUnread && <span className="w-2 h-2 bg-primary-500 rounded-full flex-shrink-0" />}
                            <h3 className={`font-medium ${isUnread ? 'text-slate-900' : 'text-slate-700'}`}>
                              {notification.title}
                            </h3>
                          </div>
                          <p className="text-sm text-slate-500 mt-1 line-clamp-2">{notification.content}</p>
                          <p className="text-xs text-slate-400 mt-2">{formatTime(notification.createdAt)}</p>
                        </div>

                        {/* 操作按钮 */}
                        <div className="flex items-center gap-1 flex-shrink-0" onClick={(e) => e.stopPropagation()}>
                          {isUnread && (
                            <button
                              onClick={(e) => handleMarkAsRead(notification.id, e)}
                              disabled={actionLoading === notification.id}
                              className="p-2 hover:bg-slate-200 rounded-lg transition-colors disabled:opacity-50"
                              title="标记已读"
                            >
                              <Check className="w-4 h-4 text-slate-500" />
                            </button>
                          )}
                          <button
                            onClick={(e) => handleDelete(notification.id, e)}
                            disabled={actionLoading === notification.id}
                            className="p-2 hover:bg-red-100 rounded-lg transition-colors disabled:opacity-50"
                            title="删除"
                          >
                            <Trash2 className="w-4 h-4 text-slate-500 hover:text-red-500" />
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </div>
        )}

        {/* 分页 */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100">
            <p className="text-sm text-slate-500">
              第 {page} / {totalPages} 页，共 {total} 条
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => handlePageChange(page - 1)}
                disabled={page === 1}
                className="p-2 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => handlePageChange(page + 1)}
                disabled={page === totalPages}
                className="p-2 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </motion.div>
    </motion.div>
  );
}
