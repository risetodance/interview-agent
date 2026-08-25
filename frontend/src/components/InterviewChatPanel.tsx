import { useMemo, useRef } from 'react';
import { Virtuoso, type VirtuosoHandle } from 'react-virtuoso';
import type { CurrentQuestionDTO, DifficultyLevel, InterviewSession } from '../types/interview';
import { PROGRESS_LABELS, type ProgressStageKey } from '../api/interview';
import {
  Send,
  User,
  BookOpen,
  Loader2
} from 'lucide-react';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
  difficulty?: string;
  knowledgeBaseName?: string | null;
  isFollowUp?: boolean;
  relatedIndex?: number;
  relatedQuestion?: string;
  // 多视角支持
  createdByPerspectiveId?: number;
  createdByPerspectiveName?: string;
}

interface InterviewChatPanelProps {
  session: InterviewSession;
  currentQuestion: CurrentQuestionDTO | null;
  messages: Message[];
  answer: string;
  onAnswerChange: (answer: string) => void;
  onSubmit: () => void;
  onCompleteEarly: () => void;
  isSubmitting: boolean;
  isLoadingQuestion?: boolean;
  showCompleteConfirm: boolean;
  onShowCompleteConfirm: (show: boolean) => void;
  progressStage?: ProgressStageKey | null;
}

// 难度等级颜色映射
const difficultyColors: Record<DifficultyLevel, { bg: string; text: string; border: string }> = {
  BASIC: { bg: 'bg-emerald-50', text: 'text-emerald-700', border: 'border-emerald-200' },
  ADVANCED: { bg: 'bg-amber-50', text: 'text-amber-700', border: 'border-amber-200' },
  EXPERT: { bg: 'bg-red-50', text: 'text-red-700', border: 'border-red-200' }
};

// 难度等级标签映射
const difficultyLabels: Record<DifficultyLevel, string> = {
  BASIC: '基础',
  ADVANCED: '进阶',
  EXPERT: '专家'
};

/**
 * 面试聊天面板组件
 */
export default function InterviewChatPanel({
  session,
  currentQuestion,
  messages,
  answer,
  onAnswerChange,
  onSubmit,
  // onCompleteEarly, // 暂时未使用
  isSubmitting,
  isLoadingQuestion,
  // showCompleteConfirm, // 暂时未使用
  onShowCompleteConfirm,
  progressStage
}: InterviewChatPanelProps) {
  const virtuosoRef = useRef<VirtuosoHandle>(null);

  const progress = useMemo(() => {
    if (!session || !session.totalQuestions) return 0;
    // 优先使用 currentQuestionIndex，否则使用 currentQuestion.questionIndex
    const currentIndex = session.currentQuestionIndex ?? (currentQuestion ? currentQuestion.questionIndex : 0);
    return ((currentIndex + 1) / session.totalQuestions) * 100;
  }, [session, currentQuestion]);

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      onSubmit();
    }
  };

  return (
    <div className="flex-1 min-h-0 w-full max-w-4xl mx-auto flex flex-col">
      {/* 答题进度 */}
      <div className="bg-white border border-zinc-200 rounded-lg shrink-0">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
          <span className="text-sm font-medium text-zinc-900">答题进度</span>
          <span className="font-mono text-xs text-zinc-400 tabular-nums">
            题目 {session.currentQuestionIndex !== undefined ? session.currentQuestionIndex + 1 : (currentQuestion ? currentQuestion.questionIndex + 1 : 0)}{session.totalQuestions ? ` / ${session.totalQuestions}` : ''} · {Math.round(progress)}%
          </span>
        </div>
        <div className="px-5 py-3.5">
          <div className="h-1.5 bg-zinc-100 rounded-full overflow-hidden">
            <div className="h-full bg-primary-600 rounded-full" style={{ width: `${progress}%` }} />
          </div>
        </div>
      </div>

      {/* 聊天区域 */}
      <div className="mt-4 flex-1 min-h-0 bg-white border border-zinc-200 rounded-lg overflow-hidden flex flex-col">
        <Virtuoso
          ref={virtuosoRef}
          data={messages}
          initialTopMostItemIndex={messages.length - 1}
          followOutput="smooth"
          className="flex-1"
          itemContent={(_index, msg) => (
            <div className="pb-4 px-6 first:pt-6">
              <MessageBubble message={msg} />
            </div>
          )}
        />

        {/* 输入区域 */}
        <div className="border-t border-zinc-100 bg-zinc-50 p-4 shrink-0">
          <div className="flex gap-3">
            <textarea
              value={answer}
              onChange={(e) => onAnswerChange(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="输入你的回答... (Ctrl/Cmd + Enter 提交)"
              className="flex-1 px-3 py-2.5 border border-zinc-300 rounded-md bg-white text-sm text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors resize-none disabled:bg-zinc-100 disabled:text-zinc-400"
              rows={3}
              disabled={isSubmitting || isLoadingQuestion}
            />
            <div className="flex flex-col gap-2 justify-end">
              <button
                onClick={onSubmit}
                disabled={!answer.trim() || isSubmitting || isLoadingQuestion || !!progressStage}
                className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2 whitespace-nowrap"
              >
                {isSubmitting || isLoadingQuestion || progressStage ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    {progressStage ? PROGRESS_LABELS[progressStage] : (isLoadingQuestion ? '加载中...' : '提交中')}
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    提交
                  </>
                )}
              </button>
              <button
                onClick={() => onShowCompleteConfirm(true)}
                disabled={isSubmitting || isLoadingQuestion || !!progressStage}
                className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
              >
                提前交卷
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// 消息气泡组件
function MessageBubble({ message }: { message: Message }) {
  if (message.type === 'interviewer') {
    const difficulty = message.difficulty as DifficultyLevel | undefined;
    const difficultyStyle = difficulty ? difficultyColors[difficulty] : null;
    const isFollowUp = message.isFollowUp || false;

    return (
      <div className="flex items-start gap-3">
        <div className="w-8 h-8 rounded-md bg-primary-50 border border-primary-100 flex items-center justify-center shrink-0">
          <User className="w-4 h-4 text-primary-700" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 mb-1.5 flex-wrap">
            <span className="text-xs font-medium text-zinc-500">面试官</span>
            {/* 视角标签（始终显示） */}
            {message.createdByPerspectiveName && (
              <span className="text-xs border rounded px-1.5 py-0.5 bg-primary-50 border-primary-200 text-primary-700 font-medium">
                {message.createdByPerspectiveName}
              </span>
            )}
            {isFollowUp ? (
              <span className="text-xs border rounded px-1.5 py-0.5 bg-amber-50 border-amber-200 text-amber-700">
                追问 {message.relatedIndex ? `· 关于问题${message.relatedIndex + 1}` : ''}
              </span>
            ) : null}
            {/* 分类（追问时也显示） */}
            {message.category && (
              <span className="text-xs border rounded px-1.5 py-0.5 bg-zinc-50 border-zinc-200 text-zinc-600">
                {message.category}
              </span>
            )}
            {/* 难度（追问时也显示） */}
            {difficultyStyle && difficulty && (
              <span className={`text-xs border rounded px-1.5 py-0.5 ${difficultyStyle.bg} ${difficultyStyle.text} ${difficultyStyle.border}`}>
                {difficultyLabels[difficulty]}
              </span>
            )}
          </div>
          {message.knowledgeBaseName && !isFollowUp && (
            <div className="flex items-center gap-1 mb-1.5 text-xs text-zinc-400">
              <BookOpen className="w-3 h-3" />
              <span>{message.knowledgeBaseName}</span>
            </div>
          )}
          <div className="bg-white border border-zinc-200 rounded-md px-4 py-3 text-sm text-zinc-800 leading-relaxed whitespace-pre-wrap">
            {isFollowUp && message.relatedQuestion ? (
              <>
                <div className="text-xs text-amber-700 mb-2 pb-2 border-b border-amber-200">
                  {message.relatedIndex ? `关于问题${message.relatedIndex + 1}：` : ''}{message.relatedQuestion}
                </div>
                <div>{message.content}</div>
              </>
            ) : (
              message.content
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-start gap-3 justify-end">
      <div className="flex-1 max-w-[80%]">
        <div className="bg-primary-600 text-white rounded-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap">
          {message.content}
        </div>
      </div>
      <div className="w-8 h-8 rounded-md bg-zinc-100 border border-zinc-200 flex items-center justify-center shrink-0">
        <User className="w-4 h-4 text-zinc-500" />
      </div>
    </div>
  );
}
