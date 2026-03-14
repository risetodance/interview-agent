<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getKnowledgebaseList, deleteKnowledgebase, uploadToKnowledgebase, type Knowledgebase, type KnowledgebaseListParams } from '../../api/knowledgebase'

// 知识库列表数据
const knowledgebaseList = ref<Knowledgebase[]>([])
const loading = ref(false)
const refreshing = ref(false)
const listParams = ref<KnowledgebaseListParams>({
  page: 1,
  pageSize: 20
})
const total = ref(0)

// 创建知识库相关
const showCreateModal = ref(false)
const createLoading = ref(false)
const kbName = ref('')
const kbDescription = ref('')
const selectedFilePath = ref('')
const selectedFileName = ref('')

// 状态文本映射
const statusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待处理', color: '#909399' },
  PROCESSING: { text: '处理中', color: '#E6A23C' },
  COMPLETED: { text: '已完成', color: '#67C23A' },
  FAILED: { text: '处理失败', color: '#F56C6C' }
}

// 加载知识库列表
const loadKnowledgebaseList = async (refresh = false) => {
  if (loading.value) return

  loading.value = true
  refreshing.value = refresh

  if (refresh) {
    listParams.value.page = 1
  }

  try {
    const result = await getKnowledgebaseList(listParams.value)
    if (refresh) {
      knowledgebaseList.value = result.list
    } else {
      knowledgebaseList.value = [...knowledgebaseList.value, ...result.list]
    }
    total.value = result.total
    listParams.value.page++
  } catch (error) {
    console.error('加载知识库列表失败:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  loadKnowledgebaseList(true)
}

// 上拉加载更多
const onLoadMore = () => {
  if (knowledgebaseList.value.length < total.value) {
    loadKnowledgebaseList()
  }
}

// 跳转到问答页面
const goToChat = (id: number, name: string) => {
  uni.navigateTo({
    url: `/pages/knowledge/chat?id=${id}&name=${name}`
  })
}

// 创建知识库
const createKnowledgebase = () => {
  showCreateModal.value = true
}

// 选择文件
const chooseFile = () => {
  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.doc,.docx,.txt,.md'
  input.onchange = (e: any) => {
    const file = e.target.files[0]
    if (file) {
      selectedFileName.value = file.name
      // 将文件转为路径（用于上传）
      selectedFilePath.value = URL.createObjectURL(file)
    }
  }
  input.click()
  // #endif

  // #ifndef H5
  uni.chooseFile({
    count: 1,
    type: 'file',
    success: (res) => {
      if (res.tempFiles[0]) {
        selectedFilePath.value = res.tempFiles[0].path
        selectedFileName.value = res.tempFiles[0].name
      }
    }
  })
  // #endif
}

// 确认创建
const confirmCreate = async () => {
  if (!kbName.value) {
    uni.showToast({ title: '请输入知识库名称', icon: 'none' })
    return
  }

  if (!selectedFilePath.value) {
    uni.showToast({ title: '请选择要上传的文档', icon: 'none' })
    return
  }

  createLoading.value = true
  try {
    await uploadToKnowledgebase(selectedFilePath.value, kbName.value, kbDescription.value)
    uni.showToast({ title: '创建成功', icon: 'success' })
    showCreateModal.value = false
    // 重置表单
    kbName.value = ''
    kbDescription.value = ''
    selectedFilePath.value = ''
    selectedFileName.value = ''
    // 刷新列表
    loadKnowledgebaseList(true)
  } catch (error: any) {
    console.error('创建知识库失败:', error)
    uni.showToast({ title: error.message || '创建失败', icon: 'none' })
  } finally {
    createLoading.value = false
  }
}

// 取消创建
const cancelCreate = () => {
  showCreateModal.value = false
  kbName.value = ''
  kbDescription.value = ''
  selectedFilePath.value = ''
  selectedFileName.value = ''
}

// 删除知识库
const handleDelete = (id: number, index: number) => {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这个知识库吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteKnowledgebase(id)
          uni.showToast({
            title: '删除成功',
            icon: 'success'
          })
          knowledgebaseList.value.splice(index, 1)
        } catch (error) {
          console.error('删除知识库失败:', error)
        }
      }
    }
  })
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  loadKnowledgebaseList()
})
</script>

<template>
  <view class="knowledge-list-container">
    <!-- 顶部搜索 -->
    <view class="header">
      <view class="search-bar">
        <text class="iconfont icon-search">&#xe618;</text>
        <input
          v-model="listParams.keyword"
          placeholder="搜索知识库"
          @confirm="loadKnowledgebaseList(true)"
        />
      </view>
    </view>

    <!-- 知识库列表 -->
    <scroll-view
      class="knowledge-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view v-if="knowledgebaseList.length === 0 && !loading" class="empty">
        <text class="empty-icon">&#xe617;</text>
        <text class="empty-text">暂无知识库</text>
        <text class="empty-desc">点击下方按钮创建您的第一个知识库</text>
      </view>

      <view
        v-for="(item, index) in knowledgebaseList"
        :key="item.id"
        class="knowledge-card"
        @click="goToChat(item.id, item.name)"
      >
        <view class="knowledge-header">
          <view class="knowledge-avatar">
            <text class="avatar-icon">&#xe617;</text>
          </view>
          <view class="knowledge-info">
            <view class="knowledge-name">{{ item.name }}</view>
            <view class="knowledge-desc">
              {{ item.description || '暂无描述' }}
            </view>
          </view>
          <view class="knowledge-actions" @click.stop>
            <text class="delete-btn" @click="handleDelete(item.id, index)">删除</text>
          </view>
        </view>

        <view class="knowledge-stats">
          <view class="stat-item">
            <text class="stat-label">文档数</text>
            <text class="stat-value">{{ item.documentCount }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">状态</text>
            <text
              class="stat-value"
              :style="{ color: statusMap[item.status]?.color || '#909399' }"
            >
              {{ statusMap[item.status]?.text || '未知' }}
            </text>
          </view>
        </view>

        <view class="knowledge-footer">
          <text class="update-time">更新于 {{ formatDate(item.updatedAt) }}</text>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-more">
        <text>{{ knowledgebaseList.length < total ? '加载中...' : '没有更多了' }}</text>
      </view>
    </scroll-view>

    <!-- 创建按钮 -->
    <view class="create-btn" @click="createKnowledgebase">
      <text class="iconfont">&#xe60d;</text>
      <text>创建知识库</text>
    </view>

    <!-- 创建知识库弹窗 -->
    <view v-if="showCreateModal" class="modal-mask" @click="cancelCreate">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">创建知识库</text>
          <text class="modal-close" @click="cancelCreate">✕</text>
        </view>

        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">知识库名称 *</text>
            <input
              v-model="kbName"
              class="form-input"
              placeholder="请输入知识库名称"
            />
          </view>

          <view class="form-item">
            <text class="form-label">描述（可选）</text>
            <input
              v-model="kbDescription"
              class="form-input"
              placeholder="请输入知识库描述"
            />
          </view>

          <view class="form-item">
            <text class="form-label">上传文档 *</text>
            <view class="file-upload" @click="chooseFile">
              <text v-if="selectedFileName" class="file-name">{{ selectedFileName }}</text>
              <text v-else class="file-placeholder">点击选择文件（PDF/DOC/DOCX/TXT/MD）</text>
            </view>
          </view>
        </view>

        <view class="modal-footer">
          <view class="btn-cancel" @click="cancelCreate">取消</view>
          <view class="btn-confirm" :class="{ loading: createLoading }" @click="confirmCreate">
            <text>{{ createLoading ? '上传中...' : '确认创建' }}</text>
          </view>
        </view>
      </view>
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
$info: #818cf8;

.knowledge-list-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: $bg;
}

.header {
  position: relative;
  padding: 48rpx 40rpx 80rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 50%, $primary-light 100%);
  overflow: hidden;

  // 装饰性元素
  &::before {
    content: '';
    position: absolute;
    width: 300rpx;
    height: 300rpx;
    background: rgba(255, 255, 255, 0.06);
    border-radius: 50%;
    top: -100rpx;
    right: -80rpx;
  }
}

.search-bar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 40rpx;
  backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.12);

  .iconfont {
    font-size: 32rpx;
    color: rgba(255, 255, 255, 0.75);
    margin-right: 16rpx;
  }

  input {
    flex: 1;
    font-size: 28rpx;
    color: #fff;
    &::placeholder {
      color: rgba(255, 255, 255, 0.6);
    }
  }
}

.knowledge-list {
  flex: 1;
  padding: 32rpx 40rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  .empty-icon {
    font-size: 160rpx;
    color: #e2e8f0;
    margin-bottom: 32rpx;
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

.knowledge-card {
  background: $card-bg;
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.99);
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  }
}

.knowledge-header {
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
}

.knowledge-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  overflow: hidden;
  margin-right: 24rpx;
  background: linear-gradient(135deg, $warning, #fbbf24);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .avatar-icon {
    font-size: 44rpx;
    color: #fff;
  }
}

.knowledge-info {
  flex: 1;
  min-width: 0;
}

.knowledge-name {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.knowledge-desc {
  font-size: 26rpx;
  color: $text-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-actions {
  flex-shrink: 0;

  .delete-btn {
    font-size: 26rpx;
    color: $danger;
    padding: 12rpx 20rpx;
    background: rgba($danger, 0.08);
    border-radius: 12rpx;
  }
}

.knowledge-stats {
  display: flex;
  padding: 24rpx 0;
  border-top: 1rpx solid #f1f5f9;
  border-bottom: 1rpx solid #f1f5f9;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:first-child {
    border-right: 1rpx solid #f1f5f9;
  }
}

.stat-label {
  font-size: 24rpx;
  color: $text-muted;
  margin-bottom: 8rpx;
}

.stat-value {
  font-size: 30rpx;
  font-weight: 700;
  color: $text-primary;
}

.knowledge-footer {
  margin-top: 20rpx;
}

.update-time {
  font-size: 24rpx;
  color: $text-muted;
}

.loading-more {
  text-align: center;
  padding: 32rpx;
  font-size: 26rpx;
  color: $text-muted;
}

// 创建按钮
.create-btn {
  position: fixed;
  bottom: 60rpx;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  width: 260rpx;
  height: 84rpx;
  background: linear-gradient(135deg, $primary, $primary-light);
  border-radius: 42rpx;
  box-shadow: 0 8rpx 32rpx rgba($primary, 0.35);
  color: white;
  font-size: 30rpx;
  font-weight: 600;
  transition: all 0.3s ease;

  &:active {
    transform: translateX(-50%) scale(0.95);
    box-shadow: 0 4rpx 16rpx rgba($primary, 0.3);
  }

  .iconfont {
    font-size: 36rpx;
  }
}

// 弹窗样式
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 600rpx;
  background: white;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
}

.modal-close {
  font-size: 32rpx;
  color: $text-muted;
  padding: 8rpx;
}

.modal-body {
  padding: 32rpx;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  display: block;
  font-size: 26rpx;
  color: $text-secondary;
  margin-bottom: 12rpx;
  font-weight: 500;
}

.form-input {
  width: 100%;
  height: 80rpx;
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: $text-primary;
  box-sizing: border-box;

  &:focus {
    border-color: $primary;
    background: white;
  }
}

.file-upload {
  width: 100%;
  height: 160rpx;
  background: #f8fafc;
  border: 2rpx dashed #cbd5e1;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12rpx;
}

.file-name {
  font-size: 26rpx;
  color: $primary;
  font-weight: 500;
  text-align: center;
  padding: 0 20rpx;
  word-break: break-all;
}

.file-placeholder {
  font-size: 24rpx;
  color: $text-muted;
  text-align: center;
}

.modal-footer {
  display: flex;
  border-top: 1rpx solid #f0f0f0;
}

.btn-cancel,
.btn-confirm {
  flex: 1;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 500;
}

.btn-cancel {
  color: $text-secondary;
  border-right: 1rpx solid #f0f0f0;
}

.btn-confirm {
  color: $primary;
  font-weight: 600;

  &.loading {
    opacity: 0.6;
  }
}
</style>
