import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  LayoutDashboard,
  Users,
  Settings,
  FileText,
  Clock,
  TrendingUp,
  Loader2,
} from 'lucide-react';
import {
  adminApi,
  DashboardStats,
  RecentActivity,
} from '../../api/admin';

// 统计卡片组件
function StatCard({
  icon: Icon,
  label,
  value,
  color,
  trend,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  color: string;
  trend?: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white rounded-xl p-6 shadow-sm border border-slate-100"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className={`p-3 rounded-lg ${color}`}>
            <Icon className="w-6 h-6 text-white" />
          </div>
          <div>
            <p className="text-sm text-slate-500">{label}</p>
            <p className="text-2xl font-bold text-slate-800">{value.toLocaleString()}</p>
          </div>
        </div>
        {trend && (
          <div className="flex items-center gap-1 text-green-600 text-sm">
            <TrendingUp className="w-4 h-4" />
            <span>{trend}</span>
          </div>
        )}
      </div>
    </motion.div>
  );
}

// 活动类型图标
function getActivityIcon(type: RecentActivity['type']) {
  switch (type) {
    case 'USER':
      return Users;
    case 'INTERVIEW':
      return FileText;
    case 'RESUME':
      return FileText;
    case 'KNOWLEDGEBASE':
      return FileText;
    default:
      return Clock;
  }
}

// 格式化日期
function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;

  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  });
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [activities, setActivities] = useState<RecentActivity[]>([]);
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async () => {
    try {
      const [statsData, activitiesData] = await Promise.all([
        adminApi.getDashboardStats(),
        adminApi.getRecentActivities(10),
      ]);
      setStats(statsData);
      setActivities(activitiesData);
    } catch (error) {
      console.error('加载仪表盘数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto">
      {/* 页面标题 */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
          <LayoutDashboard className="w-7 h-7 text-primary-500" />
          仪表盘
        </h1>
        <p className="text-slate-500 mt-1">系统运行概览和数据统计</p>
      </div>

      {/* 统计卡片 */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          <StatCard
            icon={Users}
            label="用户总数"
            value={stats.totalUsers}
            color="bg-primary-500"
          />
          <StatCard
            icon={Users}
            label="活跃用户"
            value={stats.activeUsers}
            color="bg-emerald-500"
          />
          <StatCard
            icon={Clock}
            label="待审核用户"
            value={stats.pendingUsers}
            color="bg-amber-500"
            trend="待处理"
          />
          <StatCard
            icon={FileText}
            label="简历总数"
            value={stats.totalResumes}
            color="bg-indigo-500"
          />
          <StatCard
            icon={FileText}
            label="面试记录"
            value={stats.totalInterviews}
            color="bg-blue-500"
          />
          <StatCard
            icon={FileText}
            label="知识库"
            value={stats.totalKnowledgeBases}
            color="bg-purple-500"
          />
        </div>
      )}

      {/* 最近活动 */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100">
        <div className="p-6 border-b border-slate-100">
          <h2 className="text-lg font-semibold text-slate-800">最近活动</h2>
        </div>
        <div className="divide-y divide-slate-50">
          {activities.length === 0 ? (
            <div className="text-center py-12 text-slate-400">
              暂无最近活动
            </div>
          ) : (
            activities.map((activity, index) => {
              const Icon = getActivityIcon(activity.type);
              return (
                <motion.div
                  key={activity.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.05 }}
                  className="flex items-center gap-4 px-6 py-4 hover:bg-slate-50 transition-colors"
                >
                  <div className="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center">
                    <Icon className="w-5 h-5 text-slate-500" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-slate-800 truncate">{activity.description}</p>
                    <p className="text-xs text-slate-400">
                      {activity.action} · {activity.type}
                    </p>
                  </div>
                  <div className="text-sm text-slate-400 whitespace-nowrap">
                    {formatDate(activity.createdAt)}
                  </div>
                </motion.div>
              );
            })
          )}
        </div>
      </div>

      {/* 快捷操作 */}
      <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
        <button
          onClick={() => navigate('/admin/users')}
          className="flex items-center gap-4 p-6 bg-white rounded-xl shadow-sm border border-slate-100 hover:border-primary-200 hover:shadow-md transition-all"
        >
          <div className="w-12 h-12 rounded-lg bg-primary-50 flex items-center justify-center">
            <Users className="w-6 h-6 text-primary-500" />
          </div>
          <div className="text-left">
            <h3 className="font-semibold text-slate-800">用户管理</h3>
            <p className="text-sm text-slate-500">审核和管理用户</p>
          </div>
        </button>

        <button
          onClick={() => navigate('/admin/config')}
          className="flex items-center gap-4 p-6 bg-white rounded-xl shadow-sm border border-slate-100 hover:border-primary-200 hover:shadow-md transition-all"
        >
          <div className="w-12 h-12 rounded-lg bg-emerald-50 flex items-center justify-center">
            <Settings className="w-6 h-6 text-emerald-500" />
          </div>
          <div className="text-left">
            <h3 className="font-semibold text-slate-800">系统配置</h3>
            <p className="text-sm text-slate-500">配置系统参数</p>
          </div>
        </button>

        <button
          onClick={() => navigate('/admin/audit-logs')}
          className="flex items-center gap-4 p-6 bg-white rounded-xl shadow-sm border border-slate-100 hover:border-primary-200 hover:shadow-md transition-all"
        >
          <div className="w-12 h-12 rounded-lg bg-amber-50 flex items-center justify-center">
            <FileText className="w-6 h-6 text-amber-500" />
          </div>
          <div className="text-left">
            <h3 className="font-semibold text-slate-800">审计日志</h3>
            <p className="text-sm text-slate-500">查看系统操作记录</p>
          </div>
        </button>
      </div>
    </div>
  );
}
