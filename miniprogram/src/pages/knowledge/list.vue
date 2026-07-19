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

// 知识库卡片图标颜色（按向量化状态）
const statusIconColor = (status: VectorStatus): string => {
  return statusMap[status]?.color || '#0ea5e9'
}

onMounted(() => {
  loadData()
})
</script>


<template>
  <view class="kb-container">
    <!-- 顶部品牌区：标题 + 统计数字 + 搜索 -->
    <view class="kb-header">
      <view class="header-row">
        <view class="header-left">
          <text class="page-title">知识库</text>
          <view v-if="stats" class="header-stats">
            <text class="hs-num">{{ stats.totalCount }}</text>
            <text class="hs-label">个库</text>
            <text class="hs-sep">·</text>
            <text class="hs-num">{{ stats.totalAccessCount }}</text>
            <text class="hs-label">次访问</text>
            <text class="hs-sep">·</text>
            <text class="hs-num">{{ formatFileSize(stats.totalStorageSize) }}</text>
          </view>
        </view>
      </view>
      <view class="search-bar">
        <view class="search-icon-wrap"><Icon name="search" :size="16" color="#94a3b8" /></view>
        <input
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索知识库"
          placeholder-class="search-ph"
          @confirm="handleSearch"
        />
        <view v-if="searchKeyword" class="search-clear" @click="searchKeyword = ''; handleSearch()">
          <Icon name="x" :size="14" color="#94a3b8" />
        </view>
      </view>
    </view>

    <!-- 筛选栏：紧凑的胶囊筛选 -->
    <view class="filter-bar">
      <picker mode="selector" :range="['按时间', '按大小', '按访问']" :value="['time', 'size', 'access'].indexOf(sortBy)" @change="handleSortChange">
        <view class="chip">
          <Icon name="filter" :size="12" color="#64748b" />
          <text class="chip-text">{{ { time: '按时间', size: '按大小', access: '按访问' }[sortBy] }}</text>
          <Icon name="chevron-down" :size="12" color="#94a3b8" />
        </view>
      </picker>

      <picker mode="selector" :range="['全部分类', ...categories]" :value="selectedCategory ? ['', ...categories].indexOf(selectedCategory) : 0" @change="handleCategoryChange">
        <view class="chip" :class="{ active: !!selectedCategory }">
          <Icon name="tag" :size="12" color="#64748b" />
          <text class="chip-text">{{ selectedCategory || '全部分类' }}</text>
          <Icon name="chevron-down" :size="12" color="#94a3b8" />
        </view>
      </picker>
    </view>

    <!-- 知识库列表 -->
    <scroll-view
      class="kb-list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="knowledgebaseList.length === 0 && !loading" class="empty">
        <view class="empty-icon"><Icon name="folder" :size="56" color="#cbd5e1" /></view>
        <text class="empty-text">暂无知识库</text>
        <text class="empty-desc">点击右下角按钮创建第一个知识库</text>
      </view>

      <view
        v-for="item in knowledgebaseList"
        :key="item.id"
        class="kb-card"
        :class="{ selected: selectedKbIds.has(item.id) }"
        @click="toggleKbSelection(item.id)"
      >
        <!-- 顶部：图标 + 名称 + 状态 + 操作 -->
        <view class="card-top">
          <view class="card-icon" :class="'status-' + item.vectorStatus?.toLowerCase()">
            <Icon name="file-text" :size="22" :color="statusIconColor(item.vectorStatus)" />
          </view>
          <view class="card-info">
            <text class="card-name">{{ item.name }}</text>
            <text class="card-filename">{{ item.originalFilename }}</text>
          </view>
          <view
            class="status-pill"
            :style="{ background: statusMap[item.vectorStatus]?.bgColor, color: statusMap[item.vectorStatus]?.color }"
          >
            {{ statusMap[item.vectorStatus]?.text }}
          </view>
        </view>

        <!-- meta 行：大小 · 访问 · 分块 · 时间，单行小字 -->
        <view class="card-meta">
          <text class="meta-text">{{ formatFileSize(item.fileSize) }}</text>
          <text class="meta-dot">·</text>
          <text class="meta-text">{{ item.accessCount }} 次访问</text>
          <text class="meta-dot">·</text>
          <text class="meta-text">{{ item.chunkCount }} 分块</text>
          <text class="meta-dot">·</text>
          <text class="meta-text">{{ formatTimeAgo(item.uploadedAt) }}</text>
        </view>

        <!-- 分类 + 操作 -->
        <view class="card-bottom">
          <!-- 分类编辑 -->
          <view class="category-area" @click.stop>
            <template v-if="editingCategoryId === item.id">
              <input
                v-model="editingCategoryValue"
                class="category-input"
                placeholder="分类名"
                @click.stop
              />
              <view class="cat-act confirm" @click.stop="saveCategory(item.id)"><Icon name="check" :size="12" color="#fff" /></view>
              <view class="cat-act cancel" @click.stop="cancelEditCategory"><Icon name="x" :size="12" color="#64748b" /></view>
            </template>
            <template v-else>
              <text class="cat-tag" :class="{ empty: !item.category }" @click.stop="startEditCategory(item)">
                <Icon name="tag" :size="11" :color="item.category ? '#0ea5e9' : '#94a3b8'" />
                {{ item.category || '未分类' }}
              </text>
            </template>
          </view>

          <!-- 操作图标 -->
          <view class="card-actions" @click.stop>
            <view class="icon-action" @click="handleDownload(item)">
              <Icon name="download" :size="16" color="#64748b" />
            </view>
            <view v-if="item.vectorStatus === 'FAILED'" class="icon-action" @click="handleRevectorize(item)">
              <Icon name="rotate-cw" :size="16" color="#f59e0b" />
            </view>
            <view class="icon-action danger" @click.stop="handleDelete(item)">
              <Icon name="trash" :size="16" color="#ef4444" />
            </view>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading-more">
        <text>加载中...</text>
      </view>
    </scroll-view>

    <!-- 悬浮创建按钮（FAB） -->
    <view class="fab" @click="showCreateModal = true">
      <Icon name="plus" :size="24" color="#fff" />
    </view>

    <!-- 多选底部栏 -->
    <view v-if="showSelectionBar" class="selection-bar">
      <view class="sel-info">
        <text class="sel-count">已选 {{ selectedKbIds.size }} 个</text>
        <text class="sel-clear" @click="clearSelection">清除</text>
      </view>
      <view class="sel-actions">
        <view class="sel-btn ghost" @click="toggleSelectAll">
          <text>{{ selectedKbIds.size === knowledgebaseList.length ? '取消全选' : '全选' }}</text>
        </view>
        <view class="sel-btn primary" @click="goToChat">
          <Icon name="message" :size="14" color="#fff" />
          <text>开始问答</text>
        </view>
      </view>
    </view>

    <!-- 创建知识库弹窗 -->
    <view v-if="showCreateModal" class="modal-mask" @click="cancelCreate">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">创建知识库</text>
          <view class="modal-close" @click="cancelCreate"><Icon name="x" :size="18" color="#94a3b8" /></view>
        </view>

        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">知识库名称 *</text>
            <input
              v-model="kbName"
              class="form-input"
              placeholder="请输入知识库名称"
              placeholder-class="form-ph"
            />
          </view>

          <view class="form-item">
            <text class="form-label">分类（可选）</text>
            <input
              v-model="kbCategory"
              class="form-input"
              placeholder="请输入分类名称"
              placeholder-class="form-ph"
            />
          </view>

          <view class="form-item">
            <text class="form-label">上传文档 *</text>
            <view class="file-upload" @click="chooseFile">
              <view class="fu-icon"><Icon name="upload" :size="20" color="#0ea5e9" /></view>
              <text v-if="selectedFileName" class="file-name">{{ selectedFileName }}</text>
              <text v-else class="file-ph">点击选择文件（PDF/DOC/DOCX/TXT/MD）</text>
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
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">确认删除</text>
        </view>
        <view class="modal-body">
          <text class="delete-msg">确定要删除「{{ deleteItem?.name }}」吗？此操作不可恢复。</text>
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

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.kb-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $bg;
}

// ===== 顶部品牌区 =====
.kb-header {
  flex-shrink: 0;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  padding: calc(env(safe-area-inset-top, 0px) + 48rpx) 32rpx 32rpx;
}

.header-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.page-title {
  font-size: 44rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1rpx;
}

.header-stats {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
  margin-top: 12rpx;
  flex-wrap: wrap;
}

.hs-num {
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
}

.hs-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.75);
}

.hs-sep {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.4);
  margin: 0 4rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  padding: 0 20rpx;
  height: 76rpx;
}

.search-icon-wrap {
  display: flex;
  align-items: center;
  margin-right: 12rpx;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
}

.search-ph {
  color: $text-muted;
}

.search-clear {
  padding: 8rpx;
  display: flex;
  align-items: center;
}

// ===== 筛选栏 =====
.filter-bar {
  flex-shrink: 0;
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx 8rpx;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  background: $card-bg;
  border-radius: 999rpx;
  padding: 12rpx 24rpx;
  box-shadow: 0 1rpx 8rpx rgba(15, 23, 42, 0.04);

  &.active {
    background: rgba($primary, 0.1);
  }
}

.chip-text {
  font-size: 24rpx;
  color: $text-secondary;
  font-weight: 500;
}

// ===== 列表 =====
.kb-list {
  flex: 1;
  padding: 16rpx 32rpx 32rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 140rpx 0;
}

.empty-icon {
  width: 112rpx;
  height: 112rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}

.empty-text {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 8rpx;
}

.empty-desc {
  font-size: 24rpx;
  color: $text-muted;
}

// ===== 知识库卡片 =====
.kb-card {
  background: $card-bg;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(15, 23, 42, 0.04);
  border: 2rpx solid transparent;
  transition: border-color 0.15s;

  &.selected {
    border-color: $primary;
    background: rgba($primary, 0.02);
  }
}

.card-top {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.card-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #f0f9ff;

  &.status-failed { background: #fef2f2; }
  &.status-processing { background: #eff6ff; }
  &.status-pending { background: #fffbeb; }
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
  margin-bottom: 4rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card-filename {
  display: block;
  font-size: 22rpx;
  color: $text-muted;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.status-pill {
  flex-shrink: 0;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 500;
}

.card-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f1f5f9;
}

.meta-text {
  font-size: 22rpx;
  color: $text-muted;
}

.meta-dot {
  font-size: 20rpx;
  color: #cbd5e1;
  margin: 0 6rpx;
}

.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  gap: 12rpx;
}

.category-area {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.category-input {
  flex: 1;
  height: 52rpx;
  background: $bg;
  border: 2rpx solid $primary;
  border-radius: 10rpx;
  padding: 0 16rpx;
  font-size: 24rpx;
  min-width: 0;
}

.cat-act {
  width: 52rpx;
  height: 52rpx;
  border-radius: 10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &.confirm { background: $primary; }
  &.cancel { background: #f1f5f9; }
}

.cat-tag {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 16rpx;
  background: rgba($primary, 0.08);
  color: $primary;
  border-radius: 999rpx;
  font-size: 22rpx;

  &.empty {
    background: #f1f5f9;
    color: $text-muted;
  }
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.icon-action {
  width: 60rpx;
  height: 60rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg;

  &:active {
    background: #e2e8f0;
  }
}

.loading-more {
  text-align: center;
  padding: 24rpx;
  font-size: 24rpx;
  color: $text-muted;
}

// ===== FAB =====
.fab {
  position: fixed;
  right: 40rpx;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 48rpx);
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba($primary, 0.4);
  z-index: 100;

  &:active {
    transform: scale(0.95);
  }
}

// ===== 多选栏 =====
.selection-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 32rpx calc(env(safe-area-inset-bottom, 0px) + 20rpx);
  background: $card-bg;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 99;
}

.sel-info {
  display: flex;
  flex-direction: column;
}

.sel-count {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-primary;
}

.sel-clear {
  font-size: 22rpx;
  color: $text-muted;
  margin-top: 2rpx;
}

.sel-actions {
  display: flex;
  gap: 16rpx;
}

.sel-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 28rpx;
  border-radius: 12rpx;
  font-size: 26rpx;
  font-weight: 500;

  &.ghost {
    background: $bg;
    color: $text-secondary;
  }

  &.primary {
    background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
    color: #fff;
  }
}

// ===== 弹窗 =====
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 48rpx;
}

.modal-content {
  width: 100%;
  max-width: 600rpx;
  background: $card-bg;
  border-radius: 24rpx;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 32rpx 16rpx;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
}

.modal-close {
  padding: 8rpx;
  display: flex;
  align-items: center;
}

.modal-body {
  padding: 16rpx 32rpx;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  display: block;
  font-size: 24rpx;
  color: $text-secondary;
  margin-bottom: 12rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  background: $bg;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: $text-primary;
  box-sizing: border-box;
}

.form-ph {
  color: $text-muted;
}

.file-upload {
  width: 100%;
  min-height: 80rpx;
  padding: 20rpx 24rpx;
  background: $bg;
  border: 2rpx dashed #cbd5e1;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  box-sizing: border-box;
}

.fu-icon {
  display: flex;
  align-items: center;
}

.file-name {
  font-size: 26rpx;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.file-ph {
  font-size: 26rpx;
  color: $text-muted;
}

.delete-msg {
  font-size: 28rpx;
  color: $text-secondary;
  line-height: 1.6;
}

.modal-footer {
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx 32rpx;
}

.btn-cancel,
.btn-confirm {
  flex: 1;
  height: 84rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 500;
}

.btn-cancel {
  background: $bg;
  color: $text-secondary;
}

.btn-confirm {
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  color: #fff;

  &.danger {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  }

  &.loading {
    opacity: 0.7;
  }
}
</style>
