import { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from 'react';
import { authApi } from '../api/auth';
import { userApi, type UserProfile, type ProfileUpdateRequest, type PasswordChangeRequest } from '../api/user';
import { configApi } from '../api/config';
import type { LoginRequest, RegisterRequest } from '../types/auth';

/**
 * localStorage key
 */
const TOKEN_KEY = 'auth_token';

/**
 * 默认空闲超时时间（分钟）
 */
const DEFAULT_IDLE_TIMEOUT = 30;

/**
 * 获取本地存储的 token
 */
function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * 保存 token 到本地存储
 */
function setStoredToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * 清除本地存储的 token
 */
function removeStoredToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * 用户上下文类型
 */
interface UserContextType {
  user: UserProfile | null;
  token: string | null;
  isLoggedIn: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  fetchUserProfile: () => Promise<void>;
  updateProfile: (data: ProfileUpdateRequest) => Promise<void>;
  changePassword: (oldPwd: string, newPwd: string) => Promise<void>;
}

/**
 * 创建用户上下文
 */
const UserContext = createContext<UserContextType | undefined>(undefined);

/**
 * 用户状态提供者
 */
export function UserProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [token, setToken] = useState<string | null>(getStoredToken());
  const [idleTimeoutMinutes, setIdleTimeoutMinutes] = useState<number>(DEFAULT_IDLE_TIMEOUT);
  const idleTimerRef = useRef<NodeJS.Timeout | null>(null);
  const logoutRef = useRef<() => void>(() => {});

  const isLoggedIn = !!token;

  // 获取会话配置
  useEffect(() => {
    configApi.getSessionConfig()
      .then(config => {
        setIdleTimeoutMinutes(config.idleTimeoutMinutes || DEFAULT_IDLE_TIMEOUT);
      })
      .catch(() => {
        // 使用默认配置
      });
  }, []);

  // 清除空闲计时器
  const clearIdleTimer = useCallback(() => {
    if (idleTimerRef.current) {
      clearTimeout(idleTimerRef.current);
      idleTimerRef.current = null;
    }
  }, []);

  // 重置空闲计时器
  const resetIdleTimer = useCallback(() => {
    clearIdleTimer();
    if (isLoggedIn && idleTimeoutMinutes > 0) {
      idleTimerRef.current = setTimeout(() => {
        // 空闲超时，自动登出
        console.log('Session idle timeout, logging out...');
        logoutRef.current();
      }, idleTimeoutMinutes * 60 * 1000);
    }
  }, [isLoggedIn, idleTimeoutMinutes, clearIdleTimer]);

  // 监听用户活动事件
  useEffect(() => {
    if (!isLoggedIn) return;

    const handleActivity = () => {
      resetIdleTimer();
    };

    // 监听各种用户活动事件
    window.addEventListener('mousemove', handleActivity);
    window.addEventListener('mousedown', handleActivity);
    window.addEventListener('keypress', handleActivity);
    window.addEventListener('scroll', handleActivity);
    window.addEventListener('touchstart', handleActivity);

    // 初始启动计时器
    resetIdleTimer();

    return () => {
      window.removeEventListener('mousemove', handleActivity);
      window.removeEventListener('mousedown', handleActivity);
      window.removeEventListener('keypress', handleActivity);
      window.removeEventListener('scroll', handleActivity);
      window.removeEventListener('touchstart', handleActivity);
      clearIdleTimer();
    };
  }, [isLoggedIn, resetIdleTimer, clearIdleTimer]);

  /**
   * 用户登录
   */
  const login = async (username: string, password: string): Promise<void> => {
    const loginData: LoginRequest = { username, password };
    const response = await authApi.login(loginData);

    setToken(response.token);
    setStoredToken(response.token);

    // 登录成功后获取用户信息
    await fetchUserProfile();
  };

  /**
   * 用户注册
   */
  const register = async (data: RegisterRequest): Promise<void> => {
    await authApi.register(data);
    // 注册成功后自动登录
    await login(data.username, data.password);
  };

  /**
   * 用户登出
   */
  const logout = (): void => {
    clearIdleTimer();
    setUser(null);
    setToken(null);
    removeStoredToken();
  };

  // 设置 logoutRef 以便空闲超时可以调用 logout
  useEffect(() => {
    logoutRef.current = logout;
  }, [logout]);

  /**
   * 获取用户信息
   */
  const fetchUserProfile = async (): Promise<void> => {
    try {
      const profile = await userApi.getUserProfile();
      setUser(profile);
    } catch (error) {
      // 获取用户信息失败时，清除登录状态
      logout();
      throw error;
    }
  };

  /**
   * 更新用户资料
   */
  const updateProfile = async (data: ProfileUpdateRequest): Promise<void> => {
    const updatedProfile = await userApi.updateProfile(data);
    setUser(updatedProfile);
  };

  /**
   * 修改密码
   */
  const changePassword = async (oldPwd: string, newPwd: string): Promise<void> => {
    const passwordData: PasswordChangeRequest = {
      oldPassword: oldPwd,
      newPassword: newPwd,
    };
    await userApi.changePassword(passwordData);
  };

  // 初始化：检查 token 并获取用户信息
  useEffect(() => {
    if (token) {
      fetchUserProfile().catch(() => {
        // token 无效，清除登录状态
        logout();
      });
    }
  }, []);

  return (
    <UserContext.Provider
      value={{
        user,
        token,
        isLoggedIn,
        login,
        register,
        logout,
        fetchUserProfile,
        updateProfile,
        changePassword,
      }}
    >
      {children}
    </UserContext.Provider>
  );
}

/**
 * 使用用户上下文
 */
export function useUser(): UserContextType {
  const context = useContext(UserContext);
  if (!context) {
    throw new Error('useUser must be used within a UserProvider');
  }
  return context;
}

/**
 * 便捷hook - 检查是否已登录
 */
export function useAuth() {
  const { isLoggedIn, user, token } = useUser();
  return { isLoggedIn, user, token };
}
