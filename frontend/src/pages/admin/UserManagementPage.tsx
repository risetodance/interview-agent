import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Users,
  Search,
  Check,
  X,
  Ban,
  Play,
  Loader2,
  ChevronDown,
} from 'lucide-react';
import {
  adminApi,
  AdminUser,
  UserQueryParams,
} from '../../api/admin';

// 状态标签组件
function StatusBadge({ status }: { status: AdminUser['status'] }) {
  const config: Record<string, { label: string; className: string }> = {
    ACTIVE: { label: '正常', className: 'bg-green-100 text-green-700' },
    BANNED: { label: '已禁用', className: 'bg-slate-100 text-slate-700' },
    PENDING: { label: '待审核', className: 'bg-amber-100 text-amber-700' },
    APPROVED: { label: '已通过', className: 'bg-green-100 text-green-700' },
    REJECTED: { label: '已拒绝', className: 'bg-red-100 text-red-700' },
    DISABLED: { label: '已禁用', className: 'bg-slate-100 text-slate-700' },
  };

  const { label, className } = config[status] || config.PENDING;

  return (
    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}

// 格式化日期
function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// 用户角色标签
function RoleBadge({ role }: { role: string }) {
  const isAdmin = role === 'ADMIN';
  return (
    <span
      className={`px-2 py-0.5 rounded text-xs font-medium ${
        isAdmin
          ? 'bg-purple-100 text-purple-700'
          : 'bg-slate-100 text-slate-600'
      }`}
    >
      {role}
    </span>
  );
}

export default function UserManagementPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);

  // 操作状态
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  const loadUsers = useCallback(async () => {
    try {
      setLoading(true);
      const params: UserQueryParams = {
        page: currentPage,
        size: pageSize,
      };
      if (statusFilter) {
        params.status = statusFilter;
      }
      if (searchKeyword) {
        params.keyword = searchKeyword;
      }
      const data = await adminApi.getUsers(params);
      setUsers(data.content);
      setTotalElements(data.totalElements);
    } catch (error) {
      console.error('加载用户列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, statusFilter, searchKeyword]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  // 审核通过
  const handleApprove = async (userId: number) => {
    try {
      setActionLoading(userId);
      await adminApi.approveUser(userId);
      await loadUsers();
    } catch (error) {
      console.error('审核通过失败:', error);
    } finally {
      setActionLoading(null);
    }
  };

  // 审核拒绝
  const handleReject = async (userId: number) => {
    try {
      setActionLoading(userId);
      await adminApi.rejectUser(userId);
      await loadUsers();
    } catch (error) {
      console.error('审核拒绝失败:', error);
    } finally {
      setActionLoading(null);
    }
  };

  // 禁用用户
  const handleDisable = async (userId: number) => {
    try {
      setActionLoading(userId);
      await adminApi.disableUser(userId);
      await loadUsers();
    } catch (error) {
      console.error('禁用用户失败:', error);
    } finally {
      setActionLoading(null);
    }
  };

  // 启用用户
  const handleEnable = async (userId: number) => {
    try {
      setActionLoading(userId);
      await adminApi.enableUser(userId);
      await loadUsers();
    } catch (error) {
      console.error('启用用户失败:', error);
    } finally {
      setActionLoading(null);
    }
  };

  // 搜索处理
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0);
    loadUsers();
  };

  // 分页
  const totalPages = Math.ceil(totalElements / pageSize);

  return (
    <div className="max-w-7xl mx-auto">
      {/* 页面标题 */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
          <Users className="w-7 h-7 text-primary-500" />
          用户管理
        </h1>
        <p className="text-slate-500 mt-1">审核和管理平台用户</p>
      </div>

      {/* 搜索和筛选栏 */}
      <div className="bg-white rounded-xl p-4 shadow-sm border border-slate-100 mb-6">
        <div className="flex flex-wrap items-center gap-4">
          {/* 搜索框 */}
          <form onSubmit={handleSearch} className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="text"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder="搜索用户名或邮箱..."
                className="w-full pl-10 pr-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>
          </form>

          {/* 状态筛选 */}
          <div className="relative">
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setCurrentPage(0);
              }}
              className="appearance-none pl-4 pr-10 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white cursor-pointer"
            >
              <option value="">全部状态</option>
              <option value="ACTIVE">正常</option>
              <option value="PENDING">待审核</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已拒绝</option>
              <option value="BANNED">已禁用</option>
              <option value="DISABLED">已禁用(旧)</option>
            </select>
            <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          </div>
        </div>
      </div>

      {/* 用户列表 */}
      <div className="bg-white border border-slate rounded-xl shadow-sm-100 overflow-x-auto">
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
          </div>
        ) : users.length === 0 ? (
          <div className="text-center py-20">
            <Users className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-500">暂无用户</p>
          </div>
        ) : (
          <>
            <table className="w-full">
              <thead className="bg-slate-50 border-b border-slate-100">
                <tr>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    用户
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    角色
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    状态
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    积分
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    会员
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-slate-600">
                    注册时间
                  </th>
                  <th className="text-right px-4 py-3 text-sm font-medium text-slate-600">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user, index) => (
                  <motion.tr
                    key={user.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.05 }}
                    className="border-b border-slate-50 hover:bg-slate-50 transition-colors"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-primary-100 flex items-center justify-center text-primary-600 font-medium">
                          {user.nickname?.[0] || user.username[0]}
                        </div>
                        <div>
                          <p className="font-medium text-slate-800">{user.nickname || user.username}</p>
                          <p className="text-xs text-slate-400">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <RoleBadge role={user.role} />
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={user.status} />
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-600">
                      {user.points.toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-600">
                      {user.membership}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-500 whitespace-nowrap">
                      {formatDate(user.createdAt)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <AnimatePresence mode="wait">
                          {user.status === 'PENDING' && (
                            <>
                              <button
                                onClick={() => handleApprove(user.id)}
                                disabled={actionLoading === user.id}
                                className="flex items-center gap-1 px-2.5 py-1.5 text-sm text-green-600 hover:bg-green-50 rounded-lg transition-colors disabled:opacity-50"
                                title="审核通过"
                              >
                                {actionLoading === user.id ? (
                                  <Loader2 className="w-4 h-4 animate-spin" />
                                ) : (
                                  <Check className="w-4 h-4" />
                                )}
                                <span>通过</span>
                              </button>
                              <button
                                onClick={() => handleReject(user.id)}
                                disabled={actionLoading === user.id}
                                className="flex items-center gap-1 px-2.5 py-1.5 text-sm text-red-600 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                                title="审核拒绝"
                              >
                                <X className="w-4 h-4" />
                                <span>拒绝</span>
                              </button>
                            </>
                          )}
                          {(user.status === 'ACTIVE' || user.status === 'APPROVED') && (
                            <button
                              onClick={() => handleDisable(user.id)}
                              disabled={actionLoading === user.id}
                              className="flex items-center gap-1 px-2.5 py-1.5 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
                              title="禁用用户"
                            >
                              {actionLoading === user.id ? (
                                <Loader2 className="w-4 h-4 animate-spin" />
                              ) : (
                                <Ban className="w-4 h-4" />
                              )}
                              <span>禁用</span>
                            </button>
                          )}
                          {(user.status === 'REJECTED' || user.status === 'DISABLED' || user.status === 'BANNED') && (
                            <button
                              onClick={() => handleEnable(user.id)}
                              disabled={actionLoading === user.id}
                              className="flex items-center gap-1 px-2.5 py-1.5 text-sm text-green-600 hover:bg-green-50 rounded-lg transition-colors disabled:opacity-50"
                              title="启用用户"
                            >
                              {actionLoading === user.id ? (
                                <Loader2 className="w-4 h-4 animate-spin" />
                              ) : (
                                <Play className="w-4 h-4" />
                              )}
                              <span>启用</span>
                            </button>
                          )}
                        </AnimatePresence>
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
