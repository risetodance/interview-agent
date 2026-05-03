<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useQuestionBankStore, type QuestionBankDTO } from '../../stores/question-bank'

// Store
const questionBankStore = useQuestionBankStore()

// 搜索关键词
const searchKeyword = ref('')

// 加载状态
const loading = ref(false)
const refreshing = ref(false)

// 过滤后的题库
const filteredSystemBanks = computed(() => {
  if (!searchKeyword.value.trim()) {
    return questionBankStore.systemBanks
  }
  const keyword = searchKeyword.value.toLowerCase()
  return questionBankStore.systemBanks.filter(bank =>
    bank.name.toLowerCase().includes(keyword) ||
    bank.description?.toLowerCase().includes(keyword)
  )
})

const filteredUserBanks = computed(() => {
  if (!searchKeyword.value.trim()) {
    return questionBankStore.userBanks
  }
  const keyword = searchKeyword.value.toLowerCase()
  return questionBankStore.userBanks.filter(bank =>
    bank.name.toLowerCase().includes(keyword) ||
    bank.description?.toLowerCase().includes(keyword)
  )
})

// 加载题库列表
const loadBanks = async (refresh = false) => {
  if (loading.value) return

  loading.value = true
  refreshing.value = refresh

  try {
    await questionBankStore.fetchAllBanks()
  } catch (error) {
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  loadBanks(true)
}

// 跳转到题库详情
const goToDetail = (bank: QuestionBankDTO) => {
  uni.navigateTo({
    url: `/pages/question-bank/detail?id=${bank.id}&name=${encodeURIComponent(bank.name)}`
  })
}

// 跳转到创建题库
const goToCreate = () => {
  uni.navigateTo({
    url: '/pages/question-bank/create'
  })
}

// 跳转到编辑题库
const goToEdit = (bank: QuestionBankDTO) => {
  uni.navigateTo({
    url: `/pages/question-bank/create?id=${bank.id}`
  })
}

// 删除题库
const handleDelete = (bank: QuestionBankDTO) => {
  uni.showModal({
    title: '确认删除',
    content: `确定要删除题库"${bank.name}"吗？删除后无法恢复。`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await questionBankStore.removeBank(bank.id)
          uni.showToast({
            title: '删除成功',
            icon: 'success'
          })
        } catch (error) {
        }
      }
    }
  })
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 获取题库类型标签样式
const getBankTypeStyle = (type: string) => {
  if (type === 'SYSTEM') {
    return { color: '#409EFF', bgColor: '#ecf5ff' }
  }
  return { color: '#67C23A', bgColor: '#f0f9ff' }
}

onShow(() => {
  loadBanks()
})
</script>

<template>
  <view class="question-bank-list-container">
    <!-- 顶部区域 -->
    <view class="header">
      <view class="header-title">
        <text class="title-text">题库管理</text>
        <text class="title-desc">管理你的面试题库</text>
      </view>

      <!-- 创建题库按钮 -->
      <view class="create-btn" @click="goToCreate">
        <text class="iconfont">+</text>
        <text>创建题库</text>
      </view>
    </view>

    <!-- 搜索框 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="search-icon">&#xe618;</text>
        <input
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索题库名称或描述"
          confirm-type="search"
        />
      </view>
    </view>

    <!-- 题库列表 -->
    <scroll-view
      class="bank-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <!-- 系统题库 -->
      <view v-if="filteredSystemBanks.length > 0" class="bank-section">
        <view class="section-header">
          <view class="section-title">
            <view class="section-icon system"></view>
            <text>系统题库</text>
          </view>
          <text class="section-count">{{ filteredSystemBanks.length }} 个</text>
        </view>

        <view
          v-for="bank in filteredSystemBanks"
          :key="bank.id"
          class="bank-card system"
          @click="goToDetail(bank)"
        >
          <view class="card-header">
            <view class="card-info">
              <view class="card-title-row">
                <text class="card-title">{{ bank.name }}</text>
                <view class="type-tag" :style="{ color: getBankTypeStyle(bank.type).color, backgroundColor: getBankTypeStyle(bank.type).bgColor }">
                  {{ bank.type === 'SYSTEM' ? '系统' : '我的' }}
                </view>
              </view>
              <text v-if="bank.description" class="card-desc">{{ bank.description }}</text>
            </view>
          </view>

          <view class="card-content">
            <view class="content-item">
              <text class="content-label">题目数量</text>
              <text class="content-value">{{ bank.questionCount }} 题</text>
            </view>
            <view class="content-item">
              <text class="content-label">创建时间</text>
              <text class="content-value">{{ formatDate(bank.createdAt) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 用户题库 -->
      <view v-if="filteredUserBanks.length > 0" class="bank-section">
        <view class="section-header">
          <view class="section-title">
            <view class="section-icon user"></view>
            <text>我的题库</text>
          </view>
          <text class="section-count">{{ filteredUserBanks.length }} 个</text>
        </view>

        <view
          v-for="bank in filteredUserBanks"
          :key="bank.id"
          class="bank-card user"
          @click="goToDetail(bank)"
        >
          <view class="card-header">
            <view class="card-info">
              <view class="card-title-row">
                <text class="card-title">{{ bank.name }}</text>
                <view class="type-tag" :style="{ color: getBankTypeStyle(bank.type).color, backgroundColor: getBankTypeStyle(bank.type).bgColor }">
                  {{ bank.type === 'SYSTEM' ? '系统' : '我的' }}
                </view>
              </view>
              <text v-if="bank.description" class="card-desc">{{ bank.description }}</text>
            </view>
            <view class="card-actions" @click.stop>
              <view class="action-btn" @click.stop="goToEdit(bank)">编辑</view>
              <view class="action-btn delete" @click.stop="handleDelete(bank)">删除</view>
            </view>
          </view>

          <view class="card-content">
            <view class="content-item">
              <text class="content-label">题目数量</text>
              <text class="content-value">{{ bank.questionCount }} 题</text>
            </view>
            <view class="content-item">
              <text class="content-label">创建时间</text>
              <text class="content-value">{{ formatDate(bank.createdAt) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-if="filteredSystemBanks.length === 0 && filteredUserBanks.length === 0 && !loading" class="empty">
        <text class="empty-icon">&#xe60c;</text>
        <text class="empty-text">暂无题库</text>
        <text class="empty-desc">点击上方按钮创建你的第一个题库</text>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-more">
        <text>加载中...</text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

.question-bank-list-container {
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

// 创建题库按钮
.create-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 20rpx 36rpx;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 40rpx;
  font-size: 28rpx;
  color: #fff;
  backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  transition: all 0.3s ease;
  width: auto;
  box-sizing: border-box;

  &:active {
    background: rgba(255, 255, 255, 0.25);
    transform: scale(0.97);
  }

  .iconfont {
    font-size: 32rpx;
    margin: 0;
    padding: 0;
    flex-shrink: 0;
  }

  text {
    margin: 0;
    padding: 0;
    line-height: 1;
    display: inline-block;
    flex-shrink: 0;
  }
}

// 搜索框
.search-bar {
  margin: -60rpx 40rpx 32rpx;
  position: relative;
  z-index: 2;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  background: $card-bg;
  border-radius: 16rpx;
  padding: 0 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.08);
}

.search-icon {
  font-size: 32rpx;
  color: $text-muted;
  margin-right: 16rpx;
}

.search-input {
  flex: 1;
  height: 88rpx;
  font-size: 28rpx;
  color: $text-primary;
}

// 题库列表
.bank-list {
  flex: 1;
  padding: 0 40rpx;
}

.bank-section {
  margin-bottom: 40rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
}

.section-icon {
  width: 32rpx;
  height: 32rpx;
  border-radius: 8rpx;

  &.system {
    background: linear-gradient(135deg, $info, #60a5fa);
  }

  &.user {
    background: linear-gradient(135deg, $success, #34d399);
  }
}

.section-count {
  font-size: 24rpx;
  color: $text-muted;
}

// 题库卡片
.bank-card {
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

  &.system::before {
    background: $info;
  }

  &.user::before {
    background: $success;
  }

  &:active {
    transform: scale(0.99);
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20rpx;
}

.card-info {
  flex: 1;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
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
}

.card-desc {
  font-size: 26rpx;
  color: $text-muted;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  display: flex;
  gap: 16rpx;
}

.action-btn {
  padding: 12rpx 24rpx;
  border-radius: 16rpx;
  background: #f5f5f5;
  font-size: 26rpx;
  color: $text-secondary;

  &.delete {
    background: #fef0f0;
    color: $danger;
  }
}

.action-icon {
  width: 28rpx;
  height: 28rpx;
  color: $text-secondary;
}

.action-btn.delete .action-icon {
  color: $danger;
}

.card-content {
  display: flex;
  padding-top: 20rpx;
  border-top: 1rpx solid #f5f5f5;
}

.content-item {
  flex: 1;
  display: flex;
  flex-direction: column;

  &:first-child {
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

// 空状态
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

.loading-more {
  text-align: center;
  padding: 30rpx;
  font-size: 26rpx;
  color: #999;
}
</style>
