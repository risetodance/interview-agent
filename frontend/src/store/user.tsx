import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { authApi } from '../api/auth';
import { userApi, type UserProfile, type ProfileUpdateRequest, type PasswordChangeRequest } from '../api/user';
import type { LoginRequest, RegisterRequest } from '../types/auth';

/**
 * localStorage key
 */
const TOKEN_KEY = 'auth_token';

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

  const isLoggedIn = !!token;

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
    setUser(null);
    setToken(null);
    removeStoredToken();
  };

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
