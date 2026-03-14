<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getNotificationList, markNotificationAsRead, type Notification } from '../../api/notification'

// 加载状态
const isLoading = ref(false)
const isRefreshing = ref(false)
const isLoadingMore = ref(false)

// 通知列表
const notifications = ref<Notification[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const hasMore = computed(() => notifications.value.length < total.value)

// 通知类型映射
const notificationTypeMap: Record<string, { label: string; icon: string; color: string }> = {
  SYSTEM: { label: '系统通知', icon: '系统', color: '#6366f1' },
  INTERVIEW: { label: '面试通知', icon: '面试', color: '#818cf8' },
  RESUME: { label: '简历通知', icon: '简历', color: '#a5b4fc' },
  MEMBERSHIP: { label: '会员通知', icon: '会员', color: '#ffeaa7' },
  POINTS: { label: '积分通知', icon: '积分', color: '#fab1a0' },
  KNOWLEDGE: { label: '知识库', icon: '知识', color: '#81ecec' }
}

// 获取通知类型信息
const getTypeInfo = (type: string) => {
  return notificationTypeMap[type] || notificationTypeMap.SYSTEM
}

// 格式化时间
const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return `${date.getMonth() + 1}-${date.getDate()}`
}

// 加载通知列表
const loadNotifications = async (loadMore = false) => {
  if (loadMore) {
    if (isLoadingMore.value || !hasMore.value) return
    isLoadingMore.value = true
    page.value++
  } else {
    if (isLoading.value) return
    isLoading.value = true
    page.value = 1
  }

  try {
    const res = await getNotificationList({
      page: page.value,
      pageSize
    })

    if (loadMore) {
      notifications.value = [...notifications.value, ...res.list]
    } else {
      notifications.value = res.list
    }
    total.value = res.total
  } catch (error) {
    console.error('加载通知列表失败:', error)
    if (!loadMore) {
      uni.showToast({
        title: '加载失败，请稍后重试',
        icon: 'none'
      })
    }
  } finally {
    isLoading.value = false
    isRefreshing.value = false
    isLoadingMore.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  isRefreshing.value = true
  loadNotifications()
}

// 加载更多
const onLoadMore = () => {
  loadNotifications(true)
}

// 标记单条已读
const handleMarkAsRead = async (item: Notification) => {
  if (item.isRead) return

  try {
    await markNotificationAsRead(item.id)
    // 更新本地状态
    const index = notifications.value.findIndex(n => n.id === item.id)
    if (index !== -1) {
      notifications.value[index].isRead = true
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

// 点击通知项
const handleNotificationClick = (item: Notification) => {
  // 先标记已读
  handleMarkAsRead(item)

  // 根据通知类型跳转到对应页面
  if (item.relatedId) {
    let url = ''
    switch (item.relatedType) {
      case 'INTERVIEW':
        url = `/pages/interview/session?id=${item.relatedId}`
        break
      case 'RESUME':
        url = `/pages/resume/detail?id=${item.relatedId}`
        break
      case 'MEMBERSHIP':
        url = `/pages/points/index`
        break
      case 'KNOWLEDGE':
        url = `/pages/knowledge/chat?id=${item.relatedId}`
        break
      default:
        // 系统通知无跳转
        return
    }

    if (url) {
      uni.navigateTo({ url })
    }
  }
}

// 跳转到设置页面
const goToSettings = () => {
  uni.navigateTo({
    url: '/pages/notification/settings'
  })
}

// 未读数量
const unreadCount = computed(() => {
  return notifications.value.filter(n => !n.isRead).length
})

onMounted(() => {
  loadNotifications()
})
</script>

<template>
  <view class="notification-container">
    <!-- 顶部操作栏 -->
    <view class="header-bar">
      <view class="header-left">
        <text class="header-title">消息通知</text>
        <text v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</text>
      </view>
      <view class="header-right" @click="goToSettings">
        <text class="settings-btn">设置</text>
      </view>
    </view>

    <!-- 通知列表 -->
    <scroll-view
      class="notification-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <!-- 加载中 -->
      <view v-if="isLoading && notifications.length === 0" class="loading-container">
        <text class="loading-text">加载中...</text>
      </view>

      <!-- 空状态 -->
      <view v-else-if="!isLoading && notifications.length === 0" class="empty-container">
        <view class="empty-icon">
          <text class="empty-icon-text">通知</text>
        </view>
        <text class="empty-text">暂无通知消息</text>
        <text class="empty-subtext">有新消息时会在这里显示</text>
      </view>

      <!-- 通知列表项 -->
      <view
        v-for="item in notifications"
        :key="item.id"
        class="notification-item"
        :class="{ unread: !item.isRead }"
        @click="handleNotificationClick(item)"
      >
        <!-- 类型图标 -->
        <view class="notification-icon" :style="{ background: getTypeInfo(item.type).color }">
          <text class="notification-icon-text">{{ getTypeInfo(item.type).icon }}</text>
        </view>

        <!-- 内容区域 -->
        <view class="notification-content">
          <view class="notification-header">
            <text class="notification-type">{{ getTypeInfo(item.type).label }}</text>
            <text class="notification-time">{{ formatTime(item.createdAt) }}</text>
          </view>
          <text class="notification-title">{{ item.title }}</text>
          <text class="notification-desc">{{ item.content }}</text>
        </view>

        <!-- 未读标识 -->
        <view v-if="!item.isRead" class="unread-dot" />
      </view>

      <!-- 加载更多 -->
      <view v-if="isLoadingMore" class="loading-more">
        <text class="loading-more-text">加载中...</text>
      </view>

      <!-- 没有更多 -->
      <view v-else-if="!hasMore && notifications.length > 0" class="no-more">
        <text class="no-more-text">没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
// 配色变量 - 靛蓝清新配色
$primary-color: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg-color: #f8fafc;
$card-bg: #ffffff;
$text-primary: #1e293b;
$text-secondary: #475569;
$text-muted: #94a3b8;
$success: #6366f1;
$warning: #f59e0b;
$danger: #ef4444;
$info: #818cf8;

.notification-container {
  min-height: 100vh;
  background-color: $bg-color;
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 40rpx;
  background: $card-bg;
  position: sticky;
  top: 0;
  z-index: 10;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.header-left {
  display: flex;
  align-items: center;
}

.header-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
}

.unread-badge {
  margin-left: 16rpx;
  padding: 4rpx 16rpx;
  background: #ff6b6b;
  border-radius: 20rpx;
  font-size: 24rpx;
  color: #fff;
}

.header-right {
  display: flex;
  align-items: center;
}

.settings-btn {
  font-size: 28rpx;
  color: $primary-color;
}

.notification-list {
  height: calc(100vh - 100rpx);
  padding: 24rpx 40rpx;
}

.loading-container,
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 0;
}

.loading-text {
  font-size: 28rpx;
  color: #999;
}

.empty-icon {
  width: 160rpx;
  height: 160rpx;
  background: #f0f0f0;
  border-radius: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
}

.empty-icon-text {
  font-size: 48rpx;
  color: #ccc;
}

.empty-text {
  font-size: 32rpx;
  color: #333;
  margin-bottom: 16rpx;
}

.empty-subtext {
  font-size: 26rpx;
  color: #999;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  background: $card-bg;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  position: relative;
  transition: all 0.2s ease;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  &.unread {
    background: linear-gradient(135deg, rgba($primary-color, 0.03) 0%, $card-bg 100%);
    box-shadow: 0 4rpx 20rpx rgba($primary-color, 0.08);

    .notification-title {
      font-weight: 700;
    }
  }

  &:active {
    transform: scale(0.99);
  }
}

.notification-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.notification-icon-text {
  font-size: 26rpx;
  color: #fff;
  font-weight: 700;
}

.notification-content {
  flex: 1;
  margin-left: 24rpx;
  min-width: 0;
}

.notification-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.notification-type {
  font-size: 24rpx;
  color: $primary-color;
}

.notification-time {
  font-size: 24rpx;
  color: #999;
}

.notification-title {
  font-size: 30rpx;
  color: $text-primary;
  margin-bottom: 10rpx;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-weight: 600;
}

.notification-desc {
  font-size: 26rpx;
  color: $text-muted;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.unread-dot {
  position: absolute;
  top: 32rpx;
  right: 32rpx;
  width: 16rpx;
  height: 16rpx;
  background: #ff6b6b;
  border-radius: 8rpx;
}

.loading-more,
.no-more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx 0;
}

.loading-more-text,
.no-more-text {
  font-size: 26rpx;
  color: #999;
}
</style>
