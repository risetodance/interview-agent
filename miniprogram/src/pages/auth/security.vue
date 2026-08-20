<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '../../stores/user'
import Icon from '../../components/common/Icon.vue'
import { changePassword, changePasswordByEmail, bindEmail } from '../../api/user'
import { useEmailCode } from '../../composables/useEmailCode'
import { isValidEmail } from '../../utils/validate'

const userStore = useUserStore()
const { userInfo, isLoggedIn } = storeToRefs(userStore)

onMounted(() => {
  if (isLoggedIn.value && !userInfo.value?.email) {
    // ST-1：空 catch 补日志，避免静默吞错
    userStore.fetchUserInfo().catch((e) => console.error('刷新用户信息失败', e))
  }
})

// ========== 修改密码（双方式） ==========
// pwdTab: 'old' 旧密码方式 | 'email' 邮箱验证码方式
const pwdTab = ref<'old' | 'email'>('old')
const oldPassword = ref('')
const pwdCode = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const isChangingPwd = ref(false)

// SIM-3：改密场景发码至当前绑定邮箱（各自独立倒计时，防抖/清理由 composable 封装）
const {
  codeText: pwdCodeText,
  isCounting: pwdCounting,
  send: sendPwdCode
} = useEmailCode('CHANGE_PASSWORD', '验证码已发送至绑定邮箱')

const currentEmail = computed(() => userInfo.value?.email || '')

const handleGetPwdCode = () => {
  if (!currentEmail.value) {
    uni.showToast({ title: '当前账号未绑定邮箱，请先在下方绑定', icon: 'none' })
    return
  }
  sendPwdCode(currentEmail.value)
}

const handleChangePassword = async () => {
  if (isChangingPwd.value) return
  if (newPwd.value.length < 6) {
    uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
    return
  }
  if (pwdTab.value === 'old' && !oldPassword.value) {
    uni.showToast({ title: '请输入原密码', icon: 'none' })
    return
  }
  if (pwdTab.value === 'email' && !pwdCode.value) {
    uni.showToast({ title: '请输入邮箱验证码', icon: 'none' })
    return
  }

  isChangingPwd.value = true
  try {
    if (pwdTab.value === 'old') {
      await changePassword(oldPassword.value, newPwd.value)
    } else {
      await changePasswordByEmail(pwdCode.value, newPwd.value)
    }
    uni.showToast({ title: '密码修改成功', icon: 'success' })
    oldPassword.value = ''
    pwdCode.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
  } catch (error: any) {
    uni.showToast({ title: error.message || '修改失败', icon: 'none' })
  } finally {
    isChangingPwd.value = false
  }
}

// ========== 绑定/换绑邮箱（新邮箱单验证） ==========
const newEmail = ref('')
const bindCode = ref('')
const isBinding = ref(false)
// SIM-3：绑定场景发码至新邮箱
const {
  codeText: bindCodeText,
  isCounting: bindCounting,
  send: sendBindCode
} = useEmailCode('BIND_EMAIL', '验证码已发送至新邮箱')

const handleGetBindCode = () => {
  // 空邮箱同样被此正则拦截并提示
  if (!isValidEmail(newEmail.value)) {
    uni.showToast({ title: '请输入正确的新邮箱地址', icon: 'none' })
    return
  }
  sendBindCode(newEmail.value)
}

const handleBindEmail = async () => {
  if (isBinding.value) return
  if (!isValidEmail(newEmail.value)) {
    uni.showToast({ title: '请输入正确的新邮箱地址', icon: 'none' })
    return
  }
  if (!bindCode.value) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }

  isBinding.value = true
  try {
    await bindEmail(newEmail.value, bindCode.value)
    uni.showToast({ title: '邮箱绑定成功', icon: 'success' })
    newEmail.value = ''
    bindCode.value = ''
    // 刷新用户信息以显示新邮箱（ST-1：空 catch 补日志，避免静默吞错）
    userStore.fetchUserInfo().catch((e) => console.error('刷新用户信息失败', e))
  } catch (error: any) {
    uni.showToast({ title: error.message || '绑定失败', icon: 'none' })
  } finally {
    isBinding.value = false
  }
}
</script>

<template>
  <view class="security-container">
    <!-- 页面标题 -->
    <view class="page-header">
      <text class="page-title">账号与安全</text>
      <text class="page-sub">管理登录凭证与绑定邮箱</text>
    </view>

    <!-- 修改密码 -->
    <view class="section-card">
      <view class="section-title-row">
        <view class="section-icon pwd">
          <Icon name="key" size="28rpx" color="#fff" />
        </view>
        <text class="section-title">修改密码</text>
      </view>

      <!-- 方式切换 -->
      <view class="way-tabs">
        <view class="way-tab" :class="{ active: pwdTab === 'old' }" @click="pwdTab = 'old'">
          <text>原密码验证</text>
        </view>
        <view class="way-tab" :class="{ active: pwdTab === 'email' }" @click="pwdTab = 'email'">
          <text>邮箱验证码</text>
        </view>
      </view>

      <view class="input-group">
        <view v-if="pwdTab === 'old'" class="input-item">
          <text class="input-label">原密码</text>
          <input v-model="oldPassword" type="password" placeholder="请输入原密码" class="input-field" />
        </view>
        <view v-else class="input-item">
          <text class="input-label">验证码（发送至 {{ currentEmail || '未绑定邮箱' }}）</text>
          <view class="code-row">
            <input v-model="pwdCode" type="number" placeholder="邮箱验证码" maxlength="6" class="input-field code-input" />
            <view class="code-btn" :class="{ disabled: pwdCounting }" @click="handleGetPwdCode">
              <text>{{ pwdCodeText }}</text>
            </view>
          </view>
        </view>
        <view class="input-item">
          <text class="input-label">新密码</text>
          <input v-model="newPwd" type="password" placeholder="至少 6 位" class="input-field" />
        </view>
        <view class="input-item">
          <text class="input-label">确认新密码</text>
          <input v-model="confirmPwd" type="password" placeholder="再次输入新密码" class="input-field" />
        </view>
      </view>

      <button class="submit-btn" :disabled="isChangingPwd" @click="handleChangePassword">
        <text>{{ isChangingPwd ? '提交中...' : '修改密码' }}</text>
      </button>
    </view>

    <!-- 绑定/换绑邮箱 -->
    <view class="section-card">
      <view class="section-title-row">
        <view class="section-icon email">
          <Icon name="mail" size="28rpx" color="#fff" />
        </view>
        <text class="section-title">绑定邮箱</text>
      </view>

      <view class="current-email">
        <text class="current-label">当前邮箱</text>
        <text class="current-value">{{ currentEmail || '未绑定' }}</text>
      </view>

      <view class="input-group">
        <view class="input-item">
          <text class="input-label">新邮箱</text>
          <input v-model="newEmail" type="text" placeholder="请输入要绑定的新邮箱" class="input-field" />
        </view>
        <view class="input-item">
          <text class="input-label">验证码</text>
          <view class="code-row">
            <input v-model="bindCode" type="number" placeholder="新邮箱收到的验证码" maxlength="6" class="input-field code-input" />
            <view class="code-btn" :class="{ disabled: bindCounting }" @click="handleGetBindCode">
              <text>{{ bindCodeText }}</text>
            </view>
          </view>
        </view>
      </view>

      <button class="submit-btn" :disabled="isBinding" @click="handleBindEmail">
        <text>{{ isBinding ? '绑定中...' : (currentEmail ? '换绑邮箱' : '绑定邮箱') }}</text>
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.security-container {
  min-height: 100vh;
  background: $bg;
  padding: 40rpx 32rpx 60rpx;
}

.page-header {
  padding: 20rpx 8rpx 32rpx;

  .page-title {
    display: block;
    font-size: 40rpx;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 8rpx;
  }

  .page-sub {
    display: block;
    font-size: 26rpx;
    color: $text-muted;
  }
}

.section-card {
  background: $card-bg;
  border-radius: 24rpx;
  padding: 36rpx 32rpx;
  margin-bottom: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(15, 23, 42, 0.05);
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 28rpx;
}

.section-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &.pwd {
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  }

  &.email {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  }
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
}

// 方式切换 tabs（复用登录页语言）
.way-tabs {
  display: flex;
  gap: 16rpx;
  margin-bottom: 28rpx;
}

.way-tab {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14rpx;
  background: $bg;
  font-size: 26rpx;
  color: $text-secondary;
  transition: all 0.2s;

  &.active {
    background: rgba($primary, 0.1);
    color: $primary;
    font-weight: 600;
  }
}

.current-email {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: $bg;
  border-radius: 14rpx;
  margin-bottom: 28rpx;

  .current-label {
    font-size: 26rpx;
    color: $text-secondary;
  }

  .current-value {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
  }
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-bottom: 32rpx;
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
  height: 88rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  border-radius: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;

  &::after {
    border: none;
  }

  &:active {
    transform: scale(0.98);
  }
}
</style>
