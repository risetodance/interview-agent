import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  User,
  Crown,
  Settings,
  LogOut,
  ChevronDown,
} from 'lucide-react';
import { useUser } from '../store/user';
import NotificationBell from './notification/NotificationBell';
import BrandMark from './BrandMark';

interface NavItem {
  id: string;
  path: string;
  label: string;
}

/**
 * 用户端布局：固定顶栏 + 水平导航 + 居中内容容器
 */
export default function Layout() {
  const location = useLocation();
  const currentPath = location.pathname;

  // 顶栏一级导航（扁平，不分段）
  const navItems: NavItem[] = [
    { id: 'upload', path: '/upload', label: '工作台' },
    { id: 'resumes', path: '/history', label: '简历库' },
    { id: 'interviews', path: '/interviews', label: '面试记录' },
    { id: 'questions', path: '/questions', label: '题库' },
    { id: 'kb', path: '/knowledgebase', label: '知识库' },
    { id: 'chat', path: '/knowledgebase/chat', label: '问答' },
  ];

  // 判断当前页面是否匹配导航项
  const isActive = (path: string) => {
    switch (path) {
      case '/upload':
        return currentPath === '/upload' || currentPath === '/';
      case '/history':
        return currentPath.startsWith('/history');
      case '/interviews':
        // 进行中面试 /interview/:resumeId 也归属面试记录
        return currentPath.startsWith('/interviews') || currentPath.startsWith('/interview/');
      case '/questions':
        return currentPath.startsWith('/questions');
      case '/knowledgebase':
        // /knowledgebase/chat 归属「问答」项
        return currentPath.startsWith('/knowledgebase') && !currentPath.startsWith('/knowledgebase/chat');
      case '/knowledgebase/chat':
        return currentPath.startsWith('/knowledgebase/chat');
      default:
        return currentPath.startsWith(path);
    }
  };

  return (
    <div className="h-screen flex flex-col bg-primary-50/60">
      {/* 顶栏 */}
      <header className="z-40 bg-white border-b border-zinc-200 shrink-0">
        <div className="mx-auto max-w-7xl h-14 px-4 sm:px-6 flex items-center gap-4 sm:gap-6">
          {/* 品牌 */}
          <Link to="/upload" className="flex items-center gap-2.5 shrink-0">
            <BrandMark className="w-6 h-6" />
            <span className="text-sm font-semibold text-zinc-900 tracking-tight">智能面试助手</span>
          </Link>

          {/* 水平导航（小屏横向滚动） */}
          <nav className="flex items-center gap-0.5 h-full overflow-x-auto scrollbar-none">
            {navItems.map((item) => {
              const active = isActive(item.path);
              return (
                <Link
                  key={item.id}
                  to={item.path}
                  className={`h-full flex items-center px-2.5 sm:px-3 text-sm whitespace-nowrap border-b-2 -mb-px transition-colors ${
                    active
                      ? 'border-primary-600 text-zinc-900 font-medium'
                      : 'border-transparent text-zinc-500 hover:text-zinc-900'
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          {/* 右侧：通知 + 用户菜单 */}
          <div className="ml-auto flex items-center gap-1 shrink-0">
            <NotificationBell />
            <UserMenu />
          </div>
        </div>
      </header>

      {/* 主内容区 */}
      <main className="flex-1 min-h-0 overflow-y-auto">
        <div className="mx-auto max-w-7xl min-h-full flex flex-col px-4 sm:px-6 py-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

/**
 * 用户菜单：头像 + 下拉（个人中心 / 会员中心 / 管理后台 / 退出登录）
 */
function UserMenu() {
  const { logout, user } = useUser();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const isAdmin = user?.role === 'ADMIN';

  // 点击菜单外部或按 ESC 关闭
  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  const handleLogout = () => {
    setOpen(false);
    logout();
    navigate('/login');
  };

  const menuItemCls =
    'w-full flex items-center gap-2.5 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50 transition-colors text-left';

  return (
    <div ref={menuRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1.5 h-8 pl-1 pr-2 rounded-md hover:bg-zinc-100 transition-colors"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <span className="w-[26px] h-[26px] rounded-full bg-primary-800 text-white text-[11px] font-medium flex items-center justify-center uppercase">
          {user?.username?.charAt(0) || 'U'}
        </span>
        <span className="hidden sm:block max-w-[96px] truncate text-sm text-zinc-700">{user?.username}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-zinc-400 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute right-0 mt-1.5 w-48 bg-white border border-zinc-200 rounded-md shadow-lg py-1 fade-in" role="menu">
          <div className="px-3 py-2 border-b border-zinc-100">
            <p className="text-sm font-medium text-zinc-900 truncate">{user?.username}</p>
            <p className="font-mono text-xs text-zinc-400 mt-0.5">ID {user?.id ?? '—'}</p>
          </div>
          <div className="py-1">
            <Link to="/profile" className={menuItemCls} onClick={() => setOpen(false)} role="menuitem">
              <User className="w-4 h-4 text-zinc-400" />
              个人中心
            </Link>
            <Link to="/membership" className={menuItemCls} onClick={() => setOpen(false)} role="menuitem">
              <Crown className="w-4 h-4 text-zinc-400" />
              会员中心
            </Link>
            {isAdmin && (
              <Link to="/admin" className={menuItemCls} onClick={() => setOpen(false)} role="menuitem">
                <Settings className="w-4 h-4 text-zinc-400" />
                管理后台
              </Link>
            )}
          </div>
          <div className="border-t border-zinc-100 py-1">
            <button type="button" onClick={handleLogout} className={`${menuItemCls} text-red-600 hover:bg-red-50`} role="menuitem">
              <LogOut className="w-4 h-4 text-red-400" />
              退出登录
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
