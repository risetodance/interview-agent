<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useQuestionBankStore } from '../../stores/question-bank'

// Store
const questionBankStore = useQuestionBankStore()

// 表单数据
const formData = ref({
  name: '',
  description: ''
})

// 是否为编辑模式
const isEdit = ref(false)
// 题库 ID
const bankId = ref<number | null>(null)
// 加载状态
const loading = ref(false)
// 保存状态
const saving = ref(false)

// 验证表单
const validateForm = (): boolean => {
  if (!formData.value.name.trim()) {
    uni.showToast({
      title: '请输入题库名称',
      icon: 'none'
    })
    return false
  }
  if (formData.value.name.trim().length > 50) {
    uni.showToast({
      title: '题库名称不能超过50个字符',
      icon: 'none'
    })
    return false
  }
  if (formData.value.description && formData.value.description.length > 200) {
    uni.showToast({
      title: '题库描述不能超过200个字符',
      icon: 'none'
    })
    return false
  }
  return true
}

// 保存题库
const handleSave = async () => {
  if (!validateForm()) return

  saving.value = true

  try {
    const data = {
      name: formData.value.name.trim(),
      description: formData.value.description.trim() || undefined
    }

    if (isEdit.value && bankId.value) {
      // 编辑题库
      await questionBankStore.modifyBank(bankId.value, data)
      uni.showToast({
        title: '更新成功',
        icon: 'success'
      })
    } else {
      // 创建题库
      await questionBankStore.addBank(data)
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

// 加载题库详情
const loadBankDetail = async (id: number) => {
  loading.value = true

  try {
    const bank = await questionBankStore.fetchBankDetail(id)
    formData.value.name = bank.name || ''
    formData.value.description = bank.description || ''
  } catch (error) {
  } finally {
    loading.value = false
  }
}

// 页面加载
onLoad((options: any) => {
  if (options.id) {
    bankId.value = parseInt(options.id)
    isEdit.value = true
    uni.setNavigationBarTitle({ title: '编辑题库' })
    loadBankDetail(bankId.value)
  } else {
    uni.setNavigationBarTitle({ title: '创建题库' })
  }
})
</script>

<template>
  <view class="question-bank-create-container">
    <!-- 表单区域 -->
    <view class="form-section">
      <!-- 题库名称 -->
      <view class="form-item">
        <view class="form-label">
          <text class="required">*</text>
          <text>题库名称</text>
        </view>
        <view class="form-input-wrap">
          <input
            v-model="formData.name"
            class="form-input"
            placeholder="请输入题库名称"
            maxlength="50"
            :disabled="loading"
          />
        </view>
        <view class="form-hint">{{ formData.name.length }}/50</view>
      </view>

      <!-- 题库描述 -->
      <view class="form-item">
        <view class="form-label">
          <text>题库描述</text>
        </view>
        <view class="form-input-wrap">
          <textarea
            v-model="formData.description"
            class="form-textarea"
            placeholder="请输入题库描述（可选）"
            maxlength="200"
            :disabled="loading"
          />
        </view>
        <view class="form-hint">{{ formData.description?.length || 0 }}/200</view>
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
        <text v-else>{{ isEdit ? '保存修改' : '创建题库' }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@use '../../styles/variables.scss' as *;

.question-bank-create-container {
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
  font-size: 28rpx;
  color: $text-primary;
}

.form-textarea {
  height: 160rpx;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: $text-primary;
  line-height: 1.6;
}

.form-hint {
  font-size: 24rpx;
  color: $text-muted;
  text-align: right;
  margin-top: 12rpx;
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
