<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useInterviewStore, type CreateInterviewParams } from '../../stores/interview'
import { getResumeList, type Resume } from '../../api/resume'
import { getQuestionCategories, type QuestionCategory } from '../../api/interview'

// 面试类型选项
const interviewTypes = [
  {
    value: 'practice',
    label: '练习模式',
    desc: '适合日常练习，无压力',
    icon: '&#xe60a;',
    color: '#6366f1'
  },
  {
    value: 'real',
    label: '真实面试',
    desc: '模拟真实面试场景',
    icon: '&#xe617;',
    color: '#2196f3'
  }
]

// 岗位分类
const positionCategories = [
  { label: '前端开发', positions: ['前端开发工程师', 'React工程师', 'Vue工程师', '移动端工程师'] },
  { label: '后端开发', positions: ['后端开发工程师', 'Java工程师', 'Go工程师', 'Python工程师'] },
  { label: '全栈', positions: ['全栈开发工程师', '技术专家'] },
  { label: '移动端', positions: ['iOS工程师', 'Android工程师', 'Flutter工程师'] },
  { label: '算法', positions: ['算法工程师', 'NLP工程师', 'CV工程师'] },
  { label: '其他', positions: ['产品经理', '测试工程师', '架构师'] }
]

// Store
const interviewStore = useInterviewStore()

// 表单数据
const formData = ref<CreateInterviewParams>({
  title: '',
  type: 'practice',
  position: '',
  company: '',
  duration: 30,
  resumeId: undefined,
  questionCount: 5
})

// 简历列表
const resumeList = ref<Resume[]>([])
const selectedResume = ref<Resume | null>(null)

// 知识库列表（模拟数据）
const knowledgeBaseList = ref([
  { id: 1, name: '前端面试题库', questionCount: 500 },
  { id: 2, name: '算法题库', questionCount: 300 },
  { id: 3, name: '项目经验汇总', questionCount: 200 }
])
const selectedKnowledgeBase = ref<number[]>([])

// 题目分类
const questionCategories = ref<QuestionCategory[]>([])
const selectedCategories = ref<number[]>([])

// UI状态
const loading = ref(false)
const currentStep = ref(1)
const showPositionPicker = ref(false)
const showResumePicker = ref(false)
const showCategoryPicker = ref(false)

// 时长选项
const durationOptions = [
  { value: 15, label: '15 分钟' },
  { value: 30, label: '30 分钟' },
  { value: 45, label: '45 分钟' },
  { value: 60, label: '60 分钟' }
]

// 题数选项
const questionCountOptions = [
  { value: 3, label: '3 题' },
  { value: 5, label: '5 题' },
  { value: 8, label: '8 题' },
  { value: 10, label: '10 题' }
]

// 当前岗位列表
const currentPositionList = computed(() => {
  const category = positionCategories.find(c =>
    c.positions.includes(formData.value.position || '')
  )
  return category?.positions || []
})

// 加载简历列表
const loadResumeList = async () => {
  try {
    const result = await getResumeList({ page: 1, pageSize: 10 })
    resumeList.value = result.list || []
  } catch (error) {
    console.error('加载简历列表失败:', error)
  }
}

// 加载题目分类
const loadQuestionCategories = async () => {
  try {
    const categories = await getQuestionCategories()
    questionCategories.value = categories || []
  } catch (error) {
    console.error('加载题目分类失败:', error)
    // 使用默认分类
    questionCategories.value = [
      { id: 1, name: 'JavaScript', questionCount: 100 },
      { id: 2, name: 'TypeScript', questionCount: 80 },
      { id: 3, name: 'React', questionCount: 60 },
      { id: 4, name: 'Vue', questionCount: 50 },
      { id: 5, name: '计算机基础', questionCount: 120 },
      { id: 6, name: '算法', questionCount: 200 }
    ]
  }
}

// 选择面试类型
const selectType = (type: string) => {
  formData.value.type = type as 'practice' | 'real'
}

// 选择岗位
const selectPosition = (position: string) => {
  formData.value.position = position
  showPositionPicker.value = false
}

// 选择简历
const selectResume = (resume: Resume) => {
  selectedResume.value = resume
  formData.value.resumeId = resume.id
  showResumePicker.value = false
}

// 选择知识库
const toggleKnowledgeBase = (id: number) => {
  const index = selectedKnowledgeBase.value.indexOf(id)
  if (index > -1) {
    selectedKnowledgeBase.value.splice(index, 1)
  } else {
    selectedKnowledgeBase.value.push(id)
  }
}

// 选择题目分类
const toggleCategory = (id: number) => {
  const index = selectedCategories.value.indexOf(id)
  if (index > -1) {
    selectedCategories.value.splice(index, 1)
  } else {
    selectedCategories.value.push(id)
  }
}

// 下一步
const nextStep = () => {
  if (currentStep.value < 3) {
    currentStep.value++
  }
}

// 上一步
const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

// 生成面试标题
const generateTitle = (): string => {
  const typeText = formData.value.type === 'practice' ? '练习' : '面试'
  const positionText = formData.value.position || '通用'
  const date = new Date()
  const dateStr = `${date.getMonth() + 1}-${date.getDate()}`
  return `${positionText}${typeText}-${dateStr}`
}

// 创建面试
const createInterview = async () => {
  if (!formData.value.position) {
    uni.showToast({
      title: '请选择目标岗位',
      icon: 'none'
    })
    return
  }

  loading.value = true

  try {
    // 生成标题
    formData.value.title = formData.value.title || generateTitle()

    // 添加选中的知识库和分类
    const params = {
      ...formData.value,
      questionTypeIds: selectedCategories.value.length > 0
        ? selectedCategories.value
        : undefined
    }

    const interview = await interviewStore.createNewInterview(params)

    uni.showToast({
      title: '创建成功',
      icon: 'success'
    })

    // 跳转到面试会话页面
    setTimeout(() => {
      uni.redirectTo({
        url: `/pages/interview/session?id=${interview.sessionId || interview.id}`
      })
    }, 1000)
  } catch (error) {
    console.error('创建面试失败:', error)
    uni.showToast({
      title: '创建失败，请重试',
      icon: 'none'
    })
  } finally {
    loading.value = false
  }
}

// 取消选择简历
const clearResume = () => {
  selectedResume.value = null
  formData.value.resumeId = undefined
}

onMounted(() => {
  loadResumeList()
  loadQuestionCategories()
})
</script>

<template>
  <view class="create-interview-container">
    <!-- 顶部进度 -->
    <view class="step-header">
      <view class="step-indicator">
        <view
          v-for="step in 3"
          :key="step"
          class="step-item"
          :class="{ active: currentStep >= step, current: currentStep === step }"
        >
          <view class="step-circle">{{ step }}</view>
          <text class="step-label">
            {{ step === 1 ? '选择类型' : step === 2 ? '填写信息' : '确认' }}
          </text>
        </view>
        <view class="step-line">
          <view class="step-line-inner" :style="{ width: ((currentStep - 1) / 2) * 100 + '%' }"></view>
        </view>
      </view>
    </view>

    <!-- 步骤1: 选择面试类型 -->
    <scroll-view v-if="currentStep === 1" class="step-content" scroll-y>
      <view class="section">
        <view class="section-title">选择面试类型</view>
        <view class="type-list">
          <view
            v-for="item in interviewTypes"
            :key="item.value"
            class="type-item"
            :class="{ active: formData.type === item.value }"
            @click="selectType(item.value)"
          >
            <view class="type-icon" :style="{ backgroundColor: item.color + '20', color: item.color }">
              <text v-html="item.icon"></text>
            </view>
            <view class="type-info">
              <view class="type-label">{{ item.label }}</view>
              <view class="type-desc">{{ item.desc }}</view>
            </view>
            <view class="type-check" :class="{ checked: formData.type === item.value }">
              <text v-if="formData.type === item.value" class="check-icon">&#xe618;</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 推荐岗位 -->
      <view class="section">
        <view class="section-title">推荐岗位</view>
        <view class="position-tags">
          <view
            v-for="category in positionCategories"
            :key="category.label"
            class="position-category"
          >
            <view class="category-label">{{ category.label }}</view>
            <view class="category-positions">
              <text
                v-for="position in category.positions"
                :key="position"
                class="position-tag"
                :class="{ active: formData.position === position }"
                @click="formData.position = position"
              >
                {{ position }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 步骤2: 填写信息 -->
    <scroll-view v-if="currentStep === 2" class="step-content" scroll-y>
      <view class="section">
        <view class="section-title">面试信息</view>

        <!-- 面试标题 -->
        <view class="form-item">
          <view class="form-label">面试标题</view>
          <input
            v-model="formData.title"
            class="form-input"
            placeholder="请输入面试标题（可选）"
            placeholder-class="input-placeholder"
          />
        </view>

        <!-- 目标岗位 -->
        <view class="form-item">
          <view class="form-label">目标岗位 <text class="required">*</text></view>
          <view class="form-picker" @click="showPositionPicker = true">
            <text :class="{ placeholder: !formData.position }">
              {{ formData.position || '请选择目标岗位' }}
            </text>
            <text class="arrow">&#xe61c;</text>
          </view>
        </view>

        <!-- 公司名称 -->
        <view class="form-item">
          <view class="form-label">目标公司</view>
          <input
            v-model="formData.company"
            class="form-input"
            placeholder="请输入目标公司（可选）"
            placeholder-class="input-placeholder"
          />
        </view>

        <!-- 面试时长 -->
        <view class="form-item">
          <view class="form-label">面试时长</view>
          <view class="option-group">
            <view
              v-for="option in durationOptions"
              :key="option.value"
              class="option-item"
              :class="{ active: formData.duration === option.value }"
              @click="formData.duration = option.value"
            >
              {{ option.label }}
            </view>
          </view>
        </view>

        <!-- 题目数量 -->
        <view class="form-item">
          <view class="form-label">题目数量</view>
          <view class="option-group">
            <view
              v-for="option in questionCountOptions"
              :key="option.value"
              class="option-item"
              :class="{ active: formData.questionCount === option.value }"
              @click="formData.questionCount = option.value"
            >
              {{ option.label }}
            </view>
          </view>
        </view>
      </view>

      <!-- 关联简历 -->
      <view class="section">
        <view class="section-title">关联简历</view>
        <view v-if="selectedResume" class="selected-resume">
          <view class="resume-info">
            <text class="resume-name">{{ selectedResume.name }}</text>
            <text class="resume-status">{{ selectedResume.fileName }}</text>
          </view>
          <text class="clear-btn" @click="clearResume">清除</text>
        </view>
        <view v-else class="resume-selector" @click="showResumePicker = true">
          <text class="selector-icon">&#xe60d;</text>
          <text class="selector-text">选择简历（可选）</text>
        </view>
      </view>

      <!-- 知识库选择 -->
      <view class="section">
        <view class="section-title">关联知识库</view>
        <view class="knowledge-list">
          <view
            v-for="kb in knowledgeBaseList"
            :key="kb.id"
            class="knowledge-item"
            :class="{ active: selectedKnowledgeBase.includes(kb.id) }"
            @click="toggleKnowledgeBase(kb.id)"
          >
            <view class="knowledge-check" :class="{ checked: selectedKnowledgeBase.includes(kb.id) }">
              <text v-if="selectedKnowledgeBase.includes(kb.id)">&#xe618;</text>
            </view>
            <view class="knowledge-info">
              <text class="knowledge-name">{{ kb.name }}</text>
              <text class="knowledge-count">包含 {{ kb.questionCount }} 题</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 步骤3: 确认 -->
    <view v-if="currentStep === 3" class="step-content">
      <view class="section">
        <view class="section-title">确认面试信息</view>
        <view class="confirm-card">
          <view class="confirm-item">
            <text class="confirm-label">面试类型</text>
            <text class="confirm-value">
              {{ interviewTypes.find(t => t.value === formData.type)?.label }}
            </text>
          </view>
          <view class="confirm-item">
            <text class="confirm-label">目标岗位</text>
            <text class="confirm-value">{{ formData.position || '未选择' }}</text>
          </view>
          <view v-if="formData.company" class="confirm-item">
            <text class="confirm-label">目标公司</text>
            <text class="confirm-value">{{ formData.company }}</text>
          </view>
          <view class="confirm-item">
            <text class="confirm-label">面试时长</text>
            <text class="confirm-value">{{ formData.duration }} 分钟</text>
          </view>
          <view class="confirm-item">
            <text class="confirm-label">题目数量</text>
            <text class="confirm-value">{{ formData.questionCount }} 题</text>
          </view>
          <view v-if="selectedResume" class="confirm-item">
            <text class="confirm-label">关联简历</text>
            <text class="confirm-value">{{ selectedResume.name }}</text>
          </view>
          <view v-if="selectedKnowledgeBase.length > 0" class="confirm-item">
            <text class="confirm-label">关联知识库</text>
            <text class="confirm-value">
              {{ knowledgeBaseList.filter(kb => selectedKnowledgeBase.includes(kb.id)).map(kb => kb.name).join('、') }}
            </text>
          </view>
        </view>
      </view>

      <view class="start-tip">
        <text class="tip-icon">&#xe617;</text>
        <text class="tip-text">点击开始面试后，将进入 AI 模拟面试环节</text>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="bottom-actions">
      <view v-if="currentStep > 1" class="btn btn-secondary" @click="prevStep">
        上一步
      </view>
      <view
        v-if="currentStep < 3"
        class="btn btn-primary"
        :class="{ disabled: currentStep === 1 && !formData.position }"
        @click="nextStep"
      >
        下一步
      </view>
      <view
        v-if="currentStep === 3"
        class="btn btn-primary start-btn"
        :class="{ loading }"
        @click="createInterview"
      >
        <text v-if="!loading">开始面试</text>
        <text v-else>创建中...</text>
      </view>
    </view>

    <!-- 岗位选择器 -->
    <view v-if="showPositionPicker" class="picker-mask" @click="showPositionPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text class="picker-title">选择目标岗位</text>
          <text class="picker-close" @click="showPositionPicker = false">&#xe61c;</text>
        </view>
        <scroll-view class="picker-body" scroll-y>
          <view
            v-for="category in positionCategories"
            :key="category.label"
            class="picker-category"
          >
            <view class="picker-category-label">{{ category.label }}</view>
            <view class="picker-positions">
              <view
                v-for="position in category.positions"
                :key="position"
                class="picker-position"
                :class="{ active: formData.position === position }"
                @click="selectPosition(position)"
              >
                {{ position }}
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 简历选择器 -->
    <view v-if="showResumePicker" class="picker-mask" @click="showResumePicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text class="picker-title">选择简历</text>
          <text class="picker-close" @click="showResumePicker = false">&#xe61c;</text>
        </view>
        <scroll-view class="picker-body" scroll-y>
          <view v-if="resumeList.length === 0" class="picker-empty">
            <text>暂无简历</text>
          </view>
          <view
            v-for="resume in resumeList"
            :key="resume.id"
            class="picker-resume"
            @click="selectResume(resume)"
          >
            <view class="resume-icon">&#xe60a;</view>
            <view class="resume-details">
              <text class="resume-name">{{ resume.name }}</text>
              <text class="resume-file">{{ resume.fileName }}</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
// 靛蓝清新配色
$primary-color: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg-color: #f8fafc;

.create-interview-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
}

.step-header {
  background-color: #fff;
  padding: 30rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.step-indicator {
  display: flex;
  justify-content: space-between;
  position: relative;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;

  .step-circle {
    width: 56rpx;
    height: 56rpx;
    border-radius: 50%;
    background-color: #f0f0f0;
    color: #999;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28rpx;
    font-weight: 600;
    margin-bottom: 12rpx;
    transition: all 0.3s;
  }

  .step-label {
    font-size: 24rpx;
    color: #999;
    transition: all 0.3s;
  }

  &.active {
    .step-circle {
      background-color: $primary-color;
      color: #fff;
    }

    .step-label {
      color: $primary-color;
      font-weight: 500;
    }
  }

  &.current {
    .step-circle {
      box-shadow: 0 0 0 8rpx rgba(102, 126, 234, 0.2);
    }
  }
}

.step-line {
  position: absolute;
  top: 28rpx;
  left: 15%;
  right: 15%;
  height: 4rpx;
  background-color: #f0f0f0;
  z-index: 0;
}

.step-line-inner {
  height: 100%;
  background-color: $primary-color;
  transition: width 0.3s;
}

.step-content {
  flex: 1;
  padding: 30rpx;
  overflow: hidden;
}

.section {
  background-color: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 30rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 24rpx;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.type-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border: 2rpx solid #f0f0f0;
  border-radius: 12rpx;
  transition: all 0.3s;

  &.active {
    border-color: $primary-color;
    background-color: #f8f7ff;
  }

  .type-icon {
    width: 80rpx;
    height: 80rpx;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 40rpx;
    margin-right: 24rpx;
  }

  .type-info {
    flex: 1;
  }

  .type-label {
    font-size: 30rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 8rpx;
  }

  .type-desc {
    font-size: 24rpx;
    color: #999;
  }

  .type-check {
    width: 44rpx;
    height: 44rpx;
    border-radius: 50%;
    border: 2rpx solid #ddd;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s;

    &.checked {
      background-color: $primary-color;
      border-color: $primary-color;
      color: #fff;
    }

    .check-icon {
      font-size: 24rpx;
    }
  }
}

.position-tags {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.position-category {
  .category-label {
    font-size: 26rpx;
    color: #999;
    margin-bottom: 16rpx;
  }

  .category-positions {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
  }
}

.position-tag {
  padding: 12rpx 24rpx;
  background-color: #f5f5f5;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: #666;
  transition: all 0.3s;

  &.active {
    background-color: $primary-color;
    color: #fff;
  }
}

.form-item {
  margin-bottom: 30rpx;

  .form-label {
    font-size: 28rpx;
    color: #333;
    margin-bottom: 16rpx;

    .required {
      color: #F56C6C;
    }
  }
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #333;

  .input-placeholder {
    color: #999;
  }
}

.form-picker {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 80rpx;
  padding: 0 24rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #333;

  .placeholder {
    color: #999;
  }

  .arrow {
    color: #999;
    font-size: 24rpx;
  }
}

.option-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.option-item {
  padding: 16rpx 32rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #666;
  transition: all 0.3s;

  &.active {
    background-color: $primary-color;
    color: #fff;
  }
}

.selected-resume {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;

  .resume-info {
    display: flex;
    flex-direction: column;

    .resume-name {
      font-size: 28rpx;
      color: #333;
      margin-bottom: 8rpx;
    }

    .resume-status {
      font-size: 24rpx;
      color: #999;
    }
  }

  .clear-btn {
    font-size: 26rpx;
    color: #F56C6C;
  }
}

.resume-selector {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 32rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  border: 2rpx dashed #ddd;

  .selector-icon {
    font-size: 40rpx;
    color: $primary-color;
  }

  .selector-text {
    font-size: 28rpx;
    color: #999;
  }
}

.knowledge-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.knowledge-item {
  display: flex;
  align-items: center;
  padding: 20rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  transition: all 0.3s;

  &.active {
    background-color: #f8f7ff;
  }

  .knowledge-check {
    width: 40rpx;
    height: 40rpx;
    border-radius: 50%;
    border: 2rpx solid #ddd;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 20rpx;
    font-size: 20rpx;
    color: #fff;

    &.checked {
      background-color: $primary-color;
      border-color: $primary-color;
    }
  }

  .knowledge-info {
    flex: 1;

    .knowledge-name {
      font-size: 28rpx;
      color: #333;
      display: block;
      margin-bottom: 8rpx;
    }

    .knowledge-count {
      font-size: 24rpx;
      color: #999;
    }
  }
}

.confirm-card {
  background-color: #f5f5f5;
  border-radius: 12rpx;
  padding: 24rpx;
}

.confirm-item {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #eee;

  &:last-child {
    border-bottom: none;
  }

  .confirm-label {
    font-size: 28rpx;
    color: #999;
  }

  .confirm-value {
    font-size: 28rpx;
    color: #333;
    font-weight: 500;
    text-align: right;
    max-width: 60%;
  }
}

.start-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 24rpx;
  background-color: #fff7e6;
  border-radius: 8rpx;

  .tip-icon {
    font-size: 32rpx;
    color: #faad14;
  }

  .tip-text {
    font-size: 26rpx;
    color: #fa8c16;
  }
}

.bottom-actions {
  display: flex;
  gap: 24rpx;
  padding: 30rpx;
  background-color: #fff;
  border-top: 1rpx solid #f0f0f0;

  // 按钮全宽
  .btn {
    flex: 1;
  }

  // 上一步按钮
  .btn-secondary {
    flex: 0 0 180rpx;
    background-color: #f0f1f5;
    color: #6366f1;
    border: 2rpx solid #6366f1;
  }

  // 开始面试按钮占满剩余空间
  .start-btn {
    flex: 1;
  }
}

.btn {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 44rpx;
  font-size: 32rpx;
  font-weight: 500;
  transition: all 0.3s;

  &.btn-primary {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    color: #fff;

    &.disabled {
      opacity: 0.5;
    }

    &.loading {
      opacity: 0.7;
    }
  }

  &.start-btn {
    flex: none;
    width: 100%;
  }
}

.picker-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.picker-content {
  width: 100%;
  max-height: 70vh;
  background-color: #fff;
  border-radius: 32rpx 32rpx 0 0;
  overflow: hidden;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  border-bottom: 1rpx solid #f0f0f0;

  .picker-title {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
  }

  .picker-close {
    font-size: 36rpx;
    color: #999;
  }
}

.picker-body {
  max-height: 60vh;
  padding: 30rpx;
}

.picker-category {
  margin-bottom: 30rpx;

  .picker-category-label {
    font-size: 28rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
  }

  .picker-positions {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
  }

  .picker-position {
    padding: 16rpx 32rpx;
    background-color: #f5f5f5;
    border-radius: 8rpx;
    font-size: 28rpx;
    color: #666;

    &.active {
      background-color: $primary-color;
      color: #fff;
    }
  }
}

.picker-empty {
  text-align: center;
  padding: 60rpx;
  color: #999;
  font-size: 28rpx;
}

.picker-resume {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  margin-bottom: 16rpx;

  .resume-icon {
    font-size: 40rpx;
    color: $primary-color;
    margin-right: 20rpx;
  }

  .resume-details {
    flex: 1;

    .resume-name {
      font-size: 28rpx;
      color: #333;
      display: block;
      margin-bottom: 8rpx;
    }

    .resume-file {
      font-size: 24rpx;
      color: #999;
    }
  }
}
</style>
