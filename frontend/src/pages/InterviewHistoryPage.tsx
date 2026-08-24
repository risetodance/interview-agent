import { useEffect, useState, useCallback, useRef } from 'react';
import { historyApi, type InterviewItem, type EvaluateStatus } from '../api/history';
import { formatDate } from '../utils/date';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import { useToast } from '../components/Toast';
import {
  Search,
  Download,
  Trash2,
  ChevronRight,
  FileText,
  Loader2,
} from 'lucide-react';

interface InterviewHistoryPageProps {
  onBack: () => void;
  onViewInterview: (sessionId: string, resumeId?: number) => void;
}

interface InterviewWithResume extends InterviewItem {
  resumeId: number;
  resumeFilename: string;
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
}

interface InterviewStats {
  totalCount: number;
  completedCount: number;
  averageScore: number;
}

// 统计卡片组件（数字卡范式：mono 数字 + 单位小字）
function StatCard({
  label,
  value,
  unit,
}: {
  label: string;
  value: number | string;
  unit?: string;
}) {
  return (
    <div className="bg-white border border-zinc-200 rounded-lg px-5 py-4">
      <p className="text-xs text-zinc-500">{label}</p>
      <p className="mt-1.5 flex items-baseline gap-1">
        <span className="font-mono text-2xl font-semibold text-primary-800 tabular-nums">{value}</span>
        {unit && <span className="text-xs text-zinc-400">{unit}</span>}
      </p>
    </div>
  );
}

// 判断是否为已完成状态（包括 COMPLETED 和 EVALUATED）
function isCompletedStatus(status: string): boolean {
  return status === 'COMPLETED' || status === 'EVALUATED';
}

// 判断评估是否完成
function isEvaluateCompleted(interview: InterviewWithResume): boolean {
  // 如果 evaluateStatus 存在且为 COMPLETED，则评估已完成
  if (interview.evaluateStatus === 'COMPLETED') return true;
  // 向后兼容：如果 status 为 EVALUATED，也认为评估已完成
  if (interview.status === 'EVALUATED') return true;
  return false;
}

// 判断是否正在评估中
function isEvaluating(interview: InterviewWithResume): boolean {
  return interview.evaluateStatus === 'PENDING' || interview.evaluateStatus === 'PROCESSING';
}

// 判断评估是否失败
function isEvaluateFailed(interview: InterviewWithResume): boolean {
  return interview.evaluateStatus === 'FAILED';
}

// 状态文本
function getStatusText(interview: InterviewWithResume): string {
  // 评估失败
  if (isEvaluateFailed(interview)) {
    return '评估失败';
  }
  // 正在评估
  if (isEvaluating(interview)) {
    return interview.evaluateStatus === 'PROCESSING' ? '评估中' : '等待评估';
  }
  // 评估完成
  if (isEvaluateCompleted(interview)) {
    return '已完成';
  }
  // 面试进行中
  if (interview.status === 'IN_PROGRESS') {
    return '进行中';
  }
  // 面试已完成但评估未开始
  if (isCompletedStatus(interview.status)) {
    return '已提交';
  }
  return '已创建';
}

// 状态 chip 样式（语义色，克制使用）
function getStatusChipCls(interview: InterviewWithResume): string {
  if (isEvaluateFailed(interview)) {
    return 'text-red-700 bg-red-50 border-red-200';
  }
  if (isEvaluating(interview)) {
    return 'text-amber-700 bg-amber-50 border-amber-200';
  }
  if (isEvaluateCompleted(interview)) {
    return 'text-emerald-700 bg-emerald-50 border-emerald-200';
  }
  if (interview.status === 'IN_PROGRESS') {
    return 'text-amber-700 bg-amber-50 border-amber-200';
  }
  return 'text-zinc-500 bg-zinc-50 border-zinc-200';
}

// 获取分数条颜色
function getScoreColor(score: number): string {
  if (score >= 80) return 'bg-emerald-500';
  if (score >= 60) return 'bg-amber-500';
  return 'bg-red-500';
}

export default function InterviewHistoryPage({ onBack: _onBack, onViewInterview }: InterviewHistoryPageProps) {
  const toast = useToast();
  const [interviews, setInterviews] = useState<InterviewWithResume[]>([]);
  const [stats, setStats] = useState<InterviewStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const [deleteItem, setDeleteItem] = useState<InterviewWithResume | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);
  const pollingTimerRef = useRef<number | null>(null);
  const interviewsRef = useRef<InterviewWithResume[]>([]);

  // 比较两个面试列表是否有状态变化
  const hasStatusChanged = (oldList: InterviewWithResume[], newList: InterviewWithResume[]): boolean => {
    if (oldList.length !== newList.length) return true;
    const oldMap = new Map(oldList.map(i => [i.sessionId, i]));
    for (const newItem of newList) {
      const oldItem = oldMap.get(newItem.sessionId);
      if (!oldItem) return true;
      // 比较关键状态字段
      if (oldItem.status !== newItem.status || oldItem.evaluateStatus !== newItem.evaluateStatus || oldItem.overallScore !== newItem.overallScore) {
        return true;
      }
    }
    return false;
  };

  const loadAllInterviews = useCallback(async (isPolling = false) => {
    try {
      const resumes = await historyApi.getResumes();
      const allInterviews: InterviewWithResume[] = [];

      for (const resume of resumes) {
        const detail = await historyApi.getResumeDetail(resume.id);
        if (detail.interviews && detail.interviews.length > 0) {
          detail.interviews.forEach(interview => {
            allInterviews.push({
              ...interview,
              resumeId: resume.id,
              resumeFilename: resume.filename
            });
          });
        }
      }

      // 按创建时间倒序排序
      allInterviews.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );

      // 轮询时：只有状态变化了才更新
      if (isPolling) {
        if (hasStatusChanged(interviewsRef.current, allInterviews)) {
          interviewsRef.current = allInterviews;
          setInterviews(allInterviews);
          updateStats(allInterviews);
        }
      } else {
        setLoading(true);
        interviewsRef.current = allInterviews;
        setInterviews(allInterviews);
        updateStats(allInterviews);
        setLoading(false);
      }
    } catch (err) {
      console.error('加载面试记录失败', err);
    }
  }, []);

  const updateStats = (allInterviews: InterviewWithResume[]) => {
    const evaluated = allInterviews.filter(i => isEvaluateCompleted(i));
    const totalScore = evaluated.reduce((sum, i) => sum + (i.overallScore || 0), 0);
    setStats({
      totalCount: allInterviews.length,
      completedCount: evaluated.length,
      averageScore: evaluated.length > 0 ? Math.round(totalScore / evaluated.length) : 0,
    });
  };

  // 初始加载
  useEffect(() => {
    loadAllInterviews(false);
  }, []);

  // 轮询检查评估状态
  useEffect(() => {
    // 清除之前的轮询定时器
    if (pollingTimerRef.current) {
      clearInterval(pollingTimerRef.current);
      pollingTimerRef.current = null;
    }

    // 检查是否有正在评估的面试
    const hasEvaluating = interviews.some(i => isEvaluating(i));

    if (hasEvaluating) {
      // 启动轮询
      pollingTimerRef.current = window.setInterval(() => {
        loadAllInterviews(true);
      }, 3000);
    }

    return () => {
      if (pollingTimerRef.current) {
        clearInterval(pollingTimerRef.current);
        pollingTimerRef.current = null;
      }
    };
  }, [interviews, loadAllInterviews]);

  const handleDeleteClick = (interview: InterviewWithResume, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteItem(interview);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteItem) return;

    setDeletingSessionId(deleteItem.sessionId);
    try {
      await historyApi.deleteInterview(deleteItem.sessionId);
      await loadAllInterviews();
      toast.success('删除成功');
      setDeleteItem(null);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingSessionId(null);
    }
  };

  const handleExport = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExporting(sessionId);
    try {
      const blob = await historyApi.exportInterviewPdf(sessionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `面试报告_${sessionId.slice(-8)}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      toast.error('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  const filteredInterviews = interviews.filter(interview =>
    interview.resumeFilename.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-end justify-between gap-4 mb-6 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">面试记录</h1>
          <p className="mt-1 text-sm text-zinc-500">查看和管理所有模拟面试记录</p>
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400 pointer-events-none" />
          <input
            type="text"
            placeholder="搜索简历名称"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full h-9 pl-8 pr-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
          />
        </div>
      </div>

      {/* 统计概览 */}
      {stats && (
        <div className="grid grid-cols-3 gap-4 mb-6">
          <StatCard label="面试总数" value={stats.totalCount} unit="场" />
          <StatCard label="已完成" value={stats.completedCount} unit="场" />
          <StatCard label="平均分数" value={stats.averageScore} unit="分" />
        </div>
      )}

      {/* 加载状态 */}
      {loading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-6 h-6 text-zinc-400 animate-spin" />
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredInterviews.length === 0 && (
        <div className="bg-white border border-zinc-200 rounded-lg">
          <p className="px-5 py-8 text-xs text-zinc-400 text-center">
            暂无面试记录，开始一次模拟面试后记录将显示在这里
          </p>
        </div>
      )}

      {/* 表格 */}
      {!loading && filteredInterviews.length > 0 && (
        <div className="bg-white border border-zinc-200 rounded-lg overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-zinc-50 border-b border-zinc-100">
                <tr>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">关联简历</th>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">题目数</th>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">状态</th>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">得分</th>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">创建时间</th>
                  <th className="text-right px-5 py-3 text-xs font-medium text-zinc-500">操作</th>
                </tr>
              </thead>
              <tbody>
                {filteredInterviews.map((interview) => (
                  <tr
                    key={interview.sessionId}
                    onClick={() => onViewInterview(interview.sessionId, interview.resumeId)}
                    className="border-b border-zinc-100 last:border-b-0 hover:bg-zinc-50 cursor-pointer transition-colors group"
                  >
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <FileText className="w-4 h-4 text-zinc-400 shrink-0" />
                        <div className="min-w-0">
                          <p className="text-sm text-zinc-800 truncate max-w-[280px]">{interview.resumeFilename}</p>
                          <p className="font-mono text-xs text-zinc-400 mt-0.5">#{interview.sessionId.slice(-8)}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      <span className="font-mono text-sm text-zinc-700 tabular-nums">{interview.totalQuestions}</span>
                      <span className="ml-1 text-xs text-zinc-400">题</span>
                    </td>
                    <td className="px-5 py-3.5">
                      <span
                        className={`inline-flex items-center text-xs border rounded px-1.5 py-0.5 ${getStatusChipCls(interview)}`}
                      >
                        {getStatusText(interview)}
                      </span>
                    </td>
                    <td className="px-5 py-3.5">
                      {isEvaluateCompleted(interview) && interview.overallScore !== null ? (
                        <div className="flex items-center gap-2.5">
                          <div className="w-16 h-1.5 bg-zinc-100 rounded-full overflow-hidden">
                            <div
                              className={`h-full rounded-full ${getScoreColor(interview.overallScore)}`}
                              style={{ width: `${interview.overallScore}%` }}
                            />
                          </div>
                          <span className="font-mono text-sm font-medium text-zinc-800 tabular-nums">
                            {interview.overallScore}
                          </span>
                        </div>
                      ) : isEvaluating(interview) ? (
                        <span className="text-sm text-amber-700">生成中…</span>
                      ) : isEvaluateFailed(interview) ? (
                        <span className="text-sm text-red-700" title={interview.evaluateError}>失败</span>
                      ) : (
                        <span className="text-sm text-zinc-400">—</span>
                      )}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-xs text-zinc-500 tabular-nums whitespace-nowrap">
                      {formatDate(interview.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <div className="flex items-center justify-end gap-0.5">
                        {/* 导出按钮 */}
                        {isEvaluateCompleted(interview) && (
                          <button
                            onClick={(e) => handleExport(interview.sessionId, e)}
                            disabled={exporting === interview.sessionId}
                            className="p-2 text-zinc-400 hover:text-primary-700 hover:bg-primary-50 rounded-md transition-colors disabled:opacity-50"
                            title="导出 PDF"
                          >
                            {exporting === interview.sessionId ? (
                              <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                              <Download className="w-4 h-4" />
                            )}
                          </button>
                        )}
                        {/* 删除按钮 */}
                        <button
                          onClick={(e) => handleDeleteClick(interview, e)}
                          disabled={deletingSessionId === interview.sessionId}
                          className="p-2 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors disabled:opacity-50"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                        <ChevronRight className="w-4 h-4 text-zinc-300 group-hover:text-zinc-500 transition-colors" />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteItem !== null}
        item={deleteItem ? { id: deleteItem.id, sessionId: deleteItem.sessionId } : null}
        itemType="面试记录"
        loading={deletingSessionId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteItem(null)}
      />
    </div>
  );
}
