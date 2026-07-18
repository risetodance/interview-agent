<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { getInterviewDetail, getAbilityProfile, getComprehensiveReport, getSessionPerspectives, getPerspectiveDetail, type ComprehensiveReportDTO, type PerspectiveScoreDTO, type PerspectiveDetailDTO, type AbilityProfile, type CategoryScore } from '../../api/interview'
import { renderMarkdown } from '../../utils/marked'

// 路由参数
const pageId = ref<string>('')

// 面试详情数据
const interview = ref<any>(null)
const comprehensiveReport = ref<ComprehensiveReportDTO | null>(null)
const perspectives = ref<PerspectiveScoreDTO[]>([])
const currentPerspectiveDetail = ref<PerspectiveDetailDTO | null>(null)
const loading = ref(true)
const abilityLoading = ref(true)
// 能力画像（N1：原 getAbilityProfile 返回值被丢弃，现绑定渲染）
const abilityProfile = ref<AbilityProfile | null>(null)

// Tab 相关
const currentTabIndex = ref(0)
const tabs = ref<string[]>(['综合报告'])
const perspectiveTabs = ref<{ id: number; name: string }[]>([])

// 计算分数颜色 - 与Web端一致
const getScoreColor = (score: number) => {
  if (score >= 80) return '#16a34a'  // green-600
  if (score >= 60) return '#d97706'  // amber-600
  return '#dc2626'  // red-600
}

// 切换 Tab
const switchTab = (index: number) => {
  currentTabIndex.value = index
}

// 轮询定时器
let pollingTimer: ReturnType<typeof setInterval> | null = null

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

onUnmounted(() => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
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

    // 加载综合报告
    try {
      const reportRes = await getComprehensiveReport(pageId.value)
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
      console.error('加载综合报告失败:', e)
    }

    // 加载视角评分列表
    try {
      const perspectivesRes: any = await getSessionPerspectives(pageId.value)
      perspectives.value = Array.isArray(perspectivesRes) ? perspectivesRes : ((perspectivesRes as any).list || [])
    } catch (e) {
      console.error('加载视角评分列表失败:', e)
    }

    // 加载能力画像数据（N1：绑定到 abilityProfile 以供模板渲染）
    try {
      const profileRes = await getAbilityProfile(pageId.value)
      abilityProfile.value = profileRes ?? null
      abilityLoading.value = false
    } catch (e) {
      abilityProfile.value = null
      abilityLoading.value = false
    }
  } catch (error) {
    console.error('加载报告数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 轮询检查状态（Web端逻辑）
const startPolling = () => {
  if (pollingTimer) return

  pollingTimer = setInterval(() => {
    const hasProcessing = perspectives.value.some(p => p.status === 'PROCESSING' || p.status === 'PENDING')
    if (!hasProcessing || loading.value) return

    loadPerspectives()
    if (comprehensiveReport.value === null) {
      loadComprehensiveReport()
    }
  }, 5000)
}

// 停止轮询
const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 加载视角概览
const loadPerspectives = async () => {
  try {
    const data = await getSessionPerspectives(pageId.value)
    perspectives.value = Array.isArray(data) ? data : ((data as any).list || [])
  } catch (err) {
    console.error('加载视角概览失败:', err)
  }
}

// 加载综合报告
const loadComprehensiveReport = async () => {
  try {
    const data = await getComprehensiveReport(pageId.value)
    comprehensiveReport.value = data
  } catch (err) {
    console.error('加载综合报告失败:', err)
  }
}

// 监听状态变化，决定是否轮询
watch(() => perspectives.value, (newPerspectives) => {
  const hasProcessing = newPerspectives.some(p => p.status === 'PROCESSING' || p.status === 'PENDING')
  if (hasProcessing && !loading.value) {
    startPolling()
  } else {
    stopPolling()
  }
}, { deep: true })

// 加载指定视角详情
const loadPerspectiveDetail = async (perspectiveId: number) => {
  try {
    const detail = await getPerspectiveDetail(pageId.value, perspectiveId)
    currentPerspectiveDetail.value = detail
  } catch (e) {
    console.error('加载视角详情失败:', e)
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

// 圆形进度相关计算
const circumference = 2 * Math.PI * 36
const progressOffset = computed(() => {
  const score = comprehensiveReport.value?.overallScore || interview.value?.overallScore || 0
  const progress = score / 100
  return circumference * (1 - progress)
})

// 各视角得分进度条宽度计算
const getProgressWidth = (score: number | null | undefined) => {
  if (score === null || score === undefined) return '0%'
  return `${score}%`
}

// 能力画像各维度（categoryScores 是 Record，扁平化为数组渲染，N1）
const abilityCategoryList = computed<CategoryScore[]>(() => {
  const cs = abilityProfile.value?.categoryScores
  if (!cs) return []
  return Object.values(cs)
})

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

// 获取状态颜色
const getStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    'PENDING': 'bg-slate-100 text-slate-600',
    'PROCESSING': 'bg-blue-100 text-blue-600',
    'COMPLETED': 'bg-green-100 text-green-600',
    'FAILED': 'bg-red-100 text-red-600'
  }
  return colors[status] || 'bg-slate-100 text-slate-600'
}

// 获取状态文本
const getStatusText = (perspective: PerspectiveScoreDTO) => {
  if (perspective.status === 'PROCESSING') return '评估中'
  if (perspective.status === 'COMPLETED' && perspective.score !== null) return `${perspective.score}分`
  if (perspective.status === 'PENDING') return '等待中'
  return ''
}
</script>

<template>
  <view class="report-page">
    <!-- 加载状态 -->
    <view v-if="loading" class="loading-container">
      <view class="loading-spinner"></view>
      <text class="loading-text">加载中...</text>
    </view>

    <!-- 错误状态 -->
    <view v-else-if="!interview" class="error-container">
      <text class="error-icon">!</text>
      <text class="error-text">加载报告失败</text>
    </view>

    <!-- 报告内容 -->
    <view v-else class="report-content">
      <!-- 页面标题 -->
      <view class="page-header">
        <view class="header-left">
          <view class="header-icon">📊</view>
          <view class="header-info">
            <text class="header-title">面试报告</text>
            <text class="header-subtitle">多视角评估综合报告</text>
          </view>
        </view>
      </view>

      <!-- Tab 导航 -->
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
              <text class="tab-icon">{{ index === 0 ? '📊' : (perspectives[index - 1]?.perspectiveIcon || '👤') }}</text>
              <text class="tab-text">{{ tab }}</text>
              <!-- 视角状态标签 -->
              <view v-if="index > 0" class="tab-status" :class="getStatusColor(perspectives[index - 1]?.status || 'PENDING')">
                <view v-if="perspectives[index - 1]?.status === 'PROCESSING'" class="status-spinner"></view>
                <text class="status-text">{{ getStatusText(perspectives[index - 1]) }}</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 综合报告 Tab -->
      <view v-if="currentTabIndex === 0" class="tab-content">
        <!-- 综合得分卡片 -->
        <view class="comprehensive-score-card">
          <view class="score-main">
            <text class="score-label">综合得分</text>
            <text class="score-value">{{ comprehensiveReport?.overallScore || interview.overallScore || 0 }}</text>
            <text class="score-unit">加权平均分</text>
          </view>
        </view>

        <!-- 各视角得分 + 评估统计 -->
        <view class="grid-2col">
          <!-- 各视角得分 -->
          <view class="card perspectives-card">
            <view class="card-header">
              <text class="card-icon">📈</text>
              <text class="card-title">各视角得分</text>
            </view>
            <view class="perspectives-list">
              <view
                v-for="p in (comprehensiveReport?.perspectives || [])"
                :key="p.perspectiveId"
                class="perspective-item"
              >
                <view class="perspective-info">
                  <text class="perspective-icon">{{ p.perspectiveIcon || getPerspectiveIcon(p.perspectiveName) }}</text>
                  <text class="perspective-name">{{ p.perspectiveName }}</text>
                  <text v-if="p.weight !== undefined" class="perspective-weight">权重 {{ formatWeight(p.weight) }}</text>
                </view>
                <view class="perspective-bar-container">
                  <view class="perspective-bar">
                    <view
                      class="perspective-bar-fill"
                      :style="{ width: getProgressWidth(p.score) }"
                    ></view>
                  </view>
                </view>
                <text class="perspective-score" :style="{ color: getScoreColor(p.score || 0) }">
                  {{ p.score !== null && p.score !== undefined ? `${p.score}分` : '-' }}
                </text>
              </view>
              <!-- 无视角数据时显示空状态 -->
              <view v-if="!comprehensiveReport?.perspectives?.length" class="empty-perspectives">
                <text>暂无视角数据</text>
              </view>
            </view>
          </view>

          <!-- 评估统计 -->
          <view class="card stats-card">
            <view class="card-header">
              <text class="card-icon">📋</text>
              <text class="card-title">评估统计</text>
            </view>
            <view class="stats-list">
              <view class="stat-item">
                <view class="stat-icon good">✓</view>
                <view class="stat-info">
                  <text class="stat-label">优势数量</text>
                  <text class="stat-value">{{ comprehensiveReport?.strengths?.length || 0 }}</text>
                </view>
              </view>
              <view class="stat-item">
                <view class="stat-icon warn">!</view>
                <view class="stat-info">
                  <text class="stat-label">改进建议</text>
                  <text class="stat-value">{{ comprehensiveReport?.improvements?.length || 0 }}</text>
                </view>
              </view>
              <view class="stat-item">
                <view class="stat-icon info">💬</view>
                <view class="stat-info">
                  <text class="stat-label">视角数量</text>
                  <text class="stat-value">{{ comprehensiveReport?.perspectives?.length || 0 }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 综合评价（父容器） -->
        <view v-if="comprehensiveReport?.evaluation || comprehensiveReport?.developmentSuggestions" class="card">
          <view class="card-header">
            <text class="card-icon">💬</text>
            <text class="card-title">综合评价</text>
          </view>
          <view class="evaluation-content">
            <view v-if="comprehensiveReport?.evaluation" class="eval-section">
              <view class="eval-subheader">
                <text class="eval-subicon">📝</text>
                <text class="eval-subtitle">评价</text>
              </view>
              <text class="eval-text">{{ comprehensiveReport.evaluation }}</text>
            </view>
            <view v-if="comprehensiveReport?.developmentSuggestions" class="eval-section">
              <view class="eval-subheader">
                <text class="eval-subicon">💡</text>
                <text class="eval-subtitle">发展建议</text>
              </view>
              <text class="eval-text">{{ comprehensiveReport.developmentSuggestions }}</text>
            </view>
          </view>
        </view>

        <!-- 综合优势 -->
        <view v-if="comprehensiveReport?.strengths?.length" class="card strengths-card">
          <view class="card-header">
            <text class="card-icon good">✓</text>
            <text class="card-title">综合优势</text>
          </view>
          <view class="list-content">
            <view v-for="(item, idx) in comprehensiveReport.strengths" :key="idx" class="list-item">
              <view class="bullet good"></view>
              <text class="item-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <!-- 综合改进建议 -->
        <view v-if="comprehensiveReport?.improvements?.length" class="card improvements-card">
          <view class="card-header">
            <text class="card-icon warn">!</text>
            <text class="card-title">改进建议</text>
          </view>
          <view class="list-content">
            <view v-for="(item, idx) in comprehensiveReport.improvements" :key="idx" class="list-item">
              <view class="bullet warn"></view>
              <text class="item-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <!-- 能力画像（N1：原 getAbilityProfile 返回值被丢弃，现绑定渲染） -->
        <view v-if="abilityProfile" class="card ability-profile-card">
          <view class="card-header">
            <text class="card-icon info">🎯</text>
            <text class="card-title">能力画像</text>
          </view>
          <view class="ability-overall">
            <text class="ability-label">综合均分</text>
            <text class="ability-score" :style="{ color: getScoreColor(abilityProfile.overallScore || 0) }">{{ abilityProfile.overallScore ?? 0 }}</text>
          </view>
          <view v-if="abilityCategoryList.length" class="ability-categories">
            <view v-for="cat in abilityCategoryList" :key="cat.category" class="ability-cat-item">
              <view class="ability-cat-info">
                <text class="ability-cat-name">{{ cat.category }}</text>
                <text class="ability-cat-meta">{{ cat.count ?? 0 }} 题</text>
              </view>
              <view class="ability-cat-bar">
                <view class="ability-cat-bar-fill" :style="{ width: getProgressWidth(cat.avgScore) }"></view>
              </view>
              <text class="ability-cat-score">{{ cat.avgScore ?? 0 }}</text>
            </view>
          </view>
          <view v-if="abilityProfile.strengths?.length" class="ability-sub">
            <text class="ability-sub-title good">优势</text>
            <view class="ability-tags">
              <text v-for="(s, i) in abilityProfile.strengths" :key="'s'+i" class="ability-tag good">{{ s }}</text>
            </view>
          </view>
          <view v-if="abilityProfile.weaknesses?.length" class="ability-sub">
            <text class="ability-sub-title warn">待加强</text>
            <view class="ability-tags">
              <text v-for="(w, i) in abilityProfile.weaknesses" :key="'w'+i" class="ability-tag warn">{{ w }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 各视角 Tab -->
      <view v-else class="tab-content">
        <view class="perspective-detail" v-if="currentPerspectiveDetail">
          <!-- 视角信息卡片 -->
          <view class="perspective-score-card">
            <view class="perspective-header-info">
              <text class="perspective-icon-large">{{ getPerspectiveIcon(currentPerspectiveDetail.perspectiveIcon || currentPerspectiveDetail.roleName) }}</text>
              <view class="perspective-info">
                <text class="perspective-name">{{ currentPerspectiveDetail.roleName }}</text>
                <view class="perspective-meta">
                  <text class="meta-item">共 {{ currentPerspectiveDetail.questionCount || currentPerspectiveDetail.questionScores?.length || 0 }} 题</text>
                  <text class="meta-sep">·</text>
                  <text class="meta-item">已评 {{ currentPerspectiveDetail.answeredCount || currentPerspectiveDetail.questionScores?.filter(q => q.score !== null).length || 0 }} 题</text>
                </view>
              </view>
            </view>
            <view class="perspective-score-display">
              <text class="score-value" :style="{ color: getScoreColor(currentPerspectiveDetail.score || 0) }">
                {{ currentPerspectiveDetail.score !== null && currentPerspectiveDetail.score !== undefined ? currentPerspectiveDetail.score : '--' }}
              </text>
              <text class="score-unit">分</text>
            </view>
          </view>

          <!-- 综合评价 -->
          <view v-if="currentPerspectiveDetail.feedback" class="card perspective-feedback">
            <view class="card-header">
              <text class="card-icon">💬</text>
              <text class="card-title">综合评价</text>
            </view>
            <text class="feedback-content">{{ currentPerspectiveDetail.feedback }}</text>
          </view>

          <!-- 优势 -->
          <view v-if="currentPerspectiveDetail.strengths?.length" class="card strengths-card">
            <view class="card-header">
              <text class="card-icon good">✓</text>
              <text class="card-title">优势</text>
            </view>
            <view class="list-content">
              <view v-for="(item, idx) in currentPerspectiveDetail.strengths" :key="idx" class="list-item">
                <view class="bullet good"></view>
                <text class="item-text">{{ item }}</text>
              </view>
            </view>
          </view>

          <!-- 改进建议 -->
          <view v-if="currentPerspectiveDetail.improvements?.length" class="card improvements-card">
            <view class="card-header">
              <text class="card-icon warn">!</text>
              <text class="card-title">改进建议</text>
            </view>
            <view class="list-content">
              <view v-for="(item, idx) in currentPerspectiveDetail.improvements" :key="idx" class="list-item">
                <view class="bullet warn"></view>
                <text class="item-text">{{ item }}</text>
              </view>
            </view>
          </view>

          <!-- 该视角的问答记录 -->
          <view v-if="currentPerspectiveDetail.questionScores?.length" class="card questions-card">
            <view class="card-header">
              <text class="card-icon">❓</text>
              <text class="card-title">问题详情</text>
            </view>
            <view class="questions-list">
              <view
                v-for="(answer, idx) in currentPerspectiveDetail.questionScores"
                :key="idx"
                class="question-item"
              >
                <view class="question-header">
                  <view class="question-info">
                    <text class="question-index">第 {{ idx + 1 }} 题</text>
                    <text class="question-category">{{ answer.category }}</text>
                    <text class="question-difficulty" :class="'diff-' + (answer.difficulty?.toLowerCase() || 'basic')">
                      {{ answer.difficulty || '基础' }}
                    </text>
                  </view>
                  <view class="question-score" :style="{ color: getScoreColor(answer.score || 0) }">
                    {{ answer.score !== null && answer.score !== undefined ? `${answer.score}分` : '--' }}
                  </view>
                </view>

                <view class="question-detail">
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
                    <rich-text class="detail-text reference-text" :nodes="renderMarkdown(answer.referenceAnswer)"></rich-text>
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
          <view class="loading-spinner"></view>
          <text class="loading-text">加载中...</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@use '../../styles/variables.scss' as *;

// 使用 Web 端一致的配色
$success: #22c55e;  // green-500
$success-dark: #16a34a;  // green-600
$warning: #f59e0b;  // amber-500
$danger: #ef4444;  // red-500
$text-primary: #1e293b;  // slate-800
$text-regular: #475569;  // slate-600
$text-secondary: #64748b;  // slate-500

.report-page {
  min-height: 100vh;
  background: $bg;
  overflow-x: hidden;
}

// 加载容器
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 0;
}

.loading-spinner {
  width: 64rpx;
  height: 64rpx;
  border: 4rpx solid #e2e8f0;
  border-top-color: $primary;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  margin-top: 20rpx;
  color: $text-secondary;
  font-size: 28rpx;
}

// 错误容器
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 0;
}

.error-icon {
  width: 80rpx;
  height: 80rpx;
  background: $danger;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  font-weight: bold;
}

.error-text {
  margin-top: 20rpx;
  color: $danger;
  font-size: 28rpx;
}

.report-content {
  padding: 24rpx;
}

// 页面头部
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16rpx;
  }

  .header-icon {
    width: 80rpx;
    height: 80rpx;
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 40rpx;
  }

  .header-info {
    display: flex;
    flex-direction: column;
  }

  .header-title {
    font-size: 40rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .header-subtitle {
    font-size: 24rpx;
    color: $text-secondary;
    margin-top: 4rpx;
  }
}

// Tab 样式
.tabs-container {
  background-color: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.tabs-scroll {
  white-space: nowrap;
}

.tabs-wrapper {
  display: flex;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 28rpx 32rpx;
  font-size: 28rpx;
  color: $text-secondary;
  position: relative;
  flex-shrink: 0;
  transition: all 0.2s;

  &.active {
    color: $primary;
    font-weight: 600;
    background: rgba($primary, 0.05);

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 80%;
      height: 4rpx;
      background-color: $primary;
      border-radius: 2rpx;
    }
  }

  .tab-icon {
    font-size: 28rpx;
  }
}

.tab-status {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 12rpx;
  border-radius: 20rpx;
  font-size: 20rpx;
  margin-left: 8rpx;

  &.bg-slate-100 {
    background: #f1f5f9;
  }
  &.text-slate-600 {
    color: #64748b;
  }
  &.bg-blue-100 {
    background: #dbeafe;
  }
  &.text-blue-600 {
    color: #2563eb;
  }
  &.bg-green-100 {
    background: #dcfce7;
  }
  &.text-green-600 {
    color: #16a34a;
  }
  &.bg-red-100 {
    background: #fee2e2;
  }
  &.text-red-600 {
    color: #dc2626;
  }
}

.status-spinner {
  width: 16rpx;
  height: 16rpx;
  border: 2rpx solid #93c5fd;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-right: 4rpx;
}

.status-text {
  white-space: nowrap;
}

// 综合得分卡片
.comprehensive-score-card {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 24rpx;
  padding: 48rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;

  .score-main {
    display: flex;
    flex-direction: column;
  }

  .score-label {
    font-size: 28rpx;
    color: rgba(255, 255, 255, 0.8);
    margin-bottom: 12rpx;
  }

  .score-value {
    font-size: 96rpx;
    font-weight: 700;
    color: #fff;
    line-height: 1;
  }

  .score-unit {
    font-size: 28rpx;
    color: rgba(255, 255, 255, 0.7);
    margin-top: 12rpx;
  }

  .score-circle {
    width: 100rpx;
    height: 100rpx;
    border-radius: 50%;
    border: 8rpx solid rgba(255, 255, 255, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;

    .circle-icon {
      font-size: 40rpx;
    }
  }
}

// 两列网格布局
.grid-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

// 卡片通用样式
.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .card-header {
    display: flex;
    align-items: center;
    margin-bottom: 24rpx;
  }

  .card-icon {
    font-size: 32rpx;
    margin-right: 12rpx;

    &.good { color: $success; }
    &.warn { color: $warning; }
    &.info { color: $primary; }
  }

  .card-title {
    font-size: 30rpx;
    font-weight: 600;
    color: $text-primary;
  }
}

// 视角列表
.perspectives-card {
  .perspectives-list {
    display: flex;
    flex-direction: column;
    gap: 20rpx;
  }

  .perspective-item {
    display: flex;
    align-items: center;
    gap: 12rpx;
  }

  .perspective-info {
    display: flex;
    align-items: center;
    gap: 8rpx;
    min-width: 160rpx;

    .perspective-icon {
      font-size: 28rpx;
    }

    .perspective-name {
      font-size: 26rpx;
      color: $text-primary;
      font-weight: 500;
    }

    .perspective-weight {
      font-size: 20rpx;
      color: $text-secondary;
    }
  }

  .perspective-bar-container {
    flex: 1;
    height: 12rpx;
    background: #f0f0f0;
    border-radius: 6rpx;
    overflow: hidden;
  }

  .perspective-bar {
    width: 100%;
    height: 100%;
  }

  .perspective-bar-fill {
    height: 100%;
    background: linear-gradient(90deg, $primary 0%, $primary-light 100%);
    border-radius: 6rpx;
    transition: width 0.5s ease;
  }

  .perspective-score {
    font-size: 26rpx;
    font-weight: 600;
    min-width: 48rpx;
    text-align: right;
  }

  .empty-perspectives {
    text-align: center;
    padding: 40rpx 0;
    color: $text-secondary;
    font-size: 26rpx;
  }
}

// 能力画像卡片（N1）
.ability-profile-card {
  .ability-overall {
    display: flex;
    align-items: baseline;
    gap: 16rpx;
    margin-bottom: 24rpx;
    padding-bottom: 20rpx;
    border-bottom: 1rpx solid #f0f0f0;

    .ability-label {
      font-size: 26rpx;
      color: $text-secondary;
    }

    .ability-score {
      font-size: 48rpx;
      font-weight: 700;
    }
  }

  .ability-categories {
    display: flex;
    flex-direction: column;
    gap: 20rpx;
    margin-bottom: 24rpx;
  }

  .ability-cat-item {
    display: flex;
    align-items: center;
    gap: 12rpx;
  }

  .ability-cat-info {
    display: flex;
    flex-direction: column;
    min-width: 140rpx;

    .ability-cat-name {
      font-size: 26rpx;
      color: $text-primary;
      font-weight: 500;
    }

    .ability-cat-meta {
      font-size: 20rpx;
      color: $text-secondary;
    }
  }

  .ability-cat-bar {
    flex: 1;
    height: 12rpx;
    background: #f0f0f0;
    border-radius: 6rpx;
    overflow: hidden;
  }

  .ability-cat-bar-fill {
    height: 100%;
    background: linear-gradient(90deg, $primary 0%, $primary-light 100%);
    border-radius: 6rpx;
  }

  .ability-cat-score {
    font-size: 26rpx;
    font-weight: 600;
    min-width: 48rpx;
    text-align: right;
  }

  .ability-sub {
    margin-top: 16rpx;

    .ability-sub-title {
      font-size: 26rpx;
      font-weight: 600;
      margin-bottom: 12rpx;
      display: block;

      &.good { color: $success; }
      &.warn { color: $warning; }
    }

    .ability-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 12rpx;
    }

    .ability-tag {
      padding: 6rpx 16rpx;
      border-radius: 20rpx;
      font-size: 22rpx;

      &.good {
        background: rgba($success, 0.1);
        color: $success;
      }

      &.warn {
        background: rgba($warning, 0.1);
        color: $warning;
      }
    }
  }
}

// 统计卡片
.stats-card {
  .stats-list {
    display: flex;
    flex-direction: column;
    gap: 24rpx;
  }

  .stat-item {
    display: flex;
    align-items: center;
    gap: 16rpx;

    .stat-icon {
      width: 64rpx;
      height: 64rpx;
      border-radius: 16rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28rpx;

      &.good {
        background: rgba($success, 0.1);
        color: $success;
      }

      &.warn {
        background: rgba($warning, 0.1);
        color: $warning;
      }

      &.info {
        background: rgba($primary, 0.1);
        color: $primary;
      }
    }

    .stat-info {
      display: flex;
      flex-direction: column;
    }

    .stat-label {
      font-size: 24rpx;
      color: $text-secondary;
    }

    .stat-value {
      font-size: 32rpx;
      font-weight: 700;
      color: $text-primary;
    }
  }
}

// 评价内容
.evaluation-content {
  .eval-section {
    margin-bottom: 24rpx;

    &:last-child {
      margin-bottom: 0;
    }

    .eval-subheader {
      display: flex;
      align-items: center;
      gap: 8rpx;
      margin-bottom: 12rpx;
    }

    .eval-subicon {
      font-size: 24rpx;
    }

    .eval-subtitle {
      font-size: 28rpx;
      font-weight: 500;
      color: $text-primary;
    }

    .eval-text {
      font-size: 28rpx;
      color: $text-regular;
      line-height: 1.8;
      white-space: pre-wrap;
    }
  }
}

// 优势/改进建议列表
.list-content {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.list-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;

  .bullet {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    margin-top: 8rpx;
    flex-shrink: 0;

    &.good { background: $success; }
    &.warn { background: $warning; }
  }

  .item-text {
    font-size: 28rpx;
    color: $text-regular;
    line-height: 1.6;
    flex: 1;
  }
}

// 问答列表
.questions-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.question-item {
  border: 1rpx solid #eee;
  border-radius: 16rpx;
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
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
  }

  .question-category {
    font-size: 22rpx;
    color: $primary;
    background: rgba($primary, 0.1);
    padding: 4rpx 12rpx;
    border-radius: 8rpx;
  }

  .question-perspective {
    font-size: 22rpx;
    color: $primary;
    background: rgba($primary, 0.15);
    padding: 4rpx 12rpx;
    border-radius: 8rpx;
    font-weight: 600;
  }

  .question-difficulty {
    font-size: 22rpx;
    padding: 4rpx 12rpx;
    border-radius: 8rpx;

    &.diff-basic, &.diff-easy {
      color: #059669;
      background: #d1fae5;
    }

    &.diff-medium, &.diff-intermediate {
      color: #2563eb;
      background: #dbeafe;
    }

    &.diff-advanced, &.diff-hard, &.diff-expert {
      color: #d97706;
      background: #fef3c7;
    }
  }

  .question-score {
    font-size: 28rpx;
    font-weight: 600;
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
    font-size: 28rpx;
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
    background: rgba($success, 0.1);
    padding: 16rpx;
    border-radius: 12rpx;
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
    color: $warning;
    background: rgba($warning, 0.1);
    padding: 6rpx 16rpx;
    border-radius: 20rpx;
    border: 1rpx solid rgba($warning, 0.2);
  }
}

// 视角详情页
.perspective-score-card {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 24rpx;
  padding: 40rpx;
  margin-bottom: 24rpx;

  .perspective-header-info {
    display: flex;
    align-items: center;
    margin-bottom: 30rpx;

    .perspective-icon-large {
      font-size: 72rpx;
      margin-right: 24rpx;
    }

    .perspective-info {
      flex: 1;

      .perspective-name {
        font-size: 40rpx;
        font-weight: 700;
        color: #fff;
        display: block;
        margin-bottom: 8rpx;
      }

      .perspective-meta {
        display: flex;
        align-items: center;
        gap: 8rpx;

        .meta-item {
          font-size: 24rpx;
          color: rgba(255, 255, 255, 0.8);
        }

        .meta-sep {
          color: rgba(255, 255, 255, 0.5);
        }
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
    white-space: pre-wrap;
  }
}

// 加载中
.loading-perspective {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 0;
}

// 空状态
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 0;
  color: $text-secondary;
  font-size: 28rpx;
}

// Tab content
.tab-content {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
