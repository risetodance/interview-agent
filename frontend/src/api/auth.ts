import { request } from './request';
import type { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse } from '../types/auth';

/**
 * 邮箱验证码场景（与后端 EmailCodeService.Scene 对应）
 */
export type EmailCodeScene = 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD' | 'CHANGE_PASSWORD' | 'BIND_EMAIL';

/**
 * 邮箱验证码登录响应（两步式第一步）
 * needsRegister=true：邮箱未注册，验证码已还原，前端进入第二步设置用户名密码
 */
export interface EmailLoginResponse {
  needsRegister: boolean;
  login: LoginResponse | null;
}

export const authApi = {
  /**
   * 用户登录
   */
  async login(data: LoginRequest): Promise<LoginResponse> {
    return request.post<LoginResponse>('/api/auth/login', data);
  },

  /**
   * 用户注册
   */
  async register(data: RegisterRequest): Promise<RegisterResponse> {
    return request.post<RegisterResponse>('/api/auth/register', data);
  },

  /**
   * 发送邮箱验证码
   */
  async sendEmailCode(email: string, scene: EmailCodeScene): Promise<void> {
    return request.post('/api/auth/email/code/send', { email, scene });
  },

  /**
   * 邮箱验证码登录（两步式第一步）
   */
  async emailLogin(email: string, code: string): Promise<EmailLoginResponse> {
    return request.post<EmailLoginResponse>('/api/auth/email/login', { email, code });
  },

  /**
   * 邮箱验证码注册（两步式第二步，完成即登录）
   */
  async emailRegister(data: {
    email: string;
    code: string;
    username: string;
    password: string;
    nickname?: string;
  }): Promise<LoginResponse> {
    return request.post<LoginResponse>('/api/auth/email/register', data);
  },

  /**
   * 忘记密码：凭邮箱验证码重置
   */
  async resetPassword(email: string, code: string, newPassword: string): Promise<void> {
    return request.post('/api/auth/password/reset', { email, code, newPassword });
  },

  /**
   * 登录态：邮箱验证码修改密码（码发往当前账号绑定邮箱）
   */
  async changePasswordByEmail(code: string, newPassword: string): Promise<void> {
    return request.post('/api/users/password/change-by-email', { code, newPassword });
  },

  /**
   * 登录态：绑定/换绑邮箱（新邮箱验证码单验证）
   */
  async bindEmail(email: string, code: string): Promise<void> {
    return request.put('/api/users/email/bind', { email, code });
  },
};
