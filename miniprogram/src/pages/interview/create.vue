<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getResumeList, type Resume } from '../../api/resume'

// 简历列表
const resumeList = ref<Resume[]>([])
const selectedResume = ref<Resume | null>(null)

// 加载简历列表
const loadResumeList = async () => {
  try {
    const result = await getResumeList({ page: 1, pageSize: 50 })
    resumeList.value = result.list || []
  } catch (error) {
    console.error('加载简历列表失败:', error)
  }
}

// 选择简历
const selectResume = (resume: Resume) => {
  selectedResume.value = resume
}

// 继续到配置页面
const goToConfig = () => {
  if (!selectedResume.value) {
    uni.showToast({
      title: '请先选择简历',
      icon: 'none'
    })
    return
  }

  // 跳转到配置页面
  uni.redirectTo({
    url: `/pages/interview/config?id=${selectedResume.value.id}&name=${encodeURIComponent(selectedResume.value.name || '')}`
  })
}

// 返回
const goBack = () => {
  uni.navigateBack()
}

onMounted(() => {
  loadResumeList()
})
</script>

<template>
  <view class="create-interview-container">
    <!-- 顶部导航 -->
    <view class="nav-bar">
      <view class="nav-back" @click="goBack">
        <svg class="back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
      </view>
      <text class="nav-title">选择简历</text>
      <view class="nav-placeholder"></view>
    </view>

    <!-- 提示文字 -->
    <view class="tips">
      <text class="tips-text">请选择一份简历开始面试</text>
    </view>

    <!-- 简历列表 -->
    <scroll-view class="resume-list" scroll-y>
      <view
        v-for="resume in resumeList"
        :key="resume.id"
        class="resume-card"
        :class="{ selected: selectedResume?.id === resume.id }"
        @click="selectResume(resume)"
      >
        <view class="resume-icon">
          <svg class="icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="16" y1="13" x2="8" y2="13"></line>
            <line x1="16" y1="17" x2="8" y2="17"></line>
            <polyline points="10 9 9 9 8 9"></polyline>
          </svg>
        </view>
        <view class="resume-info">
          <text class="resume-name">{{ resume.name || '未命名简历' }}</text>
          <text class="resume-file">{{ resume.fileName || '' }}</text>
          <text class="resume-date">上传于 {{ resume.updatedAt || resume.createdAt }}</text>
        </view>
        <view v-if="selectedResume?.id === resume.id" class="check-icon">
          <svg class="icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-if="resumeList.length === 0" class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
        </svg>
        <text class="empty-text">暂无简历</text>
        <text class="empty-hint">请先上传简历</text>
      </view>
    </scroll-view>

    <!-- 底部按钮 -->
    <view class="bottom-action">
      <view
        class="start-btn"
        :class="{ disabled: !selectedResume }"
        @click="goToConfig"
      >
        <text>继续</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

.create-interview-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f7fa;
}

.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #f0f0f0;

  .nav-back {
    width: 60rpx;
    height: 60rpx;
    display: flex;
    align-items: center;
    justify-content: center;

    .back-icon {
      width: 36rpx;
      height: 36rpx;
      color: #333;
    }
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: #333;
  }

  .nav-placeholder {
    width: 60rpx;
  }
}

.tips {
  padding: 30rpx;
  background-color: #fff;
  margin-bottom: 20rpx;

  .tips-text {
    font-size: 28rpx;
    color: #666;
  }
}

.resume-list {
  flex: 1;
  padding: 0 20rpx;
}

.resume-card {
  display: flex;
  align-items: center;
  padding: 30rpx;
  background-color: #fff;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  border: 4rpx solid transparent;
  transition: all 0.3s;

  &.selected {
    border-color: $primary-color;
    background-color: #f8f7ff;
  }

  .resume-icon {
    width: 80rpx;
    height: 80rpx;
    border-radius: 16rpx;
    background-color: #eef2ff;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 24rpx;

    .icon-svg {
      width: 40rpx;
      height: 40rpx;
      color: $primary-color;
    }
  }

  .resume-info {
    flex: 1;
    display: flex;
    flex-direction: column;

    .resume-name {
      font-size: 32rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 8rpx;
    }

    .resume-file {
      font-size: 26rpx;
      color: #666;
      margin-bottom: 4rpx;
    }

    .resume-date {
      font-size: 24rpx;
      color: #999;
    }
  }

  .check-icon {
    width: 48rpx;
    height: 48rpx;
    border-radius: 50%;
    background-color: $primary-color;
    display: flex;
    align-items: center;
    justify-content: center;

    .icon-svg {
      width: 24rpx;
      height: 24rpx;
      color: #fff;
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 0;

  .empty-icon {
    width: 80rpx;
    height: 80rpx;
    color: #ddd;
    margin-bottom: 20rpx;
  }

  .empty-text {
    font-size: 32rpx;
    color: #666;
    margin-bottom: 12rpx;
  }

  .empty-hint {
    font-size: 26rpx;
    color: #999;
  }
}

.bottom-action {
  padding: 24rpx;
  background-color: #fff;
  border-top: 1rpx solid #f0f0f0;
}

.start-btn {
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: 48rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #fff;

  &.disabled {
    opacity: 0.5;
  }
}
</style>
