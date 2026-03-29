import { request } from './request';
import type {
  InterviewerRole,
  CreateRoleRequest,
  ComprehensiveReportDTO,
  PerspectiveScore,
  PerspectiveDetailDTO,
} from '../types/interviewerRole';

/**
 * 面试官角色管理 API
 */
export const interviewerRoleApi = {
  /**
   * 获取所有角色列表
   * GET /api/admin/interviewer-roles
   */
  async getRoles(): Promise<InterviewerRole[]> {
    return request.get<InterviewerRole[]>('/api/admin/interviewer-roles');
  },

  /**
   * 获取单个角色详情
   * GET /api/admin/interviewer-roles/{id}
   */
  async getRole(id: number): Promise<InterviewerRole> {
    return request.get<InterviewerRole>(`/api/admin/interviewer-roles/${id}`);
  },

  /**
   * 创建角色
   * POST /api/admin/interviewer-roles
   */
  async createRole(data: CreateRoleRequest): Promise<InterviewerRole> {
    return request.post<InterviewerRole>('/api/admin/interviewer-roles', data);
  },

  /**
   * 更新角色
   * PUT /api/admin/interviewer-roles/{id}
   */
  async updateRole(id: number, data: Partial<CreateRoleRequest>): Promise<InterviewerRole> {
    return request.put<InterviewerRole>(`/api/admin/interviewer-roles/${id}`, data);
  },

  /**
   * 删除角色
   * DELETE /api/admin/interviewer-roles/{id}
   */
  async deleteRole(id: number): Promise<void> {
    return request.delete<void>(`/api/admin/interviewer-roles/${id}`);
  },

  /**
   * 更新角色权重
   * PUT /api/admin/interviewer-roles/{id}/weight
   */
  async updateWeight(id: number, data: { weight: number }): Promise<InterviewerRole> {
    return request.put<InterviewerRole>(`/api/admin/interviewer-roles/${id}/weight`, data);
  },
};

/**
 * 面试视角流程 API
 */
export const perspectiveApi = {
  /**
   * 获取各视角评分状态和结果
   * GET /api/interview/sessions/{sessionId}/perspectives
   */
  async getPerspectiveScores(sessionId: string): Promise<PerspectiveScore[]> {
    return request.get<PerspectiveScore[]>(
      `/api/interview/sessions/${sessionId}/perspectives`
    );
  },

  /**
   * 获取指定视角详情
   * GET /api/interview/sessions/{sessionId}/perspectives/{perspectiveId}
   */
  async getPerspectiveDetail(
    sessionId: string,
    perspectiveId: number
  ): Promise<PerspectiveDetailDTO> {
    return request.get<PerspectiveDetailDTO>(
      `/api/interview/sessions/${sessionId}/perspectives/${perspectiveId}`
    );
  },

  /**
   * 获取综合报告
   * GET /api/interview/sessions/{sessionId}/report/comprehensive
   */
  async getComprehensiveReport(sessionId: string): Promise<ComprehensiveReportDTO> {
    return request.get<ComprehensiveReportDTO>(
      `/api/interview/sessions/${sessionId}/report/comprehensive`
    );
  },
};
