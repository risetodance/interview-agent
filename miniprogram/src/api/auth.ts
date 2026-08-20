import { post } from '../utils/request'

// 微信登录参数
export interface WechatLoginParams {
  code: string
  encryptedData?: string
  iv?: string
  nickName?: string
  avatarUrl?: string
  gender?: number
}

// 登录响应（对齐后端 LoginResponse 真实字段：仅 token/userId/username/role，
// 后端从不返回 refreshToken/expiresIn/nickname/avatar）
export interface LoginResult {
  token: string
  userId?: number
  username?: string
  role?: string
}

// 手机号登录参数
export interface PhoneLoginParams {
  phone: string
  code: string
}

// 普通登录参数
export interface LoginParams {
  username: string
  password: string
}

/**
 * 微信小程序一键登录
 * 使用 uni.login 获取 code，再通过 uni.getUserProfile 获取用户信息
 * H5模式下使用mock的code调用后端
 */
export const wechatLogin = (data: WechatLoginParams) => {
  return post<LoginResult>('/api/auth/wechat/login', data)
}

/**
 * 微信扫码登录（Web端）
 */
export const wechatQrCodeLogin = (code: string) => {
  return post<LoginResult>('/api/auth/wechat/scan/login', { code })
}

/**
 * 手机号登录
 */
export const phoneLogin = (data: PhoneLoginParams) => {
  return post<LoginResult>('/api/auth/phone/login', data)
}

// ========== 邮箱验证码认证（腾讯云 SES 发信） ==========

/**
 * 邮箱验证码场景（与后端 EmailCodeService.Scene 对应）
 */
export type EmailCodeScene = 'LOGIN' | 'RESET_PASSWORD' | 'CHANGE_PASSWORD' | 'BIND_EMAIL'

/**
 * 发送邮箱验证码
 * POST /api/auth/email/code/send
 * @param email 收件邮箱
 * @param scene 场景：LOGIN-登录/注册、RESET_PASSWORD-忘记密码、CHANGE_PASSWORD-改密、BIND_EMAIL-绑定邮箱
 */
export const sendEmailCode = (email: string, scene: EmailCodeScene) => {
  return post('/api/auth/email/code/send', { email, scene })
}

/**
 * 邮箱验证码登录响应（两步式第一步）
 * needsRegister=true 表示邮箱未注册：验证码已还原，前端进入第二步设置用户名密码后调 emailRegister
 */
export interface EmailLoginResult {
  needsRegister: boolean
  login: LoginResult | null
}

/**
 * 邮箱验证码登录（两步式第一步）
 * POST /api/auth/email/login
 */
export const emailLogin = (email: string, code: string) => {
  return post<EmailLoginResult>('/api/auth/email/login', { email, code })
}

/**
 * 邮箱验证码注册（两步式第二步：设置用户名与密码，完成即登录）
 * POST /api/auth/email/register
 */
export const emailRegister = (data: {
  email: string
  code: string
  username: string
  password: string
  nickname?: string
}) => {
  return post<LoginResult>('/api/auth/email/register', data)
}

/**
 * 忘记密码：凭邮箱验证码重置密码
 * POST /api/auth/password/reset
 */
export const resetPassword = (email: string, code: string, newPassword: string) => {
  return post('/api/auth/password/reset', { email, code, newPassword })
}

/**
 * 普通账号登录
 */
export const login = (data: LoginParams) => {
  return post<LoginResult>('/api/auth/login', data)
}

/**
 * 注册
 */
export const register = (data: LoginParams & { email?: string }) => {
  return post<LoginResult>('/api/auth/register', data)
}

/**
 * 发送手机验证码
 * @param phone 手机号
 * @param scene 场景：login-登录/register-注册/bind-绑定
 */
export const sendVerifyCode = (phone: string, scene: string = 'login') => {
  return post('/api/auth/code/send', { phone, scene })
}

/**
 * 刷新 Token
 */
export const refreshToken = (refreshToken: string) => {
  return post<LoginResult>('/api/auth/refresh', { refreshToken })
}

/**
 * 登出
 */
export const logout = () => {
  return post('/api/auth/logout')
}
