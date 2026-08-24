import { useState, useEffect, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { perspectiveApi } from '../api/interviewerRole';
import type { ComprehensiveReportDTO, PerspectiveDetailDTO, PerspectiveScore } from '../types/interviewerRole';
import {
  Loader2,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';

interface InterviewReportPageProps {
  sessionId: string;
  onBack: () => void;
}

type TabType = 'comprehensive' | string;

// 状态 chip 样式（语义色，克制使用）
const STATUS_CHIP_CLS: Record<string, string> = {
  PENDING: 'text-zinc-500 bg-zinc-50 border-zinc-200',
  PROCESSING: 'text-amber-700 bg-amber-50 border-amber-200',
  COMPLETED: 'text-emerald-700 bg-emerald-50 border-emerald-200',
  FAILED: 'text-red-700 bg-red-50 border-red-200',
};

/**
 * 面试综合报告页面 - 多 Tab 布局
 */
export default function InterviewReportPage({ sessionId, onBack }: InterviewReportPageProps) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 综合报告数据
  const [comprehensiveReport, setComprehensiveReport] = useState<ComprehensiveReportDTO | null>(null);
  const [perspectives, setPerspectives] = useState<PerspectiveScore[]>([]);

  // 当前 Tab
  const [activeTab, setActiveTab] = useState<TabType>('comprehensive');
  const [activePerspectiveDetail, setActivePerspectiveDetail] = useState<PerspectiveDetailDTO | null>(null);

  // 加载视角概览
  const loadPerspectives = useCallback(async () => {
    try {
      const data = await perspectiveApi.getPerspectiveScores(sessionId);
      setPerspectives(data);
    } catch (err) {
      console.error('加载视角概览失败:', err);
    }
  }, [sessionId]);

  // 加载综合报告
  const loadComprehensiveReport = useCallback(async () => {
    try {
      const data = await perspectiveApi.getComprehensiveReport(sessionId);
      setComprehensiveReport(data);
    } catch (err) {
      console.error('加载综合报告失败:', err);
    }
  }, [sessionId]);

  // 加载视角详情
  const loadPerspectiveDetail = useCallback(async (perspectiveId: number) => {
    try {
      const data = await perspectiveApi.getPerspectiveDetail(sessionId, perspectiveId);
      setActivePerspectiveDetail(data);
    } catch (err) {
      console.error('加载视角详情失败:', err);
    }
  }, [sessionId]);

  // 初始化加载
  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        await Promise.all([
          loadPerspectives(),
          loadComprehensiveReport(),
        ]);
      } catch (err) {
        console.error('加载报告失败:', err);
        setError('加载报告失败');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [loadPerspectives, loadComprehensiveReport]);

  // 切换 Tab 时加载视角详情
  useEffect(() => {
    if (activeTab !== 'comprehensive') {
      const perspectiveId = parseInt(activeTab, 10);
      if (!isNaN(perspectiveId)) {
        loadPerspectiveDetail(perspectiveId);
      }
    }
  }, [activeTab, loadPerspectiveDetail]);

  // 轮询检查状态
  useEffect(() => {
    const hasProcessing = perspectives.some(p => p.status === 'PROCESSING' || p.status === 'PENDING');
    if (!hasProcessing || loading) return;

    const timer = setInterval(() => {
      loadPerspectives();
      if (comprehensiveReport === null) {
        loadComprehensiveReport();
      }
    }, 5000);

    return () => clearInterval(timer);
  }, [perspectives, loading, loadPerspectives, loadComprehensiveReport, comprehensiveReport]);

  // 获取所有 Tab（综合报告 + 各视角）
  const allTabs: { id: TabType; label: string }[] = [
    { id: 'comprehensive', label: '综合报告' },
    ...perspectives.map(p => ({
      id: String(p.perspectiveId),
      label: p.perspectiveName,
    })),
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-6 h-6 text-zinc-400 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <AlertCircle className="w-6 h-6 text-red-500 mx-auto mb-2" />
          <p className="text-sm text-red-600">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-end justify-between gap-4 mb-6 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">面试报告</h1>
          <p className="mt-1 text-sm text-zinc-500">多视角评估综合报告</p>
        </div>
        <div className="flex items-center gap-3">
          <span className="font-mono text-xs text-zinc-400">#{sessionId.slice(-8)}</span>
          <button
            onClick={onBack}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
          >
            返回
          </button>
        </div>
      </div>

      {/* Tab 导航：底线式 */}
      <div className="flex border-b border-zinc-200 mb-6 overflow-x-auto scrollbar-none">
        {allTabs.map((tab) => {
          const isActive = activeTab === tab.id;
          const perspectiveOverview = tab.id !== 'comprehensive'
            ? perspectives.find(p => String(p.perspectiveId) === tab.id)
            : null;

          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`
                flex items-center gap-2 px-4 py-2.5 text-sm border-b-2 -mb-px transition-colors whitespace-nowrap
                ${isActive
                  ? 'border-primary-600 text-zinc-900 font-medium'
                  : 'border-transparent text-zinc-500 hover:text-zinc-800'
                }
              `}
            >
              <span>{tab.label}</span>
              {perspectiveOverview && (
                <span
                  className={`inline-flex items-center gap-1 text-xs border rounded px-1.5 py-0.5 ${
                    STATUS_CHIP_CLS[perspectiveOverview.status as keyof typeof STATUS_CHIP_CLS] ?? STATUS_CHIP_CLS.PENDING
                  }`}
                >
                  {perspectiveOverview.status === 'PROCESSING' && <RefreshCw className="w-3 h-3 animate-spin" />}
                  {perspectiveOverview.status === 'COMPLETED' && perspectiveOverview.score !== null && (
                    <span className="font-mono tabular-nums">{perspectiveOverview.score}分</span>
                  )}
                  {perspectiveOverview.status === 'PENDING' && '等待中'}
                  {perspectiveOverview.status === 'FAILED' && '失败'}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Tab 内容 */}
      {activeTab === 'comprehensive' ? (
        <div key="comprehensive" className="fade-in">
          {comprehensiveReport ? (
            <ComprehensiveReport report={comprehensiveReport} />
          ) : (
            <div className="bg-white border border-zinc-200 rounded-lg px-5 py-12 text-center">
              <Loader2 className="w-6 h-6 text-zinc-400 animate-spin mx-auto mb-3" />
              <p className="text-sm text-zinc-500">正在生成综合报告…</p>
            </div>
          )}
        </div>
      ) : (
        <div key={activeTab} className="fade-in">
          {activePerspectiveDetail ? (
            <PerspectiveReport detail={activePerspectiveDetail} />
          ) : (
            <div className="bg-white border border-zinc-200 rounded-lg px-5 py-12 text-center">
              <Loader2 className="w-6 h-6 text-zinc-400 animate-spin mx-auto mb-3" />
              <p className="text-sm text-zinc-500">加载中…</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// 统计卡片（数字卡范式：mono 数字 + 单位小字）
function StatCard({ label, value, unit }: { label: string; value: string | number; unit?: string }) {
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

// 卡片头（h-[46px] 细分割线范式）
function CardHeader({ title, meta }: { title: string; meta?: string }) {
  return (
    <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
      <h3 className="text-sm font-medium text-zinc-900">{title}</h3>
      {meta && <span className="font-mono text-xs text-zinc-400">{meta}</span>}
    </div>
  );
}

/**
 * 综合报告内容
 */
function ComprehensiveReport({ report }: { report: ComprehensiveReportDTO }) {
  return (
    <div className="space-y-5">
      {/* 统计概览 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="综合得分" value={report.overallScore} unit="分" />
        <StatCard label="视角数量" value={report.perspectives.length} unit="个" />
        <StatCard label="优势数量" value={report.strengths.length} unit="条" />
        <StatCard label="改进建议" value={report.improvements.length} unit="条" />
      </div>

      {/* 各视角得分 */}
      <div className="bg-white border border-zinc-200 rounded-lg">
        <CardHeader title="各视角得分" meta={`${report.perspectives.length} 个视角`} />
        <div className="p-5 space-y-4">
          {report.perspectives.map((p) => (
            <div key={p.id} className="flex items-center gap-4">
              <div className="w-28 shrink-0">
                <p className="text-sm text-zinc-800 truncate">{p.perspectiveName}</p>
                {p.weight !== undefined && (
                  <p className="font-mono text-xs text-zinc-400 mt-0.5">权重 {((p.weight as number) * 100).toFixed(0)}%</p>
                )}
              </div>
              <div className="flex-1 h-1.5 bg-zinc-100 rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary-500 rounded-full"
                  style={{ width: `${p.score ?? 0}%` }}
                />
              </div>
              <span className="w-10 shrink-0 text-right font-mono text-sm font-medium text-zinc-800 tabular-nums">
                {p.score ?? '—'}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* 综合评价 */}
      {(report.evaluation || report.developmentSuggestions) && (
        <div className="bg-white border border-zinc-200 rounded-lg">
          <CardHeader title="综合评价" />
          <div className="p-5 space-y-5">
            {report.evaluation && (
              <div>
                <h4 className="text-xs font-medium text-zinc-500 mb-1.5">评价</h4>
                <p className="text-sm text-zinc-700 leading-relaxed whitespace-pre-wrap">
                  {report.evaluation}
                </p>
              </div>
            )}
            {report.developmentSuggestions && (
              <div>
                <h4 className="text-xs font-medium text-zinc-500 mb-1.5">发展建议</h4>
                <p className="text-sm text-zinc-700 leading-relaxed whitespace-pre-wrap">
                  {report.developmentSuggestions}
                </p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 优势 / 改进建议 */}
      {(report.strengths.length > 0 || report.improvements.length > 0) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          {report.strengths.length > 0 && (
            <div className="bg-white border border-zinc-200 rounded-lg">
              <CardHeader title="综合优势" meta={`${report.strengths.length} 条`} />
              <ul className="p-5 space-y-2">
                {report.strengths.map((s, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-zinc-700">
                    <span className="w-1.5 h-1.5 bg-emerald-600 rounded-full mt-2 shrink-0" />
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {report.improvements.length > 0 && (
            <div className="bg-white border border-zinc-200 rounded-lg">
              <CardHeader title="改进建议" meta={`${report.improvements.length} 条`} />
              <ul className="p-5 space-y-2">
                {report.improvements.map((s, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-zinc-700">
                    <span className="w-1.5 h-1.5 bg-amber-600 rounded-full mt-2 shrink-0" />
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * 单个视角的报告内容
 */
function PerspectiveReport({ detail }: { detail: PerspectiveDetailDTO }) {
  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-emerald-700';
    if (score >= 60) return 'text-amber-700';
    return 'text-red-700';
  };

  return (
    <div className="space-y-5">
      {/* 视角信息 + 得分 */}
      <div className="bg-white border border-zinc-200 rounded-lg px-5 py-4 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="font-mono text-xs text-zinc-400">视角</p>
          <h2 className="text-base font-medium text-zinc-900 mt-0.5 truncate">{detail.roleName}</h2>
        </div>
        <p className="flex items-baseline gap-1 shrink-0">
          <span className={`font-mono text-2xl font-semibold tabular-nums ${getScoreColor(detail.score)}`}>
            {detail.score}
          </span>
          <span className="text-xs text-zinc-400">分</span>
        </p>
      </div>

      {/* 评价内容 */}
      {detail.feedback && (
        <div className="bg-white border border-zinc-200 rounded-lg">
          <CardHeader title="评价" />
          <div className="p-5">
            <p className="text-sm text-zinc-700 leading-relaxed whitespace-pre-wrap">
              {detail.feedback}
            </p>
          </div>
        </div>
      )}

      {/* 优势 / 改进建议 */}
      {(detail.strengths.length > 0 || detail.improvements.length > 0) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          {detail.strengths.length > 0 && (
            <div className="bg-white border border-zinc-200 rounded-lg">
              <CardHeader title="优势" meta={`${detail.strengths.length} 条`} />
              <ul className="p-5 space-y-2">
                {detail.strengths.map((s, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-zinc-700">
                    <span className="w-1.5 h-1.5 bg-emerald-600 rounded-full mt-2 shrink-0" />
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {detail.improvements.length > 0 && (
            <div className="bg-white border border-zinc-200 rounded-lg">
              <CardHeader title="改进建议" meta={`${detail.improvements.length} 条`} />
              <ul className="p-5 space-y-2">
                {detail.improvements.map((s, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-zinc-700">
                    <span className="w-1.5 h-1.5 bg-amber-600 rounded-full mt-2 shrink-0" />
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* 问题详情列表 */}
      {detail.questionScores.length > 0 && (
        <div className="bg-white border border-zinc-200 rounded-lg">
          <CardHeader title="问题详情" meta={`${detail.questionScores.length} 题`} />
          <div className="p-5 space-y-4">
            {detail.questionScores.map((q) => (
              <div key={q.questionIndex} className="border border-zinc-200 rounded-md p-4">
                <div className="flex items-start justify-between gap-4 mb-3">
                  <div className="flex-1 min-w-0">
                    <span className="font-mono text-xs text-zinc-400 mb-1 block">Q{q.questionIndex + 1}</span>
                    <p className="text-sm font-medium text-zinc-900">{q.question}</p>
                  </div>
                  <p className="flex items-baseline gap-0.5 shrink-0">
                    <span className={`font-mono text-lg font-semibold tabular-nums ${getScoreColor(q.score)}`}>
                      {q.score}
                    </span>
                    <span className="text-xs text-zinc-400">分</span>
                  </p>
                </div>
                {q.userAnswer && (
                  <div className="bg-zinc-50 border border-zinc-100 rounded-md p-3">
                    <p className="text-xs text-zinc-500 mb-1">我的回答</p>
                    <p className="text-sm text-zinc-700 whitespace-pre-wrap">{q.userAnswer}</p>
                  </div>
                )}
                {q.feedback && (
                  <div className="bg-zinc-50 border border-zinc-100 rounded-md p-3 mt-2">
                    <p className="text-xs text-zinc-500 mb-1">评价</p>
                    <div className="prose prose-sm prose-zinc max-w-none">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {q.feedback}
                      </ReactMarkdown>
                    </div>
                  </div>
                )}
                {q.referenceAnswer && (
                  <div className="bg-primary-50/60 border border-primary-100 rounded-md p-3 mt-2">
                    <p className="text-xs text-primary-700 mb-1">参考答案</p>
                    <div className="prose prose-sm prose-zinc max-w-none">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {q.referenceAnswer}
                      </ReactMarkdown>
                    </div>
                  </div>
                )}
                {q.keyPoints && q.keyPoints.length > 0 && (
                  <div className="mt-3">
                    <p className="text-xs text-zinc-500 mb-1.5">关键要点</p>
                    <div className="flex flex-wrap gap-1.5">
                      {q.keyPoints.map((point, idx) => (
                        <span
                          key={idx}
                          className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded px-1.5 py-0.5"
                        >
                          {point}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
