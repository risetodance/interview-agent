import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Sparkles,
  Upload,
  FileStack,
  Users,
  Database,
  MessageSquare,
  ChevronRight,
  User,
  BookOpen,
  Crown,
  LogOut,
  Settings,
} from 'lucide-react';
import { useUser } from '../store/user';
import NotificationBell from './notification/NotificationBell';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description?: string;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
}

export default function Layout() {
  const location = useLocation();
  const currentPath = location.pathname;
  const navigate = useNavigate();
  const { logout, user } = useUser();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // 按业务模块组织的导航项
  const navGroups: NavGroup[] = [
    {
      id: 'career',
      title: '简历与面试',
      items: [
        { id: 'upload', path: '/upload', label: '上传简历', icon: Upload, description: 'AI 分析简历' },
        { id: 'resumes', path: '/history', label: '简历库', icon: FileStack, description: '管理所有简历' },
        { id: 'interviews', path: '/interviews', label: '面试记录', icon: Users, description: '查看面试历史' },
        { id: 'questions', path: '/questions', label: '题库管理', icon: BookOpen, description: '管理面试题库' },
      ],
    },
    {
      id: 'knowledge',
      title: '知识库',
      items: [
        { id: 'kb-manage', path: '/knowledgebase', label: '知识库管理', icon: Database, description: '管理知识文档' },
        { id: 'chat', path: '/knowledgebase/chat', label: '问答助手', icon: MessageSquare, description: '基于知识库问答' },
      ],
    },
    {
      id: 'account',
      title: '账号',
      items: [
        { id: 'membership', path: '/membership', label: '会员中心', icon: Crown, description: 'VIP 会员与积分' },
        { id: 'profile', path: '/profile', label: '个人中心', icon: User, description: '查看和编辑个人资料' },
      ],
    },
  ];

  // 如果是管理员，添加后台管理入口
  const isAdmin = user?.role === 'ADMIN';
  const adminNav: NavGroup | null = isAdmin ? {
    id: 'admin',
    title: '系统管理',
    items: [
      { id: 'admin', path: '/admin', label: '管理后台', icon: Settings, description: '系统管理入口' },
    ],
  } : null;

  // 登出按钮点击处理
  const handleLogoutClick = (e: React.MouseEvent) => {
    e.preventDefault();
    handleLogout();
  };

  // 判断当前页面是否匹配导航项
  const isActive = (path: string) => {
    if (path === '/upload') {
      return currentPath === '/upload' || currentPath === '/';
    }
    if (path === '/knowledgebase') {
      return currentPath === '/knowledgebase' || currentPath === '/knowledgebase/upload';
    }
    if (path === '/questions') {
      return currentPath.startsWith('/questions');
    }
    if (path === '/membership') {
      return currentPath.startsWith('/membership');
    }
    if (path === '/notifications') {
      return currentPath.startsWith('/notifications');
    }
    return currentPath.startsWith(path);
  };

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50">
      {/* 左侧边栏 */}
      <aside className="w-64 bg-white border-r border-slate-100 fixed h-screen left-0 top-0 z-50 flex flex-col">
        {/* Logo */}
        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
          <Link to="/upload" className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <span className="text-lg font-bold text-slate-800 tracking-tight block">AI Interview</span>
              <span className="text-xs text-slate-400">智能面试助手</span>
            </div>
          </Link>
          <NotificationBell />
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 overflow-y-auto">
          <div className="space-y-6">
            {/* 管理员后台入口 */}
            {adminNav && (
              <div>
                <div className="px-3 mb-2">
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    {adminNav.title}
                  </span>
                </div>
                <div className="space-y-1">
                  {adminNav.items.map((item) => {
                    const active = isActive(item.path);
                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                          ${active
                            ? 'bg-primary-50 text-primary-600'
                            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                          }`}
                      >
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-primary-100 text-primary-600'
                            : 'bg-slate-100 text-slate-500 group-hover:bg-slate-200 group-hover:text-slate-700'
                          }`}
                        >
                          <item.icon className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-slate-400 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && (
                          <ChevronRight className="w-4 h-4 text-primary-400" />
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            )}
            {navGroups.map((group) => (
              <div key={group.id}>
                {/* 分组标题 */}
                <div className="px-3 mb-2">
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    {group.title}
                  </span>
                </div>
                {/* 分组下的导航项 */}
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const active = isActive(item.path);
                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                          ${active
                            ? 'bg-primary-50 text-primary-600'
                            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                          }`}
                      >
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-primary-100 text-primary-600'
                            : 'bg-slate-100 text-slate-500 group-hover:bg-slate-200 group-hover:text-slate-700'
                          }`}
                        >
                          <item.icon className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-slate-400 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && (
                          <ChevronRight className="w-4 h-4 text-primary-400" />
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </nav>

        {/* 底部信息 */}
        <div className="p-4 border-t border-slate-100 space-y-2">
          {/* 登出按钮 */}
          <button
            onClick={handleLogoutClick}
            className="group w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-slate-600 hover:bg-red-50 hover:text-red-600 transition-all duration-200"
          >
            <div className="w-9 h-9 rounded-lg flex items-center justify-center bg-slate-100 text-slate-500 group-hover:bg-red-100 group-hover:text-red-600 transition-colors">
              <LogOut className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <span className="text-sm font-medium block">退出登录</span>
              {user && <span className="text-xs text-slate-400 truncate block">{user.username}</span>}
            </div>
          </button>

          <div className="px-3 py-2 bg-gradient-to-r from-primary-50 to-indigo-50 rounded-xl">
            <p className="text-xs text-primary-600 font-medium">AI 面试助手 v1.0</p>
            <p className="text-xs text-slate-400 mt-0.5">Powered by AI</p>
          </div>
        </div>
      </aside>

      {/* 主内容区 */}
      <main className="flex-1 ml-64 p-10 min-h-screen overflow-y-auto">
        <motion.div
          key={currentPath}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          transition={{ duration: 0.3 }}
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
