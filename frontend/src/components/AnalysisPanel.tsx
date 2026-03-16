import {useMemo} from 'react';
import {motion} from 'framer-motion';
import RadarChart from './RadarChart';
import ScoreProgressBar from './ScoreProgressBar';
import {formatDateTime} from '../utils/date';
import {
  AlertCircle,
  TrendingUp,
  Download,
  Target,
  CheckCircle2,
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

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case '高':
        return 'bg-red-50 border-red-200 text-red-700';
      case '中':
        return 'bg-amber-50 border-amber-200 text-amber-700';
      case '低':
        return 'bg-blue-50 border-blue-200 text-blue-700';
      default:
        return 'bg-slate-50 border-slate-200 text-slate-700';
    }
  };

  const getPriorityBadgeColor = (priority: string) => {
    switch (priority) {
      case '高':
        return 'bg-red-500 text-white';
      case '中':
        return 'bg-amber-500 text-white';
      case '低':
        return 'bg-blue-500 text-white';
      default:
        return 'bg-slate-500 text-white';
    }
  };

  const getCategoryColor = (category: string) => {
    const colors: Record<string, string> = {
      '项目': 'bg-purple-100 text-purple-700',
      '技能': 'bg-indigo-100 text-indigo-700',
      '内容': 'bg-emerald-100 text-emerald-700',
      '格式': 'bg-pink-100 text-pink-700',
      '结构': 'bg-cyan-100 text-cyan-700',
      '表达': 'bg-orange-100 text-orange-700'
    };
    return colors[category] || 'bg-slate-100 text-slate-700';
  };

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
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-6 bg-blue-100 rounded-full flex items-center justify-center">
          {isExplicitProcessing ? (
            <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
          ) : (
            <Clock className="w-8 h-8 text-yellow-500" />
          )}
        </div>
        <h3 className="text-xl font-semibold text-slate-700 mb-2">
          {isExplicitProcessing ? 'AI 正在分析中...' : '等待分析'}
        </h3>
        <p className="text-slate-500 mb-4">
          {isExplicitProcessing
            ? '请稍候，AI 正在对您的简历进行深度分析'
            : '简历已上传成功，即将开始 AI 分析'}
        </p>
        <p className="text-sm text-slate-400">页面将自动刷新显示分析结果</p>
      </div>
    );
  }

  // 处理分析失败状态
  // 1. 显式的 FAILED 状态
  // 2. 有分析结果但结果无效
  if (analyzeStatus === 'FAILED' || !isAnalysisValid) {
    return (
      <div className="bg-white rounded-2xl p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-6 bg-red-100 rounded-full flex items-center justify-center">
          <AlertCircle className="w-8 h-8 text-red-500" />
        </div>
        <h3 className="text-xl font-semibold text-slate-700 mb-2">分析失败</h3>
        <p className="text-slate-500 mb-4">AI 服务暂时不可用，请稍后重试</p>
        {(analyzeError || analysis?.summary) && (
          <div className="mt-4 p-4 bg-red-50 border border-red-200 rounded-lg text-left mb-4">
            <p className="text-sm text-red-600">{analyzeError || analysis.summary}</p>
          </div>
        )}
        {onReanalyze && (
          <motion.button
            onClick={onReanalyze}
            disabled={reanalyzing}
            className="px-6 py-2.5 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 transition-colors disabled:opacity-50 flex items-center gap-2 mx-auto"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            <RefreshCw className={`w-4 h-4 ${reanalyzing ? 'animate-spin' : ''}`} />
            {reanalyzing ? '重新分析中...' : '重新分析'}
          </motion.button>
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
    <div className="space-y-6">
      {/* 核心评价和雷达图 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 核心评价 */}
        <motion.div 
          className="bg-white rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2 text-slate-500">
              <TrendingUp className="w-5 h-5" />
              <span className="font-semibold">核心评价</span>
            </div>
            <motion.button
              onClick={onExport}
              disabled={exporting}
              className="px-4 py-2 border border-slate-200 bg-white rounded-lg text-slate-600 text-sm font-medium hover:bg-slate-50 transition-all disabled:opacity-50 flex items-center gap-2"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Download className="w-4 h-4" />
              {exporting ? '导出中...' : '导出分析报告'}
            </motion.button>
          </div>

          <div className="bg-gradient-to-br from-emerald-50 to-green-50 rounded-xl p-6">
            <p className="text-lg text-slate-800 leading-relaxed mb-6">
              {analysis.summary || '候选人具备扎实的技术基础，有大型项目架构经验。'}
            </p>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div className="bg-white rounded-xl p-5">
                <span className="text-sm font-semibold text-emerald-600 block mb-2">总分</span>
                <span className="text-4xl font-bold text-slate-900">{analysis.overallScore || 0}</span>
                <span className="text-sm text-slate-500">/ 100</span>
              </div>
              <div className="bg-white rounded-xl p-5">
                <span className="text-sm font-semibold text-emerald-600 block mb-2">分析时间</span>
                <span className="text-sm text-slate-700">
                  {formatDateTime(analysis.analyzedAt)}
                </span>
              </div>
            </div>

            {/* 优势标签 */}
            {analysis.strengths && analysis.strengths.length > 0 && (
              <div className="bg-white rounded-xl p-4">
                <span className="text-sm font-semibold text-emerald-600 block mb-3">优势亮点</span>
                <div className="flex flex-wrap gap-2">
                  {analysis.strengths.map((s: string, i: number) => (
                    <span key={i} className="px-3 py-1.5 bg-emerald-100 text-emerald-700 border border-emerald-200 rounded-lg text-sm font-medium">
                      {s}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </motion.div>

        {/* 多维度评分雷达图 */}
        <motion.div 
          className="bg-white rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <div className="flex items-center gap-2 text-slate-500 mb-6">
            <Target className="w-5 h-5" />
            <span className="font-semibold">多维度评分</span>
          </div>

          <RadarChart data={radarData} height={320} />

          {/* 维度得分详情 */}
          <div className="mt-4 grid grid-cols-2 gap-3">
            <ScoreProgressBar
              label="项目经验"
              score={projectScore}
              maxScore={40}
              color="bg-purple-500"
              delay={0.3}
              className="col-span-2"
            />
            <ScoreProgressBar
              label="技能匹配"
              score={skillMatchScore}
              maxScore={20}
              color="bg-blue-500"
              delay={0.4}
            />
            <ScoreProgressBar
              label="内容完整性"
              score={contentScore}
              maxScore={15}
              color="bg-emerald-500"
              delay={0.5}
            />
            <ScoreProgressBar
              label="结构清晰度"
              score={structureScore}
              maxScore={15}
              color="bg-cyan-500"
              delay={0.6}
            />
            <ScoreProgressBar
              label="表达专业性"
              score={expressionScore}
              maxScore={10}
              color="bg-orange-500"
              delay={0.7}
            />
          </div>
        </motion.div>
      </div>

      {/* 改进建议 - 按优先级分类 */}
      <motion.div 
        className="bg-white rounded-2xl p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <div className="flex items-center gap-2 text-slate-500 mb-6">
          <CheckCircle2 className="w-5 h-5" />
          <span className="font-semibold">改进建议</span>
          <span className="text-sm text-slate-400">
            ({analysis.suggestions?.length || 0} 条)
          </span>
        </div>

        <div className="space-y-6">
          {/* 高优先级 */}
          {suggestionsByPriority.high.length > 0 && (
            <SuggestionSection
              priority="高"
              suggestions={suggestionsByPriority.high}
              getPriorityColor={getPriorityColor}
              getPriorityBadgeColor={getPriorityBadgeColor}
              getCategoryColor={getCategoryColor}
              delay={0.4}
            />
          )}

          {/* 中优先级 */}
          {suggestionsByPriority.medium.length > 0 && (
            <SuggestionSection
              priority="中"
              suggestions={suggestionsByPriority.medium}
              getPriorityColor={getPriorityColor}
              getPriorityBadgeColor={getPriorityBadgeColor}
              getCategoryColor={getCategoryColor}
              delay={0.5}
            />
          )}

          {/* 低优先级 */}
          {suggestionsByPriority.low.length > 0 && (
            <SuggestionSection
              priority="低"
              suggestions={suggestionsByPriority.low}
              getPriorityColor={getPriorityColor}
              getPriorityBadgeColor={getPriorityBadgeColor}
              getCategoryColor={getCategoryColor}
              delay={0.6}
            />
          )}

          {analysis.suggestions?.length === 0 && (
            <div className="text-center py-8 text-slate-500">暂无改进建议</div>
          )}
        </div>
      </motion.div>
    </div>
  );
}

// 建议分组组件
function SuggestionSection({
  priority,
  suggestions,
  getPriorityColor,
  getPriorityBadgeColor,
  getCategoryColor,
  delay
}: {
  priority: string;
  suggestions: any[];
  getPriorityColor: (p: string) => string;
  getPriorityBadgeColor: (p: string) => string;
  getCategoryColor: (c: string) => string;
  delay: number;
}) {
  const priorityColors: Record<string, { bg: string; text: string; border: string }> = {
    '高': { bg: 'bg-red-100', text: 'text-red-700', border: 'bg-red-100' },
    '中': { bg: 'bg-amber-100', text: 'text-amber-700', border: 'bg-amber-100' },
    '低': { bg: 'bg-blue-100', text: 'text-blue-700', border: 'bg-blue-100' }
  };

  const colors = priorityColors[priority] || priorityColors['中'];

  return (
    <div>
      <div className="flex items-center gap-2 mb-4">
        <span className={`px-3 py-1 ${colors.bg} ${colors.text} rounded-full text-sm font-semibold`}>
          {priority}优先级 ({suggestions.length})
        </span>
        <div className={`flex-1 h-px ${colors.border}`}></div>
      </div>
      <div className="space-y-3">
        {suggestions.map((s: any, i: number) => (
          <motion.div 
            key={`${priority}-${i}`}
            className={`p-4 rounded-xl border-2 ${getPriorityColor(priority)}`}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: delay + i * 0.1 }}
          >
            <div className="flex items-start gap-3 mb-2">
              <span className={`px-2 py-0.5 rounded text-xs font-semibold ${getPriorityBadgeColor(priority)}`}>
                {priority}
              </span>
              <span className={`px-2 py-0.5 rounded text-xs font-medium ${getCategoryColor(s.category || '其他')}`}>
                {s.category || '其他'}
              </span>
            </div>
            <div className="mb-2">
              <p className="font-semibold text-slate-900 mb-1">{s.issue || '问题描述'}</p>
              <p className="text-sm leading-relaxed">{s.recommendation || s}</p>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

