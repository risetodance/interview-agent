<script setup lang="ts">
import { ref, computed, toRef, onMounted, onUnmounted, nextTick } from 'vue'
import { onUnload, onHide } from '@dcloudio/uni-app'
import { useInterviewStore } from '../../stores/interview'
import { getCurrentQuestion, getSessionProgress, connectInterviewStream, SSE_EVENT_TYPES, PROGRESS_LABELS, submitAnswerAdaptive, endInterview, type StreamCurrentQuestionDTO } from '../../api/interview'

// 路由参数
const pageId = ref<string>('')
const pageMode = ref<'interview' | 'result'>('interview')
// 页面选项（用于判断是新面试还是恢复面试）
const pageOptions = ref<Record<string, any>>({})

// Store
const interviewStore = useInterviewStore()

// 数据
const interview = computed(() => interviewStore.currentInterview)
const questions = computed(() => interviewStore.currentQuestions)
const currentQuestion = computed(() => interviewStore.currentQuestion)
const progress = computed(() => interviewStore.progress)
const currentIndex = computed(() => interviewStore.currentQuestionIndex)
const isLoading = computed(() => interviewStore.isLoading)
// 使用 toRef 保持与 store 状态的响应式连接，允许直接赋值
const isSubmitting = toRef(interviewStore, 'isSubmitting')

// UI状态
const answerText = ref('')
const isRecording = ref(false)
const recordingTime = ref(0)
const recordingTimer = ref<number | null>(null)

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
// SSE 重连：H5 的 EventSource / 小程序 chunked request 在弱网或后端 broken pipe 后会断开，
// 不自动重连会导致后续提交答案收不到实时事件、只能反复走 healFromServer。这里在非提交态延迟重连。
const sseReconnectTimer = ref<number | null>(null)
const SSE_RECONNECT_DELAY = 2000
const SSE_RECONNECT_MAX = 5
let sseReconnectAttempts = 0
// 进度阶段状态（用于显示不同的加载提示）
const progressStage = ref<string | null>(null)

// 已渲染过的题目 questionIndex 集合：submitAnswer 返回值与 SSE onQuestion 去重（NC1）
const renderedQuestionIndices = ref<Set<number>>(new Set())

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

// 评分颜色（B14 实时评分气泡）
const getScoreColor = (score?: number | null) => {
  if (score === undefined || score === null) return '#64748b'
  if (score >= 80) return '#16a34a'
  if (score >= 60) return '#d97706'
  return '#dc2626'
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
  // 获取页面参数

  pageId.value = options.id || ''
  pageMode.value = options.mode === 'result' ? 'result' : 'interview'
  pageOptions.value = options

  // 如果 URL 中有 total 参数（新面试），设置总题数
  if (options.total) {
    sessionTotalQuestions.value = Number(options.total) || 0
  }

  if (pageId.value) {
    initInterview()
  }
})

// 初始化面试
const initInterview = async () => {
  try {

    // 如果是结果模式，获取面试结果
    if (pageMode.value === 'result') {
      const result = await interviewStore.fetchInterviewResult(pageId.value)
      interviewResult.value = result
      interviewStatus.value = 'completed'
      return
    }

    // 判断是否是新创建的面试（有 total 参数）还是恢复已有面试（只有 id）
    const isNewSession = !!pageOptions.value.total

    if (isNewSession) {
      // 新创建的面试：先调用 getCurrentQuestion 获取第一题
      isLoadingNextQuestion.value = true
      try {
        const firstQuestion = await getCurrentQuestion(pageId.value)

        // 保存总题数和当前问题
        if (firstQuestion.questionIndex !== undefined) {
          sessionTotalQuestions.value = sessionTotalQuestions.value || 0
        }

        restoredCurrentQuestion.value = {
          questionIndex: firstQuestion.questionIndex,
          question: firstQuestion.question,
          category: firstQuestion.category,
          difficulty: firstQuestion.difficulty,
          knowledgeBaseName: firstQuestion.knowledgeBaseName,
          createdByPerspectiveId: firstQuestion.createdByPerspectiveId,
          createdByPerspectiveName: firstQuestion.createdByPerspectiveName
        }
        renderedQuestionIndices.value.add(firstQuestion.questionIndex)

        // 添加第一题到消息列表
        messages.value = [{
          id: Date.now() + Math.random(),
          type: 'interviewer',
          content: firstQuestion.question,
          category: firstQuestion.category,
          questionIndex: firstQuestion.questionIndex,
          difficulty: firstQuestion.difficulty,
          knowledgeBaseName: firstQuestion.knowledgeBaseName,
          timestamp: Date.now(),
          createdByPerspectiveId: firstQuestion.createdByPerspectiveId,
          createdByPerspectiveName: firstQuestion.createdByPerspectiveName
        }]
      } catch (questionErr) {
        uni.showToast({
          title: '获取面试问题失败',
          icon: 'none'
        })
        return
      } finally {
        isLoadingNextQuestion.value = false
      }

      // 开始面试状态
      interviewStatus.value = 'answering'

      // 建立 SSE 连接
      setupSSEConnection()
    } else {
      // 恢复已有面试：获取会话进度（包括历史记录和当前问题）
      const progress = await getSessionProgress(pageId.value)

      // 保存总题数（如果有有效的总题数则更新，否则保留 URL 中的值）
      if (progress.totalQuestions > 0) {
        sessionTotalQuestions.value = progress.totalQuestions
      }

      // 检查面试是否已完成
      if (progress.currentQuestionIndex >= progress.totalQuestions) {
        interviewStatus.value = 'completed'
        return
      }

      // 恢复历史消息
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
        renderedQuestionIndices.value.add(progress.currentQuestion.questionIndex)
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
      interviewStatus.value = 'answering'

      // 建立 SSE 连接
      setupSSEConnection()
    }
  } catch (error) {
    uni.showToast({
      title: '加载失败',
      icon: 'none'
    })
  }
}

// 应用下一题到 UI（NC1：submitAnswer 返回值与 SSE onQuestion 共用，按 questionIndex 去重）
const applyNextQuestion = (question: StreamCurrentQuestionDTO) => {
  // 去重：同一 questionIndex 不重复渲染（返回值与 SSE 双触发时只生效一次）
  if (renderedQuestionIndices.value.has(question.questionIndex)) {
    isLoadingNextQuestion.value = false
    progressStage.value = null
    interviewStatus.value = 'answering'
    isSubmitting.value = false
    return
  }
  renderedQuestionIndices.value.add(question.questionIndex)

  progressStage.value = null
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
  // 隐藏加载遮罩 + 释放提交锁（原代码成功路径未重置 isSubmitting，此处一并修复）
  isLoadingNextQuestion.value = false
  isSubmitting.value = false
  interviewStatus.value = 'answering'
  scrollToBottom()
}

// 面试完成（NC1：submitAnswer 返回 hasNextQuestion=false 时进入）
const handleComplete = () => {
  interviewStatus.value = 'completed'
  progressStage.value = null
  isLoadingNextQuestion.value = false
  isSubmitting.value = false
  // 跳转到面试列表页，等待评估完成后再查看报告
  uni.redirectTo({
    url: '/pages/interview/list'
  })
}

// NC1 兜底：后端 submitAnswerAdaptive 硬编码返回 hasNextQuestion=true / nextQuestion=null（注释"SSE 推送下一题"），
// 会话推进实际依赖 SSE。而 SSE 在小程序端可能因弱网 / 后台回收失效，导致"提交后等不到下一题"永久卡死。
// 故在 SSE 之外加一道兜底：超时或 SSE 报错时，用 getSessionProgress 拉后端真实进度自愈推进。
const submitGuardTimer = ref<number | null>(null)

// heal 重试：后端处于 PROCESSING（已提交、尚未评分）时 currentQuestion 为 null，
// 不能当作"已答完"，需保持遮罩并轮询，直到拿到下一题或真正完成；
// 否则会误触发 handleComplete → redirectTo('/pages/interview/list')，即用户看到的"闪退到列表页"。
// 间隔 3s；后端完整工作流（评分 + Decider + 检索 + 出题）实测可达 48s+，
// 故总时长给到 120s（40 次），避免 currentIndex 推进前就放弃 → "收不到下一题"。
const HEAL_RETRY_INTERVAL = 3000
const HEAL_MAX_RETRIES = 40
const healRetryCount = ref(0)
const healRetryTimer = ref<number | null>(null)

const healFromServer = async (submittedIndex: number) => {
  // 已被 SSE 正常推进（applyNextQuestion / handleComplete 已复位 isLoadingNextQuestion）则不干预
  if (!isLoadingNextQuestion.value) return
  try {
    const progress = await getSessionProgress(pageId.value)
    const q = progress.currentQuestion
    const isProcessing = progress.processingStatus === 'PROCESSING'

    // 后端已生成下一题但 SSE 推送丢失 → 直接渲染（applyNextQuestion 内部按 questionIndex 去重）
    if (q && q.questionIndex > submittedIndex) {
      healRetryCount.value = 0
      applyNextQuestion(q)
      return
    }

    // 无当前题：必须区分"处理中"和"已答完"
    if (!q) {
      if (isProcessing) {
        // 工作流仍在评分/生成下一题，currentQuestion 暂为 null——正是原 !q 误判为"已完成"导致闪退的根因
        // 保持遮罩并有限次轮询，等下一题就绪或评分完成，绝不在此进入完成流程
        // 仅在上一轮定时器已触发完（healRetryTimer 为空）时才启动下一轮并累加计数；
        // 否则 submitGuardTimer / 反复 onError 并发调用会让计数涨太快、提前耗尽重试次数。
        if (healRetryCount.value >= HEAL_MAX_RETRIES) {
          // 超过重试上限仍无结果 → 释放锁并提示，避免永久卡 loading（用户可手动重试）
          healRetryCount.value = 0
          isLoadingNextQuestion.value = false
          isSubmitting.value = false
          interviewStatus.value = 'answering'
          uni.showToast({ title: 'AI 处理超时，请重试', icon: 'none' })
        } else if (!healRetryTimer.value) {
          healRetryCount.value++
          progressStage.value = SSE_EVENT_TYPES.PROGRESS_SCORING
          healRetryTimer.value = setTimeout(() => {
            healRetryTimer.value = null
            healFromServer(submittedIndex)
          }, HEAL_RETRY_INTERVAL) as unknown as number
        }
        // 已有定时器在等待：直接返回，让递归轮询的节奏保持稳定（不额外累加计数）
      } else if (progress.currentQuestionIndex >= progress.totalQuestions) {
        // 真正答完所有题 → 进入完成流程（保留原兜底）
        healRetryCount.value = 0
        handleComplete()
      } else {
        // 既无当前题、又非处理中、且未到末题：后端状态异常，释放锁让用户重试
        healRetryCount.value = 0
        isLoadingNextQuestion.value = false
        isSubmitting.value = false
        interviewStatus.value = 'answering'
      }
    }
    // q 存在但 questionIndex <= submittedIndex：仍是当前题（恢复场景），不推进，保持现状
  } catch (e) {
    console.error('[NC1 兜底] 拉取会话进度失败:', e)
    healRetryCount.value = 0
    // 自愈也失败时释放锁，避免永久卡在 loading（用户可手动重试）
    isLoadingNextQuestion.value = false
    isSubmitting.value = false
    interviewStatus.value = 'answering'
  }
}

const startSubmitTimeoutGuard = (submittedIndex: number) => {
  if (submitGuardTimer.value) clearTimeout(submitGuardTimer.value)
  submitGuardTimer.value = setTimeout(() => {
    submitGuardTimer.value = null
    healFromServer(submittedIndex)
  }, 30000) as unknown as number
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
      interviewStatus.value = 'connected'
      // 连接已通，重置重连计数
      sseReconnectAttempts = 0
    },
    onProgress: (stage: string) => {
      progressStage.value = stage
      // B11: 修正常量大小写（原 'QUESTION_GENERATING' 不存在，永不命中）
      if (stage === SSE_EVENT_TYPES.PROGRESS_GENERATING) {
        isLoadingNextQuestion.value = true
      }
    },
    onQuestion: (question) => {
      // NC1: SSE 推送的下一题作为补充（applyNextQuestion 内部按 questionIndex 去重）
      applyNextQuestion(question)
    },
    onEvaluation: (evaluation) => {
      // B14: 实时展示本题评分反馈（与 Web 端一致）
      if (evaluation && typeof evaluation.score === 'number') {
        messages.value.push({
          id: Date.now() + Math.random(),
          type: 'evaluation',
          content: evaluation.feedback || `本题得分：${evaluation.score} 分`,
          questionIndex: evaluation.questionIndex,
          evaluation: {
            score: evaluation.score,
            strength: [],
            improvements: []
          },
          timestamp: Date.now()
        })
        scrollToBottom()
      }
    },
    onComplete: () => {
      handleComplete()
    },
    onError: (errorMsg) => {
      progressStage.value = null
      // NC1 兜底：SSE 断开时若仍在等下一题，立即用后端进度自愈，避免晾在当前题
      if (isLoadingNextQuestion.value && restoredCurrentQuestion.value) {
        // 提交中：靠 healFromServer 轮询自愈；H5 下 SSE 在评分窗口断开属预期，不再弹错误 toast 打扰用户
        healFromServer(restoredCurrentQuestion.value.questionIndex)
      } else {
        // 非提交态：SSE 意外断开，延迟重连以恢复实时性（避免下一轮提交再次完全依赖轮询）
        isLoadingNextQuestion.value = false
        scheduleSseReconnect()
      }
    }
  })
}

// SSE 延迟重连（带指数退避 + 上限，避免 broken pipe 风暴）
const scheduleSseReconnect = () => {
  if (sseReconnectTimer.value) return
  if (sseReconnectAttempts >= SSE_RECONNECT_MAX) return
  if (interviewStatus.value === 'completed') return
  sseReconnectAttempts++
  sseReconnectTimer.value = setTimeout(() => {
    sseReconnectTimer.value = null
    if (interviewStatus.value !== 'completed' && interviewStatus.value !== 'idle') {
      setupSSEConnection()
    }
  }, SSE_RECONNECT_DELAY * sseReconnectAttempts) as unknown as number
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

// 滚动到底部（N16：用 selectorQuery 取实际内容高度，替代魔法数 99999）
const scrollToBottom = () => {
  nextTick(() => {
    const query = uni.createSelectorQuery()
    query.select('.chat-section').boundingClientRect()
    query.selectAll('.message-item').boundingClientRect()
    query.exec((res) => {
      const container = res[0] as { height: number } | null
      const items = res[1] as Array<{ height: number }> | undefined
      if (container && items && items.length) {
        const contentHeight = items.reduce((sum, it) => sum + (it.height || 0), 0)
        // scroll-top 设为内容高度；微信 scroll-view 对相同值不触发滚动，故同值时 +1 强制刷新
        const target = contentHeight > 0 ? contentHeight : container.height
        scrollTop.value = scrollTop.value >= target ? target + 1 : target
      } else {
        // 兜底（首次无数据或查询失败）：递增触发滚动
        scrollTop.value = scrollTop.value + 1
      }
    })
  })
}

// 提交回答
const submitAnswer = async () => {
  // N9: 连点防护（提交中 / 加载下一题中直接拦截）
  if (isSubmitting.value || isLoadingNextQuestion.value) return

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
    // NC1: 用 submitAnswerAdaptive 的同步返回值驱动下一题（不再丢弃返回值依赖 SSE）
    const res = await submitAnswerAdaptive(pageId.value, questionIndex, submittedAnswer)
    if (res?.hasNextQuestion && res.nextQuestion) {
      // 后端同步返回了下一题 → 直接渲染
      applyNextQuestion(res.nextQuestion)
    } else if (res?.hasNextQuestion) {
      // 有下一题但返回值未携带（后端异步生成，当前后端契约恒如此）→ 保留遮罩，等 SSE onQuestion 推送
      // isLoadingNextQuestion / isSubmitting 保持 true，由 applyNextQuestion 在收到推送时释放
      // NC1 兜底：SSE 在小程序端可能失效，启动 30s 超时自愈（拉 getSessionProgress 推进），避免永久卡死
      startSubmitTimeoutGuard(questionIndex)
    } else {
      // 没有下一题 → 进入完成流程
      handleComplete()
    }
  } catch (error) {
    interviewStatus.value = 'answering'
    isLoadingNextQuestion.value = false
    isSubmitting.value = false
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

// 结束面试并等待结果
const finishInterviewAndWaitForResult = async () => {
  try {
    // 调用后端接口结束面试
    await endInterview(pageId.value)

    interviewStatus.value = 'completed'
    showReportLoading.value = false

    uni.showToast({
      title: '面试已结束，评估进行中',
      icon: 'none'
    })

    // 跳转到面试列表页
    uni.redirectTo({
      url: '/pages/interview/list'
    })
  } catch (error) {
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

// 清理页面资源（N10）：兜底定时器 + 录音定时器 + SSE 连接
const cleanupResources = () => {
  if (submitGuardTimer.value) {
    clearTimeout(submitGuardTimer.value)
    submitGuardTimer.value = null
  }
  if (healRetryTimer.value) {
    clearTimeout(healRetryTimer.value)
    healRetryTimer.value = null
  }
  if (sseReconnectTimer.value) {
    clearTimeout(sseReconnectTimer.value)
    sseReconnectTimer.value = null
  }
  healRetryCount.value = 0
  sseReconnectAttempts = 0
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }
  if (sseCleanup.value) {
    sseCleanup.value()
    sseCleanup.value = null
  }
}

// N10: 小程序页面 onUnload/onHide 比 Vue onUnmounted 更可靠地触发
onUnload(() => {
  cleanupResources()
})

onHide(() => {
  // 切后台：清理录音定时器与 SSE 连接，避免后台泄漏
  cleanupResources()
})

// 组件卸载时（H5 兜底）
onUnmounted(() => {
  cleanupResources()
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

          <!-- 评价消息（实时评分，B14） -->
          <view v-if="msg.type === 'evaluation'" class="evaluation-message">
            <view class="eval-bubble">
              <view class="eval-header">
                <text class="eval-label">AI 实时评分</text>
                <text class="eval-score" :style="{ color: getScoreColor(msg.evaluation?.score) }">{{ msg.evaluation?.score }} 分</text>
              </view>
              <text v-if="msg.content" class="eval-feedback">{{ msg.content }}</text>
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
@use '../../styles/variables.scss' as *;

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

// 评价消息（实时评分，B14）
.evaluation-message {
  .eval-bubble {
    background-color: rgba(34, 197, 94, 0.08);
    border: 1rpx solid rgba(34, 197, 94, 0.25);
    border-radius: 16rpx;
    padding: 20rpx 24rpx;
  }

  .eval-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8rpx;
  }

  .eval-label {
    font-size: 24rpx;
    color: #64748b;
    font-weight: 600;
  }

  .eval-score {
    font-size: 32rpx;
    font-weight: 700;
  }

  .eval-feedback {
    display: block;
    font-size: 26rpx;
    color: #475569;
    line-height: 1.6;
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
