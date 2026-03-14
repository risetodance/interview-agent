import { get, post, put, uploadFile } from '../utils/request'

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

// 检测是否为H5环境
const isH5 = typeof window !== 'undefined' && typeof uni !== 'undefined' && !uni.getSystemInfoSync

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
  if (isH5 || (typeof uni !== 'undefined' && !uni.getSystemInfoSync?.())) {
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
  if (isH5 || (typeof uni !== 'undefined' && !uni.getSystemInfoSync?.())) {
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
 */
export const updateUserProfile = (data: any) => {
  return put('/api/users/me', data)
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
