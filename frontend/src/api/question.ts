import { request } from './request';

// 题库类型
export type QuestionBankType = 'SYSTEM' | 'USER';

// 题目难度
export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

// 题库 DTO
export interface QuestionBankDTO {
  id: number;
  name: string;
  description: string | null;
  type: QuestionBankType;
  userId: number | null;
  questionCount: number;
  createdAt: string;
  updatedAt: string;
}

// 题目 DTO
export interface QuestionDTO {
  id: number;
  questionBankId: number;
  content: string;
  answer: string;
  difficulty: QuestionDifficulty;
  tags: string[] | null;
  createdAt: string;
  updatedAt: string;
}

// 创建题库请求
export interface CreateQuestionBankRequest {
  name: string;
  description?: string;
}

// 更新题库请求
export interface UpdateQuestionBankRequest {
  name?: string;
  description?: string;
}

// 创建题目请求
export interface CreateQuestionRequest {
  questionBankId: number;
  content: string;
  answer?: string;
  difficulty?: QuestionDifficulty;
  tags?: string[];
}

// 批量创建题目请求
export interface BatchCreateQuestionsRequest {
  questions: CreateQuestionRequest[];
}

// 分页响应
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const questionApi = {
  // ========== 题库管理 ==========

  /**
   * 获取所有系统预置题库
   */
  async getSystemBanks(): Promise<QuestionBankDTO[]> {
    return request.get<QuestionBankDTO[]>('/api/question-banks/system');
  },

  /**
   * 获取用户的题库列表（包括系统预置）
   */
  async getUserBanks(): Promise<QuestionBankDTO[]> {
    return request.get<QuestionBankDTO[]>('/api/question-banks');
  },

  /**
   * 获取用户自定义题库
   */
  async getMyBanks(): Promise<QuestionBankDTO[]> {
    return request.get<QuestionBankDTO[]>('/api/question-banks/my');
  },

  /**
   * 获取题库详情
   */
  async getBankById(id: number): Promise<QuestionBankDTO> {
    return request.get<QuestionBankDTO>(`/api/question-banks/${id}`);
  },

  /**
   * 创建题库
   */
  async createBank(data: CreateQuestionBankRequest): Promise<QuestionBankDTO> {
    return request.post<QuestionBankDTO>('/api/question-banks', data);
  },

  /**
   * 更新题库
   */
  async updateBank(id: number, data: UpdateQuestionBankRequest): Promise<QuestionBankDTO> {
    return request.put<QuestionBankDTO>(`/api/question-banks/${id}`, data);
  },

  /**
   * 删除题库
   */
  async deleteBank(id: number): Promise<void> {
    return request.delete(`/api/question-banks/${id}`);
  },

  // ========== 题目管理 ==========

  /**
   * 获取题库下的所有题目
   */
  async getQuestionsByBankId(bankId: number): Promise<QuestionDTO[]> {
    return request.get<QuestionDTO[]>(`/api/questions/bank/${bankId}`);
  },

  /**
   * 分页获取题库下的题目
   */
  async getQuestionsByBankIdPaged(
    bankId: number,
    page: number = 0,
    size: number = 20
  ): Promise<PageResponse<QuestionDTO>> {
    return request.get<PageResponse<QuestionDTO>>(
      `/api/questions/bank/${bankId}/page?page=${page}&size=${size}`
    );
  },

  /**
   * 获取题目详情
   */
  async getQuestionById(id: number): Promise<QuestionDTO> {
    return request.get<QuestionDTO>(`/api/questions/${id}`);
  },

  /**
   * 根据难度筛选题目
   */
  async getQuestionsByDifficulty(
    bankId: number,
    difficulty: QuestionDifficulty
  ): Promise<QuestionDTO[]> {
    return request.get<QuestionDTO[]>(
      `/api/questions/bank/${bankId}/difficulty/${difficulty}`
    );
  },

  /**
   * 随机获取题目（用于面试）
   */
  async getRandomQuestions(bankId: number, limit: number = 5): Promise<QuestionDTO[]> {
    return request.get<QuestionDTO[]>(`/api/questions/bank/${bankId}/random?limit=${limit}`);
  },

  /**
   * 从多个题库随机获取题目
   */
  async getRandomQuestionsFromBanks(bankIds: number[], limit: number = 5): Promise<QuestionDTO[]> {
    const ids = bankIds.join(',');
    return request.get<QuestionDTO[]>(`/api/questions/banks/random?bankIds=${ids}&limit=${limit}`);
  },

  /**
   * 创建题目
   */
  async createQuestion(data: CreateQuestionRequest): Promise<QuestionDTO> {
    return request.post<QuestionDTO>('/api/questions', data);
  },

  /**
   * 批量创建题目
   */
  async batchCreateQuestions(bankId: number, questions: CreateQuestionRequest[]): Promise<number> {
    return request.post<number>(`/api/questions/batch?bankId=${bankId}`, questions);
  },

  /**
   * 更新题目
   */
  async updateQuestion(id: number, data: Partial<CreateQuestionRequest>): Promise<QuestionDTO> {
    return request.put<QuestionDTO>(`/api/questions/${id}`, data);
  },

  /**
   * 删除题目
   */
  async deleteQuestion(id: number): Promise<void> {
    return request.delete(`/api/questions/${id}`);
  },

  // ========== 题目导入 ==========

  /**
   * 从 Excel 文件导入题目
   */
  async importFromExcel(file: File, bankId: number): Promise<number> {
    const formData = new FormData();
    formData.append('file', file);
    return request.upload<number>(`/api/questions/import/excel?bankId=${bankId}`, formData);
  },

  /**
   * 从 Markdown 内容导入题目
   */
  async importFromMarkdown(content: string, bankId: number): Promise<number> {
    return request.post<number>(`/api/questions/import/markdown?bankId=${bankId}`, { content });
  },

  /**
   * 预览 Excel 导入内容（不保存）
   */
  async previewExcel(file: File): Promise<QuestionDTO[]> {
    const formData = new FormData();
    formData.append('file', file);
    return request.upload<QuestionDTO[]>('/api/questions/import/preview/excel', formData);
  },

  /**
   * 预览 Markdown 内容（不保存）
   */
  async previewMarkdown(content: string): Promise<QuestionDTO[]> {
    return request.post<QuestionDTO[]>('/api/questions/import/preview/markdown', { content });
  },
};
