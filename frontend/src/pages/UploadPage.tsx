import { ChangeEvent, DragEvent, useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, FileText, X, AlertCircle, Loader2, ChevronRight } from 'lucide-react';
import { resumeApi } from '../api/resume';
import { historyApi, type AnalyzeStatus, type ResumeListItem } from '../api/history';
import { getErrorMessage } from '../api/request';
import { formatDate } from '../utils/date';

interface UploadPageProps {
  onUploadComplete: (resumeId: number) => void;
}

/** 最近简历最多展示条数 */
const MAX_RECENT = 20;

/** 解析状态文案 */
function analyzeStatusText(status?: AnalyzeStatus): string {
  switch (status) {
    case 'PENDING': return '待解析';
    case 'PROCESSING': return '解析中';
    case 'COMPLETED': return '已完成';
    case 'FAILED': return '解析失败';
    default: return '待解析';
  }
}

/** 解析状态色彩（克制语义色） */
function analyzeStatusCls(status?: AnalyzeStatus): string {
  switch (status) {
    case 'PROCESSING': return 'text-amber-700 bg-amber-50 border-amber-200';
    case 'FAILED': return 'text-red-700 bg-red-50 border-red-200';
    case 'COMPLETED': return 'text-zinc-500 bg-zinc-50 border-zinc-200';
    default: return 'text-zinc-500 bg-zinc-50 border-zinc-200';
  }
}

/**
 * 工作台（默认首页）
 * 统计概览 + 简历上传 + 最近简历，数据全部来自现有接口
 */
export default function UploadPage({ onUploadComplete }: UploadPageProps) {
  const navigate = useNavigate();

  // 上传状态
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);

  // 工作台数据（统计从列表接口派生：后端无独立统计端点）
  const [allResumes, setAllResumes] = useState<ResumeListItem[]>([]);
  const [loaded, setLoaded] = useState(false);

  // 拉取简历列表（失败静默降级，不阻断上传主流程）
  const loadData = useCallback(async (cancelledRef?: { current: boolean }) => {
    try {
      const resumeList = await historyApi.getResumes();
      if (cancelledRef?.current) return;
      setAllResumes(resumeList);
      setLoaded(true);
    } catch {
      if (cancelledRef?.current) return;
      setLoaded(true);
    }
  }, []);

  useEffect(() => {
    const cancelledRef = { current: false };
    loadData(cancelledRef);
    return () => { cancelledRef.current = true; };
  }, [loadData]);

  /** 上传成功后刷新工作台数据（下次回到本页时 useEffect 也会重新拉） */
  const refreshData = async () => {
    await loadData();
  };

  // 拖放与文件选择
  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
  }, []);

  const handleDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const files = e.dataTransfer.files;
    if (files.length > 0) setSelectedFile(files[0]);
  }, []);

  const handleFileChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) setSelectedFile(files[0]);
  }, []);

  /** 上传并解析 */
  const handleUpload = async () => {
    if (!selectedFile) return;
    setUploading(true);
    setError('');

    try {
      const data = await resumeApi.uploadAndAnalyze(selectedFile);

      // 异步模式：只检查上传是否成功（storage 信息）
      if (!data.storage || !data.storage.resumeId) {
        throw new Error('上传失败，请重试');
      }

      // 先刷新工作台数据再跳转（简历库列表展示最新状态）
      await refreshData();
      onUploadComplete(data.storage.resumeId);
    } catch (err) {
      setError(getErrorMessage(err));
      setUploading(false);
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  // 最近简历候选（按上传时间倒序，最多 20 条）
  const recentResumes = [...allResumes]
    .sort((a, b) => (b.uploadedAt || '').localeCompare(a.uploadedAt || ''))
    .slice(0, MAX_RECENT);

  // 按列表区实际可用高度动态决定展示条数（能放下几条就展示几条，上限 20）
  const listRef = useRef<HTMLUListElement>(null);
  const [visibleCount, setVisibleCount] = useState(6);

  useEffect(() => {
    const ul = listRef.current;
    if (!ul) return;

    const update = () => {
      const height = ul.clientHeight;
      if (height <= 0) return;
      // 以第一条实测高度为准；列表为空时按结构估算
      const firstItem = ul.querySelector('li');
      const itemHeight = firstItem ? (firstItem as HTMLElement).offsetHeight : 54;
      if (itemHeight <= 0) return;
      const count = Math.floor(height / itemHeight);
      setVisibleCount(Math.max(1, Math.min(MAX_RECENT, count)));
    };

    update();
    const observer = new ResizeObserver(update);
    observer.observe(ul);
    return () => observer.disconnect();
    // 数据异步到达后 ul 才挂载，需重新绑定观察
  }, [recentResumes.length > 0]);

  // 实际展示条数（数据不足时以数据为准）
  const shownCount = Math.min(visibleCount, recentResumes.length);
  const shownResumes = recentResumes.slice(0, shownCount);

  // 统计从全量列表派生
  const totalCount = allResumes.length;
  const totalInterviewCount = allResumes.reduce((sum, r) => sum + (r.interviewCount || 0), 0);
  const maxScore = allResumes.reduce((max, r) => Math.max(max, r.latestScore ?? 0), 0) || null;

  const today = new Date();
  const dateStr = formatDate(today.toISOString(), { year: 'numeric', month: '2-digit', day: '2-digit' });
  const weekday = today.toLocaleDateString('zh-CN', { weekday: 'short' });

  const statCards = [
    { label: '简历', value: loaded ? String(totalCount) : '—', unit: '份' },
    { label: '模拟面试', value: loaded ? String(totalInterviewCount) : '—', unit: '场' },
    { label: '最高评分', value: maxScore !== null ? String(maxScore) : '—', unit: '分' },
  ];

  return (
    <div className="fade-in flex-1 flex flex-col">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">工作台</h1>
          <p className="mt-1 text-sm text-zinc-500">上传简历，开始一场模拟面试</p>
        </div>
        <span className="font-mono text-xs text-zinc-400">
          {dateStr} · {weekday}
        </span>
      </div>

      {/* 统计概览 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {statCards.map((card) => (
          <div key={card.label} className="bg-white border border-zinc-200 rounded-lg px-5 py-4">
            <p className="text-xs text-zinc-500">{card.label}</p>
            <p className="mt-1.5 flex items-baseline gap-1">
              <span className="font-mono text-2xl font-semibold text-primary-800 tabular-nums">
                {card.value}
              </span>
              <span className="text-xs text-zinc-400">{card.unit}</span>
            </p>
          </div>
        ))}
      </div>

      {/* 主体：上传 + 最近简历（行高锁定为容器高度，条数不影响卡片高度，避免测量反馈循环） */}
      <div className="flex-1 min-h-0 grid grid-rows-[minmax(0,1fr)] lg:grid-cols-3 gap-5">
        {/* 上传区 */}
        <div className="lg:col-span-2 bg-white border border-zinc-200 rounded-lg flex flex-col">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">导入简历</h2>
            <span className="font-mono text-xs text-zinc-400">PDF / DOCX / TXT · ≤ 10MB</span>
          </div>

          <div className="flex-1 min-h-0 flex flex-col p-5">
            <div
              className={`flex-1 min-h-0 flex flex-col items-center justify-center border border-dashed rounded-lg px-6 text-center cursor-pointer transition-colors ${
                dragOver
                  ? 'border-primary-600 bg-primary-50/50'
                  : 'border-zinc-300 hover:border-primary-400'
              }`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => !uploading && document.getElementById('workspace-upload-input')?.click()}
            >
              <input
                type="file"
                id="workspace-upload-input"
                className="hidden"
                accept=".pdf,.doc,.docx,.txt"
                onChange={handleFileChange}
                disabled={uploading}
              />

              {selectedFile ? (
                <div
                  className="flex items-center gap-3 border border-zinc-200 bg-zinc-50 rounded-md px-4 py-3 text-left max-w-lg w-full"
                  onClick={(e) => e.stopPropagation()}
                >
                  <FileText className="w-4 h-4 text-zinc-400 shrink-0" />
                  <span className="flex-1 min-w-0 truncate font-mono text-sm text-zinc-800">
                    {selectedFile.name}
                  </span>
                  <span className="font-mono text-xs text-zinc-400 shrink-0">
                    {formatFileSize(selectedFile.size)}
                  </span>
                  <button
                    type="button"
                    className="p-1 text-zinc-400 hover:text-zinc-700 transition-colors shrink-0"
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedFile(null);
                    }}
                    aria-label="移除文件"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ) : (
                <div>
                  <div className="mx-auto w-11 h-11 rounded-md bg-primary-50 border border-primary-100 flex items-center justify-center">
                    <Upload className="w-5 h-5 text-primary-700" strokeWidth={1.75} />
                  </div>
                  <p className="mt-3.5 text-sm text-zinc-600">
                    拖拽简历文件到此处，或
                    <button
                      type="button"
                      className="text-primary-700 hover:text-primary-800 underline underline-offset-2 ml-1"
                      onClick={(e) => {
                        e.stopPropagation();
                        document.getElementById('workspace-upload-input')?.click();
                      }}
                    >
                      浏览文件
                    </button>
                  </p>
                  <p className="mt-1.5 text-xs text-zinc-400">上传后 AI 自动解析，即可开始多视角模拟面试</p>
                </div>
              )}
            </div>

            {/* 错误提示 */}
            {error && (
              <div className="mt-4 shrink-0 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700 fade-in">
                <AlertCircle className="w-4 h-4 shrink-0" />
                {error}
              </div>
            )}

            {/* 上传按钮 */}
            {selectedFile && (
              <div className="mt-4 shrink-0 fade-in">
                <button
                  type="button"
                  onClick={handleUpload}
                  disabled={uploading}
                  className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  {uploading && <Loader2 className="w-4 h-4 animate-spin" />}
                  {uploading ? '处理中…' : '上传并解析'}
                </button>
              </div>
            )}
          </div>
        </div>

        {/* 最近简历（窄屏隐藏，宽屏条目数随断点递增：2 → lg:3 → xl:5） */}
        <div className="hidden lg:flex bg-white border border-zinc-200 rounded-lg flex-col">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">最近简历</h2>
            {shownResumes.length > 0 && (
              <span className="font-mono text-xs text-zinc-400">最近 {shownResumes.length} 条</span>
            )}
          </div>

          {recentResumes.length === 0 ? (
            <p className="flex-1 flex items-center justify-center px-5 py-8 text-xs text-zinc-400 text-center">
              暂无简历，上传第一份开始模拟面试
            </p>
          ) : (
            <ul ref={listRef} className="flex-1 min-h-0 overflow-hidden py-1">
              {shownResumes.map((resume) => (
                <li key={resume.id}>
                  <button
                    type="button"
                    onClick={() => navigate(`/history/${resume.id}`)}
                    className="w-full flex items-center gap-3 px-5 py-2.5 hover:bg-zinc-50 transition-colors text-left"
                  >
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-zinc-800 truncate">{resume.filename}</p>
                      <p className="font-mono text-xs text-zinc-400 mt-0.5">
                        {formatDate(resume.uploadedAt, { month: '2-digit', day: '2-digit' })}
                      </p>
                    </div>
                    {typeof resume.latestScore === 'number' ? (
                      <span className="font-mono text-sm font-medium text-primary-800 tabular-nums shrink-0">
                        {resume.latestScore}
                      </span>
                    ) : (
                      <span
                        className={`text-xs border rounded px-1.5 py-0.5 shrink-0 ${analyzeStatusCls(resume.analyzeStatus)}`}
                      >
                        {analyzeStatusText(resume.analyzeStatus)}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="border-t border-zinc-100 shrink-0">
            <button
              type="button"
              onClick={() => navigate('/history')}
              className="w-full flex items-center justify-center gap-0.5 py-2.5 text-xs text-primary-700 hover:text-primary-800 hover:bg-zinc-50 transition-colors rounded-b-lg"
            >
              查看全部
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
