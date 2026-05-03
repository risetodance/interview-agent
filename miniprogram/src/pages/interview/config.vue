<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getResumeDetail } from '../../api/resume'
import { createSession, getPerspectives, type InterviewerRoleDTO } from '../../api/interview'

// 题目数量选项
const questionCountOptions = [
  { value: 6, label: '6 题' },
  { value: 8, label: '8 题' },
  { value: 10, label: '10 题' },
  { value: 12, label: '12 题' },
  { value: 15, label: '15 题' }
]

// 简历数据
const resumeId = ref<number>(0)
const resumeName = ref('')
const resumePreview = ref('')
const resumeText = ref('')
const selectedQuestionCount = ref(8)
const loading = ref(false)
const isCreating = ref(false)

// 视角选择相关
const perspectives = ref<InterviewerRoleDTO[]>([])
const selectedPerspectiveIds = ref<number[]>([])
const perspectivesLoading = ref(false)
const MAX_PERSPECTIVES = 3

// 各视角权重配置，key 为视角ID，value 为权重值
const perspectiveWeights = ref<Record<string, number>>({})

// 视角图标映射
const perspectiveIcons: Record<string, string> = {
  'TECH_INTERVIEWER': '💻',
  'HR_INTERVIEWER': '👔',
  'TECH_DIRECTOR': '📋'
}

// 计算已选择的视角详情
const selectedPerspectivesDetail = computed(() => {
  return perspectives.value.filter(p => selectedPerspectiveIds.value.includes(p.id))
})

// 计算总权重（基于用户配置的权重）
const totalWeight = computed(() => {
  return selectedPerspectivesDetail.value.reduce((sum, p) => {
    const weight = perspectiveWeights.value[String(p.id)] ?? p.weight
    return sum + weight
  }, 0)
})

// 权重是否有效（总和为100%，容差0.01）
const isWeightValid = computed(() => {
  return Math.abs(totalWeight.value - 1) < 0.01
})

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
    const result: any = await getResumeDetail(resumeId.value)
    resumeName.value = result.name || result.fileName || '简历'
    resumeText.value = result.resumeText || ''
    resumePreview.value = resumeText.value.length > 500 ? resumeText.value.substring(0, 500) + '...' : resumeText.value
  } catch (error) {
    resumeName.value = '简历详情'
    resumePreview.value = '简历内容加载失败'
  } finally {
    loading.value = false
  }
}

// 加载可选视角列表
const loadPerspectives = async () => {
  perspectivesLoading.value = true
  try {
    const result: any = await getPerspectives()
    // 后端返回数组直接使用
    perspectives.value = Array.isArray(result) ? result : (result.list || [])

    // 自动选中所有 status=true 且 defaultTemplate=true 的视角（最多3个）
    // 初始化权重时，按各角色默认权重的比例归一化，使总和为1（与前端行为一致）
    const enabledRoles = perspectives.value.filter(p => p.status)
    const defaultRoleIds = enabledRoles.slice(0, MAX_PERSPECTIVES).map(p => p.id)
    selectedPerspectiveIds.value = defaultRoleIds

    // 初始化权重：按比例归一化，使总和为1
    if (defaultRoleIds.length > 0) {
      const totalDefaultWeight = enabledRoles.reduce((sum, r) => sum + r.weight, 0)
      enabledRoles.forEach(role => {
        perspectiveWeights.value[String(role.id)] = totalDefaultWeight > 0
          ? role.weight / totalDefaultWeight
          : 1 / defaultRoleIds.length
      })
    }
  } catch (error) {
    // 使用默认视角
    perspectives.value = [
      { id: 1, roleName: '技术面试官', roleCode: 'TECH_INTERVIEWER', weight: 0.4, description: '重点考察技术能力和项目经验', status: true, icon: 'code' },
      { id: 2, roleName: 'HR面试官', roleCode: 'HR_INTERVIEWER', weight: 0.3, description: '重点考察综合素质和沟通能力', status: true, icon: 'user' },
      { id: 3, roleName: '技术总监', roleCode: 'TECH_DIRECTOR', weight: 0.3, description: '重点考察架构思维和团队协作', status: true, icon: 'admin' }
    ]
    // 默认选中所有视角，并归一化权重
    selectedPerspectiveIds.value = [1, 2, 3]
    perspectiveWeights.value = { '1': 0.4, '2': 0.3, '3': 0.3 }
  } finally {
    perspectivesLoading.value = false
  }
}

// 切换视角选择
const togglePerspective = (id: number) => {
  const index = selectedPerspectiveIds.value.indexOf(id)
  if (index > -1) {
    // 取消选择，不调整其他视角的权重（与前端行为一致）
    selectedPerspectiveIds.value.splice(index, 1)
    // 清理已移除视角的权重
    delete perspectiveWeights.value[String(id)]
  } else {
    // 选中（最多3个）
    if (selectedPerspectiveIds.value.length < MAX_PERSPECTIVES) {
      const perspective = perspectives.value.find(p => p.id === id)
      const defaultWeight = perspective ? perspective.weight : 0
      selectedPerspectiveIds.value.push(id)
      perspectiveWeights.value[String(id)] = defaultWeight
    } else {
      uni.showToast({
        title: `最多只能选择${MAX_PERSPECTIVES}个视角`,
        icon: 'none'
      })
    }
  }
}

// 检查视角是否选中
const isPerspectiveSelected = (id: number) => {
  return selectedPerspectiveIds.value.includes(id)
}

// 格式化学权重
const formatWeight = (weight: number) => {
  return `${Math.round(weight * 100)}%`
}

// 验证权重总和（前端要求总和必须为100%才允许开始面试）
const weightError = ref<string | null>(null)

// 验证权重并在需要时自动调整
const validateAndAdjustWeights = () => {
  if (selectedPerspectiveIds.value.length === 0) {
    weightError.value = null
    return
  }

  const total = totalWeight.value
  if (Math.abs(total - 1) < 0.01) {
    weightError.value = null
  } else {
    weightError.value = `权重总和需为100%（当前${Math.round(total * 100)}%）`
  }
}

// 调整视角权重
const onWeightChange = (id: number, newWeight: number) => {
  // 只更新当前视角的权重，不自动调整其他视角
  perspectiveWeights.value[String(id)] = newWeight
  validateAndAdjustWeights()
}

// 开始面试
const startInterview = async () => {
  if (isCreating.value) return

  if (selectedPerspectiveIds.value.length === 0) {
    uni.showToast({
      title: '请至少选择一个视角',
      icon: 'none'
    })
    return
  }

  // 验证并检查权重总和
  const weightSum = totalWeight.value
  if (weightSum < 0.99 || weightSum > 1.01) {
    uni.showToast({
      title: '权重总和需为100%',
      icon: 'none'
    })
    return
  }

  // 再次验证
  validateAndAdjustWeights()
  if (weightError.value) {
    uni.showToast({
      title: weightError.value,
      icon: 'none'
    })
    return
  }

  isCreating.value = true
  try {
    // 构建 perspectiveWeights 参数（只传选中的视角权重）
    const weights: Record<string, number> = {}
    selectedPerspectiveIds.value.forEach(id => {
      weights[String(id)] = perspectiveWeights.value[String(id)] ?? 0
    })

    // 调用创建会话接口，传递选中的视角和权重配置
    const session = await createSession({
      resumeText: resumeText.value,
      questionCount: selectedQuestionCount.value,
      resumeId: resumeId.value,
      selectedPerspectives: selectedPerspectiveIds.value,
      perspectiveWeights: weights
    })

    // 创建成功，跳转到会话页面
    uni.redirectTo({
      url: `/pages/interview/session?id=${session.sessionId}&total=${session.totalQuestions}`
    })
  } catch (error) {
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

  // 加载可选视角
  loadPerspectives()
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

    <!-- 视角选择区域 -->
    <view class="config-section">
      <view class="section-title">
        选择面试官视角
        <text class="section-hint">（可多选，最多{{ MAX_PERSPECTIVES }}个）</text>
      </view>

      <!-- 加载状态 -->
      <view v-if="perspectivesLoading" class="perspectives-loading">
        <text>加载视角中...</text>
      </view>

      <!-- 视角卡片列表 -->
      <view v-else class="perspectives-grid">
        <view
          v-for="perspective in perspectives"
          :key="perspective.id"
          class="perspective-card"
          :class="{ selected: isPerspectiveSelected(perspective.id) }"
          @click="togglePerspective(perspective.id)"
        >
          <view class="perspective-header">
            <text class="perspective-icon">{{ perspectiveIcons[perspective.roleCode] || '👤' }}</text>
            <view class="perspective-check" v-if="isPerspectiveSelected(perspective.id)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
            </view>
          </view>
          <text class="perspective-name">{{ perspective.roleName }}</text>
          <text class="perspective-weight">权重: {{ formatWeight(isPerspectiveSelected(perspective.id) ? (perspectiveWeights[String(perspective.id)] || perspective.weight) : perspective.weight) }}</text>
          <text class="perspective-desc">{{ perspective.description }}</text>
          <!-- 权重滑块（选中时显示） -->
          <view v-if="isPerspectiveSelected(perspective.id)" class="weight-slider-wrapper" @click.stop>
            <text class="weight-slider-label">调整权重：</text>
            <slider
              class="weight-slider"
              :value="Math.round((perspectiveWeights[String(perspective.id)] || perspective.weight) * 100)"
              :min="5"
              :max="90"
              :step="5"
              activeColor="#0ea5e9"
              backgroundColor="#e5e7eb"
              block-size="18"
              @change="(e: any) => onWeightChange(perspective.id, e.detail.value / 100)"
            />
            <text class="weight-value">{{ formatWeight(perspectiveWeights[String(perspective.id)] || perspective.weight) }}</text>
          </view>
        </view>
      </view>

      <!-- 已选择视角汇总 -->
      <view v-if="selectedPerspectivesDetail.length > 0" class="selected-summary">
        <text class="summary-label">已选择：</text>
        <text class="summary-text">
          {{ selectedPerspectivesDetail.map(p => p.roleName).join('、') }}
        </text>
        <view class="weight-status">
          <text class="weight-total" :class="{ valid: isWeightValid, invalid: !isWeightValid }">
            总权重 {{ formatWeight(totalWeight) }}
          </text>
          <text v-if="!isWeightValid" class="weight-hint">
            {{ totalWeight < 1 ? '权重不足' : '权重超出' }}
          </text>
        </view>
      </view>

      <!-- 权重错误提示 -->
      <view v-if="weightError" class="weight-error">
        <text>⚠️ {{ weightError }}</text>
      </view>
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
      <view class="btn btn-start" :class="{ disabled: isCreating || selectedPerspectiveIds.length === 0 || !isWeightValid }" @click="startInterview">
        <text v-if="isCreating">创建中...</text>
        <text v-else-if="selectedPerspectiveIds.length === 0">请选择视角</text>
        <text v-else-if="!isWeightValid">权重不足</text>
        <text v-else>开始面试 →</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

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
  position: relative;
  z-index: 1;

  .section-title {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 30rpx;
    padding-left: 20rpx;
    border-left: 6rpx solid $primary-color;
  }

  .section-hint {
    font-size: 24rpx;
    font-weight: 400;
    color: #999;
    margin-left: 12rpx;
  }
}

.perspectives-loading {
  padding: 40rpx;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}

.perspectives-grid {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.perspective-card {
  display: flex;
  flex-direction: column;
  padding: 24rpx;
  background-color: #f9fafb;
  border-radius: 16rpx;
  border: 4rpx solid transparent;
  transition: all 0.3s;

  &.selected {
    background-color: #f8f7ff;
    border-color: $primary-color;
  }

  .perspective-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12rpx;
  }

  .perspective-icon {
    font-size: 40rpx;
  }

  .perspective-check {
    width: 40rpx;
    height: 40rpx;
    border-radius: 50%;
    background-color: $primary-color;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 20rpx;
      height: 20rpx;
      color: #fff;
    }
  }

  .perspective-name {
    font-size: 30rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 8rpx;
  }

  .perspective-weight {
    font-size: 26rpx;
    color: $primary-color;
    font-weight: 500;
    margin-bottom: 8rpx;
  }

  .perspective-desc {
    font-size: 24rpx;
    color: #666;
    line-height: 1.4;
  }
}

.weight-slider-wrapper {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;

  .weight-slider-label {
    font-size: 22rpx;
    color: #666;
    flex-shrink: 0;
  }

  .weight-slider {
    flex: 1;
    height: 40rpx;
  }

  .weight-value {
    font-size: 24rpx;
    color: $primary-color;
    font-weight: 600;
    flex-shrink: 0;
    min-width: 80rpx;
    text-align: right;
  }
}

.selected-summary {
  margin-top: 24rpx;
  padding: 20rpx;
  background-color: #f0f0f0;
  border-radius: 12rpx;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;

  .summary-label {
    font-size: 26rpx;
    color: #666;
    font-weight: 500;
  }

  .summary-text {
    font-size: 26rpx;
    color: $primary-color;
    font-weight: 600;
  }

  .weight-status {
    display: flex;
    align-items: center;
    gap: 8rpx;
    margin-left: auto;
  }

  .weight-total {
    font-size: 24rpx;
    font-weight: 600;

    &.valid {
      color: #22c55e;
    }

    &.invalid {
      color: #ef4444;
    }
  }

  .weight-hint {
    font-size: 22rpx;
    color: #f59e0b;
  }
}

.weight-error {
  margin-top: 16rpx;
  padding: 16rpx;
  background-color: #fef3c7;
  border: 2rpx solid #fbbf24;
  border-radius: 12rpx;
  color: #92400e;
  font-size: 26rpx;
  text-align: center;
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
  z-index: 100;
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
