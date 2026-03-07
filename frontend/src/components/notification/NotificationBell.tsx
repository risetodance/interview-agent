import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { notificationApi } from '../../api/notification';

interface NotificationBellProps {
  className?: string;
}

export default function NotificationBell({ className = '' }: NotificationBellProps) {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);

  // 加载未读数量
  useEffect(() => {
    const loadUnreadCount = async () => {
      try {
        const response = await notificationApi.getUnreadCount();
        setUnreadCount(response.unreadCount);
      } catch (error) {
        console.error('获取未读通知数量失败', error);
      } finally {
        setLoading(false);
      }
    };

    loadUnreadCount();

    // 定时刷新（每分钟）
    const interval = setInterval(loadUnreadCount, 60000);
    return () => clearInterval(interval);
  }, []);

  // 点击跳转到通知列表
  const handleClick = () => {
    navigate('/notifications');
  };

  return (
    <button
      onClick={handleClick}
      className={`relative p-2 hover:bg-slate-100 rounded-xl transition-colors ${className}`}
      title="通知中心"
    >
      <Bell className="w-5 h-5 text-slate-600" />
      {!loading && unreadCount > 0 && (
        <span
          className={`absolute -top-1 -right-1 min-w-[18px] h-[18px] flex items-center justify-center px-1 bg-red-500 text-white text-xs font-medium rounded-full ${
            unreadCount > 99 ? 'px-1' : ''
          }`}
        >
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      )}
    </button>
  );
}
