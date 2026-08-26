import { useUserStore } from '../stores/user'

// composables/ 目录：有状态的组合式逻辑（含响应式状态与生命周期清理），
// 与 utils/ 的无状态纯函数区分。

/**
 * 登录成功统一处理 composable：存 token → 拉取用户信息 → toast → reLaunch 首页。
 * login 页与微信绑定页（bind-account）共用，保证各登录入口行为一致。
 *
 * 沿用原 login.vue 内联实现的约束：
 * - C1：后端 LoginResponse 不含 refreshToken，setToken 第二参可省略
 * - B9/N3：fetchUserInfo 失败不阻塞登录主流程（token 已存），后续 401 兜底重登
 * - B10：reLaunch 清空页面栈，避免返回到登录页重复登录
 */
export function useLoginSuccess() {
  const userStore = useUserStore()

  const handleLoginSuccess = async (result: { token: string }, successText = '登录成功') => {
    userStore.setToken(result.token)
    try {
      await userStore.fetchUserInfo()
    } catch (e) {
      console.error('获取用户信息失败:', e)
    }
    uni.showToast({ title: successText, icon: 'success' })
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/index/index' })
    }, 500)
  }

  return { handleLoginSuccess }
}
