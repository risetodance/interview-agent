<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '../../stores/user'
import Icon from '../../components/common/Icon.vue'
import { wechatLogin, emailLogin, emailRegister } from '../../api/auth'
import { login } from '../../api/user'
import { isH5 } from '../../utils/env'
import { useEmailCode } from '../../composables/useEmailCode'
import { isValidEmail } from '../../utils/validate'

// 用户 Store
const userStore = useUserStore()

// 加载状态
const isLoading = ref(false)

// N8：用户协议勾选状态（原硬编码 ✓ 纯展示，登录前必须真实校验）
const agreed = ref(false)

// 微信小程序登录
const handleWechatLogin = async () => {
  if (isLoading.value) return
  // N8：协议未勾选禁止登录
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议和隐私政策', icon: 'none' })
    return
  }
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
        // ignore profile error
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
    // C1：后端 LoginResponse 不含 refreshToken，setToken 第二参可省略
    userStore.setToken(result.token)
    // B9/N3: 后端 LoginResponse 仅含 token/userId/username/role（无 nickname/avatar），
    // 统一调用 fetchUserInfo 拉取权威用户信息，确保登录态成立（isLoggedIn = !!token && !!userInfo）
    // B9 回归修复：fetchUserInfo 失败不阻塞登录主流程（token 已存），后续 401 兜底重登
    try {
      await userStore.fetchUserInfo()
    } catch (e) {
      console.error('获取用户信息失败:', e)
    }

    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      // B10: reLaunch 清空页面栈，避免返回到登录页重复登录
      uni.reLaunch({ url: '/pages/index/index' })
    }, 500)
  } catch (error: any) {
    uni.showToast({ title: error.message || '登录失败，请重试', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

// 账号密码登录（M6：删除无引用死变量 showAccountLogin）
const username = ref('')
const password = ref('')

// 邮箱验证码登录（两步式：第一步登录/识别未注册，第二步设置用户名密码完成注册）
const showEmailLogin = ref(false)
const email = ref('')
const emailCode = ref('')
// SIM-3：发码防抖/倒计时/定时器清理统一由 useEmailCode 封装
const { codeText, isCounting, send: sendCode } = useEmailCode('LOGIN')
// 两步式第二步状态：true 表示邮箱已验证但未注册，正在设置账号信息
const isRegisterStep = ref(false)
const regUsername = ref('')
const regPassword = ref('')

// 登录成功后的统一处理：存 token → 拉取用户信息 → 回首页
// C1：LoginResult 已收窄为无 refreshToken，仅需 token（setToken 第二参可选）
const handleLoginSuccess = async (result: { token: string }) => {
  userStore.setToken(result.token)
  // B9 回归修复：fetchUserInfo 失败不阻塞登录主流程
  try {
    await userStore.fetchUserInfo()
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }
  uni.showToast({ title: '登录成功', icon: 'success' })
  setTimeout(() => {
    // B10: reLaunch 清空页面栈
    uni.reLaunch({ url: '/pages/index/index' })
  }, 500)
}

const handleAccountLogin = async () => {
  if (isLoading.value) return
  // N8：协议未勾选禁止登录
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议和隐私政策', icon: 'none' })
    return
  }
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    // 统一走正式 login 接口（原 H5 分支的 /api/auth/login/test 已随后端接口一并删除）
    const result = await login({ username: username.value, password: password.value })
    await handleLoginSuccess(result)
  } catch (error: any) {
    uni.showToast({ title: error.message || '登录失败', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

// 获取邮箱验证码（第一步）：仅做邮箱校验，发码/防抖/倒计时交给 composable
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

// 邮箱验证码登录（第一步）→ 已注册直接登录；未注册进入第二步
const handleEmailLogin = async () => {
  // N8：协议未勾选禁止登录
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议和隐私政策', icon: 'none' })
    return
  }
  if (!email.value || !emailCode.value) {
    uni.showToast({ title: '请填写邮箱和验证码', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const result = await emailLogin(email.value, emailCode.value)
    if (result.needsRegister) {
      // 验证码已由后端还原（5 分钟内有效），进入第二步设置账号信息
      isRegisterStep.value = true
      regUsername.value = email.value.split('@')[0]
      uni.showToast({ title: '该邮箱未注册，请设置账号信息', icon: 'none' })
      return
    }
    await handleLoginSuccess(result.login!)
  } catch (error: any) {
    uni.showToast({ title: error.message || '登录失败', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

// 邮箱验证码注册（两步式第二步）：设置用户名密码，完成即登录
const handleEmailRegister = async () => {
  // M2：与 handleEmailLogin 一致，注册完成即登录，同样需先勾选协议
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议和隐私政策', icon: 'none' })
    return
  }
  if (!regUsername.value || !regPassword.value) {
    uni.showToast({ title: '请设置用户名和密码', icon: 'none' })
    return
  }
  if (regUsername.value.length < 3) {
    uni.showToast({ title: '用户名至少 3 个字符', icon: 'none' })
    return
  }
  if (regPassword.value.length < 6) {
    uni.showToast({ title: '密码至少 6 位', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const result = await emailRegister({
      email: email.value,
      code: emailCode.value,
      username: regUsername.value,
      password: regPassword.value
    })
    await handleLoginSuccess(result)
  } catch (error: any) {
    uni.showToast({ title: error.message || '注册失败', icon: 'none' })
    // ST-5：验证码失效时自动回第一步重新收码（对齐 Web 行为）
    // 结构化业务码优先（12002 过期/12003 错误/12004 错误次数过多），消息含"验证码"兜底
    const code = error?.code
    if (code === 12002 || code === 12003 || code === 12004 ||
        (code === undefined && (error.message || '').includes('验证码'))) {
      emailCode.value = ''
      isRegisterStep.value = false
    }
  } finally {
    isLoading.value = false
  }
}

// 第二步返回第一步（重新收码）
const backToEmailStep = () => {
  isRegisterStep.value = false
  regUsername.value = ''
  regPassword.value = ''
  // M3：清空旧验证码，保证"返回重新获取验证码"文案与行为一致
  emailCode.value = ''
}

// 切回"账号登录"tab（M4：顺带复位两步式注册残留，避免再次进入邮箱登录时串脏数据）
const switchToAccountLogin = () => {
  showEmailLogin.value = false
  isRegisterStep.value = false
  regUsername.value = ''
  regPassword.value = ''
}

// 忘记密码
const goToForgotPassword = () => {
  uni.navigateTo({ url: '/pages/auth/forgot-password' })
}
</script>

<template>
  <view class="login-container">
    <!-- 顶部品牌区 -->
    <view class="hero">
      <view class="brand-badge">
        <Icon name="sparkles" :size="44" color="#fff" />
      </view>
      <text class="brand-title">AI 面试指南</text>
      <text class="brand-sub">多视角面试官 · 自适应难度 · 实时评分</text>
    </view>

    <!-- 登录卡片 -->
    <view class="login-card">
      <!-- 主登录按钮 -->
      <button class="primary-btn" :disabled="isLoading" @click="handleWechatLogin">
        <Icon name="message" :size="20" color="#fff" />
        <text class="btn-text">{{ isLoading ? '登录中...' : '微信一键登录' }}</text>
      </button>

      <!-- 分割线 -->
      <view class="divider">
        <view class="divider-line"></view>
        <text class="divider-text">其他方式</text>
        <view class="divider-line"></view>
      </view>

      <!-- 登录方式 tabs -->
      <view class="login-tabs">
        <view
          class="tab-item"
          :class="{ active: !showEmailLogin }"
          @click="switchToAccountLogin"
        >
          <text>账号登录</text>
        </view>
        <view
          class="tab-item"
          :class="{ active: showEmailLogin }"
          @click="showEmailLogin = true"
        >
          <text>邮箱登录</text>
        </view>
      </view>

      <!-- 账号密码表单 -->
      <view v-if="!showEmailLogin" class="form-section">
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
        <!-- 忘记密码入口（账号密码场景） -->
        <view class="forgot-row" @click="goToForgotPassword">
          <text class="forgot-link">忘记密码？</text>
        </view>
      </view>

      <!-- 邮箱验证码表单 -->
      <view v-else class="form-section">
        <!-- 第一步：邮箱 + 验证码 -->
        <view v-if="!isRegisterStep" class="input-group">
          <view class="input-item">
            <text class="input-label">邮箱</text>
            <input v-model="email" type="text" placeholder="请输入邮箱地址" class="input-field" />
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
        </view>
        <!-- 第二步：邮箱已验证未注册 → 设置用户名密码完成注册 -->
        <view v-else class="input-group">
          <view class="input-item">
            <text class="input-label">邮箱（已验证）</text>
            <input :value="email" type="text" disabled class="input-field input-disabled" />
          </view>
          <view class="input-item">
            <text class="input-label">用户名</text>
            <input v-model="regUsername" type="text" placeholder="3-50 个字符" class="input-field" />
          </view>
          <view class="input-item">
            <text class="input-label">设置密码</text>
            <input v-model="regPassword" type="password" placeholder="至少 6 位" class="input-field" />
          </view>
        </view>
        <button
          v-if="!isRegisterStep"
          class="submit-btn"
          :disabled="isLoading"
          @click="handleEmailLogin"
        >
          <text>登录 / 注册</text>
        </button>
        <view v-else class="register-actions">
          <button class="submit-btn" :disabled="isLoading" @click="handleEmailRegister">
            <text>完成注册并登录</text>
          </button>
          <view class="back-link" @click="backToEmailStep">
            <text>返回重新获取验证码</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 用户协议 -->
    <view class="agreement">
      <view class="agreement-check" :class="{ checked: agreed }" @click="agreed = !agreed">
        <Icon v-if="agreed" name="check" :size="14" color="#fff" />
      </view>
      <text class="agreement-text">
        我已阅读并同意
        <text class="link">《用户协议》</text>
        和
        <text class="link">《隐私政策》</text>
      </text>
    </view>

    <!-- 底部 -->
    <view class="bottom">
      <text class="copyright">© 2026 AI 面试指南</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.login-container {
  min-height: 100vh;
  background: $bg;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: calc(env(safe-area-inset-top, 0px) + 120rpx) 48rpx 60rpx;
}

// 顶部品牌区
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 72rpx;
}

.brand-badge {
  width: 128rpx;
  height: 128rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
  box-shadow: 0 12rpx 32rpx rgba($primary, 0.3);
}

.brand-title {
  font-size: 48rpx;
  font-weight: 800;
  color: $text-primary;
  letter-spacing: 2rpx;
  margin-bottom: 12rpx;
}

.brand-sub {
  font-size: 24rpx;
  color: $text-muted;
  letter-spacing: 1rpx;
}

// 登录卡片
.login-card {
  width: 100%;
  background: $card-bg;
  border-radius: 32rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.06);
}

// 主登录按钮（微信绿）
.primary-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, #07c160 0%, #06ad56 100%);
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(7, 193, 96, 0.3);

  &::after {
    border: none;
  }

  &:active {
    transform: scale(0.98);
  }

  .btn-text {
    font-size: 32rpx;
    font-weight: 600;
    color: #fff;
  }
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
  background: #e2e8f0;
}

.divider-text {
  padding: 0 24rpx;
  font-size: 24rpx;
  color: $text-muted;
}

// tabs
.login-tabs {
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

// 表单
.form-section {
  display: flex;
  flex-direction: column;
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

// 提交按钮
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

// 用户协议
.agreement {
  display: flex;
  align-items: center;
  margin-top: 40rpx;
}

.agreement-check {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16rpx;
  flex-shrink: 0;

  &.checked {
    background: $primary;
    border-color: $primary;
  }
}

.agreement-text {
  font-size: 24rpx;
  color: $text-muted;
}

.link {
  color: $primary;
}

// 忘记密码入口
.forgot-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20rpx;
}

.forgot-link {
  font-size: 26rpx;
  color: $primary;
}

// 第二步禁用态邮箱输入框
.input-disabled {
  opacity: 0.6;
}

// 第二步返回链接
.back-link {
  display: flex;
  justify-content: center;
  margin-top: 20rpx;
  font-size: 26rpx;
  color: $text-muted;
}

// 底部
.bottom {
  margin-top: auto;
  padding-top: 60rpx;
}

.copyright {
  font-size: 22rpx;
  color: $text-muted;
}
</style>
