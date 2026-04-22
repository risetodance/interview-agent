import { request } from './request';
import type {
  AbilityProfileDTO,
  CreateInterviewRequest,
  CurrentQuestionDTO,
  InterviewReport,
  InterviewSession,
  ScoreTrend,
  SessionProgressDTO,
  SubmitAnswerRequest,
} from '../types/interview';

/**
 * 进度阶段描述
 */
export const PROGRESS_LABELS = {
  progress_scoring: '正在评分...',
  progress_deciding: '正在决策...',
  progress_search_preparing: '正在准备搜索...',
  progress_generating: '正在出题...',
} as const;

export type ProgressStageKey = keyof typeof PROGRESS_LABELS;

export const interviewApi = {
  /**
   * 创建面试会话
   */
  async createSession(req: CreateInterviewRequest): Promise<InterviewSession> {
    return request.post<InterviewSession>('/api/interview/sessions', req, {
      timeout: 180000, // 3分钟超时，AI生成问题需要时间
    });
  },

  /**
   * 获取会话信息
   */
  async getSession(sessionId: string): Promise<InterviewSession> {
    return request.get<InterviewSession>(`/api/interview/sessions/${sessionId}`);
  },

  /**
   * 获取当前问题
   */
  async getCurrentQuestion(sessionId: string): Promise<CurrentQuestionDTO> {
    return request.get<CurrentQuestionDTO>(`/api/interview/sessions/${sessionId}/current`);
  },

  /**
   * 获取会话进度
   */
  async getSessionProgress(sessionId: string): Promise<SessionProgressDTO> {
    return request.get<SessionProgressDTO>(`/api/interview/sessions/${sessionId}/progress`);
  },

  /**
   * 提交答案（工作流模式，立即返回，后台通过 SSE 推送结果）
   */
  async submitAnswer(req: SubmitAnswerRequest): Promise<void> {
    return request.post<void>(
      `/api/interview/sessions/${req.sessionId}/answer`,
      { questionIndex: req.questionIndex, answer: req.answer }
    );
  },

  /**
   * 获取面试报告
   */
  async getReport(sessionId: string): Promise<InterviewReport> {
    return request.get<InterviewReport>(`/api/interview/sessions/${sessionId}/report`, {
      timeout: 180000, // 3分钟超时，AI评估需要时间
    });
  },

  /**
   * 查找未完成的面试会话
   */
  async findUnfinishedSession(resumeId: number): Promise<InterviewSession | null> {
    try {
      return await request.get<InterviewSession>(`/api/interview/sessions/unfinished/${resumeId}`);
    } catch {
      // 如果没有未完成的会话，返回null
      return null;
    }
  },

  /**
   * 暂存答案（不进入下一题）
   */
  async saveAnswer(req: SubmitAnswerRequest): Promise<void> {
    return request.put<void>(
      `/api/interview/sessions/${req.sessionId}/answers`,
      { questionIndex: req.questionIndex, answer: req.answer }
    );
  },

  /**
   * 提前交卷
   */
  async completeInterview(sessionId: string): Promise<void> {
    return request.post<void>(`/api/interview/sessions/${sessionId}/complete`);
  },

  /**
   * 切换面试知识库
   * 会根据新的知识库重新生成未回答的问题
   */
  async switchKnowledgeBase(sessionId: string, knowledgeBaseIds: number[]): Promise<InterviewSession> {
    return request.put<InterviewSession>(
      `/api/interview/sessions/${sessionId}/knowledge-base`,
      { knowledgeBaseIds }
    );
  },

  /**
   * 获取评分趋势
   */
  async getScoreTrend(): Promise<ScoreTrend> {
    return request.get<ScoreTrend>('/api/interview/score-trend');
  },

  /**
   * 获取能力画像（面试结束后）
   */
  async getAbilityProfile(sessionId: string): Promise<AbilityProfileDTO> {
    return request.get<AbilityProfileDTO>(`/api/interview/sessions/${sessionId}/ability-profile`);
  },

  /**
   * SSE 连接事件类型
   */
  SSE_EVENT_TYPES: {
    CONNECTED: 'connected',
    QUESTION: 'question',
    EVALUATION: 'evaluation',
    INTERVIEW_COMPLETE: 'interview_complete',
    ERROR: 'error',
    PROGRESS_SCORING: 'progress_scoring',
    PROGRESS_DECIDING: 'progress_deciding',
    PROGRESS_SEARCH_PREPARING: 'progress_search_preparing',
    PROGRESS_GENERATING: 'progress_generating',
  } as const,

  /**
   * SSE 连接结果
   */
  connectInterviewStream(
    sessionId: string,
    callbacks: {
      onConnected?: () => void;
      onQuestion?: (data: CurrentQuestionDTO) => void;
      onEvaluation?: (data: { questionIndex: number; score: number; feedback: string }) => void;
      onComplete?: (data: { overallScore: number; summary: Record<string, unknown> }) => void;
      onError?: (error: string) => void;
      onProgress?: (stage: ProgressStageKey) => void;
    }
  ): { eventSource: EventSource; cleanup: () => void } {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
    const token = localStorage.getItem('auth_token');

    // EventSource 不支持自定义 header，通过 URL 参数传递 token
    const streamUrl = token
      ? `${apiBaseUrl}/api/interview/sessions/${sessionId}/stream?token=${encodeURIComponent(token)}`
      : `${apiBaseUrl}/api/interview/sessions/${sessionId}/stream`;

    const eventSource = new EventSource(streamUrl, { withCredentials: true });

    eventSource.addEventListener(this.SSE_EVENT_TYPES.CONNECTED, () => {
      callbacks.onConnected?.();
    });

    eventSource.addEventListener(this.SSE_EVENT_TYPES.QUESTION, (event: MessageEvent) => {
      const data = JSON.parse(event.data) as CurrentQuestionDTO;
      callbacks.onQuestion?.(data);
    });

    eventSource.addEventListener(this.SSE_EVENT_TYPES.EVALUATION, (event: MessageEvent) => {
      const data = JSON.parse(event.data);
      callbacks.onEvaluation?.(data);
    });

    eventSource.addEventListener(this.SSE_EVENT_TYPES.INTERVIEW_COMPLETE, (event: MessageEvent) => {
      const data = JSON.parse(event.data);
      callbacks.onComplete?.(data);
      eventSource.close();
    });

    eventSource.addEventListener(this.SSE_EVENT_TYPES.ERROR, (event: MessageEvent) => {
      const data = JSON.parse(event.data);
      callbacks.onError?.(data.message || '未知错误');
      // 关闭连接，防止自动重连
      eventSource.close();
    });

    // 进度事件监听
    const progressEvents = [
      this.SSE_EVENT_TYPES.PROGRESS_SCORING,
      this.SSE_EVENT_TYPES.PROGRESS_DECIDING,
      this.SSE_EVENT_TYPES.PROGRESS_SEARCH_PREPARING,
      this.SSE_EVENT_TYPES.PROGRESS_GENERATING,
    ];

    progressEvents.forEach((eventType) => {
      eventSource.addEventListener(eventType, () => {
        callbacks.onProgress?.(eventType as ProgressStageKey);
      });
    });

    eventSource.onerror = () => {
      console.log('SSE onerror triggered, closing connection');
      // 关闭连接
      eventSource.close();
    };

    // 返回 EventSource 对象和清理函数
    return {
      eventSource,
      cleanup: () => eventSource.close()
    };
  },
};
