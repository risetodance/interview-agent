<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { getInterviewDetail, getAbilityProfile, type AbilityProfile } from '../../api/interview'

// 路由参数
const pageId = ref<string>('')

// 面试详情数据
const interview = ref<any>(null)
const abilityProfile = ref<AbilityProfile | null>(null)
const loading = ref(true)
const abilityLoading = ref(true)
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
  abilityLoading.value = true
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

    // 加载能力画像数据
    try {
      const profileRes = await getAbilityProfile(pageId.value)
      console.log('[Report] profileRes:', profileRes)
      console.log('[Report] categoryScores:', profileRes?.categoryScores)
      console.log('[Report] categoryScores keys:', profileRes?.categoryScores ? Object.keys(profileRes.categoryScores) : 'null')
      abilityProfile.value = profileRes
      // 绘制雷达图
      await nextTick()
      drawRadarChart()
    } catch (e) {
      console.error('获取能力画像失败:', e)
    } finally {
      abilityLoading.value = false
    }
  } catch (error) {
    console.error('获取面试详情失败:', error)
  } finally {
    loading.value = false
  }
}

// 绘制雷达图
const drawRadarChart = () => {
  console.log('[Report] drawRadarChart called')
  console.log('[Report] abilityProfile:', abilityProfile.value)

  if (!abilityProfile.value || !abilityProfile.value.categoryScores) {
    console.log('[Report] 雷达图跳过: abilityProfile为空或categoryScores为空')
    return
  }

  const categoryScores = abilityProfile.value.categoryScores
  const categories = Object.keys(categoryScores)
  console.log('[Report] categories:', categories)

  if (categories.length === 0) {
    console.log('[Report] 雷达图跳过: categories为空')
    return
  }

  // categoryScores 是 Map 格式
  const data = categories.map(key => ({
    name: key,
    score: categoryScores[key].avgScore || 0
  }))

  console.log('[Report] 开始绘制雷达图')

  // uni-app H5: 直接使用原生 HTML5 Canvas API
  // 尝试多种方式获取 canvas 元素
  let canvas = document.querySelector('#radarCanvas canvas') as HTMLCanvasElement
  if (!canvas) {
    canvas = document.getElementById('radarCanvas') as HTMLCanvasElement
  }
  if (!canvas) {
    console.error('[Report] Canvas元素获取失败')
    // 降级到 UNI-API
    const ctx = uni.createCanvasContext('radarChart')
    if (!ctx) {
      console.error('[Report] Canvas上下文创建失败')
      return
    }
    setTimeout(() => {
      drawRadarChartWithCtx(ctx, data)
      ctx.draw(true)
    }, 100)
    return
  }
  console.log('[Report] Canvas元素获取成功:', canvas)

  const ctx2d = canvas.getContext('2d')
  if (!ctx2d) {
    console.error('[Report] Canvas 2D上下文获取失败')
    return
  }
  console.log('[Report] Canvas 2D上下文创建成功')

  // 使用原生 Canvas API 绘制
  setTimeout(() => {
    drawRadarChartWithCtx(ctx2d, data)
  }, 100)
}

function drawRadarChartWithCtx(ctx: any, data: any[]) {
  // 画布参数
  const canvasWidth = 320
  const canvasHeight = 280
  const centerX = canvasWidth / 2
  const centerY = canvasHeight / 2 + 10
  const radius = 85
  const angleStep = (2 * Math.PI) / data.length
  const startAngle = -Math.PI / 2

  // 绘制背景网格 (使用原生 Canvas API)
  ctx.strokeStyle = '#e5e7eb'
  ctx.lineWidth = 1

  // 绘制3个同心圆
  for (let i = 1; i <= 3; i++) {
    const r = (radius / 3) * i
    ctx.beginPath()
    for (let j = 0; j <= data.length; j++) {
      const angle = startAngle + angleStep * (j % data.length)
      const x = centerX + r * Math.cos(angle)
      const y = centerY + r * Math.sin(angle)
      if (j === 0) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
    }
    ctx.closePath()
    ctx.stroke()
  }

  // 绘制轴线
  for (let i = 0; i < data.length; i++) {
    const angle = startAngle + angleStep * i
    ctx.beginPath()
    ctx.moveTo(centerX, centerY)
    ctx.lineTo(
      centerX + radius * Math.cos(angle),
      centerY + radius * Math.sin(angle)
    )
    ctx.stroke()
  }

  // 绘制数据区域 (使用原生 Canvas API)
  ctx.beginPath()
  ctx.fillStyle = 'rgba(139, 92, 246, 0.3)'
  ctx.strokeStyle = '#8b5cf6'
  ctx.lineWidth = 2

  for (let i = 0; i <= data.length; i++) {
    const idx = i % data.length
    const angle = startAngle + angleStep * idx
    const score = data[idx].score / 100
    const r = radius * score
    const x = centerX + r * Math.cos(angle)
    const y = centerY + r * Math.sin(angle)

    if (i === 0) {
      ctx.moveTo(x, y)
    } else {
      ctx.lineTo(x, y)
    }
  }
  ctx.closePath()
  ctx.fill()
  ctx.stroke()

  // 绘制数据点
  for (let i = 0; i < data.length; i++) {
    const angle = startAngle + angleStep * i
    const score = data[i].score / 100
    const r = radius * score
    const x = centerX + r * Math.cos(angle)
    const y = centerY + r * Math.sin(angle)

    ctx.beginPath()
    ctx.fillStyle = '#8b5cf6'
    ctx.arc(x, y, 5, 0, 2 * Math.PI)
    ctx.fill()
  }

  // 绘制标签 (使用原生 Canvas API) - 显示完整标签，放在雷达图外侧
  ctx.fillStyle = '#64748b'
  ctx.font = '12px sans-serif'

  for (let i = 0; i < data.length; i++) {
    const angle = startAngle + angleStep * i
    const labelRadius = radius + 38
    const x = centerX + labelRadius * Math.cos(angle)
    const y = centerY + labelRadius * Math.sin(angle)

    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(data[i].name, x, y)
  }

  // 绘制分数 - 放在数据点往外偏移15px的位置，避免重叠
  for (let i = 0; i < data.length; i++) {
    const angle = startAngle + angleStep * i
    const score = data[i].score / 100
    const scoreRadius = radius * score + 15
    const x = centerX + scoreRadius * Math.cos(angle)
    const y = centerY + scoreRadius * Math.sin(angle)

    ctx.fillStyle = '#8b5cf6'
    ctx.font = 'bold 11px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(String(Math.round(data[i].score)), x, y)
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

      <!-- 能力画像雷达图 -->
      <view v-if="abilityProfile && !abilityLoading" class="section-card ability-profile">
        <view class="section-header">
          <text class="section-icon">&#xe605;</text>
          <text class="section-title">能力画像</text>
        </view>
        <view class="radar-container">
          <canvas
            id="radarCanvas"
            canvas-id="radarChart"
            class="radar-canvas"
            style="width: 320px; height: 280px;"
          ></canvas>
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

.ability-profile {
  .section-icon {
    color: $primary;
    font-weight: 600;
  }

  .radar-container {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 20rpx 0;
  }

  .radar-canvas {
    width: 320px;
    height: 280px;
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
