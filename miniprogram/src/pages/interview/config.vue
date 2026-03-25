<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getResumeDetail, type Resume } from '../../api/resume'
import { createSession } from '../../api/interview'

// 题目数量选项
const questionCountOptions = [
  { value: 6, label: '6 题' },
  { value: 8, label: '8 题' },
  { value: 10, label: '10 题' },
  { value: 12, label: '12 题' },
  { value: 15, label: '15 题' }
]

// 题目分布说明
const distributionText = '题目分布：项目经历(20%) + MySQL(20%) + Redis(20%) + Java基础/集合/并发(30%) + Spring(10%)'

// 简历数据
const resumeId = ref<number>(0)
const resumeName = ref('')
const resumePreview = ref('')
const resumeText = ref('')
const selectedQuestionCount = ref(8)
const loading = ref(false)
const isCreating = ref(false)

// 返回面试列表
const goToList = () => {
  uni.redirectTo({
    url: '/pages/interview/list'
  })
}

// 加载简历详情
const loadResumeDetail = async () => {
  if (!resumeId.value) return

  loading.value = true
  try {
    const result = await getResumeDetail(resumeId.value)
    resumeName.value = result.name || result.filename || '简历'
    // 取前500字纯文本作为预览（不包含AI分析summary）
    resumeText.value = result.resumeText || ''
    resumePreview.value = resumeText.value.length > 500 ? resumeText.value.substring(0, 500) + '...' : resumeText.value
  } catch (error) {
    console.error('加载简历详情失败:', error)
    resumeName.value = '简历详情'
    resumePreview.value = '简历内容加载失败'
  } finally {
    loading.value = false
  }
}

// 开始面试
const startInterview = async () => {
  if (isCreating.value) return

  isCreating.value = true
  try {
    // 调用创建会话接口
    const session = await createSession({
      resumeText: resumeText.value,
      questionCount: selectedQuestionCount.value,
      resumeId: resumeId.value
    })

    // 创建成功，跳转到会话页面
    uni.redirectTo({
      url: `/pages/interview/session?id=${session.sessionId}&total=${session.totalQuestions}`
    })
  } catch (error) {
    console.error('创建面试失败:', error)
    uni.showToast({
      title: '创建面试失败，请重试',
      icon: 'none'
    })
  } finally {
    isCreating.value = false
  }
}

onMounted(() => {
  // 从 URL 参数获取简历 ID
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}

  if (options.id) {
    resumeId.value = Number(options.id)
    loadResumeDetail()
  }

  if (options.name) {
    resumeName.value = decodeURIComponent(options.name)
  }
})
</script>

<template>
  <view class="config-container">
    <!-- 页面标题 -->
    <view class="page-header">
      <view class="header-icon">
        <svg class="header-icon-svg" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
          <circle cx="12" cy="12" r="6" stroke="currentColor" stroke-width="2"/>
          <circle cx="12" cy="12" r="2" fill="currentColor"/>
        </svg>
      </view>
      <text class="header-title">模拟面试</text>
      <text class="header-subtitle">配置您的面试参数</text>
    </view>

    <!-- 面试配置区域 -->
    <view class="config-section">
      <view class="section-title">面试配置</view>

      <!-- 题目数量选择 -->
      <view class="config-item">
        <text class="config-label">题目数量</text>
        <view class="question-count-grid">
          <view
            v-for="option in questionCountOptions"
            :key="option.value"
            class="count-option"
            :class="{ active: selectedQuestionCount === option.value }"
            @click="selectedQuestionCount = option.value"
          >
            {{ option.label }}
          </view>
        </view>
      </view>

      <!-- 简历预览 -->
      <view class="config-item">
        <text class="config-label">简历预览（前500字）</text>
        <view class="resume-preview-card">
          <text class="preview-name">{{ resumeName }}</text>
          <text class="preview-content">{{ resumePreview }}</text>
        </view>
      </view>

    </view>

    <!-- 创建会话加载遮罩 -->
    <view v-if="isCreating" class="loading-overlay">
      <view class="loading-content">
        <view class="loading-spinner"></view>
        <text class="loading-text">正在创建面试...</text>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="bottom-actions">
      <view class="btn btn-back" @click="goToList">
        <text>← 返回</text>
      </view>
      <view class="btn btn-start" :class="{ disabled: isCreating }" @click="startInterview">
        <text v-if="!isCreating">开始面试 →</text>
        <text v-else>创建中...</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
$primary-color: #6366f1;
$primary-light: #a5b4fc;

.config-container {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding-bottom: 180rpx;
}

.page-header {
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  padding: 60rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;

  .header-icon {
    width: 100rpx;
    height: 100rpx;
    border-radius: 50%;
    background-color: rgba(255, 255, 255, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 20rpx;

    .header-icon-svg {
      width: 48rpx;
      height: 48rpx;
      color: #fff;
    }
  }

  .header-title {
    font-size: 40rpx;
    font-weight: 700;
    color: #fff;
    margin-bottom: 8rpx;
  }

  .header-subtitle {
    font-size: 28rpx;
    color: rgba(255, 255, 255, 0.8);
  }
}

.config-section {
  margin: 30rpx;
  background-color: #fff;
  border-radius: 20rpx;
  padding: 30rpx;

  .section-title {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 30rpx;
    padding-left: 20rpx;
    border-left: 6rpx solid $primary-color;
  }
}

.config-item {
  margin-bottom: 30rpx;

  &:last-child {
    margin-bottom: 0;
  }

  .config-label {
    display: block;
    font-size: 28rpx;
    font-weight: 500;
    color: #333;
    margin-bottom: 16rpx;
  }
}

.question-count-grid {
  display: flex;
  gap: 16rpx;
}

.count-option {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #666;
  border: 4rpx solid transparent;
  transition: all 0.3s;

  &.active {
    background-color: #f8f7ff;
    border-color: $primary-color;
    color: $primary-color;
    font-weight: 600;
  }
}

.resume-preview-card {
  background-color: #f9fafb;
  border-radius: 12rpx;
  padding: 24rpx;

  .preview-name {
    display: block;
    font-size: 30rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
  }

  .preview-content {
    display: block;
    font-size: 26rpx;
    color: #666;
    line-height: 1.6;
  }
}

.distribution-card {
  background-color: #f9fafb;
  border-radius: 12rpx;
  padding: 24rpx;

  .distribution-text {
    display: block;
    font-size: 26rpx;
    color: #666;
    line-height: 1.6;
  }
}

// 创建会话加载遮罩
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  .loading-content {
    background-color: #fff;
    border-radius: 24rpx;
    padding: 60rpx 80rpx;
    display: flex;
    flex-direction: column;
    align-items: center;

    .loading-spinner {
      width: 60rpx;
      height: 60rpx;
      border: 6rpx solid #e5e7eb;
      border-top-color: $primary-color;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin-bottom: 24rpx;
    }

    .loading-text {
      font-size: 28rpx;
      color: #666;
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.bottom-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 24rpx;
  padding: 24rpx;
  background-color: #fff;
  border-top: 1rpx solid #f0f0f0;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.btn {
  flex: 1;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 48rpx;
  font-size: 32rpx;
  font-weight: 600;
  transition: all 0.3s;

  &.btn-back {
    background-color: #f5f5f5;
    color: #666;
  }

  &.btn-start {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    color: #fff;

    &.disabled {
      opacity: 0.6;
    }
  }
}
</style>
