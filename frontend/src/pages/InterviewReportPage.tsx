import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { perspectiveApi } from '../api/interviewerRole';
import type { ComprehensiveReportDTO, PerspectiveDetailDTO, PerspectiveScore } from '../types/interviewerRole';
import {
  Loader2,
  AlertCircle,
  RefreshCw,
  Star,
  ThumbsUp,
  ThumbsDown,
  MessageSquare,
  Award,
  Lightbulb,
  TrendingUp, MessagesSquare,
} from 'lucide-react';

interface InterviewReportPageProps {
  sessionId: string;
  onBack: () => void;
}

type TabType = 'comprehensive' | string;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-slate-100 text-slate-600',
  PROCESSING: 'bg-blue-100 text-blue-600',
  COMPLETED: 'bg-green-100 text-green-600',
  FAILED: 'bg-red-100 text-red-600',
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
  const allTabs: { id: TabType; label: string; icon?: string }[] = [
    { id: 'comprehensive', label: '综合报告', icon: '📊' },
    ...perspectives.map(p => ({
      id: String(p.perspectiveId),
      label: p.perspectiveName,
      icon: p.perspectiveIcon || '👤',
    })),
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center text-red-500">
          <AlertCircle className="w-8 h-8 mx-auto mb-2" />
          <p>{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* 页面标题 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
              <Award className="w-5 h-5 text-white" />
            </div>
            面试报告
          </h1>
          <p className="text-slate-500 mt-1">多视角评估综合报告</p>
        </div>
        <motion.button
          onClick={onBack}
          className="px-5 py-2.5 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-colors"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          返回
        </motion.button>
      </div>

      {/* Tab 导航 */}
      <div className="bg-white rounded-2xl shadow-sm mb-6 overflow-hidden">
        <div className="flex overflow-x-auto">
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
                  flex items-center gap-2 px-6 py-4 text-sm font-medium border-b-2 transition-colors whitespace-nowrap
                  ${isActive
                    ? 'border-primary-500 text-primary-600 bg-primary-50/50'
                    : 'border-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                  }
                `}
              >
                {tab.icon && <span>{tab.icon}</span>}
                <span>{tab.label}</span>
                {perspectiveOverview && (
                  <span className={`px-2 py-0.5 rounded-full text-xs ${STATUS_COLORS[perspectiveOverview.status as keyof typeof STATUS_COLORS]}`}>
                    {perspectiveOverview.status === 'PROCESSING' && <RefreshCw className="w-3 h-3 animate-spin inline" />}
                    {perspectiveOverview.status === 'COMPLETED' && perspectiveOverview.score !== null && (
                      <span>{perspectiveOverview.score}分</span>
                    )}
                    {perspectiveOverview.status === 'PENDING' && '等待中'}
                    {perspectiveOverview.status === 'FAILED' && '失败'}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Tab 内容 */}
      <AnimatePresence mode="wait">
        {activeTab === 'comprehensive' ? (
          <motion.div
            key="comprehensive"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
          >
            {comprehensiveReport ? (
              <ComprehensiveReport report={comprehensiveReport} />
            ) : (
              <div className="bg-white rounded-2xl shadow-sm p-12 text-center">
                <Loader2 className="w-8 h-8 text-primary-500 animate-spin mx-auto mb-4" />
                <p className="text-slate-500">正在生成综合报告...</p>
              </div>
            )}
          </motion.div>
        ) : (
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
          >
            {activePerspectiveDetail ? (
              <PerspectiveReport detail={activePerspectiveDetail} />
            ) : (
              <div className="bg-white rounded-2xl shadow-sm p-12 text-center">
                <Loader2 className="w-8 h-8 text-primary-500 animate-spin mx-auto mb-4" />
                <p className="text-slate-500">加载中...</p>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/**
 * 综合报告内容
 */
function ComprehensiveReport({ report }: { report: ComprehensiveReportDTO }) {
  return (
    <div className="space-y-6">
      {/* 综合得分卡片 */}
      <div className="bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl p-8 text-white">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-primary-100 text-sm font-medium mb-1">综合得分</p>
            <p className="text-5xl font-bold">{report.overallScore}</p>
            <p className="text-primary-200 text-sm mt-1">加权平均分</p>
          </div>
          <div className="text-right">
            <div className="w-20 h-20 rounded-full border-4 border-primary-300 flex items-center justify-center">
              <Star className="w-10 h-10 text-primary-200" />
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 各视角得分 */}
        <div className="lg:col-span-2 bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-primary-500" />
            各视角得分
          </h3>
          <div className="space-y-4">
            {report.perspectives.map((p) => (
              <div key={p.id} className="flex items-center gap-4">
                <div className="w-32 text-sm font-medium text-slate-700 flex flex-col gap-0.5">
                  <div className="flex items-center gap-2">
                    <span>{p.perspectiveIcon || '👤'}</span>
                    <span>{p.perspectiveName}</span>
                  </div>
                  {p.weight !== undefined && (
                    <span className="text-xs text-slate-400">权重 {((p.weight as number) * 100).toFixed(0)}%</span>
                  )}
                </div>
                <div className="flex-1 h-3 bg-slate-100 rounded-full overflow-hidden">
                  <motion.div
                    className="h-full bg-primary-500 rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${p.score ?? 0}%` }}
                    transition={{ duration: 0.8 }}
                  />
                </div>
                <div className="w-12 text-sm font-bold text-slate-800 text-right">
                  {p.score ?? '-'}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 汇总统计 */}
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4">评估统计</h3>
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                <ThumbsUp className="w-5 h-5 text-green-600" />
              </div>
              <div>
                <p className="text-sm text-slate-500">优势数量</p>
                <p className="font-bold text-slate-800">{report.strengths.length}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-amber-100 rounded-lg flex items-center justify-center">
                <ThumbsDown className="w-5 h-5 text-amber-600" />
              </div>
              <div>
                <p className="text-sm text-slate-500">改进建议</p>
                <p className="font-bold text-slate-800">{report.improvements.length}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center">
                <MessageSquare className="w-5 h-5 text-blue-600" />
              </div>
              <div>
                <p className="text-sm text-slate-500">视角数量</p>
                <p className="font-bold text-slate-800">{report.perspectives.length}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 综合评价（父容器） */}
      {(report.evaluation || report.developmentSuggestions) && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <MessageSquare className="w-5 h-5 text-primary-500" />
            综合评价
          </h3>
          {report.evaluation && (
            <div className="mb-4">
              <h4 className="text-base font-medium text-slate-800 mb-2 flex items-center gap-2">
                <MessagesSquare className="w-4 h-4 text-primary-500" />
                评价
              </h4>
              <div className="prose prose-slate max-w-none">
                <p className="text-slate-700 leading-relaxed whitespace-pre-wrap">
                  {report.evaluation}
                </p>
              </div>
            </div>
          )}
          {report.developmentSuggestions && (
            <div>
              <h4 className="text-base font-medium text-slate-800 mb-2 flex items-center gap-2">
                <Lightbulb className="w-4 h-4 text-primary-500" />
                发展建议
              </h4>
              <div className="prose prose-slate max-w-none">
                <p className="text-slate-700 leading-relaxed whitespace-pre-wrap">
                  {report.developmentSuggestions}
                </p>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 综合优势 */}
      {report.strengths.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <ThumbsUp className="w-5 h-5 text-green-500" />
            综合优势
          </h3>
          <ul className="space-y-2">
            {report.strengths.map((s, i) => (
              <li key={i} className="flex items-start gap-2 text-slate-700">
                <span className="w-2 h-2 bg-green-500 rounded-full mt-2 flex-shrink-0" />
                <span>{s}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 综合改进建议 */}
      {report.improvements.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <ThumbsDown className="w-5 h-5 text-amber-500" />
            改进建议
          </h3>
          <ul className="space-y-2">
            {report.improvements.map((s, i) => (
              <li key={i} className="flex items-start gap-2 text-slate-700">
                <span className="w-2 h-2 bg-amber-500 rounded-full mt-2 flex-shrink-0" />
                <span>{s}</span>
              </li>
            ))}
          </ul>
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
    if (score >= 80) return 'text-green-600';
    if (score >= 60) return 'text-amber-600';
    return 'text-red-600';
  };

  return (
    <div className="space-y-6">
      {/* 视角信息卡片 */}
      <div className="bg-white rounded-2xl shadow-sm p-6">
        <div className="flex items-center gap-6">
          <div className="w-16 h-16 bg-gradient-to-br from-purple-500 to-purple-600 rounded-2xl flex items-center justify-center text-3xl">
            {detail.perspectiveIcon || '👤'}
          </div>
          <div className="flex-1">
            <h2 className="text-2xl font-bold text-slate-900">{detail.roleName}</h2>
            <div className="flex items-center gap-4 mt-2">
              <span className={`text-2xl font-bold ${getScoreColor(detail.score)}`}>
                {detail.score}分
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 评价内容 */}
      {detail.feedback && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <MessageSquare className="w-5 h-5 text-primary-500" />
            评价
          </h3>
          <p className="text-slate-700 leading-relaxed whitespace-pre-wrap">
            {detail.feedback}
          </p>
        </div>
      )}

      {/* 优势 */}
      {detail.strengths.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <ThumbsUp className="w-5 h-5 text-green-500" />
            优势
          </h3>
          <ul className="space-y-2">
            {detail.strengths.map((s, i) => (
              <li key={i} className="flex items-start gap-2 text-slate-700">
                <span className="w-2 h-2 bg-green-500 rounded-full mt-2 flex-shrink-0" />
                <span>{s}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 改进建议 */}
      {detail.improvements.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <ThumbsDown className="w-5 h-5 text-amber-500" />
            改进建议
          </h3>
          <ul className="space-y-2">
            {detail.improvements.map((s, i) => (
              <li key={i} className="flex items-start gap-2 text-slate-700">
                <span className="w-2 h-2 bg-amber-500 rounded-full mt-2 flex-shrink-0" />
                <span>{s}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 问题详情列表 */}
      {detail.questionScores.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-primary-500" />
            问题详情
          </h3>
          <div className="space-y-6">
            {detail.questionScores.map((q) => (
              <div key={q.questionIndex} className="border border-slate-200 rounded-xl p-4">
                <div className="flex items-start justify-between gap-4 mb-3">
                  <div className="flex-1">
                    <span className="text-xs text-slate-500 mb-1 block">问题 {q.questionIndex + 1}</span>
                    <p className="font-medium text-slate-900">{q.question}</p>
                  </div>
                  <div className={`text-xl font-bold ${getScoreColor(q.score)}`}>
                    {q.score}分
                  </div>
                </div>
                {q.userAnswer && (
                  <div className="text-sm text-slate-700 bg-blue-50 rounded-lg p-3 mt-2 border border-blue-100">
                    <p className="text-xs text-blue-600 mb-1 font-medium">我的回答：</p>
                    <p className="whitespace-pre-wrap">{q.userAnswer}</p>
                  </div>
                )}
                {q.feedback && (
                  <div className="text-sm text-slate-700 bg-slate-50 rounded-lg p-3">
                    <p className="text-xs text-slate-500 mb-1">评价：</p>
                    <div className="prose prose-sm prose-slate max-w-none">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {q.feedback}
                      </ReactMarkdown>
                    </div>
                  </div>
                )}
                {q.referenceAnswer && (
                  <div className="text-sm text-slate-700 bg-green-50 rounded-lg p-3 mt-2 border border-green-100">
                    <p className="text-xs text-green-600 mb-1 font-medium">参考答案：</p>
                    <div className="prose prose-sm prose-green max-w-none">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {q.referenceAnswer}
                      </ReactMarkdown>
                    </div>
                  </div>
                )}
                {q.keyPoints && q.keyPoints.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs text-amber-600 mb-1 font-medium">关键要点：</p>
                    <div className="flex flex-wrap gap-1">
                      {q.keyPoints.map((point, idx) => (
                        <span key={idx} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 border border-amber-100">
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
