<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getInterviewDetail, getAbilityProfile, getComprehensiveReport, getSessionPerspectives, getPerspectiveDetail, type ComprehensiveReportDTO, type PerspectiveScoreDTO, type PerspectiveDetailDTO } from '../../api/interview'

// 路由参数
const pageId = ref<string>('')

// 面试详情数据
const interview = ref<any>(null)
const comprehensiveReport = ref<ComprehensiveReportDTO | null>(null)
const perspectives = ref<PerspectiveScoreDTO[]>([])
const currentPerspectiveDetail = ref<PerspectiveDetailDTO | null>(null)
const loading = ref(true)
const abilityLoading = ref(true)
const expandedQuestions = ref<Set<number>>(new Set())

// Tab 相关
const currentTabIndex = ref(0)
const tabs = ref<string[]>(['综合报告'])
const perspectiveTabs = ref<{ id: number; name: string }[]>([])

// 计算分数颜色
const getScoreColor = (score: number) => {
  if (score >= 90) return '#67C23A'
  if (score >= 70) return '#409EFF'
  if (score >= 60) return '#E6A23C'
  return '#F56C6C'
}

// 切换 Tab
const switchTab = (index: number) => {
  currentTabIndex.value = index
}

// 页面参数
onMounted(() => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}
  pageId.value = options.id || ''
  if (pageId.value) {
    loadReportData()
  }
})

// 加载报告数据
const loadReportData = async () => {
  loading.value = true
  abilityLoading.value = true
  try {
    // 加载基本信息
    const detailRes = await getInterviewDetail(pageId.value)
    interview.value = detailRes

    // 从详情数据中获取answers
    if (detailRes.answers && detailRes.answers.length > 0) {
      detailRes.answers.forEach((_: any, idx: number) => {
        expandedQuestions.value.add(idx)
      })
    }

    // 加载综合报告
    try {
      const reportRes = await getComprehensiveReport(pageId.value)
      console.log('[Report] comprehensiveReport:', reportRes)
      comprehensiveReport.value = reportRes

      // 构建 Tab 列表
      tabs.value = ['综合报告']
      perspectiveTabs.value = []
      if (reportRes.perspectives && reportRes.perspectives.length > 0) {
        reportRes.perspectives.forEach((p: any) => {
          tabs.value.push(p.perspectiveName)
          perspectiveTabs.value.push({
            id: p.perspectiveId,
            name: p.perspectiveName
          })
        })
      }
    } catch (e) {
      console.error('获取综合报告失败:', e)
    }

    // 加载视角评分列表
    try {
      const perspectivesRes: any = await getSessionPerspectives(pageId.value)
      perspectives.value = Array.isArray(perspectivesRes) ? perspectivesRes : ((perspectivesRes as any).list || [])
    } catch (e) {
      console.error('获取视角列表失败:', e)
    }

    // 加载能力画像数据
    try {
      const profileRes = await getAbilityProfile(pageId.value)
      console.log('[Report] profileRes:', profileRes)
      abilityLoading.value = false
    } catch (e) {
      console.error('获取能力画像失败:', e)
      abilityLoading.value = false
    }
  } catch (error) {
    console.error('获取面试详情失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载指定视角详情
const loadPerspectiveDetail = async (perspectiveId: number) => {
  try {
    const detail = await getPerspectiveDetail(pageId.value, perspectiveId)
    currentPerspectiveDetail.value = detail
  } catch (e) {
    console.error('获取视角详情失败:', e)
  }
}

// Tab 切换时加载视角详情
const onTabChange = (index: number) => {
  currentTabIndex.value = index
  if (index === 0) {
    // 综合报告 Tab
    currentPerspectiveDetail.value = null
  } else {
    // 各视角 Tab
    const perspectiveIndex = index - 1
    if (perspectiveTabs.value[perspectiveIndex]) {
      loadPerspectiveDetail(perspectiveTabs.value[perspectiveIndex].id)
    }
  }
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// 获取答案列表（从interview中获取）
const answers = computed(() => interview.value?.answers || [])

// 圆形进度相关计算
const circumference = 2 * Math.PI * 48
const progressOffset = computed(() => {
  const score = comprehensiveReport.value?.overallScore || interview.value?.overallScore || 0
  const progress = score / 100
  return circumference * (1 - progress)
})

// 切换问题展开状态
const toggleQuestion = (index: number) => {
  if (expandedQuestions.value.has(index)) {
    expandedQuestions.value.delete(index)
  } else {
    expandedQuestions.value.add(index)
  }
}

// 视角图标映射
const perspectiveIcons: Record<string, string> = {
  '技术面试官': '💻',
  'HR面试官': '👔',
  '技术总监': '📋',
  'code': '💻',
  'user': '👔',
  'admin': '📋'
}

// 获取视角图标
const getPerspectiveIcon = (nameOrIcon: string) => {
  return perspectiveIcons[nameOrIcon] || '👤'
}

// 格式化学权重
const formatWeight = (weight: number) => {
  return `${Math.round(weight * 100)}%`
}
</script>

<template>
  <view class="report-page">
    <view v-if="loading" class="loading">
      <text>加载中...</text>
    </view>

    <view v-else-if="interview" class="report-content">
      <!-- 基本信息 -->
      <view class="info-bar">
        <text class="interview-date">{{ formatDate(interview.completedAt || interview.createdAt) }}</text>
      </view>

      <!-- Tab 切换 -->
      <view class="tabs-container">
        <scroll-view class="tabs-scroll" scroll-x>
          <view class="tabs-wrapper">
            <view
              v-for="(tab, index) in tabs"
              :key="index"
              class="tab-item"
              :class="{ active: currentTabIndex === index }"
              @click="switchTab(index); onTabChange(index)"
            >
              <text class="tab-text">{{ tab }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 综合报告 Tab -->
      <view v-if="currentTabIndex === 0" class="tab-content">
        <!-- 评分卡片 -->
        <view class="score-card">
          <view class="score-center">
            <view class="circle-progress">
              <svg class="progress-ring" width="120" height="120" viewBox="0 0 120 120">
                <circle
                  cx="60"
                  cy="60"
                  r="48"
                  fill="none"
                  stroke="rgba(255,255,255,0.2)"
                  stroke-width="8"
                />
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
                <text class="score-number">{{ comprehensiveReport?.overallScore || interview.overallScore || 0 }}</text>
              </view>
            </view>
            <view class="score-label-text">综合评分</view>
          </view>
          <view class="score-feedback" v-if="comprehensiveReport?.comprehensiveFeedback || interview.overallFeedback">
            <text class="feedback-text">{{ comprehensiveReport?.comprehensiveFeedback || interview.overallFeedback }}</text>
          </view>
        </view>

        <!-- 综合评价 -->
        <view v-if="comprehensiveReport?.evaluation" class="section-card evaluation">
          <view class="section-header">
            <text class="section-icon">📝</text>
            <text class="section-title">综合评价</text>
          </view>
          <text class="evaluation-content">{{ comprehensiveReport.evaluation }}</text>
        </view>

        <!-- 发展建议 -->
        <view v-if="comprehensiveReport?.developmentSuggestions" class="section-card suggestions">
          <view class="section-header">
            <text class="section-icon">💡</text>
            <text class="section-title">发展建议</text>
          </view>
          <text class="suggestions-content">{{ comprehensiveReport.developmentSuggestions }}</text>
        </view>

        <!-- 各视角得分汇总 -->
        <view v-if="comprehensiveReport?.perspectives?.length" class="section-card perspectives-summary">
          <view class="section-header">
            <text class="section-icon">📊</text>
            <text class="section-title">各视角得分</text>
          </view>
          <view class="perspectives-list">
            <view
              v-for="p in comprehensiveReport.perspectives"
              :key="p.perspectiveId"
              class="perspective-item"
            >
              <text class="perspective-icon">{{ getPerspectiveIcon(p.perspectiveName) }}</text>
              <text class="perspective-name">{{ p.perspectiveName }}</text>
              <text class="perspective-weight">权重 {{ formatWeight(p.weight) }}</text>
              <view class="perspective-score" :style="{ color: getScoreColor(p.score || 0) }">
                {{ p.score || '--' }}分
              </view>
            </view>
          </view>
        </view>

        <!-- 表现优势 -->
        <view v-if="comprehensiveReport?.strengths?.length" class="section-card strengths">
          <view class="section-header">
            <text class="section-icon">✓</text>
            <text class="section-title">综合优势</text>
          </view>
          <view class="section-content">
            <view v-for="(item, idx) in comprehensiveReport.strengths" :key="idx" class="list-item">
              <text class="bullet-point">•</text>
              <text class="item-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <!-- 改进建议 -->
        <view v-if="comprehensiveReport?.improvements?.length" class="section-card improvements">
          <view class="section-header">
            <text class="section-icon">!</text>
            <text class="section-title">改进建议</text>
          </view>
          <view class="section-content">
            <view v-for="(item, idx) in comprehensiveReport.improvements" :key="idx" class="list-item">
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
                  <text v-if="(answer as any).perspectiveName" class="question-perspective">[{{ (answer as any).perspectiveName }}]</text>
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

      <!-- 各视角 Tab -->
      <view v-else class="tab-content">
        <view class="perspective-detail" v-if="currentPerspectiveDetail">
          <!-- 视角评分卡片 -->
          <view class="perspective-score-card">
            <view class="perspective-header-info">
              <text class="perspective-icon-large">{{ getPerspectiveIcon(currentPerspectiveDetail.perspectiveIcon || currentPerspectiveDetail.roleName) }}</text>
              <view class="perspective-info">
                <text class="perspective-name">{{ currentPerspectiveDetail.roleName }}</text>
                <text class="perspective-meta">
                  共 {{ currentPerspectiveDetail.questionCount }} 题 ·
                  已评 {{ currentPerspectiveDetail.answeredCount }} 题
                </text>
              </view>
            </view>
            <view class="perspective-score-display">
              <text class="score-value" :style="{ color: getScoreColor(currentPerspectiveDetail.score || 0) }">
                {{ currentPerspectiveDetail.score || '--' }}
              </text>
              <text class="score-unit">分</text>
            </view>
          </view>

          <!-- 综合评价 -->
          <view v-if="currentPerspectiveDetail.feedback" class="section-card perspective-feedback">
            <view class="section-header">
              <text class="section-icon">💬</text>
              <text class="section-title">综合评价</text>
            </view>
            <text class="feedback-content">{{ currentPerspectiveDetail.feedback }}</text>
          </view>

          <!-- 优势 -->
          <view v-if="currentPerspectiveDetail.strengths?.length" class="section-card strengths">
            <view class="section-header">
              <text class="section-icon">✓</text>
              <text class="section-title">优势</text>
            </view>
            <view class="section-content">
              <view v-for="(item, idx) in currentPerspectiveDetail.strengths" :key="idx" class="list-item">
                <text class="bullet-point">•</text>
                <text class="item-text">{{ item }}</text>
              </view>
            </view>
          </view>

          <!-- 改进建议 -->
          <view v-if="currentPerspectiveDetail.improvements?.length" class="section-card improvements">
            <view class="section-header">
              <text class="section-icon">!</text>
              <text class="section-title">改进建议</text>
            </view>
            <view class="section-content">
              <view v-for="(item, idx) in currentPerspectiveDetail.improvements" :key="idx" class="list-item">
                <text class="bullet-point">•</text>
                <text class="item-text">{{ item }}</text>
              </view>
            </view>
          </view>

          <!-- 该视角的问答记录 -->
          <view v-if="currentPerspectiveDetail.questionScores?.length" class="section-card questions">
            <view class="section-header">
              <text class="section-icon">&#xe606;</text>
              <text class="section-title">问答记录</text>
            </view>
            <view class="questions-list">
              <view
                v-for="(answer, idx) in currentPerspectiveDetail.questionScores"
                :key="idx"
                class="question-item"
              >
                <view class="question-header" @click="toggleQuestion(idx)">
                  <view class="question-info">
                    <text class="question-index">第 {{ idx + 1 }} 题</text>
                    <text class="question-category">{{ answer.category }}</text>
                    <text class="question-difficulty" :class="'diff-' + answer.difficulty.toLowerCase()">
                      {{ answer.difficulty }}
                    </text>
                  </view>
                  <view class="question-score" :style="{ color: getScoreColor(answer.score || 0) }">
                    {{ answer.score || '--' }}分
                  </view>
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
                  <view v-if="answer.feedback" class="detail-block">
                    <text class="detail-label">AI 评价</text>
                    <text class="detail-text feedback-text">{{ answer.feedback }}</text>
                  </view>
                  <view v-if="answer.referenceAnswer" class="detail-block">
                    <text class="detail-label">参考答案</text>
                    <text class="detail-text reference-text">{{ answer.referenceAnswer }}</text>
                  </view>
                  <view v-if="answer.keyPoints?.length" class="detail-block">
                    <text class="detail-label">关键要点</text>
                    <view class="key-points-list">
                      <text v-for="(point, kIdx) in answer.keyPoints" :key="kIdx" class="key-point-tag">{{ point }}</text>
                    </view>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 视角详情加载中 -->
        <view v-else class="loading-perspective">
          <text>加载视角详情...</text>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <text>暂无数据</text>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

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

.loading, .empty {
  padding: 100rpx;
  text-align: center;
  color: $text-secondary;
}

.loading-perspective {
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

// Tab 样式
.tabs-container {
  background-color: #fff;
  margin-bottom: 20rpx;
  border-radius: 16rpx;
  overflow: hidden;
}

.tabs-scroll {
  white-space: nowrap;
}

.tabs-wrapper {
  display: flex;
  padding: 0 10rpx;
}

.tab-item {
  display: inline-block;
  padding: 24rpx 30rpx;
  font-size: 28rpx;
  color: $text-secondary;
  position: relative;
  flex-shrink: 0;

  &.active {
    color: $primary;
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 60%;
      height: 4rpx;
      background-color: $primary;
      border-radius: 2rpx;
    }
  }
}

.tab-content {
  // Content styles
}

.score-card {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
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
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
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
    color: $text-primary;
  }

  .section-content {
    display: flex;
    flex-direction: column;
    gap: 16rpx;
  }
}

// 视角汇总样式
.perspectives-summary {
  .perspectives-list {
    display: flex;
    flex-direction: column;
    gap: 16rpx;
  }

  .perspective-item {
    display: flex;
    align-items: center;
    padding: 20rpx;
    background-color: #f9fafb;
    border-radius: 12rpx;

    .perspective-icon {
      font-size: 32rpx;
      margin-right: 16rpx;
    }

    .perspective-name {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
      margin-right: 16rpx;
    }

    .perspective-weight {
      font-size: 24rpx;
      color: $text-secondary;
      flex: 1;
    }

    .perspective-score {
      font-size: 32rpx;
      font-weight: 700;
    }
  }
}

// 分类得分样式
.category-scores {
  .category-list {
    display: flex;
    flex-direction: column;
    gap: 20rpx;
  }

  .category-item {
    display: flex;
    align-items: center;
    gap: 16rpx;

    .category-info {
      display: flex;
      flex-direction: column;
      min-width: 120rpx;

      .category-name {
        font-size: 26rpx;
        color: $text-primary;
        font-weight: 500;
      }

      .category-count {
        font-size: 22rpx;
        color: $text-secondary;
      }
    }

    .category-score-bar {
      flex: 1;
      height: 12rpx;
      background-color: #f0f0f0;
      border-radius: 6rpx;
      overflow: hidden;

      .bar-fill {
        height: 100%;
        border-radius: 6rpx;
        transition: width 0.3s;
      }
    }

    .category-score {
      font-size: 26rpx;
      font-weight: 600;
      min-width: 80rpx;
      text-align: right;
    }
  }
}

.strengths {
  .section-icon {
    color: $success;
  }

  .bullet-point {
    color: $primary;
  }
}

.improvements {
  .section-icon {
    color: $warning;
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
    flex: 1;
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
    }

    .question-perspective {
      font-size: 22rpx;
      color: $primary;
      background: rgba(99, 102, 241, 0.15);
      padding: 4rpx 12rpx;
      border-radius: 6rpx;
      font-weight: 600;
    }

    .question-difficulty {
      font-size: 22rpx;
      padding: 4rpx 12rpx;
      border-radius: 6rpx;

      &.diff-basic {
        color: #059669;
        background: #d1fae5;
      }

      &.diff-advanced {
        color: #d97706;
        background: #fef3c7;
      }

      &.diff-expert {
        color: #dc2626;
        background: #fee2e2;
      }
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

  .key-points-list {
    display: flex;
    flex-wrap: wrap;
    gap: 12rpx;
    margin-top: 8rpx;

    .key-point-tag {
      font-size: 22rpx;
      color: $primary;
      background: rgba(99, 102, 241, 0.1);
      padding: 6rpx 16rpx;
      border-radius: 20rpx;
      border: 1rpx solid rgba(99, 102, 241, 0.2);
    }
  }
}

// 视角详情页样式
.perspective-detail {
  // ...
}

.perspective-score-card {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 24rpx;
  padding: 40rpx;
  margin-bottom: 20rpx;

  .perspective-header-info {
    display: flex;
    align-items: center;
    margin-bottom: 30rpx;

    .perspective-icon-large {
      font-size: 56rpx;
      margin-right: 20rpx;
    }

    .perspective-info {
      flex: 1;

      .perspective-name {
        font-size: 36rpx;
        font-weight: 700;
        color: #fff;
        display: block;
        margin-bottom: 8rpx;
      }

      .perspective-meta {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.8);
      }
    }
  }

  .perspective-score-display {
    display: flex;
    align-items: baseline;

    .score-value {
      font-size: 80rpx;
      font-weight: 700;
    }

    .score-unit {
      font-size: 32rpx;
      color: rgba(255, 255, 255, 0.8);
      margin-left: 8rpx;
    }
  }
}

.perspective-feedback {
  .feedback-content {
    font-size: 28rpx;
    color: $text-regular;
    line-height: 1.6;
  }
}

.evaluation {
  .section-icon {
    color: $primary;
  }

  .evaluation-content {
    font-size: 28rpx;
    color: $text-regular;
    line-height: 1.8;
  }
}

.suggestions {
  .section-icon {
    color: $warning;
  }

  .suggestions-content {
    font-size: 28rpx;
    color: $text-regular;
    line-height: 1.8;
  }
}
</style>
