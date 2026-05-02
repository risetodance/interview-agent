import { get, post, put, del } from '../utils/request'

// 题库类型
export type QuestionBankType = 'SYSTEM' | 'USER'

// 题目难度
export type QuestionDifficulty = 'BASIC' | 'ADVANCED' | 'EXPERT'

// 题库 DTO
export interface QuestionBankDTO {
  id: number
  name: string
  description: string | null
  type: QuestionBankType
  userId: number | null
  questionCount: number
  createdAt: string
  updatedAt: string
}

// 题目 DTO
export interface QuestionDTO {
  id: number
  questionBankId: number
  content: string
  answer: string
  difficulty: QuestionDifficulty
  tags: string[] | null
  createdAt: string
  updatedAt: string
}

// 创建题库请求
export interface CreateQuestionBankRequest {
  name: string
  description?: string
}

// 更新题库请求
export interface UpdateQuestionBankRequest {
  name?: string
  description?: string
}

// 创建题目请求
export interface CreateQuestionRequest {
  questionBankId: number
  content: string
  answer?: string
  difficulty?: QuestionDifficulty
  tags?: string[]
}

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// ========== 题库管理 API ==========

/**
 * 获取系统题库列表
 */
export const getSystemBanks = () => {
  return get<QuestionBankDTO[]>('/api/question-banks/system')
}

/**
 * 获取用户题库列表（包含系统题库和用户题库）
 */
export const getUserBanks = () => {
  return get<QuestionBankDTO[]>('/api/question-banks')
}

/**
 * 获取当前用户的题库
 */
export const getMyBanks = () => {
  return get<QuestionBankDTO[]>('/api/question-banks/my')
}

/**
 * 获取题库详情
 */
export const getBankById = (id: number) => {
  return get<QuestionBankDTO>(`/api/question-banks/${id}`)
}

/**
 * 创建题库
 */
export const createBank = (data: CreateQuestionBankRequest) => {
  return post<QuestionBankDTO>('/api/question-banks', data)
}

/**
 * 更新题库
 */
export const updateBank = (id: number, data: UpdateQuestionBankRequest) => {
  return put<QuestionBankDTO>(`/api/question-banks/${id}`, data)
}

/**
 * 删除题库
 */
export const deleteBank = (id: number) => {
  return del(`/api/question-banks/${id}`)
}

// ========== 题目管理 API ==========

/**
 * 分页获取题目列表
 */
export const getQuestionsByBankIdPaged = (
  bankId: number,
  page: number = 0,
  size: number = 20,
  difficulty?: QuestionDifficulty,
  keyword?: string
) => {
  let url = `/api/questions/bank/${bankId}/page?page=${page}&size=${size}`
  if (difficulty) {
    url += `&difficulty=${difficulty}`
  }
  if (keyword?.trim()) {
    url += `&keyword=${encodeURIComponent(keyword.trim())}`
  }
  return get<PageResponse<QuestionDTO>>(url)
}

/**
 * 获取题目详情
 */
export const getQuestionById = (id: number) => {
  return get<QuestionDTO>(`/api/questions/${id}`)
}

/**
 * 创建题目
 */
export const createQuestion = (data: CreateQuestionRequest) => {
  return post<QuestionDTO>('/api/questions', data)
}

/**
 * 更新题目
 */
export const updateQuestion = (id: number, data: Partial<CreateQuestionRequest>) => {
  return put<QuestionDTO>(`/api/questions/${id}`, data)
}

/**
 * 删除题目
 */
export const deleteQuestion = (id: number) => {
  return del(`/api/questions/${id}`)
}
