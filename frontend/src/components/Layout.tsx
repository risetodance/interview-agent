import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Bot,
  FileUp,
  Folder,
  ClipboardList,
  Library,
  MessageCircle,
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
        { id: 'upload', path: '/upload', label: '上传简历', icon: FileUp, description: 'AI智能分析简历' },
        { id: 'resumes', path: '/history', label: '简历库', icon: Folder, description: '查看已分析简历' },
        { id: 'interviews', path: '/interviews', label: '面试记录', icon: ClipboardList, description: '历史面试回顾' },
        { id: 'questions', path: '/questions', label: '题库管理', icon: BookOpen, description: '管理面试题库' },
      ],
    },
    {
      id: 'knowledge',
      title: '知识库',
      items: [
        { id: 'kb-manage', path: '/knowledgebase', label: '知识库', icon: Library, description: '管理知识库文档' },
        { id: 'chat', path: '/knowledgebase/chat', label: '问答助手', icon: MessageCircle, description: '基于知识库回答' },
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
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 to-sky-50">
      {/* 左侧边栏 */}
        <aside className="w-64 bg-white border-r border-gray-100 fixed h-screen left-0 top-0 z-50 flex flex-col shadow-lg">
        {/* Logo */}
        <div className="p-5 border-b border-gray-100 flex items-center justify-between">
          <Link to="/upload" className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-sky-400 to-sky-500 rounded-xl flex items-center justify-center text-white shadow-lg shadow-sky-500/30">
              <Bot className="w-5 h-5" />
            </div>
            <div>
              <span className="text-lg font-bold text-gray-800 tracking-tight block">AI Assistant</span>
              <span className="text-xs text-gray-400">智能面试助手</span>
            </div>
          </Link>
          <NotificationBell />
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 overflow-y-auto">
          <div className="space-y-5">
            {/* 管理员后台入口 */}
            {adminNav && (
              <div>
                <div className="px-3 mb-2">
                  <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">
                    {adminNav.title}
                  </span>
                </div>
                <div className="space-y-0.5">
                  {adminNav.items.map((item) => {
                    const active = isActive(item.path);
                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200
                          ${active
                            ? 'bg-sky-50 border-l-[3px] border-sky-500 text-sky-600'
                            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900 border-l-[3px] border-transparent'
                          }`}
                      >
                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-sky-100 text-sky-600'
                            : 'bg-gray-100 text-gray-500 group-hover:bg-gray-200 group-hover:text-gray-700'
                          }`}
                        >
                          <item.icon className="w-4 h-4" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-gray-400 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && (
                          <ChevronRight className="w-4 h-4 text-sky-400" />
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
                <div className="px-3 mb-2 mt-4 first:mt-0">
                  <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">
                    {group.title}
                  </span>
                </div>
                {/* 分组下的导航项 */}
                <div className="space-y-0.5">
                  {group.items.map((item) => {
                    const active = isActive(item.path);
                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200
                          ${active
                            ? 'bg-sky-50 border-l-[3px] border-sky-500 text-sky-600'
                            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900 border-l-[3px] border-transparent'
                          }`}
                      >
                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-sky-100 text-sky-600'
                            : 'bg-gray-100 text-gray-500 group-hover:bg-gray-200 group-hover:text-gray-700'
                          }`}
                        >
                          <item.icon className="w-4 h-4" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-gray-400 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && (
                          <ChevronRight className="w-4 h-4 text-sky-400" />
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
        <div className="p-4 border-t border-gray-100 space-y-2">
          {/* 登出按钮 */}
          <button
            onClick={handleLogoutClick}
            className="group w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-gray-600 hover:bg-red-50 hover:text-red-600 transition-all duration-200"
          >
            <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-gray-100 text-gray-500 group-hover:bg-red-100 group-hover:text-red-600 transition-colors">
              <LogOut className="w-4 h-4" />
            </div>
            <div className="flex-1 min-w-0">
              <span className="text-sm font-medium block">退出登录</span>
              {user && <span className="text-xs text-gray-400 truncate block">{user.username}</span>}
            </div>
          </button>

          <div className="px-3 py-3 bg-gradient-to-r from-sky-50 to-sky-100/50 rounded-xl border border-gray-100">
            <p className="text-xs text-sky-600 font-medium">AI驱动的高效面试</p>
            <p className="text-xs text-gray-400 mt-0.5">Powered by AI</p>
          </div>
        </div>
      </aside>

      {/* 主内容区 */}
      <main className="flex-1 ml-64 flex flex-col min-h-screen relative">
        {/* 背景装饰 - 玻璃态圆形 */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-20 left-1/4 w-72 h-72 bg-sky-200/40 rounded-full blur-3xl animate-pulse" />
          <div className="absolute bottom-20 right-20 w-96 h-96 bg-sky-300/30 rounded-full blur-3xl animate-pulse animation-delay-2000" />
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-sky-100/20 rounded-full blur-3xl" />
        </div>

        {/* 装饰性圆点 - 动画效果 */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-32 right-32 w-3 h-3 bg-sky-400/60 rounded-full animate-float-1" />
          <div className="absolute top-48 right-48 w-2 h-2 bg-sky-300/80 rounded-full animate-float-2" />
          <div className="absolute bottom-40 left-1/3 w-4 h-4 bg-sky-400/50 rounded-full animate-float-3" />
          <div className="absolute bottom-60 left-48 w-2 h-2 bg-sky-300/70 rounded-full animate-float-4" />
          <div className="absolute top-40 left-1/4 w-2 h-2 bg-sky-400/40 rounded-full animate-float-5" />
          <div className="absolute bottom-32 right-1/3 w-3 h-3 bg-sky-400/50 rounded-full animate-float-6" />
        </div>

        {/* 页面内容 */}
        <div className="relative w-full h-full p-10 overflow-auto">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
