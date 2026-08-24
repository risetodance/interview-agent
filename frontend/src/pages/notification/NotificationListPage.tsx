import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Check,
  CheckCheck,
  Trash2,
  Settings,
  ChevronLeft,
  ChevronRight,
  Loader2,
} from 'lucide-react';
import {
  notificationApi,
  Notification,
  NotificationType,
  NotificationStatus,
} from '../../api/notification';
import { getErrorMessage } from '../../api/request';

// 通知类型映射
const notificationTypeConfig: Record<NotificationType, { label: string }> = {
  SYSTEM: { label: '系统通知' },
  INTERVIEW: { label: '面试通知' },
  RESUME: { label: '简历通知' },
  KNOWLEDGEBASE: { label: '知识库通知' },
  MEMBERSHIP: { label: '会员通知' },
};

// 通知类型 chip（中性色，非状态语义）
const typeChipCls = 'text-zinc-500 bg-zinc-50 border-zinc-200';

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
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">通知中心</h1>
          <p className="mt-1 text-sm text-zinc-500">
            共 <span className="font-mono tabular-nums text-zinc-700">{total}</span> 条通知
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={handleMarkAllAsRead}
            disabled={actionLoading !== null}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-2"
          >
            <CheckCheck className="w-4 h-4" />
            全部已读
          </button>
          <button
            onClick={handleGoToSettings}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors inline-flex items-center gap-2"
          >
            <Settings className="w-4 h-4" />
            通知设置
          </button>
        </div>
      </div>

      {/* 消息提示 */}
      {message && (
        <div
          className={`mb-4 border rounded-md px-3 py-2.5 text-sm fade-in ${
            message.type === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-red-200 bg-red-50 text-red-700'
          }`}
        >
          {message.text}
        </div>
      )}

      {/* 筛选器 */}
      <div className="bg-white border border-zinc-200 rounded-lg px-5 py-4 mb-4">
        <div className="flex gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-zinc-600">类型</span>
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as NotificationType | '')}
              className="h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
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
            <span className="text-xs font-medium text-zinc-600">状态</span>
            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as NotificationStatus | '')}
              className="h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
            >
              <option value="">全部</option>
              <option value="UNREAD">未读</option>
              <option value="READ">已读</option>
            </select>
          </div>
        </div>
      </div>

      {/* 通知列表 */}
      <div className="bg-white border border-zinc-200 rounded-lg overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-5 h-5 text-zinc-400 animate-spin" />
          </div>
        ) : notifications.length === 0 ? (
          <p className="px-5 py-8 text-xs text-zinc-400 text-center">暂无通知</p>
        ) : (
          <div className="divide-y divide-zinc-100">
            {notifications.map((notification) => {
              const typeConfig = notificationTypeConfig[notification.type];
              const isUnread = notification.status === 'UNREAD';

              return (
                <div
                  key={notification.id}
                  className={`px-5 py-3.5 hover:bg-zinc-50 transition-colors ${
                    isUnread ? 'bg-primary-50/50' : ''
                  }`}
                >
                  <div className="flex gap-4">
                    {/* 内容 */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            {isUnread && (
                              <span className="w-1.5 h-1.5 rounded-full bg-primary-600 shrink-0" />
                            )}
                            <span
                              className={`text-xs border rounded px-1.5 py-0.5 shrink-0 ${typeChipCls}`}
                            >
                              {typeConfig.label}
                            </span>
                            <h3
                              className={`text-sm truncate ${
                                isUnread ? 'font-medium text-zinc-900' : 'text-zinc-600'
                              }`}
                            >
                              {notification.title}
                            </h3>
                          </div>
                          <p className="text-xs text-zinc-500 mt-1.5 line-clamp-2">
                            {notification.content}
                          </p>
                          <p className="font-mono text-xs text-zinc-400 mt-1.5">
                            {formatTime(notification.createdAt)}
                          </p>
                        </div>

                        {/* 操作按钮 */}
                        <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
                          {isUnread && (
                            <button
                              onClick={(e) => handleMarkAsRead(notification.id, e)}
                              disabled={actionLoading === notification.id}
                              className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors disabled:opacity-50"
                              title="标记已读"
                            >
                              <Check className="w-4 h-4" />
                            </button>
                          )}
                          <button
                            onClick={(e) => handleDelete(notification.id, e)}
                            disabled={actionLoading === notification.id}
                            className="p-1.5 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors disabled:opacity-50"
                            title="删除"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* 分页 */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-zinc-100">
            <p className="text-xs text-zinc-500">
              第 <span className="font-mono text-zinc-700 tabular-nums">{page}</span> /{' '}
              <span className="font-mono text-zinc-700 tabular-nums">{totalPages}</span> 页 · 共{' '}
              <span className="font-mono text-zinc-700 tabular-nums">{total}</span> 条
            </p>
            <div className="flex items-center gap-1">
              <button
                onClick={() => handlePageChange(page - 1)}
                disabled={page === 1}
                className="h-8 w-8 flex items-center justify-center border border-zinc-300 rounded-md text-zinc-500 hover:bg-zinc-50 hover:border-zinc-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                aria-label="上一页"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => handlePageChange(page + 1)}
                disabled={page === totalPages}
                className="h-8 w-8 flex items-center justify-center border border-zinc-300 rounded-md text-zinc-500 hover:bg-zinc-50 hover:border-zinc-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                aria-label="下一页"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
