import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getInterviewList,
  getInterviewDetail,
  createInterview,
  deleteInterview,
  getInterviewQuestions,
  submitAnswer,
  submitAnswerAdaptive,
  getCurrentQuestion,
  getInterviewResult,
  getEvaluateStatus,
  getInterviewHistory
} from '../api/interview'

// 面试类型定义
export interface Interview {
  sessionId: string
  id?: number
  title: string
  description?: string
  type?: 'practice' | 'real'
  status: 'pending' | 'in_progress' | 'completed' | 'cancelled' | 'CREATED' | 'IN_PROGRESS' | 'COMPLETED'
  position?: string
  company?: string
  duration?: number // 分钟
  questionCount?: number
  totalQuestions?: number
  currentQuestionIndex?: number
  score?: number
  overallScore?: number // 评估总分
  evaluateStatus?: string // 评估状态
  feedback?: string
  resumeId?: number
  resumeText?: string
  createdAt?: string
  updatedAt?: string
}

export interface InterviewQuestion {
  id?: number
  questionIndex?: number
  question?: string
  content?: string
  type?: 'text' | 'audio' | 'video' | 'PROJECT' | 'MYSQL' | 'REDIS'
  category?: string
  difficulty?: 'easy' | 'medium' | 'hard'
  idealAnswer?: string
  tips?: string
  orderIndex?: number
  userAnswer?: string
  answer?: string
  score?: number
  feedback?: string
  answerStatus?: 'pending' | 'answered' | 'evaluated'
  evaluation?: QuestionEvaluation
}

export interface QuestionEvaluation {
  score: number
  strength: string[]
  improvements: string[]
  suggestedAnswer?: string
  overallFeedback?: string
}

export interface CreateInterviewParams {
  title: string
  type: 'practice' | 'real'
  position?: string
  company?: string
  duration?: number
  resumeId?: number
  questionCount?: number
}

export interface AnswerParams {
  questionId: number
  interviewId: number
  answer: string
  audioUrl?: string
  videoUrl?: string
}

export const useInterviewStore = defineStore('interview', () => {
  // ========== State ==========
  const interviewList = ref<Interview[]>([])
  const currentInterview = ref<Interview | null>(null)
  const currentQuestions = ref<InterviewQuestion[]>([])
  const currentQuestionIndex = ref(0)
  const isLoading = ref(false)
  const isSubmitting = ref(false)

  // 面试统计
  const statistics = ref({
    totalCount: 0,
    completedCount: 0,
    averageScore: 0,
    totalDuration: 0
  })

  // 当前问题
  const currentQuestion = computed(() => {
    if (currentQuestions.value.length === 0) return null
    return currentQuestions.value[currentQuestionIndex.value] || null
  })

  // 面试进度
  const progress = computed(() => {
    if (!currentQuestions.value.length) return 0
    const answered = currentQuestions.value.filter(
      q => q.answerStatus === 'answered' || q.answerStatus === 'evaluated'
    ).length
    return Math.round((answered / currentQuestions.value.length) * 100)
  })

  // ========== Actions ==========

  /**
   * 获取面试列表
   */
  const fetchInterviewList = async (params?: {
    page?: number
    pageSize?: number
    status?: string
  }) => {
    isLoading.value = true
    try {
      const res = await getInterviewList(params)
      const list = res.list || res.data || []
      // 转换每个item的状态
      interviewList.value = list.map((item: any) => ({
        ...item,
        status: mapStatus(item.status)
      }))
      return interviewList.value
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取面试详情
   */
  const fetchInterviewDetail = async (id: string | number) => {
    isLoading.value = true
    try {
      const detail = await getInterviewDetail(id)
      // 转换后端数据格式为前端格式
      const transformedDetail: Interview = {
        sessionId: detail.sessionId,
        id: detail.sessionId ? parseInt(detail.sessionId.substring(0, 8), 16) : undefined,
        title: detail.resumeText?.split('\n')[0] || 'AI面试',
        status: mapStatus(detail.status),
        questionCount: detail.totalQuestions,
        currentQuestionIndex: detail.currentQuestionIndex,
        resumeText: detail.resumeText,
        createdAt: new Date().toISOString(),
        // 添加评估相关字段，用于轮询检查评估状态
        overallScore: detail.overallScore,
        evaluateStatus: detail.evaluateStatus
      }
      currentInterview.value = transformedDetail
      return transformedDetail
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取面试评估状态（轻量级接口，用于轮询检查评估是否完成）
   */
  const fetchEvaluateStatus = async (id: string | number) => {
    return await getEvaluateStatus(id)
  }

  // 映射后端状态到前端状态
  const mapStatus = (status: string): Interview['status'] => {
    const statusMap: Record<string, Interview['status']> = {
      'CREATED': 'pending',
      'IN_PROGRESS': 'in_progress',
      'COMPLETED': 'completed',
      'EVALUATED': 'completed',
      'CANCELLED': 'cancelled'
    }
    return statusMap[status] || 'pending'
  }

  /**
   * 创建面试
   */
  const createNewInterview = async (params: CreateInterviewParams) => {
    isLoading.value = true
    try {
      const interview = await createInterview(params)
      // 添加到列表
      interviewList.value.unshift(interview)
      // 设置为当前面试
      currentInterview.value = interview
      return interview
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 删除面试
   */
  const removeInterview = async (id: number) => {
    await deleteInterview(id)
    // 从列表中移除
    const index = interviewList.value.findIndex(i => i.id === id)
    if (index > -1) {
      interviewList.value.splice(index, 1)
    }
    // 如果是当前面试，清空
    if (currentInterview.value?.id === id) {
      currentInterview.value = null
      currentQuestions.value = []
      currentQuestionIndex.value = 0
    }
  }

  /**
   * 获取面试问题列表
   */
  const fetchQuestions = async (interviewId: string | number) => {
    isLoading.value = true
    try {
      const detailData = await getInterviewQuestions(interviewId)
      // /details 接口返回 InterviewDetailDTO，包含 answers 数组
      // 使用 answers 数组来构建问题列表
      const answers = detailData.answers || []
      // 转换后端数据格式为前端格式
      const transformedQuestions: InterviewQuestion[] = answers.map((q: any) => ({
        id: q.questionIndex,
        questionIndex: q.questionIndex,
        question: q.question,
        content: q.question,
        type: 'text',
        category: q.category,
        userAnswer: q.userAnswer,
        answer: q.userAnswer,
        score: q.score,
        feedback: q.feedback,
        answerStatus: q.userAnswer ? (q.score ? 'evaluated' : 'answered') : 'pending'
      }))
      currentQuestions.value = transformedQuestions
      currentQuestionIndex.value = 0
      return transformedQuestions
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 提交答案（自适应难度版本）
   * 新模式：立即返回，SSE 推送下一题
   */
  const submitQuestionAnswer = async (params: AnswerParams) => {
    isSubmitting.value = true
    try {
      // 使用自适应难度 API 提交答案（立即返回，SSE 推送下一题）
      await submitAnswerAdaptive(params.interviewId, params.questionId, params.answer)
      // 不再等待下一题，SSE 会推送
    } finally {
      isSubmitting.value = false
    }
  }

  /**
   * 获取当前问题（自适应难度版本）
   */
  const fetchCurrentQuestion = async (sessionId: string | number) => {
    isLoading.value = true
    try {
      const question = await getCurrentQuestion(sessionId)
      // 更新当前问题
      if (currentQuestions.value.length === 0 || currentQuestionIndex.value === 0) {
        currentQuestions.value.push({
          id: question.questionIndex,
          questionIndex: question.questionIndex,
          question: question.question,
          content: question.question,
          category: question.category,
          difficulty: question.difficulty as 'easy' | 'medium' | 'hard',
          answerStatus: 'pending'
        })
      }
      return question
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取面试结果
   */
  const fetchInterviewResult = async (interviewId: string | number) => {
    isLoading.value = true
    try {
      const result = await getInterviewResult(interviewId)
      // 更新当前面试
      if (currentInterview.value?.id === interviewId) {
        currentInterview.value = {
          ...currentInterview.value,
          score: result.score,
          feedback: result.feedback
        }
      }
      return result
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取面试历史统计
   */
  const fetchStatistics = async () => {
    try {
      const stats = await getInterviewHistory()
      statistics.value = stats
      return stats
    } catch (error) {
    }
  }

  /**
   * 下一题
   */
  const nextQuestion = () => {
    if (currentQuestionIndex.value < currentQuestions.value.length - 1) {
      currentQuestionIndex.value++
    }
  }

  /**
   * 上一题
   */
  const prevQuestion = () => {
    if (currentQuestionIndex.value > 0) {
      currentQuestionIndex.value--
    }
  }

  /**
   * 跳转到指定题目
   */
  const goToQuestion = (index: number) => {
    if (index >= 0 && index < currentQuestions.value.length) {
      currentQuestionIndex.value = index
    }
  }

  /**
   * 开始面试（设置当前面试）
   */
  const startInterview = (interviewId: string | number) => {
    // 设置当前面试 ID
    if (currentInterview.value) {
      currentInterview.value.status = 'in_progress'
    }
  }

  /**
   * 结束面试
   */
  const finishInterview = () => {
    currentInterview.value = null
    currentQuestions.value = []
    currentQuestionIndex.value = 0
  }

  /**
   * 重置状态
   */
  const reset = () => {
    currentInterview.value = null
    currentQuestions.value = []
    currentQuestionIndex.value = 0
    isLoading.value = false
    isSubmitting.value = false
  }

  return {
    // State
    interviewList,
    currentInterview,
    currentQuestions,
    currentQuestionIndex,
    isLoading,
    isSubmitting,
    statistics,

    // Getters
    currentQuestion,
    progress,

    // Actions
    fetchInterviewList,
    fetchInterviewDetail,
    fetchEvaluateStatus,
    createNewInterview,
    removeInterview,
    fetchQuestions,
    fetchCurrentQuestion,
    submitQuestionAnswer,
    fetchInterviewResult,
    fetchStatistics,
    nextQuestion,
    prevQuestion,
    goToQuestion,
    reset
  }
})
