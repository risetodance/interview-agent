<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import {
  getResumeDetail,
  reanalyzeResume,
  downloadResume,
  reuploadResume,
  type ResumeDetail,
  type ResumeSuggestion
} from '../../api/resume'
import { isH5 } from '../../utils/env'

// 简历详情数据
const resumeDetail = ref<ResumeDetail | null>(null)
const loading = ref(false)
const analyzing = ref(false)
const polling = ref(false) // 正在轮询等待分析完成

// 按优先级分类建议
const suggestionsByPriority = computed(() => {
  if (!resumeDetail.value?.analysis?.suggestions) return { high: [], medium: [], low: [] }

  const suggestions = resumeDetail.value.analysis.suggestions
  return {
    high: suggestions.filter((s: ResumeSuggestion) => s.priority === '高'),
    medium: suggestions.filter((s: ResumeSuggestion) => s.priority === '中'),
    low: suggestions.filter((s: ResumeSuggestion) => s.priority === '低')
  }
})

// 获取优先级颜色
const getPriorityColor = (priority: string) => {
  switch (priority) {
    case '高':
      return { bg: 'bg-red-50', border: 'border-red-200', text: 'text-red-700' }
    case '中':
      return { bg: 'bg-amber-50', border: 'border-amber-200', text: 'text-amber-700' }
    case '低':
      return { bg: 'bg-blue-50', border: 'border-blue-200', text: 'text-blue-700' }
    default:
      return { bg: 'bg-slate-50', border: 'border-slate-200', text: 'text-slate-700' }
  }
}

// 获取优先级徽章颜色
const getPriorityBadgeColor = (priority: string) => {
  switch (priority) {
    case '高':
      return 'bg-red-500 text-white'
    case '中':
      return 'bg-amber-500 text-white'
    case '低':
      return 'bg-blue-500 text-white'
    default:
      return 'bg-slate-500 text-white'
  }
}

// 获取类别颜色样式
const getCategoryStyle = (category: string) => {
  const colors: Record<string, { bg: string; text: string }> = {
    '项目': { bg: '#f3e8ff', text: '#7c3aed' },    // purple-100, purple-700
    '技能': { bg: '#e0f2fe', text: '#0284c7' },    // sky-100, sky-700
    '内容': { bg: '#d1fae5', text: '#059669' },    // emerald-100, emerald-700
    '格式': { bg: '#fce7f3', text: '#db2777' },    // pink-100, pink-700
    '结构': { bg: '#cffafe', text: '#0891b2' },    // cyan-100, cyan-700
    '表达': { bg: '#ffedd5', text: '#ea580c' }     // orange-100, orange-700
  }
  return colors[category] || { bg: '#f1f5f9', text: '#475569' } // slate-100, slate-700
}

// 获取分组标题颜色
const getGroupTitleColor = (priority: string) => {
  switch (priority) {
    case '高':
      return { bg: 'bg-red-100', text: 'text-red-700' }
    case '中':
      return { bg: 'bg-amber-100', text: 'text-amber-700' }
    case '低':
      return { bg: 'bg-blue-100', text: 'text-blue-700' }
    default:
      return { bg: 'bg-slate-100', text: 'text-slate-700' }
  }
}

// 页面是否处于活动状态
let pageActive = true

// 页面卸载时停止轮询
onBeforeUnmount(() => {
  pageActive = false
})

// 从 URL 获取简历 ID
const resumeId = ref<number>(0)

// 页面加载时获取 ID 并加载数据
onMounted(() => {
  pageActive = true
  // 从页面参数获取简历 ID
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  const options = currentPage?.options || {}

  if (options.id) {
    resumeId.value = Number(options.id)
    loadResumeDetail()
  }
})

// 加载简历详情
const loadResumeDetail = async () => {
  if (!resumeId.value) return

  loading.value = true
  try {
    const result = await getResumeDetail(resumeId.value)
    resumeDetail.value = result

    // 如果状态是 PENDING 或 PROCESSING，自动开始轮询
    if (result.parseStatus === 'PENDING' || result.parseStatus === 'PROCESSING') {
      polling.value = true
      pollAnalysisStatus()
    } else if (result.analysis) {
      // 绘制雷达图
      await nextTick()
      setTimeout(() => drawRadarChart(), 100)
    }
  } catch (error) {
    uni.showToast({
      title: '加载失败',
      icon: 'none'
    })
  } finally {
    loading.value = false
  }
}

// 绘制雷达图
const drawRadarChart = () => {
  if (!resumeDetail.value?.analysis) {
    return
  }

  const analysis = resumeDetail.value.analysis

  // 雷达图数据 - 5个维度（包含满分用于归一化）
  const data = [
    { name: '表达专业性', score: analysis.expressionScore || 0, fullMark: 10 },
    { name: '技能匹配', score: analysis.skillMatchScore || 0, fullMark: 20 },
    { name: '内容完整性', score: analysis.contentScore || 0, fullMark: 15 },
    { name: '结构清晰度', score: analysis.structureScore || 0, fullMark: 15 },
    { name: '项目经验', score: analysis.projectScore || 0, fullMark: 40 }
  ]

  // 检查是否有有效数据
  if (data.every(item => item.score === 0)) {
    return
  }

  // 使用原生 HTML5 Canvas API
  let canvas = document.querySelector('#resumeRadarCanvas canvas') as HTMLCanvasElement
  if (!canvas) {
    canvas = document.getElementById('resumeRadarCanvas') as HTMLCanvasElement
  }
  if (!canvas) {
    return
  }

  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return
  }

  drawResumeRadarChart(ctx, data)
}

function drawResumeRadarChart(ctx: any, data: any[]) {
  const canvasWidth = 320
  const canvasHeight = 280
  const centerX = canvasWidth / 2
  const centerY = canvasHeight / 2 + 10
  const radius = 85
  const angleStep = (2 * Math.PI) / data.length
  const startAngle = -Math.PI / 2

  // 绘制背景网格
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
    ctx.lineTo(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle))
    ctx.stroke()
  }

  // 绘制数据区域
  ctx.beginPath()
  ctx.fillStyle = 'rgba(139, 92, 246, 0.3)'
  ctx.strokeStyle = '#8b5cf6'
  ctx.lineWidth = 2

  for (let i = 0; i <= data.length; i++) {
    const idx = i % data.length
    const angle = startAngle + angleStep * idx
    // 使用 fullMark 归一化：score / fullMark * 100
    const normalizedScore = (data[idx].score / data[idx].fullMark) * 100
    const r = radius * (normalizedScore / 100)
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
    const normalizedScore = (data[i].score / data[i].fullMark) * 100
    const r = radius * (normalizedScore / 100)
    const x = centerX + r * Math.cos(angle)
    const y = centerY + r * Math.sin(angle)

    ctx.beginPath()
    ctx.fillStyle = '#8b5cf6'
    ctx.arc(x, y, 5, 0, 2 * Math.PI)
    ctx.fill()
  }

  // 绘制标签
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

  // 绘制分数
  for (let i = 0; i < data.length; i++) {
    const angle = startAngle + angleStep * i
    const normalizedScore = (data[i].score / data[i].fullMark) * 100
    const scoreRadius = radius * (normalizedScore / 100) + 15
    const x = centerX + scoreRadius * Math.cos(angle)
    const y = centerY + scoreRadius * Math.sin(angle)

    ctx.fillStyle = '#8b5cf6'
    ctx.font = 'bold 11px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(String(Math.round(data[i].score)), x, y)
  }
}

// 重新分析简历
const handleReanalyze = async () => {
  if (analyzing.value || polling.value) return

  uni.showModal({
    title: '确认重新分析',
    content: '确定要重新分析这份简历吗？',
    success: async (res) => {
      if (res.confirm) {
        analyzing.value = true
        polling.value = true
        try {
          await reanalyzeResume(resumeId.value)

          // 轮询等待分析完成
          await pollAnalysisStatus()
        } catch (error) {
          uni.showToast({
            title: '分析失败',
            icon: 'none'
          })
        } finally {
          analyzing.value = false
          polling.value = false
        }
      }
    }
  })
}

// 轮询检查分析状态
const pollAnalysisStatus = async () => {
  const maxAttempts = 30 // 最多等待30次
  const intervalMs = 2000 // 每次间隔2秒

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (!pageActive) {
      polling.value = false
      return
    }

    await new Promise(resolve => setTimeout(resolve, intervalMs))

    if (!pageActive) {
      polling.value = false
      return
    }

    try {
      // 获取最新状态
      const result = await getResumeDetail(resumeId.value)
      resumeDetail.value = result

      const parseStatus = result.parseStatus

      // COMPLETED 或 FAILED 表示分析完成
      if (parseStatus === 'COMPLETED' || parseStatus === 'FAILED') {
        polling.value = false // 停止轮询
        uni.showToast({
          title: parseStatus === 'COMPLETED' ? '分析完成' : '分析失败',
          icon: parseStatus === 'COMPLETED' ? 'success' : 'none'
        })
        return
      }

      // 分析中，显示进度
      if (attempt % 3 === 0) { // 每6秒显示一次
        uni.showToast({
          title: '分析中...',
          icon: 'none'
        })
      }
    } catch (error) {
    }
  }

  // 超时
  polling.value = false
  uni.showToast({
    title: '分析超时，请稍后刷新',
    icon: 'none'
  })
}

// 下载简历
const handleDownload = async () => {
  if (!resumeDetail.value) return

  uni.showLoading({
    title: '下载中...',
    mask: true
  })

  try {
    const result = await downloadResume(resumeId.value)

    if (isH5) {
      // H5: 直接下载，浏览器会处理
      uni.hideLoading()
      uni.showToast({
        title: '开始下载',
        icon: 'success'
      })
    } else {
      // 小程序: 打开PDF文件
      if (result.tempFilePath) {
        uni.openDocument({
          filePath: result.tempFilePath,
          fileType: 'pdf',
          success: () => {
            uni.hideLoading()
            uni.showToast({
              title: '打开成功',
              icon: 'success'
            })
          },
          fail: (err) => {
            uni.hideLoading()
            uni.showToast({
              title: '打开文件失败',
              icon: 'none'
            })
          }
        })
      } else {
        uni.hideLoading()
        uni.showToast({
          title: '下载失败',
          icon: 'none'
        })
      }
    }
  } catch (error) {
    uni.hideLoading()
    uni.showToast({
      title: '导出失败',
      icon: 'none'
    })
  }
}

// 重新上传简历
const handleReupload = () => {
  if (!resumeDetail.value) return

  // #ifdef MP-WEIXIN
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['pdf', 'doc', 'docx'],
    success: async (res) => {
      const file = res.tempFiles[0]
      uni.showLoading({
        title: '上传中...',
        mask: true
      })
      try {
        await reuploadResume(resumeId.value, file.path)
        uni.hideLoading()
        uni.showToast({
          title: '上传成功，正在解析',
          icon: 'success',
          duration: 1500
        })
        // 刷新详情
        loadResumeDetail()
        // 触发列表刷新
        setTimeout(() => {
          uni.$emit('resume-list-refresh')
        }, 500)
      } catch (error: any) {
        uni.hideLoading()
        uni.showToast({
          title: error.message || '上传失败',
          icon: 'none'
        })
      }
    },
    fail: (error) => {
    }
  })
  // #endif

  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.doc,.docx'
  input.onchange = async (e: any) => {
    const file = e.target.files?.[0]
    if (!file) return

    uni.showLoading({
      title: '上传中...',
      mask: true
    })

    try {
      // H5 需要将文件转换为路径
      const filePath = URL.createObjectURL(file)
      await reuploadResume(resumeId.value, filePath)
      uni.hideLoading()
      uni.showToast({
        title: '上传成功，正在解析',
        icon: 'success',
        duration: 1500
      })
      // 刷新详情
      loadResumeDetail()
      // 触发列表刷新
      setTimeout(() => {
        uni.$emit('resume-list-refresh')
      }, 500)
    } catch (error: any) {
      uni.hideLoading()
      uni.showToast({
        title: error.message || '上传失败',
        icon: 'none'
      })
    }
  }
  input.click()
  // #endif
}

// 分享简历
const handleShare = () => {
  if (!resumeDetail.value) return

  uni.showToast({
    title: '功能开发中',
    icon: 'none'
  })
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 格式化分析时间（格式：2026/03/21 01:43）
const formatAnalysisDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${year}/${month}/${day} ${hours}:${minutes}`
}

// 格式化分析项目（字符串或对象）
const formatAnalysisItem = (item: any): string => {
  if (!item) return ''
  if (typeof item === 'string') return item
  if (typeof item === 'object') {
    return item.recommendation || item.issue || JSON.stringify(item)
  }
  return String(item)
}
</script>

<template>
  <view class="resume-detail-container">
    <!-- 加载中 -->
    <view v-if="loading" class="loading">
      <text>加载中...</text>
    </view>

    <!-- 分析中遮罩层 -->
    <view v-if="polling" class="polling-overlay">
      <view class="polling-content">
        <view class="spinner"></view>
        <text class="polling-text">AI 正在分析中...</text>
        <text class="polling-subtext">请稍候</text>
      </view>
    </view>

    <!-- 简历内容 -->
    <scroll-view v-else-if="resumeDetail" class="content" scroll-y>
      <!-- 附件简历卡片 -->
      <view class="section attachment-card">
        <view class="attachment-header">
          <view class="attachment-icon">
            <text class="icon-text">附</text>
          </view>
          <text class="attachment-name">{{ resumeDetail.name }}</text>
        </view>
      </view>

      <!-- 分析结果 -->
      <view v-if="resumeDetail.analysis" class="section analysis">
        <view class="section-title">AI 分析结果</view>

        <!-- 核心评价模块（绿色渐变背景，全宽上下结构） -->
        <view class="core-evaluation-section">
          <view class="core-evaluation-bg">
            <!-- 核心评价文字 -->
            <text class="core-summary">{{ resumeDetail.analysis?.summary || '简历基础框架完整，候选人在技术领域有一定积累。' }}</text>

            <!-- 总分 + 分析时间 双列布局 -->
            <view class="core-stats-grid">
              <view class="stat-card">
                <text class="stat-label">总分</text>
                <view class="stat-score-row">
                  <text class="stat-score">{{ resumeDetail.analysis.overallScore || 0 }}</text>
                  <text class="stat-max">/ 100</text>
                </view>
              </view>
              <view class="stat-card">
                <text class="stat-label">分析时间</text>
                <text class="stat-time">{{ resumeDetail.analysis.analyzedAt ? formatAnalysisDate(resumeDetail.analysis.analyzedAt) : '-' }}</text>
              </view>
            </view>

            <!-- 优势亮点 -->
            <view v-if="resumeDetail.analysis.strengths?.length" class="strength-card">
              <text class="strength-label">优势亮点</text>
              <view class="tags-list">
                <text
                  v-for="(strength, idx) in resumeDetail.analysis.strengths"
                  :key="idx"
                  class="strength-tag"
                >
                  {{ strength }}
                </text>
              </view>
            </view>
          </view>
        </view>

        <!-- 能力画像雷达图 + 详细分数 -->
        <view v-if="resumeDetail.analysis.expressionScore || resumeDetail.analysis.skillMatchScore || resumeDetail.analysis.contentScore || resumeDetail.analysis.structureScore || resumeDetail.analysis.projectScore" class="radar-section">
          <view class="radar-container">
            <canvas
              id="resumeRadarCanvas"
              canvas-id="resumeRadarChart"
              class="radar-canvas"
              style="width: 320px; height: 280px;"
            ></canvas>
          </view>
          <!-- 详细分数列表 -->
          <view class="score-list">
            <view class="score-item">
              <text class="score-label">项目经验</text>
              <view class="score-row">
                <view class="score-bar">
                  <view class="score-bar-inner project" :style="{ width: ((resumeDetail.analysis.projectScore ?? 0) / 40 * 100) + '%' }"></view>
                </view>
                <text class="score-value">{{ resumeDetail.analysis.projectScore ?? 0 }}/40</text>
              </view>
            </view>
            <view class="score-item">
              <text class="score-label">技能匹配</text>
              <view class="score-row">
                <view class="score-bar">
                  <view class="score-bar-inner skill" :style="{ width: ((resumeDetail.analysis.skillMatchScore ?? 0) / 20 * 100) + '%' }"></view>
                </view>
                <text class="score-value">{{ resumeDetail.analysis.skillMatchScore ?? 0 }}/20</text>
              </view>
            </view>
            <view class="score-item">
              <text class="score-label">内容完整性</text>
              <view class="score-row">
                <view class="score-bar">
                  <view class="score-bar-inner content" :style="{ width: ((resumeDetail.analysis.contentScore ?? 0) / 15 * 100) + '%' }"></view>
                </view>
                <text class="score-value">{{ resumeDetail.analysis.contentScore ?? 0 }}/15</text>
              </view>
            </view>
            <view class="score-item">
              <text class="score-label">结构清晰度</text>
              <view class="score-row">
                <view class="score-bar">
                  <view class="score-bar-inner structure" :style="{ width: ((resumeDetail.analysis.structureScore ?? 0) / 15 * 100) + '%' }"></view>
                </view>
                <text class="score-value">{{ resumeDetail.analysis.structureScore ?? 0 }}/15</text>
              </view>
            </view>
            <view class="score-item">
              <text class="score-label">表达专业性</text>
              <view class="score-row">
                <view class="score-bar">
                  <view class="score-bar-inner expression" :style="{ width: ((resumeDetail.analysis.expressionScore ?? 0) / 10 * 100) + '%' }"></view>
                </view>
                <text class="score-value">{{ resumeDetail.analysis.expressionScore ?? 0 }}/10</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 匹配职位 -->
        <view v-if="resumeDetail.analysis.matchedPositions?.length" class="matched-positions">
          <text class="item-label">推荐职位</text>
          <view class="tag-list">
            <text
              v-for="position in resumeDetail.analysis.matchedPositions"
              :key="position"
              class="tag"
            >
              {{ position }}
            </text>
          </view>
        </view>

        <!-- 改进建议 - 按优先级分组 -->
        <view v-if="resumeDetail.analysis.suggestions?.length" class="suggestions-section">
          <view class="suggestions-header">
            <text class="suggestions-title">改进建议</text>
            <text class="suggestions-count">({{ resumeDetail.analysis.suggestions.length }} 条)</text>
          </view>

          <!-- 高优先级 -->
          <view v-if="suggestionsByPriority.high.length > 0" class="priority-group">
            <view class="priority-group-header">
              <text class="priority-badge high">高优先级 ({{ suggestionsByPriority.high.length }})</text>
              <view class="priority-divider high"></view>
            </view>
            <view class="suggestion-list">
              <view
                v-for="(item, idx) in suggestionsByPriority.high"
                :key="'high-' + idx"
                class="suggestion-card high"
              >
                <view class="suggestion-tags">
                  <text class="tag-priority high">{{ item.priority }}</text>
                  <text class="tag-category" :style="{ backgroundColor: getCategoryStyle(item.category || '其他').bg, color: getCategoryStyle(item.category || '其他').text }">{{ item.category || '其他' }}</text>
                </view>
                <view class="suggestion-content">
                  <text class="issue-text">{{ item.issue || '问题描述' }}</text>
                  <text class="recommendation-text">{{ item.recommendation || '暂无改进建议' }}</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 中优先级 -->
          <view v-if="suggestionsByPriority.medium.length > 0" class="priority-group">
            <view class="priority-group-header">
              <text class="priority-badge medium">中优先级 ({{ suggestionsByPriority.medium.length }})</text>
              <view class="priority-divider medium"></view>
            </view>
            <view class="suggestion-list">
              <view
                v-for="(item, idx) in suggestionsByPriority.medium"
                :key="'medium-' + idx"
                class="suggestion-card medium"
              >
                <view class="suggestion-tags">
                  <text class="tag-priority medium">{{ item.priority }}</text>
                  <text class="tag-category" :style="{ backgroundColor: getCategoryStyle(item.category || '其他').bg, color: getCategoryStyle(item.category || '其他').text }">{{ item.category || '其他' }}</text>
                </view>
                <view class="suggestion-content">
                  <text class="issue-text">{{ item.issue || '问题描述' }}</text>
                  <text class="recommendation-text">{{ item.recommendation || '暂无改进建议' }}</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 低优先级 -->
          <view v-if="suggestionsByPriority.low.length > 0" class="priority-group">
            <view class="priority-group-header">
              <text class="priority-badge low">低优先级 ({{ suggestionsByPriority.low.length }})</text>
              <view class="priority-divider low"></view>
            </view>
            <view class="suggestion-list">
              <view
                v-for="(item, idx) in suggestionsByPriority.low"
                :key="'low-' + idx"
                class="suggestion-card low"
              >
                <view class="suggestion-tags">
                  <text class="tag-priority low">{{ item.priority }}</text>
                  <text class="tag-category" :style="{ backgroundColor: getCategoryStyle(item.category || '其他').bg, color: getCategoryStyle(item.category || '其他').text }">{{ item.category || '其他' }}</text>
                </view>
                <view class="suggestion-content">
                  <text class="issue-text">{{ item.issue || '问题描述' }}</text>
                  <text class="recommendation-text">{{ item.recommendation || '暂无改进建议' }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 关联面试历史（B13：透传后端 ResumeDetailDTO.interviews，与 Web 端一致） -->
      <view v-if="resumeDetail.interviews?.length" class="section">
        <view class="section-title">关联面试</view>
        <view v-for="(iv, idx) in resumeDetail.interviews" :key="iv.sessionId || idx" class="interview-item">
          <view class="interview-info">
            <text class="interview-status">{{ iv.status || '进行中' }}</text>
            <text class="interview-progress">{{ iv.answeredCount ?? 0 }} / {{ iv.totalQuestions ?? 0 }} 题</text>
          </view>
          <text v-if="iv.overallScore !== undefined && iv.overallScore !== null" class="interview-score">{{ iv.overallScore }} 分</text>
        </view>
      </view>

      <!-- 底部占位 -->
      <view class="bottom-placeholder"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view v-if="resumeDetail" class="action-bar">
      <view class="action-item" @click="handleReupload">
        <text class="iconfont">&#xe60d;</text>
        <text>重新上传</text>
      </view>
      <view class="action-item" @click="handleReanalyze" :class="{ disabled: analyzing }">
        <text class="iconfont" :class="{ spinning: analyzing }">&#xe61a;</text>
        <text>{{ analyzing ? '分析中' : '重分析' }}</text>
      </view>
      <view class="action-item" @click="handleDownload">
        <text class="iconfont">&#xe61b;</text>
        <text>导出</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@use '../../styles/variables.scss' as *;

.resume-detail-container {
  min-height: 100vh;
  background-color: #f5f5f5;
  padding-bottom: 120rpx;
}

.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background-color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;

  .back-btn,
  .action-btn {
    font-size: 30rpx;
    color: #0ea5e9;
    width: 120rpx;
  }

  .action-btn {
    text-align: right;
  }

  .title {
    font-size: 34rpx;
    font-weight: 600;
    color: #333;
  }
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 400rpx;
  font-size: 28rpx;
  color: #999;
}

// 分析中遮罩层
.polling-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.polling-content {
  background-color: #fff;
  border-radius: 24rpx;
  padding: 60rpx 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.15);
}

.spinner {
  width: 80rpx;
  height: 80rpx;
  border: 6rpx solid #f0f0f0;
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.polling-text {
  font-size: 32rpx;
  color: #333;
  margin-top: 30rpx;
  font-weight: 500;
}

.polling-subtext {
  font-size: 26rpx;
  color: #999;
  margin-top: 12rpx;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.content {
  padding: 20rpx;
}

.section {
  background-color: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 24rpx;
  padding-left: 20rpx;
  border-left: 6rpx solid #0ea5e9;
}

// 附件简历卡片
.attachment-card {
  .attachment-header {
    display: flex;
    align-items: center;
    gap: 20rpx;
  }

  .attachment-icon {
    width: 80rpx;
    height: 80rpx;
    border-radius: 50%;
    background-color: #eef2ff;
    display: flex;
    align-items: center;
    justify-content: center;

    .icon-text {
      font-size: 32rpx;
      color: #0ea5e9;
      font-weight: 600;
    }
  }

  .attachment-name {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
  }

  .attachment-summary {
    margin-top: 20rpx;
    padding: 20rpx;
    background-color: #f5f5f5;
    border-radius: 12rpx;

    .summary-text {
      font-size: 28rpx;
      color: #666;
      line-height: 1.6;
    }
  }
}

// 分析结果
.analysis {
  // 核心评价模块（绿色渐变背景，全宽）
  .core-evaluation-section {
    margin-bottom: 24rpx;
  }

  .core-evaluation-bg {
    background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
    border-radius: 24rpx;
    padding: 32rpx;

    .core-summary {
      display: block;
      font-size: 28rpx;
      color: #1e293b;
      line-height: 1.6;
      margin-bottom: 24rpx;
    }

    // 总分 + 分析时间 双列布局
    .core-stats-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16rpx;
      margin-bottom: 20rpx;
    }

    // 总分卡片 - 白色背景
    .stat-card {
      background-color: #fff;
      border-radius: 20rpx;
      padding: 24rpx;

      .stat-label {
        display: block;
        font-size: 24rpx;
        font-weight: 600;
        color: #059669;
        margin-bottom: 12rpx;
      }

      .stat-score-row {
        display: flex;
        align-items: baseline;
        gap: 8rpx;
      }

      .stat-score {
        font-size: 56rpx;
        font-weight: 700;
        color: #1e293b;
        line-height: 1;
      }

      .stat-max {
        font-size: 24rpx;
        color: #94a3b8;
      }

      .stat-time {
        font-size: 24rpx;
        color: #475569;
      }
    }

    // 优势亮点卡片 - 白色背景
    .strength-card {
      background-color: #fff;
      border-radius: 20rpx;
      padding: 24rpx;

      .strength-label {
        display: block;
        font-size: 24rpx;
        font-weight: 600;
        color: #059669;
        margin-bottom: 12rpx;
      }

      .tags-list {
        display: flex;
        flex-wrap: wrap;
        gap: 12rpx;
      }

      .strength-tag {
        padding: 10rpx 20rpx;
        background-color: #d1fae5;
        color: #047857;
        border: 2rpx solid #a7f3d0;
        border-radius: 12rpx;
        font-size: 24rpx;
        font-weight: 500;
      }
    }
  }

  // 能力画像雷达图
  .radar-section {
    display: flex;
    flex-direction: column;
    gap: 16rpx;

    .radar-container {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 10rpx 0;
    }

    .radar-canvas {
      width: 320px;
      height: 280px;
    }

    .score-list {
      display: flex;
      flex-direction: column;
      gap: 12rpx;
    }

    .score-item {
      display: flex;
      flex-direction: column;
      gap: 4rpx;
    }

    .score-label {
      font-size: 22rpx;
      color: #64748b;
    }

    .score-row {
      display: flex;
      align-items: center;
      gap: 12rpx;
    }

    .score-bar {
      flex: 1;
      height: 16rpx;
      background-color: #e2e8f0;
      border-radius: 8rpx;
      overflow: hidden;
    }

    .score-bar-inner {
      height: 100%;
      border-radius: 8rpx;

      &.project {
        background-color: #a855f7;
      }

      &.skill {
        background-color: #3b82f6;
      }

      &.content {
        background-color: #10b981;
      }

      &.structure {
        background-color: #06b6d4;
      }

      &.expression {
        background-color: #f97316;
      }
    }

    .score-value {
      font-size: 24rpx;
      color: #334155;
      font-weight: 600;
      width: 80rpx;
      text-align: right;
      flex-shrink: 0;
    }
  }

  .match-rate {
    display: flex;
    align-items: center;
    gap: 20rpx;
    margin-bottom: 24rpx;

    .match-label {
      font-size: 28rpx;
      color: #333;
      min-width: 140rpx;
    }

    .match-bar {
      flex: 1;
      height: 16rpx;
      background-color: #f5f5f5;
      border-radius: 8rpx;
      overflow: hidden;
    }

    .match-inner {
      height: 100%;
      background: linear-gradient(90deg, #0ea5e9, #38bdf8);
      border-radius: 8rpx;
    }

    .match-value {
      font-size: 28rpx;
      color: #0ea5e9;
      font-weight: 500;
      min-width: 80rpx;
      text-align: right;
    }
  }

  .item-label {
    display: block;
    font-size: 28rpx;
    font-weight: 500;
    color: #333;
    margin-bottom: 16rpx;
  }

  .matched-positions {
    margin-bottom: 24rpx;

    .tag-list {
      display: flex;
      flex-wrap: wrap;
      gap: 12rpx;
    }

    .tag {
      padding: 8rpx 20rpx;
      background-color: #eef2ff;
      color: #0ea5e9;
      border-radius: 20rpx;
      font-size: 24rpx;
    }
  }

  // 改进建议模块
  .suggestions-section {
    margin-bottom: 24rpx;
  }

  .suggestions-header {
    display: flex;
    align-items: center;
    gap: 8rpx;
    margin-bottom: 24rpx;

    .suggestions-title {
      font-size: 28rpx;
      font-weight: 600;
      color: #333;
    }

    .suggestions-count {
      font-size: 24rpx;
      color: #94a3b8;
    }
  }

  // 优先级分组
  .priority-group {
    margin-bottom: 24rpx;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .priority-group-header {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-bottom: 16rpx;

    .priority-badge {
      padding: 8rpx 20rpx;
      border-radius: 999rpx;
      font-size: 24rpx;
      font-weight: 600;

      &.high {
        background-color: #fef2f2;
        color: #dc2626;
      }

      &.medium {
        background-color: #fffbeb;
        color: #d97706;
      }

      &.low {
        background-color: #eff6ff;
        color: #2563eb;
      }
    }

    .priority-divider {
      flex: 1;
      height: 2rpx;

      &.high {
        background-color: #fecaca;
      }

      &.medium {
        background-color: #fde68a;
      }

      &.low {
        background-color: #bfdbfe;
      }
    }
  }

  .suggestion-list {
    display: flex;
    flex-direction: column;
    gap: 16rpx;
  }

  .suggestion-card {
    background-color: #fff;
    border-radius: 20rpx;
    padding: 24rpx;

    &.high {
      border: 4rpx solid #fecaca;
      background-color: #fef2f2;
    }

    &.medium {
      border: 4rpx solid #fde68a;
      background-color: #fffbeb;
    }

    &.low {
      border: 4rpx solid #bfdbfe;
      background-color: #eff6ff;
    }

    .suggestion-tags {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-bottom: 12rpx;
    }

    .tag-priority {
      padding: 6rpx 16rpx;
      border-radius: 8rpx;
      font-size: 20rpx;
      font-weight: 600;

      &.high {
        background-color: #ef4444;
        color: #fff;
      }

      &.medium {
        background-color: #f59e0b;
        color: #fff;
      }

      &.low {
        background-color: #3b82f6;
        color: #fff;
      }
    }

    .tag-category {
      padding: 6rpx 16rpx;
      border-radius: 8rpx;
      font-size: 20rpx;
      font-weight: 500;
    }

    .suggestion-content {
      display: flex;
      flex-direction: column;
      gap: 8rpx;
    }

    .issue-text {
      font-size: 28rpx;
      font-weight: 600;
      color: #1e293b;
      line-height: 1.5;
    }

    .recommendation-text {
      font-size: 26rpx;
      color: #64748b;
      line-height: 1.6;
    }
  }
}

// 时间线样式
.timeline-item {
  position: relative;
  padding-left: 40rpx;
  padding-bottom: 30rpx;

  &:last-child {
    padding-bottom: 0;

    .timeline-dot {
      background-color: #fff;
    }
  }

  .timeline-dot {
    position: absolute;
    left: 0;
    top: 8rpx;
    width: 20rpx;
    height: 20rpx;
    border-radius: 50%;
    background-color: #0ea5e9;
    border: 4rpx solid #fff;
    box-shadow: 0 0 0 4rpx #eef2ff;
  }

  .timeline-content {
    .content-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8rpx;
    }

    .school,
    .company {
      font-size: 30rpx;
      font-weight: 500;
      color: #333;
    }

    .degree,
    .position {
      font-size: 26rpx;
      color: #666;
    }

    .time {
      display: block;
      font-size: 24rpx;
      color: #999;
      margin: 8rpx 0;
    }

    .description {
      display: block;
      font-size: 26rpx;
      color: #666;
      line-height: 1.6;
      margin-top: 8rpx;
    }
  }
}

// 项目经验
.project-item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  .project-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8rpx;
  }

  .project-name {
    font-size: 30rpx;
    font-weight: 500;
    color: #333;
  }

  .project-role {
    font-size: 26rpx;
    color: #666;
  }

  .project-time {
    display: block;
    font-size: 24rpx;
    color: #999;
    margin-bottom: 12rpx;
  }

  .description {
    display: block;
    font-size: 26rpx;
    color: #666;
    line-height: 1.6;
    margin-bottom: 12rpx;
  }

  .tech-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
  }

  .tech-tag {
    padding: 4rpx 12rpx;
    background-color: #f5f5f5;
    color: #666;
    border-radius: 8rpx;
    font-size: 22rpx;
  }
}

// 技能列表
.skills-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.skill-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 20rpx;
  background-color: #f8fbf8;
  border-radius: 12rpx;

  .skill-name {
    font-size: 28rpx;
    color: #333;
  }

  .skill-level {
    font-size: 24rpx;
    color: #0ea5e9;
    padding: 4rpx 12rpx;
    background-color: #eef2ff;
    border-radius: 8rpx;
  }
}

// 证书
.cert-item {
  display: flex;
  flex-direction: column;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  .cert-name {
    font-size: 28rpx;
    color: #333;
    margin-bottom: 8rpx;
  }

  .cert-issuer,
  .cert-date {
    font-size: 24rpx;
    color: #999;
  }
}

// 关联面试
.interview-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  .interview-info {
    display: flex;
    flex-direction: column;
    gap: 4rpx;
  }

  .interview-status {
    font-size: 28rpx;
    color: #333;
    font-weight: 500;
  }

  .interview-progress {
    font-size: 24rpx;
    color: #999;
  }

  .interview-score {
    font-size: 32rpx;
    font-weight: 700;
    color: #0ea5e9;
  }
}

.bottom-placeholder {
  height: 40rpx;
}

// 底部操作栏
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  background-color: #fff;
  padding: 20rpx 30rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);

  .action-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;

    .iconfont {
      font-size: 40rpx;
      color: #666;
    }

    text:last-child {
      font-size: 24rpx;
      color: #666;
    }

    &.disabled {
      opacity: 0.5;
    }

    .spinning {
      animation: spin 1s linear infinite;
    }
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
