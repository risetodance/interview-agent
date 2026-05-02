<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useQuestionBankStore, type QuestionDTO, type QuestionDifficulty } from '../../stores/question-bank'

// Store
const questionBankStore = useQuestionBankStore()

// 题库 ID 和名称
const bankId = ref<number>(0)
const bankName = ref<string>('')

// 搜索和筛选
const searchKeyword = ref('')
const selectedDifficulty = ref<QuestionDifficulty | null>(null)

// 加载状态
const loading = ref(false)
const refreshing = ref(false)

// 选中题目
const selectedQuestion = ref<QuestionDTO | null>(null)

// 难度选项
const difficultyOptions = [
  { value: null, label: '全部' },
  { value: 'BASIC' as QuestionDifficulty, label: '基础' },
  { value: 'ADVANCED' as QuestionDifficulty, label: '进阶' },
  { value: 'EXPERT' as QuestionDifficulty, label: '专家' }
]

// 分页信息
const pagination = computed(() => questionBankStore.pagination)

// 加载题目列表
const loadQuestions = async (refresh = false) => {
  if (loading.value) return

  loading.value = true
  refreshing.value = refresh

  try {
    const page = refresh ? 0 : pagination.value.page
    await questionBankStore.fetchQuestions(bankId.value, page, selectedDifficulty.value, searchKeyword.value)
  } catch (error) {
    console.error('加载题目列表失败:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  loadQuestions(true)
}

// 上拉加载更多
const onLoadMore = () => {
  if (pagination.value.page < pagination.value.totalPages - 1) {
    questionBankStore.pagination.page++
    loadQuestions()
  }
}

// 切换难度筛选
const onDifficultyChange = (difficulty: QuestionDifficulty | null) => {
  selectedDifficulty.value = difficulty
  loadQuestions(true)
}

// 搜索题目
const onSearch = () => {
  loadQuestions(true)
}

// 查看题目详情
const viewQuestion = (question: QuestionDTO) => {
  selectedQuestion.value = question
}

// 关闭详情
const closeDetail = () => {
  selectedQuestion.value = null
}

// 跳转到编辑题目
const goToEdit = (question: QuestionDTO) => {
  uni.navigateTo({
    url: `/pages/question-bank/edit?id=${question.id}&bankId=${bankId.value}`
  })
}

// 删除题目
const handleDelete = (question: QuestionDTO) => {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这道题目吗？删除后无法恢复。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await questionBankStore.removeQuestion(question.id)
          uni.showToast({
            title: '删除成功',
            icon: 'success'
          })
          // 如果删除的是选中的题目，关闭详情
          if (selectedQuestion.value?.id === question.id) {
            selectedQuestion.value = null
          }
          // 刷新列表
          loadQuestions(true)
        } catch (error) {
          console.error('删除题目失败:', error)
        }
      }
    }
  })
}

// 获取难度样式
const getDifficultyStyle = (difficulty: QuestionDifficulty) => {
  switch (difficulty) {
    case 'BASIC':
      return { color: '#67C23A', bgColor: '#f0f9ff' }
    case 'ADVANCED':
      return { color: '#E6A23C', bgColor: '#fdf6ec' }
    case 'EXPERT':
      return { color: '#F56C6C', bgColor: '#fef0f0' }
    default:
      return { color: '#909399', bgColor: '#f4f4f5' }
  }
}

// 获取难度标签
const getDifficultyLabel = (difficulty: QuestionDifficulty) => {
  switch (difficulty) {
    case 'BASIC': return '基础'
    case 'ADVANCED': return '进阶'
    case 'EXPERT': return '专家'
    default: return '未知'
  }
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 页面加载
onLoad((options: any) => {
  if (options.id) {
    bankId.value = parseInt(options.id)
  }
  if (options.name) {
    bankName.value = decodeURIComponent(options.name)
  }
  // 加载数据
  loadQuestions()
})
</script>

<template>
  <view class="question-detail-container">
    <!-- 顶部区域 -->
    <view class="header">
      <view class="header-title">
        <text class="title-text">{{ bankName || '题目列表' }}</text>
        <text class="title-desc">共 {{ pagination.totalElements }} 道题目</text>
      </view>
    </view>

    <!-- 搜索和筛选 -->
    <view class="filter-bar">
      <!-- 搜索框 -->
      <view class="search-wrap">
        <input
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索题目内容"
          confirm-type="search"
          @confirm="onSearch"
        />
        <view class="search-btn" @click="onSearch">
          <text>搜索</text>
        </view>
      </view>

      <!-- 难度筛选 -->
      <scroll-view class="difficulty-tabs" scroll-x>
        <view
          v-for="option in difficultyOptions"
          :key="String(option.value)"
          class="difficulty-tab"
          :class="{ active: selectedDifficulty === option.value }"
          @click="onDifficultyChange(option.value)"
        >
          <text>{{ option.label }}</text>
        </view>
      </scroll-view>
    </view>

    <!-- 题目列表 -->
    <scroll-view
      class="question-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view
        v-for="question in questionBankStore.questions"
        :key="question.id"
        class="question-card"
        :class="{ selected: selectedQuestion?.id === question.id }"
        @click="viewQuestion(question)"
      >
        <view class="question-content">
          <text class="question-text">{{ question.content }}</text>
        </view>
        <view class="question-meta">
          <view
            class="difficulty-tag"
            :style="{ color: getDifficultyStyle(question.difficulty).color, backgroundColor: getDifficultyStyle(question.difficulty).bgColor }"
          >
            {{ getDifficultyLabel(question.difficulty) }}
          </view>
          <text v-if="question.tags && question.tags.length > 0" class="question-tags">
            {{ question.tags.slice(0, 3).join('、') }}
          </text>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-if="questionBankStore.questions.length === 0 && !loading" class="empty">
        <text class="empty-text">暂无题目</text>
        <text class="empty-desc">该题库下还没有题目</text>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-more">
        <text>{{ questionBankStore.questions.length < pagination.totalElements ? '加载中...' : '没有更多了' }}</text>
      </view>
    </scroll-view>

    <!-- 题目详情弹窗 -->
    <view v-if="selectedQuestion" class="detail-overlay" @click="closeDetail">
      <view class="detail-panel" @click.stop>
        <view class="detail-header">
          <text class="detail-title">题目详情</text>
          <view class="close-btn" @click="closeDetail">
            <text class="close-icon">&#xe614;</text>
          </view>
        </view>

        <scroll-view class="detail-content" scroll-y>
          <!-- 题目内容 -->
          <view class="detail-section">
            <view class="section-label">题目内容</view>
            <view class="section-value">{{ selectedQuestion.content }}</view>
          </view>

          <!-- 参考答案 -->
          <view class="detail-section">
            <view class="section-label">参考答案</view>
            <view class="section-value answer">{{ selectedQuestion.answer || '暂无参考答案' }}</view>
          </view>

          <!-- 难度 -->
          <view class="detail-section">
            <view class="section-label">难度</view>
            <view
              class="difficulty-tag large"
              :style="{ color: getDifficultyStyle(selectedQuestion.difficulty).color, backgroundColor: getDifficultyStyle(selectedQuestion.difficulty).bgColor }"
            >
              {{ getDifficultyLabel(selectedQuestion.difficulty) }}
            </view>
          </view>

          <!-- 标签 -->
          <view v-if="selectedQuestion.tags && selectedQuestion.tags.length > 0" class="detail-section">
            <view class="section-label">标签</view>
            <view class="tag-list">
              <view v-for="tag in selectedQuestion.tags" :key="tag" class="tag-item">
                {{ tag }}
              </view>
            </view>
          </view>

          <!-- 创建时间 -->
          <view class="detail-section">
            <view class="section-label">创建时间</view>
            <view class="section-value">{{ formatDate(selectedQuestion.createdAt) }}</view>
          </view>
        </scroll-view>

        <!-- 操作按钮 -->
        <view class="detail-footer">
          <view class="action-btn edit" @click="goToEdit(selectedQuestion)">
            <text class="action-icon">&#xe613;</text>
            <text>编辑</text>
          </view>
          <view class="action-btn delete" @click="handleDelete(selectedQuestion)">
            <text class="action-icon">&#xe614;</text>
            <text>删除</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

.question-detail-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: $bg;
}

// 顶部区域
.header {
  padding: 48rpx 40rpx 32rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
}

.header-title {
  .title-text {
    display: block;
    font-size: 40rpx;
    font-weight: 700;
    color: #fff;
    margin-bottom: 8rpx;
  }

  .title-desc {
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.75);
  }
}

// 筛选栏
.filter-bar {
  background: $card-bg;
  padding: 24rpx 40rpx;
}

.search-wrap {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.search-input {
  flex: 1;
  height: 72rpx;
  background: #f8fafc;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
}

.search-btn {
  width: 120rpx;
  height: 72rpx;
  background: $primary;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #fff;
}

.difficulty-tabs {
  white-space: nowrap;
}

.difficulty-tab {
  display: inline-block;
  padding: 12rpx 24rpx;
  margin-right: 16rpx;
  background: #f5f5f5;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: $text-secondary;

  &.active {
    background: rgba($primary, 0.1);
    color: $primary;
    font-weight: 600;
  }
}

// 题目列表
.question-list {
  flex: 1;
  padding: 32rpx 40rpx;
}

.question-card {
  background: $card-bg;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;

  &.selected {
    border: 2rpx solid $primary;
  }

  &:active {
    transform: scale(0.99);
  }
}

.question-content {
  margin-bottom: 16rpx;
}

.question-text {
  font-size: 28rpx;
  color: $text-primary;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.difficulty-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;

  &.large {
    font-size: 26rpx;
    padding: 8rpx 20rpx;
  }
}

.question-tags {
  font-size: 24rpx;
  color: $text-muted;
}

// 空状态
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;

  .empty-text {
    font-size: 32rpx;
    font-weight: 600;
    color: $text-secondary;
    margin-bottom: 12rpx;
  }

  .empty-desc {
    font-size: 26rpx;
    color: $text-muted;
  }
}

.loading-more {
  text-align: center;
  padding: 30rpx;
  font-size: 26rpx;
  color: #999;
}

// 详情弹窗
.detail-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.detail-panel {
  width: 100%;
  max-height: 80vh;
  background: $card-bg;
  border-radius: 32rpx 32rpx 0 0;
  display: flex;
  flex-direction: column;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 40rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.detail-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
}

.close-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-icon {
  font-size: 28rpx;
  color: $text-secondary;
}

.detail-content {
  flex: 1;
  padding: 32rpx 40rpx;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 32rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.section-label {
  font-size: 26rpx;
  color: $text-muted;
  margin-bottom: 12rpx;
}

.section-value {
  font-size: 28rpx;
  color: $text-primary;
  line-height: 1.6;

  &.answer {
    background: #f8fafc;
    padding: 20rpx;
    border-radius: 12rpx;
    white-space: pre-wrap;
  }
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-item {
  padding: 8rpx 16rpx;
  background: rgba($primary, 0.1);
  color: $primary;
  border-radius: 8rpx;
  font-size: 24rpx;
}

.detail-footer {
  display: flex;
  gap: 24rpx;
  padding: 24rpx 40rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #f5f5f5;
}

.action-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 28rpx;
  font-weight: 500;

  &.edit {
    background: rgba($primary, 0.1);
    color: $primary;
  }

  &.delete {
    background: #fef0f0;
    color: $danger;
  }
}

.action-icon {
  font-size: 28rpx;
}
</style>
