import { useMemo, useRef } from 'react';
import { motion } from 'framer-motion';
import { Virtuoso, type VirtuosoHandle } from 'react-virtuoso';
import type { CurrentQuestionDTO, DifficultyLevel, InterviewSession } from '../types/interview';
import {
  Send,
  User,
  BookOpen
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
  onShowCompleteConfirm
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
    <div className="flex flex-col h-[calc(100vh-200px)] max-w-4xl mx-auto">
      {/* 进度条 */}
      <div className="bg-white rounded-2xl p-6 mb-4 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-semibold text-slate-700">
            题目 {session.currentQuestionIndex !== undefined ? session.currentQuestionIndex + 1 : (currentQuestion ? currentQuestion.questionIndex + 1 : 0)}{session.totalQuestions ? ` / ${session.totalQuestions}` : ''}
          </span>
          <span className="text-sm text-slate-500">
            {Math.round(progress)}%
          </span>
        </div>
        <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
          <motion.div
            className="h-full bg-gradient-to-r from-primary-500 to-primary-600 rounded-full"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.3 }}
          />
        </div>
      </div>

      {/* 聊天区域 */}
      <div className="flex-1 bg-white rounded-2xl shadow-sm overflow-hidden flex flex-col min-h-0">
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
        <div className="border-t border-slate-200 p-4 bg-slate-50">
          <div className="flex gap-3">
            <textarea
              value={answer}
              onChange={(e) => onAnswerChange(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="输入你的回答... (Ctrl/Cmd + Enter 提交)"
              className="flex-1 px-4 py-3 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
              rows={3}
              disabled={isSubmitting || isLoadingQuestion}
            />
            <div className="flex flex-col gap-2">
              <motion.button
                onClick={onSubmit}
                disabled={!answer.trim() || isSubmitting || isLoadingQuestion}
                className="px-6 py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                whileHover={{ scale: isSubmitting || isLoadingQuestion || !answer.trim() ? 1 : 1.02 }}
                whileTap={{ scale: isSubmitting || isLoadingQuestion || !answer.trim() ? 1 : 0.98 }}
              >
                {isSubmitting || isLoadingQuestion ? (
                  <>
                    <motion.div
                      className="w-4 h-4 border-2 border-white border-t-transparent rounded-full"
                      animate={{ rotate: 360 }}
                      transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                    />
                    {isLoadingQuestion ? '加载中...' : '提交中'}
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    提交
                  </>
                )}
              </motion.button>
              <motion.button
                onClick={() => onShowCompleteConfirm(true)}
                disabled={isSubmitting || isLoadingQuestion}
                className="px-6 py-3 bg-slate-200 text-slate-700 rounded-xl font-medium hover:bg-slate-300 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                whileHover={{ scale: isSubmitting || isLoadingQuestion ? 1 : 1.02 }}
                whileTap={{ scale: isSubmitting || isLoadingQuestion ? 1 : 0.98 }}
              >
                提前交卷
              </motion.button>
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
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        className="flex items-start gap-3"
      >
        <div className="w-8 h-8 bg-primary-100 rounded-full flex items-center justify-center flex-shrink-0">
          <User className="w-4 h-4 text-primary-600" />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1 flex-wrap">
            <span className="text-sm font-semibold text-slate-700">面试官</span>
            {/* 视角标签（始终显示） */}
            {message.createdByPerspectiveName && (
              <span className="px-2 py-0.5 bg-purple-50 text-purple-600 text-xs rounded-full border border-purple-200 font-medium">
                [{message.createdByPerspectiveName}]
              </span>
            )}
            {isFollowUp ? (
              <span className="px-2 py-0.5 bg-amber-50 text-amber-600 text-xs rounded-full border border-amber-200">
                追问 {message.relatedIndex ? `· 关于问题${message.relatedIndex + 1}` : ''}
              </span>
            ) : null}
            {/* 分类（追问时也显示） */}
            {message.category && (
              <span className="px-2 py-0.5 bg-primary-50 text-primary-600 text-xs rounded-full">
                {message.category}
              </span>
            )}
            {/* 难度（追问时也显示） */}
            {difficultyStyle && difficulty && (
              <span className={`px-2 py-0.5 ${difficultyStyle.bg} ${difficultyStyle.text} text-xs rounded-full border ${difficultyStyle.border}`}>
                {difficultyLabels[difficulty]}
              </span>
            )}
          </div>
          {message.knowledgeBaseName && !isFollowUp && (
            <div className="flex items-center gap-1 mb-2 text-xs text-slate-500">
              <BookOpen className="w-3 h-3" />
              <span>{message.knowledgeBaseName}</span>
            </div>
          )}
          <div className="bg-slate-100 rounded-2xl rounded-tl-none p-4 text-slate-800 leading-relaxed">
            {isFollowUp && message.relatedQuestion ? (
              <>
                <div className="text-xs text-amber-600 mb-2 pb-2 border-b border-amber-200">
                  {message.relatedIndex ? `关于问题${message.relatedIndex + 1}：` : ''}{message.relatedQuestion}
                </div>
                <div>{message.content}</div>
              </>
            ) : (
              message.content
            )}
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="flex items-start gap-3 justify-end"
    >
      <div className="flex-1 max-w-[80%]">
        <div className="bg-primary-500 text-white rounded-2xl rounded-tr-none p-4 leading-relaxed">
          {message.content}
        </div>
      </div>
      <div className="w-8 h-8 bg-slate-200 rounded-full flex items-center justify-center flex-shrink-0">
        <svg className="w-4 h-4 text-slate-600" viewBox="0 0 24 24" fill="none">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="2" />
        </svg>
      </div>
    </motion.div>
  );
}

