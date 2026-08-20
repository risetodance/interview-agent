/**
 * 校验工具：仅放无状态纯函数。
 * 有状态逻辑（如验证码倒计时）见 ../composables/。
 */

/**
 * 邮箱格式校验（宽松正则，与后端 @Email 校验对齐，同 Web 端正则）
 */
export const isValidEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
