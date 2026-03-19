// 面试相关类型定义

// 难度等级
export type DifficultyLevel = 'BASIC' | 'ADVANCED' | 'EXPERT';

// 当前问题 DTO
export interface CurrentQuestionDTO {
  questionIndex: number;
  question: string;
  category: string;
  difficulty: DifficultyLevel;
  knowledgeBaseId: number | null;
  knowledgeBaseName: string | null;
  referenceContext: string | null;
}

export interface InterviewSession {
  sessionId: string;
  resumeText: string;
  totalQuestions: number;
  currentQuestionIndex: number;
  questions: InterviewQuestion[];
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'EVALUATED';
}

export interface InterviewQuestion {
  questionIndex: number;
  question: string;
  type: QuestionType;
  category: string;
  userAnswer: string | null;
  score: number | null;
  feedback: string | null;
  isFollowUp?: boolean;
  parentQuestionIndex?: number | null;
}

export type QuestionType = 
  | 'PROJECT' 
  | 'JAVA_BASIC' 
  | 'JAVA_COLLECTION' 
  | 'JAVA_CONCURRENT' 
  | 'MYSQL' 
  | 'REDIS' 
  | 'SPRING' 
  | 'SPRING_BOOT';

export interface CreateInterviewRequest {
  resumeText: string;
  questionCount: number;
  resumeId?: number;
  forceCreate?: boolean;  // 是否强制创建新会话（忽略未完成的会话）
  questionBankIds?: number[];  // 题库ID列表
  knowledgeBaseIds?: number[];  // 知识库ID列表
}

export interface SubmitAnswerRequest {
  sessionId: string;
  questionIndex: number;
  answer: string;
}

export interface SubmitAnswerResponse {
  hasNextQuestion: boolean;
  nextQuestion: CurrentQuestionDTO | null;
  currentIndex: number;
  totalQuestions: number;
}

export interface CurrentQuestionResponse {
  completed: boolean;
  question?: InterviewQuestion;
  message?: string;
}

export interface InterviewReport {
  sessionId: string;
  totalQuestions: number;
  overallScore: number;
  categoryScores: CategoryScore[];
  questionDetails: QuestionEvaluation[];
  overallFeedback: string;
  strengths: string[];
  improvements: string[];
  referenceAnswers: ReferenceAnswer[];
}

export interface CategoryScore {
  category: string;
  score: number;
  questionCount: number;
}

export interface QuestionEvaluation {
  questionIndex: number;
  question: string;
  category: string;
  userAnswer: string;
  score: number;
  feedback: string;
}

export interface ReferenceAnswer {
  questionIndex: number;
  question: string;
  referenceAnswer: string;
  keyPoints: string[];
}

// 评分趋势
export interface ScoreTrend {
  dailyScores: DailyScore[];
  statistics: ScoreStatistics;
}

export interface DailyScore {
  date: string;  // ISO 日期格式
  averageScore: number;
  interviewCount: number;
}

export interface ScoreStatistics {
  averageScore: number;
  highestScore: number;
  lowestScore: number;
  totalInterviews: number;
}

// 切换知识库请求
export interface SwitchKnowledgeBaseRequest {
  knowledgeBaseIds: number[];
}

// 能力画像 DTO
export interface AbilityProfileDTO {
  overallScore: number;
  categoryScores: Record<string, CategoryScoreDTO>;
  strengths: string[];
  weaknesses: string[];
}

// 后端 CategoryScoreDTO 格式 (Map 格式)
export interface CategoryScoreDTO {
  category: string;
  totalScore: number;
  count: number;
  avgScore: number;
}
