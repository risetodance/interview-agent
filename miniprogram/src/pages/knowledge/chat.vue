<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ragChat, type RagChatResponse, type ChatMessage } from '../../api/knowledgebase'

// 路由参数
const knowledgebaseId = ref<number>(0)
const knowledgebaseName = ref<string>('')

// 消息列表
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isLoading = ref(false)
const scrollTop = ref(99999)

// 录音相关
const isRecording = ref(false)
const recordingTime = ref(0)
const recordingTimer = ref<number | null>(null)
const recorderManager = ref<any>(null)

// 加载历史记录的标记
const hasLoadedHistory = ref(false)

// 获取页面参数
onMounted(() => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}

  knowledgebaseId.value = Number(options.id) || 0
  knowledgebaseName.value = decodeURIComponent(options.name || '知识库问答')

  // 初始化录音管理器
  initRecorder()

  // 添加欢迎消息
  addWelcomeMessage()
})

// 初始化录音管理器
const initRecorder = () => {
  // #ifdef MP-WEIXIN
  recorderManager.value = uni.getRecorderManager()

  recorderManager.value.onStop((res: any) => {
    console.log('录音完成', res)
    handleVoiceRecord(res.tempFilePath)
  })

  recorderManager.value.onError((err: any) => {
    console.error('录音错误', err)
    uni.showToast({
      title: '录音失败',
      icon: 'none'
    })
    stopRecording()
  })
  // #endif
}

// 添加欢迎消息
const addWelcomeMessage = () => {
  messages.value.push({
    id: Date.now(),
    type: 'answer',
    content: `欢迎使用知识库问答！我是 AI 助手，可以根据「${knowledgebaseName.value}」中的内容回答你的问题。\n\n请输入你的问题，我会从知识库中检索相关信息并为你解答。`,
    timestamp: new Date().toISOString()
  })
}

// 发送消息
const sendMessage = async () => {
  if (!inputText.value.trim() || isLoading.value) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  addUserMessage(question)

  // 发送请求
  await sendToAI(question)
}

// 添加用户消息
const addUserMessage = (content: string) => {
  messages.value.push({
    id: Date.now(),
    type: 'question',
    content,
    timestamp: new Date().toISOString()
  })

  scrollToBottom()
}

// 发送消息到 AI
const sendToAI = async (question: string) => {
  isLoading.value = true

  // 添加loading消息
  const loadingId = Date.now()
  messages.value.push({
    id: loadingId,
    type: 'answer',
    content: '正在思考中...',
    timestamp: new Date().toISOString()
  })

  try {
    // 构建历史记录
    const history = messages.value
      .filter(m => m.type === 'question' || (m.type === 'answer' && m.id !== loadingId))
      .slice(-10)
      .map(m => ({
        role: m.type === 'question' ? 'user' as const : 'assistant' as const,
        content: m.content
      }))

    const response = await ragChat({
      knowledgebaseId: knowledgebaseId.value,
      message: question,
      history
    })

    // 更新loading消息为AI回复
    const loadingIndex = messages.value.findIndex(m => m.id === loadingId)
    if (loadingIndex !== -1) {
      messages.value[loadingIndex] = {
        id: loadingId,
        type: 'answer',
        content: response.answer,
        timestamp: new Date().toISOString()
      }
    }

    // 如果有来源信息，显示来源
    if (response.sources && response.sources.length > 0) {
      const sourceText = '\n\n📚 参考来源：\n' + response.sources
        .map((s, i) => `${i + 1}. ${s.documentName}`)
        .join('\n')

      messages.value[loadingIndex].content += sourceText
    }
  } catch (error) {
    console.error('AI 回答失败:', error)

    // 更新loading消息为错误
    const loadingIndex = messages.value.findIndex(m => m.id === loadingId)
    if (loadingIndex !== -1) {
      messages.value[loadingIndex] = {
        id: loadingId,
        type: 'answer',
        content: '抱歉，我无法回答这个问题。请稍后重试或尝试其他问题。',
        timestamp: new Date().toISOString()
      }
    }
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    scrollTop.value = 99999
  })
}

// 开始录音
const startRecording = () => {
  // #ifdef MP-WEIXIN
  if (!recorderManager.value) {
    uni.showToast({
      title: '录音功能仅支持微信小程序',
      icon: 'none'
    })
    return
  }

  try {
    recorderManager.value.start({
      format: 'mp3',
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      duration: 60000 // 最大60秒
    })

    isRecording.value = true
    recordingTime.value = 0

    recordingTimer.value = setInterval(() => {
      recordingTime.value++
      // 最长录音60秒
      if (recordingTime.value >= 60) {
        stopRecording()
      }
    }, 1000) as unknown as number

    uni.showToast({
      title: '开始录音',
      icon: 'none'
    })
  } catch (error) {
    console.error('开始录音失败:', error)
    uni.showToast({
      title: '录音失败',
      icon: 'none'
    })
  }
  // #endif

  // #ifndef MP-WEIXIN
  uni.showToast({
    title: '录音功能仅支持微信小程序',
    icon: 'none'
  })
  // #endif
}

// 停止录音
const stopRecording = () => {
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  // #ifdef MP-WEIXIN
  if (recorderManager.value) {
    recorderManager.value.stop()
  }
  // #endif

  isRecording.value = false
  recordingTime.value = 0
}

// 处理语音录制结果
const handleVoiceRecord = (filePath: string) => {
  // 这里可以添加语音识别功能
  // 目前先用提示
  uni.showToast({
    title: '录音完成，请输入文字',
    icon: 'none'
  })
}

// 格式化录音时间
const formatRecordingTime = (seconds: number): string => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

// 格式化时间
const formatTime = (timestamp: string): string => {
  const date = new Date(timestamp)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

// 返回列表
const goBack = () => {
  uni.navigateBack()
}

// 组件卸载时清理
onUnmounted(() => {
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
  }

  // #ifdef MP-WEIXIN
  if (recorderManager.value && isRecording.value) {
    recorderManager.value.stop()
  }
  // #endif
})
</script>

<template>
  <view class="chat-container">
    <!-- 顶部导航 -->
    <view class="chat-header">
      <view class="header-left" @click="goBack">
        <text class="back-icon">&#xe61b;</text>
      </view>
      <view class="header-center">
        <text class="chat-title">{{ knowledgebaseName }}</text>
        <text class="chat-subtitle">知识库问答</text>
      </view>
      <view class="header-right">
        <!-- 占位，保持对称 -->
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="message-list"
      scroll-y
      :scroll-top="scrollTop"
      :refresher-enabled="false"
    >
      <view
        v-for="msg in messages"
        :key="msg.id"
        class="message-item"
        :class="msg.type"
      >
        <!-- AI 回答 -->
        <view v-if="msg.type === 'answer'" class="answer-bubble">
          <view class="ai-avatar">
            <text>&#xe617;</text>
          </view>
          <view class="bubble-content">
            <text class="bubble-text">{{ msg.content }}</text>
            <text class="bubble-time">{{ formatTime(msg.timestamp) }}</text>
          </view>
        </view>

        <!-- 用户问题 -->
        <view v-else class="question-bubble">
          <view class="bubble-content">
            <text class="bubble-text">{{ msg.content }}</text>
            <text class="bubble-time">{{ formatTime(msg.timestamp) }}</text>
          </view>
          <view class="user-avatar">
            <text>&#xe60a;</text>
          </view>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="isLoading" class="loading-indicator">
        <view class="loading-dots">
          <view class="dot"></view>
          <view class="dot"></view>
          <view class="dot"></view>
        </view>
        <text class="loading-text">AI 正在思考...</text>
      </view>
    </scroll-view>

    <!-- 输入区域 -->
    <view class="input-section">
      <!-- 录音时间显示 -->
      <view v-if="isRecording" class="recording-tip">
        <view class="recording-indicator"></view>
        <text class="recording-text">{{ formatRecordingTime(recordingTime) }}</text>
        <text class="recording-hint">点击停止录音</text>
      </view>

      <!-- 输入框区域 -->
      <view class="input-area">
        <!-- 录音按钮 -->
        <view
          class="voice-btn"
          :class="{ recording: isRecording }"
          @click="isRecording ? stopRecording() : startRecording()"
        >
          <text v-if="!isRecording">&#xe618;</text>
          <text v-else class="stop-icon">&#xe61c;</text>
        </view>

        <!-- 文本输入 -->
        <textarea
          v-model="inputText"
          class="message-input"
          placeholder="请输入你的问题..."
          placeholder-class="input-placeholder"
          :disabled="isLoading"
          :maxlength="2000"
          :auto-height="true"
          @confirm="sendMessage"
        />

        <!-- 发送按钮 -->
        <view
          class="send-btn"
          :class="{ disabled: !inputText.trim() || isLoading }"
          @click="sendMessage"
        >
          <text>发送</text>
        </view>
      </view>

      <!-- 底部提示 -->
      <view class="bottom-tip">
        <text>AI 会根据知识库内容回答问题</text>
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

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}

.header-left {
  width: 80rpx;

  .back-icon {
    font-size: 40rpx;
    color: #333;
  }
}

.header-center {
  flex: 1;
  text-align: center;

  .chat-title {
    display: block;
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
  }

  .chat-subtitle {
    display: block;
    font-size: 24rpx;
    color: #999;
    margin-top: 4rpx;
  }
}

.header-right {
  width: 80rpx;
}

.message-list {
  flex: 1;
  padding: 30rpx;
  overflow: hidden;
}

.message-item {
  margin-bottom: 30rpx;

  &.answer {
    display: flex;
    align-items: flex-start;
  }

  &.question {
    display: flex;
    justify-content: flex-end;
  }
}

.ai-avatar,
.user-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  flex-shrink: 0;
}

.ai-avatar {
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  color: #fff;
  margin-right: 20rpx;
}

.user-avatar {
  background: linear-gradient(135deg, #6366f1 0%, #45a049 100%);
  color: #fff;
  margin-left: 20rpx;
}

.bubble-content {
  max-width: 70%;
  position: relative;
}

.answer-bubble {
  .bubble-content {
    background-color: #fff;
    border-radius: 24rpx;
    padding: 24rpx;
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  }

  .bubble-text {
    font-size: 28rpx;
    color: #333;
    line-height: 1.6;
    white-space: pre-wrap;
  }

  .bubble-time {
    display: block;
    margin-top: 12rpx;
    font-size: 20rpx;
    color: #bbb;
    text-align: right;
  }
}

.question-bubble {
  .bubble-content {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border-radius: 24rpx;
    padding: 24rpx;

    .bubble-text {
      color: #fff;
    }

    .bubble-time {
      color: rgba(255, 255, 255, 0.7);
    }
  }

  .bubble-text {
    font-size: 28rpx;
    color: #333;
    line-height: 1.6;
    white-space: pre-wrap;
  }

  .bubble-time {
    display: block;
    margin-top: 12rpx;
    font-size: 20rpx;
    color: #bbb;
    text-align: right;
  }
}

.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30rpx;

  .loading-dots {
    display: flex;
    gap: 12rpx;
    margin-bottom: 16rpx;

    .dot {
      width: 16rpx;
      height: 16rpx;
      border-radius: 50%;
      background-color: $primary-color;
      animation: loadingBounce 1.4s ease-in-out infinite;

      &:nth-child(1) {
        animation-delay: 0s;
      }

      &:nth-child(2) {
        animation-delay: 0.2s;
      }

      &:nth-child(3) {
        animation-delay: 0.4s;
      }
    }
  }

  .loading-text {
    font-size: 24rpx;
    color: #999;
  }
}

@keyframes loadingBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-section {
  background-color: #fff;
  border-top: 1rpx solid #f0f0f0;
}

.recording-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 20rpx;
  background-color: #fef0f0;

  .recording-indicator {
    width: 16rpx;
    height: 16rpx;
    border-radius: 50%;
    background-color: #F56C6C;
    animation: pulse 1s infinite;
  }

  .recording-text {
    font-size: 28rpx;
    color: #F56C6C;
    font-weight: 500;
  }

  .recording-hint {
    font-size: 24rpx;
    color: #F56C6C;
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.input-area {
  display: flex;
  align-items: flex-end;
  padding: 20rpx 30rpx;
  gap: 20rpx;
}

.voice-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #666;
  transition: all 0.3s;
  flex-shrink: 0;

  &.recording {
    background-color: #F56C6C;
    color: #fff;
    animation: pulse 1s infinite;

    .stop-icon {
      font-size: 24rpx;
    }
  }
}

.message-input {
  flex: 1;
  min-height: 72rpx;
  max-height: 200rpx;
  padding: 16rpx 20rpx;
  background-color: #f5f5f5;
  border-radius: 36rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.4;

  .input-placeholder {
    color: #999;
  }
}

.send-btn {
  padding: 16rpx 32rpx;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: 36rpx;
  color: #fff;
  font-size: 28rpx;
  font-weight: 500;
  transition: all 0.3s;
  flex-shrink: 0;

  &.disabled {
    opacity: 0.5;
  }
}

.bottom-tip {
  text-align: center;
  padding: 16rpx;
  font-size: 22rpx;
  color: #bbb;
  border-top: 1rpx solid #f0f0f0;
}
</style>
