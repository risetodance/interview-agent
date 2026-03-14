import { get, post, del, uploadFile } from '../utils/request'

// 知识库
export interface Knowledgebase {
  id: number
  name: string
  description?: string
  documentCount: number
  status: KnowledgebaseStatus
  createdAt: string
  updatedAt: string
}

// 知识库状态
export type KnowledgebaseStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

// 知识库列表查询参数
export interface KnowledgebaseListParams {
  page?: number
  pageSize?: number
  keyword?: string
}

// 知识库列表响应
export interface KnowledgebaseListResult {
  list: Knowledgebase[]
  total: number
  page: number
  pageSize: number
}

// RAG 聊天消息
export interface ChatMessage {
  id: number
  type: 'question' | 'answer'
  content: string
  timestamp: string
}

// RAG 聊天请求
export interface RagChatRequest {
  knowledgebaseId: number
  message: string
  history?: Array<{
    role: 'user' | 'assistant'
    content: string
  }>
}

// RAG 聊天响应
export interface RagChatResponse {
  answer: string
  sources?: Array<{
    documentId: number
    documentName: string
    chunk: string
    similarity: number
  }>
}

/**
 * 获取知识库列表
 */
export const getKnowledgebaseList = (params?: KnowledgebaseListParams) => {
  return get<any>('/api/knowledgebase/list', params).then(data => {
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
 * 获取知识库详情
 */
export const getKnowledgebaseDetail = (id: number) => {
  return get<Knowledgebase>(`/api/knowledgebase/${id}`)
}

/**
 * 创建知识库
 */
export const createKnowledgebase = (data: { name: string; description?: string }) => {
  return post<Knowledgebase>('/api/knowledgebase/query', data)
}

/**
 * 上传文档到知识库
 */
export const uploadToKnowledgebase = (
  filePath: string,
  name: string,
  description?: string
) => {
  return uploadFile<Knowledgebase>(filePath, {
    url: '/api/knowledgebase/upload',
    name: 'file',
    formData: {
      name,
      description: description || ''
    },
    showLoading: true
  })
}

/**
 * 删除知识库
 */
export const deleteKnowledgebase = (id: number) => {
  return del(`/api/knowledgebase/${id}`)
}

/**
 * RAG 问答
 */
export const ragChat = (data: RagChatRequest) => {
  return post<RagChatResponse>('/api/rag-chat/sessions', {
    knowledgebaseId: data.knowledgebaseId,
    message: data.message
  })
}

/**
 * 获取 RAG 会话列表
 */
export const getRagSessions = () => {
  return get('/api/rag-chat/sessions')
}

/**
 * 获取 RAG 消息历史
 */
export const getRagMessages = (sessionId: number) => {
  return get(`/api/rag-chat/sessions/${sessionId}`)
}
