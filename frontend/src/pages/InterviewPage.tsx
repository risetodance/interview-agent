import {useEffect, useState, useRef} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {interviewApi} from '../api/interview';
import {interviewerRoleApi} from '../api/interviewerRole';
import ConfirmDialog from '../components/ConfirmDialog';
import InterviewConfigPanel from '../components/InterviewConfigPanel';
import InterviewChatPanel from '../components/InterviewChatPanel';
import type {CurrentQuestionDTO, InterviewSession} from '../types/interview';
import type {InterviewerRole} from '../types/interviewerRole';

type InterviewStage = 'config' | 'interview';

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

interface InterviewProps {
  resumeText: string;
  resumeId?: number;
  sessionId?: string; // 传入sessionId时，直接恢复该会话
  onBack: () => void;
  onInterviewComplete: () => void;
}

export default function Interview({ resumeText, resumeId, sessionId, onBack, onInterviewComplete }: InterviewProps) {
  const [stage, setStage] = useState<InterviewStage>('config');
  const [questionCount, setQuestionCount] = useState(8);
  const [session, setSession] = useState<InterviewSession | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<CurrentQuestionDTO | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [answer, setAnswer] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [checkingUnfinished, setCheckingUnfinished] = useState(false);
  const [unfinishedSession, setUnfinishedSession] = useState<InterviewSession | null>(null);
  const [showCompleteConfirm, setShowCompleteConfirm] = useState(false);
  const [forceCreateNew, setForceCreateNew] = useState(false);
  const [isLoadingQuestion, setIsLoadingQuestion] = useState(false);
  // 多视角支持
  const [availableRoles, setAvailableRoles] = useState<InterviewerRole[]>([]);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [selectedPerspectives, setSelectedPerspectives] = useState<number[]>([]);
  // 会话级权重配置
  const [perspectiveWeights, setPerspectiveWeights] = useState<Record<number, number>>({});

  // 使用 ref 防止重复请求
  const hasInitialized = useRef(false);

  // SSE 连接 ref
  const eventSourceRef = useRef<EventSource | null>(null);

  // 检查是否有未完成的面试（组件挂载时和resumeId变化时）
  useEffect(() => {
    // 防止重复初始化
    if (hasInitialized.current) {
      return;
    }
    hasInitialized.current = true;

    if (sessionId) {
      // 如果有sessionId，直接恢复该会话
      restoreSessionById(sessionId);
    } else if (resumeId) {
      checkUnfinishedSession();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resumeId, sessionId]);

  // 连接 SSE，当进入面试阶段时建立连接
  useEffect(() => {
    if (!session || stage !== 'interview') return;

    // 清理之前的连接
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    // 连接 SSE
    const cleanup = interviewApi.connectInterviewStream(session.sessionId, {
      onConnected: () => {
        console.log('SSE connected');
      },
      onQuestion: (question) => {
        setCurrentQuestion(question);
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: question.question,
          category: question.category,
          questionIndex: question.questionIndex,
          difficulty: question.difficulty,
          knowledgeBaseName: question.knowledgeBaseName,
          isFollowUp: question.isFollowUp,
          relatedIndex: question.relatedIndex,
          relatedQuestion: question.relatedQuestion,
          createdByPerspectiveId: question.createdByPerspectiveId,
          createdByPerspectiveName: question.createdByPerspectiveName,
        }]);
        setIsSubmitting(false);
      },
      onEvaluation: (evaluation) => {
        // 评估结果可以用于更新显示，但不阻塞流程
        console.log('Evaluation received:', evaluation);
      },
      onComplete: () => {
        onInterviewComplete();
      },
      onError: (errorMsg) => {
        setError(errorMsg);
        setIsSubmitting(false);
      },
    });

    eventSourceRef.current = cleanup as unknown as EventSource;

    return () => {
      cleanup();
    };
  }, [session, stage]);

  // 加载可用的面试官角色（仅在配置阶段）
  useEffect(() => {
    if (stage !== 'config') return;
    setLoadingRoles(true);
    interviewerRoleApi.getRoles()
      .then(roles => {
        setAvailableRoles(roles);
        // 自动选择已启用且为默认模板的角色
        const defaultRoleIds = roles
          .filter(r => r.status && r.defaultTemplate)
          .map(r => r.id);
        if (defaultRoleIds.length > 0) {
          const selectedDefaults = defaultRoleIds.slice(0, 3);
          setSelectedPerspectives(selectedDefaults);
          // 初始化默认视角的权重，归一化使其总和为1
          const defaultRoles = selectedDefaults
            .map(id => roles.find(r => r.id === id))
            .filter((r): r is NonNullable<typeof r> => r !== undefined);
          const totalDefaultWeight = defaultRoles.reduce((sum, r) => sum + r.weight, 0);
          const initWeights: Record<number, number> = {};
          defaultRoles.forEach(role => {
            initWeights[role.id] = totalDefaultWeight > 0 ? role.weight / totalDefaultWeight : 1 / defaultRoles.length;
          });
          setPerspectiveWeights(initWeights);
        }
      })
      .catch(err => {
        console.error('加载面试官角色失败', err);
      })
      .finally(() => {
        setLoadingRoles(false);
      });
  }, [stage]);

  // 根据sessionId恢复会话
  const restoreSessionById = async (sid: string) => {
    setCheckingUnfinished(true);
    try {
      // 调用 getSessionProgress 获取会话进度（包括历史记录和当前问题）
      const progress = await interviewApi.getSessionProgress(sid);

      // 构建消息历史
      const restoredMessages: Message[] = [];
      for (const historyItem of progress.history) {
        restoredMessages.push({
          type: 'interviewer',
          content: historyItem.question,
          category: historyItem.category,
          questionIndex: historyItem.questionIndex,
          difficulty: historyItem.difficulty,
          createdByPerspectiveId: historyItem.createdByPerspectiveId,
          createdByPerspectiveName: historyItem.createdByPerspectiveName,
          isFollowUp: historyItem.isFollowUp,
          relatedIndex: historyItem.relatedIndex,
          relatedQuestion: historyItem.relatedQuestion,
        });
        restoredMessages.push({
          type: 'user',
          content: historyItem.userAnswer
        });
      }

      // 添加当前问题
      if (progress.currentQuestion) {
        restoredMessages.push({
          type: 'interviewer',
          content: progress.currentQuestion.question,
          category: progress.currentQuestion.category,
          questionIndex: progress.currentQuestion.questionIndex,
          difficulty: progress.currentQuestion.difficulty,
          knowledgeBaseName: progress.currentQuestion.knowledgeBaseName,
          isFollowUp: progress.currentQuestion.isFollowUp,
          relatedIndex: progress.currentQuestion.relatedIndex,
          relatedQuestion: progress.currentQuestion.relatedQuestion,
          createdByPerspectiveId: progress.currentQuestion.createdByPerspectiveId,
          createdByPerspectiveName: progress.currentQuestion.createdByPerspectiveName,
        });
      }

      // 创建 session 对象
      const tempSession: InterviewSession = {
        sessionId: sid,
        resumeText: resumeText,
        totalQuestions: progress.totalQuestions,
        currentQuestionIndex: progress.currentQuestionIndex,
        questions: [],
        status: 'IN_PROGRESS'
      };

      setSession(tempSession);
      setCurrentQuestion(progress.currentQuestion);
      setMessages(restoredMessages);
      setStage('interview');
    } catch (err) {
      console.error('恢复会话失败', err);
      setError('恢复面试失败，请重新开始');
    } finally {
      setCheckingUnfinished(false);
    }
  };
  
  const checkUnfinishedSession = async () => {
    if (!resumeId) return;
    
    setCheckingUnfinished(true);
    try {
      const foundSession = await interviewApi.findUnfinishedSession(resumeId);
      if (foundSession) {
        setUnfinishedSession(foundSession);
      }
    } catch (err) {
      console.error('检查未完成面试失败', err);
    } finally {
      setCheckingUnfinished(false);
    }
  };

  const handleContinueUnfinished = async (sessionId: string) => {
    if (!unfinishedSession) return;
    // 直接调用 restoreSessionById 继续面试，不进行页面导航
    await restoreSessionById(sessionId);
    setUnfinishedSession(null);
  };
  
  const handleStartNew = () => {
    setUnfinishedSession(null);
    setForceCreateNew(true);  // 标记需要强制创建新会话
  };

  const startInterview = async () => {
    setIsCreating(true);
    setError('');

    try {
      // 创建新面试（如果 forceCreateNew 为 true，则强制创建新会话）
      const newSession = await interviewApi.createSession({
        resumeText,
        questionCount,
        resumeId,
        forceCreate: forceCreateNew,
        selectedPerspectives: selectedPerspectives.length > 0 ? selectedPerspectives : undefined,
        perspectiveWeights: Object.keys(perspectiveWeights).length > 0 ? perspectiveWeights : undefined,
      });

      // 重置强制创建标志
      setForceCreateNew(false);

      setSession(newSession);
      setStage('interview');

      // 调用 getCurrentQuestion 获取第一题，显示 loading
      setIsLoadingQuestion(true);
      try {
        const firstQuestion = await interviewApi.getCurrentQuestion(newSession.sessionId);
        setCurrentQuestion(firstQuestion);
        setMessages([{
          type: 'interviewer',
          content: firstQuestion.question,
          category: firstQuestion.category,
          questionIndex: firstQuestion.questionIndex,
          difficulty: firstQuestion.difficulty,
          knowledgeBaseName: firstQuestion.knowledgeBaseName,
          createdByPerspectiveId: firstQuestion.createdByPerspectiveId,
          createdByPerspectiveName: firstQuestion.createdByPerspectiveName,
        }]);
      } catch (questionErr) {
        console.error('获取第一题失败', questionErr);
        setError('获取面试问题失败，请重试');
        setStage('config');
      } finally {
        setIsLoadingQuestion(false);
      }
    } catch (err) {
      setError('创建面试失败，请重试');
      console.error(err);
      setForceCreateNew(false);  // 出错时也重置标志
    } finally {
      setIsCreating(false);
    }
  };
  
  const handleSubmitAnswer = async () => {
    if (!answer.trim() || !session || !currentQuestion) return;

    const submittedAnswer = answer.trim();
    setIsSubmitting(true);

    const userMessage: Message = {
      type: 'user',
      content: submittedAnswer
    };
    setMessages(prev => [...prev, userMessage]);
    setAnswer(''); // 先清空输入框

    try {
      // 调用新的 submitAnswer 接口（立即返回，后台通过 SSE 推送结果）
      await interviewApi.submitAnswer({
        sessionId: session.sessionId,
        questionIndex: currentQuestion.questionIndex,
        answer: submittedAnswer
      });
      // 等待 SSE 事件来更新 UI（nextQuestion 或 complete）
    } catch (err) {
      setError('提交答案失败，请重试');
      console.error(err);
      setIsSubmitting(false);
    }
  };

  const handleCompleteEarly = async () => {
    if (!session) return;

    setIsSubmitting(true);
    try {
      await interviewApi.completeInterview(session.sessionId);
      setShowCompleteConfirm(false);
      // 面试已完成，评估将在后台进行，跳转到面试记录页
      onInterviewComplete();
    } catch (err) {
      setError('提前交卷失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };
  
  // 配置界面
  const renderConfig = () => {
    return (
      <InterviewConfigPanel
        questionCount={questionCount}
        onQuestionCountChange={setQuestionCount}
        onStart={startInterview}
        isCreating={isCreating}
        checkingUnfinished={checkingUnfinished}
        unfinishedSession={unfinishedSession}
        onContinueUnfinished={handleContinueUnfinished}
        onStartNew={handleStartNew}
        resumeText={resumeText}
        onBack={onBack}
        error={error}
        availableRoles={availableRoles}
        selectedPerspectives={selectedPerspectives}
        onPerspectivesChange={setSelectedPerspectives}
        loadingRoles={loadingRoles}
        perspectiveWeights={perspectiveWeights}
        onPerspectiveWeightsChange={setPerspectiveWeights}
      />
    );
  };
  
  // 面试对话界面
  const renderInterview = () => {
    if (!session) return null;

    return (
      <div className="relative">
        {/* 加载下一题时的遮罩 */}
        {isLoadingQuestion && (
          <div className="absolute inset-0 bg-white/80 backdrop-blur-sm z-50 flex items-center justify-center">
            <div className="text-center">
              <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin" />
              <p className="text-slate-500">正在出题中...</p>
            </div>
          </div>
        )}
        <InterviewChatPanel
          session={session}
          currentQuestion={currentQuestion}
          messages={messages}
          answer={answer}
          onAnswerChange={setAnswer}
          onSubmit={handleSubmitAnswer}
          onCompleteEarly={handleCompleteEarly}
          isSubmitting={isSubmitting}
          isLoadingQuestion={isLoadingQuestion}
          showCompleteConfirm={showCompleteConfirm}
          onShowCompleteConfirm={setShowCompleteConfirm}
        />
      </div>
    );
  };

  const stageSubtitles = {
    config: '配置您的面试参数',
    interview: '认真回答每个问题，展示您的实力'
  };
  
  return (
    <div className="pb-10">
      {/* 页面头部 */}
      <motion.div 
        className="text-center mb-10"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="text-3xl font-bold text-slate-900 mb-2 flex items-center justify-center gap-3">
          <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
            <svg className="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="12" y1="19" x2="12" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="8" y1="23" x2="16" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          模拟面试
        </h1>
        <p className="text-slate-500">{stageSubtitles[stage]}</p>
      </motion.div>
      
      <AnimatePresence mode="wait" initial={false}>
        {stage === 'config' && (
          <motion.div
            key="config"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.3 }}
          >
            {renderConfig()}
          </motion.div>
        )}
        {stage === 'interview' && (
          <motion.div
            key="interview"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            {renderInterview()}
          </motion.div>
        )}
      </AnimatePresence>
      
      {/* 提前交卷确认对话框 */}
      <ConfirmDialog
        open={showCompleteConfirm}
        title="提前交卷"
        message="确定要提前交卷吗？未回答的问题将按0分计算。"
        confirmText="确定交卷"
        cancelText="取消"
        confirmVariant="warning"
        loading={isSubmitting}
        onConfirm={handleCompleteEarly}
        onCancel={() => setShowCompleteConfirm(false)}
      />
    </div>
  );
}
