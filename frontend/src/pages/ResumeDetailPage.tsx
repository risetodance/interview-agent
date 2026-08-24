import {useEffect, useState, useCallback, useRef} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {historyApi, InterviewDetail, ResumeDetail} from '../api/history';
import AnalysisPanel from '../components/AnalysisPanel';
import InterviewPanel from '../components/InterviewPanel';
import InterviewDetailPanel from '../components/InterviewDetailPanel';
import {formatDateOnly} from '../utils/date';
import {
  ChevronLeft,
  Download,
  Mic,
  Loader2,
} from 'lucide-react';

interface ResumeDetailPageProps {
  resumeId: number;
  onBack: () => void;
  onStartInterview: (resumeText: string, resumeId: number) => void;
}

type TabType = 'analysis' | 'interview';
type DetailViewType = 'list' | 'interviewDetail';

export default function ResumeDetailPage({ resumeId, onBack, onStartInterview }: ResumeDetailPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('analysis');
  const [exporting, setExporting] = useState<string | null>(null);
  const [detailView, setDetailView] = useState<DetailViewType>('list');
  const [selectedInterview, setSelectedInterview] = useState<InterviewDetail | null>(null);
  const [loadingInterview, setLoadingInterview] = useState(false);
  const [reanalyzing, setReanalyzing] = useState(false);

  // 防止重复加载面试详情的 ref
  const hasLoadedViewInterview = useRef(false);

  // 静默加载数据（用于轮询）
  const loadResumeDetailSilent = useCallback(async () => {
    try {
      const data = await historyApi.getResumeDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    }
  }, [resumeId]);

  const loadResumeDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await historyApi.getResumeDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    } finally {
      setLoading(false);
    }
  }, [resumeId]);

  useEffect(() => {
    loadResumeDetail();
  }, [loadResumeDetail]);

  // 轮询：当分析状态为待处理时，每5秒刷新一次
  // 待处理判断：显式的 PENDING/PROCESSING 状态，或状态未定义且无分析结果
  useEffect(() => {
    const isProcessing = resume && (
      resume.analyzeStatus === 'PENDING' ||
      resume.analyzeStatus === 'PROCESSING' ||
      (resume.analyzeStatus === undefined && (!resume.analyses || resume.analyses.length === 0))
    );

    if (isProcessing && !loading) {
      const timer = setInterval(() => {
        loadResumeDetailSilent();
      }, 5000);

      return () => clearInterval(timer);
    }
  }, [resume, loading, loadResumeDetailSilent]);

  // 重新分析
  const handleReanalyze = async () => {
    try {
      setReanalyzing(true);
      await historyApi.reanalyze(resumeId);
      await loadResumeDetailSilent();
    } catch (err) {
      console.error('重新分析失败', err);
    } finally {
      setReanalyzing(false);
    }
  };

  // 检查是否需要自动打开面试详情
  useEffect(() => {
    const viewInterview = (location.state as { viewInterview?: string })?.viewInterview;
    if (viewInterview && resume && !hasLoadedViewInterview.current) {
      hasLoadedViewInterview.current = true;
      // 切换到面试标签页
      setActiveTab('interview');
      // 加载并显示面试详情
      const loadAndViewInterview = async () => {
        setLoadingInterview(true);
        try {
          const detail = await historyApi.getInterviewDetail(viewInterview);
          setSelectedInterview(detail);
          setDetailView('interviewDetail');
        } catch (err) {
          console.error('加载面试详情失败', err);
        } finally {
          setLoadingInterview(false);
        }
      };
      loadAndViewInterview();
    }
  }, [location.state, resume]);

  const handleExportAnalysisPdf = async () => {
    setExporting('analysis');
    try {
      const blob = await historyApi.exportAnalysisPdf(resumeId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `简历分析报告_${resume?.filename || resumeId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  const handleExportInterviewPdf = async (sessionId: string) => {
    setExporting(sessionId);
    try {
      const blob = await historyApi.exportInterviewPdf(sessionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `面试报告_${sessionId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  const handleViewInterview = async (sessionId: string) => {
    setLoadingInterview(true);
    try {
      const detail = await historyApi.getInterviewDetail(sessionId);

      // 如果是进行中的面试，跳转到面试页面继续
      if (detail.status === 'IN_PROGRESS' || detail.status === 'CREATED') {
        navigate(`/interview/${resumeId}`, {
          state: { sessionId, resumeId }
        });
        return;
      }

      // 检查评估是否完成，只有评估完成后才能查看综合报告
      if (detail.evaluateStatus !== 'COMPLETED') {
        alert('面试评估尚未完成，请稍后再试');
        setLoadingInterview(false);
        return;
      }

      // 已完成的面试，跳转到报告页面（与 /interviews/:id/report 一样的综合报告页面）
      navigate(`/interviews/${sessionId}/report`, {
        state: { sessionId, resumeId, from: 'resumeDetail' }
      });
    } catch (err) {
      alert('加载面试详情失败');
    } finally {
      setLoadingInterview(false);
    }
  };

  const handleBackToInterviewList = () => {
    setDetailView('list');
    setSelectedInterview(null);
  };

  const handleDeleteInterview = async (sessionId: string) => {
    // 删除后重新加载简历详情
    await loadResumeDetail();
    // 如果删除的是当前查看的面试，返回列表
    if (selectedInterview?.sessionId === sessionId) {
      setDetailView('list');
      setSelectedInterview(null);
    }
  };

  const handleTabChange = (tab: TabType) => {
    setActiveTab(tab);
    setDetailView('list');
    setSelectedInterview(null);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
      </div>
    );
  }

  if (!resume) {
    return (
      <div className="text-center py-24">
        <p className="text-sm text-red-600 mb-4">加载失败，请返回重试</p>
        <button
          onClick={onBack}
          className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
        >
          返回列表
        </button>
      </div>
    );
  }

  const latestAnalysis = resume.analyses?.[0];
  const tabs = [
    { id: 'analysis' as const, label: '简历分析' },
    { id: 'interview' as const, label: '面试记录', count: resume.interviews?.length || 0 },
  ];

  return (
    <div className="fade-in w-full">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6 flex-wrap gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <button
            onClick={detailView === 'interviewDetail' ? handleBackToInterviewList : onBack}
            className="h-9 w-9 shrink-0 flex items-center justify-center rounded-md border border-zinc-300 text-zinc-500 hover:bg-zinc-50 hover:text-zinc-700 hover:border-zinc-400 transition-colors"
            aria-label="返回"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <h1 className="text-xl font-semibold text-zinc-900 tracking-tight truncate max-w-[560px]">
              {detailView === 'interviewDetail' ? `面试详情 #${selectedInterview?.sessionId?.slice(-6) || ''}` : resume.filename}
            </h1>
            <p className="mt-1 font-mono text-xs text-zinc-400 tabular-nums">
              {detailView === 'interviewDetail'
                ? `完成于 ${formatDateOnly(selectedInterview?.completedAt || selectedInterview?.createdAt || '')}`
                : `上传于 ${formatDateOnly(resume.uploadedAt)}`
              }
            </p>
          </div>
        </div>

        <div className="flex gap-2.5">
          {detailView === 'interviewDetail' && selectedInterview && (
            <button
              onClick={() => handleExportInterviewPdf(selectedInterview.sessionId)}
              disabled={exporting === selectedInterview.sessionId}
              className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-2"
            >
              <Download className="w-4 h-4" />
              {exporting === selectedInterview.sessionId ? '导出中...' : '导出 PDF'}
            </button>
          )}
          {detailView !== 'interviewDetail' && (
            <button
              onClick={() => onStartInterview(resume.resumeText, resumeId)}
              className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors inline-flex items-center gap-2"
            >
              <Mic className="w-4 h-4" />
              开始模拟面试
            </button>
          )}
        </div>
      </div>

      {/* 标签页切换（底线式）- 仅在非面试详情时显示 */}
      {detailView !== 'interviewDetail' && (
        <div className="flex items-center border-b border-zinc-200 mb-6">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => handleTabChange(tab.id)}
              className={`pb-2.5 pr-6 text-sm border-b-2 -mb-px transition-colors ${
                activeTab === tab.id
                  ? 'border-primary-600 text-zinc-900 font-medium'
                  : 'border-transparent text-zinc-500 hover:text-zinc-800'
              }`}
            >
              {tab.label}
              {tab.count !== undefined && tab.count > 0 && (
                <span className="ml-1.5 font-mono text-xs text-zinc-400 tabular-nums">{tab.count}</span>
              )}
            </button>
          ))}
        </div>
      )}

      {/* 内容区域 */}
      <div>
        {detailView === 'interviewDetail' && selectedInterview ? (
          <InterviewDetailPanel interview={selectedInterview} />
        ) : activeTab === 'analysis' ? (
          <div key="analysis" className="fade-in">
            <AnalysisPanel
              analysis={latestAnalysis}
              analyzeStatus={resume.analyzeStatus}
              analyzeError={resume.analyzeError}
              onExport={handleExportAnalysisPdf}
              exporting={exporting === 'analysis'}
              onReanalyze={handleReanalyze}
              reanalyzing={reanalyzing}
            />
          </div>
        ) : (
          <div key="interview" className="fade-in">
            <InterviewPanel
              interviews={resume.interviews || []}
              onStartInterview={() => onStartInterview(resume.resumeText, resumeId)}
              onViewInterview={handleViewInterview}
              onExportInterview={handleExportInterviewPdf}
              onDeleteInterview={handleDeleteInterview}
              exporting={exporting}
              loadingInterview={loadingInterview}
            />
          </div>
        )}
      </div>
    </div>
  );
}
