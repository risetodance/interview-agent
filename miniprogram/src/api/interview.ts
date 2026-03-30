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
 * 获取面试报告
 */
export const getInterviewReport = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/report`)
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
      // 从题库随机获取题目
      return get<any[]>('/api/question-banks/random', { bankIds, limit: 5 })
    }
    return []
  } catch (error) {
    console.error('获取推荐岗位失败:', error)
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
