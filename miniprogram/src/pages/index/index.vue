<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../stores/user'
import Icon from '../../components/common/Icon.vue'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const userInfo = computed(() => userStore.userInfo)

// 功能模块：每项一个和谐区分的强调色，破除"一片蓝"的单调
const features = ref([
  {
    id: 'resume',
    title: '简历管理',
    desc: '智能分析简历',
    icon: 'file-text',
    accent: '#0ea5e9',
    tint: '#f0f9ff',
    path: '/pages/resume/list'
  },
  {
    id: 'interview',
    title: 'AI面试',
    desc: '模拟面试实战',
    icon: 'mic',
    accent: '#6366f1',
    tint: '#eef2ff',
    path: '/pages/interview/list'
  },
  {
    id: 'knowledge',
    title: '知识库',
    desc: '智能问答助手',
    icon: 'database',
    accent: '#06b6d4',
    tint: '#ecfeff',
    path: '/pages/knowledge/list'
  },
  {
    id: 'question-bank',
    title: '题库管理',
    desc: '管理面试题库',
    icon: 'library',
    accent: '#14b8a6',
    tint: '#f0fdfa',
    path: '/pages/question-bank/list'
  },
  {
    id: 'notification',
    title: '消息',
    desc: '最新动态',
    icon: 'bell',
    accent: '#f59e0b',
    tint: '#fffbeb',
    path: '/pages/notification/list'
  },
  {
    id: 'points',
    title: '积分商城',
    desc: '兑换惊喜好礼',
    icon: 'gift',
    accent: '#f43f5e',
    tint: '#fff1f2',
    path: '/pages/points/index'
  }
])

const statusBarHeight = ref(0)
onMounted(() => {
  try {
    const sysInfo = (uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()) as any
    statusBarHeight.value = sysInfo.statusBarHeight || 0
  } catch (e) {
    statusBarHeight.value = 0
  }
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(() => {
  if (isLoggedIn.value && userInfo.value?.nickname) return userInfo.value.nickname
  if (isLoggedIn.value && userInfo.value?.username) return userInfo.value.username
  return '面试达人'
})

const goToFeature = (path: string) => {
  uni.navigateTo({ url: path })
}

const goToLogin = () => {
  uni.navigateTo({ url: '/pages/auth/login' })
}

const goToProfile = () => {
  uni.navigateTo({ url: '/pages/profile/index' })
}

const startInterview = () => {
  if (isLoggedIn.value) {
    uni.navigateTo({ url: '/pages/interview/list' })
  } else {
    goToLogin()
  }
}
</script>

<template>
  <view class="index-container">
    <!-- 状态栏安全区占位 -->
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }"></view>

    <!-- 紧凑顶栏 -->
    <view class="topbar">
      <view class="topbar-left">
        <text class="greeting-text">{{ greeting }}，</text>
        <text class="username-text">{{ displayName }}</text>
      </view>
      <view class="avatar-wrap" @click="goToProfile">
        <view class="avatar">
          <text class="avatar-text">{{ displayName.charAt(0) }}</text>
        </view>
      </view>
    </view>

    <scroll-view class="main-scroll" scroll-y :show-scrollbar="false">
      <!-- Hero 主操作卡 -->
      <view class="hero-card" @click="startInterview">
        <view class="hero-pattern"></view>
        <view class="hero-deco">
          <Icon name="sparkles" :size="120" color="rgba(255,255,255,0.2)" />
        </view>
        <view class="hero-body">
          <text class="hero-title">AI 模拟面试</text>
          <text class="hero-sub">多视角面试官 · 自适应难度 · 实时评分</text>
          <view class="hero-cta">
            <text class="hero-cta-text">{{ isLoggedIn ? '立即开始' : '登录后开始' }}</text>
            <Icon name="chevron-right" :size="16" color="#0284c7" />
          </view>
        </view>
      </view>

      <!-- 核心功能 -->
      <view class="features-section">
        <view class="section-header">
          <text class="section-title">核心功能</text>
          <text class="section-desc">探索 AI 面试的无限可能</text>
        </view>

        <view class="features-grid">
          <view
            v-for="item in features"
            :key="item.id"
            class="feature-card"
            :hover-class="'feature-card--pressed'"
            @click="goToFeature(item.path)"
          >
            <view class="feature-icon" :style="{ backgroundColor: item.tint }">
              <Icon :name="item.icon" :size="24" :color="item.accent" />
            </view>
            <view class="feature-info">
              <text class="feature-title">{{ item.title }}</text>
              <text class="feature-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-section">
        <text class="copyright">© 2026 AI 面试指南</text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.index-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $bg;
}

.status-bar {
  flex-shrink: 0;
}

.topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 40rpx 16rpx;
}

.topbar-left {
  display: flex;
  flex-direction: column;
}

.greeting-text {
  font-size: 24rpx;
  color: $text-muted;
  line-height: 1.4;
}

.username-text {
  font-size: 36rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.3;
}

.avatar-wrap {
  padding: 8rpx;
}

.avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 12rpx rgba($primary, 0.3);
}

.avatar-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
}

.main-scroll {
  flex: 1;
  // 不给 scroll-view 设 padding：小程序端 scroll-view 对 padding 的宽度计算有特殊性，
  // 会导致内容（尤其 2 列 grid 右列）溢出视口右边缘被裁，表现为"右侧遮挡"。
  // 改为内部各区块用横向 margin 收窄，scroll-view 本身满宽。
}

// Hero 主操作卡
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #38bdf8 60%, #7dd3fc 100%);
  padding: 48rpx 40rpx;
  margin: 0 32rpx 48rpx;
  box-shadow: 0 12rpx 32rpx rgba(14, 165, 233, 0.28);
}

.hero-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  // 点阵纹理替代漂浮渐变球：轻量、有质感
  background-image: radial-gradient(rgba(255, 255, 255, 0.18) 1.5px, transparent 1.5px);
  background-size: 28rpx 28rpx;
  pointer-events: none;
}

.hero-deco {
  position: absolute;
  top: -16rpx;
  right: -8rpx;
  pointer-events: none;
}

.hero-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
}

.hero-title {
  font-size: 44rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1rpx;
  margin-bottom: 12rpx;
}

.hero-sub {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 32rpx;
  line-height: 1.5;
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  align-self: flex-start;
  background: #fff;
  padding: 16rpx 32rpx;
  border-radius: 999rpx;
}

.hero-cta-text {
  font-size: 28rpx;
  font-weight: 600;
  color: $primary-dark;
}

.features-section {
  margin: 0 32rpx 40rpx;
}

.section-header {
  margin-bottom: 24rpx;
}

.section-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 4rpx;
}

.section-desc {
  font-size: 24rpx;
  color: $text-muted;
}

.features-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: $card-bg;
  border-radius: 24rpx;
  padding: 28rpx 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(15, 23, 42, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.feature-card--pressed {
  transform: scale(0.97);
  box-shadow: 0 1rpx 6rpx rgba(15, 23, 42, 0.06);
}

.feature-icon {
  flex-shrink: 0;
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.feature-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 4rpx;
}

.feature-desc {
  font-size: 22rpx;
  color: $text-muted;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bottom-section {
  padding: 16rpx 32rpx 64rpx;
  display: flex;
  justify-content: center;
}

.copyright {
  font-size: 22rpx;
  color: $text-muted;
}
</style>
