<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../stores/user'

// 用户 Store
const userStore = useUserStore()
const { userInfo, points, isLoggedIn } = userStore

// 加载状态
const isLoading = ref(false)

// 用户名显示
const displayName = computed(() => {
  return userInfo.value?.nickname || userInfo.value?.username || '未设置昵称'
})

// 默认头像
const defaultAvatar = 'https://mmbiz.qpic.cn/mmbiz/icTdbqWNOwNRna42FI242Lxia07jQodd2SJPG10Lskb46rSE8MP17pnp177hR8sIhLiaG2iafw9mLqQO3M3T2oKfTqicA/0'

// 头像
const avatarUrl = computed(() => {
  return userInfo.value?.avatar || defaultAvatar
})

// 页面显示时刷新用户信息
onMounted(() => {
  if (isLoggedIn.value) {
    userStore.fetchUserInfo()
  }
})

// 跳转到积分记录
const goToPoints = () => {
  uni.navigateTo({
    url: '/pages/points/index'
  })
}

// 跳转到我的简历
const goToResumes = () => {
  uni.navigateTo({
    url: '/pages/resume/list'
  })
}

// 跳转到我的面试
const goToInterviews = () => {
  uni.navigateTo({
    url: '/pages/interview/list'
  })
}

// 跳转到我的知识库
const goToKnowledgeBase = () => {
  uni.navigateTo({
    url: '/pages/knowledge/list'
  })
}

// 退出登录
const handleLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        // 跳转到登录页
        uni.reLaunch({
          url: '/pages/auth/login'
        })
      }
    }
  })
}

// 选择并上传头像
const chooseAvatar = async () => {
  if (isLoading.value) return

  try {
    const res = await uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera']
    })

    if (res.tempFilePaths && res.tempFilePaths.length > 0) {
      isLoading.value = true

      // TODO: 调用上传头像接口
      // const { uploadAvatar } = await import('../api/user')
      // const result = await uploadAvatar(res.tempFilePaths[0])

      uni.showToast({
        title: '头像上传成功',
        icon: 'success'
      })
    }
  } catch (error: any) {
    console.error('选择头像失败:', error)
    if (error.errMsg && !error.errMsg.includes('cancel')) {
      uni.showToast({
        title: error.message || '上传失败',
        icon: 'none'
      })
    }
  } finally {
    isLoading.value = false
  }
}

// 菜单项配置（移除会员中心）
const menuItems = computed(() => [
  {
    icon: 'points',
    title: '积分记录',
    subtitle: `当前积分: ${points}`,
    path: '/pages/points/index'
  },
  {
    icon: 'resume',
    title: '我的简历',
    subtitle: '管理您的简历',
    path: '/pages/resume/list'
  },
  {
    icon: 'interview',
    title: '我的面试',
    subtitle: '查看面试记录',
    path: '/pages/interview/list'
  },
  {
    icon: 'knowledge',
    title: '我的知识库',
    subtitle: '个人知识管理',
    path: '/pages/knowledge/list'
  }
])

// 设置项配置
const settingsItems = [
  {
    icon: 'notification',
    title: '消息通知',
    path: '/pages/notification/list'
  }
]

// 处理菜单点击
const handleMenuClick = (item: any) => {
  if (item.path) {
    uni.navigateTo({
      url: item.path
    })
  }
}
</script>

<template>
  <view class="profile-container">
    <!-- 用户信息头部 -->
    <view class="profile-header">
      <!-- 背景装饰 -->
      <view class="header-bg">
        <view class="header-bg-gradient" />
      </view>

      <!-- 用户信息 -->
      <view class="user-info">
        <!-- 头像区域 -->
        <view class="avatar-wrapper" @click="chooseAvatar">
          <image
            class="avatar"
            :src="avatarUrl"
            mode="aspectFill"
          />
          <view class="avatar-edit-icon">
            <text class="icon">编辑</text>
          </view>
        </view>

        <!-- 昵称显示 -->
        <view class="user-detail">
          <view class="nickname-row">
            <text class="nickname">{{ displayName }}</text>
          </view>

          <!-- 未登录提示 -->
          <view v-if="!isLoggedIn" class="login-tip">
            <text class="login-tip-text">登录后可享受更多服务</text>
          </view>
        </view>
      </view>

      <!-- 积分展示卡片 -->
      <view class="points-card" @click="goToPoints">
        <view class="points-icon">
          <text class="points-icon-text">积分</text>
        </view>
        <view class="points-info">
          <text class="points-value">{{ points }}</text>
          <text class="points-label">当前积分</text>
        </view>
        <view class="points-action">
          <text class="points-action-text">签到/兑换</text>
          <text class="arrow">></text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <view class="section-title">
        <text class="section-title-text">我的功能</text>
      </view>

      <view class="menu-grid">
        <view
          v-for="item in menuItems"
          :key="item.title"
          class="menu-item"
          @click="handleMenuClick(item)"
        >
          <view class="menu-item-icon" :class="item.icon">
            <text class="menu-item-icon-text">{{ item.icon === 'points' ? '积分' : item.icon === 'resume' ? '简历' : item.icon === 'interview' ? '面试' : '知识' }}</text>
          </view>
          <text class="menu-item-title">{{ item.title }}</text>
          <text class="menu-item-subtitle">{{ item.subtitle }}</text>
        </view>
      </view>
    </view>

    <!-- 设置菜单 -->
    <view class="settings-section">
      <view class="section-title">
        <text class="section-title-text">其他设置</text>
      </view>

      <view class="settings-list">
        <view
          v-for="item in settingsItems"
          :key="item.title"
          class="settings-item"
          @click="handleMenuClick(item)"
        >
          <text class="settings-item-title">{{ item.title }}</text>
          <view class="settings-item-arrow">
            <text class="arrow">></text>
          </view>
        </view>
      </view>
    </view>

    <!-- 退出登录按钮 -->
    <view v-if="isLoggedIn" class="logout-section">
      <button class="logout-btn" @click="handleLogout">
        <text class="logout-btn-text">退出登录</text>
      </button>
    </view>

    <!-- 未登录状态 -->
    <view v-else class="login-section">
      <button class="login-btn" @click="uni.navigateTo({ url: '/pages/auth/login' })">
        <text class="login-btn-text">立即登录</text>
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
// 配色变量 - 靛蓝清新配色
$primary: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg: #f8fafc;
$card-bg: #ffffff;
$text-primary: #1e293b;
$text-secondary: #475569;
$text-muted: #94a3b8;
$success: #6366f1;
$warning: #f59e0b;
$danger: #ef4444;
$info: #818cf8;

.profile-container {
  min-height: 100vh;
  background-color: $bg;
  padding-bottom: 60rpx;
}

.profile-header {
  position: relative;
  padding-bottom: 160rpx;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 440rpx;
  overflow: hidden;
}

.header-bg-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 550rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 50%, $primary-light 100%);
  border-radius: 0 0 50% 50% / 0 0 40rpx 40rpx;
}

.user-info {
  position: relative;
  display: flex;
  align-items: center;
  padding: 60rpx 40rpx 40rpx;
}

.avatar-wrapper {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: 80rpx;
  overflow: visible;
}

.avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 80rpx;
  border: 6rpx solid #fff;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.15);
}

.avatar-edit-icon {
  position: absolute;
  bottom: -10rpx;
  right: -10rpx;
  width: 56rpx;
  height: 56rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 4rpx solid #fff;
}

.icon {
  font-size: 20rpx;
  color: #fff;
}

.user-detail {
  flex: 1;
  margin-left: 32rpx;
}

.nickname-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.nickname {
  font-size: 40rpx;
  font-weight: bold;
  color: #fff;
}

.login-tip {
  margin-top: 12rpx;
}

.login-tip-text {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.7);
}

.points-card {
  position: absolute;
  left: 30rpx;
  right: 30rpx;
  bottom: -100rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
}

.points-icon {
  width: 80rpx;
  height: 80rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.points-icon-text {
  font-size: 24rpx;
  color: #fff;
  font-weight: 600;
}

.points-info {
  flex: 1;
  margin-left: 24rpx;
}

.points-value {
  font-size: 48rpx;
  font-weight: bold;
  color: #333;
}

.points-label {
  display: block;
  font-size: 24rpx;
  color: #999;
  margin-top: 4rpx;
}

.points-action {
  display: flex;
  align-items: center;
}

.points-action-text {
  font-size: 26rpx;
  color: $primary;
}

.arrow {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #ccc;
}

.menu-section {
  margin-top: 120rpx;
  padding: 0 30rpx;
}

.section-title {
  margin-bottom: 24rpx;
}

.section-title-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.menu-grid {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -12rpx;
}

.menu-item {
  width: calc(50% - 24rpx);
  background: $card-bg;
  border-radius: 24rpx;
  padding: 36rpx 28rpx;
  margin: 0 12rpx 24rpx;
  position: relative;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.98);
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  }
}

.menu-item-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}

.menu-item-icon.points {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
}

.menu-item-icon.resume {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.menu-item-icon.interview {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.menu-item-icon.knowledge {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}

.menu-item-icon-text {
  font-size: 24rpx;
  color: #fff;
  font-weight: 600;
}

.menu-item-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}

.menu-item-subtitle {
  display: block;
  font-size: 24rpx;
  color: #999;
  margin-top: 8rpx;
}

.settings-section {
  margin-top: 20rpx;
  padding: 0 30rpx;
}

.settings-list {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.settings-item:last-child {
  border-bottom: none;
}

.settings-item-title {
  font-size: 30rpx;
  color: #333;
}

.settings-item-arrow {
  display: flex;
  align-items: center;
}

.logout-section,
.login-section {
  padding: 40rpx 30rpx;
}

.logout-btn,
.login-btn {
  width: 100%;
  height: 100rpx;
  border-radius: 50rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.98);
  }
}

.logout-btn {
  background: $card-bg;
  border: 2rpx solid rgba($danger, 0.3);

  .logout-btn-text {
    color: $danger;
  }
}

.login-btn {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  box-shadow: 0 8rpx 32rpx rgba($primary, 0.35);

  .login-btn-text {
    color: #fff;
  }
}

.logout-btn-text {
  font-size: 32rpx;
  font-weight: 600;
}

.login-btn-text {
  font-size: 34rpx;
  font-weight: 600;
}
</style>
