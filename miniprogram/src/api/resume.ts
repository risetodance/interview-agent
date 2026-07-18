import { get, post, del, uploadFile } from '../utils/request'
import { apiBaseUrl, isH5 } from '../utils/env'

/**
 * 简历分析改进建议项（对齐后端 AnalysisHistoryDTO.suggestions 中 Suggestion 记录结构）
 * - 后端真实结构为对象数组（category/priority/issue/recommendation）
 * - 旧数据/兼容数据可能是字符串数组，解析时会包装为此结构（见 parseSuggestions）
 */
export interface ResumeSuggestion {
  category?: string
  priority?: string
  issue?: string
  recommendation?: string
}

// 简历基本信息
export interface Resume {
  id: number
  name: string
  fileName: string
  fileUrl: string
  fileSize: number
  fileType: string
  status: ResumeStatus
  parseStatus: ParseStatus
  parseProgress?: number
  interviewStatus?: InterviewStatus
  createdAt: string
  updatedAt: string
}

// 面试状态
export type InterviewStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'EVALUATED' | null

// 简历投递状态
export type ResumeStatus = 'DRAFT' | 'SUBMITTED' | 'REVIEWING' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

// 简历解析状态
export type ParseStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

// 简历列表查询参数
export interface ResumeListParams {
  page?: number
  pageSize?: number
  status?: ResumeStatus
  parseStatus?: ParseStatus
  keyword?: string
}

// 简历列表响应
export interface ResumeListResult {
  list: Resume[]
  total: number
  page: number
  pageSize: number
}

// 简历详情（对齐后端 ResumeDetailDTO 原始结构，B13：移除自造的 basicInfo/educationList/workExperienceList/projectList/skills/certificates 等后端不提供的字段）
export interface ResumeDetail extends Resume {
  // 后端原始字段
  resumeText?: string
  storageUrl?: string
  accessCount?: number
  analyzeStatus?: ParseStatus
  analyzeError?: string
  analyses?: ResumeAnalysisHistory[]
  interviews?: ResumeInterview[]
  // 前端派生：最新一次分析结果（取 analyses[0]，供雷达图/分析展示）
  analysis?: ResumeAnalysis
}

// 分析历史项（对应后端 AnalysisHistoryDTO）
export interface ResumeAnalysisHistory {
  id?: number
  overallScore?: number
  contentScore?: number
  structureScore?: number
  skillMatchScore?: number
  expressionScore?: number
  projectScore?: number
  summary?: string
  analyzedAt?: string
  createdAt?: string
  strengths?: any[]
  suggestions?: any[]
  matchedPositions?: string[]
  scoreDetail?: { skillMatchScore?: number }
}

// 关联面试历史（对应后端 ResumeDetailDTO.interviews，结构宽松）
export interface ResumeInterview {
  sessionId?: string
  status?: string
  overallScore?: number
  totalQuestions?: number
  currentQuestionIndex?: number
  answeredCount?: number
  [key: string]: any
}

// 教育经历
export interface Education {
  id?: number
  school: string
  degree: string
  major: string
  startDate: string
  endDate?: string
  description?: string
}

// 工作经历
export interface WorkExperience {
  id?: number
  company: string
  position: string
  location?: string
  startDate: string
  endDate?: string
  description?: string
}

// 项目经验
export interface Project {
  id?: number
  name: string
  role: string
  startDate?: string
  endDate?: string
  description?: string
  technologies?: string[]
}

// 技能
export interface Skill {
  name: string
  level: 'beginner' | 'intermediate' | 'advanced' | 'expert'
  yearsOfExperience?: number
}

// 证书
export interface Certificate {
  name: string
  issuer: string
  date?: string
}

// 简历分析结果
export interface ResumeAnalysis {
  // 核心评价（来自后端 summary）
  summary?: string
  // 分析时间
  analyzedAt?: string
  // 技能匹配度
  skillMatchRate?: number
  // 匹配职位
  matchedPositions?: string[]
  // 优势
  strengths?: string[]
  // 待改进
  improvements?: string[]
  // 综合评分
  overallScore?: number
  // 分析建议（后端实为 Suggestion 记录数组，兼容旧字符串数据，见 parseSuggestions）
  suggestions?: ResumeSuggestion[]
  // 雷达图评分维度
  expressionScore?: number   // 表达专业性
  skillMatchScore?: number   // 技能匹配
  contentScore?: number      // 内容完整性
  structureScore?: number    // 结构清晰度
  projectScore?: number      // 项目经验
}

// 上传简历响应
export interface UploadResumeResult {
  id: number
  name: string
  fileName: string
  parseStatus: ParseStatus
}

/**
 * 获取简历列表
 */
export const getResumeList = (params?: ResumeListParams) => {
  // mock返回的是分页对象，后端返回的是List<Resume>
  return get<any>('/api/resumes', params).then(data => {
    // 如果是mock（包含list属性），直接返回
    if (data && data.list) {
      return data
    }
    // 后端返回数组的情况，需要映射字段名
    const list = (data || []).map((item: any) => ({
      ...item,
      // 将 filename 映射为 name
      name: item.filename || item.name || '未命名简历',
      // 确保 fileName 也有值
      fileName: item.filename || item.fileName || '',
      // 将 analyzeStatus 映射为 parseStatus
      parseStatus: item.analyzeStatus || item.parseStatus || 'PENDING',
      // 将 uploadedAt 映射为 updatedAt
      updatedAt: item.uploadedAt || item.updatedAt,
      // 面试状态
      interviewStatus: item.interviewStatus || null
    }))
    return {
      list: list,
      total: list.length || 0,
      page: params?.page || 1,
      pageSize: params?.pageSize || 20
    }
  })
}

/**
 * 获取简历详情
 * 转换后端返回的数据结构为前端期望的格式
 */
export const getResumeDetail = (id: number) => {
  return get<any>(`/api/resumes/${id}/detail`).then(data => {
    // 从 analyses 获取最新的分析结果
    const latestAnalysis = data.analyses?.[0]

    // 处理 strengths（可能是字符串 JSON 或字符串数组）
    const parseJsonField = (field: any): string[] => {
      if (!field) return []
      if (Array.isArray(field)) return field
      if (typeof field === 'string') {
        try {
          const parsed = JSON.parse(field)
          return Array.isArray(parsed) ? parsed : []
        } catch {
          return [field]
        }
      }
      return []
    }

    // 处理 suggestions：后端实为 Suggestion 记录数组（category/priority/issue/recommendation）
    // 兼容旧数据：若数组项是字符串，包装为 { category: '改进建议', priority: '中', issue: <字符串>, recommendation: '' }
    // 写法对齐后端 ResumeGradingService 的兼容逻辑
    const parseSuggestions = (field: any): ResumeSuggestion[] => {
      let raw: any[] = []
      if (!field) return []
      if (Array.isArray(field)) {
        raw = field
      } else if (typeof field === 'string') {
        try {
          const parsed = JSON.parse(field)
          if (Array.isArray(parsed)) raw = parsed
        } catch {
          // 字符串无法解析时按单条处理
          raw = [field]
        }
      }
      return raw.map((item: any): ResumeSuggestion => {
        if (typeof item === 'string') {
          return {
            category: '改进建议',
            priority: '中',
            issue: item,
            recommendation: ''
          }
        }
        return item && typeof item === 'object' ? item : {}
      })
    }

    // 构建前端期望的数据结构（B13：对齐后端 ResumeDetailDTO 原始结构，不再自造 basicInfo/educationList 等）
    return {
      id: data.id,
      name: data.filename,
      fileName: data.filename,
      fileUrl: data.storageUrl,
      fileSize: data.fileSize,
      fileType: data.contentType,
      status: 'DRAFT' as const,
      parseStatus: data.analyzeStatus || 'PENDING',
      createdAt: data.uploadedAt,
      updatedAt: data.uploadedAt,
      // 后端原始字段透传
      resumeText: data.resumeText,
      storageUrl: data.storageUrl,
      accessCount: data.accessCount,
      analyzeStatus: data.analyzeStatus || 'PENDING',
      analyzeError: data.analyzeError,
      analyses: data.analyses || [],
      interviews: data.interviews || [],
      // 派生：最新一次分析结果（供雷达图/分析展示）
      analysis: latestAnalysis ? {
        summary: latestAnalysis.summary,
        overallScore: latestAnalysis.overallScore,
        analyzedAt: latestAnalysis.analyzedAt || latestAnalysis.createdAt,
        skillMatchRate: latestAnalysis.skillMatchScore
          ? Math.min(100, Math.round(latestAnalysis.skillMatchScore / 25 * 100))
          : latestAnalysis.scoreDetail?.skillMatchScore
            ? Math.min(100, Math.round(latestAnalysis.scoreDetail.skillMatchScore / 25 * 100))
            : 0,
        matchedPositions: latestAnalysis.matchedPositions || [],
        strengths: parseJsonField(latestAnalysis.strengths),
        improvements: parseJsonField(latestAnalysis.suggestions),
        suggestions: parseSuggestions(latestAnalysis.suggestions),
        expressionScore: latestAnalysis.expressionScore,
        skillMatchScore: latestAnalysis.skillMatchScore,
        contentScore: latestAnalysis.contentScore,
        structureScore: latestAnalysis.structureScore,
        projectScore: latestAnalysis.projectScore
      } : undefined
    } as ResumeDetail
  })
}

/**
 * 上传简历
 */
export const uploadResume = (filePath: string, name?: string) => {
  return uploadFile<UploadResumeResult>(filePath, {
    url: '/api/resumes/upload',
    name: 'file',
    formData: name ? { name } : {},
    showLoading: true
  })
}

/**
 * 删除简历
 */
export const deleteResume = (id: number) => {
  return del(`/api/resumes/${id}`)
}

/**
 * 重新分析简历
 */
export const reanalyzeResume = (id: number) => {
  return post(`/api/resumes/${id}/reanalyze`)
}

/**
 * 重新上传简历
 */
export const reuploadResume = (id: number, filePath: string) => {
  return uploadFile<any>(filePath, {
    url: `/api/resumes/${id}/reupload`,
    name: 'file',
    showLoading: true
  })
}

/**
 * 下载简历PDF
 * H5: 使用fetch下载并触发浏览器下载
 * 小程序: 使用uni.downloadFile
 *
 * D2/N6: 基础地址与 isH5 已统一收敛到 utils/env.ts，避免本地重复拼接
 */
export const downloadResume = (id: number): Promise<{ tempFilePath?: string; url?: string }> => {
  // apiBaseUrl 已去掉 /api 尾，各模块自带 /api 前缀
  const url = `${apiBaseUrl}/api/resumes/${id}/export`

  // 获取 token
  const token = uni.getStorageSync('token')

  if (isH5) {
    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    return fetch(url, {
      headers
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`下载失败: ${response.status}`)
        }
        return response.blob()
      })
      .then(blob => {
        // 创建blob URL并触发下载
        const blobUrl = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = blobUrl
        link.download = `resume_${id}.pdf`
        link.click()

        // 清理blob URL
        setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)

        return { url }
      })
      .catch(err => {
        throw err
      })
  }

  // 小程序环境：使用 uni.downloadFile
  return new Promise((resolve, reject) => {
    const header: Record<string, string> = {}
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    uni.downloadFile({
      url: url,
      header: header,
      success: (res) => {
        if (res.statusCode === 200) {
          resolve({ tempFilePath: res.tempFilePath })
        } else {
          reject(new Error(`下载失败: ${res.statusCode}`))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}
