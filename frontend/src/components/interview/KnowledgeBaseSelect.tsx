import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  FileText,
  Check,
  Search,
  Loader2,
  X,
} from 'lucide-react';
import {
  knowledgeBaseApi,
  KnowledgeBaseItem,
} from '../../api/knowledgebase';

interface KnowledgeBaseSelectProps {
  selectedIds: number[];
  onChange: (ids: number[]) => void;
  maxSelections?: number;
  disabled?: boolean;
  placeholder?: string;
}

/**
 * 知识库选择组件
 * 用于在面试中选择知识库
 */
export default function KnowledgeBaseSelect({
  selectedIds,
  onChange,
  maxSelections = 5,
  disabled = false,
  placeholder = '点击选择知识库',
}: KnowledgeBaseSelectProps) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  // 加载知识库列表（只加载已向量化的）
  useEffect(() => {
    loadKnowledgeBases();
  }, []);

  const loadKnowledgeBases = async () => {
    try {
      setLoading(true);
      const data = await knowledgeBaseApi.getAllKnowledgeBases();
      // 只显示已完成的
      const completed = data.filter(kb => kb.vectorStatus === 'COMPLETED');
      setKnowledgeBases(completed);
    } catch (err) {
      console.error('加载知识库失败', err);
    } finally {
      setLoading(false);
    }
  };

  // 过滤知识库
  const filteredKnowledgeBases = knowledgeBases.filter(kb =>
    kb.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
    (kb.category?.toLowerCase().includes(searchKeyword.toLowerCase()))
  );

  // 处理选择知识库
  const handleSelect = (kbId: number) => {
    if (disabled) return;

    if (selectedIds.includes(kbId)) {
      // 取消选择
      onChange(selectedIds.filter(id => id !== kbId));
    } else {
      // 新增选择
      if (selectedIds.length < maxSelections) {
        onChange([...selectedIds, kbId]);
      }
    }
  };

  // 获取选中的知识库
  const selectedKBs = knowledgeBases.filter(kb => selectedIds.includes(kb.id));

  return (
    <div className="relative">
      {/* 已选知识库显示 */}
      <div
        className={`min-h-[48px] border rounded-lg p-2 cursor-pointer flex items-center gap-2 flex-wrap ${
          disabled
            ? 'bg-slate-50 border-slate-200 cursor-not-allowed'
            : 'bg-white border-slate-200 hover:border-slate-300'
        }`}
        onClick={() => !disabled && setIsOpen(!isOpen)}
      >
        {selectedKBs.length === 0 ? (
          <span className="text-slate-400">{placeholder}</span>
        ) : (
          selectedKBs.map(kb => (
            <span
              key={kb.id}
              className="inline-flex items-center gap-1 px-2 py-1 bg-green-100 text-green-700 rounded text-sm"
            >
              {kb.name}
              {!disabled && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleSelect(kb.id);
                  }}
                  className="hover:text-green-900"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          ))
        )}
        {selectedKBs.length > 0 && selectedKBs.length < maxSelections && !disabled && (
          <span className="text-xs text-slate-400 ml-auto">
            还可以选择 {maxSelections - selectedKBs.length} 个
          </span>
        )}
      </div>

      {/* 下拉选择框 */}
      <AnimatePresence>
        {isOpen && !disabled && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="absolute z-50 w-full mt-2 bg-white border border-slate-200 rounded-lg shadow-lg max-h-[400px] overflow-hidden"
          >
            {/* 搜索框 */}
            <div className="p-3 border-b border-slate-200">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  type="text"
                  placeholder="搜索知识库..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent"
                  onClick={(e) => e.stopPropagation()}
                />
              </div>
            </div>

            {/* 知识库列表 */}
            <div className="overflow-y-auto max-h-[300px] p-2">
              {loading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-6 h-6 text-green-600 animate-spin" />
                </div>
              ) : filteredKnowledgeBases.length === 0 ? (
                <div className="text-center py-8 text-slate-400">
                  暂无已完成的知识库
                </div>
              ) : (
                <div className="space-y-1">
                  {filteredKnowledgeBases.map(kb => {
                    const isSelected = selectedIds.includes(kb.id);
                    const isDisabled = !isSelected && selectedIds.length >= maxSelections;

                    return (
                      <div
                        key={kb.id}
                        className={`flex items-center justify-between p-3 rounded-lg cursor-pointer transition-colors ${
                          isDisabled
                            ? 'opacity-50 cursor-not-allowed'
                            : isSelected
                            ? 'bg-green-50'
                            : 'hover:bg-slate-50'
                        }`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (!isDisabled) {
                            handleSelect(kb.id);
                          }
                        }}
                      >
                        <div className="flex items-center gap-3">
                          <div className="p-2 rounded-lg bg-green-100">
                            <FileText className="w-4 h-4 text-green-600" />
                          </div>
                          <div>
                            <div className="font-medium text-slate-900">{kb.name}</div>
                            <div className="text-xs text-slate-500">
                              {kb.questionCount} 个问答
                              {kb.category ? ` · ${kb.category}` : ''}
                            </div>
                          </div>
                        </div>
                        {isSelected && (
                          <div className="w-5 h-5 rounded-full bg-green-600 flex items-center justify-center">
                            <Check className="w-3 h-3 text-white" />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 底部提示 */}
            {filteredKnowledgeBases.length > 0 && (
              <div className="p-3 border-t border-slate-200 text-xs text-slate-400">
                已选择 {selectedIds.length}/{maxSelections} 个知识库
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* 点击外部关闭 */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40"
          onClick={() => setIsOpen(false)}
        />
      )}
    </div>
  );
}
