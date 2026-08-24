import {useMemo, useState} from 'react';
import {CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {formatDateOnly} from '../utils/date';
import {getScoreColor} from '../utils/score';
import type {InterviewItem} from '../api/history';
import {historyApi} from '../api/history';
import ConfirmDialog from './ConfirmDialog';
import {
  Loader2,
  Calendar,
  MessageSquare,
  Download,
  Trash2,
  ChevronRight
} from 'lucide-react';

interface InterviewPanelProps {
  interviews: InterviewItem[];
  onStartInterview: () => void;
  onViewInterview: (sessionId: string) => void;
  onExportInterview: (sessionId: string) => void;
  onDeleteInterview: (sessionId: string) => void;
  exporting: string | null;
  loadingInterview: boolean;
}

/**
 * 面试记录面板组件
 */
export default function InterviewPanel({
  interviews,
  onStartInterview,
  onViewInterview,
  onExportInterview,
  onDeleteInterview,
  exporting,
  loadingInterview
}: InterviewPanelProps) {
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ sessionId: string } | null>(null);

  const handleDeleteClick = (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation(); // 阻止触发卡片点击事件
    setDeleteConfirm({ sessionId });
  };

  const handleDeleteConfirm = async () => {
    if (!deleteConfirm) return;

    const { sessionId } = deleteConfirm;
    setDeletingSessionId(sessionId);
    try {
      await historyApi.deleteInterview(sessionId);
      onDeleteInterview(sessionId);
      setDeleteConfirm(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingSessionId(null);
    }
  };
  // 准备图表数据
  const chartData = useMemo(() => {
    return interviews
      .filter(i => i.overallScore !== null)
      .map((interview) => ({
        name: formatDateOnly(interview.createdAt),
        score: interview.overallScore || 0,
        index: interviews.length - interviews.indexOf(interview)
      }))
      .reverse();
  }, [interviews]);

  if (interviews.length === 0) {
    return (
      <div className="bg-white border border-zinc-200 rounded-lg py-16 flex flex-col items-center justify-center fade-in">
        <p className="text-sm text-zinc-500">暂无面试记录</p>
        <p className="mt-1 mb-5 text-xs text-zinc-400">开始模拟面试，获取专业评估</p>
        <button
          onClick={onStartInterview}
          className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors"
        >
          开始模拟面试
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* 面试表现趋势图 */}
      {chartData.length > 0 && (
        <div className="bg-white border border-zinc-200 rounded-lg fade-in">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
            <h3 className="text-sm font-medium text-zinc-900">面试表现趋势</h3>
            <span className="font-mono text-xs text-zinc-400">共 {chartData.length} 场</span>
          </div>
          <div className="p-5">
            <div className="h-48">
              <ResponsiveContainer
                width="100%"
                height="100%"
                initialDimension={{ width: 600, height: 192 }}
              >
                <LineChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" />
                  <XAxis
                    dataKey="name"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: '#a1a1aa', fontSize: 12 }}
                  />
                  <YAxis
                    domain={[0, 100]}
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: '#a1a1aa', fontSize: 12 }}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#fff',
                      border: '1px solid #e4e4e7',
                      borderRadius: '6px'
                    }}
                    formatter={(value) => [`${value} 分`, '得分']}
                  />
                  <Line
                    type="monotone"
                    dataKey="score"
                    stroke="#276f8d"
                    strokeWidth={2}
                    dot={{ fill: '#276f8d', stroke: '#276f8d', strokeWidth: 2, r: 4 }}
                    activeDot={{ r: 5, fill: '#276f8d', stroke: '#276f8d', strokeWidth: 2 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      )}

      {/* 历史面试场次 */}
      <div className="bg-white border border-zinc-200 rounded-lg">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100">
          <h3 className="text-sm font-medium text-zinc-900">历史面试</h3>
          <span className="font-mono text-xs text-zinc-400">{interviews.length} 场</span>
        </div>

        <div className="p-5 space-y-3">
          {interviews.map((interview, index) => (
            <InterviewItemCard
              key={interview.id}
              interview={interview}
              index={index}
              total={interviews.length}
              exporting={exporting === interview.sessionId}
              deleting={deletingSessionId === interview.sessionId}
              onView={() => onViewInterview(interview.sessionId)}
              onExport={() => onExportInterview(interview.sessionId)}
              onDelete={(e) => handleDeleteClick(interview.sessionId, e)}
            />
          ))}
        </div>

        {/* 删除确认对话框 */}
        <ConfirmDialog
          open={deleteConfirm !== null}
          title="删除面试记录"
          message="确定要删除这条面试记录吗？删除后无法恢复。"
          confirmText="确定删除"
          cancelText="取消"
          confirmVariant="danger"
          loading={deletingSessionId !== null}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteConfirm(null)}
        />

        {loadingInterview && (
          <div className="fixed inset-0 bg-black/20 flex items-center justify-center z-50">
            <div className="bg-white border border-zinc-200 rounded-md px-5 py-4 flex items-center gap-3">
              <Loader2 className="w-4 h-4 text-primary-600 animate-spin" />
              <span className="text-sm text-zinc-600">加载面试详情...</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// 面试项卡片组件
function InterviewItemCard({
  interview,
  index,
  total,
  exporting,
  deleting,
  onView,
  onExport,
  onDelete
}: {
  interview: InterviewItem;
  index: number;
  total: number;
  exporting: boolean;
  deleting: boolean;
  onView: () => void;
  onExport: () => void;
  onDelete: (e: React.MouseEvent) => void;
}) {
  return (
    <div
      onClick={onView}
      className="flex items-center gap-4 px-4 py-3 bg-white border border-zinc-200 rounded-md hover:bg-zinc-50 cursor-pointer transition-colors"
    >
      {/* 得分 */}
      <div className={`w-11 h-11 rounded-md flex items-center justify-center font-mono text-base font-semibold tabular-nums shrink-0 ${
        interview.overallScore !== null
          ? getScoreColor(interview.overallScore, [85, 70])
          : 'bg-zinc-100 text-zinc-400'
      }`}>
        {interview.overallScore ?? '-'}
      </div>

      {/* 信息 */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-zinc-800 truncate">
          模拟面试 <span className="font-mono">#{total - index}</span>
        </p>
        <div className="flex items-center gap-4 mt-1 text-xs text-zinc-400">
          <span className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5" />
            <span className="font-mono">{formatDateOnly(interview.createdAt)}</span>
          </span>
          <span className="flex items-center gap-1">
            <MessageSquare className="w-3.5 h-3.5" />
            <span className="font-mono">{interview.totalQuestions} 题</span>
          </span>
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-1 shrink-0">
        {/* 导出按钮 */}
        <button
          onClick={(e) => { e.stopPropagation(); onExport(); }}
          disabled={exporting}
          className="p-2 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="导出面试记录"
        >
          {exporting ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <Download className="w-4 h-4" />
          )}
        </button>

        {/* 删除按钮 */}
        <button
          onClick={onDelete}
          disabled={deleting}
          className="p-2 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="删除面试记录"
        >
          {deleting ? (
            <Loader2 className="w-4 h-4 animate-spin text-red-600" />
          ) : (
            <Trash2 className="w-4 h-4" />
          )}
        </button>
      </div>

      {/* 箭头 */}
      <ChevronRight className="w-4 h-4 text-zinc-300 shrink-0" />
    </div>
  );
}
