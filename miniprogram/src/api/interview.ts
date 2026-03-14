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
 * 获取面试详情 - 直接返回完整响应
 */
export const getInterviewDetail = (sessionId: string | number) => {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `/api/interview/sessions/${sessionId}`,
      method: 'GET',
      success: (res: any) => {
        if (res.data?.code === 200) {
          resolve(res.data.data)
        } else {
          reject(new Error(res.data?.message || '获取面试详情失败'))
        }
      },
      fail: reject
    })
  })
}

/**
 * 创建面试
 */
export const createInterview = (data: CreateInterviewParams) => {
  return post('/api/interview/sessions', data)
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
  return post(`/api/interview/sessions/${sessionId}/end`)
}

/**
 * 获取当前问题 - 直接返回完整响应
 */
export const getInterviewQuestions = (sessionId: string | number) => {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `/api/interview/sessions/${sessionId}/questions`,
      method: 'GET',
      success: (res: any) => {
        if (res.data?.code === 200) {
          resolve(res.data.data)
        } else {
          reject(new Error(res.data?.message || '获取面试问题失败'))
        }
      },
      fail: reject
    })
  })
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
export const submitAnswer = (sessionId: string | number, questionId: number, answer: string) => {
  return post(`/api/interview/sessions/${sessionId}/answers`, {
    questionId,
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
 * 获取面试结果
 */
export const getInterviewResult = (sessionId: string | number) => {
  return get(`/api/interview/sessions/${sessionId}/result`)
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
