<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useInterviewStore } from '../../stores/interview'
import { getSessionProgress, connectInterviewStream, SSE_EVENT_TYPES, PROGRESS_LABELS, submitAnswerAdaptive, endInterview } from '../../api/interview'

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
const pollTimer = ref<number | null>(null)

// 滚动相关
const scrollTop = ref(0)
const scrollViewRef = ref<any>(null)

// 面试状态
const interviewStatus = ref<'idle' | 'connected' | 'answering' | 'evaluating' | 'completed'>('idle')
// 跳转报告页面时的加载蒙版
const showReportLoading = ref(false)
// SSE 连接清理函数
const sseCleanup = ref<(() => void) | null>(null)
// 是否正在加载下一题（显示遮罩）
const isLoadingNextQuestion = ref(false)
// 进度阶段状态（用于显示不同的加载提示）
const progressStage = ref<string | null>(null)

// 恢复的当前问题（用于会话进度恢复）
const restoredCurrentQuestion = ref<{
  questionIndex: number
  question: string
  category: string
  difficulty?: string
  knowledgeBaseName?: string | null
  createdByPerspectiveId?: number
  createdByPerspectiveName?: string
} | null>(null)

// 消息列表类型（与Web端一致）
interface MessageItem {
  id: number
  type: 'interviewer' | 'user' | 'system' | 'evaluation'
  content: string
  category?: string
  questionIndex?: number
  difficulty?: string
  knowledgeBaseName?: string | null
  isFollowUp?: boolean
  relatedIndex?: number
  relatedQuestion?: string
  timestamp: number
  // 多视角扩展字段
  createdByPerspectiveId?: number
  createdByPerspectiveName?: string
  // 评价数据
  evaluation?: {
    score: number
    strength: string[]
    improvements: string[]
    suggestedAnswer?: string
    overallFeedback?: string
  }
}

// 消息列表
const messages = ref<MessageItem[]>([])

// 会话进度信息
const sessionTotalQuestions = ref(0) // 从会话进度中获取的总题数
const totalQuestions = computed(() => sessionTotalQuestions.value || interview.value?.totalQuestions || 0)
const currentQuestionIndex = computed(() => restoredCurrentQuestion.value?.questionIndex ?? 0)
const progressPercent = computed(() => {
  if (!totalQuestions.value || currentQuestionIndex.value == null) return 0
  return Math.round(((currentQuestionIndex.value + 1) / totalQuestions.value) * 100)
})
const currentQuestionInfo = computed(() => restoredCurrentQuestion.value || null)

// 难度颜色映射
const getDifficultyStyle = (difficulty?: string) => {
  const styles: Record<string, { bg: string; color: string }> = {
    'BASIC': { bg: '#d1fae5', color: '#059669' },
    '基础': { bg: '#d1fae5', color: '#059669' },
    'ADVANCED': { bg: '#fef3c7', color: '#d97706' },
    '进阶': { bg: '#fef3c7', color: '#d97706' },
    'EXPERT': { bg: '#fee2e2', color: '#dc2626' },
    '专家': { bg: '#fee2e2', color: '#dc2626' }
  }
  return styles[difficulty || ''] || { bg: '#f5f5f5', color: '#666' }
}

// 难度标签映射
const getDifficultyLabel = (difficulty?: string) => {
  const labels: Record<string, string> = {
    'BASIC': '基础',
    '基础': '基础',
    'ADVANCED': '进阶',
    '进阶': '进阶',
    'EXPERT': '专家',
    '专家': '专家'
  }
  return labels[difficulty || ''] || difficulty || ''
}

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
  console.log('[Session] page options:', options)

  pageId.value = options.id || ''
  pageMode.value = options.mode === 'result' ? 'result' : 'interview'

  // 如果 URL 中有 total 参数（新面试），设置总题数
  if (options.total) {
    sessionTotalQuestions.value = Number(options.total) || 0
    console.log('[Session] set totalQuestions from URL:', sessionTotalQuestions.value)
  }

  if (pageId.value) {
    initInterview()
  }
})

// 初始化面试
const initInterview = async () => {
  try {
    console.log('[Session] initInterview start, pageId:', pageId.value, 'mode:', pageMode.value)

    // 如果是结果模式，获取面试结果
    if (pageMode.value === 'result') {
      console.log('[Session] result mode - fetching result...')
      const result = await interviewStore.fetchInterviewResult(pageId.value)
      interviewResult.value = result
      interviewStatus.value = 'completed'
      console.log('[Session] result mode ready')
      return
    }

    // 获取会话进度（包括历史记录和当前问题）
    console.log('[Session] fetching session progress...')
    const progress = await getSessionProgress(pageId.value)
    console.log('[Session] progress fetched:', progress)

    // 保存总题数（如果有有效的总题数则更新，否则保留 URL 中的值）
    if (progress.totalQuestions > 0) {
      sessionTotalQuestions.value = progress.totalQuestions
    }

    // 检查面试是否已完成
    if (progress.currentQuestionIndex >= progress.totalQuestions) {
      interviewStatus.value = 'completed'
      console.log('[Session] interview completed')
      return
    }

    // 恢复历史消息
    console.log('[Session] restoring history messages...')
    messages.value = []
    for (const historyItem of progress.history) {
      // 添加问题消息
      messages.value.push({
        id: Date.now() + Math.random(),
        type: 'interviewer',
        content: historyItem.question,
        category: historyItem.category,
        questionIndex: historyItem.questionIndex,
        difficulty: historyItem.difficulty,
        timestamp: Date.now(),
        createdByPerspectiveId: historyItem.createdByPerspectiveId,
        createdByPerspectiveName: historyItem.createdByPerspectiveName
      })
      // 添加用户回答
      messages.value.push({
        id: Date.now() + Math.random(),
        type: 'user',
        content: historyItem.userAnswer,
        timestamp: Date.now()
      })
    }

    // 保存当前问题
    if (progress.currentQuestion) {
      restoredCurrentQuestion.value = {
        questionIndex: progress.currentQuestion.questionIndex,
        question: progress.currentQuestion.question,
        category: progress.currentQuestion.category,
        difficulty: progress.currentQuestion.difficulty,
        knowledgeBaseName: progress.currentQuestion.knowledgeBaseName,
        createdByPerspectiveId: progress.currentQuestion.createdByPerspectiveId,
        createdByPerspectiveName: progress.currentQuestion.createdByPerspectiveName
      }
      // 添加当前问题到消息列表
      messages.value.push({
        id: Date.now() + Math.random(),
        type: 'interviewer',
        content: progress.currentQuestion.question,
        category: progress.currentQuestion.category,
        questionIndex: progress.currentQuestion.questionIndex,
        difficulty: progress.currentQuestion.difficulty,
        knowledgeBaseName: progress.currentQuestion.knowledgeBaseName,
        timestamp: Date.now(),
        createdByPerspectiveId: progress.currentQuestion.createdByPerspectiveId,
        createdByPerspectiveName: progress.currentQuestion.createdByPerspectiveName
      })
    }

    // 开始面试状态
    console.log('[Session] starting interview with restored session...')
    interviewStatus.value = 'answering'

    // 建立 SSE 连接
    setupSSEConnection()

    console.log('[Session] initInterview done')
  } catch (error) {
    console.error('初始化面试失败:', error)
    uni.showToast({
      title: '加载失败',
      icon: 'none'
    })
  }
}

// 建立 SSE 连接
const setupSSEConnection = () => {
  // 清理之前的连接
  if (sseCleanup.value) {
    sseCleanup.value()
    sseCleanup.value = null
  }

  // 建立 SSE 连接
  sseCleanup.value = connectInterviewStream(pageId.value, {
    onConnected: () => {
      console.log('[SSE] connected')
      interviewStatus.value = 'connected'
    },
    onProgress: (stage: string) => {
      console.log('[SSE] received progress stage:', stage)
      progressStage.value = stage
      if (stage === 'QUESTION_GENERATING') {
        isLoadingNextQuestion.value = true
      }
    },
    onQuestion: (question) => {
      console.log('[SSE] received question:', question)
      // 清空进度阶段状态
      progressStage.value = null
      // 更新当前问题
      restoredCurrentQuestion.value = {
        questionIndex: question.questionIndex,
        question: question.question,
        category: question.category,
        difficulty: question.difficulty,
        knowledgeBaseName: question.knowledgeBaseName,
        createdByPerspectiveId: question.createdByPerspectiveId,
        createdByPerspectiveName: question.createdByPerspectiveName
      }
      // 添加问题到消息列表
      messages.value.push({
        id: Date.now() + Math.random(),
        type: 'interviewer',
        content: question.question,
        category: question.category,
        questionIndex: question.questionIndex,
        difficulty: question.difficulty,
        knowledgeBaseName: question.knowledgeBaseName,
        timestamp: Date.now(),
        createdByPerspectiveId: question.createdByPerspectiveId,
        createdByPerspectiveName: question.createdByPerspectiveName
      })
      // 隐藏加载遮罩
      isLoadingNextQuestion.value = false
      interviewStatus.value = 'answering'
      scrollToBottom()
    },
    onEvaluation: (evaluation) => {
      console.log('[SSE] received evaluation:', evaluation)
      // 评估结果可以用于更新显示，但不阻塞流程
    },
    onComplete: (data) => {
      console.log('[SSE] interview complete:', data)
      interviewStatus.value = 'completed'
      progressStage.value = null
      addSystemMessage('面试已完成')
      // 跳转到报告页面
      uni.redirectTo({
        url: `/pages/interview/report?id=${pageId.value}`
      })
    },
    onError: (errorMsg) => {
      console.error('[SSE] error:', errorMsg)
      progressStage.value = null
      uni.showToast({
        title: errorMsg || '连接错误',
        icon: 'none'
      })
      isLoadingNextQuestion.value = false
    }
  })
}

// 显示当前问题
const showCurrentQuestion = () => {
  if (restoredCurrentQuestion.value) {
    // 使用恢复的问题
    addQuestionMessageFromContent(restoredCurrentQuestion.value.question)
  } else if (currentQuestion.value) {
    addQuestionMessage(currentQuestion.value)
  }
}

// 从内容添加问题消息（用于恢复的会话）
const addQuestionMessageFromContent = (content: string) => {
  messages.value.push({
    id: Date.now() + Math.random(),
    type: 'interviewer',
    content: content,
    timestamp: Date.now()
  })
  setTimeout(() => {
    scrollToBottom()
  }, 100)
}

// 添加问题消息
const addQuestionMessage = (question: InterviewQuestion) => {
  messages.value.push({
    id: Date.now(),
    type: 'interviewer',
    content: question.question || question.content || '',
    questionIndex: question.questionIndex,
    timestamp: Date.now()
  })

  // 滚动到底部
  setTimeout(() => {
    scrollToBottom()
  }, 100)
}

// 添加回答消息
const addAnswerMessage = (answer: string, questionIndex: number) => {
  messages.value.push({
    id: Date.now(),
    type: 'user',
    content: answer,
    questionIndex,
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
      questionIndex: data.questionIndex as number,
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
    // 触发 scroll-view 滚动到最新消息
    const newScrollTop = scrollTop.value + 100
    scrollTop.value = newScrollTop > 99999 ? 0 : newScrollTop
    // 强制触发滚动
    nextTick(() => {
      scrollTop.value = 99999
    })
  }, 200)
}

// 提交回答
const submitAnswer = async () => {
  const answer = answerText.value.trim()
  if (!answer || !restoredCurrentQuestion.value) return

  const submittedAnswer = answer
  const questionIndex = restoredCurrentQuestion.value.questionIndex

  // 添加回答消息
  addAnswerMessage(submittedAnswer, questionIndex)

  // 清空输入
  answerText.value = ''

  // 显示正在加载下一题
  isLoadingNextQuestion.value = true
  interviewStatus.value = 'evaluating'
  isSubmitting.value = true

  try {
    // 使用 submitAnswerAdaptive API（立即返回，SSE 推送下一题）
    await submitAnswerAdaptive(pageId.value, questionIndex, submittedAnswer)
    // 等待 SSE 事件来更新 UI（nextQuestion 或 complete）
  } catch (error) {
    console.error('提交答案失败:', error)
    interviewStatus.value = 'answering'
    isLoadingNextQuestion.value = false
    isSubmitting.value = false
    uni.showToast({
      title: '提交失败，请重试',
      icon: 'none'
    })
  }
}

// 跳转到下一题
const goToNextQuestion = () => {
  if (currentIndex.value < questions.value.length - 1) {
    interviewStore.nextQuestion()
    showCurrentQuestion()
    interviewStatus.value = 'answering'
    scrollToBottom()
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

// 跳转到报告页面
const goToReport = () => {
  showReportLoading.value = false
  uni.redirectTo({
    url: `/pages/interview/report?id=${pageId.value}`
  })
}

// 结束面试并等待结果
const finishInterviewAndWaitForResult = async () => {
  try {
    // 调用后端接口结束面试
    await endInterview(pageId.value)

    // 显示加载蒙版，等待评估完成
    interviewStatus.value = 'evaluating'
    showReportLoading.value = true

    // 轮询等待评估完成
    const pollResult = async () => {
      try {
        const status = await interviewStore.fetchEvaluateStatus(pageId.value)

        if (status && status.overallScore !== null && status.overallScore !== undefined) {
          goToReport()
        } else {
          pollTimer.value = setTimeout(pollResult, 2000) as unknown as number
        }
      } catch (error) {
        pollTimer.value = setTimeout(pollResult, 2000) as unknown as number
      }
    }

    setTimeout(pollResult, 2000)
  } catch (error) {
    console.error('结束面试失败:', error)
    showReportLoading.value = false
    uni.showToast({
      title: '结束面试失败',
      icon: 'none'
    })
  }
}

// 结束面试
const finishInterview = () => {
  uni.showModal({
    title: '确认结束',
    content: '确定要结束这场面试吗？',
    success: async (res) => {
      if (res.confirm) {
        finishInterviewAndWaitForResult()
      }
    }
  })
}

// 返回列表
const goBackToList = () => {
  interviewStore.reset()
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

  // 停止轮询
  if (pollTimer.value) {
    clearTimeout(pollTimer.value)
  }

  // 关闭 SSE 连接
  if (sseCleanup.value) {
    sseCleanup.value()
    sseCleanup.value = null
  }
})
</script>

<template>
  <view class="interview-session-container">
    <!-- 加载遮罩 -->
    <view v-if="showReportLoading" class="loading-overlay">
      <view class="loading-content">
        <view class="loading-spinner"></view>
        <text class="loading-text">AI 正在生成评估报告</text>
        <text class="loading-subtext">请稍候...</text>
      </view>
    </view>

    <!-- 主内容区域 -->
    <view class="main-content">
      <!-- 页面标题 -->
      <view class="page-header">
        <text class="page-title">模拟面试</text>
        <text class="page-subtitle">认真回答每个问题，展示您的实力</text>
      </view>

      <!-- 进度区域 -->
      <view class="progress-section">
        <view class="progress-header">
          <text class="progress-text">
            题目 {{ currentQuestionIndex + 1 }} / {{ totalQuestions }}
            <text v-if="currentQuestionInfo?.createdByPerspectiveName" class="perspective-indicator">[{{ currentQuestionInfo.createdByPerspectiveName }}]</text>
          </text>
          <text class="progress-percent">{{ progressPercent }}%</text>
        </view>
        <view class="progress-bar">
          <view class="progress-fill" :style="{ width: progressPercent + '%' }"></view>
        </view>
      </view>

      <!-- 聊天区域 -->
      <scroll-view
        class="chat-section"
        scroll-y
        :scroll-top="scrollTop"
        :refresher-enabled="false"
      >
        <!-- 欢迎消息 -->
        <view v-if="messages.length === 0" class="welcome-message">
          <view class="welcome-icon-wrapper">
            <svg class="welcome-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <circle cx="12" cy="12" r="6"/>
              <circle cx="12" cy="12" r="2"/>
            </svg>
          </view>
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
          <!-- 面试官消息 -->
          <view v-if="msg.type === 'interviewer'" class="avatar interviewer-avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </view>
          <view v-if="msg.type === 'interviewer'" class="message-body">
            <view class="message-tags">
              <text class="message-label">面试官</text>
              <text v-if="msg.createdByPerspectiveName" class="tag perspective-tag">{{ msg.createdByPerspectiveName }}</text>
              <text v-if="msg.category" class="tag category-tag">{{ msg.category }}</text>
              <text v-if="msg.difficulty" class="tag difficulty-tag" :style="{ backgroundColor: getDifficultyStyle(msg.difficulty).bg, color: getDifficultyStyle(msg.difficulty).color }">
                {{ getDifficultyLabel(msg.difficulty) }}
              </text>
            </view>
            <view class="bubble interviewer-bubble">{{ msg.content }}</view>
          </view>

          <!-- 用户消息 -->
          <view v-if="msg.type === 'user'" class="user-message-wrapper">
            <view class="message-body">
              <view class="bubble user-bubble">{{ msg.content }}</view>
            </view>
            <view class="avatar user-avatar">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </view>
          </view>
        </view>

        <!-- 加载状态 -->
        <view v-if="isLoadingNextQuestion || interviewStatus === 'evaluating'" class="loading-indicator">
          <view class="loading-dots">
            <view class="dot"></view>
            <view class="dot"></view>
            <view class="dot"></view>
          </view>
          <text class="loading-text">{{ progressStage ? PROGRESS_LABELS[progressStage] || progressStage : (isLoadingNextQuestion ? '正在出题中...' : 'AI 正在分析你的回答...') }}</text>
        </view>
      </scroll-view>

      <!-- 输入区域 -->
      <view v-if="interviewStatus !== 'completed'" class="input-section">
        <view class="input-row">
          <textarea
            v-model="answerText"
            class="answer-input"
            placeholder="输入你的回答..."
            placeholder-class="input-placeholder"
            :disabled="interviewStatus === 'evaluating' || isSubmitting || !!progressStage"
            :maxlength="2000"
          />
          <view class="button-group">
            <view
              class="submit-btn"
              :class="{ disabled: !answerText.trim() || interviewStatus === 'evaluating' || isSubmitting || !!progressStage }"
              @click="submitAnswer"
            >
              <text v-if="progressStage">{{ PROGRESS_LABELS[progressStage] || progressStage }}</text>
              <text v-else-if="isSubmitting || interviewStatus === 'evaluating'">提交中...</text>
              <text v-else>提交</text>
            </view>
            <view
              class="early-exit-btn"
              :class="{ disabled: isSubmitting || !!progressStage }"
              @click="finishInterview"
            >
              <text>提前交卷</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

// 主容器
.interview-session-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
}

// 主内容区域
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

// 页面标题
.page-header {
  padding: 40rpx 30rpx 20rpx;
  text-align: center;

  .page-title {
    display: block;
    font-size: 36rpx;
    font-weight: 700;
    color: #1e293b;
    margin-bottom: 8rpx;
  }

  .page-subtitle {
    display: block;
    font-size: 26rpx;
    color: #64748b;
  }
}

// 进度区域
.progress-section {
  background-color: #fff;
  margin: 0 20rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  margin-bottom: 16rpx;

  .progress-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12rpx;

    .progress-text {
      font-size: 28rpx;
      font-weight: 600;
      color: #334155;
    }

    .perspective-indicator {
      font-size: 24rpx;
      color: $primary-color;
      font-weight: 600;
      margin-left: 12rpx;
    }

    .progress-percent {
      font-size: 26rpx;
      color: #64748b;
    }
  }

  .progress-bar {
    height: 12rpx;
    background-color: #e2e8f0;
    border-radius: 6rpx;
    overflow: hidden;

    .progress-fill {
      height: 100%;
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      border-radius: 6rpx;
      transition: width 0.3s ease;
    }
  }

  .question-tags {
    display: flex;
    gap: 12rpx;
    margin-top: 16rpx;
    flex-wrap: wrap;

    .tag {
      padding: 6rpx 16rpx;
      border-radius: 8rpx;
      font-size: 22rpx;
    }

    .category-tag {
      background-color: rgba(99, 102, 241, 0.1);
      color: $primary-color;
    }

    .difficulty-tag {
      border: 1rpx solid;
    }
  }
}

// 聊天区域
.chat-section {
  flex: 1;
  min-height: 0;
  padding: 0 20rpx;
  overflow-y: auto;
}

// 欢迎消息
.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  .welcome-icon-wrapper {
    width: 100rpx;
    height: 100rpx;
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24rpx;

    .welcome-icon {
      width: 48rpx;
      height: 48rpx;
      color: #fff;
    }
  }

  .welcome-text {
    font-size: 32rpx;
    color: #1e293b;
    font-weight: 600;
    margin-bottom: 12rpx;
  }

  .welcome-desc {
    font-size: 26rpx;
    color: #94a3b8;
  }
}

// 消息项
.message-item {
  margin-bottom: 30rpx;

  &.interviewer {
    display: flex;
    align-items: flex-start;

    .avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      svg {
        width: 36rpx;
        height: 36rpx;
      }
    }

    .interviewer-avatar {
      background-color: #ede9fe;
      margin-right: 16rpx;

      svg {
        color: $primary-color;
      }
    }

    .message-body {
      flex: 1;
      max-width: 75%;
    }

    .message-tags {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-bottom: 10rpx;
      flex-wrap: wrap;

      .message-label {
        font-size: 24rpx;
        font-weight: 600;
        color: #475569;
      }

      .tag {
        padding: 4rpx 12rpx;
        border-radius: 6rpx;
        font-size: 20rpx;
      }

      .category-tag {
        background-color: rgba(99, 102, 241, 0.1);
        color: $primary-color;
      }

      .perspective-tag {
        background-color: rgba(99, 102, 241, 0.2);
        color: $primary-dark;
        font-weight: 600;
      }

      .difficulty-tag {
        border: 1rpx solid;
      }
    }

    .bubble {
      padding: 24rpx;
      border-radius: 20rpx;
      font-size: 28rpx;
      line-height: 1.6;
      word-break: break-all;
    }

    .interviewer-bubble {
      background-color: rgba(139, 92, 246, 0.1);
      color: #1e293b;
      border-top-left-radius: 8rpx;
    }
  }

  &.user {
    display: flex;
    align-items: flex-start;
    justify-content: flex-end;

    .avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      margin-left: 16rpx;

      svg {
        width: 36rpx;
        height: 36rpx;
      }
    }

    .user-avatar {
      background-color: #f1f5f9;

      svg {
        color: #64748b;
      }
    }

    .message-body {
      max-width: 70%;
    }

    .bubble {
      padding: 24rpx;
      border-radius: 20rpx;
      font-size: 28rpx;
      line-height: 1.6;
      word-break: break-all;
    }

    .user-bubble {
      background-color: $primary-color;
      color: #fff;
      border-top-right-radius: 8rpx;
    }
  }
}

// 用户消息包装器
.user-message-wrapper {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding-right: 20rpx;

  .message-body {
    margin-right: 16rpx;
  }
}

// 加载状态
.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30rpx 0;

  .loading-dots {
    display: flex;
    gap: 8rpx;
    margin-bottom: 16rpx;

    .dot {
      width: 12rpx;
      height: 12rpx;
      border-radius: 50%;
      background-color: $primary-color;
      animation: bounce 1.4s infinite ease-in-out both;

      &:nth-child(1) { animation-delay: -0.32s; }
      &:nth-child(2) { animation-delay: -0.16s; }
    }
  }

  .loading-text {
    font-size: 26rpx;
    color: #64748b;
  }
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

// 输入区域
.input-section {
  flex-shrink: 0;
  padding: 20rpx;
  background-color: #fff;
  border-top: 1rpx solid #e2e8f0;

  .input-row {
    display: flex;
    gap: 16rpx;
    align-items: flex-end;
  }

  .answer-input {
    flex: 1;
    min-height: 80rpx;
    max-height: 200rpx;
    padding: 20rpx;
    background-color: #f8fafc;
    border: 2rpx solid #e2e8f0;
    border-radius: 16rpx;
    font-size: 28rpx;
    line-height: 1.5;
    color: #1e293b;
  }

  .input-placeholder {
    color: #94a3b8;
  }

  .button-group {
    display: flex;
    flex-direction: column;
    gap: 12rpx;
  }

  .submit-btn {
    padding: 20rpx 32rpx;
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border-radius: 16rpx;
    font-size: 28rpx;
    font-weight: 600;
    color: #fff;
    text-align: center;

    &.disabled {
      opacity: 0.5;
    }
  }

  .early-exit-btn {
    padding: 16rpx 24rpx;
    background-color: #f1f5f9;
    border-radius: 12rpx;
    font-size: 24rpx;
    color: #64748b;
    text-align: center;
  }
}

// 加载遮罩
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
      color: #1e293b;
      font-weight: 600;
    }

    .loading-subtext {
      font-size: 24rpx;
      color: #64748b;
      margin-top: 8rpx;
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
