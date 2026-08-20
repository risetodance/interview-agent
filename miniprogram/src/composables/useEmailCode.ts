import { ref, onUnmounted } from 'vue'
import { sendEmailCode, type EmailCodeScene } from '../api/auth'

// composables/ 目录：有状态的组合式逻辑（含响应式状态与生命周期清理），
// 与 utils/ 的无状态纯函数区分。

/**
 * 邮箱验证码发送 composable：统一封装 双守卫防抖（倒计时中/发送中）→ 发码 →
 * 成功 toast + 60s 倒计时 → 失败 toast → finally 复位发送中 → onUnmounted 清理定时器。
 * 邮箱格式校验留在调用方（各页提示文案不同；security 改密场景校验的是绑定邮箱非空）。
 */
export function useEmailCode(scene: EmailCodeScene, successText = '验证码已发送，请查收邮件') {
  const codeText = ref('获取验证码')
  const isCounting = ref(false)
  const isSending = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  const send = async (email: string) => {
    if (isCounting.value || isSending.value) return
    isSending.value = true
    try {
      await sendEmailCode(email, scene)
      uni.showToast({ title: successText, icon: 'none' })
      isCounting.value = true
      let seconds = 60
      codeText.value = `${seconds}s`
      timer = setInterval(() => {
        seconds--
        codeText.value = seconds > 0 ? `${seconds}s` : '获取验证码'
        if (seconds <= 0) {
          clearInterval(timer!)
          timer = null
          isCounting.value = false
        }
      }, 1000)
    } catch (error: any) {
      uni.showToast({ title: error.message || '发送失败', icon: 'none' })
    } finally {
      isSending.value = false
    }
  }

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { codeText, isCounting, isSending, send }
}
