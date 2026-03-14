import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, getUserProfile, updateUserProfile, logout as apiLogout } from '../api/user'

// 用户类型定义
export interface UserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  phone?: string
  email?: string
  role?: string
  vipLevel?: number
  vipExpireTime?: string
  points?: number
  createdAt?: string
  updatedAt?: string
}

export interface LoginParams {
  username: string
  password: string
}

export interface WechatLoginParams {
  code: string
  encryptedData?: string
  iv?: string
}

export const useUserStore = defineStore('user', () => {
  // ========== State ==========
  const token = ref<string>('')
  const refreshToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)
  const isLoading = ref(false)

  // ========== Getters ==========
  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)

  const isVip = computed(() => {
    if (!userInfo.value?.vipLevel || userInfo.value.vipLevel <= 0) {
      return false
    }
    // 检查VIP是否过期
    if (userInfo.value.vipExpireTime) {
      return new Date(userInfo.value.vipExpireTime).getTime() > Date.now()
    }
    return userInfo.value.vipLevel > 0
  })

  const vipLevel = computed(() => userInfo.value?.vipLevel || 0)

  const points = computed(() => userInfo.value?.points || 0)

  // ========== Actions ==========

  /**
   * 设置 Token
   */
  const setToken = (newToken: string, newRefreshToken?: string) => {
    token.value = newToken
    if (newRefreshToken) {
      refreshToken.value = newRefreshToken
    }
    // 持久化存储
    uni.setStorageSync('token', newToken)
    if (newRefreshToken) {
      uni.setStorageSync('refreshToken', newRefreshToken)
    }
  }

  /**
   * 设置用户信息
   */
  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
    uni.setStorageSync('userInfo', info)
  }

  /**
   * 初始化 - 从存储中恢复用户状态
   */
  const init = () => {
    const storedToken = uni.getStorageSync('token')
    const storedRefreshToken = uni.getStorageSync('refreshToken')
    const storedUserInfo = uni.getStorageSync('userInfo')

    if (storedToken) {
      token.value = storedToken
    }
    if (storedRefreshToken) {
      refreshToken.value = storedRefreshToken
    }
    if (storedUserInfo) {
      userInfo.value = storedUserInfo
    }
  }

  /**
   * 账号密码登录
   */
  const loginByAccount = async (params: LoginParams) => {
    isLoading.value = true
    try {
      const res = await login(params)
      setToken(res.token, res.refreshToken)
      // 登录成功后获取用户信息
      await fetchUserInfo()
      return res
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 微信登录
   */
  const loginByWechat = async (params: WechatLoginParams) => {
    isLoading.value = true
    try {
      const res = await login(params)
      setToken(res.token, res.refreshToken)
      // 登录成功后获取用户信息
      await fetchUserInfo()
      return res
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取用户信息
   */
  const fetchUserInfo = async () => {
    if (!token.value) return

    try {
      const info = await getUserProfile()
      setUserInfo(info)
      return info
    } catch (error) {
      console.error('Fetch user info error:', error)
      throw error
    }
  }

  /**
   * 更新用户信息
   */
  const updateUser = async (data: Partial<UserInfo>) => {
    const updatedInfo = await updateUserProfile(data)
    setUserInfo(updatedInfo)
    return updatedInfo
  }

  /**
   * 更新积分
   */
  const updatePoints = (delta: number) => {
    if (userInfo.value) {
      userInfo.value.points = (userInfo.value.points || 0) + delta
      uni.setStorageSync('userInfo', userInfo.value)
    }
  }

  /**
   * 登出
   */
  const logout = async (showToast = true) => {
    try {
      // 调用后端登出接口（可选）
      if (token.value) {
        await apiLogout().catch(() => {})
      }
    } finally {
      // 清理本地状态
      token.value = ''
      refreshToken.value = ''
      userInfo.value = null

      // 清理存储
      uni.removeStorageSync('token')
      uni.removeStorageSync('refreshToken')
      uni.removeStorageSync('userInfo')

      if (showToast) {
        uni.showToast({
          title: '已退出登录',
          icon: 'success'
        })
      }
    }
  }

  /**
   * 清除所有用户数据（不调用后端）
   */
  const clearUserData = () => {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null

    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    uni.removeStorageSync('userInfo')
  }

  // 初始化
  init()

  return {
    // State
    token,
    refreshToken,
    userInfo,
    isLoading,

    // Getters
    isLoggedIn,
    isVip,
    vipLevel,
    points,

    // Actions
    setToken,
    setUserInfo,
    init,
    loginByAccount,
    loginByWechat,
    fetchUserInfo,
    updateUser,
    updatePoints,
    logout,
    clearUserData
  }
})
