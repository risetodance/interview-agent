import { Link, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  Settings,
  FileText,
  LogOut,
  Sparkles,
} from 'lucide-react';
import { useUser } from '../../store/user';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description?: string;
}

export default function AdminSidebar() {
  const location = useLocation();
  const currentPath = location.pathname;
  const { logout, user } = useUser();

  const handleLogout = () => {
    logout();
    window.location.href = '/login';
  };

  const navItems: NavItem[] = [
    {
      id: 'dashboard',
      path: '/admin',
      label: '仪表盘',
      icon: LayoutDashboard,
      description: '系统运行概览'
    },
    {
      id: 'users',
      path: '/admin/users',
      label: '用户管理',
      icon: Users,
      description: '审核和管理用户'
    },
    {
      id: 'config',
      path: '/admin/config',
      label: '系统配置',
      icon: Settings,
      description: '配置系统参数'
    },
    {
      id: 'audit-logs',
      path: '/admin/audit-logs',
      label: '审计日志',
      icon: FileText,
      description: '查看操作记录'
    },
  ];

  // 判断当前页面是否匹配导航项
  const isActive = (path: string) => {
    if (path === '/admin') {
      return currentPath === '/admin';
    }
    return currentPath.startsWith(path);
  };

  return (
    <aside className="w-64 bg-slate-800 fixed h-screen left-0 top-0 z-50 flex flex-col">
      {/* Logo */}
      <div className="p-6 border-b border-slate-700">
        <Link to="/admin" className="flex items-center gap-3">
          <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <span className="text-lg font-bold text-white tracking-tight block">AI Interview</span>
            <span className="text-xs text-slate-400">管理后台</span>
          </div>
        </Link>
      </div>

      {/* 导航菜单 */}
      <nav className="flex-1 p-4 overflow-y-auto">
        <div className="space-y-2">
          {navItems.map((item) => {
            const active = isActive(item.path);
            return (
              <Link
                key={item.id}
                to={item.path}
                className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                  ${active
                    ? 'bg-primary-500/20 text-primary-400'
                    : 'text-slate-300 hover:bg-slate-700 hover:text-white'
                  }`}
              >
                <div className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors
                  ${active
                    ? 'bg-primary-500/30 text-primary-400'
                    : 'bg-slate-700 text-slate-400 group-hover:bg-slate-600 group-hover:text-white'
                  }`}
                >
                  <item.icon className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                    {item.label}
                  </span>
                  {item.description && (
                    <span className="text-xs text-slate-500 truncate block">
                      {item.description}
                    </span>
                  )}
                </div>
              </Link>
            );
          })}
        </div>
      </nav>

      {/* 底部信息 */}
      <div className="p-4 border-t border-slate-700 space-y-2">
        {/* 返回用户端 */}
        <Link
          to="/upload"
          className="group w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-slate-300 hover:bg-slate-700 hover:text-white transition-all duration-200"
        >
          <div className="w-9 h-9 rounded-lg flex items-center justify-center bg-slate-700 text-slate-400 group-hover:bg-slate-600 group-hover:text-white transition-colors">
            <FileText className="w-5 h-5" />
          </div>
          <div className="flex-1 min-w-0">
            <span className="text-sm font-medium block">返回用户端</span>
            <span className="text-xs text-slate-500 truncate block">普通用户视图</span>
          </div>
        </Link>

        {/* 登出按钮 */}
        <button
          onClick={handleLogout}
          className="group w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-slate-300 hover:bg-red-500/20 hover:text-red-400 transition-all duration-200"
        >
          <div className="w-9 h-9 rounded-lg flex items-center justify-center bg-slate-700 text-slate-400 group-hover:bg-red-500/30 group-hover:text-red-400 transition-colors">
            <LogOut className="w-5 h-5" />
          </div>
          <div className="flex-1 min-w-0">
            <span className="text-sm font-medium block">退出登录</span>
            {user && <span className="text-xs text-slate-500 truncate block">{user.username}</span>}
          </div>
        </button>

        <div className="px-3 py-2 bg-gradient-to-r from-primary-500/20 to-purple-500/20 rounded-xl">
          <p className="text-xs text-primary-400 font-medium">AI 面试助手 v1.0</p>
          <p className="text-xs text-slate-500 mt-0.5">管理后台</p>
        </div>
      </div>
    </aside>
  );
}
