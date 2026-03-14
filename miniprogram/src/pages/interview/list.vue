<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useInterviewStore, type Interview } from '../../stores/interview'
import { getRecommendedPositions } from '../../api/interview'

// 面试类型定义
const interviewTypeOptions = [
  { value: 'practice', label: '练习模式', icon: '&#xe60a;' },
  { value: 'real', label: '真实面试', icon: '&#xe617;' }
]

// 状态筛选
const statusTabs = [
  { value: '', label: '全部' },
  { value: 'pending', label: '待开始' },
  { value: 'in_progress', label: '进行中' },
  { value: 'completed', label: '已完成' }
]

// Store
const interviewStore = useInterviewStore()

// 数据
const loading = ref(false)
const refreshing = ref(false)
const currentStatus = ref('')
const listParams = ref({
  page: 1,
  pageSize: 20
})
const total = ref(0)
const recommendedPositions = ref<string[]>([])

// 面试列表
const interviewList = computed(() => interviewStore.interviewList)

// 状态映射
const statusMap: Record<string, { text: string; color: string; bgColor: string }> = {
  pending: { text: '待开始', color: '#909399', bgColor: '#f4f4f5' },
  in_progress: { text: '进行中', color: '#409EFF', bgColor: '#ecf5ff' },
  completed: { text: '已完成', color: '#67C23A', bgColor: '#f0f9ff' },
  cancelled: { text: '已取消', color: '#F56C6C', bgColor: '#fef0f0' }
}

// 面试类型映射
const typeMap: Record<string, { text: string; color: string }> = {
  practice: { text: '练习', color: '#67C23A' },
  real: { text: '真实', color: '#409EFF' }
}

// 加载面试列表
const loadInterviewList = async (refresh = false) => {
  if (loading.value) return

  loading.value = true
  refreshing.value = refresh

  if (refresh) {
    listParams.value.page = 1
  }

  try {
    const params = {
      ...listParams.value,
      status: currentStatus.value || undefined
    }
    await interviewStore.fetchInterviewList(params)
    total.value = interviewList.value.length
    listParams.value.page++
  } catch (error) {
    console.error('加载面试列表失败:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  loadInterviewList(true)
}

// 上拉加载更多
const onLoadMore = () => {
  if (interviewList.value.length < total.value) {
    loadInterviewList()
  }
}

// 切换状态筛选
const onStatusChange = (status: string) => {
  currentStatus.value = status
  loadInterviewList(true)
}

// 跳转到创建面试
const goToCreate = () => {
  uni.navigateTo({
    url: '/pages/interview/create'
  })
}

// 跳转到面试会话
const goToSession = (item: Interview) => {
  if (item.status === 'pending') {
    // 待开始的面试，点击开始
    uni.navigateTo({
      url: `/pages/interview/session?id=${item.id}`
    })
  } else if (item.status === 'in_progress') {
    // 进行中的面试，继续
    uni.navigateTo({
      url: `/pages/interview/session?id=${item.id}`
    })
  } else if (item.status === 'completed') {
    // 已完成的面试，查看报告
    uni.navigateTo({
      url: `/pages/interview/session?id=${item.id}&mode=result`
    })
  }
}

// 删除面试
const handleDelete = (id: number, index: number) => {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这场面试记录吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await interviewStore.removeInterview(id)
          uni.showToast({
            title: '删除成功',
            icon: 'success'
          })
        } catch (error) {
          console.error('删除面试失败:', error)
        }
      }
    }
  })
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 获取状态样式
const getStatusStyle = (status: string) => {
  const style = statusMap[status] || statusMap.pending
  return {
    color: style.color,
    backgroundColor: style.bgColor
  }
}

// 加载推荐岗位
const loadRecommendedPositions = async () => {
  try {
    const positions = await getRecommendedPositions()
    recommendedPositions.value = positions || []
  } catch (error) {
    console.error('获取推荐岗位失败:', error)
    // 使用默认岗位
    recommendedPositions.value = [
      '前端开发工程师',
      '后端开发工程师',
      '全栈开发工程师',
      '移动端开发工程师',
      '算法工程师'
    ]
  }
}

onMounted(() => {
  loadInterviewList()
  loadRecommendedPositions()
})
</script>

<template>
  <view class="interview-list-container">
    <!-- 顶部区域 -->
    <view class="header">
      <view class="header-title">
        <text class="title-text">我的面试</text>
        <text class="title-desc">AI 模拟面试，提升你的面试技巧</text>
      </view>

      <!-- 创建面试按钮 -->
      <view class="create-btn" @click="goToCreate">
        <text class="iconfont">&#xe60d;</text>
        <text>创建面试</text>
      </view>
    </view>

    <!-- 面试类型快捷入口 -->
    <view class="type-cards">
      <view
        v-for="item in interviewTypeOptions"
        :key="item.value"
        class="type-card"
        :class="item.value"
        @click="goToCreate"
      >
        <text class="type-icon" v-html="item.icon"></text>
        <text class="type-label">{{ item.label }}</text>
      </view>
    </view>

    <!-- 状态筛选 -->
    <view class="status-tabs">
      <view
        v-for="tab in statusTabs"
        :key="tab.value"
        class="status-tab"
        :class="{ active: currentStatus === tab.value }"
        @click="onStatusChange(tab.value)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </view>

    <!-- 面试列表 -->
    <scroll-view
      class="interview-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view v-if="interviewList.length === 0 && !loading" class="empty">
        <text class="empty-icon">&#xe60c;</text>
        <text class="empty-text">暂无面试记录</text>
        <text class="empty-desc">点击上方按钮创建你的第一场 AI 模拟面试</text>
      </view>

      <view
        v-for="(item, index) in interviewList"
        :key="item.id"
        class="interview-card"
        @click="goToSession(item)"
      >
        <!-- 卡片头部 -->
        <view class="card-header">
          <view class="card-info">
            <view class="card-title-row">
              <text class="card-title">{{ item.title || '未命名面试' }}</text>
              <view class="type-tag" :style="{ color: typeMap[item.type]?.color }">
                {{ typeMap[item.type]?.text || '练习' }}
              </view>
            </view>
            <view class="card-meta">
              <text v-if="item.position" class="position">{{ item.position }}</text>
              <text v-if="item.company" class="company">{{ item.company }}</text>
            </view>
          </view>
          <view class="card-status" :style="getStatusStyle(item.status)">
            {{ statusMap[item.status]?.text || '待开始' }}
          </view>
        </view>

        <!-- 卡片内容 -->
        <view class="card-content">
          <view class="content-item">
            <text class="content-label">面试时间</text>
            <text class="content-value">{{ formatDate(item.createdAt) }}</text>
          </view>
          <view class="content-item">
            <text class="content-label">题目数量</text>
            <text class="content-value">{{ item.questionCount || 0 }} 题</text>
          </view>
          <view v-if="item.duration" class="content-item">
            <text class="content-label">预计时长</text>
            <text class="content-value">{{ item.duration }} 分钟</text>
          </view>
        </view>

        <!-- 卡片底部 -->
        <view class="card-footer">
          <view v-if="item.status === 'completed' && item.score !== undefined" class="score-section">
            <text class="score-label">面试评分</text>
            <text class="score-value">{{ item.score }} 分</text>
          </view>
          <view v-else-if="item.status === 'in_progress'" class="progress-section">
            <text class="progress-label">进度</text>
            <text class="progress-value">{{ item.currentQuestionIndex || 0 }}/{{ item.questionCount || 0 }}</text>
          </view>
          <view class="action-btns" @click.stop>
            <text
              v-if="item.status !== 'completed'"
              class="action-btn start"
              @click="goToSession(item)"
            >
              {{ item.status === 'pending' ? '开始面试' : '继续面试' }}
            </text>
            <text
              v-else
              class="action-btn view"
              @click="goToSession(item)"
            >
              查看报告
            </text>
            <text
              class="action-btn delete"
              @click="handleDelete(item.id, index)"
            >
              删除
            </text>
          </view>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-more">
        <text>{{ interviewList.length < total ? '加载中...' : '没有更多了' }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss">
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

.interview-list-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: $bg;
}

// 顶部区域 - 渐变背景
.header {
  position: relative;
  padding: 48rpx 40rpx 100rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 50%, $primary-light 100%);
  overflow: hidden;

  // 装饰性圆形
  &::before,
  &::after {
    content: '';
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);
  }

  &::before {
    width: 200rpx;
    height: 200rpx;
    top: -60rpx;
    right: -40rpx;
  }

  &::after {
    width: 120rpx;
    height: 120rpx;
    bottom: 20rpx;
    left: -30rpx;
  }
}

.header-title {
  margin-bottom: 32rpx;

  .title-text {
    display: block;
    font-size: 48rpx;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1rpx;
  }

  .title-desc {
    display: block;
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.75);
    margin-top: 12rpx;
  }
}

// 创建面试按钮
.create-btn {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 36rpx;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 40rpx;
  font-size: 28rpx;
  color: #fff;
  backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  transition: all 0.3s ease;

  &:active {
    background: rgba(255, 255, 255, 0.25);
    transform: scale(0.97);
  }

  .iconfont {
    font-size: 32rpx;
  }
}

// 面试类型快捷入口
.type-cards {
  display: flex;
  gap: 20rpx;
  padding: 0 40rpx;
  margin-top: -60rpx;
  position: relative;
  z-index: 2;
}

.type-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32rpx 24rpx;
  border-radius: 24rpx;
  background: $card-bg;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.97);
  }

  &.practice {
    .type-icon-wrap {
      background: linear-gradient(135deg, $success, #34d399);
    }
  }

  &.real {
    .type-icon-wrap {
      background: linear-gradient(135deg, $info, #60a5fa);
    }
  }
}

.type-icon-wrap {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}

.type-icon {
  font-size: 40rpx;
}

.type-label {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-primary;
}

// 状态筛选
.status-tabs {
  display: flex;
  background: $card-bg;
  margin: 32rpx 40rpx 0;
  border-radius: 16rpx;
  padding: 8rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.status-tab {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  font-size: 28rpx;
  color: $text-muted;
  border-radius: 12rpx;
  transition: all 0.3s ease;
  position: relative;

  &.active {
    color: $primary;
    font-weight: 600;
    background: rgba($primary, 0.08);
  }
}

.interview-list {
  flex: 1;
  padding: 32rpx 40rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  .empty-icon {
    font-size: 160rpx;
    color: #e2e8f0;
    margin-bottom: 32rpx;
  }

  .empty-text {
    font-size: 32rpx;
    font-weight: 600;
    color: $text-secondary;
    margin-bottom: 12rpx;
  }

  .empty-desc {
    font-size: 26rpx;
    color: $text-muted;
    text-align: center;
  }
}

.interview-card {
  background: $card-bg;
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;

  // 左侧装饰条
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 6rpx;
    border-radius: 3rpx 0 0 3rpx;
  }

  &.pending::before { background: $text-muted; }
  &.in_progress::before { background: $info; }
  &.completed::before { background: $success; }

  &:active {
    transform: scale(0.99);
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.card-info {
  flex: 1;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.card-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.type-tag {
  font-size: 22rpx;
  font-weight: 500;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
  background-color: #f5f5f5;
}

.card-meta {
  display: flex;
  gap: 16rpx;
  font-size: 24rpx;
  color: #999;

  .position {
    &::before {
      content: '';
      display: inline-block;
      width: 24rpx;
      height: 24rpx;
      margin-right: 8rpx;
      background: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cGF0aCBkPSJNMTIgMkM4LjEzIDIgNSA1LjEzIDUgOWMwIDcuNyA3IDEzIDcgMTNzNy01LjMgNy0xM2MwLTMuOC0zLjEzLTctNy03em0wIDkuNWMtMS4zOCAwLTIuNS0xLjEyLTIuNS0yLjVzMS4xMi0yLjUgMi41LTIuNSAyLjUgMS4xMiAyLjUgMi41LTEuMTIgMi41LTIuNSAyLjV6IiBmaWxsPSIjOTk5Ii8+PC9zdmc+') no-repeat center;
      background-size: contain;
      vertical-align: middle;
    }
  }
}

.card-status {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: 24rpx;
  font-weight: 500;
}

.card-content {
  display: flex;
  padding: 20rpx 0;
  border-top: 1rpx solid #f5f5f5;
  border-bottom: 1rpx solid #f5f5f5;
  margin-bottom: 20rpx;
}

.content-item {
  flex: 1;
  display: flex;
  flex-direction: column;

  &:first-child {
    border-right: 1rpx solid #f5f5f5;
  }

  &:nth-child(2) {
    border-right: 1rpx solid #f5f5f5;
  }
}

.content-label {
  font-size: 24rpx;
  color: #999;
  margin-bottom: 8rpx;
}

.content-value {
  font-size: 28rpx;
  color: #333;
  font-weight: 500;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-section,
.progress-section {
  display: flex;
  flex-direction: column;

  .score-label,
  .progress-label {
    font-size: 24rpx;
    color: #999;
    margin-bottom: 8rpx;
  }

  .score-value {
    font-size: 36rpx;
    font-weight: bold;
    color: $primary;
  }

  .progress-value {
    font-size: 28rpx;
    font-weight: 500;
    color: #409EFF;
  }
}

.action-btns {
  display: flex;
  gap: 16rpx;
}

.action-btn {
  padding: 12rpx 24rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  font-weight: 500;

  &.start {
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
    color: #fff;
  }

  &.view {
    background-color: #ecf5ff;
    color: #409EFF;
  }

  &.delete {
    background-color: #fef0f0;
    color: #F56C6C;
  }
}

.loading-more {
  text-align: center;
  padding: 30rpx;
  font-size: 26rpx;
  color: #999;
}
</style>
