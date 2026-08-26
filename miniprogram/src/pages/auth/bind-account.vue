<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { wechatBindByPassword, wechatBindByEmailCode } from '../../api/auth'
import { useEmailCode } from '../../composables/useEmailCode'
import { useLoginSuccess } from '../../composables/useLoginSuccess'
import { isValidEmail } from '../../utils/validate'

// 微信关联账号页：微信登录未绑定时由登录页 needsBind 分支跳入
// 票据 5 分钟一次性，openid 暂存后端 Redis（前端只见票据）

// 关联票据（URL query 传入）
const ticket = ref('')

onLoad((options) => {
  ticket.value = options?.ticket || ''
  if (!ticket.value) {
    uni.showToast({ title: '缺少微信登录凭证，请重新微信登录', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 900)
  }
})

// 双通道：true=账号/邮箱+密码，false=邮箱+验证码
const usePassword = ref(true)
const isLoading = ref(false)

// 密码通道
const account = ref('')
const password = ref('')

// 验证码通道（复用 LOGIN 场景验证码：绑定即登录，与登录页邮箱 tab 同一套码）
const email = ref('')
const emailCode = ref('')
const { codeText, isCounting, send: sendCode } = useEmailCode('LOGIN')

const { handleLoginSuccess } = useLoginSuccess()

// 票据失效（13002）/ 失败次数超限（13005）：票据已作废，回登录页重新走微信登录
const isTicketError = (code?: number) => code === 13002 || code === 13005

const handleTicketInvalid = (message: string) => {
  uni.showToast({ title: message || '微信登录凭证已过期，请重新微信登录', icon: 'none' })
  setTimeout(() => uni.reLaunch({ url: '/pages/auth/login' }), 900)
}

// 关联通道一：账号/邮箱 + 密码
const handleBindByPassword = async () => {
  if (isLoading.value) return
  if (!account.value || !password.value) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const result = await wechatBindByPassword({
      ticket: ticket.value,
      account: account.value,
      password: password.value
    })
    await handleLoginSuccess(result, '绑定成功')
  } catch (error: any) {
    if (isTicketError(error?.code)) {
      handleTicketInvalid(error.message)
      return
    }
    uni.showToast({ title: error.message || '绑定失败，请重试', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

// 获取邮箱验证码：仅做邮箱校验，发码/防抖/倒计时交给 composable
const handleGetCode = () => {
  if (!email.value) {
    uni.showToast({ title: '请输入邮箱地址', icon: 'none' })
    return
  }
  if (!isValidEmail(email.value)) {
    uni.showToast({ title: '请输入正确的邮箱地址', icon: 'none' })
    return
  }
  sendCode(email.value)
}

// 关联通道二：邮箱 + 验证码
const handleBindByEmailCode = async () => {
  if (isLoading.value) return
  if (!email.value || !emailCode.value) {
    uni.showToast({ title: '请填写邮箱和验证码', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const result = await wechatBindByEmailCode({
      ticket: ticket.value,
      email: email.value,
      code: emailCode.value
    })
    await handleLoginSuccess(result, '绑定成功')
  } catch (error: any) {
    if (isTicketError(error?.code)) {
      handleTicketInvalid(error.message)
      return
    }
    // 其余错误（验证码错误/邮箱未注册等）直接展示后端消息，保留在当前页重试
    uni.showToast({ title: error.message || '绑定失败，请重试', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

// 注册引导：返回登录页切「邮箱登录」tab，两步式完成注册后再来绑定
const goBackToRegister = () => {
  uni.navigateBack()
}
</script>

<template>
  <view class="bind-container">
    <!-- 说明卡片 -->
    <view class="tips-card">
      <text class="tips-title">该微信尚未绑定账号</text>
      <text class="tips-desc">输入已有账号的凭证完成关联，绑定后即可微信一键登录</text>
    </view>

    <!-- 关联表单卡片 -->
    <view class="bind-card">
      <!-- 通道 tabs -->
      <view class="channel-tabs">
        <view class="tab-item" :class="{ active: usePassword }" @click="usePassword = true">
          <text>账号密码</text>
        </view>
        <view class="tab-item" :class="{ active: !usePassword }" @click="usePassword = false">
          <text>邮箱验证码</text>
        </view>
      </view>

      <!-- 通道一：账号/邮箱 + 密码 -->
      <view v-if="usePassword" class="input-group">
        <view class="input-item">
          <text class="input-label">账号</text>
          <input v-model="account" type="text" placeholder="用户名或邮箱" class="input-field" />
        </view>
        <view class="input-item">
          <text class="input-label">密码</text>
          <input v-model="password" type="password" placeholder="请输入密码" class="input-field" />
        </view>
        <button class="submit-btn" :disabled="isLoading" @click="handleBindByPassword">
          <text>{{ isLoading ? '绑定中...' : '绑定并登录' }}</text>
        </button>
      </view>

      <!-- 通道二：邮箱 + 验证码 -->
      <view v-else class="input-group">
        <view class="input-item">
          <text class="input-label">邮箱</text>
          <input v-model="email" type="text" placeholder="账号绑定的邮箱地址" class="input-field" />
        </view>
        <view class="input-item">
          <text class="input-label">验证码</text>
          <view class="code-row">
            <input v-model="emailCode" type="number" placeholder="请输入邮箱验证码" maxlength="6" class="input-field code-input" />
            <view class="code-btn" :class="{ disabled: isCounting }" @click="handleGetCode">
              <text>{{ codeText }}</text>
            </view>
          </view>
        </view>
        <button class="submit-btn" :disabled="isLoading" @click="handleBindByEmailCode">
          <text>{{ isLoading ? '绑定中...' : '绑定并登录' }}</text>
        </button>
      </view>
    </view>

    <!-- 注册引导（无账号的新用户） -->
    <view class="register-guide" @click="goBackToRegister">
      <text>还没有账号？返回用「邮箱登录」注册</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.bind-container {
  min-height: 100vh;
  background: $bg;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: calc(env(safe-area-inset-top, 0px) + 80rpx) 48rpx 60rpx;
}

// 说明卡片
.tips-card {
  width: 100%;
  background: rgba($primary, 0.06);
  border-radius: 24rpx;
  padding: 36rpx 32rpx;
  margin-bottom: 40rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.tips-title {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
}

.tips-desc {
  font-size: 26rpx;
  color: $text-secondary;
  line-height: 1.6;
}

// 表单卡片
.bind-card {
  width: 100%;
  background: $card-bg;
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.06);
}

// 通道 tabs（样式对齐登录页 login-tabs）
.channel-tabs {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.tab-item {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background: $bg;
  font-size: 28rpx;
  color: $text-secondary;
  transition: all 0.2s;

  &.active {
    background: rgba($primary, 0.1);
    color: $primary;
    font-weight: 600;
  }
}

// 表单（样式对齐登录页）
.input-group {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.input-item {
  display: flex;
  flex-direction: column;
}

.input-label {
  font-size: 26rpx;
  color: $text-secondary;
  margin-bottom: 12rpx;
}

.input-field {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background: $bg;
  border-radius: 16rpx;
  font-size: 30rpx;
  color: $text-primary;
  box-sizing: border-box;
}

.code-row {
  display: flex;
  gap: 16rpx;
}

.code-input {
  flex: 1;
}

.code-btn {
  flex-shrink: 0;
  height: 88rpx;
  padding: 0 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba($primary, 0.1);
  border-radius: 16rpx;
  font-size: 26rpx;
  color: $primary;

  &.disabled {
    opacity: 0.5;
  }
}

// 提交按钮（样式对齐登录页 submit-btn）
.submit-btn {
  width: 100%;
  height: 96rpx;
  margin-top: 16rpx;
  background: linear-gradient(135deg, #07c160 0%, #06ad56 100%);
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(7, 193, 96, 0.3);
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;

  &::after {
    border: none;
  }

  &:active {
    transform: scale(0.98);
  }
}

// 注册引导
.register-guide {
  margin-top: 40rpx;
  font-size: 26rpx;
  color: $primary;
}
</style>
