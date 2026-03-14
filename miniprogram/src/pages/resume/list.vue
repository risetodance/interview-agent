<script setup lang="ts">
import { ref, onMounted, onActivated, onUnmounted } from 'vue'
import { getResumeList, deleteResume, type Resume, type ResumeListParams } from '../../api/resume'

// 简历列表数据
const resumeList = ref<Resume[]>([])
const loading = ref(false)
const refreshing = ref(false)
const listParams = ref<ResumeListParams>({
  page: 1,
  pageSize: 20
})
const total = ref(0)

// 解析状态映射
const parseStatusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待解析', color: '#909399' },
  PROCESSING: { text: '解析中', color: '#E6A23C' },
  COMPLETED: { text: '已完成', color: '#67C23A' },
  FAILED: { text: '解析失败', color: '#F56C6C' }
}

// 加载简历列表
const loadResumeList = async (refresh = false) => {
  if (loading.value) return

  loading.value = true
  refreshing.value = refresh

  if (refresh) {
    listParams.value.page = 1
  }

  try {
    const result = await getResumeList(listParams.value)
    if (refresh) {
      resumeList.value = result.list
    } else {
      resumeList.value = [...resumeList.value, ...result.list]
    }
    total.value = result.total
    listParams.value.page++
  } catch (error) {
    console.error('加载简历列表失败:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 页面首次加载时获取列表
onMounted(() => {
  loadResumeList(true)
})

// KeepAlive激活时刷新列表（每次返回页面时都会触发）
onActivated(() => {
  loadResumeList(true)
})

// 下拉刷新
const onRefresh = () => {
  loadResumeList(true)
}

// 上拉加载更多
const onLoadMore = () => {
  if (resumeList.value.length < total.value) {
    loadResumeList()
  }
}

// 跳转到简历详情
const goToDetail = (id: number) => {
  uni.navigateTo({
    url: `/pages/resume/detail?id=${id}`
  })
}

// 跳转到上传页面
const goToUpload = () => {
  uni.navigateTo({
    url: '/pages/resume/upload'
  })
}

// 删除简历
const handleDelete = (id: number, index: number) => {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这份简历吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteResume(id)
          uni.showToast({
            title: '删除成功',
            icon: 'success'
          })
          resumeList.value.splice(index, 1)
        } catch (error) {
          console.error('删除简历失败:', error)
        }
      }
    }
  })
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

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

</script>

<template>
  <view class="resume-list-container">
    <!-- 顶部搜索和筛选 -->
    <view class="header">
      <view class="search-bar">
        <text class="iconfont icon-search">&#xe618;</text>
        <input
          v-model="listParams.keyword"
          placeholder="搜索简历"
          @confirm="loadResumeList(true)"
        />
      </view>
    </view>

    <!-- 简历列表 -->
    <scroll-view
      class="resume-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view v-if="resumeList.length === 0 && !loading" class="empty">
        <text class="empty-icon">&#xe60c;</text>
        <text class="empty-text">暂无简历</text>
        <text class="empty-desc">点击下方按钮上传您的第一份简历</text>
      </view>

      <view
        v-for="(item, index) in resumeList"
        :key="item.id"
        class="resume-card"
        @click="goToDetail(item.id)"
      >
        <view class="resume-header">
          <view class="resume-avatar">
            <image
              v-if="item.basicInfo?.avatar"
              :src="item.basicInfo.avatar"
              mode="aspectFill"
            />
            <text v-else class="avatar-placeholder">
              {{ item.name?.charAt(0) || '简历' }}
            </text>
          </view>
          <view class="resume-info">
            <view class="resume-name">{{ item.name || '未命名简历' }}</view>
            <view class="resume-meta">
              <text class="file-name">{{ item.fileName }}</text>
              <text class="separator">|</text>
              <text class="file-size">{{ formatFileSize(item.fileSize) }}</text>
            </view>
          </view>
          <view class="resume-actions" @click.stop>
            <text class="delete-btn" @click="handleDelete(item.id, index)">删除</text>
          </view>
        </view>

        <view class="resume-status">
          <view class="status-item">
            <text class="status-label">解析状态</text>
            <view class="parse-status">
              <text
                class="status-value"
                :style="{ color: parseStatusMap[item.parseStatus]?.color || '#909399' }"
              >
                {{ parseStatusMap[item.parseStatus]?.text || '未知' }}
              </text>
              <view
                v-if="item.parseStatus === 'PROCESSING' && item.parseProgress"
                class="progress-bar"
              >
                <view
                  class="progress-inner"
                  :style="{ width: item.parseProgress + '%' }"
                ></view>
              </view>
            </view>
          </view>
        </view>

        <view class="resume-footer">
          <text class="update-time">更新于 {{ formatDate(item.updatedAt) }}</text>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-more">
        <text>{{ resumeList.length < total ? '加载中...' : '没有更多了' }}</text>
      </view>
    </scroll-view>

    <!-- 上传按钮 -->
    <view class="upload-btn" @click="goToUpload">
      <text class="iconfont">&#xe60d;</text>
      <text>上传简历</text>
    </view>
  </view>
</template>

<style lang="scss">
// 配色变量 - 靛蓝清新配色
$primary: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg: #f8fafc;
$card-bg: #ffffff;
$text-primary: #1e293b;
$text-secondary: #475569;
$text-muted: #94a3b8;
$success: #6366f1;
$warning: #f59e0b;
$danger: #ef4444;

.resume-list-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg;
}

.header {
  padding: 24rpx 32rpx;
  background-color: $card-bg;
  border-bottom: 1rpx solid #f1f5f9;
}

.search-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 28rpx;
  background-color: #f1f5f9;
  border-radius: 16rpx;

  .iconfont {
    font-size: 28rpx;
    color: $text-muted;
    margin-right: 16rpx;
  }

  input {
    flex: 1;
    font-size: 28rpx;
    color: $text-primary;
  }
}

.resume-list {
  flex: 1;
  padding: 24rpx 32rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx 0;

  .empty-icon {
    font-size: 120rpx;
    color: #e2e8f0;
    margin-bottom: 24rpx;
  }

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

.resume-card {
  background-color: $card-bg;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 1rpx 8rpx rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease;

  &:active {
    transform: scale(0.99);
    box-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.04);
  }
}

.resume-header {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
}

.resume-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  overflow: hidden;
  margin-right: 20rpx;
  background: linear-gradient(135deg, $primary, $primary-light);
  display: flex;
  align-items: center;
  justify-content: center;

  image {
    width: 100%;
    height: 100%;
  }

  .avatar-placeholder {
    font-size: 36rpx;
    color: white;
    font-weight: 600;
  }
}

.resume-info {
  flex: 1;
  min-width: 0;
}

.resume-name {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.resume-meta {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: $text-muted;

  .separator {
    margin: 0 10rpx;
  }
}

.resume-actions {
  .delete-btn {
    font-size: 24rpx;
    color: $danger;
    padding: 8rpx 16rpx;
    background: #fef2f2;
    border-radius: 8rpx;
  }
}

.resume-status {
  display: flex;
  padding: 20rpx 0;
  border-top: 1rpx solid #f1f5f9;
}

.status-item {
  flex: 1;
  display: flex;
  flex-direction: column;

  &:first-child {
    border-right: 1rpx solid #f1f5f9;
  }
}

.status-label {
  font-size: 22rpx;
  color: $text-muted;
  margin-bottom: 6rpx;
}

.status-value {
  font-size: 26rpx;
  font-weight: 600;
}

.parse-status {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.progress-bar {
  width: 56rpx;
  height: 6rpx;
  background-color: #e2e8f0;
  border-radius: 3rpx;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  background: $success;
  border-radius: 3rpx;
}

.resume-footer {
  margin-top: 16rpx;
}

.update-time {
  font-size: 22rpx;
  color: $text-muted;
}

.loading-more {
  text-align: center;
  padding: 24rpx;
  font-size: 24rpx;
  color: $text-muted;
}

.upload-btn {
  position: fixed;
  bottom: 60rpx;
  left: 30rpx;
  right: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  background: linear-gradient(135deg, $primary, $accent);
  border-radius: 16rpx;
  box-shadow: 0 8rpx 24rpx rgba($primary, 0.3);
  color: white;
  font-size: 32rpx;
  font-weight: 500;

  .iconfont {
    font-size: 36rpx;
  }
}
</style>
