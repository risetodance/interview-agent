<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '../../stores/user'
import Icon from '../../components/common/Icon.vue'

// 用户 Store
const userStore = useUserStore()
const { userInfo, points, isLoggedIn } = storeToRefs(userStore)

// 跳转登录（模板里不能直接用全局 uni，TS 不识别，抽成方法）
const goToLogin = () => {
  uni.navigateTo({ url: '/pages/auth/login' })
}

// 加载状态
const isLoading = ref(false)

// 用户名显示
const displayName = computed(() => {
  return userInfo.value?.nickname || userInfo.value?.username || '未设置昵称'
})

// 头像：有真实头像时由模板渲染 <image>；无头像时用昵称首字母 CSS 占位
// （微信官方默认头像 mmbiz.qpic.cn 直链已失效返回 400，不再依赖远程兜底图，
// 与首页/简历列表的首字母占位方案保持一致）

// 页面显示时刷新用户信息
onMounted(() => {
  if (isLoggedIn.value) {
    // catch 兜底：token 失效等场景由 request.ts 统一登出跳转，这里只吞掉 rejection 避免 Uncaught promise
    userStore.fetchUserInfo().catch(() => {})
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
    subtitle: `当前积分: ${points.value}`,
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

// 设置项配置（icon 对应 Icon.vue 图标名）
const settingsItems = [
  {
    icon: 'bell',
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
            v-if="userInfo?.avatar"
            class="avatar"
            :src="userInfo.avatar"
            mode="aspectFill"
          />
          <view v-else class="avatar avatar-placeholder">
            <text class="avatar-placeholder-text">{{ displayName.charAt(0) }}</text>
          </view>
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
          <Icon name="chevron-right" size="24rpx" color="#94a3b8" />
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
          <view class="settings-item-left">
            <view class="settings-item-icon">
              <Icon :name="item.icon" size="28rpx" color="#fff" />
            </view>
            <text class="settings-item-title">{{ item.title }}</text>
          </view>
          <Icon name="chevron-right" size="24rpx" color="#94a3b8" />
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
      <button class="login-btn" @click="goToLogin">
        <text class="login-btn-text">立即登录</text>
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.profile-container {
  min-height: 100vh;
  background-color: $bg;
  padding-bottom: 60rpx;
}

.profile-header {
  position: relative;
  // 底部留白 + menu-section 的 margin-top 共同构成积分卡与"我的功能"的间距（80rpx）；
  // 渐变背景 header-bg(440rpx) 需覆盖积分卡底边：user-info(284) + 卡底(≈410) < 440 ✓
  padding-bottom: 40rpx;
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
  // 左右边距与积分卡/菜单区统一为 30rpx，对齐全页基准线
  // 底部 64rpx：预留头像编辑角标(bottom:-10rpx) + 阴影的完整露出空间，
  // 再叠加积分卡 -44rpx 上浮后不遮头像（60+160+64-44=240 > 头像底 220/角标底 230）
  padding: 60rpx 30rpx 64rpx;
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

.avatar-placeholder {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-placeholder-text {
  font-size: 56rpx;
  font-weight: 600;
  color: #fff;
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
  // 改为文档流 + 负 margin 上浮，替代 absolute 悬浮；
  // 后续区块自动跟随卡片实际底边排布，不再依赖 magic number
  position: relative;
  margin: -44rpx 30rpx 0;
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
  line-height: 1;
  color: $primary;
  margin-right: 4rpx;
}

.menu-section {
  // 积分卡改为文档流后自动跟随其后，仅需常规间隙
  margin-top: 40rpx;
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
  // 两列网格：原先 flex + 负 margin + calc(50% - 24rpx) 的写法中 calc 内嵌 rpx
  // 在 H5 端换算失效导致宽度塌陷成内容宽（单列窄条），改用 grid + gap 双端行为一致
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24rpx;
}

.menu-item {
  background: $card-bg;
  border-radius: 24rpx;
  padding: 36rpx 28rpx;
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
  // 与 menu-section 间距节奏统一
  margin-top: 40rpx;
  padding: 0 30rpx;
}

.settings-list {
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  transition: all 0.3s ease;

  &:active {
    background-color: #f8fafc;
  }
}

.settings-item:last-child {
  border-bottom: none;
}

.settings-item-left {
  display: flex;
  align-items: center;
}

// 与 menu-item-icon 同语言的图标容器（尺寸略小，列表行内比例）
.settings-item-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.settings-item-title {
  font-size: 30rpx;
  color: #333;
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
