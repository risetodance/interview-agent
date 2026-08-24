import {useEffect, useState} from 'react';
import {historyApi, ResumeListItem} from '../api/history';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import {formatDateOnly} from '../utils/date';
import {
  Search,
  FileText,
  Trash2,
  ChevronRight,
  Loader2,
} from 'lucide-react';

interface HistoryListProps {
  onSelectResume: (id: number) => void;
}

/** 面试状态 chip 语义色 */
function interviewChipCls(interviewCount: number): string {
  return interviewCount > 0
    ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
    : 'text-zinc-500 bg-zinc-50 border-zinc-200';
}

function interviewChipText(interviewCount: number): string {
  return interviewCount > 0 ? '已完成' : '待面试';
}

export default function HistoryList({ onSelectResume }: HistoryListProps) {
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: number; filename: string } | null>(null);

  useEffect(() => {
    loadResumes();
  }, []);

  const loadResumes = async () => {
    setLoading(true);
    try {
      const data = await historyApi.getResumes();
      setResumes(data);
    } catch (err) {
      console.error('加载历史记录失败', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteClick = (id: number, filename: string, e: React.MouseEvent) => {
    e.stopPropagation(); // 阻止触发行点击事件
    setDeleteConfirm({ id, filename });
  };

  const handleDeleteConfirm = async () => {
    if (!deleteConfirm) return;

    const { id } = deleteConfirm;
    setDeletingId(id);
    try {
      await historyApi.deleteResume(id);
      // 重新加载列表
      await loadResumes();
      setDeleteConfirm(null);
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
    <div className="fade-in w-full">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6 flex-wrap gap-4">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">简历库</h1>
          <p className="mt-1 text-sm text-zinc-500">管理已解析的简历及其模拟面试记录</p>
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400 pointer-events-none" />
          <input
            type="text"
            placeholder="搜索简历..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full h-9 pl-9 pr-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
          />
        </div>
      </div>

      {/* 加载状态 */}
      {loading && (
        <div className="flex items-center justify-center py-24">
          <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredResumes.length === 0 && (
        <div className="bg-white border border-zinc-200 rounded-lg">
          <p className="px-5 py-12 text-xs text-zinc-400 text-center">
            {searchTerm ? '没有匹配的简历，换个关键词试试' : '暂无简历记录，上传第一份简历开始 AI 分析'}
          </p>
        </div>
      )}

      {/* 表格 */}
      {!loading && filteredResumes.length > 0 && (
        <div className="bg-white border border-zinc-200 rounded-lg overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-zinc-100">
                <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">简历名称</th>
                <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">上传日期</th>
                <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">AI 评分</th>
                <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">面试状态</th>
                <th className="w-24"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100">
              {filteredResumes.map((resume) => (
                <tr
                  key={resume.id}
                  onClick={() => onSelectResume(resume.id)}
                  className="hover:bg-zinc-50 cursor-pointer transition-colors group"
                >
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3 min-w-0">
                      <FileText className="w-4 h-4 text-zinc-400 shrink-0" />
                      <span className="font-mono text-sm text-zinc-800 truncate max-w-[360px]">
                        {resume.filename}
                      </span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5">
                    <span className="font-mono text-xs text-zinc-500 tabular-nums">
                      {formatDateOnly(resume.uploadedAt)}
                    </span>
                  </td>
                  <td className="px-5 py-3.5">
                    {resume.latestScore !== undefined ? (
                      <span className="font-mono text-sm font-medium text-primary-800 tabular-nums">
                        {resume.latestScore}
                      </span>
                    ) : (
                      <span className="text-zinc-300">—</span>
                    )}
                  </td>
                  <td className="px-5 py-3.5">
                    <span
                      className={`inline-flex text-xs border rounded px-1.5 py-0.5 ${interviewChipCls(resume.interviewCount)}`}
                    >
                      {interviewChipText(resume.interviewCount)}
                    </span>
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={(e) => handleDeleteClick(resume.id, resume.filename, e)}
                        disabled={deletingId === resume.id}
                        className="p-1.5 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="删除简历"
                      >
                        {deletingId === resume.id ? (
                          <Loader2 className="w-4 h-4 animate-spin" />
                        ) : (
                          <Trash2 className="w-4 h-4" />
                        )}
                      </button>
                      <ChevronRight className="w-4 h-4 text-zinc-300 group-hover:text-primary-600 transition-colors" />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteConfirm !== null}
        item={deleteConfirm}
        itemType="简历"
        loading={deletingId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteConfirm(null)}
        customMessage={
          deleteConfirm ? (
            <>
              <p className="mb-2">确定要删除简历 <strong>"{deleteConfirm.filename}"</strong> 吗？</p>
              <p className="text-sm text-zinc-500 mb-2">删除后将同时删除：</p>
              <ul className="text-sm text-zinc-500 list-disc list-inside mb-2">
                <li>简历评价记录</li>
                <li>所有模拟面试记录</li>
              </ul>
              <p className="text-sm font-medium text-red-600">此操作不可恢复！</p>
            </>
          ) : undefined
        }
      />
    </div>
  );
}
