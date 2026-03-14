<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { uploadResume, reanalyzeResume, type UploadResumeResult } from '../../api/resume'

// 简历名称
const resumeName = ref('')

// 测试模式：自动选择文件
onMounted(() => {
  // #ifdef H5
  // 开发测试用：自动填充测试文件
  handleFileSelected({
    name: '简历-开科版.pdf',
    path: '/static/简历-开科版.pdf',
    size: 572060,
    originalFile: null
  })
  // #endif
})

// 选择的文件信息
const selectedFile = ref<{
  name: string
  path: string
  size: number
  type: string
} | null>(null)
// 上传进度
const uploadProgress = ref(0)
// 上传状态
const uploadStatus = ref<'idle' | 'uploading' | 'success' | 'error'>('idle')
// 错误信息
const errorMessage = ref('')

// 支持的文件类型
const acceptedTypes = ['.pdf', '.doc', '.docx']
// 最大文件大小 10MB
const maxFileSize = 10 * 1024 * 1024

// 选择文件
const chooseFile = () => {
  // #ifdef MP-WEIXIN
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['pdf', 'doc', 'docx'],
    success: (res) => {
      handleFileSelected(res.tempFiles[0])
    },
    fail: (error) => {
      console.error('选择文件失败:', error)
      uni.showToast({
        title: '选择文件失败',
        icon: 'none'
      })
    }
  })
  // #endif

  // #ifdef H5
  // H5 环境下创建文件输入元素
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.doc,.docx'
  input.onchange = (e: any) => {
    const file = e.target.files?.[0]
    if (file) {
      handleFileSelected({
        name: file.name,
        path: URL.createObjectURL(file),
        size: file.size,
        originalFile: file
      })
    }
  }
  input.click()
  // #endif
}

// 处理选中的文件
const handleFileSelected = (file: any) => {
  if (!file) return

  // 检查文件大小
  if (file.size > maxFileSize) {
    uni.showToast({
      title: '文件大小不能超过10MB',
      icon: 'none'
    })
    return
  }

  selectedFile.value = {
    name: file.name,
    path: file.path,
    size: file.size,
    type: getFileType(file.name),
    originalFile: file.originalFile
  }

  // 如果没有输入名称，默认使用文件名
  if (!resumeName.value) {
    resumeName.value = file.name.replace(/\.(pdf|doc|docx)$/i, '')
  }

  uploadStatus.value = 'idle'
  errorMessage.value = ''
}

// 获取文件类型
const getFileType = (fileName: string): string => {
  const ext = fileName.split('.').pop()?.toLowerCase()
  switch (ext) {
    case 'pdf':
      return 'PDF'
    case 'doc':
      return 'DOC'
    case 'docx':
      return 'DOCX'
    default:
      return 'FILE'
  }
}

// 格式化文件大小
const formatFileSize = (size: number): string => {
  if (size < 1024) {
    return size + ' B'
  } else if (size < 1024 * 1024) {
    return (size / 1024).toFixed(1) + ' KB'
  } else {
    return (size / (1024 * 1024)).toFixed(1) + ' MB'
  }
}

// 上传简历
const handleUpload = async () => {
  if (!selectedFile.value) {
    uni.showToast({
      title: '请先选择文件',
      icon: 'none'
    })
    return
  }

  uploadStatus.value = 'uploading'
  uploadProgress.value = 0
  errorMessage.value = ''

  // 模拟进度（因为 uni.uploadFile 不直接提供进度回调）
  const progressTimer = setInterval(() => {
    if (uploadProgress.value < 90) {
      uploadProgress.value += 10
    }
  }, 200)

  try {
    let result: any

    // #ifdef H5
    // H5 环境下使用原生 fetch 上传
    const baseURL = import.meta.env.VITE_API_BASE_URL?.replace(/\/api$/, '') || 'https://api.interview-guide.com'
    const token = uni.getStorageSync('token')

    // 从 static 文件夹获取测试简历文件并上传
    const fileResponse = await fetch('/static/简历-开科版.pdf')
    const fileBlob = await fileResponse.blob()
    const file = new File([fileBlob], '简历-开科版.pdf', { type: 'application/pdf' })

    const formData = new FormData()
    formData.append('file', file)
    if (resumeName.value) {
      formData.append('name', resumeName.value)
    }

    const uploadResponse = await fetch(baseURL + '/api/resumes/upload', {
      method: 'POST',
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: formData
    })

    const uploadData = await uploadResponse.json()
    if (uploadData.code === 200) {
      result = uploadData.data
    } else {
      throw new Error(uploadData.message || '上传失败')
    }
    // #endif

    // #ifdef MP-WEIXIN
    result = await uploadResume(
      selectedFile.value.path,
      resumeName.value || undefined
    )
    // #endif

    clearInterval(progressTimer)
    uploadProgress.value = 100
    uploadStatus.value = 'success'

    uni.showToast({
      title: '上传成功，正在解析',
      icon: 'success',
      duration: 1500
    })

    // 跳转到详情页面，详情页会自动轮询等待分析完成
    setTimeout(() => {
      const resumeId = result.resume?.id || result.id || result.storage?.resumeId
      uni.redirectTo({
        url: `/pages/resume/detail?id=${resumeId}`
      })
    }, 1500)
  } catch (error: any) {
    clearInterval(progressTimer)
    uploadStatus.value = 'error'
    errorMessage.value = error.message || '上传失败，请重试'

    uni.showToast({
      title: errorMessage.value,
      icon: 'none'
    })
  }
}

// 重新选择文件
const resetUpload = () => {
  selectedFile.value = null
  resumeName.value = ''
  uploadStatus.value = 'idle'
  uploadProgress.value = 0
  errorMessage.value = ''
}

// 返回上一页
const goBack = () => {
  uni.navigateBack()
}
</script>

<template>
  <view class="upload-container">
    <!-- 顶部导航 -->
    <view class="nav-bar">
      <text class="back-btn" @click="goBack">取消</text>
      <text class="title">上传简历</text>
      <text class="placeholder"></text>
    </view>

    <!-- 上传区域 -->
    <view class="upload-area">
      <!-- 文件选择 -->
      <view
        v-if="!selectedFile"
        class="upload-placeholder"
        @click="chooseFile"
      >
        <view class="upload-icon">
          <text class="iconfont">&#xe60d;</text>
        </view>
        <text class="upload-text">点击选择简历文件</text>
        <text class="upload-hint">支持 PDF、DOC、DOCX 格式，最大 10MB</text>
      </view>

      <!-- 已选择文件 -->
      <view v-else class="file-card">
        <view class="file-info">
          <view class="file-icon" :class="selectedFile.type.toLowerCase()">
            <text class="iconfont">&#xe614;</text>
          </view>
          <view class="file-detail">
            <text class="file-name">{{ selectedFile.name }}</text>
            <text class="file-size">{{ formatFileSize(selectedFile.size) }}</text>
          </view>
          <text class="change-btn" @click="chooseFile">更换</text>
        </view>
      </view>
    </view>

    <!-- 简历名称输入 -->
    <view class="form-section">
      <text class="form-label">简历名称（可选）</text>
      <input
        v-model="resumeName"
        class="form-input"
        placeholder="请输入简历名称，便于识别"
        maxlength="50"
      />
    </view>

    <!-- 上传进度 -->
    <view v-if="uploadStatus === 'uploading'" class="progress-section">
      <view class="progress-header">
        <text class="progress-text">正在上传...</text>
        <text class="progress-percent">{{ uploadProgress }}%</text>
      </view>
      <view class="progress-bar">
        <view
          class="progress-inner"
          :style="{ width: uploadProgress + '%' }"
        ></view>
      </view>
    </view>

    <!-- 上传成功 -->
    <view v-if="uploadStatus === 'success'" class="success-section">
      <view class="success-icon">
        <text class="iconfont">&#xe617;</text>
      </view>
      <text class="success-text">上传成功！</text>
      <text class="success-hint">正在跳转到简历详情...</text>
    </view>

    <!-- 上传错误 -->
    <view v-if="uploadStatus === 'error'" class="error-section">
      <view class="error-icon">
        <text class="iconfont">&#xe616;</text>
      </view>
      <text class="error-text">上传失败</text>
      <text class="error-hint">{{ errorMessage }}</text>
      <text class="retry-btn" @click="handleUpload">重新上传</text>
    </view>

    <!-- 上传按钮 -->
    <view class="submit-section">
      <button
        v-if="uploadStatus === 'idle' || uploadStatus === 'error'"
        class="submit-btn"
        :disabled="!selectedFile"
        @click="handleUpload"
      >
        {{ uploadStatus === 'error' ? '重新上传' : '开始上传' }}
      </button>
    </view>

    <!-- 注意事项 -->
    <view class="tips-section">
      <text class="tips-title">上传须知</text>
      <view class="tips-list">
        <text class="tips-item">1. 支持 PDF、DOC、DOCX 格式的简历文件</text>
        <text class="tips-item">2. 文件大小不能超过 10MB</text>
        <text class="tips-item">3. 上传后系统会自动解析简历内容</text>
        <text class="tips-item">4. 解析完成后可查看详细的分析报告</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
.upload-container {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background-color: #fff;

  .back-btn,
  .placeholder {
    font-size: 30rpx;
    color: #6366f1;
    width: 120rpx;
  }

  .placeholder {
    text-align: right;
  }

  .title {
    font-size: 34rpx;
    font-weight: 600;
    color: #333;
  }
}

.upload-area {
  padding: 40rpx 30rpx;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 0;
  background-color: #fff;
  border-radius: 24rpx;
  border: 2rpx dashed #ddd;
  transition: all 0.3s;

  &:active {
    border-color: #6366f1;
    background-color: #f8fbf8;
  }

  .upload-icon {
    width: 160rpx;
    height: 160rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #eef2ff;
    border-radius: 50%;
    margin-bottom: 30rpx;

    .iconfont {
      font-size: 64rpx;
      color: #6366f1;
    }
  }

  .upload-text {
    font-size: 32rpx;
    color: #333;
    margin-bottom: 16rpx;
  }

  .upload-hint {
    font-size: 26rpx;
    color: #999;
  }
}

.file-card {
  background-color: #fff;
  border-radius: 24rpx;
  padding: 30rpx;
}

.file-info {
  display: flex;
  align-items: center;
}

.file-icon {
  width: 96rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  margin-right: 24rpx;

  &.pdf {
    background-color: #ffebee;
    .iconfont {
      color: #f44336;
    }
  }

  &.doc,
  &.docx {
    background-color: #e3f2fd;
    .iconfont {
      color: #2196f3;
    }
  }

  .iconfont {
    font-size: 48rpx;
  }
}

.file-detail {
  flex: 1;

  .file-name {
    display: block;
    font-size: 30rpx;
    font-weight: 500;
    color: #333;
    margin-bottom: 8rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-size {
    font-size: 26rpx;
    color: #999;
  }
}

.change-btn {
  font-size: 28rpx;
  color: #6366f1;
  padding: 10rpx 20rpx;
}

.form-section {
  padding: 0 30rpx 30rpx;
}

.form-label {
  display: block;
  font-size: 28rpx;
  color: #333;
  margin-bottom: 16rpx;
  font-weight: 500;
}

.form-input {
  height: 96rpx;
  padding: 0 30rpx;
  background-color: #fff;
  border-radius: 16rpx;
  font-size: 30rpx;
}

.progress-section {
  margin: 0 30rpx 30rpx;
  padding: 30rpx;
  background-color: #fff;
  border-radius: 16rpx;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;

  .progress-text {
    font-size: 28rpx;
    color: #333;
  }

  .progress-percent {
    font-size: 28rpx;
    color: #6366f1;
    font-weight: 500;
  }
}

.progress-bar {
  height: 12rpx;
  background-color: #f5f5f5;
  border-radius: 6rpx;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #45a049);
  border-radius: 6rpx;
  transition: width 0.3s ease;
}

.success-section,
.error-section {
  margin: 0 30rpx 30rpx;
  padding: 60rpx 30rpx;
  background-color: #fff;
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.success-icon,
.error-icon {
  width: 120rpx;
  height: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  margin-bottom: 24rpx;

  .iconfont {
    font-size: 56rpx;
  }
}

.success-icon {
  background-color: #eef2ff;

  .iconfont {
    color: #6366f1;
  }
}

.error-icon {
  background-color: #ffebee;

  .iconfont {
    color: #f44336;
  }
}

.success-text,
.error-text {
  font-size: 32rpx;
  font-weight: 500;
  margin-bottom: 12rpx;
}

.success-text {
  color: #6366f1;
}

.error-text {
  color: #f44336;
}

.success-hint,
.error-hint {
  font-size: 26rpx;
  color: #999;
  margin-bottom: 24rpx;
}

.retry-btn {
  font-size: 28rpx;
  color: #6366f1;
  padding: 16rpx 40rpx;
  border: 2rpx solid #6366f1;
  border-radius: 40rpx;
}

.submit-section {
  padding: 0 30rpx 30rpx;
}

.submit-btn {
  height: 96rpx;
  line-height: 96rpx;
  width: 100%;
  background: linear-gradient(135deg, #6366f1, #45a049);
  border-radius: 16rpx;
  color: #fff;
  font-size: 32rpx;
  font-weight: 500;
  border: none;

  &[disabled] {
    background: #ccc;
    color: #999;
  }
}

.tips-section {
  padding: 30rpx;
  background-color: #fff;
  margin: 0 30rpx 60rpx;
  border-radius: 16rpx;
}

.tips-title {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: #333;
  margin-bottom: 20rpx;
}

.tips-list {
  display: flex;
  flex-direction: column;
}

.tips-item {
  font-size: 26rpx;
  color: #666;
  line-height: 40rpx;
  margin-bottom: 12rpx;
}
</style>
