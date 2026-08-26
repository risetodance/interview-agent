import {useMemo, useState} from 'react';
import {Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer} from 'recharts';
import {getScoreColor} from '../utils/score';
import type {CategoryScoreDTO, InterviewDetail} from '../api/history';

interface InterviewDetailPanelProps {
  interview: InterviewDetail;
}

/**
 * 面试详情面板组件
 */
export default function InterviewDetailPanel({ interview }: InterviewDetailPanelProps) {
  // 默认展开所有题目
  const [expandedQuestions, setExpandedQuestions] = useState<Set<number>>(() => {
    const allIndices = new Set<number>();
    if (interview.answers) {
      interview.answers.forEach((_, idx) => allIndices.add(idx));
    }
    return allIndices;
  });

  const toggleQuestion = (index: number) => {
    setExpandedQuestions(prev => {
      const newSet = new Set(prev);
      if (newSet.has(index)) {
        newSet.delete(index);
      } else {
        newSet.add(index);
      }
      return newSet;
    });
  };

  // 计算圆环进度
  const { scorePercent, circumference, strokeDashoffset } = useMemo(() => {
    const percent = interview.overallScore !== null ? (interview.overallScore / 100) * 100 : 0;
    const circ = 2 * Math.PI * 54; // r=54
    const offset = circ - (percent / 100) * circ;
    return { scorePercent: percent, circumference: circ, strokeDashoffset: offset };
  }, [interview.overallScore]);

  // 准备雷达图数据（后端返回 Map 格式）
  const radarData = useMemo(() => {
    if (!interview.categoryScores || Object.keys(interview.categoryScores).length === 0) {
      return null;
    }
    return Object.values(interview.categoryScores).map((cs: CategoryScoreDTO) => ({
      category: cs.category,
      score: cs.avgScore,
      fullMark: 100
    }));
  }, [interview.categoryScores]);

  return (
    <div className="space-y-5 fade-in">
      {/* 评分卡片 */}
      <ScoreCard
        score={interview.overallScore}
        feedback={interview.overallFeedback}
        scorePercent={scorePercent}
        circumference={circumference}
        strokeDashoffset={strokeDashoffset}
      />

      {/* 能力画像雷达图 */}
      {radarData && radarData.length > 0 && (
        <AbilityProfileSection radarData={radarData} />
      )}

      {/* 表现优势 */}
      {interview.strengths && interview.strengths.length > 0 && (
        <StrengthsSection strengths={interview.strengths} />
      )}

      {/* 改进建议 */}
      {interview.improvements && interview.improvements.length > 0 && (
        <ImprovementsSection improvements={interview.improvements} />
      )}

      {/* 问答记录详情 */}
      <QuestionsSection
        answers={interview.answers || []}
        expandedQuestions={expandedQuestions}
        toggleQuestion={toggleQuestion}
      />
    </div>
  );
}

// 能力画像雷达图组件
function AbilityProfileSection({ radarData }: { radarData: any[] }) {
  return (
    <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
      <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
        <h4 className="text-sm font-medium text-zinc-900">能力画像</h4>
        <span className="font-mono text-xs text-zinc-400">{radarData.length} 个维度</span>
      </div>
      <div className="p-5">
        <div className="h-80">
          <ResponsiveContainer
            width="100%"
            height="100%"
            initialDimension={{ width: 600, height: 320 }}
          >
            <RadarChart cx="50%" cy="50%" outerRadius="70%" data={radarData}>
              <PolarGrid stroke="#e4e4e7" />
              <PolarAngleAxis
                dataKey="category"
                tick={{ fill: '#52525b', fontSize: 12 }}
              />
              <PolarRadiusAxis
                angle={30}
                domain={[0, 100]}
                tick={{ fill: '#a1a1aa', fontSize: 10 }}
              />
              <Radar
                name="得分"
                dataKey="score"
                stroke="#276f8d"
                fill="#3589a5"
                fillOpacity={0.2}
                strokeWidth={2}
              />
            </RadarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}

// 评分卡片组件
function ScoreCard({
  score,
  feedback,
  // scorePercent, // 暂时未使用
  circumference,
  strokeDashoffset
}: {
  score: number | null;
  feedback: string | null;
  scorePercent: number;
  circumference: number;
  strokeDashoffset: number;
}) {
  return (
    <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
      <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
        <h4 className="text-sm font-medium text-zinc-900">面试评估</h4>
        <span className="font-mono text-xs text-zinc-400">满分 100</span>
      </div>
      <div className="p-6 flex flex-col items-center text-center">
        {/* 圆环进度条 */}
        <div className="relative w-32 h-32">
          <svg className="w-32 h-32 transform -rotate-90" viewBox="0 0 120 120">
            <circle
              cx="60"
              cy="60"
              r="54"
              stroke="#e4e4e7"
              strokeWidth="8"
              fill="none"
            />
            <circle
              cx="60"
              cy="60"
              r="54"
              stroke="#276f8d"
              strokeWidth="8"
              fill="none"
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={strokeDashoffset}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="font-mono text-3xl font-semibold text-zinc-900 tabular-nums">
              {score ?? '-'}
            </span>
            <span className="text-xs text-zinc-400 mt-0.5">总分</span>
          </div>
        </div>

        <p className="mt-5 text-sm text-zinc-600 max-w-2xl leading-relaxed">
          {feedback || '表现良好，展示了扎实的技术基础。'}
        </p>
      </div>
    </div>
  );
}

// 优势部分组件
function StrengthsSection({ strengths }: { strengths: string[] }) {
  return (
    <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
      <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
        <h4 className="text-sm font-medium text-zinc-900">表现优势</h4>
        <span className="font-mono text-xs text-zinc-400">{strengths.length} 项</span>
      </div>
      <ul className="p-5 space-y-2.5">
        {strengths.map((s: string, i: number) => (
          <li key={i} className="text-sm text-zinc-700 flex items-start gap-2.5">
            <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full mt-[7px] shrink-0"></span>
            <span>{s}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// 改进建议部分组件
function ImprovementsSection({ improvements }: { improvements: string[] }) {
  return (
    <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
      <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
        <h4 className="text-sm font-medium text-zinc-900">改进建议</h4>
        <span className="font-mono text-xs text-zinc-400">{improvements.length} 项</span>
      </div>
      <ul className="p-5 space-y-2.5">
        {improvements.map((s: string, i: number) => (
          <li key={i} className="text-sm text-zinc-700 flex items-start gap-2.5">
            <span className="w-1.5 h-1.5 bg-amber-500 rounded-full mt-[7px] shrink-0"></span>
            <span>{s}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// 问答部分组件
function QuestionsSection({
  answers,
  expandedQuestions,
  toggleQuestion
}: {
  answers: any[];
  expandedQuestions: Set<number>;
  toggleQuestion: (index: number) => void;
}) {
  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-medium text-zinc-900">问答记录详情</h4>
        <span className="font-mono text-xs text-zinc-400">{answers.length} 题</span>
      </div>

      <div className="space-y-3">
        {answers.map((answer, idx) => (
          <QuestionCard
            key={idx}
            answer={answer}
            isExpanded={expandedQuestions.has(idx)}
            onToggle={() => toggleQuestion(idx)}
          />
        ))}
      </div>
    </div>
  );
}

// 问题卡片组件
function QuestionCard({
  answer,
  isExpanded,
  onToggle
}: {
  answer: any;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="bg-white border border-zinc-200 rounded-lg shadow-sm overflow-hidden">
      {/* 问题头部 */}
      <div
        className="px-5 py-3.5 flex items-center justify-between gap-3 cursor-pointer hover:bg-zinc-50 transition-colors"
        onClick={onToggle}
      >
        <div className="flex items-center gap-2.5 min-w-0 flex-wrap">
          <span className="w-7 h-7 bg-zinc-100 text-zinc-600 rounded-md flex items-center justify-center font-mono text-xs font-semibold tabular-nums shrink-0">
            {answer.questionIndex + 1}
          </span>
          <span className="text-xs border rounded px-1.5 py-0.5 bg-primary-50 border-primary-200 text-primary-700">
            {answer.category || '综合'}
          </span>
          <span className={`text-xs font-medium rounded px-1.5 py-0.5 font-mono tabular-nums ${getScoreColor(answer.score, [80, 60])}`}>
            得分 {answer.score}
          </span>
        </div>
        <svg
          className={`w-4 h-4 text-zinc-400 shrink-0 ${isExpanded ? 'rotate-180' : ''}`}
          viewBox="0 0 24 24"
          fill="none"
        >
          <polyline points="6,9 12,15 18,9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
      </div>

      {/* 问题内容 */}
      <div className="px-5 pb-4">
        <p className="text-sm text-zinc-800 font-medium leading-relaxed">{answer.question}</p>
      </div>

      {/* 展开内容 */}
      {isExpanded && (
        <div className="px-5 pb-5 fade-in">
          <div className="space-y-3 border-t border-zinc-100 pt-4">
            {/* 你的回答 */}
            <div className="bg-zinc-50 border border-zinc-100 rounded-md p-4">
              <p className="text-xs text-zinc-400 mb-1.5">你的回答</p>
              <p className={`text-sm leading-relaxed ${
                !answer.userAnswer || answer.userAnswer === '不知道'
                  ? 'text-red-700 font-medium'
                  : 'text-zinc-700'
              }`}>
                "{answer.userAnswer || '(未回答)'}"
              </p>
            </div>

            {/* AI 深度评价 */}
            {answer.feedback && (
              <div>
                <p className="text-xs font-medium text-zinc-500 mb-1.5">AI 深度评价</p>
                <p className="text-sm text-zinc-700 leading-relaxed">{answer.feedback}</p>
              </div>
            )}

            {/* 参考答案 */}
            {answer.referenceAnswer && (
              <div className="bg-zinc-50 border border-zinc-100 rounded-md p-4">
                <p className="text-xs font-medium text-zinc-500 mb-2">参考答案</p>
                <div className="text-sm text-zinc-700 leading-relaxed whitespace-pre-line">{answer.referenceAnswer}</div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
