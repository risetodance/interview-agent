<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../../stores/user'
import {
  getPointsInfo,
  getPointsRecords,
  getPointsTasks,
  getSignInStatus,
  checkIn,
  claimTaskReward,
  type PointsInfo,
  type PointsRecord,
  type PointsTask
} from '../../api/membership'

// 用户 Store
const userStore = useUserStore()

// 积分信息
const pointsInfo = ref<PointsInfo | null>(null)

// 积分记录
const records = ref<PointsRecord[]>([])

// 积分任务
const tasks = ref<PointsTask[]>([])

// 加载状态
const isLoading = ref(false)
const isLoadingRecords = ref(false)
const isLoadingTasks = ref(false)
const isCheckingIn = ref(false)
const isClaiming = ref(false)

// 分页
const page = ref(1)
const pageSize = 20
const hasMoreRecords = ref(true)

// 当前选中标签
const activeTab = ref<'record' | 'task'>('record')

// 签到状态
const todayCheckedIn = ref(false)

// 今日签到奖励积分
const todayCheckInPoints = ref(0)

// 连续签到天数
const consecutiveDays = ref(0)

// 当前积分
const currentPoints = computed(() => pointsInfo.value?.availablePoints || userStore.points)

// 加载积分信息
const loadPointsInfo = async () => {
  try {
    isLoading.value = true
    const info = await getPointsInfo()
    pointsInfo.value = info
  } catch (error) {
    console.error('获取积分信息失败:', error)
  } finally {
    isLoading.value = false
  }
}

// 加载积分记录
const loadPointsRecords = async (loadMore = false) => {
  if (!loadMore) {
    page.value = 1
    records.value = []
  }

  try {
    isLoadingRecords.value = true
    const result = await getPointsRecords(page.value, pageSize)

    if (loadMore) {
      records.value = [...records.value, ...result.list]
    } else {
      records.value = result.list
    }

    hasMoreRecords.value = result.list.length >= pageSize
    page.value++
  } catch (error) {
    console.error('获取积分记录失败:', error)
  } finally {
    isLoadingRecords.value = false
  }
}

// 加载积分任务
const loadPointsTasks = async () => {
  try {
    isLoadingTasks.value = true

    // 复用已加载的积分记录数据来判断签到状态
    const signedIn = records.value.some(r => r.type === 'earn' && r.description?.includes('签到'))
    const signInRecord = records.value.find(r => r.type === 'earn' && r.description?.includes('签到'))
    const signInPoints = signInRecord?.amount || todayCheckInPoints.value || 10

    // 直接构建任务列表，使用已加载的数据
    const taskList: PointsTask[] = [
      {
        id: '1',
        name: '每日签到',
        description: '每日签到可获得积分',
        points: signInPoints,
        type: 'daily',
        isCompleted: signedIn,
        canClaim: !signedIn,
        progress: signedIn ? 1 : 0,
        maxProgress: 1
      },
      {
        id: '2',
        name: '完善简历',
        description: '上传并完善简历信息',
        points: 20,
        type: 'once',
        isCompleted: false,
        canClaim: false,
        progress: 0,
        maxProgress: 1
      },
      {
        id: '3',
        name: '完成一次面试',
        description: '完成一次AI模拟面试',
        points: 30,
        type: 'once',
        isCompleted: false,
        canClaim: false,
        progress: 0,
        maxProgress: 1
      }
    ]

    tasks.value = taskList

    // 更新签到状态
    if (signedIn) {
      todayCheckedIn.value = true
      todayCheckInPoints.value = signInPoints
    }
  } catch (error) {
    console.error('获取积分任务失败:', error)
  } finally {
    isLoadingTasks.value = false
  }
}

// 加载所有数据
const loadAllData = async () => {
  // 并行加载积分信息、记录和签到状态
  const [info, signInInfo] = await Promise.all([
    getPointsInfo(),
    getSignInStatus()
  ])

  pointsInfo.value = info

  // 根据签到状态更新
  if (signInInfo?.signedIn) {
    todayCheckedIn.value = true
    todayCheckInPoints.value = signInInfo.pointsCanEarn || 10
    consecutiveDays.value = signInInfo.consecutiveDays || 0
  }

  // 再加载积分记录和任务
  await Promise.all([
    loadPointsRecords(),
    loadPointsTasks()
  ])
}

// 签到
const handleCheckIn = async () => {
  if (todayCheckedIn.value || isCheckingIn.value) return

  try {
    isCheckingIn.value = true

    const result = await checkIn()

    todayCheckedIn.value = true
    todayCheckInPoints.value = result.points
    consecutiveDays.value = result.consecutiveDays

    // 更新本地积分
    pointsInfo.value = {
      ...pointsInfo.value!,
      availablePoints: pointsInfo.value!.availablePoints + result.points,
      totalPoints: pointsInfo.value!.totalPoints + result.points
    }

    // 更新用户信息
    await userStore.fetchUserInfo()

    // 刷新任务列表
    await loadPointsTasks()

    uni.showToast({
      title: `签到成功，获得 ${result.points} 积分`,
      icon: 'success'
    })
  } catch (error: any) {
    console.error('签到失败:', error)
    uni.showToast({
      title: error.message || '签到失败，请重试',
      icon: 'none'
    })
  } finally {
    isCheckingIn.value = false
  }
}

// 领取任务奖励
const handleClaimReward = async (task: PointsTask) => {
  if (!task.canClaim || isClaiming.value) return

  try {
    isClaiming.value = true

    const result = await claimTaskReward(task.id)

    // 更新本地积分
    pointsInfo.value = {
      ...pointsInfo.value!,
      availablePoints: pointsInfo.value!.availablePoints + result.points,
      totalPoints: pointsInfo.value!.totalPoints + result.points
    }

    // 更新用户信息
    await userStore.fetchUserInfo()

    // 刷新任务列表
    await loadPointsTasks()

    uni.showToast({
      title: `获得 ${result.points} 积分`,
      icon: 'success'
    })
  } catch (error: any) {
    console.error('领取奖励失败:', error)
    uni.showToast({
      title: error.message || '领取失败，请重试',
      icon: 'none'
    })
  } finally {
    isClaiming.value = false
  }
}

// 切换标签
const switchTab = (tab: 'record' | 'task') => {
  activeTab.value = tab

  if (tab === 'record' && records.value.length === 0) {
    loadPointsRecords()
  } else if (tab === 'task' && tasks.value.length === 0) {
    loadPointsTasks()
  }
}

// 加载更多记录
const loadMoreRecords = () => {
  if (!isLoadingRecords.value && hasMoreRecords.value) {
    loadPointsRecords(true)
  }
}

// 格式化日期
const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / (1000 * 60))
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`

  return `${date.getMonth() + 1}-${date.getDate()}`
}

// 获取记录类型标签
const getRecordTypeLabel = (type: string) => {
  return type === 'earn' ? '获得' : '消费'
}

// 获取记录类型样式
const getRecordTypeClass = (type: string) => {
  return type === 'earn' ? 'earn' : 'spend'
}

// 页面显示时加载数据
onMounted(() => {
  loadAllData()
})
</script>

<template>
  <view class="points-container">
    <!-- 积分头部卡片 -->
    <view class="points-header">
      <view class="header-bg" />

      <!-- 积分卡片 -->
      <view class="points-card">
        <view class="points-card-header">
          <text class="points-label">当前积分</text>
          <view class="points-value-wrap">
            <text class="points-value">{{ currentPoints }}</text>
          </view>
        </view>

        <!-- 签到按钮 -->
        <view class="checkin-section">
          <view v-if="!todayCheckedIn" class="checkin-btn" @click="handleCheckIn">
            <text class="checkin-btn-text">{{ isCheckingIn ? '签到中...' : '立即签到' }}</text>
          </view>
          <view v-else class="checkin-status">
            <text class="checkin-status-text">已签到</text>
            <text v-if="consecutiveDays > 0" class="checkin-days">连续 {{ consecutiveDays }} 天</text>
          </view>
        </view>
      </view>

      <!-- 积分统计 -->
      <view class="points-stats">
        <view class="stat-item">
          <text class="stat-value">{{ pointsInfo?.historyPoints || 0 }}</text>
          <text class="stat-label">历史积分</text>
        </view>
        <view class="stat-divider" />
        <view class="stat-item">
          <text class="stat-value">{{ pointsInfo?.frozenPoints || 0 }}</text>
          <text class="stat-label">冻结积分</text>
        </view>
      </view>
    </view>

    <!-- 标签切换 -->
    <view class="tabs">
      <view
        class="tab-item"
        :class="{ active: activeTab === 'record' }"
        @click="switchTab('record')"
      >
        <text class="tab-text">积分记录</text>
      </view>
      <view
        class="tab-item"
        :class="{ active: activeTab === 'task' }"
        @click="switchTab('task')"
      >
        <text class="tab-text">积分任务</text>
      </view>
      <view class="tab-indicator" :class="{ right: activeTab === 'task' }" />
    </view>

    <!-- 积分记录列表 -->
    <view v-if="activeTab === 'record'" class="records-section">
      <view v-if="records.length === 0 && !isLoadingRecords" class="empty-state">
        <text class="empty-text">暂无积分记录</text>
      </view>

      <view v-else class="records-list">
        <view v-for="record in records" :key="record.id" class="record-item">
          <view class="record-info">
            <text class="record-desc">{{ record.description }}</text>
            <text class="record-source">{{ record.source }}</text>
          </view>
          <view class="record-amount">
            <text :class="['record-amount-value', getRecordTypeClass(record.type)]">
              {{ record.type === 'earn' ? '+' : '-' }}{{ record.amount }}
            </text>
            <text class="record-balance">余额: {{ record.balance }}</text>
          </view>
          <view class="record-time">
            <text class="record-time-text">{{ formatDate(record.createTime) }}</text>
          </view>
        </view>

        <!-- 加载更多 -->
        <view v-if="hasMoreRecords" class="load-more" @click="loadMoreRecords">
          <text v-if="isLoadingRecords" class="load-more-text">加载中...</text>
          <text v-else class="load-more-text">点击加载更多</text>
        </view>

        <!-- 没有更多 -->
        <view v-if="!hasMoreRecords && records.length > 0" class="no-more">
          <text class="no-more-text">没有更多了</text>
        </view>
      </view>
    </view>

    <!-- 积分任务列表 -->
    <view v-if="activeTab === 'task'" class="tasks-section">
      <!-- 签到卡片 -->
      <view class="task-checkin-card">
        <view class="task-checkin-info">
          <text class="task-checkin-title">每日签到</text>
          <text class="task-checkin-desc">签到可获得积分奖励</text>
        </view>
        <view v-if="!todayCheckedIn" class="task-checkin-btn" @click="handleCheckIn">
          <text class="task-checkin-btn-text">签到</text>
        </view>
        <view v-else class="task-checkin-done">
          <text class="task-checkin-done-text">+{{ todayCheckInPoints || 10 }}</text>
        </view>
      </view>

      <view v-if="tasks.length === 0 && !isLoadingTasks" class="empty-state">
        <text class="empty-text">暂无任务</text>
      </view>

      <view v-else class="tasks-list">
        <view v-for="task in tasks.filter(t => t.name !== '每日签到')" :key="task.id" class="task-item">
          <view class="task-info">
            <text class="task-name">{{ task.name }}</text>
            <text class="task-desc">{{ task.description }}</text>
            <view v-if="task.progress !== undefined && task.maxProgress" class="task-progress">
              <view class="task-progress-bar">
                <view class="task-progress-inner" :style="{ width: `${(task.progress / task.maxProgress) * 100}%` }" />
              </view>
              <text class="task-progress-text">{{ task.progress }}/{{ task.maxProgress }}</text>
            </view>
          </view>

          <view class="task-action">
            <text class="task-points">+{{ task.points }}</text>

            <view
              v-if="task.canClaim"
              class="task-claim-btn"
              :class="{ disabled: isClaiming }"
              @click="handleClaimReward(task)"
            >
              <text class="task-claim-btn-text">领取</text>
            </view>

            <view v-else-if="task.isCompleted" class="task-completed">
              <text class="task-completed-text">已完成</text>
            </view>

            <view v-else class="task-pending">
              <text class="task-pending-text">进行中</text>
            </view>
          </view>
        </view>
      </view>
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

.points-container {
  min-height: 100vh;
  background-color: $bg;
}

.points-header {
  position: relative;
  padding-bottom: 40rpx;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 340rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 50%, $primary-light 100%);
  border-radius: 0 0 40rpx 40rpx;
}

.points-card {
  position: relative;
  margin: 30rpx 40rpx;
  background: $card-bg;
  border-radius: 28rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.12);
}

.points-card-header {
  text-align: center;
  margin-bottom: 40rpx;
}

.points-label {
  display: block;
  font-size: 28rpx;
  color: $text-muted;
  margin-bottom: 16rpx;
}

.points-value-wrap {
  display: flex;
  align-items: baseline;
  justify-content: center;
}

.points-value {
  font-size: 80rpx;
  font-weight: 800;
  background: linear-gradient(135deg, $primary, $primary-light);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.checkin-section {
  display: flex;
  justify-content: center;
}

.checkin-btn {
  padding: 20rpx 72rpx;
  background: linear-gradient(135deg, $primary, $primary-light);
  border-radius: 40rpx;
  box-shadow: 0 6rpx 24rpx rgba($primary, 0.35);
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.96);
    box-shadow: 0 4rpx 16rpx rgba($primary, 0.25);
  }
}

.checkin-btn-text {
  font-size: 30rpx;
  color: #fff;
  font-weight: 600;
}

.checkin-status {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.checkin-status-text {
  font-size: 28rpx;
  color: #52c41a;
  font-weight: 500;
}

.checkin-days {
  font-size: 24rpx;
  color: #999;
  margin-top: 4rpx;
}

.points-stats {
  position: relative;
  display: flex;
  justify-content: space-around;
  margin: 0 40rpx;
  margin-top: 24rpx;
  background: $card-bg;
  border-radius: 20rpx;
  padding: 36rpx 0;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 40rpx;
  font-weight: 800;
  color: $text-primary;
}

.stat-label {
  display: block;
  font-size: 24rpx;
  color: $text-muted;
  margin-top: 8rpx;
}

.stat-divider {
  width: 1rpx;
  height: 60rpx;
  background: #f1f5f9;
}

.tabs {
  position: relative;
  display: flex;
  background: #fff;
  margin: 20rpx 30rpx;
  border-radius: 16rpx;
  padding: 8rpx;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  position: relative;
  z-index: 1;
}

.tab-item.active .tab-text {
  color: $primary;
}

.tab-text {
  font-size: 28rpx;
  color: #999;
  font-weight: 500;
}

.tab-indicator {
  position: absolute;
  top: 8rpx;
  left: 8rpx;
  width: calc(50% - 8rpx);
  height: calc(100% - 16rpx);
  background: rgba(102, 126, 234, 0.1);
  border-radius: 12rpx;
  transition: all 0.3s ease;
}

.tab-indicator.right {
  transform: translateX(100%);
}

.records-section,
.tasks-section {
  padding: 0 30rpx;
  padding-bottom: 40rpx;
}

.empty-state {
  padding: 80rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
}

.records-list {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}

.record-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.record-item:last-child {
  border-bottom: none;
}

.record-info {
  flex: 1;
}

.record-desc {
  display: block;
  font-size: 28rpx;
  color: #333;
  font-weight: 500;
  margin-bottom: 4rpx;
}

.record-source {
  display: block;
  font-size: 24rpx;
  color: #999;
}

.record-amount {
  text-align: right;
  margin-right: 16rpx;
}

.record-amount-value {
  display: block;
  font-size: 32rpx;
  font-weight: bold;
}

.record-amount-value.earn {
  color: #52c41a;
}

.record-amount-value.spend {
  color: #ff6b6b;
}

.record-balance {
  display: block;
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}

.record-time {
  width: 100rpx;
  text-align: right;
}

.record-time-text {
  font-size: 22rpx;
  color: #bbb;
}

.load-more,
.no-more {
  padding: 24rpx;
  text-align: center;
}

.load-more-text,
.no-more-text {
  font-size: 24rpx;
  color: #999;
}

.task-checkin-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 20rpx;
}

.task-checkin-info {
  flex: 1;
}

.task-checkin-title {
  display: block;
  font-size: 32rpx;
  color: #fff;
  font-weight: 600;
}

.task-checkin-desc {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 8rpx;
}

.task-checkin-btn {
  padding: 12rpx 32rpx;
  background: #fff;
  border-radius: 30rpx;
}

.task-checkin-btn-text {
  font-size: 26rpx;
  color: $primary;
  font-weight: 600;
}

.task-checkin-done {
  padding: 12rpx 24rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 30rpx;
}

.task-checkin-done-text {
  font-size: 26rpx;
  color: #fff;
  font-weight: 600;
}

.tasks-list {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.task-item:last-child {
  border-bottom: none;
}

.task-info {
  flex: 1;
}

.task-name {
  display: block;
  font-size: 28rpx;
  color: #333;
  font-weight: 500;
}

.task-desc {
  display: block;
  font-size: 24rpx;
  color: #999;
  margin-top: 4rpx;
}

.task-progress {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.task-progress-bar {
  flex: 1;
  height: 8rpx;
  background: #f0f0f0;
  border-radius: 4rpx;
  margin-right: 12rpx;
  overflow: hidden;
}

.task-progress-inner {
  height: 100%;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 4rpx;
}

.task-progress-text {
  font-size: 22rpx;
  color: #999;
}

.task-action {
  display: flex;
  align-items: center;
  margin-left: 16rpx;
}

.task-points {
  font-size: 32rpx;
  color: #52c41a;
  font-weight: 600;
  margin-right: 16rpx;
}

.task-claim-btn {
  padding: 10rpx 24rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 24rpx;
}

.task-claim-btn.disabled {
  opacity: 0.6;
}

.task-claim-btn-text {
  font-size: 24rpx;
  color: #fff;
}

.task-completed {
  padding: 10rpx 24rpx;
  background: #f5f5f5;
  border-radius: 24rpx;
}

.task-completed-text {
  font-size: 24rpx;
  color: #999;
}

.task-pending {
  padding: 10rpx 24rpx;
  background: #fff7e6;
  border-radius: 24rpx;
}

.task-pending-text {
  font-size: 24rpx;
  color: #fa8c16;
}
</style>
