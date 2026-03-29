// 面试官角色相关类型定义（与后端 DTO 精确对应）

/**
 * 面试官角色 DTO（对应后端 InterviewerRoleDTO）
 */
export interface InterviewerRole {
  id: number;
  roleName: string;
  roleCode: string;
  description: string;
  scoringPrompt: string;
  questionPrompt: string;
  weight: number; // 0.0-1.0
  icon: string | null;
  sortOrder: number;
  status: boolean;
  defaultTemplate: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * 创建/更新角色请求（对应后端 CreateInterviewerRoleRequest）
 */
export interface CreateRoleRequest {
  roleName: string;
  roleCode: string;
  description?: string;
  scoringPrompt: string;
  questionPrompt: string;
  weight?: number;
  icon?: string;
  sortOrder?: number;
  status?: boolean;
  defaultTemplate?: boolean;
}

/**
 * 更新权重请求（对应后端 UpdateWeightRequest）
 */
export interface UpdateWeightRequest {
  weight: number;
}

/**
 * 视角评分状态
 */
export type PerspectiveScoreStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/**
 * 视角评分 DTO（对应后端 PerspectiveScoreDTO）
 */
export interface PerspectiveScore {
  id: number;
  sessionId: number;
  perspectiveId: number;
  perspectiveName: string;
  perspectiveIcon: string | null;
  questionIndex: number;
  score: number | null;
  feedback: string | null;
  strengths: string[];
  improvements: string[];
  status: string; // PerspectiveScoreStatus
  errorMessage: string | null;
  completedAt: string | null;
  createdAt: string;
  weight?: number; // 会话级权重
}

/**
 * 视角问题评分 DTO（对应后端 PerspectiveQuestionScoreDTO）
 */
export interface PerspectiveQuestionScore {
  questionIndex: number;
  score: number;
  feedback: string;
  question: string;
  userAnswer?: string;
  referenceAnswer?: string;
  keyPoints?: string[];
}

/**
 * 视角详情 DTO（对应后端 PerspectiveDetailDTO）
 */
export interface PerspectiveDetailDTO {
  perspectiveId: number;
  roleName: string;
  perspectiveIcon: string | null;
  score: number;
  feedback: string;
  strengths: string[];
  improvements: string[];
  questionScores: PerspectiveQuestionScore[];
}

/**
 * 综合报告 DTO（对应后端 ComprehensiveReportDTO）
 */
export interface ComprehensiveReportDTO {
  sessionId: string;
  overallScore: number; // 加权平均分
  perspectives: PerspectiveScore[]; // 各视角得分详情
  comprehensiveFeedback: string; // 综合评价
  strengths: string[]; // 综合优势
  improvements: string[]; // 综合改进建议
  perspectiveDetails: Record<string, PerspectiveDetailDTO>; // 视角详情映射
}

/**
 * 当前问题 DTO（扩展，支持视角标签）
 */
export interface CurrentQuestionWithPerspectiveDTO {
  questionIndex: number;
  question: string;
  category: string;
  difficulty: 'BASIC' | 'ADVANCED' | 'EXPERT';
  knowledgeBaseId: number | null;
  knowledgeBaseName: string | null;
  referenceContext: string | null;
  isFollowUp?: boolean;
  relatedIndex?: number;
  relatedQuestion?: string;
  // 多视角支持
  createdByPerspectiveId?: number;
  createdByPerspectiveName?: string;
}

/**
 * 答题历史（扩展，支持视角标签）
 */
export interface AnswerHistoryWithPerspectiveDTO {
  questionIndex: number;
  question: string;
  category: string;
  difficulty: 'BASIC' | 'ADVANCED' | 'EXPERT';
  userAnswer: string;
  // 多视角支持
  createdByPerspectiveId?: number;
  createdByPerspectiveName?: string;
}
