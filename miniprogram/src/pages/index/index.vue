<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const { isLoggedIn, userInfo } = userStore

// 功能模块
const features = ref([
  {
    id: 'resume',
    title: '简历管理',
    desc: '智能分析简历',
    icon: '📄',
    gradient: 'linear-gradient(135deg, #6366f1 0%, #818cf8 100%)',
    bgColor: '#eef2ff',
    path: '/pages/resume/list'
  },
  {
    id: 'interview',
    title: 'AI面试',
    desc: '模拟面试实战',
    icon: '💼',
    gradient: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
    bgColor: '#e0e7ff',
    path: '/pages/interview/list'
  },
  {
    id: 'knowledge',
    title: '知识库',
    desc: '智能问答助手',
    icon: '📚',
    gradient: 'linear-gradient(135deg, #8b5cf6 0%, #a78bfa 100%)',
    bgColor: '#f5f3ff',
    path: '/pages/knowledge/list'
  },
  {
    id: 'notification',
    title: '消息',
    desc: '最新动态',
    icon: '🔔',
    gradient: 'linear-gradient(135deg, #0ea5e9 0%, #38bdf8 100%)',
    bgColor: '#e0f2fe',
    path: '/pages/notification/list'
  }
])

// 快捷入口
const quickActions = ref([
  { id: 'points', title: '积分商城', icon: '🎁', gradient: 'linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%)', path: '/pages/points/index' }
])

const goToFeature = (path: string) => {
  uni.navigateTo({ url: path })
}

const goToLogin = () => {
  uni.navigateTo({ url: '/pages/auth/login' })
}

const goToProfile = () => {
  uni.navigateTo({ url: '/pages/profile/index' })
}
</script>

<template>
  <view class="index-container">
    <!-- 顶部装饰 -->
    <view class="top-decoration">
      <view class="decoration-circle decoration-1"></view>
      <view class="decoration-circle decoration-2"></view>
      <view class="decoration-circle decoration-3"></view>
    </view>

    <!-- 顶部区域 -->
    <view class="hero-section">
      <view class="hero-content">
        <view class="hero-top">
          <view class="app-info">
            <text class="app-name">AI面试指南</text>
            <text class="app-slogan">智能面试助手</text>
          </view>
          <view class="avatar-wrap" @click="goToProfile">
            <view class="avatar">
              <text class="avatar-text">{{ isLoggedIn && userInfo?.nickname ? userInfo.nickname[0] : '我' }}</text>
            </view>
          </view>
        </view>

        <!-- 欢迎卡片 -->
        <view class="welcome-card" @click="isLoggedIn || goToLogin()">
          <view class="card-pattern"></view>
          <view class="card-content">
            <view class="card-left">
              <text class="greeting">{{ isLoggedIn ? '欢迎回来' : '登录体验更多' }}</text>
              <text class="username" v-if="isLoggedIn">{{ userInfo?.nickname || '用户' }}</text>
              <text class="tip">{{ isLoggedIn ? '准备开始面试了吗？' : 'AI助力每一次面试成功' }}</text>
            </view>
            <view class="card-right">
              <text class="arrow">→</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 功能区域 -->
    <view class="features-section">
      <view class="section-header">
        <text class="section-title">核心功能</text>
        <text class="section-desc">探索AI面试的无限可能</text>
      </view>

      <view class="features-grid">
        <view
          v-for="item in features"
          :key="item.id"
          class="feature-card"
          :style="{ '--card-bg': item.bgColor }"
          @click="goToFeature(item.path)"
        >
          <view class="card-inner">
            <view class="feature-icon" :style="{ background: item.gradient }">
              <text>{{ item.icon }}</text>
            </view>
            <view class="feature-info">
              <text class="feature-title">{{ item.title }}</text>
              <text class="feature-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 快捷入口 -->
    <view class="quick-section" v-if="isLoggedIn">
      <view class="quick-grid">
        <view
          v-for="item in quickActions"
          :key="item.id"
          class="quick-card"
          :style="{ background: item.gradient }"
          @click="goToFeature(item.path)"
        >
          <text class="quick-icon">{{ item.icon }}</text>
          <text class="quick-title">{{ item.title }}</text>
        </view>
      </view>
    </view>

    <!-- 底部 -->
    <view class="bottom-section">
      <text class="copyright">© 2026 AI面试指南</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
// 清新的薄荷绿配色
$primary: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg: #f8fafc;
$card-bg: #ffffff;
$text-primary: #1e293b;
$text-secondary: #475569;
$text-muted: #94a3b8;

.index-container {
  min-height: 100vh;
  background: $bg;
  position: relative;
  overflow: hidden;
}

// 顶部装饰
.top-decoration {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 400rpx;
  pointer-events: none;
  z-index: 0;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
}

.decoration-1 {
  width: 500rpx;
  height: 500rpx;
  background: linear-gradient(135deg, rgba($primary-light, 0.3) 0%, rgba($primary, 0.1) 100%);
  top: -200rpx;
  right: -150rpx;
  animation: float 8s ease-in-out infinite;
}

.decoration-2 {
  width: 300rpx;
  height: 300rpx;
  background: linear-gradient(135deg, rgba($accent, 0.2) 0%, rgba($primary-light, 0.1) 100%);
  top: 50rpx;
  left: -100rpx;
  animation: float 10s ease-in-out infinite reverse;
}

.decoration-3 {
  width: 200rpx;
  height: 200rpx;
  background: linear-gradient(135deg, rgba($primary, 0.15) 0%, transparent 100%);
  top: 150rpx;
  right: 50rpx;
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20rpx) rotate(5deg);
  }
}

// 顶部英雄区域
.hero-section {
  position: relative;
  padding: 120rpx 40rpx 60rpx;
  z-index: 1;
}

.hero-content {
  max-width: 100%;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 40rpx;
}

.app-info {
  display: flex;
  flex-direction: column;
}

.app-name {
  font-size: 48rpx;
  font-weight: 800;
  color: $text-primary;
  letter-spacing: 2rpx;
  background: linear-gradient(135deg, $primary-dark 0%, $primary 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.app-slogan {
  font-size: 24rpx;
  color: $text-muted;
  margin-top: 8rpx;
  font-weight: 500;
}

.avatar-wrap {
  position: relative;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary 0%, $accent 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba($primary, 0.3);
}

.avatar-text {
  font-size: 32rpx;
  font-weight: 700;
  color: white;
}

// 欢迎卡片
.welcome-card {
  position: relative;
  background: $card-bg;
  border-radius: 32rpx;
  overflow: hidden;
  box-shadow: 0 12rpx 40rpx rgba($primary, 0.12), 0 4rpx 12rpx rgba(0, 0, 0, 0.04);
}

.card-pattern {
  position: absolute;
  top: -50rpx;
  right: -50rpx;
  width: 200rpx;
  height: 200rpx;
  background: radial-gradient(circle, rgba($primary-light, 0.3) 0%, transparent 70%);
  border-radius: 50%;
}

.card-content {
  position: relative;
  padding: 36rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-left {
  display: flex;
  flex-direction: column;
}

.greeting {
  font-size: 28rpx;
  color: $text-secondary;
  font-weight: 500;
}

.username {
  font-size: 36rpx;
  font-weight: 700;
  color: $primary;
  margin-top: 4rpx;
}

.tip {
  font-size: 24rpx;
  color: $text-muted;
  margin-top: 8rpx;
}

.card-right {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary 0%, $accent 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6rpx 20rpx rgba($primary, 0.3);
}

.arrow {
  font-size: 32rpx;
  color: white;
  font-weight: 600;
}

// 功能区域
.features-section {
  position: relative;
  padding: 48rpx 40rpx;
  z-index: 1;
}

.section-header {
  margin-bottom: 36rpx;
}

.section-title {
  font-size: 38rpx;
  font-weight: 700;
  color: $text-primary;
  display: block;
}

.section-desc {
  font-size: 24rpx;
  color: $text-muted;
  margin-top: 8rpx;
  display: block;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24rpx;
}

.feature-card {
  background: var(--card-bg);
  border-radius: 28rpx;
  padding: 4rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:active {
    transform: scale(0.97);
    box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.06);
  }
}

.card-inner {
  background: var(--card-bg);
  border-radius: 24rpx;
  padding: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.feature-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.1);
}

.feature-info {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.feature-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
}

.feature-desc {
  font-size: 22rpx;
  color: $text-muted;
  margin-top: 6rpx;
}

// 快捷入口
.quick-section {
  padding: 20rpx 40rpx 48rpx;
  z-index: 1;
  position: relative;
}

.quick-grid {
  display: flex;
  gap: 24rpx;
}

.quick-card {
  flex: 1;
  border-radius: 24rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  box-shadow: 0 6rpx 24rpx rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.97);
  }
}

.quick-icon {
  font-size: 32rpx;
}

.quick-title {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-primary;
}

// 底部
.bottom-section {
  padding: 40rpx;
  text-align: center;
  position: relative;
  z-index: 1;
}

.copyright {
  font-size: 22rpx;
  color: $text-muted;
}
</style>
