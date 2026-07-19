<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useQuestionBankStore, type QuestionDifficulty, type CreateQuestionRequest } from '../../stores/question-bank'

// Store
const questionBankStore = useQuestionBankStore()

// 题目 ID 和所属题库 ID
const questionId = ref<number>(0)
const bankId = ref<number>(0)

// 是否为编辑模式
const isEdit = ref(false)

// 表单数据
const formData = ref({
  content: '',
  answer: '',
  difficulty: 'ADVANCED' as QuestionDifficulty,
  tags: ''
})

// 加载状态
const loading = ref(false)
// 保存状态
const saving = ref(false)

// 难度选项
const difficultyOptions = [
  { value: 'BASIC' as QuestionDifficulty, label: '基础', color: '#67C23A' },
  { value: 'ADVANCED' as QuestionDifficulty, label: '进阶', color: '#E6A23C' },
  { value: 'EXPERT' as QuestionDifficulty, label: '专家', color: '#F56C6C' }
]

// 验证表单
const validateForm = (): boolean => {
  if (!formData.value.content.trim()) {
    uni.showToast({
      title: '请输入题目内容',
      icon: 'none'
    })
    return false
  }
  if (formData.value.content.trim().length > 1000) {
    uni.showToast({
      title: '题目内容不能超过1000个字符',
      icon: 'none'
    })
    return false
  }
  if (formData.value.answer && formData.value.answer.length > 5000) {
    uni.showToast({
      title: '参考答案不能超过5000个字符',
      icon: 'none'
    })
    return false
  }
  return true
}

// 保存题目
const handleSave = async () => {
  if (!validateForm()) return

  saving.value = true

  try {
    // 处理标签
    const tags = formData.value.tags
      .split(/[,，]/)
      .map(t => t.trim())
      .filter(t => t.length > 0)

    const data: CreateQuestionRequest = {
      questionBankId: bankId.value,
      content: formData.value.content.trim(),
      answer: formData.value.answer.trim() || undefined,
      difficulty: formData.value.difficulty,
      tags: tags.length > 0 ? tags : undefined
    }

    if (isEdit.value && questionId.value) {
      // 更新题目
      await questionBankStore.modifyQuestion(questionId.value, data)
      uni.showToast({
        title: '更新成功',
        icon: 'success'
      })
    } else {
      // 创建题目
      await questionBankStore.addQuestion(data)
      uni.showToast({
        title: '创建成功',
        icon: 'success'
      })
    }

    // 返回上一页
    setTimeout(() => {
      uni.navigateBack()
    }, 1500)
  } catch (error) {
  } finally {
    saving.value = false
  }
}

// 加载题目详情
const loadQuestionDetail = async (id: number) => {
  loading.value = true

  try {
    const question = await questionBankStore.fetchQuestionDetail(id)
    formData.value.content = question.content || ''
    formData.value.answer = question.answer || ''
    formData.value.difficulty = question.difficulty || 'ADVANCED'
    formData.value.tags = question.tags?.join('、') || ''
  } catch (error) {
  } finally {
    loading.value = false
  }
}

// 页面加载
onLoad((options: any) => {
  if (options.id) {
    questionId.value = parseInt(options.id)
    isEdit.value = true
    uni.setNavigationBarTitle({ title: '编辑题目' })
    loadQuestionDetail(questionId.value)
  }

  if (options.bankId) {
    bankId.value = parseInt(options.bankId)
  }

  if (!options.id) {
    uni.setNavigationBarTitle({ title: '创建题目' })
  }
})
</script>

<template>
  <view class="question-edit-container">
    <!-- 表单区域 -->
    <view class="form-section">
      <!-- 题目内容 -->
      <view class="form-item">
        <view class="form-label">
          <text class="required">*</text>
          <text>题目内容</text>
        </view>
        <view class="form-input-wrap">
          <textarea
            v-model="formData.content"
            class="form-textarea"
            placeholder="请输入题目内容"
            maxlength="1000"
            :disabled="loading"
          />
        </view>
        <view class="form-hint">{{ formData.content.length }}/1000</view>
      </view>

      <!-- 参考答案 -->
      <view class="form-item">
        <view class="form-label">
          <text>参考答案</text>
        </view>
        <view class="form-input-wrap">
          <textarea
            v-model="formData.answer"
            class="form-textarea large"
            placeholder="请输入参考答案（可选）"
            maxlength="5000"
            :disabled="loading"
          />
        </view>
        <view class="form-hint">{{ formData.answer.length }}/5000</view>
      </view>

      <!-- 难度选择 -->
      <view class="form-item">
        <view class="form-label">
          <text>难度</text>
        </view>
        <view class="difficulty-options">
          <view
            v-for="option in difficultyOptions"
            :key="option.value"
            class="difficulty-option"
            :class="{ active: formData.difficulty === option.value }"
            :style="formData.difficulty === option.value ? { color: option.color, borderColor: option.color, backgroundColor: option.color + '15' } : {}"
            @click="formData.difficulty = option.value"
          >
            <text>{{ option.label }}</text>
          </view>
        </view>
      </view>

      <!-- 标签 -->
      <view class="form-item">
        <view class="form-label">
          <text>标签</text>
        </view>
        <view class="form-input-wrap">
          <input
            v-model="formData.tags"
            class="form-input"
            placeholder="请输入标签，多个标签用逗号分隔"
            :disabled="loading"
          />
        </view>
        <view class="form-hint">多个标签用逗号分隔</view>
      </view>
    </view>

    <!-- 保存按钮 -->
    <view class="submit-section">
      <view
        class="submit-btn"
        :class="{ disabled: saving }"
        @click="handleSave"
      >
        <text v-if="saving">保存中...</text>
        <text v-else>{{ isEdit ? '保存修改' : '创建题目' }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@use '../../styles/variables.scss' as *;

.question-edit-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: $bg;
  padding: 32rpx 40rpx;
}

.form-section {
  background: $card-bg;
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 48rpx;
}

.form-item {
  margin-bottom: 40rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.form-label {
  font-size: 28rpx;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 16rpx;

  .required {
    color: $danger;
    margin-right: 8rpx;
  }
}

.form-input-wrap {
  background: #f8fafc;
  border-radius: 16rpx;
  padding: 0 24rpx;
}

.form-input {
  height: 88rpx;
  width: 100%;
  box-sizing: border-box;
  font-size: 28rpx;
  color: $text-primary;
}

.form-textarea {
  height: 160rpx;
  width: 100%;
  box-sizing: border-box;
  // H5 下 textarea 默认可横向拖拽缩放，破坏布局对齐，锁定
  resize: none;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: $text-primary;
  line-height: 1.6;

  &.large {
    height: 240rpx;
  }
}

.form-hint {
  font-size: 24rpx;
  color: $text-muted;
  text-align: right;
  margin-top: 12rpx;
}

.difficulty-options {
  display: flex;
  gap: 20rpx;
}

.difficulty-option {
  flex: 1;
  height: 80rpx;
  border: 2rpx solid #e5e5e5;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: $text-secondary;
  background: #f8fafc;
  transition: all 0.3s ease;

  &.active {
    font-weight: 600;
  }
}

.submit-section {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 40rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: $card-bg;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.submit-btn {
  height: 96rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  transition: all 0.3s ease;

  &:active:not(.disabled) {
    transform: scale(0.98);
    opacity: 0.9;
  }

  &.disabled {
    opacity: 0.6;
  }
}
</style>
