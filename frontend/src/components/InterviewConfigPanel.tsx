import type {InterviewSession} from '../types/interview';
import type {InterviewerRole} from '../types/interviewerRole';
import PerspectiveSelector from './interview/PerspectiveSelector';
import {Loader2, ArrowLeft, AlertCircle} from 'lucide-react';
import {useState} from 'react';

interface InterviewConfigPanelProps {
  questionCount: number;
  onQuestionCountChange: (count: number) => void;
  onStart: () => void;
  isCreating: boolean;
  checkingUnfinished: boolean;
  unfinishedSession: InterviewSession | null;
  onContinueUnfinished: (sessionId: string, resumeId?: number) => void;
  onStartNew: () => void;
  resumeText: string;
  onBack: () => void;
  error?: string;
  // 多视角支持
  availableRoles?: InterviewerRole[];
  selectedPerspectives?: number[];
  onPerspectivesChange?: (ids: number[]) => void;
  loadingRoles?: boolean;
  // 会话级权重配置
  perspectiveWeights?: Record<number, number>;
  onPerspectiveWeightsChange?: (weights: Record<number, number>) => void;
}

/**
 * 面试配置面板组件
 */
export default function InterviewConfigPanel({
  questionCount,
  onQuestionCountChange,
  onStart,
  isCreating,
  checkingUnfinished,
  unfinishedSession,
  onContinueUnfinished,
  onStartNew,
  resumeText,
  onBack,
  error,
  availableRoles = [],
  selectedPerspectives = [],
  onPerspectivesChange,
  loadingRoles = false,
  perspectiveWeights = {},
  onPerspectiveWeightsChange,
}: InterviewConfigPanelProps) {
  const questionCounts = [6, 8, 10, 12, 15];
  const [weightError, setWeightError] = useState<string | null>(null);

  return (
    <div className="bg-white border border-zinc-200 rounded-lg fade-in">
      {/* 卡头 */}
      <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
        <h2 className="text-sm font-medium text-zinc-900">面试配置</h2>
        <span className="font-mono text-xs text-zinc-400">视角 · 权重 · 题量</span>
      </div>

      <div className="p-5 space-y-6">
        {/* 未完成面试检查中 */}
        {checkingUnfinished && (
          <div className="flex items-center justify-center gap-2 border border-zinc-200 bg-zinc-50 rounded-md px-3 py-2.5 text-sm text-zinc-500 fade-in">
            <Loader2 className="w-4 h-4 animate-spin" />
            正在检查是否有未完成的面试...
          </div>
        )}

        {/* 未完成面试处理 */}
        {unfinishedSession && !checkingUnfinished && (
          <div className="border border-amber-200 bg-amber-50 rounded-md p-4 fade-in">
            <div className="mb-3">
              <h3 className="text-sm font-medium text-amber-900 mb-0.5">检测到未完成的模拟面试</h3>
              <p className="text-xs text-amber-700">
                已完成 {(unfinishedSession.answeredCount ?? unfinishedSession.currentQuestionIndex)} / {unfinishedSession.totalQuestions} 题
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => onContinueUnfinished(unfinishedSession.sessionId)}
                className="h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors"
              >
                继续完成
              </button>
              <button
                onClick={onStartNew}
                className="h-9 px-4 rounded-md border border-zinc-300 bg-white text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
              >
                开始新的
              </button>
            </div>
          </div>
        )}

        {/* 视角选择器 */}
        {onPerspectivesChange && (
          <div>
            {loadingRoles ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-4 h-4 text-zinc-400 animate-spin" />
                <span className="ml-2 text-sm text-zinc-500">加载面试官角色...</span>
              </div>
            ) : availableRoles.length > 0 ? (
              <PerspectiveSelector
                roles={availableRoles}
                selectedIds={selectedPerspectives}
                onChange={onPerspectivesChange}
                weights={perspectiveWeights}
                onWeightsChange={onPerspectiveWeightsChange}
                onWeightValidationChange={(isValid, totalWeight) => {
                  if (!isValid && selectedPerspectives.length > 0) {
                    setWeightError(`权重总和需为100%（当前${(totalWeight * 100).toFixed(0)}%），请调整后开始面试`);
                  } else {
                    setWeightError(null);
                  }
                }}
              />
            ) : (
              <p className="px-5 py-8 text-xs text-zinc-400 text-center border border-zinc-200 rounded-md">
                暂无可用面试官角色，请先在管理后台配置
              </p>
            )}
          </div>
        )}

        {/* 题目数量 */}
        <div>
          <label className="block text-xs font-medium text-zinc-600 mb-1.5">
            题目数量
          </label>
          <div className="grid grid-cols-5 gap-2">
            {questionCounts.map((count) => (
              <button
                key={count}
                onClick={() => onQuestionCountChange(count)}
                className={`h-9 rounded-md text-sm font-medium border transition-colors tabular-nums ${
                  questionCount === count
                    ? 'border-primary-600 bg-primary-50 text-primary-700'
                    : 'border-zinc-300 bg-white text-zinc-600 hover:border-zinc-400'
                }`}
              >
                {count}
              </button>
            ))}
          </div>
        </div>

        {/* 简历预览 */}
        <div>
          <label className="block text-xs font-medium text-zinc-600 mb-1.5">简历预览（前500字）</label>
          <textarea
            value={resumeText.substring(0, 500) + (resumeText.length > 500 ? '...' : '')}
            readOnly
            className="w-full h-32 p-3 bg-zinc-50 border border-zinc-200 rounded-md text-zinc-600 text-sm resize-none focus:outline-none"
          />
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="flex items-start gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700 fade-in">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span className="break-all">{error}</span>
          </div>
        )}

        {/* 权重校验提示 */}
        {weightError && (
          <div className="flex items-start gap-2 border border-amber-200 bg-amber-50 rounded-md px-3 py-2.5 text-sm text-amber-700 fade-in">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{weightError}</span>
          </div>
        )}

        {/* 操作按钮 */}
        <div className="flex justify-end gap-2">
          <button
            onClick={onBack}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors flex items-center gap-1.5"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            返回
          </button>
          <button
            onClick={onStart}
            disabled={isCreating || checkingUnfinished || !!weightError || selectedPerspectives.length === 0 || !!unfinishedSession}
            className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {isCreating ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                正在生成题目...
              </>
            ) : selectedPerspectives.length === 0 ? (
              '请选择面试官'
            ) : unfinishedSession ? (
              '请先处理未完成的面试选项'
            ) : (
              '开始面试'
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
