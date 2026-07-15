import { get, post, del, put, uploadFile, downloadFile } from '../utils/request'
import { useUserStore } from '../stores/user'
import { storeToRefs } from 'pinia'

// 环境配置
const baseURL = import.meta.env.VITE_API_BASE_URL?.replace(/\/api$/, '') || 'https://api.interview-guide.com'

// 向量化状态
export type VectorStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

// 知识库
export interface Knowledgebase {
  id: number
  name: string
  category: string | null
  originalFilename: string
  fileSize: number
  contentType: string
  uploadedAt: string
  lastAccessedAt: string
  accessCount: number
  vectorStatus: VectorStatus
  vectorError: string | null
  chunkCount: number
}

// 知识库状态（兼容旧接口）
export type KnowledgebaseStatus = VectorStatus

// 知识库统计信息
export interface KnowledgebaseStats {
  totalCount: number
  totalAccessCount: number
  totalStorageSize: number
  completedCount: number
  processingCount: number
}

// 排序选项
export type SortOption = 'time' | 'size' | 'access'

// 知识库列表查询参数
export interface KnowledgebaseListParams {
  page?: number
  pageSize?: number
  keyword?: string
  sortBy?: SortOption
  vectorStatus?: VectorStatus
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
  sessionId: number
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
  return get<Knowledgebase[] | KnowledgebaseListResult>('/api/knowledgebase/list', params).then(data => {
    // 如果是分页格式（包含list属性），直接返回
    if (data && typeof data === 'object' && 'list' in data) {
      return data as KnowledgebaseListResult
    }
    // 后端返回数组的情况，包装成分页格式
    const list = Array.isArray(data) ? data : []
    return {
      list: list as Knowledgebase[],
      total: list.length,
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
 * 获取知识库统计信息
 */
export const getKnowledgebaseStats = () => {
  return get<KnowledgebaseStats>('/api/knowledgebase/stats')
}

/**
 * 获取所有分类
 */
export const getAllCategories = () => {
  return get<string[]>('/api/knowledgebase/categories')
}

/**
 * 根据分类获取知识库
 */
export const getKnowledgebaseByCategory = (category: string) => {
  return get<Knowledgebase[]>(`/api/knowledgebase/category/${encodeURIComponent(category)}`)
}

/**
 * 搜索知识库
 */
export const searchKnowledgebase = (keyword: string) => {
  return get<Knowledgebase[]>(`/api/knowledgebase/search?keyword=${encodeURIComponent(keyword)}`)
}

/**
 * 更新知识库分类
 */
export const updateKnowledgebaseCategory = (id: number, category: string | null) => {
  return put(`/api/knowledgebase/${id}/category`, { category })
}

/**
 * 上传文档到知识库
 */
export const uploadToKnowledgebase = (
  filePath: string,
  name: string,
  category?: string
) => {
  return uploadFile<{ knowledgeBase: Knowledgebase; storage: { fileKey: string; fileUrl: string }; duplicate: boolean }>(filePath, {
    url: '/api/knowledgebase/upload',
    name: 'file',
    formData: {
      name,
      category: category || ''
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
 * 重新向量化知识库
 */
export const revectorizeKnowledgebase = (id: number) => {
  return post(`/api/knowledgebase/${id}/revectorize`, {})
}

// ========== SSE 流式接口 ==========

/**
 * RAG 聊天回调接口
 */
export interface RagChatCallbacks {
  onConnected?: () => void
  onMessage?: (content: string) => void
  onComplete?: () => void
  onError?: (error: string) => void
}

/**
 * 连接 RAG 聊天 SSE 流
 * @param sessionId 会话 ID
 * @param question 问题
 * @param callbacks 回调
 * @returns 清理函数
 */
export const connectRagChatStream = (
  sessionId: number,
  question: string,
  callbacks: RagChatCallbacks
): (() => void) => {
  const userStore = useUserStore()
  const { token } = storeToRefs(userStore)

  // 构建 SSE URL
  const streamUrl = `${baseURL}/api/rag-chat/sessions/${sessionId}/messages/stream`

  // 构建请求头
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token.value) {
    headers['Authorization'] = `Bearer ${token.value}`
  }

  // #ifdef H5
  // H5 环境使用原生 EventSource（但需要POST方法，EventSource只支持GET）
  // 所以 H5 也使用 fetch + ReadableStream
  return connectRagChatFetch(streamUrl, headers, question, callbacks)
  // #endif

  // #ifndef H5
  // 小程序环境使用 uni.request + enableChunked
  return connectRagChatUni(streamUrl, headers, question, callbacks)
  // #endif
}

/**
 * H5 环境 RAG SSE 连接（使用 fetch + ReadableStream）
 */
const connectRagChatFetch = (
  url: string,
  headers: Record<string, string>,
  question: string,
  callbacks: RagChatCallbacks
): (() => void) => {
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
  let aborted = false

  fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ question }),
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`请求失败 (${response.status})`)
      }

      callbacks.onConnected?.()

      const stream = response.body
      if (!stream) {
        throw new Error('无法获取响应流')
      }

      reader = stream.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      const readNext = () => {
        reader!.read().then(({ done, value }) => {
          if (aborted) return

          if (done) {
            // 处理缓冲区中剩余的数据
            if (buffer.trim()) {
              const content = extractSSEData(buffer)
              if (content) callbacks.onMessage?.(content)
            }
            callbacks.onComplete?.()
            return
          }

          buffer += decoder.decode(value, { stream: true })

          // SSE 消息以 \n\n 分隔
          const messages = buffer.split('\n\n')
          buffer = messages.pop() || ''

          for (const msg of messages) {
            if (msg.trim()) {
              const content = extractSSEData(msg)
              if (content) callbacks.onMessage?.(content)
            }
          }

          readNext()
        }).catch(error => {
          if (!aborted) {
            callbacks.onError?.(error?.message || 'SSE 流读取失败')
          }
        })
      }

      readNext()
    })
    .catch(error => {
      if (!aborted) {
        callbacks.onError?.(error.message || 'SSE 连接失败')
      }
    })

  // 返回清理函数
  return () => {
    aborted = true
    try {
      reader?.cancel()
    } catch (e) {
      // 忽略
    }
  }
}

/**
 * 小程序环境 RAG SSE 连接（使用 uni.request + enableChunked）
 */
const connectRagChatUni = (
  url: string,
  headers: Record<string, string>,
  question: string,
  callbacks: RagChatCallbacks
): (() => void) => {
  let buffer = ''
  const abortController = { aborted: false }

  const requestTask = uni.request({
    url,
    method: 'POST',
    header: {
      ...headers,
      'Cache-Control': 'no-cache',
    },
    data: { question },
    enableChunked: true,
    // 请求正常完成（流结束）时触发；小程序 RequestTask 无 onComplete 方法，
    // 改用 uni.request 的 success 回调作为流结束信号
    success: () => {
      if (!abortController.aborted) {
        if (buffer.trim()) {
          const content = extractSSEData(buffer)
          if (content) callbacks.onMessage?.(content)
        }
        callbacks.onComplete?.()
      }
    },
    fail: (err) => {
      if (!abortController.aborted) {
        callbacks.onError?.(err.errMsg || 'SSE 连接失败')
      }
    }
  })

  // 监听数据块
  ;(requestTask as any).onChunkReceived((res: { data: ArrayBuffer }) => {
    try {
      const chunkStr = new TextDecoder().decode(res.data)
      buffer += chunkStr

      const messages = buffer.split('\n\n')
      buffer = messages.pop() || ''

      for (const msg of messages) {
        if (msg.trim()) {
          const content = extractSSEData(msg)
          if (content) callbacks.onMessage?.(content)
        }
      }
    } catch (e) {
      // 忽略解析错误
    }
  })

  return () => {
    abortController.aborted = true
    try {
      (requestTask as any).abort?.()
    } catch (e) {
      // 忽略
    }
  }
}

/**
 * 从 SSE 消息中提取 data 内容
 */
const extractSSEData = (message: string): string | null => {
  const lines = message.split('\n')
  const dataParts: string[] = []

  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataParts.push(line.slice(5))
    }
  }

  if (dataParts.length === 0) return null

  // 合并并还原转义的换行符
  return dataParts.join('')
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '\r')
}

/**
 * RAG 问答（已废弃，使用 connectRagChatStream 代替）
 */
export const ragChat = (data: RagChatRequest): Promise<RagChatResponse> => {
  console.warn('ragChat 已废弃，请使用 connectRagChatStream')
  return Promise.reject(new Error('ragChat 已废弃'))
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

/**
 * 创建新的 RAG 会话
 */
export const createRagSession = (knowledgebaseIds: number[]) => {
  return post<{ id: number }>('/api/rag-chat/sessions', {
    knowledgeBaseIds: knowledgebaseIds
  })
}

/**
 * 删除 RAG 会话
 */
export const deleteRagSession = (sessionId: number) => {
  return del(`/api/rag-chat/sessions/${sessionId}`)
}

/**
 * 编辑 RAG 会话标题
 */
export const updateRagSessionTitle = (sessionId: number, title: string) => {
  return put(`/api/rag-chat/sessions/${sessionId}/title`, { title })
}

/**
 * 置顶/取消置顶 RAG 会话
 */
export const toggleRagSessionPin = (sessionId: number, isPinned: boolean) => {
  return put(`/api/rag-chat/sessions/${sessionId}/pin`, { isPinned })
}

/**
 * 下载知识库文件
 */
export const downloadKnowledgebase = (id: number, filename: string) => {
  return downloadFile(`/api/knowledgebase/${id}/download`, true)
}
