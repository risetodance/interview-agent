<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  getNotificationSettings,
  updateNotificationSettings,
  subscribeWechatMessage,
  type NotificationSettings
} from '../../api/notification'

// 加载状态
const isLoading = ref(false)
const isSubmitting = ref(false)

// 通知设置
const settings = ref<NotificationSettings>({
  interviewNotification: true,
  resumeNotification: true,
  membershipNotification: true,
  pointsNotification: true,
  knowledgeNotification: true,
  systemNotification: true
})

// 微信订阅状态
const isSubscribed = ref(false)

// 设置项配置
const settingsItems = computed(() => [
  {
    key: 'interviewNotification',
    title: '面试通知',
    desc: '接收面试安排、面试提醒等通知',
    icon: '面试',
    color: '#43e97b'
  },
  {
    key: 'resumeNotification',
    title: '简历通知',
    desc: '接收简历解析、评估等通知',
    icon: '简历',
    color: '#4facfe'
  },
  {
    key: 'membershipNotification',
    title: '活动通知',
    desc: '接收最新活动、优惠信息等通知',
    icon: '活动',
    color: '#ffd700'
  },
  {
    key: 'pointsNotification',
    title: '积分通知',
    desc: '接收积分变动、积分兑换等通知',
    icon: '积分',
    color: '#fa709a'
  },
  {
    key: 'knowledgeNotification',
    title: '知识库通知',
    desc: '接收知识库更新、分享等通知',
    icon: '知识',
    color: $primary-color
  },
  {
    key: 'systemNotification',
    title: '系统通知',
    desc: '接收系统公告、功能更新等通知',
    icon: '系统',
    color: '$primary-color'
  }
])

// 加载设置
const loadSettings = async () => {
  isLoading.value = true
  try {
    const res = await getNotificationSettings()
    settings.value = res
  } catch (error) {
    console.error('加载通知设置失败:', error)
  } finally {
    isLoading.value = false
  }
}

// 切换开关
const handleToggle = async (key: keyof NotificationSettings) => {
  const newValue = !settings.value[key]
  isSubmitting.value = true

  try {
    // 先更新本地状态
    settings.value[key] = newValue

    // 调用 API 保存
    await updateNotificationSettings({
      [key]: newValue
    })

    uni.showToast({
      title: newValue ? '已开启' : '已关闭',
      icon: 'success'
    })
  } catch (error) {
    console.error('更新设置失败:', error)
    // 失败时恢复原状态
    settings.value[key] = !newValue
    uni.showToast({
      title: '设置失败，请重试',
      icon: 'none'
    })
  } finally {
    isSubmitting.value = false
  }
}

// 订阅微信消息
const handleSubscribe = async () => {
  // 模拟微信订阅消息
  // 实际需要在小程序管理后台配置模板消息
  const templateIds = [
    'YOUR_TEMPLATE_ID_1',
    'YOUR_TEMPLATE_ID_2'
  ]

  try {
    // #ifdef MP-WEIXIN
    const res = await new Promise<{ errMsg: string; confirm: boolean }>((resolve) => {
      uni.requestSubscribeMessage({
        tmplIds: templateIds,
        success: (result) => {
          resolve({ errMsg: 'ok', confirm: true })
        },
        fail: (error) => {
          resolve({ errMsg: error.errMsg || 'fail', confirm: false })
        }
      })
    })

    if (res.confirm) {
      // 调用后端保存订阅状态
      await subscribeWechatMessage(templateIds)
      isSubscribed.value = true
      uni.showToast({
        title: '订阅成功',
        icon: 'success'
      })
    } else {
      uni.showToast({
        title: '需要您允许接收通知',
        icon: 'none'
      })
    }
    // #endif

    // #ifndef MP-WEIXIN
    uni.showToast({
      title: '仅支持微信小程序',
      icon: 'none'
    })
    // #endif
  } catch (error) {
    console.error('订阅消息失败:', error)
    uni.showToast({
      title: '订阅失败，请重试',
      icon: 'none'
    })
  }
}

// 全局开关（所有通知）
const allNotificationsEnabled = computed({
  get: () => {
    return Object.values(settings.value).every(v => v)
  },
  set: (val: boolean) => {
    const keys = Object.keys(settings.value) as (keyof NotificationSettings)[]
    keys.forEach(key => {
      settings.value[key] = val
    })
    // 批量更新
    updateNotificationSettings(settings.value).then(() => {
      uni.showToast({
        title: val ? '已全部开启' : '已全部关闭',
        icon: 'success'
      })
    })
  }
})

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <view class="settings-container">
    <!-- 顶部提示 -->
    <view class="tips-card">
      <view class="tips-icon">
        <text class="tips-icon-text">!</text>
      </view>
      <view class="tips-content">
        <text class="tips-title">通知订阅</text>
        <text class="tips-desc">点击下方按钮，通过微信接收消息推送</text>
      </view>
    </view>

    <!-- 微信订阅按钮 -->
    <view class="subscribe-section">
      <button
        class="subscribe-btn"
        :class="{ subscribed: isSubscribed }"
        @click="handleSubscribe"
      >
        <text class="subscribe-btn-text">
          {{ isSubscribed ? '已订阅微信消息' : '订阅微信消息' }}
        </text>
      </button>
      <text class="subscribe-tip">订阅后可在微信服务通知中接收消息</text>
    </view>

    <!-- 通知设置列表 -->
    <view class="settings-section">
      <view class="section-header">
        <text class="section-title">通知类型</text>
        <!-- 全局开关 -->
        <switch
          :checked="allNotificationsEnabled"
          @change="(e: any) => allNotificationsEnabled = e.detail.value"
          color="$primary-color"
        />
      </view>

      <view class="settings-list">
        <view
          v-for="item in settingsItems"
          :key="item.key"
          class="settings-item"
        >
          <view class="settings-item-left">
            <view class="settings-item-icon" :style="{ background: item.color }">
              <text class="settings-item-icon-text">{{ item.icon }}</text>
            </view>
            <view class="settings-item-info">
              <text class="settings-item-title">{{ item.title }}</text>
              <text class="settings-item-desc">{{ item.desc }}</text>
            </view>
          </view>
          <switch
            :checked="settings[item.key as keyof NotificationSettings]"
            @change="() => handleToggle(item.key as keyof NotificationSettings)"
            color="$primary-color"
            :disabled="isSubmitting"
          />
        </view>
      </view>
    </view>

    <!-- 底部说明 -->
    <view class="footer-tip">
      <text class="footer-tip-text">
        关闭通知后，您将不再收到对应类型的推送消息，但仍可在消息列表中查看历史消息
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
// 靛蓝清新配色
$primary-color: #6366f1;
$primary-light: #a5b4fc;
$primary-dark: #4f46e5;
$accent: #818cf8;
$bg-color: #f8fafc;

.settings-container {
  min-height: 100vh;
  background-color: $bg-color;
  padding: 30rpx;
}

.tips-card {
  display: flex;
  align-items: flex-start;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 30rpx;
}

.tips-icon {
  width: 48rpx;
  height: 48rpx;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tips-icon-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: bold;
}

.tips-content {
  flex: 1;
  margin-left: 16rpx;
}

.tips-title {
  display: block;
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.tips-desc {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.subscribe-section {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 30rpx;
  text-align: center;
}

.subscribe-btn {
  width: 100%;
  height: 88rpx;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: 44rpx;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;

  &.subscribed {
    background: #f0f0f0;
  }
}

.subscribe-btn-text {
  font-size: 30rpx;
  color: #fff;
  font-weight: 500;

  .subscribed & {
    color: #666;
  }
}

.subscribe-tip {
  font-size: 24rpx;
  color: #999;
}

.settings-section {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 30rpx;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 30rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.section-title {
  font-size: 30rpx;
  color: #333;
  font-weight: 600;
}

.settings-list {
  padding: 0 30rpx;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.settings-item:last-child {
  border-bottom: none;
}

.settings-item-left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.settings-item-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.settings-item-icon-text {
  font-size: 22rpx;
  color: #fff;
  font-weight: 600;
}

.settings-item-info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.settings-item-title {
  display: block;
  font-size: 28rpx;
  color: #333;
  font-weight: 500;
  margin-bottom: 6rpx;
}

.settings-item-desc {
  display: block;
  font-size: 24rpx;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.footer-tip {
  padding: 20rpx;
}

.footer-tip-text {
  display: block;
  font-size: 24rpx;
  color: #999;
  text-align: center;
  line-height: 1.6;
}
</style>
