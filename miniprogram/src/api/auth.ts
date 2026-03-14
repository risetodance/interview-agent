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

// 登录响应
export interface LoginResult {
  token: string
  refreshToken?: string
  expiresIn?: number
  userId?: number
  nickname?: string
  avatar?: string
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
export const wechatLogin = async (data: WechatLoginParams) => {
  try {
    return await post<LoginResult>('/api/auth/wechat/login', data)
  } catch (error) {
    console.error('[Auth] 微信登录失败:', error)
    throw error
  }
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
