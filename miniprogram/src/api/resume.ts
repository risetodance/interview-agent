import { get, post, put, del, uploadFile } from '../utils/request'

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
  createdAt: string
  updatedAt: string
}

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

// 简历详情
export interface ResumeDetail extends Resume {
  // 基本信息
  basicInfo?: {
    name: string
    gender?: string
    age?: number
    phone?: string
    email?: string
    location?: string
    avatar?: string
    summary?: string
  }
  // 教育经历
  educationList?: Education[]
  // 工作经历
  workExperienceList?: WorkExperience[]
  // 项目经验
  projectList?: Project[]
  // 技能
  skills?: Skill[]
  // 证书
  certificates?: Certificate[]
  // 分析结果
  analysis?: ResumeAnalysis
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
  // 分析建议
  suggestions?: string[]
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
      updatedAt: item.uploadedAt || item.updatedAt
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

    // 处理 strengths 和 suggestions（可能是字符串 JSON 或数组）
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

    // 构建前端期望的数据结构
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
      resumeText: data.resumeText,
      // 基本信息 - 从 resumeText 提取或使用默认值
      basicInfo: {
        name: data.filename?.replace(/\.[^/.]+$/, '') || '未命名简历',
        summary: latestAnalysis?.summary || '暂无分析结果'
      },
      // 分析结果
      analysis: latestAnalysis ? {
        overallScore: latestAnalysis.overallScore,
        // 技能匹配度上限 100%
        skillMatchRate: latestAnalysis.skillMatchScore
          ? Math.min(100, Math.round(latestAnalysis.skillMatchScore / 25 * 100))
          : latestAnalysis.scoreDetail?.skillMatchScore
            ? Math.min(100, Math.round(latestAnalysis.scoreDetail.skillMatchScore / 25 * 100))
            : 0,
        // 匹配的岗位列表（从后端获取）
        matchedPositions: latestAnalysis.matchedPositions || [],
        strengths: parseJsonField(latestAnalysis.strengths),
        improvements: parseJsonField(latestAnalysis.suggestions),
        suggestions: parseJsonField(latestAnalysis.suggestions)
      } : undefined,
      // 分析历史
      analyses: data.analyses || []
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
 * 更新简历基本信息
 */
export const updateResumeBasicInfo = (id: number, data: ResumeDetail['basicInfo']) => {
  return put(`/api/resumes/${id}/basic-info`, data)
}

/**
 * 更新简历投递状态
 */
export const updateResumeStatus = (id: number, status: ResumeStatus) => {
  return put(`/api/resumes/${id}/status`, { status })
}

/**
 * 下载简历PDF
 * H5: 使用fetch下载并触发浏览器下载
 * 小程序: 使用uni.downloadFile
 */
export const downloadResume = (id: number): Promise<{ tempFilePath?: string; url?: string }> => {
  // 基础URL已经包含了/api，所以直接拼接
  const baseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/api$/, '')
  const url = `${baseUrl}/api/resumes/${id}/export`

  // 获取 token
  const token = uni.getStorageSync('token')

  // 优先尝试 H5 环境下的 fetch 方式（可携带自定义 header）
  // 条件编译在 .ts 文件中不生效，使用运行时检测
  const isH5 = typeof window !== 'undefined'

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
        console.error('下载失败:', err)
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
