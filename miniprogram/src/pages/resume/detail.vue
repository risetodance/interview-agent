<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getResumeDetail,
  reanalyzeResume,
  downloadResume,
  type ResumeDetail
} from '../../api/resume'

// 简历详情数据
const resumeDetail = ref<ResumeDetail | null>(null)
const loading = ref(false)
const analyzing = ref(false)
const polling = ref(false) // 正在轮询等待分析完成

// 从 URL 获取简历 ID
const resumeId = ref<number>(0)

// 页面加载时获取 ID 并加载数据
onMounted(() => {
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
    }
  } catch (error) {
    console.error('加载简历详情失败:', error)
    uni.showToast({
      title: '加载失败',
      icon: 'none'
    })
  } finally {
    loading.value = false
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
          console.error('重新分析失败:', error)
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
    await new Promise(resolve => setTimeout(resolve, intervalMs))

    try {
      // 获取最新状态
      const result = await getResumeDetail(resumeId.value)
      resumeDetail.value = result

      const parseStatus = result.parseStatus
      console.log('轮询检测状态:', parseStatus, '尝试次数:', attempt)

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
      console.error('检查分析状态失败:', error)
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

    // 检测运行环境
    const isH5 = typeof window !== 'undefined'

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
            console.error('打开文件失败:', err)
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
    console.error('下载简历失败:', error)
    uni.showToast({
      title: '导出失败',
      icon: 'none'
    })
  }
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

// 格式化分析项目（字符串或对象）
const formatAnalysisItem = (item: any): string => {
  if (!item) return ''
  if (typeof item === 'string') return item
  if (typeof item === 'object') {
    return item.recommendation || item.issue || JSON.stringify(item)
  }
  return String(item)
}

// 返回上一页
const goBack = () => {
  uni.navigateBack()
}
</script>

<template>
  <view class="resume-detail-container">
    <!-- 顶部导航 -->
    <view class="nav-bar">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">简历详情</text>
      <text class="action-btn" @click="handleShare">分享</text>
    </view>

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
      <!-- 基本信息 -->
      <view class="section basic-info">
        <view class="info-header">
          <view class="avatar">
            <image
              v-if="resumeDetail.basicInfo?.avatar"
              :src="resumeDetail.basicInfo.avatar"
              mode="aspectFill"
            />
            <text v-else class="avatar-placeholder">
              {{ resumeDetail.name?.charAt(0) || '简历' }}
            </text>
          </view>
          <view class="info-content">
            <text class="name">{{ resumeDetail.basicInfo?.name || resumeDetail.name }}</text>
            <view class="meta">
              <text v-if="resumeDetail.basicInfo?.gender" class="meta-item">
                {{ resumeDetail.basicInfo.gender }}
              </text>
              <text v-if="resumeDetail.basicInfo?.age" class="meta-item">
                {{ resumeDetail.basicInfo.age }}岁
              </text>
              <text v-if="resumeDetail.basicInfo?.location" class="meta-item">
                {{ resumeDetail.basicInfo.location }}
              </text>
            </view>
          </view>
        </view>

        <view v-if="resumeDetail.basicInfo?.phone || resumeDetail.basicInfo?.email" class="contact-info">
          <text v-if="resumeDetail.basicInfo?.phone" class="contact-item">
            <text class="iconfont">&#xe615;</text>
            {{ resumeDetail.basicInfo.phone }}
          </text>
          <text v-if="resumeDetail.basicInfo?.email" class="contact-item">
            <text class="iconfont">&#xe613;</text>
            {{ resumeDetail.basicInfo.email }}
          </text>
        </view>

        <view v-if="resumeDetail.basicInfo?.summary" class="summary">
          <text class="summary-text">{{ resumeDetail.basicInfo.summary }}</text>
        </view>
      </view>

      <!-- 分析结果 -->
      <view v-if="resumeDetail.analysis" class="section analysis">
        <view class="section-title">AI 分析结果</view>

        <!-- 综合评分 -->
        <view v-if="resumeDetail.analysis.overallScore" class="score-card">
          <view class="score-circle">
            <text class="score-value">{{ resumeDetail.analysis.overallScore }}</text>
            <text class="score-label">综合评分</text>
          </view>
        </view>

        <!-- 技能匹配度 -->
        <view v-if="resumeDetail.analysis.skillMatchRate" class="match-rate">
          <text class="match-label">技能匹配度</text>
          <view class="match-bar">
            <view
              class="match-inner"
              :style="{ width: resumeDetail.analysis.skillMatchRate + '%' }"
            ></view>
          </view>
          <text class="match-value">{{ resumeDetail.analysis.skillMatchRate }}%</text>
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

        <!-- 优势 -->
        <view v-if="resumeDetail.analysis.strengths?.length" class="strengths">
          <text class="item-label">优势</text>
          <view class="list">
            <view v-for="(item, index) in resumeDetail.analysis.strengths" :key="index" class="list-item">
              <text class="iconfont plus">&#xe617;</text>
              <text class="text">{{ item }}</text>
            </view>
          </view>
        </view>

        <!-- 待改进 -->
        <view v-if="resumeDetail.analysis.improvements?.length" class="improvements">
          <text class="item-label">待改进</text>
          <view class="list">
            <view v-for="(item, index) in resumeDetail.analysis.improvements" :key="index" class="list-item">
              <text class="iconfont minus">&#xe616;</text>
              <text class="text">{{ formatAnalysisItem(item) }}</text>
            </view>
          </view>
        </view>

        <!-- 建议 -->
        <view v-if="resumeDetail.analysis.suggestions?.length" class="suggestions">
          <text class="item-label">建议</text>
          <view class="list">
            <view v-for="(item, index) in resumeDetail.analysis.suggestions" :key="index" class="list-item suggestion">
              <text class="iconfont">&#xe618;</text>
              <text class="text">{{ formatAnalysisItem(item) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 教育经历 -->
      <view v-if="resumeDetail.educationList?.length" class="section">
        <view class="section-title">教育经历</view>
        <view v-for="edu in resumeDetail.educationList" :key="edu.id || edu.school" class="timeline-item">
          <view class="timeline-dot"></view>
          <view class="timeline-content">
            <text class="school">{{ edu.school }}</text>
            <text class="degree">{{ edu.degree }} · {{ edu.major }}</text>
            <text class="time">{{ edu.startDate }} - {{ edu.endDate || '至今' }}</text>
            <text v-if="edu.description" class="description">{{ edu.description }}</text>
          </view>
        </view>
      </view>

      <!-- 工作经历 -->
      <view v-if="resumeDetail.workExperienceList?.length" class="section">
        <view class="section-title">工作经历</view>
        <view v-for="work in resumeDetail.workExperienceList" :key="work.id || work.company" class="timeline-item">
          <view class="timeline-dot"></view>
          <view class="timeline-content">
            <view class="content-header">
              <text class="company">{{ work.company }}</text>
              <text class="position">{{ work.position }}</text>
            </view>
            <text class="time">{{ work.startDate }} - {{ work.endDate || '至今' }}</text>
            <text v-if="work.description" class="description">{{ work.description }}</text>
          </view>
        </view>
      </view>

      <!-- 项目经验 -->
      <view v-if="resumeDetail.projectList?.length" class="section">
        <view class="section-title">项目经验</view>
        <view v-for="project in resumeDetail.projectList" :key="project.id || project.name" class="project-item">
          <view class="project-header">
            <text class="project-name">{{ project.name }}</text>
            <text v-if="project.role" class="project-role">{{ project.role }}</text>
          </view>
          <text v-if="project.startDate" class="project-time">
            {{ project.startDate }} - {{ project.endDate || '至今' }}
          </text>
          <text v-if="project.description" class="description">{{ project.description }}</text>
          <view v-if="project.technologies?.length" class="tech-list">
            <text v-for="tech in project.technologies" :key="tech" class="tech-tag">{{ tech }}</text>
          </view>
        </view>
      </view>

      <!-- 技能 -->
      <view v-if="resumeDetail.skills?.length" class="section">
        <view class="section-title">专业技能</view>
        <view class="skills-list">
          <view v-for="skill in resumeDetail.skills" :key="skill.name" class="skill-item">
            <text class="skill-name">{{ skill.name }}</text>
            <text class="skill-level">{{ skill.level }}</text>
          </view>
        </view>
      </view>

      <!-- 证书 -->
      <view v-if="resumeDetail.certificates?.length" class="section">
        <view class="section-title">证书</view>
        <view v-for="cert in resumeDetail.certificates" :key="cert.name" class="cert-item">
          <text class="cert-name">{{ cert.name }}</text>
          <text class="cert-issuer">{{ cert.issuer }}</text>
          <text v-if="cert.date" class="cert-date">{{ cert.date }}</text>
        </view>
      </view>

      <!-- 底部占位 -->
      <view class="bottom-placeholder"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view v-if="resumeDetail" class="action-bar">
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
    color: #6366f1;
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
  border-top-color: #6366f1;
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
  border-left: 6rpx solid #6366f1;
}

// 基本信息
.basic-info {
  .info-header {
    display: flex;
    align-items: center;
    margin-bottom: 24rpx;
  }

  .avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    overflow: hidden;
    margin-right: 24rpx;
    background-color: #eef2ff;

    image {
      width: 100%;
      height: 100%;
    }

    .avatar-placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      font-size: 48rpx;
      color: #6366f1;
      font-weight: bold;
    }
  }

  .info-content {
    flex: 1;
  }

  .name {
    font-size: 36rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 12rpx;
  }

  .meta {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;

    .meta-item {
      font-size: 26rpx;
      color: #666;

      &::before {
        content: '|';
        margin-right: 16rpx;
        color: #ddd;
      }

      &:first-child::before {
        display: none;
      }
    }
  }

  .contact-info {
    display: flex;
    flex-wrap: wrap;
    gap: 24rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #f5f5f5;
    margin-bottom: 20rpx;

    .contact-item {
      display: flex;
      align-items: center;
      gap: 8rpx;
      font-size: 26rpx;
      color: #666;

      .iconfont {
        font-size: 28rpx;
        color: #999;
      }
    }
  }

  .summary {
    padding: 20rpx;
    background-color: #f8fbf8;
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
  .score-card {
    display: flex;
    justify-content: center;
    padding: 20rpx 0;
  }

  .score-circle {
    width: 200rpx;
    height: 200rpx;
    border-radius: 50%;
    background: linear-gradient(135deg, #6366f1, #45a049);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    .score-value {
      font-size: 56rpx;
      font-weight: bold;
      color: #fff;
    }

    .score-label {
      font-size: 24rpx;
      color: rgba(255, 255, 255, 0.8);
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
      background: linear-gradient(90deg, #6366f1, #45a049);
      border-radius: 8rpx;
    }

    .match-value {
      font-size: 28rpx;
      color: #6366f1;
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
      color: #6366f1;
      border-radius: 20rpx;
      font-size: 24rpx;
    }
  }

  .strengths,
  .improvements,
  .suggestions {
    margin-bottom: 24rpx;
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 12rpx;
  }

  .list-item {
    display: flex;
    align-items: flex-start;
    gap: 12rpx;
    font-size: 28rpx;
    color: #666;
    line-height: 1.5;

    .iconfont {
      font-size: 28rpx;
      flex-shrink: 0;

      &.plus {
        color: #6366f1;
      }

      &.minus {
        color: #f56c6c;
      }
    }

    &.suggestion .iconfont {
      color: #409eff;
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
    background-color: #6366f1;
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
    color: #6366f1;
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
