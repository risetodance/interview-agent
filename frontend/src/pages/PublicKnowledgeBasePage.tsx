import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  Database,
  Search,
  Loader2,
  HardDrive,
  FileText,
  Clock,
  Users,
  Tag,
  ExternalLink,
  ChevronDown,
  Check,
  AlertCircle,
} from 'lucide-react';
import {
  knowledgeBaseApi,
  KnowledgeBaseItem,
  PublicSortOption,
} from '../api/knowledgebase';

interface PublicKnowledgeBasePageProps {
  onBack: () => void;
}

// 格式化日期
function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function PublicKnowledgeBasePage({ onBack }: PublicKnowledgeBasePageProps) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [sortBy, setSortBy] = useState<PublicSortOption>('usage');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categories, setCategories] = useState<string[]>([]);

  // 引用状态
  const [referencing, setReferencing] = useState<number | null>(null);
  const [referenceResult, setReferenceResult] = useState<{ success: boolean; message: string } | null>(null);

  // 加载公开知识库列表
  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      let kbList: KnowledgeBaseItem[];

      if (searchKeyword) {
        kbList = await knowledgeBaseApi.searchPublicKnowledgeBases(searchKeyword);
      } else if (selectedCategory) {
        kbList = await knowledgeBaseApi.getPublicKnowledgeBasesByCategory(selectedCategory);
      } else {
        kbList = await knowledgeBaseApi.getPublicKnowledgeBases(sortBy);
      }

      setKnowledgeBases(kbList);

      // 从结果中提取分类
      const uniqueCategories = [...new Set(kbList.map(kb => kb.category).filter(Boolean))] as string[];
      setCategories(uniqueCategories);
    } catch (error) {
      console.error('加载公开知识库失败:', error);
    } finally {
      setLoading(false);
    }
  }, [searchKeyword, sortBy, selectedCategory]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 搜索处理
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    loadData();
  };

  // 引用知识库
  const handleReference = async (id: number) => {
    try {
      setReferencing(id);
      const result = await knowledgeBaseApi.referenceKnowledgeBase(id);
      setReferenceResult(result);
      // 3秒后清除结果提示
      setTimeout(() => {
        setReferenceResult(null);
      }, 3000);
    } catch (error) {
      console.error('引用知识库失败:', error);
      setReferenceResult({ success: false, message: '引用失败，请稍后重试' });
      setTimeout(() => {
        setReferenceResult(null);
      }, 3000);
    } finally {
      setReferencing(null);
    }
  };

  return (
    <div className="max-w-7xl mx-auto">
      {/* 页面标题 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
            <Database className="w-7 h-7 text-primary-500" />
            公开知识库
          </h1>
          <p className="text-slate-500 mt-1">发现并引用其他用户分享的知识库</p>
        </div>
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition-colors"
        >
          返回
        </button>
      </div>

      {/* 搜索和筛选栏 */}
      <div className="bg-white rounded-xl p-4 shadow-sm border border-slate-100 mb-6">
        <div className="flex flex-wrap items-center gap-4">
          {/* 搜索框 */}
          <form onSubmit={handleSearch} className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="text"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder="搜索公开知识库..."
                className="w-full pl-10 pr-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>
          </form>

          {/* 排序选择 */}
          <div className="relative">
            <select
              value={sortBy}
              onChange={(e) => {
                setSortBy(e.target.value as PublicSortOption);
                setSearchKeyword('');
                setSelectedCategory(null);
              }}
              className="appearance-none pl-4 pr-10 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white cursor-pointer"
            >
              <option value="usage">按引用次数</option>
              <option value="time">按上传时间</option>
            </select>
            <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          </div>

          {/* 分类筛选 */}
          <div className="relative">
            <select
              value={selectedCategory || ''}
              onChange={(e) => {
                setSelectedCategory(e.target.value || null);
                setSearchKeyword('');
              }}
              className="appearance-none pl-4 pr-10 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white cursor-pointer"
            >
              <option value="">全部分类</option>
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
            <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          </div>
        </div>
      </div>

      {/* 引用结果提示 */}
      {referenceResult && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className={`mb-4 p-4 rounded-lg flex items-center gap-3 ${
            referenceResult.success
              ? 'bg-green-50 text-green-700 border border-green-200'
              : 'bg-red-50 text-red-700 border border-red-200'
          }`}
        >
          {referenceResult.success ? (
            <Check className="w-5 h-5" />
          ) : (
            <AlertCircle className="w-5 h-5" />
          )}
          {referenceResult.message}
        </motion.div>
      )}

      {/* 知识库列表 */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
          </div>
        ) : knowledgeBases.length === 0 ? (
          <div className="text-center py-20">
            <HardDrive className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-500">暂无公开知识库</p>
          </div>
        ) : (
          <div className="divide-y divide-slate-50">
            {knowledgeBases.map((kb, index) => (
              <motion.div
                key={kb.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
                className="p-6 hover:bg-slate-50 transition-colors"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-4 flex-1 min-w-0">
                    <div className="p-3 bg-primary-50 rounded-lg">
                      <FileText className="w-6 h-6 text-primary-500" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h3 className="font-medium text-slate-800 truncate">{kb.name}</h3>
                      <p className="text-sm text-slate-400 mt-1">{kb.originalFilename}</p>

                      <div className="flex flex-wrap items-center gap-4 mt-3">
                        {/* 分类标签 */}
                        {kb.category && (
                          <span className="flex items-center gap-1 text-xs text-slate-500">
                            <Tag className="w-3 h-3" />
                            {kb.category}
                          </span>
                        )}

                        {/* 文件大小 */}
                        <span className="text-xs text-slate-500">
                          {formatFileSize(kb.fileSize)}
                        </span>

                        {/* 上传时间 */}
                        <span className="flex items-center gap-1 text-xs text-slate-500">
                          <Clock className="w-3 h-3" />
                          {formatDate(kb.uploadedAt)}
                        </span>

                        {/* 引用次数 */}
                        <span className="flex items-center gap-1 text-xs text-slate-500">
                          <Users className="w-3 h-3" />
                          {kb.usageCount || 0} 次引用
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* 引用按钮 */}
                  <button
                    onClick={() => handleReference(kb.id)}
                    disabled={referencing === kb.id}
                    className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-50"
                  >
                    {referencing === kb.id ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <ExternalLink className="w-4 h-4" />
                    )}
                    引用
                  </button>
                </div>
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
