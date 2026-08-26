import { get, post, put, uploadFile } from '../utils/request'

// 说明：登录/微信登录/刷新 token/登出等认证接口统一收敛在 api/auth.ts，
// 本文件仅保留用户资料、安全设置相关接口（原重复的 login/wechatLogin/
// miniprogramLogin/refreshToken/logout 已删除，调用方已迁移）

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
 * 端点对齐后端：PUT /api/users/me/password（原误用 POST /api/users/password 会 404）
 */
export const changePassword = (oldPassword: string, newPassword: string) => {
  return put('/api/users/me/password', { oldPassword, newPassword })
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

/**
 * 邮箱验证码修改密码（登录态，验证码发往当前账号绑定邮箱，scene=CHANGE_PASSWORD）
 * POST /api/users/password/change-by-email
 */
export const changePasswordByEmail = (code: string, newPassword: string) => {
  return post('/api/users/password/change-by-email', { code, newPassword })
}

/**
 * 绑定/换绑邮箱（新邮箱验证码单验证，scene=BIND_EMAIL）
 * PUT /api/users/email/bind
 */
export const bindEmail = (email: string, code: string) => {
  return put('/api/users/email/bind', { email, code })
}
