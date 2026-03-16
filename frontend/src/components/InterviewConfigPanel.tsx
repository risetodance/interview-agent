import {AnimatePresence, motion} from 'framer-motion';
import type {InterviewSession} from '../types/interview';

interface InterviewConfigPanelProps {
  questionCount: number;
  onQuestionCountChange: (count: number) => void;
  onStart: () => void;
  isCreating: boolean;
  checkingUnfinished: boolean;
  unfinishedSession: InterviewSession | null;
  onContinueUnfinished: () => void;
  onStartNew: () => void;
  resumeText: string;
  onBack: () => void;
  error?: string;
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
  error
}: InterviewConfigPanelProps) {
  const questionCounts = [6, 8, 10, 12, 15];

  return (
    <motion.div 
      className="max-w-2xl mx-auto"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="bg-white rounded-2xl p-8 shadow-sm">
        <h2 className="text-2xl font-bold text-slate-900 mb-6 flex items-center gap-3">
          <div className="w-10 h-10 bg-primary-100 rounded-xl flex items-center justify-center">
            <svg className="w-5 h-5 text-primary-600" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
              <circle cx="12" cy="12" r="6" stroke="currentColor" strokeWidth="2"/>
              <circle cx="12" cy="12" r="2" fill="currentColor"/>
            </svg>
          </div>
          面试配置
        </h2>
        
        {/* 未完成面试提示 */}
        <AnimatePresence>
          {checkingUnfinished && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-xl text-blue-700 text-sm text-center"
            >
              <div className="flex items-center justify-center gap-2">
                <motion.div 
                  className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full"
                  animate={{ rotate: 360 }}
                  transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                />
                正在检查是否有未完成的面试...
              </div>
            </motion.div>
          )}
          
          {unfinishedSession && !checkingUnfinished && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="mb-6 p-5 bg-gradient-to-r from-amber-50 to-orange-50 border-2 border-amber-200 rounded-xl"
            >
              <div className="flex items-start gap-3 mb-4">
                <div className="w-8 h-8 bg-amber-100 rounded-lg flex items-center justify-center flex-shrink-0">
                  <svg className="w-5 h-5 text-amber-600" viewBox="0 0 24 24" fill="none">
                    <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div className="flex-1">
                  <h3 className="font-semibold text-amber-900 mb-1">检测到未完成的模拟面试</h3>
                  <p className="text-sm text-amber-700">
                    已完成 {unfinishedSession.currentQuestionIndex} / {unfinishedSession.totalQuestions} 题
                  </p>
                </div>
              </div>
              <div className="flex gap-3">
                <motion.button
                  onClick={onContinueUnfinished}
                  className="flex-1 px-4 py-2.5 bg-amber-500 text-white rounded-lg font-medium hover:bg-amber-600 transition-colors"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  继续完成
                </motion.button>
                <motion.button
                  onClick={onStartNew}
                  className="flex-1 px-4 py-2.5 bg-white border border-amber-300 text-amber-700 rounded-lg font-medium hover:bg-amber-50 transition-colors"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  开始新的
                </motion.button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
        
        <div className="space-y-6">
          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-3">
              题目数量
            </label>
            <div className="grid grid-cols-5 gap-3">
              {questionCounts.map((count) => (
                <motion.button
                  key={count}
                  onClick={() => onQuestionCountChange(count)}
                  className={`px-4 py-3 rounded-xl font-medium transition-all ${
                    questionCount === count
                      ? 'bg-primary-500 text-white shadow-lg shadow-primary-500/30'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                >
                  {count}
                </motion.button>
              ))}
            </div>
          </div>
          
          <div className="mb-6">
            <label className="block text-sm font-semibold text-slate-600 mb-3">简历预览（前500字）</label>
            <textarea 
              value={resumeText.substring(0, 500) + (resumeText.length > 500 ? '...' : '')}
              readOnly
              className="w-full h-32 p-4 bg-slate-50 border border-slate-200 rounded-xl text-slate-600 text-sm resize-none"
            />
          </div>
          
          <p className="text-sm text-slate-500 mb-6">
            题目分布：项目经历(20%) + MySQL(20%) + Redis(20%) + Java基础/集合/并发(30%) + Spring(10%)
          </p>

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600 text-sm"
              >
                ⚠️ {error}
              </motion.div>
            )}
          </AnimatePresence>
          
          <div className="flex justify-center gap-4">
            <motion.button 
              onClick={onBack}
              className="px-6 py-3 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-all"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              ← 返回
            </motion.button>
            <motion.button
              onClick={onStart}
              disabled={isCreating}
              className="px-8 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
              whileHover={{ scale: isCreating ? 1 : 1.02, y: isCreating ? 0 : -1 }}
              whileTap={{ scale: isCreating ? 1 : 0.98 }}
            >
              {isCreating ? (
                <>
                  <motion.span 
                    className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full"
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                  />
                  正在生成题目...
                </>
              ) : (
                <>
                  开始面试 →
                </>
              )}
            </motion.button>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

