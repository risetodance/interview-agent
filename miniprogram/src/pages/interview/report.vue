<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getInterviewDetail } from '../../api/interview'

// 路由参数
const pageId = ref<string>('')

// 面试详情数据
const interview = ref<any>(null)
const loading = ref(true)
const expandedQuestions = ref<Set<number>>(new Set())

// 页面参数
onMounted(() => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}
  pageId.value = options.id || ''
  if (pageId.value) {
    loadInterviewDetail()
  }
})

// 加载面试详情
const loadInterviewDetail = async () => {
  loading.value = true
  try {
    // 加载基本信息（details接口已包含完整数据）
    const detailRes = await getInterviewDetail(pageId.value)
    interview.value = detailRes

    // 从详情数据中获取answers
    if (detailRes.answers && detailRes.answers.length > 0) {
      // 默认展开所有问题
      detailRes.answers.forEach((_: any, idx: number) => {
        expandedQuestions.value.add(idx)
      })
    }
  } catch (error) {
    console.error('获取面试详情失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取答案列表（从interview中获取）
const answers = computed(() => interview.value?.answers || [])

// 切换问题展开状态
const toggleQuestion = (index: number) => {
  if (expandedQuestions.value.has(index)) {
    expandedQuestions.value.delete(index)
  } else {
    expandedQuestions.value.add(index)
  }
}

// 返回列表页面
const goBack = () => {
  uni.navigateBack()
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// 计算分数颜色
const getScoreColor = (score: number) => {
  if (score >= 90) return '#67C23A'
  if (score >= 70) return '#409EFF'
  if (score >= 60) return '#E6A23C'
  return '#F56C6C'
}

// 圆形进度相关计算
const circumference = 2 * Math.PI * 48 // 半径48的圆周长
const progressOffset = computed(() => {
  const score = interview.value?.overallScore || 0
  // 分数对应的进度，100分=完全显示，0分=不显示
  const progress = score / 100
  return circumference * (1 - progress)
})
</script>

<template>
  <view class="report-page">
    <!-- 顶部导航 -->
    <view class="nav-header">
      <view class="header-left" @click="goBack">
        <text class="back-icon">‹</text>
      </view>
      <text class="nav-title">面试评估报告</text>
      <view class="header-right"></view>
    </view>

    <view v-if="loading" class="loading">
      <text>加载中...</text>
    </view>

    <view v-else-if="interview" class="report-content">
      <!-- 基本信息 -->
      <view class="info-bar">
        <text class="interview-date">{{ formatDate(interview.completedAt || interview.createdAt) }}</text>
      </view>

      <!-- 评分卡片 -->
      <view class="score-card">
        <view class="score-center">
          <view class="circle-progress">
            <svg class="progress-ring" width="120" height="120" viewBox="0 0 120 120">
              <!-- 背景圆 -->
              <circle
                cx="60"
                cy="60"
                r="48"
                fill="none"
                stroke="rgba(255,255,255,0.2)"
                stroke-width="8"
              />
              <!-- 进度圆 -->
              <circle
                cx="60"
                cy="60"
                r="48"
                fill="none"
                stroke="#fff"
                stroke-width="8"
                stroke-linecap="round"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="progressOffset"
                transform="rotate(-90 60 60)"
              />
            </svg>
            <view class="circle-inner">
              <text class="score-number">{{ interview.overallScore || 0 }}</text>
            </view>
          </view>
          <view class="score-label-text">总分</view>
        </view>
        <view class="score-feedback" v-if="interview.overallFeedback">
          <text class="feedback-text">{{ interview.overallFeedback }}</text>
        </view>
      </view>

      <!-- 表现优势 -->
      <view v-if="interview.strengths?.length" class="section-card strengths">
        <view class="section-header">
          <text class="section-icon">✓</text>
          <text class="section-title">表现优势</text>
        </view>
        <view class="section-content">
          <view v-for="(item, idx) in interview.strengths" :key="idx" class="list-item">
            <text class="bullet-point">•</text>
            <text class="item-text">{{ item }}</text>
          </view>
        </view>
      </view>

      <!-- 改进建议 -->
      <view v-if="interview.improvements?.length" class="section-card improvements">
        <view class="section-header">
          <text class="section-icon">!</text>
          <text class="section-title">改进建议</text>
        </view>
        <view class="section-content">
          <view v-for="(item, idx) in interview.improvements" :key="idx" class="list-item">
            <text class="bullet-point">•</text>
            <text class="item-text">{{ item }}</text>
          </view>
        </view>
      </view>

      <!-- 问答记录 -->
      <view class="section-card questions">
        <view class="section-header">
          <text class="section-icon">&#xe606;</text>
          <text class="section-title">问答记录</text>
        </view>
        <view class="questions-list">
          <view
            v-for="(answer, idx) in answers"
            :key="idx"
            class="question-item"
          >
            <view class="question-header" @click="toggleQuestion(idx)">
              <view class="question-info">
                <text class="question-index">题目 {{ idx + 1 }}</text>
                <text class="question-category">{{ answer.category }}</text>
              </view>
              <view class="question-score" :style="{ color: getScoreColor(answer.score) }">
                {{ answer.score }}分
              </view>
              <text class="expand-icon">{{ expandedQuestions.has(idx) ? '&#xe601;' : '&#xe600;' }}</text>
            </view>

            <view v-if="expandedQuestions.has(idx)" class="question-detail">
              <view class="detail-block">
                <text class="detail-label">面试题</text>
                <text class="detail-text">{{ answer.question }}</text>
              </view>
              <view class="detail-block">
                <text class="detail-label">你的回答</text>
                <text class="detail-text answer-text">{{ answer.userAnswer }}</text>
              </view>
              <view class="detail-block">
                <text class="detail-label">AI 评价</text>
                <text class="detail-text feedback-text">{{ answer.feedback }}</text>
              </view>
              <view v-if="answer.referenceAnswer" class="detail-block">
                <text class="detail-label">参考答案</text>
                <text class="detail-text reference-text">{{ answer.referenceAnswer }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <text>暂无数据</text>
    </view>
  </view>
</template>

<style lang="scss">
$primary: #6366f1;
$success: #67C23A;
$warning: #E6A23C;
$danger: #F56C6C;
$text-primary: #303133;
$text-regular: #606266;
$text-secondary: #909399;

.report-page {
  min-height: 100vh;
  background: #f5f7fa;
  overflow-x: hidden;
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background: #fff;
  border-bottom: 1rpx solid #eee;

  .header-left {
    width: 80rpx;

    .back-icon {
      font-size: 40rpx;
      color: #333;
    }
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: $text-primary;
    text-align: center;
    flex: 1;
  }

  .header-right {
    width: 80rpx;
  }
}

.loading, .empty {
  padding: 100rpx;
  text-align: center;
  color: $text-secondary;
}

.report-content {
  padding: 20rpx;
}

.info-bar {
  padding: 20rpx;
  margin-bottom: 20rpx;
  text-align: center;

  .interview-date {
    color: $text-secondary;
    font-size: 26rpx;
  }
}

.score-card {
  background: linear-gradient(135deg, $primary 0%, #818cf8 100%);
  border-radius: 24rpx;
  padding: 40rpx;
  display: flex;
  align-items: center;
  gap: 40rpx;
  margin-bottom: 20rpx;

  .score-center {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }

  .circle-progress {
    width: 140rpx;
    height: 140rpx;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
  }

  .progress-ring {
    position: absolute;
    top: 0;
    left: 0;
    width: 140rpx;
    height: 140rpx;
  }

  .circle-inner {
    width: 100rpx;
    height: 100rpx;
    border-radius: 50%;
    background: linear-gradient(135deg, $primary 0%, #818cf8 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1;
    position: relative;
  }

  .score-number {
    font-size: 56rpx;
    font-weight: 700;
    color: #fff;
  }

  .score-label-text {
    font-size: 24rpx;
    color: rgba(255, 255, 255, 0.9);
    margin-top: 8rpx;
  }

  .score-feedback {
    flex: 1;
    min-width: 0;

    .feedback-text {
      font-size: 26rpx;
      color: #fff;
      line-height: 1.6;
      word-break: break-all;
    }
  }
}

.section-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;

  .section-header {
    display: flex;
    align-items: center;
    margin-bottom: 24rpx;
  }

  .section-icon {
    font-size: 32rpx;
    font-weight: 600;
    margin-right: 12rpx;
  }

  .section-title {
    font-size: 30rpx;
    font-weight: 600;
    font-weight: 600;
    color: $text-primary;
  }

  .section-content {
    display: flex;
    flex-direction: column;
    gap: 16rpx;
  }
}

.strengths {
  .section-icon {
    color: $success;
    font-weight: 600;
  }

  .bullet-point {
    color: $primary;
  }
}

.improvements {
  .section-icon {
    color: $warning;
    font-weight: 600;
  }

  .bullet-point {
    color: $warning;
  }
}

.list-item {
  display: flex;
  align-items: flex-start;

  .bullet-point {
    margin-right: 12rpx;
    font-size: 28rpx;
  }

  .item-text {
    font-size: 26rpx;
    color: $text-regular;
    line-height: 1.5;
  }
}

.questions {
  .questions-list {
    display: flex;
    flex-direction: column;
    gap: 16rpx;
  }

  .question-item {
    border: 1rpx solid #eee;
    border-radius: 12rpx;
    overflow: hidden;
  }

  .question-header {
    display: flex;
    align-items: center;
    padding: 24rpx;
    background: #fafafa;
    flex-wrap: wrap;
    gap: 12rpx;

    .question-info {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 16rpx;
      min-width: 0;
      flex-wrap: wrap;
    }

    .question-index {
      font-size: 26rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .question-category {
      font-size: 22rpx;
      color: $primary;
      background: rgba(99, 102, 241, 0.1);
      padding: 4rpx 12rpx;
      border-radius: 6rpx;
      white-space: nowrap;
    }

    .question-score {
      font-size: 28rpx;
      font-weight: 600;
      margin-left: auto;
    }

    .expand-icon {
      font-size: 24rpx;
      color: $text-secondary;
      margin-left: 12rpx;
    }
  }

  .question-detail {
    padding: 24rpx;
    display: flex;
    flex-direction: column;
    gap: 20rpx;
  }

  .detail-block {
    word-break: break-all;

    .detail-label {
      font-size: 24rpx;
      color: $text-secondary;
      margin-bottom: 8rpx;
      display: block;
    }

    .detail-text {
      font-size: 26rpx;
      color: $text-regular;
      line-height: 1.6;
    }

    .answer-text {
      color: $text-primary;
    }

    .feedback-text {
      color: $primary;
    }

    .reference-text {
      color: $success;
      background: rgba(103, 194, 58, 0.1);
      padding: 16rpx;
      border-radius: 8rpx;
      display: block;
    }
  }
}
</style>
