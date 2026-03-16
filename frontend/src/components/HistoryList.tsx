import { useEffect, useState, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { historyApi, ResumeListItem, ResumeStats, AnalyzeStatus } from '../api/history';
import DeleteConfirmDialog from './DeleteConfirmDialog';
import { getScoreColor } from '../utils/score';
import { formatDate } from '../utils/date';
import {
  Search,
  FileText,
  CheckCircle2,
  Trash2,
  ChevronRight,
  Download,
  RefreshCw,
  Loader2,
  Clock,
  AlertCircle,
  CheckCircle,
  FileStack,
  MessageSquare,
  Eye,
} from 'lucide-react';

interface HistoryListProps {
  onSelectResume: (id: number) => void;
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// 状态图标组件
function StatusIcon({ status, hasScore }: { status?: AnalyzeStatus; hasScore: boolean }) {
  // 如果状态未定义，根据是否有分数判断
  if (status === undefined) {
    if (hasScore) {
      return <CheckCircle className="w-4 h-4 text-green-500" />;
    }
    return <Clock className="w-4 h-4 text-yellow-500" />;
  }

  switch (status) {
    case 'COMPLETED':
      return <CheckCircle className="w-4 h-4 text-green-500" />;
    case 'PROCESSING':
      return <Loader2 className="w-4 h-4 text-blue-500 animate-spin" />;
    case 'PENDING':
      return <Clock className="w-4 h-4 text-yellow-500" />;
    case 'FAILED':
      return <AlertCircle className="w-4 h-4 text-red-500" />;
    default:
      return <CheckCircle className="w-4 h-4 text-green-500" />;
  }
}

// 状态文本
function getStatusText(status?: AnalyzeStatus, hasScore?: boolean): string {
  // 如果状态未定义，根据是否有分数判断
  if (status === undefined) {
    if (hasScore) {
      return '已完成';
    }
    return '待分析';
  }

  switch (status) {
    case 'COMPLETED':
      return '已完成';
    case 'PROCESSING':
      return '分析中';
    case 'PENDING':
      return '待分析';
    case 'FAILED':
      return '失败';
    default:
      return '未知';
  }
}

// 统计卡片组件
function StatCard({
  icon: Icon,
  label,
  value,
  color,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  color: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white rounded-xl p-6 shadow-sm border border-slate-100"
    >
      <div className="flex items-center gap-4">
        <div className={`p-3 rounded-lg ${color}`}>
          <Icon className="w-6 h-6 text-white" />
        </div>
        <div>
          <p className="text-sm text-slate-500">{label}</p>
          <p className="text-2xl font-bold text-slate-800">{value.toLocaleString()}</p>
        </div>
      </div>
    </motion.div>
  );
}

export default function HistoryList({ onSelectResume }: HistoryListProps) {
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [stats, setStats] = useState<ResumeStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteItem, setDeleteItem] = useState<ResumeListItem | null>(null);
  const [reanalyzingId, setReanalyzingId] = useState<number | null>(null);

  // 静默加载数据（用于轮询）
  const loadDataSilent = useCallback(async () => {
    try {
      const [resumeData, statsData] = await Promise.all([
        historyApi.getResumes(),
        historyApi.getStatistics(),
      ]);
      setResumes(resumeData);
      setStats(statsData);
    } catch (err) {
      console.error('加载数据失败', err);
    }
  }, []);

  // 加载数据
  const loadResumes = useCallback(async () => {
    setLoading(true);
    try {
      const [resumeData, statsData] = await Promise.all([
        historyApi.getResumes(),
        historyApi.getStatistics(),
      ]);
      setResumes(resumeData);
      setStats(statsData);
    } catch (err) {
      console.error('加载数据失败', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadResumes();
  }, [loadResumes]);

  // 轮询：当有待处理项时，每5秒刷新一次
  // 待处理判断：显式的 PENDING/PROCESSING 状态，或状态未定义且无分数
  useEffect(() => {
    const hasPendingItems = resumes.some(
      r => r.analyzeStatus === 'PENDING' ||
        r.analyzeStatus === 'PROCESSING' ||
        (r.analyzeStatus === undefined && r.latestScore === undefined)
    );

    if (hasPendingItems && !loading) {
      const timer = setInterval(() => {
        loadDataSilent();
      }, 5000);

      return () => clearInterval(timer);
    }
  }, [resumes, loading, loadDataSilent]);

  // 下载简历
  const handleDownload = (resume: ResumeListItem, e: React.MouseEvent) => {
    e.stopPropagation();
    if (resume.storageUrl) {
      const link = document.createElement('a');
      link.href = resume.storageUrl;
      link.download = resume.filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }
  };

  // 重新分析
  const handleReanalyze = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      setReanalyzingId(id);
      await historyApi.reanalyze(id);
      await loadDataSilent();
    } catch (err) {
      console.error('重新分析失败', err);
    } finally {
      setReanalyzingId(null);
    }
  };

  const handleDeleteClick = (resume: ResumeListItem, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteItem(resume);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteItem) return;

    setDeletingId(deleteItem.id);
    try {
      await historyApi.deleteResume(deleteItem.id);
      await loadResumes();
      setDeleteItem(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingId(null);
    }
  };

  const filteredResumes = resumes.filter(resume =>
    resume.filename.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <motion.div
      className="w-full"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      {/* 头部 */}
      <div className="flex justify-between items-start mb-8 flex-wrap gap-6">
        <div>
          <motion.h1
            className="text-2xl font-bold text-slate-800 flex items-center gap-3"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            <FileStack className="w-7 h-7 text-primary-500" />
            简历库
          </motion.h1>
          <motion.p
            className="text-slate-500 mt-1"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            管理您已分析过的所有简历及面试记录
          </motion.p>
        </div>

        <motion.div
          className="flex items-center gap-3 bg-white border border-slate-200 rounded-xl px-4 py-2.5 min-w-[280px] focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 transition-all"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Search className="w-5 h-5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索简历..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 outline-none text-slate-700 placeholder:text-slate-400"
          />
        </motion.div>
      </div>

      {/* 统计卡片 */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <StatCard
            icon={FileStack}
            label="简历总数"
            value={stats.totalCount}
            color="bg-primary-500"
          />
          <StatCard
            icon={MessageSquare}
            label="面试总数"
            value={stats.totalInterviewCount}
            color="bg-indigo-500"
          />
          <StatCard
            icon={Eye}
            label="总访问次数"
            value={stats.totalAccessCount}
            color="bg-emerald-500"
          />
        </div>
      )}

      {/* 加载状态 */}
      {loading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredResumes.length === 0 && (
        <motion.div
          className="text-center py-20 bg-white rounded-2xl shadow-sm border border-slate-100"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <FileText className="w-16 h-16 text-slate-300 mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-slate-700 mb-2">暂无简历记录</h3>
          <p className="text-slate-500">上传简历开始您的第一次 AI 面试分析</p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && filteredResumes.length > 0 && (
        <motion.div
          className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <table className="w-full">
            <thead className="bg-slate-50 border-b border-slate-100">
              <tr>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">名称</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">大小</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">分析状态</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">AI 评分</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">面试</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">上传时间</th>
                <th className="text-right px-6 py-4 text-sm font-medium text-slate-600">操作</th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {filteredResumes.map((resume, index) => (
                  <motion.tr
                    key={resume.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.05 }}
                    onClick={() => onSelectResume(resume.id)}
                    className="border-b border-slate-50 hover:bg-slate-50 cursor-pointer transition-colors group"
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <FileText className="w-5 h-5 text-slate-400" />
                        <div>
                          <p className="font-medium text-slate-800">{resume.filename}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-600">
                      {formatFileSize(resume.fileSize)}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <StatusIcon status={resume.analyzeStatus} hasScore={resume.latestScore !== undefined} />
                        <span className="text-sm text-slate-600">
                          {getStatusText(resume.analyzeStatus, resume.latestScore !== undefined)}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {resume.latestScore !== undefined ? (
                        <div className="flex items-center gap-3">
                          <div className="w-16 h-2 bg-slate-100 rounded-full overflow-hidden">
                            <motion.div
                              className={`h-full ${getScoreColor(resume.latestScore).split(' ')[0]} rounded-full`}
                              initial={{ width: 0 }}
                              animate={{ width: `${resume.latestScore}%` }}
                              transition={{ duration: 0.8, delay: index * 0.05 }}
                            />
                          </div>
                          <span className="font-bold text-slate-800">{resume.latestScore}</span>
                        </div>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      {resume.interviewCount > 0 ? (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-emerald-50 text-emerald-600 rounded-full text-sm font-medium">
                          <CheckCircle2 className="w-4 h-4" />
                          {resume.interviewCount} 次
                        </span>
                      ) : (
                        <span className="inline-flex px-3 py-1 bg-slate-100 text-slate-500 rounded-full text-sm">待面试</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-500">
                      {formatDate(resume.uploadedAt)}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {/* 下载按钮 */}
                        {resume.storageUrl && (
                          <button
                            onClick={(e) => handleDownload(resume, e)}
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded-lg transition-colors"
                            title="下载"
                          >
                            <Download className="w-4 h-4" />
                          </button>
                        )}
                        {/* 重新分析按钮（仅 FAILED 状态显示） */}
                        {resume.analyzeStatus === 'FAILED' && (
                          <button
                            onClick={(e) => handleReanalyze(resume.id, e)}
                            disabled={reanalyzingId === resume.id}
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded-lg transition-colors disabled:opacity-50"
                            title="重新分析"
                          >
                            <RefreshCw className={`w-4 h-4 ${reanalyzingId === resume.id ? 'animate-spin' : ''}`} />
                          </button>
                        )}
                        {/* 删除按钮 */}
                        <button
                          onClick={(e) => handleDeleteClick(resume, e)}
                          disabled={deletingId === resume.id}
                          className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                        <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-primary-500 group-hover:translate-x-1 transition-all" />
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        </motion.div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteItem !== null}
        item={deleteItem ? { id: deleteItem.id, name: deleteItem.filename } : null}
        itemType="简历"
        loading={deletingId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteItem(null)}
      />
    </motion.div>
  );
}
