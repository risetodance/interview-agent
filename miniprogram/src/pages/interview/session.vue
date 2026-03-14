<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useInterviewStore, type InterviewQuestion, type QuestionEvaluation } from '../../stores/interview'
import { wsManager, WebSocketMessageType, sendInterviewAnswer, requestNextQuestion } from '../../utils/websocket'

// 路由参数
const pageId = ref<string>('')
const pageMode = ref<'interview' | 'result'>('interview')

// Store
const interviewStore = useInterviewStore()

// 数据
const interview = computed(() => interviewStore.currentInterview)
const questions = computed(() => interviewStore.currentQuestions)
const currentQuestion = computed(() => interviewStore.currentQuestion)
const progress = computed(() => interviewStore.progress)
const currentIndex = computed(() => interviewStore.currentQuestionIndex)
const isLoading = computed(() => interviewStore.isLoading)
const isSubmitting = computed(() => interviewStore.isSubmitting)

// UI状态
const showEvaluation = ref(false)
const answerText = ref('')
const isRecording = ref(false)
const recordingTime = ref(0)
const recordingTimer = ref<number | null>(null)

// 面试状态
const interviewStatus = ref<'idle' | 'connected' | 'answering' | 'evaluating' | 'completed'>('idle')

// 消息列表
const messages = ref<Array<{
  id: number
  type: 'question' | 'answer' | 'evaluation' | 'system'
  content: string
  questionId?: number
  evaluation?: QuestionEvaluation
  timestamp: number
}>>([])

// 面试结果
const interviewResult = ref<{
  score: number
  feedback: string
  strengths: string[]
  improvements: string[]
  duration: number
} | null>(null)

// 获取页面参数
onMounted(() => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}

  pageId.value = options.id || ''
  pageMode.value = options.mode === 'result' ? 'result' : 'interview'

  if (pageId.value) {
    initInterview()
  }
})

// 初始化面试
const initInterview = async () => {
  try {
    console.log('[Session] initInterview start, pageId:', pageId.value, 'mode:', pageMode.value)

    // 获取面试详情
    console.log('[Session] fetching detail...')
    await interviewStore.fetchInterviewDetail(pageId.value)
    console.log('[Session] detail fetched')

    // 获取问题列表
    console.log('[Session] fetching questions...')
    await interviewStore.fetchQuestions(pageId.value)
    console.log('[Session] questions fetched')

    // 如果是结果模式，获取结果
    if (pageMode.value === 'result') {
      console.log('[Session] fetching result...')
      const result = await interviewStore.fetchInterviewResult(pageId.value)
      interviewResult.value = result
      interviewStatus.value = 'completed'
      console.log('[Session] result mode ready')
    } else {
      // 连接 WebSocket 开始面试
      console.log('[Session] starting websocket...')
      startWebSocket()
    }
    console.log('[Session] initInterview done')
  } catch (error) {
    console.error('初始化面试失败:', error)
    uni.showToast({
      title: '加载失败',
      icon: 'none'
    })
  }
}

// 连接 WebSocket
const startWebSocket = () => {
  if (!wsManager.isConnected) {
    wsManager.connect()
  }

  // 监听连接成功
  wsManager.onConnect(() => {
    interviewStatus.value = 'connected'
    addSystemMessage('已连接面试系统')

    // 请求第一个问题
    if (questions.value.length > 0 && !currentQuestion.value?.answer) {
      showCurrentQuestion()
    }
  })

  // 监听问题
  wsManager.on(WebSocketMessageType.INTERVIEW_QUESTION, (data) => {
    if (data.questions) {
      interviewStore.fetchQuestions(pageId.value)
    }
    interviewStatus.value = 'answering'
  })

  // 监听答案评价
  wsManager.on(WebSocketMessageType.AI_EVALUATION, (data) => {
    handleEvaluation(data)
  })

  // 监听面试完成
  wsManager.on(WebSocketMessageType.INTERVIEW_COMPLETE, (data) => {
    handleInterviewComplete(data)
  })

  // 监听错误
  wsManager.on(WebSocketMessageType.ERROR, (data) => {
    addSystemMessage(data.message || '系统错误')
  })
}

// 显示当前问题
const showCurrentQuestion = () => {
  if (currentQuestion.value) {
    addQuestionMessage(currentQuestion.value)
  }
}

// 添加问题消息
const addQuestionMessage = (question: InterviewQuestion) => {
  messages.value.push({
    id: Date.now(),
    type: 'question',
    content: question.content,
    questionId: question.id,
    timestamp: Date.now()
  })

  // 滚动到底部
  setTimeout(() => {
    scrollToBottom()
  }, 100)
}

// 添加回答消息
const addAnswerMessage = (answer: string, questionId: number) => {
  messages.value.push({
    id: Date.now(),
    type: 'answer',
    content: answer,
    questionId,
    timestamp: Date.now()
  })

  scrollToBottom()
}

// 添加系统消息
const addSystemMessage = (content: string) => {
  messages.value.push({
    id: Date.now(),
    type: 'system',
    content,
    timestamp: Date.now()
  })
}

// 处理评价结果
const handleEvaluation = (data: any) => {
  interviewStatus.value = 'evaluating'

  if (data.evaluation) {
    messages.value.push({
      id: Date.now(),
      type: 'evaluation',
      content: data.evaluation.overallFeedback || '回答完毕',
      questionId: data.questionId,
      evaluation: data.evaluation,
      timestamp: Date.now()
    })

    // 更新问题状态
    const question = questions.value.find(q => q.id === data.questionId)
    if (question) {
      question.evaluation = data.evaluation
      question.answerStatus = 'evaluated'
    }
  }

  setTimeout(() => {
    interviewStatus.value = 'answering'
    scrollToBottom()
  }, 2000)
}

// 处理面试完成
const handleInterviewComplete = (data: any) => {
  interviewStatus.value = 'completed'
  addSystemMessage('面试已完成')

  // 获取最终结果
  interviewStore.fetchInterviewResult(pageId.value).then(result => {
    interviewResult.value = result
  })
}

// 滚动到底部
const scrollToBottom = () => {
  // 使用 nextTick 后滚动
  setTimeout(() => {
    uni.pageScrollTo({
      scrollTop: 99999,
      duration: 300
    })
  }, 200)
}

// 提交回答
const submitAnswer = async () => {
  if (!answerText.value.trim() || !currentQuestion.value) return

  const answer = answerText.value.trim()
  const questionId = currentQuestion.value.id

  // 添加回答消息
  addAnswerMessage(answer, questionId)

  // 清空输入
  answerText.value = ''

  // 设置为评价中
  interviewStatus.value = 'evaluating'

  try {
    // 提交答案到后端
    await interviewStore.submitQuestionAnswer({
      interviewId: pageId.value,
      questionId,
      answer
    })

    // 如果 WebSocket 连接正常，通过 WebSocket 发送
    if (wsManager.isConnected) {
      sendInterviewAnswer(answer, String(questionId))
    }
  } catch (error) {
    console.error('提交答案失败:', error)
    interviewStatus.value = 'answering'
    uni.showToast({
      title: '提交失败，请重试',
      icon: 'none'
    })
  }
}

// 开始录音
const startRecording = () => {
  isRecording.value = true
  recordingTime.value = 0

  recordingTimer.value = setInterval(() => {
    recordingTime.value++
  }, 1000) as unknown as number

  // TODO: 实际录音功能
  uni.showToast({
    title: '开始录音',
    icon: 'none'
  })
}

// 停止录音
const stopRecording = () => {
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }

  isRecording.value = false
  recordingTime.value = 0

  // TODO: 处理录音结果
  uni.showToast({
    title: '录音已停止',
    icon: 'none'
  })
}

// 格式化录音时间
const formatRecordingTime = (seconds: number): string => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

// 下一题
const goNextQuestion = () => {
  if (currentIndex.value < questions.value.length - 1) {
    interviewStore.nextQuestion()
    showCurrentQuestion()
  }
}

// 上一题
const goPrevQuestion = () => {
  if (currentIndex.value > 0) {
    interviewStore.prevQuestion()
    showCurrentQuestion()
  }
}

// 跳转到指定题目
const goToQuestion = (index: number) => {
  interviewStore.goToQuestion(index)
  showCurrentQuestion()
}

// 结束面试
const finishInterview = () => {
  uni.showModal({
    title: '确认结束',
    content: '确定要结束这场面试吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          // 通过 WebSocket 结束面试
          if (wsManager.isConnected) {
            wsManager.send(WebSocketMessageType.INTERVIEW_COMPLETE, {
              interviewId: String(pageId.value)
            })
          }

          wsManager.close()
          interviewStore.finishInterview()

          uni.showToast({
            title: '面试已结束',
            icon: 'success'
          })

          setTimeout(() => {
            uni.navigateBack()
          }, 1500)
        } catch (error) {
          console.error('结束面试失败:', error)
        }
      }
    }
  })
}

// 返回列表
const goBackToList = () => {
  wsManager.close()
  interviewStore.finishInterview()
  uni.navigateBack()
}

// 重新开始面试
const restartInterview = () => {
  pageMode.value = 'interview'
  interviewResult.value = null
  messages.value = []
  interviewStatus.value = 'idle'

  // 重新初始化
  initInterview()
}

// 分享面试报告
const shareReport = () => {
  uni.showToast({
    title: '分享功能开发中',
    icon: 'none'
  })
}

// 组件卸载时
onUnmounted(() => {
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
  }

  // 关闭 WebSocket 连接
  if (wsManager.isConnected) {
    wsManager.close()
  }
})
</script>

<template>
  <view class="interview-session-container">
    <!-- 顶部导航 -->
    <view class="session-header">
      <view class="header-left" @click="goBackToList">
        <text class="back-icon">&#xe61b;</text>
      </view>
      <view class="header-center">
        <text class="interview-title">{{ interview?.title || 'AI 模拟面试' }}</text>
        <text v-if="interviewStatus !== 'completed'" class="interview-status">
          {{ interviewStatus === 'connected' ? '已连接' :
             interviewStatus === 'answering' ? '回答中' :
             interviewStatus === 'evaluating' ? 'AI 评价中' : '准备中' }}
        </text>
      </view>
      <view class="header-right">
        <text v-if="interviewStatus !== 'completed'" class="finish-btn" @click="finishInterview">
          结束
        </text>
      </view>
    </view>

    <!-- 进度条 -->
    <view v-if="interviewStatus !== 'completed'" class="progress-bar">
      <view class="progress-info">
        <text class="progress-text">面试进度</text>
        <text class="progress-count">{{ currentIndex + 1 }} / {{ questions.length }}</text>
      </view>
      <view class="progress-track">
        <view class="progress-fill" :style="{ width: progress + '%' }"></view>
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="message-list"
      scroll-y
      :scroll-top="99999"
      :refresher-enabled="false"
    >
      <!-- 欢迎消息 -->
      <view v-if="messages.length === 0" class="welcome-message">
        <text class="welcome-icon">&#xe617;</text>
        <text class="welcome-text">AI 面试官正在准备问题</text>
        <text class="welcome-desc">请稍候...</text>
      </view>

      <!-- 消息列表 -->
      <view
        v-for="msg in messages"
        :key="msg.id"
        class="message-item"
        :class="msg.type"
      >
        <!-- 问题消息 -->
        <view v-if="msg.type === 'question'" class="question-bubble">
          <view class="ai-avatar">
            <text>&#xe617;</text>
          </view>
          <view class="bubble-content">
            <text class="bubble-text">{{ msg.content }}</text>
            <view v-if="currentQuestion?.tips" class="bubble-tips">
              <text class="tips-label">提示：</text>
              <text class="tips-text">{{ currentQuestion.tips }}</text>
            </view>
          </view>
        </view>

        <!-- 回答消息 -->
        <view v-else-if="msg.type === 'answer'" class="answer-bubble">
          <view class="bubble-content">
            <text class="bubble-text">{{ msg.content }}</text>
          </view>
          <view class="user-avatar">
            <text>&#xe60a;</text>
          </view>
        </view>

        <!-- 评价消息 -->
        <view v-else-if="msg.type === 'evaluation'" class="evaluation-bubble">
          <view class="ai-avatar">
            <text>&#xe617;</text>
          </view>
          <view class="bubble-content">
            <view class="eval-header">
              <text class="eval-title">AI 评价</text>
              <view v-if="msg.evaluation?.score" class="eval-score">
                {{ msg.evaluation.score }} 分
              </view>
            </view>
            <view v-if="msg.evaluation?.strengths?.length" class="eval-section">
              <text class="eval-label">优点：</text>
              <text v-for="(item, idx) in msg.evaluation.strengths" :key="idx" class="eval-tag strength">
                {{ item }}
              </text>
            </view>
            <view v-if="msg.evaluation?.improvements?.length" class="eval-section">
              <text class="eval-label">改进：</text>
              <text v-for="(item, idx) in msg.evaluation.improvements" :key="idx" class="eval-tag improvement">
                {{ item }}
              </text>
            </view>
            <text v-if="msg.evaluation?.suggestedAnswer" class="eval-suggested">
              <text class="suggested-label">参考答案：</text>
              {{ msg.evaluation.suggestedAnswer }}
            </text>
          </view>
        </view>

        <!-- 系统消息 -->
        <view v-else-if="msg.type === 'system'" class="system-message">
          <text>{{ msg.content }}</text>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="interviewStatus === 'evaluating'" class="loading-indicator">
        <view class="loading-dots">
          <view class="dot"></view>
          <view class="dot"></view>
          <view class="dot"></view>
        </view>
        <text class="loading-text">AI 正在分析你的回答...</text>
      </view>
    </scroll-view>

    <!-- 面试结果 -->
    <view v-if="interviewStatus === 'completed' && interviewResult" class="result-section">
      <view class="result-card">
        <view class="result-header">
          <text class="result-title">面试报告</text>
          <view class="result-score">
            <text class="score-value">{{ interviewResult.score }}</text>
            <text class="score-label">分</text>
          </view>
        </view>

        <view class="result-feedback">
          <text class="feedback-title">总体评价</text>
          <text class="feedback-text">{{ interviewResult.feedback }}</text>
        </view>

        <view v-if="interviewResult.strengths?.length" class="result-section">
          <text class="section-title">你的优势</text>
          <view class="tag-list">
            <text v-for="(item, idx) in interviewResult.strengths" :key="idx" class="tag strength">
              {{ item }}
            </text>
          </view>
        </view>

        <view v-if="interviewResult.improvements?.length" class="result-section">
          <text class="section-title">需要改进</text>
          <view class="tag-list">
            <text v-for="(item, idx) in interviewResult.improvements" :key="idx" class="tag improvement">
              {{ item }}
            </text>
          </view>
        </view>

        <view class="result-stats">
          <view class="stat-item">
            <text class="stat-value">{{ questions.length }}</text>
            <text class="stat-label">回答题目</text>
          </view>
          <view class="stat-item">
            <text class="stat-value">{{ interviewResult.duration }}分钟</text>
            <text class="stat-label">面试时长</text>
          </view>
        </view>

        <view class="result-actions">
          <view class="action-btn primary" @click="restartInterview">
            重新面试
          </view>
          <view class="action-btn secondary" @click="shareReport">
            分享报告
          </view>
        </view>
      </view>
    </view>

    <!-- 回答区域 -->
    <view v-if="interviewStatus !== 'completed'" class="answer-section">
      <!-- 题目导航 -->
      <view class="question-nav">
        <view
          v-for="(q, idx) in questions"
          :key="q.id"
          class="nav-dot"
          :class="{
            active: idx === currentIndex,
            answered: q.answerStatus === 'answered' || q.answerStatus === 'evaluated'
          }"
          @click="goToQuestion(idx)"
        >
          {{ idx + 1 }}
        </view>
      </view>

      <!-- 输入区域 -->
      <view class="input-area">
        <textarea
          v-model="answerText"
          class="answer-input"
          placeholder="请输入你的回答..."
          placeholder-class="input-placeholder"
          :disabled="interviewStatus === 'evaluating'"
          :maxlength="2000"
        />
        <view class="input-actions">
          <!-- 录音按钮 -->
          <view
            class="action-icon"
            :class="{ recording: isRecording }"
            @click="isRecording ? stopRecording() : startRecording()"
          >
            <text v-if="!isRecording">&#xe618;</text>
            <text v-else class="stop-icon">&#xe61c;</text>
          </view>

          <!-- 发送按钮 -->
          <view
            class="send-btn"
            :class="{ disabled: !answerText.trim() || interviewStatus === 'evaluating' }"
            @click="submitAnswer"
          >
            <text>发送</text>
          </view>
        </view>

        <!-- 录音时间显示 -->
        <view v-if="isRecording" class="recording-time">
          <text class="recording-icon">&#xe618;</text>
          <text>{{ formatRecordingTime(recordingTime) }}</text>
        </view>
      </view>

      <!-- 底部提示 -->
      <view class="bottom-tip">
        <text>回答完成后点击发送，AI 将实时评价你的回答</text>
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

.interview-session-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
}

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}

.header-left {
  .back-icon {
    font-size: 40rpx;
    color: #333;
  }
}

.header-center {
  flex: 1;
  text-align: center;

  .interview-title {
    display: block;
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
  }

  .interview-status {
    display: block;
    font-size: 24rpx;
    color: $primary-color;
    margin-top: 4rpx;
  }
}

.header-right {
  .finish-btn {
    padding: 12rpx 24rpx;
    background-color: #fef0f0;
    color: #F56C6C;
    border-radius: 32rpx;
    font-size: 26rpx;
  }
}

.progress-bar {
  padding: 20rpx 30rpx;
  background-color: #fff;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12rpx;

  .progress-text {
    font-size: 24rpx;
    color: #999;
  }

  .progress-count {
    font-size: 24rpx;
    color: $primary-color;
    font-weight: 500;
  }
}

.progress-track {
  height: 8rpx;
  background-color: #f0f0f0;
  border-radius: 4rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, $primary-color 0%, $primary-light 100%);
  border-radius: 4rpx;
  transition: width 0.3s;
}

.message-list {
  flex: 1;
  padding: 30rpx;
  overflow: hidden;
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  .welcome-icon {
    font-size: 80rpx;
    color: $primary-color;
    margin-bottom: 24rpx;
  }

  .welcome-text {
    font-size: 32rpx;
    color: #333;
    margin-bottom: 12rpx;
  }

  .welcome-desc {
    font-size: 26rpx;
    color: #999;
  }
}

.message-item {
  margin-bottom: 30rpx;

  &.question,
  &.evaluation {
    display: flex;
    align-items: flex-start;
  }

  &.answer {
    display: flex;
    justify-content: flex-end;
  }

  &.system {
    text-align: center;
    padding: 20rpx;
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
}

.question-bubble,
.evaluation-bubble {
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
  }
}

.answer-bubble {
  .bubble-content {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border-radius: 24rpx;
    padding: 24rpx;

    .bubble-text {
      color: #fff;
    }
  }
}

.bubble-tips {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f0f0f0;

  .tips-label {
    font-size: 24rpx;
    color: $primary-color;
    font-weight: 500;
  }

  .tips-text {
    font-size: 24rpx;
    color: #999;
  }
}

.eval-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;

  .eval-title {
    font-size: 28rpx;
    font-weight: 600;
    color: #333;
  }

  .eval-score {
    padding: 8rpx 20rpx;
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    color: #fff;
    border-radius: 20rpx;
    font-size: 28rpx;
    font-weight: 600;
  }
}

.eval-section {
  margin-top: 16rpx;

  .eval-label {
    font-size: 24rpx;
    color: #999;
    display: block;
    margin-bottom: 8rpx;
  }

  .eval-tag {
    display: inline-block;
    padding: 8rpx 16rpx;
    border-radius: 8rpx;
    font-size: 24rpx;
    margin-right: 12rpx;
    margin-bottom: 12rpx;

    &.strength {
      background-color: #eef2ff;
      color: #6366f1;
    }

    &.improvement {
      background-color: #fff3e0;
      color: #ff9800;
    }
  }
}

.eval-suggested {
  display: block;
  margin-top: 16rpx;
  padding: 16rpx;
  background-color: #f5f5f5;
  border-radius: 8rpx;
  font-size: 24rpx;
  color: #666;
  line-height: 1.6;

  .suggested-label {
    color: $primary-color;
    font-weight: 500;
  }
}

.system-message {
  text {
    display: inline-block;
    padding: 12rpx 24rpx;
    background-color: #f0f0f0;
    border-radius: 32rpx;
    font-size: 24rpx;
    color: #999;
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

.result-section {
  flex: 1;
  padding: 30rpx;
  overflow-y: auto;
}

.result-card {
  background-color: #fff;
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.08);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40rpx;

  .result-title {
    font-size: 36rpx;
    font-weight: 600;
    color: #333;
  }

  .result-score {
    display: flex;
    align-items: baseline;

    .score-value {
      font-size: 64rpx;
      font-weight: bold;
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .score-label {
      font-size: 28rpx;
      color: #999;
      margin-left: 8rpx;
    }
  }
}

.result-feedback {
  padding: 24rpx;
  background-color: #f5f5f5;
  border-radius: 12rpx;
  margin-bottom: 30rpx;

  .feedback-title {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
  }

  .feedback-text {
    font-size: 28rpx;
    color: #666;
    line-height: 1.6;
  }
}

.result-section {
  margin-bottom: 30rpx;

  .section-title {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 16rpx;
  }
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;

  .tag {
    display: inline-block;
    padding: 12rpx 20rpx;
    border-radius: 8rpx;
    font-size: 26rpx;

    &.strength {
      background-color: #eef2ff;
      color: #6366f1;
    }

    &.improvement {
      background-color: #fff3e0;
      color: #ff9800;
    }
  }
}

.result-stats {
  display: flex;
  justify-content: space-around;
  padding: 30rpx 0;
  border-top: 1rpx solid #f0f0f0;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 30rpx;

  .stat-item {
    text-align: center;

    .stat-value {
      display: block;
      font-size: 36rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 8rpx;
    }

    .stat-label {
      font-size: 24rpx;
      color: #999;
    }
  }
}

.result-actions {
  display: flex;
  gap: 24rpx;

  .action-btn {
    flex: 1;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 44rpx;
    font-size: 30rpx;
    font-weight: 500;

    &.primary {
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      color: #fff;
    }

    &.secondary {
      background-color: #f5f5f5;
      color: #666;
    }
  }
}

.answer-section {
  background-color: #fff;
  border-top: 1rpx solid #f0f0f0;
}

.question-nav {
  display: flex;
  justify-content: center;
  gap: 16rpx;
  padding: 20rpx;
  border-bottom: 1rpx solid #f0f0f0;

  .nav-dot {
    width: 48rpx;
    height: 48rpx;
    border-radius: 50%;
    background-color: #f0f0f0;
    color: #999;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24rpx;
    transition: all 0.3s;

    &.active {
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      color: #fff;
      transform: scale(1.1);
    }

    &.answered {
      background-color: #eef2ff;
      color: #6366f1;
    }
  }
}

.input-area {
  padding: 20rpx 30rpx;
}

.answer-input {
  width: 100%;
  min-height: 160rpx;
  padding: 20rpx;
  background-color: #f5f5f5;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;

  .input-placeholder {
    color: #999;
  }
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
}

.action-icon {
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

  &.recording {
    background-color: #F56C6C;
    color: #fff;
    animation: pulse 1s infinite;

    .stop-icon {
      font-size: 24rpx;
    }
  }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.send-btn {
  padding: 16rpx 48rpx;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: 36rpx;
  color: #fff;
  font-size: 28rpx;
  font-weight: 500;
  transition: all 0.3s;

  &.disabled {
    opacity: 0.5;
  }
}

.recording-time {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #F56C6C;

  .recording-icon {
    animation: pulse 1s infinite;
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
