import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Search,
  Trash2,
  MessageSquare,
  FileText,
  Loader2,
  ChevronDown,
  Edit3,
  Check,
  X,
  RefreshCw,
  Download,
} from 'lucide-react';
import {
  knowledgeBaseApi,
  KnowledgeBaseItem,
  KnowledgeBaseStats,
  SortOption,
  VectorStatus,
} from '../api/knowledgebase';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';

interface KnowledgeBaseManagePageProps {
  onUpload: () => void;
  onChat: () => void;
}

// 格式化文件大小（动态单位，最大TB）
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const unitIndex = Math.min(i, sizes.length - 1);
  return parseFloat((bytes / Math.pow(k, unitIndex)).toFixed(1)) + ' ' + sizes[unitIndex];
}

// 格式化日期
function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// 状态文本
function getStatusText(status: VectorStatus): string {
  switch (status) {
    case 'COMPLETED':
      return '已完成';
    case 'PROCESSING':
      return '处理中';
    case 'PENDING':
      return '待处理';
    case 'FAILED':
      return '失败';
    default:
      return '未知';
  }
}

// 状态 chip 样式（克制语义色）
function getStatusCls(status: VectorStatus): string {
  switch (status) {
    case 'PROCESSING':
      return 'text-amber-700 bg-amber-50 border-amber-200';
    case 'FAILED':
      return 'text-red-700 bg-red-50 border-red-200';
    case 'COMPLETED':
      return 'text-emerald-700 bg-emerald-50 border-emerald-200';
    default:
      return 'text-zinc-500 bg-zinc-50 border-zinc-200';
  }
}

export default function KnowledgeBaseManagePage({ onUpload, onChat }: KnowledgeBaseManagePageProps) {
  const [stats, setStats] = useState<KnowledgeBaseStats | null>(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('time');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [deleteItem, setDeleteItem] = useState<KnowledgeBaseItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  // 分类编辑状态
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [editingCategoryValue, setEditingCategoryValue] = useState('');
  const [savingCategory, setSavingCategory] = useState(false);
  const categoryInputRef = useRef<HTMLInputElement>(null);

  // 重新向量化状态
  const [revectorizing, setRevectorizing] = useState<number | null>(null);

  // 加载数据（不显示loading状态，用于轮询）
  const loadDataSilent = useCallback(async () => {
    try {
      const [statsData, kbList, categoryList] = await Promise.all([
        knowledgeBaseApi.getStatistics(),
        searchKeyword
          ? knowledgeBaseApi.search(searchKeyword)
          : selectedCategory
          ? knowledgeBaseApi.getByCategory(selectedCategory)
          : knowledgeBaseApi.getAllKnowledgeBases(sortBy),
        knowledgeBaseApi.getAllCategories(),
      ]);
      setStats(statsData);
      setKnowledgeBases(kbList);
      setCategories(categoryList);
    } catch (error) {
      console.error('加载数据失败:', error);
    }
  }, [searchKeyword, sortBy, selectedCategory]);

  // 加载数据
  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [statsData, kbList, categoryList] = await Promise.all([
        knowledgeBaseApi.getStatistics(),
        searchKeyword
          ? knowledgeBaseApi.search(searchKeyword)
          : selectedCategory
          ? knowledgeBaseApi.getByCategory(selectedCategory)
          : knowledgeBaseApi.getAllKnowledgeBases(sortBy),
        knowledgeBaseApi.getAllCategories(),
      ]);
      setStats(statsData);
      setKnowledgeBases(kbList);
      setCategories(categoryList);
    } catch (error) {
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, [searchKeyword, sortBy, selectedCategory]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 轮询：当有 PENDING 或 PROCESSING 状态时，每5秒刷新一次
  useEffect(() => {
    const hasPendingItems = knowledgeBases.some(
      kb => kb.vectorStatus === 'PENDING' || kb.vectorStatus === 'PROCESSING'
    );

    if (hasPendingItems && !loading) {
      const timer = setInterval(() => {
        loadDataSilent();
      }, 5000);

      return () => clearInterval(timer);
    }
  }, [knowledgeBases, loading, loadDataSilent]);

  // 重新向量化
  const handleRevectorize = async (id: number) => {
    try {
      setRevectorizing(id);
      await knowledgeBaseApi.revectorize(id);
      await loadDataSilent();
    } catch (error) {
      console.error('重新向量化失败:', error);
    } finally {
      setRevectorizing(null);
    }
  };

  // 删除知识库
  const handleDelete = async () => {
    if (!deleteItem) return;
    try {
      setDeleting(true);
      await knowledgeBaseApi.deleteKnowledgeBase(deleteItem.id);
      setDeleteItem(null);
      await loadData();
    } catch (error) {
      console.error('删除失败:', error);
    } finally {
      setDeleting(false);
    }
  };

  // 下载知识库
  const handleDownload = (kb: KnowledgeBaseItem) => {
    // 通过后端 API 下载文件
    const link = document.createElement('a');
    link.href = `/api/knowledgebase/${kb.id}/download`;
    link.download = kb.originalFilename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // 开始编辑分类
  const handleStartEditCategory = (kb: KnowledgeBaseItem) => {
    setEditingCategoryId(kb.id);
    setEditingCategoryValue(kb.category || '');
    setTimeout(() => {
      categoryInputRef.current?.focus();
    }, 50);
  };

  // 取消编辑分类
  const handleCancelEditCategory = () => {
    setEditingCategoryId(null);
    setEditingCategoryValue('');
  };

  // 保存分类
  const handleSaveCategory = async (id: number) => {
    try {
      setSavingCategory(true);
      const categoryToSave = editingCategoryValue.trim() || null;
      await knowledgeBaseApi.updateCategory(id, categoryToSave);
      setEditingCategoryId(null);
      setEditingCategoryValue('');
      await loadData();
    } catch (error) {
      console.error('更新分类失败:', error);
    } finally {
      setSavingCategory(false);
    }
  };

  // 处理分类输入框按键
  const handleCategoryKeyDown = (e: React.KeyboardEvent, id: number) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSaveCategory(id);
    } else if (e.key === 'Escape') {
      handleCancelEditCategory();
    }
  };

  // 搜索处理
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    loadData();
  };

  const statCards = stats
    ? [
        { label: '知识库总数', value: stats.totalCount.toLocaleString() },
        { label: '总访问次数', value: stats.totalAccessCount.toLocaleString() },
        { label: '总存储大小', value: formatFileSize(stats.totalStorageSize) },
      ]
    : [];

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">知识库管理</h1>
          <p className="mt-1 text-sm text-zinc-500">管理知识库文件，查看使用统计</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={onUpload}
            className="h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors"
          >
            上传知识库
          </button>
          <button
            onClick={onChat}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors flex items-center gap-1.5"
          >
            <MessageSquare className="w-4 h-4 text-zinc-400" />
            问答助手
          </button>
        </div>
      </div>

      {/* 统计概览 */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
          {statCards.map((card) => (
            <div key={card.label} className="bg-white border border-zinc-200 rounded-lg px-5 py-4">
              <p className="text-xs text-zinc-500">{card.label}</p>
              <p className="mt-1.5 font-mono text-2xl font-semibold text-primary-800 tabular-nums">
                {card.value}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* 搜索和筛选栏 */}
      <div className="bg-white border border-zinc-200 rounded-lg p-4 mb-5">
        <div className="flex flex-wrap items-center gap-3">
          {/* 搜索框 */}
          <form onSubmit={handleSearch} className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400 pointer-events-none" />
              <input
                type="text"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder="搜索知识库名称…"
                className="w-full h-9 pl-9 pr-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
              />
            </div>
          </form>

          {/* 排序选择 */}
          <div className="relative">
            <select
              value={sortBy}
              onChange={(e) => {
                setSortBy(e.target.value as SortOption);
                setSearchKeyword('');
                setSelectedCategory(null);
              }}
              className="appearance-none h-9 pl-3 pr-8 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 cursor-pointer focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
            >
              <option value="time">按时间排序</option>
              <option value="size">按大小排序</option>
              <option value="access">按访问排序</option>
            </select>
            <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400 pointer-events-none" />
          </div>

          {/* 分类筛选 */}
          <div className="relative">
            <select
              value={selectedCategory || ''}
              onChange={(e) => {
                setSelectedCategory(e.target.value || null);
                setSearchKeyword('');
              }}
              className="appearance-none h-9 pl-3 pr-8 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 cursor-pointer focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
            >
              <option value="">全部分类</option>
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
            <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400 pointer-events-none" />
          </div>
        </div>
      </div>

      {/* 知识库列表 */}
      <div className="bg-white border border-zinc-200 rounded-lg overflow-x-auto">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-5 h-5 text-primary-600 animate-spin" />
          </div>
        ) : knowledgeBases.length === 0 ? (
          <div className="px-5 py-12 text-center">
            <p className="text-xs text-zinc-400">暂无知识库，上传第一份开始构建问答</p>
            <button
              onClick={onUpload}
              className="mt-3 text-xs text-primary-700 hover:text-primary-800 hover:underline underline-offset-2 transition-colors"
            >
              上传第一个知识库
            </button>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-zinc-50 border-b border-zinc-100">
              <tr>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-zinc-500 whitespace-nowrap">
                  名称
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-zinc-500 whitespace-nowrap">
                  分类
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-zinc-500 whitespace-nowrap">
                  大小
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-zinc-500 whitespace-nowrap">
                  状态
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-zinc-500 whitespace-nowrap">
                  上传时间
                </th>
                <th className="text-right px-4 py-2.5 text-xs font-medium text-zinc-500 whitespace-nowrap">
                  操作
                </th>
              </tr>
            </thead>
            <tbody>
              {knowledgeBases.map((kb) => (
                <tr
                  key={kb.id}
                  className="border-b border-zinc-100 last:border-b-0 hover:bg-zinc-50 transition-colors"
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <FileText className="w-4 h-4 text-zinc-400 shrink-0" />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-zinc-800 truncate">{kb.name}</p>
                        <p className="font-mono text-xs text-zinc-400 truncate">{kb.originalFilename}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    {editingCategoryId === kb.id ? (
                      <div className="flex items-center gap-1.5">
                        <input
                          ref={categoryInputRef}
                          type="text"
                          value={editingCategoryValue}
                          onChange={(e) => setEditingCategoryValue(e.target.value)}
                          onKeyDown={(e) => handleCategoryKeyDown(e, kb.id)}
                          placeholder="输入分类名称"
                          list="category-suggestions"
                          className="w-28 h-8 px-2 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors disabled:bg-zinc-100"
                          disabled={savingCategory}
                        />
                        <datalist id="category-suggestions">
                          {categories.map((cat) => (
                            <option key={cat} value={cat} />
                          ))}
                        </datalist>
                        <button
                          onClick={() => handleSaveCategory(kb.id)}
                          disabled={savingCategory}
                          className="p-1 text-emerald-600 hover:bg-emerald-50 rounded-md transition-colors disabled:opacity-50"
                          title="保存"
                        >
                          {savingCategory ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                          ) : (
                            <Check className="w-4 h-4" />
                          )}
                        </button>
                        <button
                          onClick={handleCancelEditCategory}
                          disabled={savingCategory}
                          className="p-1 text-zinc-400 hover:text-zinc-600 hover:bg-zinc-100 rounded-md transition-colors disabled:opacity-50"
                          title="取消"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ) : (
                      <div className="flex items-center gap-1.5 group/category">
                        {kb.category ? (
                          <span className="text-xs text-zinc-600 bg-zinc-50 border border-zinc-200 rounded px-1.5 py-0.5">
                            {kb.category}
                          </span>
                        ) : (
                          <span className="text-xs text-zinc-400">未分类</span>
                        )}
                        <button
                          onClick={() => handleStartEditCategory(kb)}
                          className="p-1 text-zinc-400 hover:text-primary-700 hover:bg-zinc-100 rounded-md opacity-0 group-hover/category:opacity-100 transition-colors"
                          title="编辑分类"
                        >
                          <Edit3 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 font-mono text-sm text-zinc-600 tabular-nums whitespace-nowrap">
                    {formatFileSize(kb.fileSize)}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`text-xs border rounded px-1.5 py-0.5 whitespace-nowrap ${getStatusCls(kb.vectorStatus)}`}
                    >
                      {getStatusText(kb.vectorStatus)}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-zinc-500 tabular-nums whitespace-nowrap">
                    {formatDate(kb.uploadedAt)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-0.5">
                      {/* 下载按钮 */}
                      <button
                        onClick={() => handleDownload(kb)}
                        className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors"
                        title="下载"
                      >
                        <Download className="w-4 h-4" />
                      </button>
                      {/* 重新向量化按钮（仅 FAILED 状态显示） */}
                      {kb.vectorStatus === 'FAILED' && (
                        <button
                          onClick={() => handleRevectorize(kb.id)}
                          disabled={revectorizing === kb.id}
                          className="p-1.5 text-zinc-400 hover:text-primary-700 hover:bg-primary-50 rounded-md transition-colors disabled:opacity-50"
                          title="重新向量化"
                        >
                          <RefreshCw className={`w-4 h-4 ${revectorizing === kb.id ? 'animate-spin' : ''}`} />
                        </button>
                      )}
                      {/* 删除按钮 */}
                      <button
                        onClick={() => setDeleteItem(kb)}
                        className="p-1.5 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
                        title="删除"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteItem !== null}
        item={deleteItem}
        itemType="知识库"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteItem(null)}
      />
    </div>
  );
}
