import {calculatePercentage} from '../utils/score';

interface ScoreProgressBarProps {
  label: string;
  score: number;
  maxScore: number;
  color?: string;
  /** 兼容旧调用方的动画延迟参数（新版无入场动画，仅保留 props 接口） */
  delay?: number;
  className?: string;
}

/**
 * 分数进度条组件
 */
export default function ScoreProgressBar({
  label,
  score,
  maxScore,
  color = 'bg-primary-600',
  className = ''
}: ScoreProgressBarProps) {
  const percentage = calculatePercentage(score, maxScore);

  return (
    <div className={`bg-zinc-50 border border-zinc-100 rounded-md px-3 py-2.5 ${className}`}>
      <div className="text-xs text-zinc-500 mb-1.5">{label}</div>
      <div className="flex items-center gap-2.5">
        <div className="flex-1 h-1.5 bg-zinc-200 rounded-full overflow-hidden">
          <div
            className={`h-full ${color} rounded-full`}
            style={{ width: `${percentage}%` }}
          />
        </div>
        <span className="w-10 text-right font-mono text-xs text-zinc-600 tabular-nums shrink-0">
          {score}/{maxScore}
        </span>
      </div>
    </div>
  );
}
