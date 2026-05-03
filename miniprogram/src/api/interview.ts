import { get, post, del, put, uploadFile } from '../utils/request'

// 题目分类类型
export interface QuestionCategory {
  id: number
  name: string
  questionCount: number
  description?: string
}

// 面试列表查询参数
export interface InterviewListParams {
  page?: number
  pageSize?: number
  status?: string
  type?: string
  position?: string
  company?: string
}

// 创建面试参数
export interface CreateInterviewParams {
  title: string
  type: 'practice' | 'real'
  position?: string
  company?: string
  duration?: number
  resumeId?: number
  questionCount?: number
  questionTypeIds?: number[]
}

// 提交答案参数
export interface SubmitAnswerParams {
  questionId: number
  sessionId: string | number
  answer: string
  audioUrl?: string
  videoUrl?: string
}

/**
 * 获取面试列表
 */
export const getInterviewList = (params?: InterviewListParams) => {
  return get<any>('/api/interview/sessions', params).then(data => {
    // 如果是mock（包含list属性），直接返回
    if (data && data.list) {
      return data
    }
    // 后端返回数组的情况
    return {
      list: data || [],
      total: data?.length || 0,
      page: params?.page || 1,
      pageSize: params?.pageSize || 20
    }
  })
}

/**
 * 获取面试详情 - 包含评估报告数据
 */
export const getInterviewDetail = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/details`)
}

/**
 * 创建面试
 */
export const createInterview = (data: CreateInterviewParams) => {
  return post('/api/interview/sessions', data)
}

/**
 * 创建面试会话（自适应难度版本）
 * POST /api/interview/sessions
 */
export interface CreateSessionParams {
  resumeText: string
  questionCount: number
  resumeId?: number
  forceCreate?: boolean
  questionBankIds?: number[]
  knowledgeBaseIds?: number[]
  // 多视角扩展字段：选中的视角ID列表
  selectedPerspectives?: number[]
  // 各视角权重配置，key 为视角ID，value 为权重值（0-1之间）
  perspectiveWeights?: Record<string, number>
}

export interface SessionResponse {
  sessionId: string
  resumeText: string
  totalQuestions: number
  currentQuestionIndex: number
  status: string
}

export const createSession = (data: CreateSessionParams) => {
  return post<SessionResponse>('/api/interview/sessions', data, { timeout: 180000 })
}

/**
 * 更新面试
 */
export const updateInterview = (sessionId: string | number, data: Partial<CreateInterviewParams>) => {
  return put(`/api/interview/sessions/${sessionId}`, data)
}

/**
 * 删除面试
 */
export const deleteInterview = (sessionId: string | number) => {
  return del(`/api/interview/sessions/${sessionId}`)
}

/**
 * 开始面试
 */
export const startInterview = (sessionId: string | number) => {
  return post(`/api/interview/sessions/${sessionId}/start`)
}

/**
 * 结束面试
 */
export const endInterview = (sessionId: string | number) => {
  return post(`/api/interview/sessions/${sessionId}/complete`)
}

/**
 * 获取面试详情（包含所有问题）
 */
export const getInterviewQuestions = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/details`)
}

/**
 * 获取指定问题详情
 */
export const getQuestionDetail = (questionId: number) => {
  return get(`/api/questions/${questionId}`)
}

/**
 * 提交答案
 */
export const submitAnswer = (sessionId: string | number, questionIndex: number, answer: string) => {
  return post(`/api/interview/sessions/${sessionId}/answers`, {
    questionIndex,
    answer
  })
}

/**
 * 提交语音答案
 */
export const submitAudioAnswer = (
  sessionId: string | number,
  questionId: number,
  audioPath: string
) => {
  return uploadFile(audioPath, {
    url: `/api/interview/sessions/${sessionId}/audio`,
    name: 'audio',
    formData: { questionId: String(questionId) }
  })
}

/**
 * 提交视频答案
 */
export const submitVideoAnswer = (
  sessionId: string | number,
  questionId: number,
  videoPath: string
) => {
  return uploadFile(videoPath, {
    url: `/api/interview/sessions/${sessionId}/video`,
    name: 'video',
    formData: { questionId: String(questionId) }
  })
}

/**
 * 获取面试评估状态（轻量级接口）
 */
export const getEvaluateStatus = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/status`)
}

/**
 * 获取面试结果
 */
export const getInterviewResult = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/report`)
}

/**
 * 获取面试历史统计
 */
export const getInterviewHistory = () => {
  return get('/api/interview/score-trend')
}

/**
 * 导出面试报告
 */
export const exportInterviewReport = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/export`, {}, { showLoading: true })
}

/**
 * 分享面试
 */
export const shareInterview = (sessionId: string | number) => {
  return post(`/api/interview/sessions/${sessionId}/share`)
}

/**
 * 获取推荐面试岗位
 */
export const getRecommendedPositions = async () => {
  try {
    // 先获取题库列表
    const banks = await getQuestionCategories()
    if (banks && banks.length > 0) {
      const bankIds = banks.slice(0, 3).map((b: any) => b.id)
      // 从题库随机获取题目（与Web端一致）
      return get<any[]>(`/api/questions/banks/random?bankIds=${bankIds.join(',')}&limit=5`)
    }
    return []
  } catch (error) {
    return []
  }
}

/**
 * 获取面试题库分类
 */
export const getQuestionCategories = () => {
  return get('/api/question-banks')
}

/**
 * 获取指定分类的题目
 */
export const getQuestionsByCategory = (bankId: number, params?: { page?: number; pageSize?: number }) => {
  return get(`/api/questions/bank/${bankId}`, params)
}

/**
 * 能力画像类型定义
 */
export interface CategoryScore {
  category: string
  totalScore: number
  count: number
  avgScore: number
}

export interface AbilityProfile {
  categoryScores: Record<string, CategoryScore>
  overallScore: number
  strengths: string[]
  weaknesses: string[]
}

/**
 * 获取能力画像
 */
export const getAbilityProfile = (sessionId: string | number) => {
  return get<AbilityProfile>(`/api/interview/sessions/${sessionId}/profile`)
}

// ========== 多视角面试 API ==========

/**
 * 面试官角色 DTO
 * 对应后端 GET /api/admin/interviewer-roles
 */
export interface InterviewerRoleDTO {
  id: number
  roleName: string
  roleCode: string
  description: string
  weight: number
  icon: string
  status: boolean  // true = ACTIVE, false = INACTIVE
}

/**
 * 视角答题记录 DTO
 */
export interface PerspectiveAnswerDTO {
  questionIndex: number
  question: string
  userAnswer: string
  score: number | null
  feedback: string | null
  difficulty: string
  category: string
}

/**
 * 视角评分 DTO
 * 对应后端 GET /api/interview/sessions/{sessionId}/perspectives
 */
export interface PerspectiveScoreDTO {
  id?: number
  sessionId?: string
  perspectiveId: number
  perspectiveName: string
  perspectiveIcon?: string
  questionIndex?: number
  score: number | null
  feedback?: string | null
  strengths?: string[]
  improvements?: string[]
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED'
  questionCount?: number
  answeredCount?: number
}

/**
 * 视角详情 DTO
 * 对应后端 GET /api/interview/sessions/{sessionId}/perspectives/{perspectiveId}
 */
export interface PerspectiveDetailDTO {
  perspectiveId: number
  roleName: string
  perspectiveIcon?: string
  score: number | null
  feedback?: string
  strengths: string[]
  improvements: string[]
  questionCount?: number
  answeredCount?: number
  questionScores: {
    questionIndex: number
    question: string
    userAnswer: string
    score: number | null
    feedback: string | null
    difficulty: string
    category: string
    referenceAnswer?: string | null
    keyPoints?: string[]
  }[]
}

/**
 * 综合报告 DTO
 * 对应后端 GET /api/interview/sessions/{sessionId}/report/comprehensive
 */
export interface ComprehensiveReportDTO {
  sessionId: string
  overallScore: number
  perspectives?: {
    perspectiveId: number
    perspectiveName: string
    weight: number
    score: number | null
    questionCount?: number
  }[]
  comprehensiveFeedback: string
  evaluation?: string
  developmentSuggestions?: string
  strengths: string[]
  improvements: string[]
  perspectiveDetails?: Record<string, PerspectiveDetailDTO>
}

/**
 * 获取可选视角列表（创建面试时使用）
 * GET /api/admin/interviewer-roles
 */
export const getPerspectives = () => {
  return get<InterviewerRoleDTO[]>('/api/admin/interviewer-roles')
}

/**
 * 获取面试视角列表
 * GET /api/interview/sessions/{sessionId}/perspectives
 */
export const getSessionPerspectives = (sessionId: string | number) => {
  return get<PerspectiveScoreDTO[]>(`/api/interview/sessions/${sessionId}/perspectives`)
}

/**
 * 获取视角详情
 * GET /api/interview/sessions/{sessionId}/perspectives/{perspectiveId}
 */
export const getPerspectiveDetail = (sessionId: string | number, perspectiveId: number) => {
  return get<PerspectiveDetailDTO>(`/api/interview/sessions/${sessionId}/perspectives/${perspectiveId}`)
}

/**
 * 获取综合报告
 * GET /api/interview/sessions/{sessionId}/report/comprehensive
 */
export const getComprehensiveReport = (sessionId: string | number) => {
  return get<ComprehensiveReportDTO>(`/api/interview/sessions/${sessionId}/report/comprehensive`)
}

// ========== 自适应难度面试 API ==========

/**
 * 会话进度 DTO 类型
 */
export interface AnswerHistoryDTO {
  questionIndex: number
  question: string
  category: string
  difficulty: string
  userAnswer: string
  // 多视角扩展字段（后端使用 createdByPerspectiveId/Name）
  createdByPerspectiveId?: number
  createdByPerspectiveName?: string
}

export interface SessionProgressDTO {
  sessionId: string
  currentQuestionIndex: number
  totalQuestions: number
  currentQuestion: CurrentQuestionDTO | null
  history: AnswerHistoryDTO[]
}

/**
 * 获取会话进度（包括历史记录和当前问题）
 * GET /api/interview/sessions/{sessionId}/progress
 */
export const getSessionProgress = (sessionId: string | number) => {
  return get<SessionProgressDTO>(`/api/interview/sessions/${sessionId}/progress`)
}

/**
 * 当前问题 DTO 类型
 */
export interface CurrentQuestionDTO {
  questionIndex: number
  question: string
  category: string
  difficulty?: string
  knowledgeBaseId?: number | null
  knowledgeBaseName?: string | null
  referenceContext?: string
  // 多视角扩展字段（后端使用 createdByPerspectiveId/Name）
  createdByPerspectiveId?: number
  createdByPerspectiveName?: string
  // 参考答案
  referenceAnswer?: string | null
}

/**
 * 面试问题 DTO 类型 (InterviewQuestionDTO)
 */
export interface InterviewQuestionDTO {
  questionIndex: number
  question: string
  type: string
  category: string
  userAnswer?: string | null
  score?: number | null
  feedback?: string | null
  isFollowUp: boolean
  parentQuestionIndex?: number | null
  difficulty?: string
  knowledgeBaseName?: string | null
  createdByPerspectiveId?: number
  createdByPerspectiveName?: string
}

/**
 * 提交答案响应类型
 */
export interface SubmitAnswerResponse {
  hasNextQuestion: boolean
  nextQuestion?: InterviewQuestionDTO
  newIndex: number
  questionsGenerated: number
  currentScore: number
  categoryScores: Record<string, CategoryScore>
  nextDifficulty: string
}

/**
 * 获取当前问题（自适应难度版本）
 * GET /api/interview/sessions/{sessionId}/current
 */
export const getCurrentQuestion = (sessionId: string | number) => {
  return get<CurrentQuestionDTO>(`/api/interview/sessions/${sessionId}/current`)
}

/**
 * 提交答案（自适应难度版本）
 * POST /api/interview/sessions/{sessionId}/answer
 */
export const submitAnswerAdaptive = (sessionId: string | number, questionIndex: number, answer: string) => {
  return post<SubmitAnswerResponse>(`/api/interview/sessions/${sessionId}/answer`, {
    questionIndex,
    answer
  }, { timeout: 180000 })  // 3分钟超时，AI生成下一题需要时间
}

// ========== SSE 实时事件流 API ==========

/**
 * SSE 事件类型
 */
export const SSE_EVENT_TYPES = {
  CONNECTED: 'connected',
  QUESTION: 'question',
  EVALUATION: 'evaluation',
  INTERVIEW_COMPLETE: 'interview_complete',
  ERROR: 'error',
  // 进度阶段事件（与后端 SseEventType 一致）
  PROGRESS_SCORING: 'progress_scoring',
  PROGRESS_DECIDING: 'progress_deciding',
  PROGRESS_SEARCH_PREPARING: 'progress_search_preparing',
  PROGRESS_GENERATING: 'progress_generating',
} as const

// 进度阶段标签（与前端一致）
export const PROGRESS_LABELS: Record<string, string> = {
  'progress_scoring': 'AI 正在评分...',
  'progress_deciding': '正在决策下一题...',
  'progress_search_preparing': '正在准备搜索...',
  'progress_generating': '正在出题中...',
}

/**
 * SSE 事件类别常量（用于 connectInterviewStream）
 */
const EVENT_CATEGORY = {
  PROGRESS: 'progress',
  QUESTION: 'question',
  EVALUATION: 'evaluation',
  COMPLETE: 'complete',
  ERROR: 'error',
  CONNECTED: 'connected',
} as const

/**
 * SSE 事件映射配置（将 SSE 事件类型映射到事件类别）
 */
const INTERVIEW_SSE_EVENTS = {
  [SSE_EVENT_TYPES.CONNECTED]: EVENT_CATEGORY.CONNECTED,
  [SSE_EVENT_TYPES.PROGRESS_SCORING]: EVENT_CATEGORY.PROGRESS,
  [SSE_EVENT_TYPES.PROGRESS_DECIDING]: EVENT_CATEGORY.PROGRESS,
  [SSE_EVENT_TYPES.PROGRESS_SEARCH_PREPARING]: EVENT_CATEGORY.PROGRESS,
  [SSE_EVENT_TYPES.PROGRESS_GENERATING]: EVENT_CATEGORY.PROGRESS,
  [SSE_EVENT_TYPES.QUESTION]: EVENT_CATEGORY.QUESTION,
  [SSE_EVENT_TYPES.EVALUATION]: EVENT_CATEGORY.EVALUATION,
  [SSE_EVENT_TYPES.INTERVIEW_COMPLETE]: EVENT_CATEGORY.COMPLETE,
  [SSE_EVENT_TYPES.ERROR]: EVENT_CATEGORY.ERROR,
} as const

/**
 * SSE 连接回调接口
 */
export interface SSECallbacks {
  onConnected?: () => void
  onProgress?: (stage: string) => void
  onQuestion?: (data: StreamCurrentQuestionDTO) => void
  onEvaluation?: (data: { questionIndex: number; score: number; feedback: string }) => void
  onComplete?: (data: { overallScore: number; summary: Record<string, unknown> }) => void
  onError?: (error: string) => void
}

/**
 * 通用 SSE 事件回调接口
 * 用于其他 SSE 连接场景（如知识库对话等）
 */
export interface SSEMessageCallbacks {
  onConnected?: () => void
  onMessage?: (eventType: string, data: any) => void
  onError?: (error: string) => void
}

/**
 * 连接通用 SSE 流（跨平台版本）
 * H5 使用原生 EventSource，小程序使用 uni.request + enableChunked
 * @param url 完整的 SSE 请求 URL
 * @param callbacks 事件回调
 * @returns 清理函数，调用后关闭连接
 */
export const connectSSE = (
  url: string,
  callbacks: SSEMessageCallbacks
): (() => void) => {
  // #ifdef H5
  // H5 环境使用原生 EventSource
  return connectSSE_H5(url, callbacks)
  // #endif

  // #ifndef H5
  // 小程序环境使用 uni.request
  return connectSSE_Uni(url, callbacks, undefined)
  // #endif
}

/**
 * H5 环境 SSE 连接（使用原生 EventSource）
 */
const connectSSE_H5 = (
  url: string,
  callbacks: SSEMessageCallbacks
): (() => void) => {
  const eventSource = new EventSource(url)

  eventSource.addEventListener('open', () => {
    callbacks.onConnected?.()
  })

  // 监听错误
  eventSource.addEventListener('error', () => {
    callbacks.onError?.('SSE 连接错误')
    eventSource.close()
  })

  // 监听所有自定义事件类型（由服务器端发送）
  const eventTypes = [
    'connected',
    'question',
    'evaluation',
    'interview_complete',
    'error',
    'progress_scoring',
    'progress_deciding',
    'progress_search_preparing',
    'progress_generating',
  ]

  eventTypes.forEach((eventType) => {
    eventSource.addEventListener(eventType, (event: MessageEvent) => {
      try {
        const data = event.data ? JSON.parse(event.data) : null
        callbacks.onMessage?.(eventType, data)
      } catch (e) {
        callbacks.onMessage?.(eventType, event.data)
      }
    })
  })

  return () => eventSource.close()
}

/**
 * 小程序环境 SSE 连接（使用 uni.request + enableChunked）
 */
const connectSSE_Uni = (
  url: string,
  callbacks: SSEMessageCallbacks,
  _platform: unknown
): (() => void) => {
  // SSE 数据缓冲区
  let buffer = ''

  // 解析 SSE 数据块
  const parseSSEMessage = (text: string) => {
    const lines = text.split('\n')
    let eventType: string | null = null
    let data: string | null = null

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        // 多个 data 行应该用换行符连接（SSE 协议规范）
        const lineData = line.slice(5).trim()
        data = data === null ? lineData : data + '\n' + lineData
      } else if (line === '') {
        // 空行表示消息结束，处理数据
        if (eventType && data !== null) {
          handleSSEEvent(eventType, data)
        } else if (eventType && data === '') {
          // data 为空的情况（如 connected 事件）
          handleSSEEvent(eventType, '')
        }
        eventType = null
        data = null
      }
    }
  }

  // 处理 SSE 事件
  const handleSSEEvent = (eventType: string, rawData: string) => {
    // connected 事件不传递数据，只触发回调
    if (eventType === 'connected') {
      callbacks.onConnected?.()
      return
    }

    try {
      const data = rawData === '' ? null : JSON.parse(rawData)
      callbacks.onMessage?.(eventType, data)
    } catch (e) {
      // JSON 解析错误，rawData 不是 JSON，传递给调用方处理
      callbacks.onMessage?.(eventType, rawData)
    }
  }

  // 创建 AbortController 用于取消请求
  const abortController = {
    aborted: false,
    abort: () => {
      abortController.aborted = true
    }
  }

  // 创建 SSE 请求
  const requestTask = uni.request({
    url,
    method: 'GET',
    header: {
      'Cache-Control': 'no-cache',
    },
    enableChunked: true,
    fail: (err) => {
      if (!abortController.aborted) {
        callbacks.onError?.(err.errMsg || 'SSE 连接失败')
      }
    }
  })

  // 监听数据块（微信小程序端）
  ;(requestTask as any).onChunkReceived((res: { data: ArrayBuffer }) => {
    try {
      // 将 ArrayBuffer 转为字符串（使用 TextDecoder 更高效）
      const chunkStr = new TextDecoder().decode(res.data)

      // 追加到缓冲区
      buffer += chunkStr

      // 按 SSE 格式分割处理（消息以空行 \n\n 分隔）
      const messages = buffer.split('\n\n')
      buffer = messages.pop() || '' // 保留不完整的最后一块

      for (const message of messages) {
        if (message.trim()) {
          parseSSEMessage(message)
        }
      }
    } catch (e) {
      // 解析错误时忽略：数据块可能包含不完整的 UTF-8 序列
      // TextDecoder 会抛出异常，但不影响接收后续完整数据
    }
  })

  // 监听请求完成
  ;(requestTask as any).onComplete(() => {
    if (!abortController.aborted) {
      // 处理缓冲区中剩余的数据
      if (buffer.trim()) {
        parseSSEMessage(buffer)
      }
      buffer = ''
    }
  })

  // 返回清理函数
  return () => {
    abortController.abort()
    buffer = ''
    try {
      (requestTask as any).abort?.()
    } catch (e) {
      // 忽略中止错误：请求可能已经完成或在不同状态下关闭
      // 这是清理阶段的预期行为，不需要处理
    }
  }
}

/**
 * 当前问题 DTO（与前端统一）
 */
export interface StreamCurrentQuestionDTO {
  questionIndex: number
  question: string
  category: string
  difficulty?: string
  knowledgeBaseId?: number | null
  knowledgeBaseName?: string | null
  referenceContext?: string | null
  isFollowUp?: boolean
  relatedIndex?: number
  relatedQuestion?: string
  // 多视角支持
  createdByPerspectiveId?: number
  createdByPerspectiveName?: string
}

/**
 * 连接面试 SSE 流获取实时事件
 * 使用通用 connectSSE 方法实现，支持 H5 和微信小程序
 * @param sessionId 会话 ID
 * @param callbacks 事件回调
 * @returns 清理函数，调用后关闭连接
 */
export const connectInterviewStream = (
  sessionId: string | number,
  callbacks: SSECallbacks
): (() => void) => {
  // 获取环境配置的基础URL
  const env = process.env as Record<string, string>
  const apiBaseUrl = env.VITE_API_BASE_URL || ''

  // 获取 token
  // 注意：小程序端存储 token 的 key 是 'token'，不是 'auth_token'
  const token = uni.getStorageSync('token') || ''
  const streamUrl = token
    ? `${apiBaseUrl}/api/interview/sessions/${sessionId}/stream?token=${encodeURIComponent(token)}`
    : `${apiBaseUrl}/api/interview/sessions/${sessionId}/stream`

  // 使用通用 SSE 方法
  return connectSSE(streamUrl, {
    onMessage: (eventType, data) => {
      const eventCategory = INTERVIEW_SSE_EVENTS[eventType as keyof typeof INTERVIEW_SSE_EVENTS]

      switch (eventCategory) {
        case EVENT_CATEGORY.PROGRESS:
          callbacks.onProgress?.(eventType)
          break
        case EVENT_CATEGORY.QUESTION:
          if (data) {
            callbacks.onQuestion?.(data as StreamCurrentQuestionDTO)
          }
          break
        case EVENT_CATEGORY.EVALUATION:
          if (data) {
            callbacks.onEvaluation?.(data)
          }
          break
        case EVENT_CATEGORY.COMPLETE:
          callbacks.onComplete?.(data || {})
          break
        case EVENT_CATEGORY.ERROR:
          callbacks.onError?.(data?.message || '未知错误')
          break
        // EVENT_CATEGORY.CONNECTED 事件已在 connectSSE 内部处理，无需额外操作
      }
    },
    onError: (error) => {
      callbacks.onError?.(error)
    }
  })
}
