/**
 * 认证相关类型定义
 */

// 登录请求
export interface LoginRequest {
  username: string;
  password: string;
}

// 登录响应
export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  role: string;
}

// 注册请求
export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  nickname?: string;
}

// 注册响应
export interface RegisterResponse {
  id: number;
  username: string;
  email: string;
  nickname: string;
  role: string;
  createdAt?: string;
}
