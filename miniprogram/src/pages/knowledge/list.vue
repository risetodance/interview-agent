<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Icon from '../../components/common/Icon.vue'
import {
  getKnowledgebaseList,
  getKnowledgebaseStats,
  getAllCategories,
  searchKnowledgebase,
  deleteKnowledgebase,
  uploadToKnowledgebase,
  updateKnowledgebaseCategory,
  downloadKnowledgebase,
  revectorizeKnowledgebase,
  type Knowledgebase,
  type KnowledgebaseStats,
  type SortOption,
  type VectorStatus
} from '../../api/knowledgebase'

// 状态
const loading = ref(false)
const refreshing = ref(false)
const stats = ref<KnowledgebaseStats | null>(null)
const knowledgebaseList = ref<Knowledgebase[]>([])
const categories = ref<string[]>([])
const total = ref(0)

// 筛选和排序
const searchKeyword = ref('')
const sortBy = ref<SortOption>('time')
const selectedCategory = ref<string | null>(null)

// 创建知识库相关
const showCreateModal = ref(false)
const createLoading = ref(false)
const kbName = ref('')
const kbCategory = ref('')
const selectedFilePath = ref('')
const selectedFileName = ref('')

// 删除确认
const showDeleteModal = ref(false)
const deleteItem = ref<Knowledgebase | null>(null)
const deleteLoading = ref(false)

// 多选相关
const selectedKbIds = ref<Set<number>>(new Set())
const showSelectionBar = ref(false)

// 分类编辑
const editingCategoryId = ref<number | null>(null)
const editingCategoryValue = ref('')
const savingCategory = ref(false)

// 状态文本和颜色映射
const statusMap: Record<VectorStatus, { text: string; color: string; bgColor: string }> = {
  PENDING: { text: '待处理', color: '#F59E0B', bgColor: 'rgba(245, 158, 11, 0.1)' },
  PROCESSING: { text: '处理中', color: '#3B82F6', bgColor: 'rgba(59, 130, 246, 0.1)' },
  COMPLETED: { text: '已完成', color: '#10B981', bgColor: 'rgba(16, 185, 129, 0.1)' },
  FAILED: { text: '处理失败', color: '#EF4444', bgColor: 'rgba(239, 68, 68, 0.1)' }
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[Math.min(i, sizes.length - 1)]
}

// 格式化日期
const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// 格式化时间 Ago
const formatTimeAgo = (dateStr: string): string => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  return formatDate(dateStr)
}

// 加载数据
const loadData = async (refresh = false) => {
  if (loading.value && !refresh) return

  loading.value = true
  refreshing.value = refresh

  try {
    const [statsData, kbList, categoryList] = await Promise.all([
      getKnowledgebaseStats().catch(() => null),
      searchKeyword.value
        ? searchKnowledgebase(searchKeyword.value)
        : selectedCategory.value
        ? getKnowledgebaseList({ category: selectedCategory.value } as any)
            .then(res => res.list)
        : getKnowledgebaseList({ sortBy: sortBy.value } as any).then(res => res.list),
      getAllCategories().catch(() => [])
    ])

    stats.value = statsData
    // kbList可能是数组或分页对象
    knowledgebaseList.value = (kbList as any).list || (Array.isArray(kbList) ? kbList : []) || []
    categories.value = categoryList || []
    total.value = knowledgebaseList.value.length
  } catch (error) {
    console.error('加载失败:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 搜索
const handleSearch = () => {
  loadData(true)
}

// 下拉刷新
const onRefresh = () => {
  loadData(true)
}

// 跳转到问答页面（支持多选）
const goToChat = () => {
  if (selectedKbIds.value.size === 0) {
    uni.showToast({ title: '请先选择知识库', icon: 'none' })
    return
  }

  // 将选中知识库的ID和名称编码传递
  const ids = Array.from(selectedKbIds.value).join(',')
  const names = knowledgebaseList.value
    .filter(item => selectedKbIds.value.has(item.id))
    .map(item => item.name)
    .join(',')
    || '知识库问答'

  uni.navigateTo({
    url: `/pages/knowledge/chat?ids=${ids}&names=${encodeURIComponent(names)}`
  })
}

// 切换知识库选中状态
const toggleKbSelection = (id: number) => {
  const newSet = new Set(selectedKbIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedKbIds.value = newSet

  // 更新底部按钮状态
  if (newSet.size > 0) {
    showSelectionBar.value = true
  } else {
    showSelectionBar.value = false
  }
}

// 全选/取消全选
const toggleSelectAll = () => {
  if (selectedKbIds.value.size === knowledgebaseList.value.length) {
    // 取消全选
    selectedKbIds.value = new Set()
    showSelectionBar.value = false
  } else {
    // 全选
    selectedKbIds.value = new Set(knowledgebaseList.value.map(item => item.id))
    showSelectionBar.value = true
  }
}

// 清除选择
const clearSelection = () => {
  selectedKbIds.value = new Set()
  showSelectionBar.value = false
}

// 启动多选模式
const enterSelectionMode = () => {
  if (selectedKbIds.value.size === 0 && knowledgebaseList.value.length > 0) {
    // 默认选中当前点击项
    // 不自动选中，等待用户操作
  }
  showSelectionBar.value = selectedKbIds.value.size > 0
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
      selectedFilePath.value = URL.createObjectURL(file)
    }
  }
  input.click()
  // #endif

  // #ifndef H5
  uni.chooseFile({
    count: 1,
    type: 'all',
    success: (res: any) => {
      if (res.tempFiles && res.tempFiles[0]) {
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
    await uploadToKnowledgebase(selectedFilePath.value, kbName.value, kbCategory.value)
    uni.showToast({ title: '创建成功', icon: 'success' })
    showCreateModal.value = false
    resetForm()
    loadData(true)
  } catch (error: any) {
    uni.showToast({ title: error.message || '创建失败', icon: 'none' })
  } finally {
    createLoading.value = false
  }
}

// 重置表单
const resetForm = () => {
  kbName.value = ''
  kbCategory.value = ''
  selectedFilePath.value = ''
  selectedFileName.value = ''
}

// 取消创建
const cancelCreate = () => {
  showCreateModal.value = false
  resetForm()
}

// 确认删除
const confirmDelete = async () => {
  if (!deleteItem.value) return

  deleteLoading.value = true
  try {
    await deleteKnowledgebase(deleteItem.value.id)
    uni.showToast({ title: '删除成功', icon: 'success' })
    showDeleteModal.value = false
    deleteItem.value = null
    loadData(true)
  } catch (error: any) {
    uni.showToast({ title: error.message || '删除失败', icon: 'none' })
  } finally {
    deleteLoading.value = false
  }
}

// 打开删除确认
const handleDelete = (item: Knowledgebase) => {
  deleteItem.value = item
  showDeleteModal.value = true
}

// 下载知识库
const handleDownload = async (item: Knowledgebase) => {
  try {
    const filePath = await downloadKnowledgebase(item.id, item.originalFilename)
    // 知识库为文档类型（PDF/DOC/TXT/MD 等），saveImageToPhotosAlbum 仅支持图片，
    // 改用 openDocument 打开预览；showMenu 允许用户通过菜单保存或分享
    const ext = (item.originalFilename || '').split('.').pop()?.toLowerCase() || ''
    const fileTypeMap: Record<string, string> = {
      pdf: 'pdf', doc: 'doc', docx: 'docx',
      xls: 'xls', xlsx: 'xlsx', ppt: 'ppt', pptx: 'pptx'
    }
    uni.openDocument({
      filePath,
      fileType: fileTypeMap[ext],
      showMenu: true,
      success: () => {
        uni.showToast({ title: '已打开', icon: 'success' })
      },
      fail: () => {
        uni.showToast({ title: '打开失败', icon: 'none' })
      }
    })
  } catch (error) {
    uni.showToast({ title: '下载失败', icon: 'none' })
  }
}

// 重新向量化
const handleRevectorize = async (item: Knowledgebase) => {
  try {
    await revectorizeKnowledgebase(item.id)
    uni.showToast({ title: '已发起重新向量化', icon: 'success' })
    loadData(true)
  } catch (error) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

// 开始编辑分类
const startEditCategory = (item: Knowledgebase) => {
  editingCategoryId.value = item.id
  editingCategoryValue.value = item.category || ''
}

// 取消编辑分类
const cancelEditCategory = () => {
  editingCategoryId.value = null
  editingCategoryValue.value = ''
}

// 保存分类
const saveCategory = async (id: number) => {
  savingCategory.value = true
  try {
    await updateKnowledgebaseCategory(id, editingCategoryValue.value.trim() || null)
    editingCategoryId.value = null
    editingCategoryValue.value = ''
    loadData(true)
  } catch (error) {
    uni.showToast({ title: '更新分类失败', icon: 'none' })
  } finally {
    savingCategory.value = false
  }
}

// 排序变更
const handleSortChange = (e: any) => {
  sortBy.value = e.detail.value as SortOption
  searchKeyword.value = ''
  selectedCategory.value = null
  loadData(true)
}

// 分类变更
const handleCategoryChange = (e: any) => {
  selectedCategory.value = e.detail.value || null
  searchKeyword.value = ''
  loadData(true)
}

// 统计卡片颜色
const getStatColor = (index: number): string => {
  const colors = ['#3B82F6', '#10B981', '#8B5CF6']
  return colors[index % colors.length]
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <view class="knowledge-list-container">
    <!-- 顶部搜索和标题 -->
    <view class="header">
      <view class="header-content">
        <view class="header-top">
          <text class="page-title">知识库管理</text>
        </view>
        <view class="search-bar">
          <Icon name="search" :size="18" color="rgba(255,255,255,0.75)" />
          <input
            v-model="searchKeyword"
            placeholder="搜索知识库..."
            @confirm="handleSearch"
          />
          <Icon v-if="searchKeyword" name="x" :size="16" color="rgba(255,255,255,0.75)" @click="searchKeyword = ''; handleSearch()" />
        </view>
      </view>
    </view>

    <!-- 统计卡片 -->
    <view v-if="stats" class="stats-section">
      <view class="stat-card">
        <view class="stat-icon" :style="{ background: getStatColor(0) }">
          <Icon name="database" :size="32" color="#fff" />
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ stats.totalCount }}</text>
          <text class="stat-label">知识库总数</text>
        </view>
      </view>
      <view class="stat-card">
        <view class="stat-icon" :style="{ background: getStatColor(1) }">
          <Icon name="eye" :size="32" color="#fff" />
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ stats.totalAccessCount }}</text>
          <text class="stat-label">总访问次数</text>
        </view>
      </view>
      <view class="stat-card">
        <view class="stat-icon" :style="{ background: getStatColor(2) }">
          <Icon name="harddrive" :size="32" color="#fff" />
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ formatFileSize(stats.totalStorageSize) }}</text>
          <text class="stat-label">总存储大小</text>
        </view>
      </view>
    </view>

    <!-- 筛选栏 -->
    <view class="filter-section">
      <picker mode="selector" :range="['按时间', '按大小', '按访问']" :value="['time', 'size', 'access'].indexOf(sortBy)" @change="handleSortChange">
        <view class="filter-item">
          <text class="filter-label">排序</text>
          <text class="filter-value">
            {{ { time: '按时间', size: '按大小', access: '按访问' }[sortBy] }}
          </text>
          <Icon name="chevron-down" :size="14" color="#94a3b8" />
        </view>
      </picker>

      <picker mode="selector" :range="['全部分类', ...categories]" :value="selectedCategory ? ['', ...categories].indexOf(selectedCategory) : 0" @change="handleCategoryChange">
        <view class="filter-item">
          <text class="filter-label">分类</text>
          <text class="filter-value">
            {{ selectedCategory || '全部分类' }}
          </text>
          <Icon name="chevron-down" :size="14" color="#94a3b8" />
        </view>
      </picker>
    </view>

    <!-- 知识库列表 -->
    <scroll-view
      class="knowledge-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="knowledgebaseList.length === 0 && !loading" class="empty">
        <Icon name="folder" :size="80" color="#e2e8f0" />
        <text class="empty-text">暂无知识库</text>
        <text class="empty-desc">点击下方按钮创建您的第一个知识库</text>
      </view>

      <view
        v-for="item in knowledgebaseList"
        :key="item.id"
        class="knowledge-card"
      >
        <!-- 选择框 -->
        <view class="card-checkbox" @click.stop="toggleKbSelection(item.id)">
          <Icon
            :name="selectedKbIds.has(item.id) ? 'check-square' : 'square'"
            :size="22"
            :color="selectedKbIds.has(item.id) ? '#3B82F6' : '#94a3b8'"
          />
        </view>

        <!-- 卡片主体 -->
        <view class="card-main" @click="toggleKbSelection(item.id)">
          <view class="card-header">
            <view class="card-icon">
              <Icon name="file-text" :size="32" color="#fff" />
            </view>
            <view class="card-info">
              <text class="card-name">{{ item.name }}</text>
              <text class="card-filename">{{ item.originalFilename }}</text>
            </view>
            <view
              class="status-badge"
              :style="{ background: statusMap[item.vectorStatus]?.bgColor, color: statusMap[item.vectorStatus]?.color }"
            >
              {{ statusMap[item.vectorStatus]?.text }}
            </view>
          </view>

          <view class="card-meta">
            <view class="meta-item">
              <text class="meta-label">大小</text>
              <text class="meta-value">{{ formatFileSize(item.fileSize) }}</text>
            </view>
            <view class="meta-item">
              <text class="meta-label">访问</text>
              <text class="meta-value">{{ item.accessCount }}</text>
            </view>
            <view class="meta-item">
              <text class="meta-label">分块</text>
              <text class="meta-value">{{ item.chunkCount }}</text>
            </view>
          </view>

          <!-- 分类编辑 -->
          <view class="card-category">
            <template v-if="editingCategoryId === item.id">
              <input
                v-model="editingCategoryValue"
                class="category-input"
                placeholder="输入分类名称"
                @click.stop
              />
              <view class="category-actions">
                <text class="category-btn confirm" @click.stop="saveCategory(item.id)">✓</text>
                <text class="category-btn cancel" @click.stop="cancelEditCategory">✕</text>
              </view>
            </template>
            <template v-else>
              <text class="category-tag" :class="{ empty: !item.category }" @click.stop="startEditCategory(item)">
                {{ item.category || '未分类' }}
              </text>
            </template>
          </view>
        </view>

        <!-- 操作按钮 -->
        <view class="card-actions" @click.stop>
          <view class="action-btn" @click="handleDownload(item)">
            <text>下载</text>
          </view>
          <view v-if="item.vectorStatus === 'FAILED'" class="action-btn" @click="handleRevectorize(item)">
            <text>重新向量化</text>
          </view>
          <view class="action-btn danger" @click.stop="handleDelete(item)">
            <text>删除</text>
          </view>
        </view>

        <view class="card-footer">
          <text class="update-time">{{ formatTimeAgo(item.uploadedAt) }}</text>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-more">
        <text>加载中...</text>
      </view>
    </scroll-view>

    <!-- 创建按钮 -->
    <view class="create-btn" @click="showCreateModal = true">
      <Icon name="plus" :size="24" color="#fff" />
      <text>创建知识库</text>
    </view>

    <!-- 多选底部栏 -->
    <view v-if="showSelectionBar" class="selection-bar">
      <view class="selection-info">
        <text class="selection-count">已选择 {{ selectedKbIds.size }} 个知识库</text>
        <text class="selection-clear" @click="clearSelection">清除</text>
      </view>
      <view class="selection-actions">
        <view class="selection-btn secondary" @click="toggleSelectAll">
          <text>{{ selectedKbIds.size === knowledgebaseList.length ? '取消全选' : '全选' }}</text>
        </view>
        <view class="selection-btn primary" @click="goToChat">
          <text>开始问答</text>
        </view>
      </view>
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
            <text class="form-label">分类（可选）</text>
            <input
              v-model="kbCategory"
              class="form-input"
              placeholder="请输入分类名称"
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

    <!-- 删除确认弹窗 -->
    <view v-if="showDeleteModal" class="modal-mask" @click="showDeleteModal = false">
      <view class="modal-content delete-modal" @click.stop>
        <view class="modal-header">
          <text class="modal-title">确认删除</text>
        </view>
        <view class="modal-body">
          <text class="delete-message">确定要删除知识库「{{ deleteItem?.name }}」吗？此操作不可恢复。</text>
        </view>
        <view class="modal-footer">
          <view class="btn-cancel" @click="showDeleteModal = false">取消</view>
          <view class="btn-confirm danger" :class="{ loading: deleteLoading }" @click="confirmDelete">
            <text>{{ deleteLoading ? '删除中...' : '确认删除' }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

.knowledge-list-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: $bg;
}

.header {
  background: linear-gradient(135deg, $primary 0%, $primary-dark 50%, $primary-light 100%);
  padding: 48rpx 40rpx 80rpx;
  position: relative;
  overflow: hidden;

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

.header-content {
  position: relative;
  z-index: 1;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.page-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
}

.search-bar {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 40rpx;
  backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.12);

  .icon-search {
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

  .clear-btn {
    font-size: 28rpx;
    color: rgba(255, 255, 255, 0.75);
    padding: 8rpx;
  }
}

// 统计卡片
.stats-section {
  display: flex;
  gap: 24rpx;
  padding: 0 32rpx;
  margin-top: -60rpx;
  margin-bottom: 32rpx;
  position: relative;
  z-index: 10;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
}

.stat-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: #fff;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
}

.stat-label {
  font-size: 22rpx;
  color: $text-muted;
  margin-top: 4rpx;
}

// 筛选栏
.filter-section {
  display: flex;
  padding: 0 32rpx;
  margin-bottom: 24rpx;
  gap: 24rpx;
}

.filter-item {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 20rpx 28rpx;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .filter-label {
    font-size: 24rpx;
    color: $text-muted;
    margin-right: 12rpx;
  }

  .filter-value {
    font-size: 26rpx;
    color: $text-primary;
    font-weight: 500;
  }

  .filter-arrow {
    font-size: 20rpx;
    color: $text-muted;
    margin-left: 12rpx;
  }
}

// 知识库列表
.knowledge-list {
  flex: 1;
  padding: 0 32rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  :deep(.icon) {
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
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.card-main {
  margin-bottom: 20rpx;
}

.card-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.card-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, $warning, #fbbf24);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: #fff;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-filename {
  display: block;
  font-size: 24rpx;
  color: $text-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-badge {
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 500;
  flex-shrink: 0;
}

.card-meta {
  display: flex;
  padding: 20rpx 0;
  border-top: 1rpx solid #f1f5f9;
  border-bottom: 1rpx solid #f1f5f9;
}

.meta-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:first-child {
    border-right: 1rpx solid #f1f5f9;
  }

  &:last-child {
    border-left: 1rpx solid #f1f5f9;
  }
}

.meta-label {
  font-size: 22rpx;
  color: $text-muted;
  margin-bottom: 6rpx;
}

.meta-value {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
}

.card-category {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  gap: 16rpx;
}

.category-input {
  flex: 1;
  height: 64rpx;
  background: #f8fafc;
  border: 2rpx solid $primary;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}

.category-actions {
  display: flex;
  gap: 12rpx;
}

.category-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;

  &.confirm {
    background: $success;
    color: #fff;
  }

  &.cancel {
    background: #f1f5f9;
    color: $text-muted;
  }
}

.category-tag {
  display: inline-block;
  padding: 8rpx 20rpx;
  background: rgba($primary, 0.08);
  color: $primary;
  border-radius: 8rpx;
  font-size: 24rpx;

  &.empty {
    background: #f1f5f9;
    color: $text-muted;
  }
}

.card-actions {
  display: flex;
  gap: 16rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f1f5f9;
}

.action-btn {
  flex: 1;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  border-radius: 12rpx;
  font-size: 24rpx;
  color: $text-secondary;

  &.danger {
    background: rgba($danger, 0.08);
    color: $danger;
  }
}

.card-footer {
  margin-top: 16rpx;
}

.update-time {
  font-size: 22rpx;
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
  width: 280rpx;
  height: 88rpx;
  background: linear-gradient(135deg, $primary, $primary-light);
  border-radius: 44rpx;
  box-shadow: 0 8rpx 32rpx rgba($primary, 0.35);
  color: white;
  font-size: 30rpx;
  font-weight: 600;

  .icon-add {
    font-size: 36rpx;
    font-weight: 700;
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

  &.delete-modal {
    .modal-body {
      padding: 40rpx 32rpx;
    }
  }
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

.delete-message {
  font-size: 28rpx;
  color: $text-secondary;
  line-height: 1.6;
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

  &.danger {
    color: $danger;
  }

  &.loading {
    opacity: 0.6;
  }
}

// 选择框样式
.card-checkbox {
  position: absolute;
  left: 32rpx;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  padding: 16rpx 0;
}

// 知识库卡片（多选模式）
.knowledge-card {
  position: relative;
  padding-left: 80rpx;
}

// 多选底部栏
.selection-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.08);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.selection-info {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.selection-count {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
}

.selection-clear {
  font-size: 26rpx;
  color: $text-muted;
  padding: 8rpx 16rpx;
}

.selection-actions {
  display: flex;
  gap: 16rpx;
}

.selection-btn {
  height: 72rpx;
  padding: 0 32rpx;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 500;

  &.secondary {
    background: #f1f5f9;
    color: $text-secondary;
  }

  &.primary {
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    box-shadow: 0 4rpx 16rpx rgba($primary, 0.3);
  }
}

// 卡片选中状态
.knowledge-card.selected {
  .card-main {
    background: rgba($primary, 0.04);
    border-color: $primary;
  }
}
</style>
