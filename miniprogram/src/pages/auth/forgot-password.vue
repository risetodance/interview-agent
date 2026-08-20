<script setup lang="ts">
import { ref } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { resetPassword } from '../../api/auth'
import { useEmailCode } from '../../composables/useEmailCode'
import { isValidEmail } from '../../utils/validate'

// 表单状态
const email = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const isLoading = ref(false)

// SIM-3：发码防抖/倒计时/定时器清理统一由 useEmailCode 封装
// RESET_PASSWORD 场景后端会校验邮箱已注册，未注册会提示"该邮箱未注册"
const { codeText, isCounting, send: sendCode } = useEmailCode('RESET_PASSWORD')

const handleGetCode = () => {
  // M7：空邮箱给出提示而非静默返回
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

const handleReset = async () => {
  if (isLoading.value) return
  if (!isValidEmail(email.value)) {
    uni.showToast({ title: '请输入正确的邮箱地址', icon: 'none' })
    return
  }
  if (!code.value) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }
  if (newPassword.value.length < 6) {
    uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    await resetPassword(email.value, code.value, newPassword.value)
    uni.showToast({ title: '密码重置成功，请使用新密码登录', icon: 'none' })
    setTimeout(() => {
      uni.navigateBack()
    }, 1500)
  } catch (error: any) {
    uni.showToast({ title: error.message || '重置失败', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

const goBack = () => {
  uni.navigateBack()
}
</script>

<template>
  <view class="forgot-container">
    <!-- 顶部 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <Icon name="arrow-left" size="40rpx" color="#1e293b" />
      </view>
      <view class="header-badge">
        <Icon name="lock" :size="40" color="#fff" />
      </view>
      <text class="header-title">找回密码</text>
      <text class="header-sub">通过绑定邮箱的验证码重置密码</text>
    </view>

    <!-- 表单卡片 -->
    <view class="form-card">
      <view class="input-group">
        <view class="input-item">
          <text class="input-label">注册邮箱</text>
          <input v-model="email" type="text" placeholder="请输入注册时的邮箱地址" class="input-field" />
        </view>
        <view class="input-item">
          <text class="input-label">验证码</text>
          <view class="code-row">
            <input v-model="code" type="number" placeholder="邮箱验证码" maxlength="6" class="input-field code-input" />
            <view class="code-btn" :class="{ disabled: isCounting }" @click="handleGetCode">
              <text>{{ codeText }}</text>
            </view>
          </view>
        </view>
        <view class="input-item">
          <text class="input-label">新密码</text>
          <input v-model="newPassword" type="password" placeholder="至少 6 位" class="input-field" />
        </view>
        <view class="input-item">
          <text class="input-label">确认新密码</text>
          <input v-model="confirmPassword" type="password" placeholder="再次输入新密码" class="input-field" />
        </view>
      </view>

      <button class="submit-btn" :disabled="isLoading" @click="handleReset">
        <text>{{ isLoading ? '提交中...' : '重置密码' }}</text>
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.forgot-container {
  min-height: 100vh;
  background: $bg;
  padding: calc(env(safe-area-inset-top, 0px) + 40rpx) 48rpx 60rpx;
}

.header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 64rpx;
  position: relative;
}

.back-btn {
  position: absolute;
  left: 0;
  top: 0;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-badge {
  width: 112rpx;
  height: 112rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
  box-shadow: 0 12rpx 32rpx rgba($primary, 0.3);
}

.header-title {
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 12rpx;
}

.header-sub {
  font-size: 26rpx;
  color: $text-muted;
}

.form-card {
  background: $card-bg;
  border-radius: 32rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.06);
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-bottom: 40rpx;
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

.submit-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba($primary, 0.3);
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
</style>
