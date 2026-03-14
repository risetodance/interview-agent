<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useUserStore } from '../../stores/user'
import { wechatLogin } from '../../api/auth'

// 用户 Store
const userStore = useUserStore()

// 加载状态
const isLoading = ref(false)

// 检测是否为H5环境
const isH5 = typeof window !== 'undefined' && typeof uni !== 'undefined'

// 微信小程序登录
const handleWechatLogin = async () => {
  if (isLoading.value) return
  isLoading.value = true

  try {
    let loginCode = ''
    let userInfo: any = null

    if (isH5) {
      // H5环境下mock微信登录的code（因为uni.login在H5不工作）
      // 但仍然调用后端API进行验证
      loginCode = 'h5_mock_wechat_code_' + Date.now()
      userInfo = { nickName: 'H5测试用户' }
    } else {
      // 小程序环境
      const loginRes = await uni.login({ provider: 'weixin' })
      if (!loginRes.code) {
        throw new Error('获取授权码失败')
      }
      loginCode = loginRes.code

      try {
        const profileRes = await uni.getUserProfile({ desc: '用于完善用户资料' })
        userInfo = profileRes.userInfo
      } catch (profileError) {
        console.warn('获取用户信息失败:', profileError)
      }
    }

    const loginData = {
      code: loginCode,
      encryptedData: userInfo?.encryptedData,
      iv: userInfo?.iv,
      nickName: userInfo?.nickName || '微信用户',
      avatarUrl: userInfo?.avatarUrl,
      gender: userInfo?.gender
    }

    const result = await wechatLogin(loginData)
    userStore.setToken(result.token, result.refreshToken)
    if (result.userId) {
      userStore.setUserInfo({
        id: result.userId,
        nickname: result.nickname || userInfo?.nickName || '微信用户',
        avatar: result.avatar || userInfo?.avatarUrl || '',
        username: `wx_${result.userId}`
      })
    }

    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      // H5环境下使用navigateTo
      uni.navigateTo({ url: '/pages/index/index' })
    }, 500)
  } catch (error: any) {
    console.error('微信登录失败:', error)
    uni.showToast({ title: error.message || '登录失败，请重试', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

// 账号密码登录
const showAccountLogin = ref(false)
const username = ref('')
const password = ref('')

// 手机号登录
const showPhoneLogin = ref(false)
const phone = ref('')
const code = ref('')
const codeText = ref('获取验证码')
const isCounting = ref(false)
let countdownTimer: ReturnType<typeof setTimeout> | null = null

const handleAccountLogin = async () => {
  if (isLoading.value) return
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    // H5环境下使用testLogin接口（会自动创建用户）
    const { testLogin } = await import('../../api/user')
    const result = await testLogin({ username: username.value, password: password.value })
    userStore.setToken(result.token, result.refreshToken)
    await userStore.fetchUserInfo()
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      // H5环境下使用navigateTo
      uni.navigateTo({ url: '/pages/index/index' })
    }, 500)
  } catch (error: any) {
    uni.showToast({ title: error.message || '登录失败', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

const handleGetCode = async () => {
  if (isCounting.value || !phone.value) return
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }

  try {
    const { sendVerifyCode } = await import('../../api/auth')
    await sendVerifyCode(phone.value, 'login')
    isCounting.value = true
    let seconds = 60
    codeText.value = `${seconds}s`
    countdownTimer = setInterval(() => {
      seconds--
      codeText.value = `${seconds}s`
      if (seconds <= 0) {
        clearInterval(countdownTimer!)
        codeText.value = '获取验证码'
        isCounting.value = false
      }
    }, 1000)
  } catch (error: any) {
    uni.showToast({ title: error.message || '发送失败', icon: 'none' })
  }
}

const handlePhoneLogin = async () => {
  if (!phone.value || !code.value) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const { phoneLogin } = await import('../../api/auth')
    const result = await phoneLogin({ phone: phone.value, code: code.value })
    userStore.setToken(result.token, result.refreshToken)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      // H5环境下使用navigateTo
      uni.navigateTo({ url: '/pages/index/index' })
    }, 500)
  } catch (error: any) {
    console.error('手机号登录失败:', error)
    uni.showToast({ title: error.message || '登录失败', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<template>
  <view class="login-container">
    <!-- 动态背景 -->
    <view class="bg-mesh">
      <view class="mesh-gradient mesh-1"></view>
      <view class="mesh-gradient mesh-2"></view>
      <view class="mesh-gradient mesh-3"></view>
      <view class="floating-shape shape-1"></view>
      <view class="floating-shape shape-2"></view>
      <view class="floating-shape shape-3"></view>
    </view>

    <!-- 顶部品牌区 -->
    <view class="hero-section">
      <view class="brand-badge">
        <text class="badge-text">AI</text>
      </view>
      <view class="hero-title">
        <text class="title-line1">面试</text>
        <text class="title-line2">更简单</text>
      </view>
      <text class="hero-tagline">AI 助力，每一次面试都更有底气</text>
    </view>

    <!-- 登录卡片 -->
    <view class="login-card">
      <!-- 主登录按钮 -->
      <button class="primary-btn" :disabled="isLoading" @click="handleWechatLogin">
        <view class="btn-content">
          <text class="btn-text">{{ isLoading ? '登录中...' : '微信一键登录' }}</text>
        </view>
        <view class="btn-shine"></view>
      </button>

      <!-- 分割线 -->
      <view class="divider">
        <view class="divider-line"></view>
        <text class="divider-text">其他方式</text>
        <view class="divider-line"></view>
      </view>

      <!-- 登录方式tabs -->
      <view class="login-tabs">
        <view
          class="tab-item"
          :class="{ active: !showPhoneLogin }"
          @click="showPhoneLogin = false; showAccountLogin = false"
        >
          <text>账号登录</text>
        </view>
        <view
          class="tab-item"
          :class="{ active: showPhoneLogin }"
          @click="showPhoneLogin = true; showAccountLogin = false"
        >
          <text>手机号</text>
        </view>
      </view>

      <!-- 账号密码表单 -->
      <view v-if="!showPhoneLogin" class="form-section">
        <view class="input-group">
          <view class="input-item">
            <text class="input-label">用户名</text>
            <input v-model="username" type="text" placeholder="请输入用户名" class="input-field" />
          </view>
          <view class="input-item">
            <text class="input-label">密码</text>
            <input v-model="password" type="password" placeholder="请输入密码" class="input-field" />
          </view>
        </view>
        <button class="submit-btn" :loading="isLoading" @click="handleAccountLogin">
          <text>立即登录</text>
        </button>
      </view>

      <!-- 手机号表单 -->
      <view v-else class="form-section">
        <view class="input-group">
          <view class="input-item">
            <text class="input-label">手机号</text>
            <input v-model="phone" type="number" placeholder="请输入手机号" maxlength="11" class="input-field" />
          </view>
          <view class="input-item">
            <text class="input-label">验证码</text>
            <view class="code-row">
              <input v-model="code" type="number" placeholder="请输入验证码" maxlength="6" class="input-field code-input" />
              <view class="code-btn" :class="{ disabled: isCounting }" @click="handleGetCode">
                <text>{{ codeText }}</text>
              </view>
            </view>
          </view>
        </view>
        <button class="submit-btn" :disabled="isLoading" @click="handlePhoneLogin">
          <text>立即登录</text>
        </button>
      </view>
    </view>

    <!-- 用户协议 -->
    <view class="agreement">
      <view class="agreement-check">
        <text class="check-icon">✓</text>
      </view>
      <text class="agreement-text">
        我已阅读并同意
        <text class="link">《用户协议》</text>
        和
        <text class="link">《隐私政策》</text>
      </text>
    </view>

    <!-- 底部装饰 -->
    <view class="bottom-decoration">
      <text class="copyright">© 2026 AI面试指南</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
// 靛蓝清新配色
$primary: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg: #f0f4ff;
$card-bg: #ffffff;
$text-primary: #1e293b;
$text-secondary: #475569;
$text-muted: #94a3b8;

.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f4ff 0%, #e0e7ff 50%, #f5f3ff 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 40rpx 60rpx;
  position: relative;
  overflow: hidden;
}

// 动态背景
.bg-mesh {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.mesh-gradient {
  position: absolute;
  border-radius: 50%;
  filter: blur(80rpx);
  opacity: 0.6;
}

.mesh-1 {
  width: 600rpx;
  height: 600rpx;
  background: linear-gradient(135deg, rgba($primary-light, 0.8) 0%, rgba($primary, 0.4) 100%);
  top: -200rpx;
  right: -150rpx;
  animation: meshFloat 15s ease-in-out infinite;
}

.mesh-2 {
  width: 500rpx;
  height: 500rpx;
  background: linear-gradient(135deg, rgba($accent, 0.6) 0%, rgba($primary-light, 0.3) 100%);
  bottom: 100rpx;
  left: -200rpx;
  animation: meshFloat 12s ease-in-out infinite reverse;
}

.mesh-3 {
  width: 300rpx;
  height: 300rpx;
  background: linear-gradient(135deg, rgba($primary, 0.4) 0%, rgba($accent, 0.2) 100%);
  top: 40%;
  right: -50rpx;
  animation: meshFloat 18s ease-in-out infinite;
}

@keyframes meshFloat {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  25% {
    transform: translate(30rpx, -30rpx) scale(1.05);
  }
  50% {
    transform: translate(-20rpx, 20rpx) scale(0.95);
  }
  75% {
    transform: translate(20rpx, 30rpx) scale(1.02);
  }
}

.floating-shape {
  position: absolute;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10rpx);
}

.shape-1 {
  width: 120rpx;
  height: 120rpx;
  top: 15%;
  right: 15%;
  transform: rotate(15deg);
  animation: shapeFloat 8s ease-in-out infinite;
}

.shape-2 {
  width: 80rpx;
  height: 80rpx;
  top: 60%;
  left: 8%;
  transform: rotate(-20deg);
  animation: shapeFloat 10s ease-in-out infinite reverse;
}

.shape-3 {
  width: 60rpx;
  height: 60rpx;
  top: 35%;
  left: 12%;
  border-radius: 50%;
  animation: shapeFloat 12s ease-in-out infinite;
}

@keyframes shapeFloat {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20rpx) rotate(10deg);
  }
}

// 顶部品牌区
.hero-section {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 60rpx;
}

.brand-badge {
  width: 100rpx;
  height: 100rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, $primary 0%, $accent 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
  box-shadow: 0 12rpx 40rpx rgba($primary, 0.35);
  animation: badgePop 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badgePop {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.badge-text {
  font-size: 36rpx;
  font-weight: 800;
  color: white;
  letter-spacing: 2rpx;
}

.hero-title {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16rpx;
}

.title-line1 {
  font-size: 64rpx;
  font-weight: 900;
  color: $primary;
  letter-spacing: 8rpx;
  animation: slideUp 0.5s ease-out 0.2s both;
}

.title-line2 {
  font-size: 48rpx;
  font-weight: 700;
  background: linear-gradient(135deg, $primary 0%, $accent 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 12rpx;
  animation: slideUp 0.5s ease-out 0.3s both;
}

@keyframes slideUp {
  0% {
    transform: translateY(30rpx);
    opacity: 0;
  }
  100% {
    transform: translateY(0);
    opacity: 1;
  }
}

.hero-tagline {
  font-size: 26rpx;
  color: $text-secondary;
  animation: fadeIn 0.5s ease-out 0.4s both;
}

@keyframes fadeIn {
  0% { opacity: 0; }
  100% { opacity: 1; }
}

// 登录卡片
.login-card {
  width: 100%;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20rpx);
  border-radius: 40rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 20rpx 60rpx rgba($primary, 0.12), 0 8rpx 24rpx rgba(0, 0, 0, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.5);
  position: relative;
  z-index: 1;
}

// 主登录按钮
.primary-btn {
  width: 100%;
  height: 100rpx;
  background: linear-gradient(135deg, #07c160 0%, #06ad56 100%);
  border-radius: 50rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 8rpx 32rpx rgba(12, 193, 96, 0.35);
  position: relative;
  overflow: hidden;

  &:active {
    transform: scale(0.98);
  }

  &::after { border: none; }

  &:disabled {
    opacity: 0.7;
  }
}

.btn-content {
  display: flex;
  align-items: center;
  gap: 12rpx;
  z-index: 1;
}

.btn-text {
  font-size: 34rpx;
  color: white;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.btn-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: shine 3s ease-in-out infinite;
}

@keyframes shine {
  0% { left: -100%; }
  50%, 100% { left: 100%; }
}

// 分割线
.divider {
  display: flex;
  align-items: center;
  margin: 40rpx 0 32rpx;
}

.divider-line {
  flex: 1;
  height: 1rpx;
  background: linear-gradient(90deg, transparent, rgba($text-muted, 0.2), transparent);
}

.divider-text {
  padding: 0 24rpx;
  font-size: 24rpx;
  color: $text-muted;
}

// 登录tabs
.login-tabs {
  display: flex;
  background: #f1f5f9;
  border-radius: 16rpx;
  padding: 6rpx;
  margin-bottom: 32rpx;
}

.tab-item {
  flex: 1;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  transition: all 0.3s ease;

  text {
    font-size: 28rpx;
    color: $text-secondary;
    font-weight: 500;
  }

  &.active {
    background: white;
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);

    text {
      color: $primary;
      font-weight: 600;
    }
  }
}

// 表单
.form-section {
  animation: fadeSlide 0.3s ease-out;
}

@keyframes fadeSlide {
  0% {
    opacity: 0;
    transform: translateY(-10rpx);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.input-item {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.input-label {
  font-size: 26rpx;
  color: $text-secondary;
  font-weight: 500;
  padding-left: 8rpx;
}

.input-field {
  width: 100%;
  height: 88rpx;
  background: white;
  border: 2rpx solid #e2e8f0;
  border-radius: 20rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: $text-primary;
  transition: all 0.3s ease;

  &:focus {
    border-color: $primary;
    box-shadow: 0 0 0 4rpx rgba($primary, 0.1);
  }

  &::placeholder {
    color: $text-muted;
  }
}

.code-row {
  display: flex;
  gap: 16rpx;
}

.code-input {
  flex: 1;
}

.code-btn {
  width: 180rpx;
  height: 88rpx;
  background: linear-gradient(135deg, $primary 0%, $accent 100%);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  text {
    font-size: 26rpx;
    color: white;
    font-weight: 600;
  }

  &.disabled {
    background: #e2e8f0;

    text {
      color: $text-muted;
    }
  }
}

// 提交按钮
.submit-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, $primary 0%, $accent 100%);
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  margin-top: 32rpx;
  box-shadow: 0 8rpx 32rpx rgba($primary, 0.3);

  text {
    font-size: 32rpx;
    color: white;
    font-weight: 700;
  }

  &:active {
    transform: scale(0.98);
  }

  &:disabled {
    opacity: 0.6;
  }

  &::after { border: none; }
}

// 用户协议
.agreement {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin-top: 32rpx;
  padding: 0 20rpx;
}

.agreement-check {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: rgba($primary, 0.1);
  border: 2rpx solid $primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.check-icon {
  font-size: 20rpx;
  color: $primary;
  font-weight: 700;
}

.agreement-text {
  font-size: 22rpx;
  color: $text-muted;
  line-height: 1.6;
}

.link {
  color: $primary;
}

// 底部
.bottom-decoration {
  position: relative;
  z-index: 1;
  margin-top: auto;
  padding-top: 60rpx;
}

.copyright {
  font-size: 20rpx;
  color: $text-muted;
}
</style>
