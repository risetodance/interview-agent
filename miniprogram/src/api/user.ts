import { get, post, put, uploadFile } from '../utils/request'
import { isH5 } from '../utils/env'

// 登录请求参数
export interface LoginParams {
  username: string
  password: string
}

// 微信登录参数
export interface WechatLoginParams {
  code: string
  encryptedData?: string
  iv?: string
}

// 登录响应
export interface LoginResult {
  token: string
  refreshToken?: string
  expiresIn?: number
}

/**
 * 账号密码登录
 */
export const login = (data: LoginParams) => {
  return post<LoginResult>('/api/auth/login', data)
}

/**
 * H5测试用登录接口 - 自动创建用户
 */
export const testLogin = (data: LoginParams) => {
  return post<LoginResult>('/api/auth/login/test', data)
}

/**
 * 微信登录
 * H5模式下mock，因为无法调用微信API
 */
export const wechatLogin = (data: WechatLoginParams) => {
  // N6+B3：本地 isH5 声明恒为 false（!uni.getSystemInfoSync 永远取反到 false），
  // 改用 utils/env.ts 统一导出的 isH5（基于 window 判定）
  if (isH5) {
    return Promise.resolve({
      token: 'mock_wechat_token_' + Date.now(),
      refreshToken: 'mock_refresh_token_' + Date.now(),
      expiresIn: 7200,
      userId: 1
    })
  }
  return post<LoginResult>('/api/auth/wechat/login', data)
}

/**
 * 微信小程序登录
 * H5模式下mock
 */
export const miniprogramLogin = (data: WechatLoginParams) => {
  if (isH5) {
    return Promise.resolve({
      token: 'mock_wechat_token_' + Date.now(),
      refreshToken: 'mock_refresh_token_' + Date.now(),
      expiresIn: 7200,
      userId: 1
    })
  }
  return post<LoginResult>('/api/auth/wechat/login', data)
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

/**
 * 获取用户信息
 */
export const getUserProfile = () => {
  return get('/api/users/me')
}

/**
 * 更新用户信息
 * 端点对齐后端 UserController：PUT /api/users/me/profile（原误用 /api/users/me 会 404）
 */
export const updateUserProfile = (data: any) => {
  return put('/api/users/me/profile', data)
}

/**
 * 上传头像
 */
export const uploadAvatar = (filePath: string) => {
  return uploadFile(filePath, {
    url: '/api/users/avatar',
    name: 'avatar'
  })
}

/**
 * 修改密码
 */
export const changePassword = (oldPassword: string, newPassword: string) => {
  return post('/api/users/password', { oldPassword, newPassword })
}

/**
 * 绑定手机号
 */
export const bindPhone = (phone: string, code: string) => {
  return post('/api/users/phone/bind', { phone, code })
}

/**
 * 发送验证码
 */
export const sendVerifyCode = (phone: string, scene: string) => {
  return post('/api/auth/code/send', { phone, scene })
}

/**
 * 获取会员信息
 */
export const getVipInfo = () => {
  return get('/api/membership')
}
