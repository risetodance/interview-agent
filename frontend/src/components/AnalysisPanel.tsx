import {useMemo} from 'react';
import RadarChart from './RadarChart';
import ScoreProgressBar from './ScoreProgressBar';
import {formatDateTime} from '../utils/date';
import {
  AlertCircle,
  Download,
  Loader2,
  Clock,
  RefreshCw,
} from 'lucide-react';
import type { AnalyzeStatus } from '../api/history';

interface AnalysisPanelProps {
  analysis: any;
  analyzeStatus?: AnalyzeStatus;
  analyzeError?: string;
  onExport: () => void;
  exporting: boolean;
  onReanalyze?: () => void;
  reanalyzing?: boolean;
}

/**
 * 简历分析面板组件
 */
export default function AnalysisPanel({
  analysis,
  analyzeStatus,
  analyzeError,
  onExport,
  exporting,
  onReanalyze,
  reanalyzing,
}: AnalysisPanelProps) {
  // 准备雷达图数据
  const radarData = useMemo(() => {
    if (!analysis) return [];

    const projectScore = analysis.projectScore || 0;
    const skillMatchScore = analysis.skillMatchScore || 0;
    const contentScore = analysis.contentScore || 0;
    const structureScore = analysis.structureScore || 0;
    const expressionScore = analysis.expressionScore || 0;

    const projectFullMark = 40;
    const skillMatchFullMark = 20;
    const contentFullMark = 15;
    const structureFullMark = 15;
    const expressionFullMark = 10;

    return [
      {
        subject: '表达专业性',
        score: expressionScore,
        fullMark: expressionFullMark
      },
      {
        subject: '技能匹配',
        score: skillMatchScore,
        fullMark: skillMatchFullMark
      },
      {
        subject: '内容完整性',
        score: contentScore,
        fullMark: contentFullMark
      },
      {
        subject: '结构清晰度',
        score: structureScore,
        fullMark: structureFullMark
      },
      {
        subject: '项目经验',
        score: projectScore,
        fullMark: projectFullMark
      }
    ];
  }, [analysis]);

  // 按优先级分类建议
  const suggestionsByPriority = useMemo(() => {
    if (!analysis?.suggestions) return { high: [], medium: [], low: [] };

    const suggestions = analysis.suggestions;
    return {
      high: suggestions.filter((s: any) => s.priority === '高'),
      medium: suggestions.filter((s: any) => s.priority === '中'),
      low: suggestions.filter((s: any) => s.priority === '低')
    };
  }, [analysis]);

  // 检测分析结果是否有效
  // 如果总分异常低（< 10）或 summary 包含明显的错误信息，视为无效
  const hasErrorKeywords = analysis?.summary && (
    analysis.summary.includes('I/O error') ||
    analysis.summary.includes('分析过程中出现错误') ||
    analysis.summary.includes('简历分析失败') ||
    analysis.summary.includes('Remote host terminated') ||
    analysis.summary.includes('handshake')
  );
  const isAnalysisValid = analysis &&
    analysis.overallScore >= 10 &&
    analysis.summary &&
    !hasErrorKeywords;

  // 判断是否为"分析中"状态
  // 1. 显式的 PENDING/PROCESSING 状态
  // 2. 状态未定义且没有分析结果（说明还在处理中）
  const isProcessing = analyzeStatus === 'PENDING' ||
    analyzeStatus === 'PROCESSING' ||
    (analyzeStatus === undefined && !analysis);

  // 处理分析中状态
  if (isProcessing) {
    const isExplicitProcessing = analyzeStatus === 'PROCESSING';
    return (
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm px-5 py-16 text-center fade-in">
        {isExplicitProcessing ? (
          <Loader2 className="w-8 h-8 text-primary-600 animate-spin mx-auto mb-4" />
        ) : (
          <Clock className="w-8 h-8 text-zinc-400 mx-auto mb-4" />
        )}
        <h3 className="text-sm font-medium text-zinc-900">
          {isExplicitProcessing ? 'AI 正在分析中...' : '等待分析'}
        </h3>
        <p className="mt-1.5 text-sm text-zinc-500">
          {isExplicitProcessing
            ? '请稍候，AI 正在对您的简历进行深度分析'
            : '简历已上传成功，即将开始 AI 分析'}
        </p>
        <p className="mt-1 text-xs text-zinc-400">页面将自动刷新显示分析结果</p>
      </div>
    );
  }

  // 处理分析失败状态
  // 1. 显式的 FAILED 状态
  // 2. 有分析结果但结果无效
  if (analyzeStatus === 'FAILED' || !isAnalysisValid) {
    return (
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm px-5 py-16 text-center fade-in">
        <AlertCircle className="w-8 h-8 text-red-500 mx-auto mb-4" />
        <h3 className="text-sm font-medium text-zinc-900">分析失败</h3>
        <p className="mt-1.5 text-sm text-zinc-500 mb-4">AI 服务暂时不可用，请稍后重试</p>
        {(analyzeError || analysis?.summary) && (
          <div className="mt-4 mb-5 mx-auto max-w-xl border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-left">
            <p className="text-sm text-red-700 break-all">{analyzeError || analysis.summary}</p>
          </div>
        )}
        {onReanalyze && (
          <button
            onClick={onReanalyze}
            disabled={reanalyzing}
            className="h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-2 mx-auto"
          >
            <RefreshCw className={`w-4 h-4 ${reanalyzing ? 'animate-spin' : ''}`} />
            {reanalyzing ? '重新分析中...' : '重新分析'}
          </button>
        )}
      </div>
    );
  }

  const projectScore = analysis.projectScore || 0;
  const skillMatchScore = analysis.skillMatchScore || 0;
  const contentScore = analysis.contentScore || 0;
  const structureScore = analysis.structureScore || 0;
  const expressionScore = analysis.expressionScore || 0;

  return (
    <div className="space-y-5 fade-in">
      {/* 核心评价和雷达图 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* 核心评价 */}
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h3 className="text-sm font-medium text-zinc-900">核心评价</h3>
            <button
              onClick={onExport}
              disabled={exporting}
              className="h-9 px-3.5 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" />
              {exporting ? '导出中...' : '导出 PDF'}
            </button>
          </div>

          <div className="p-5">
            <p className="text-[15px] text-zinc-700 leading-relaxed">
              {analysis.summary || '候选人具备扎实的技术基础，有大型项目架构经验。'}
            </p>

            <div className="mt-5 pt-4 border-t border-zinc-100 flex items-end justify-between gap-4 flex-wrap">
              <div>
                <p className="text-xs text-zinc-500">总分</p>
                <p className="mt-1 flex items-baseline gap-1">
                  <span className="font-mono text-2xl font-semibold text-primary-800 tabular-nums">
                    {analysis.overallScore || 0}
                  </span>
                  <span className="text-xs text-zinc-400">/ 100</span>
                </p>
              </div>
              <div>
                <p className="text-xs text-zinc-500">分析时间</p>
                <p className="mt-1.5 font-mono text-xs text-zinc-600 tabular-nums">
                  {formatDateTime(analysis.analyzedAt)}
                </p>
              </div>
            </div>

            {/* 优势标签 */}
            {analysis.strengths && analysis.strengths.length > 0 && (
              <div className="mt-5 pt-4 border-t border-zinc-100">
                <p className="text-xs text-zinc-500 mb-2.5">优势亮点</p>
                <div className="flex flex-wrap gap-2">
                  {analysis.strengths.map((s: string, i: number) => (
                    <span
                      key={i}
                      className="text-xs border rounded px-1.5 py-0.5 text-emerald-700 bg-emerald-50 border-emerald-200"
                    >
                      {s}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 多维度评分雷达图 */}
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h3 className="text-sm font-medium text-zinc-900">多维度评分</h3>
            <span className="font-mono text-xs text-zinc-400">5 维度 · 满分 100</span>
          </div>

          <div className="p-5">
            <RadarChart data={radarData} height={300} />

            {/* 维度得分详情 */}
            <div className="mt-4 grid grid-cols-2 gap-3">
              <ScoreProgressBar
                label="项目经验"
                score={projectScore}
                maxScore={40}
                color="bg-primary-600"
                className="col-span-2"
              />
              <ScoreProgressBar
                label="技能匹配"
                score={skillMatchScore}
                maxScore={20}
                color="bg-primary-600"
              />
              <ScoreProgressBar
                label="内容完整性"
                score={contentScore}
                maxScore={15}
                color="bg-primary-600"
              />
              <ScoreProgressBar
                label="结构清晰度"
                score={structureScore}
                maxScore={15}
                color="bg-primary-600"
              />
              <ScoreProgressBar
                label="表达专业性"
                score={expressionScore}
                maxScore={10}
                color="bg-primary-600"
              />
            </div>
          </div>
        </div>
      </div>

      {/* 改进建议 - 按优先级分类 */}
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
          <h3 className="text-sm font-medium text-zinc-900">改进建议</h3>
          <span className="font-mono text-xs text-zinc-400 tabular-nums">
            {analysis.suggestions?.length || 0} 条
          </span>
        </div>

        <div className="p-5 space-y-6">
          {/* 高优先级 */}
          {suggestionsByPriority.high.length > 0 && (
            <SuggestionSection
              priority="高"
              suggestions={suggestionsByPriority.high}
            />
          )}

          {/* 中优先级 */}
          {suggestionsByPriority.medium.length > 0 && (
            <SuggestionSection
              priority="中"
              suggestions={suggestionsByPriority.medium}
            />
          )}

          {/* 低优先级 */}
          {suggestionsByPriority.low.length > 0 && (
            <SuggestionSection
              priority="低"
              suggestions={suggestionsByPriority.low}
            />
          )}

          {analysis.suggestions?.length === 0 && (
            <p className="text-xs text-zinc-400 text-center py-6">暂无改进建议</p>
          )}
        </div>
      </div>
    </div>
  );
}

// 建议分组组件
function SuggestionSection({
  priority,
  suggestions,
}: {
  priority: string;
  suggestions: any[];
}) {
  const chipCls: Record<string, string> = {
    '高': 'text-red-700 bg-red-50 border-red-200',
    '中': 'text-amber-700 bg-amber-50 border-amber-200',
    '低': 'text-zinc-600 bg-zinc-50 border-zinc-200',
  };

  // 分类标签配色：不同类别用不同语义色区分（避开 indigo/purple/sky）
  const categoryCls: Record<string, string> = {
    '项目': 'text-primary-700 bg-primary-50 border-primary-200',
    '技能': 'text-emerald-700 bg-emerald-50 border-emerald-200',
    '格式': 'text-amber-700 bg-amber-50 border-amber-200',
    '内容': 'text-rose-700 bg-rose-50 border-rose-200',
    '结构': 'text-teal-700 bg-teal-50 border-teal-200',
    '表达': 'text-orange-700 bg-orange-50 border-orange-200',
  };

  return (
    <div>
      <div className="flex items-center gap-3 mb-3">
        <span className={`text-xs border rounded px-1.5 py-0.5 font-medium ${chipCls[priority] || chipCls['低']}`}>
          {priority}优先级
        </span>
        <span className="font-mono text-xs text-zinc-400 tabular-nums">{suggestions.length} 条</span>
        <span className="flex-1 h-px bg-zinc-100" />
      </div>
      <div className="space-y-3">
        {suggestions.map((s: any, i: number) => (
          <div key={`${priority}-${i}`} className="border border-zinc-200 rounded-md p-4">
            <div className="flex items-center gap-2 mb-2">
              <span className={`text-xs border rounded px-1.5 py-0.5 font-medium ${categoryCls[s.category] || 'text-zinc-600 bg-zinc-50 border-zinc-200'}`}>
                {s.category || '其他'}
              </span>
            </div>
            <div>
              <p className="text-sm font-medium text-zinc-900 mb-1">{s.issue || '问题描述'}</p>
              <p className="text-sm leading-relaxed text-zinc-600">{s.recommendation || s}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
