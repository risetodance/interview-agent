import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  FileText,
  Search,
  Loader2,
  ChevronDown,
  Calendar,
  User,
  Globe,
} from 'lucide-react';
import { adminApi, AuditLog } from '../../api/admin';

// 格式化日期时间
function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

// 动作类型标签
function ActionBadge({ action }: { action: string }) {
  const getActionColor = (action: string) => {
    if (action.includes('CREATE') || action.includes('APPROVE')) {
      return 'bg-green-100 text-green-700';
    }
    if (action.includes('UPDATE') || action.includes('EDIT')) {
      return 'bg-blue-100 text-blue-700';
    }
    if (action.includes('DELETE') || action.includes('REJECT') || action.includes('DISABLE')) {
      return 'bg-red-100 text-red-700';
    }
    if (action.includes('LOGIN') || action.includes('LOGOUT')) {
      return 'bg-purple-100 text-purple-700';
    }
    return 'bg-slate-100 text-slate-700';
  };

  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${getActionColor(action)}`}>
      {action}
    </span>
  );
}

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(20);

  // 筛选条件
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [actionFilter, setActionFilter] = useState('');

  const loadLogs = useCallback(async () => {
    try {
      setLoading(true);
      const params: {
        page: number;
        size: number;
        startDate?: string;
        endDate?: string;
        action?: string;
      } = {
        page: currentPage,
        size: pageSize,
      };
      if (startDate) {
        params.startDate = startDate;
      }
      if (endDate) {
        params.endDate = endDate;
      }
      if (actionFilter) {
        params.action = actionFilter;
      }
      const data = await adminApi.getAuditLogs(params);
      setLogs(data.content);
      setTotalElements(data.totalElements);
    } catch (error) {
      console.error('加载审计日志失败:', error);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, startDate, endDate, actionFilter]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  // 搜索处理
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0);
    loadLogs();
  };

  // 清除筛选
  const handleClearFilter = () => {
    setStartDate('');
    setEndDate('');
    setActionFilter('');
    setCurrentPage(0);
    loadLogs();
  };

  // 分页
  const totalPages = Math.ceil(totalElements / pageSize);

  // 动作类型选项
  const actionOptions = [
    'USER_CREATE',
    'USER_UPDATE',
    'USER_APPROVE',
    'USER_REJECT',
    'USER_DISABLE',
    'USER_ENABLE',
    'LOGIN',
    'LOGOUT',
    'CONFIG_UPDATE',
  ];

  return (
    <div className="max-w-7xl mx-auto">
      {/* 页面标题 */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
          <FileText className="w-7 h-7 text-primary-500" />
          审计日志
        </h1>
        <p className="text-slate-500 mt-1">查看系统操作记录和用户行为</p>
      </div>

      {/* 筛选栏 */}
      <div className="bg-white rounded-xl p-4 shadow-sm border border-slate-100 mb-6">
        <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-4">
          {/* 开始日期 */}
          <div>
            <label className="block text-sm text-slate-600 mb-1">开始日期</label>
            <div className="relative">
              <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="pl-10 pr-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>

          {/* 结束日期 */}
          <div>
            <label className="block text-sm text-slate-600 mb-1">结束日期</label>
            <div className="relative">
              <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="pl-10 pr-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>

          {/* 操作类型 */}
          <div>
            <label className="block text-sm text-slate-600 mb-1">操作类型</label>
            <div className="relative">
              <select
                value={actionFilter}
                onChange={(e) => setActionFilter(e.target.value)}
                className="appearance-none pl-4 pr-10 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white cursor-pointer"
              >
                <option value="">全部操作</option>
                {actionOptions.map((action) => (
                  <option key={action} value={action}>
                    {action}
                  </option>
                ))}
              </select>
              <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
            </div>
          </div>

          {/* 搜索按钮 */}
          <div className="flex items-center gap-2">
            <button
              type="submit"
              className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
            >
              <Search className="w-4 h-4" />
              搜索
            </button>
            <button
              type="button"
              onClick={handleClearFilter}
              className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              清除
            </button>
          </div>
        </form>
      </div>

      {/* 日志列表 */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-x-auto">
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
          </div>
        ) : logs.length === 0 ? (
          <div className="text-center py-20">
            <FileText className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-500">暂无日志记录</p>
          </div>
        ) : (
          <>
            <table className="w-full">
              <thead className="bg-slate-50 border-b border-slate-100">
                <tr>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    时间
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    用户
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    操作
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    资源
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    请求方法
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    IP 地址
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    详情
                  </th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log, index) => (
                  <motion.tr
                    key={log.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.03 }}
                    className="border-b border-slate-50 hover:bg-slate-50 transition-colors"
                  >
                    <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">
                      {formatDateTime(log.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <User className="w-4 h-4 text-slate-400" />
                        <span className="text-sm text-slate-800">{log.username}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <ActionBadge action={log.action} />
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-600">
                      {log.resource}
                    </td>
                    <td className="px-4 py-3">
                      <span className="px-2 py-0.5 bg-slate-100 text-slate-600 rounded text-xs font-mono">
                        {log.method}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <Globe className="w-4 h-4 text-slate-400" />
                        <span className="text-sm text-slate-600 font-mono">{log.ip}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="max-w-xs">
                        <p className="text-sm text-slate-600 truncate" title={log.details}>
                          {log.details || '-'}
                        </p>
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </tbody>
            </table>

            {/* 分页 */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100">
                <p className="text-sm text-slate-500">
                  共 {totalElements} 条记录，第 {currentPage + 1}/{totalPages} 页
                </p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                    disabled={currentPage === 0}
                    className="px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    上一页
                  </button>
                  <button
                    onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={currentPage === totalPages - 1}
                    className="px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    下一页
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
